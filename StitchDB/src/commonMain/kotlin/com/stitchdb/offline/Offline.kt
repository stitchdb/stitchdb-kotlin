package com.stitchdb.offline

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Offline-first sync (`stitchdb.offline()`): a local copy of the rows the signed-in user may see in the tables that
 * sync (Studio › Database › Offline sync). Reads come from the local copy, writes apply locally at once and are queued;
 * the queue is pushed and changes are pulled whenever the device is online.
 *
 * The engine in this file only talks to an [OfflineTransport]; the HTTP glue lives in StitchOffline.kt.
 */

/**
 * Where the local copy lives: string keys, JSON values. Keys used: `m` (meta), `pk` (primary keys of the synced
 * tables), `q/<n>` (queued writes) and `r/<table>/<primary key as JSON array>` (rows).
 */
interface OfflineStore {
    suspend fun get(key: String): JsonElement?
    suspend fun set(key: String, value: JsonElement)
    suspend fun delete(key: String)
    /** Every entry whose key starts with [prefix]. */
    suspend fun entries(prefix: String): List<Pair<String, JsonElement>>
    /** Remove everything. */
    suspend fun clear()
}

/** An [OfflineStore] in memory: survives a new [OfflineClient] on the same instance, not a restart of the process. */
class MemoryOfflineStore : OfflineStore {
    @Volatile
    private var map: PersistentMap<String, JsonElement> = persistentMapOf()
    override suspend fun get(key: String): JsonElement? = map[key]
    override suspend fun set(key: String, value: JsonElement) { map = map.putting(key, value) }
    override suspend fun delete(key: String) { map = map.removing(key) }
    override suspend fun entries(prefix: String): List<Pair<String, JsonElement>> =
        map.filterKeys { it.startsWith(prefix) }.map { it.key to it.value }
    override suspend fun clear() { map = persistentMapOf() }
}

/** The sync state. [lastSyncAt] is an ISO-8601 timestamp. */
data class SyncStatus(
    val online: Boolean = true,
    val syncing: Boolean = false,
    val pending: Int = 0,
    val lastSyncAt: String? = null,
    val error: String? = null,
)

/** A local write lost to the server's row ([status] `conflict`) or was refused ([status] `rejected`). */
data class ConflictEvent(
    val table: String,
    val pk: JsonObject,
    val local: JsonObject?,
    val server: JsonObject?,
    val status: String,
    val message: String?,
)

/** A sync error. [status] is the HTTP status, 0 when the server could not be reached. */
class OfflineException(message: String, val status: Int) : Exception(message)

/** How the engine reaches the server. Paths are relative to `/sync/v1`; failures throw [OfflineException]. */
internal interface OfflineTransport {
    suspend fun get(path: String): JsonObject
    suspend fun post(path: String, body: JsonObject): JsonObject
}

private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
private const val PUSH_BATCH = 200

private fun rid(n: Int = 16): String = buildString { repeat(n) { append(ID_ALPHABET[Random.nextInt(64)]) } }
private fun nowIso(): String = Clock.System.now().toString()
private fun qKey(n: Long) = "q/" + n.toString().padStart(12, '0')

private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private val JsonElement?.long: Long? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal class Entry(val row: JsonObject, val seq: Long) {
    fun toJson() = buildJsonObject { put("row", row); put("seq", seq) }
    companion object {
        fun from(e: JsonElement?) = e.obj?.let { o -> o["row"].obj?.let { Entry(it, o["seq"].long ?: 0) } }
    }
}

private class Mutation(
    val id: String,
    val n: Long,
    val table: String,
    val op: String,
    val row: JsonObject?,
    val pk: JsonObject?,
    var base: Long?,
    val at: String,
) {
    fun toWire() = buildJsonObject {
        put("id", id); put("table", table); put("op", op); put("base", base); put("at", at)
        if (row != null) put("row", row) else if (pk != null) put("pk", pk)
    }
    fun toStored() = JsonObject(toWire() + ("_n" to JsonPrimitive(n)))
    companion object {
        fun from(e: JsonElement) = e.jsonObject.let { o ->
            Mutation(o["id"].str ?: "", o["_n"].long ?: 0, o["table"].str ?: "", o["op"].str ?: "upsert", o["row"].obj, o["pk"].obj, o["base"].long, o["at"].str ?: "")
        }
    }
}

/** One synced table. Rows are [JsonObject]s; decode them with your serializer of choice. */
class OfflineTable internal constructor(private val c: OfflineClient, val name: String) {
    /** Every local row. */
    fun all(): List<JsonObject> = c.rows(name).values.map { it.row }
    /** One row by primary key: an object with the key columns, or the value for a one-column key. */
    fun get(pk: JsonElement): JsonObject? = c.rows(name)[c.key(name, pk)]?.row
    fun get(pk: String): JsonObject? = get(JsonPrimitive(pk))
    fun get(pk: Number): JsonObject? = get(JsonPrimitive(pk))
    /** Local rows that match. */
    fun where(predicate: (JsonObject) -> Boolean): List<JsonObject> = all().filter(predicate)
    /** Insert or update a row locally and queue it for the server. The row must include its primary key. */
    suspend fun upsert(row: JsonObject) = c.write(name, "upsert", row)
    /** Delete a row locally and on the server. */
    suspend fun delete(pk: JsonElement) = c.write(name, "delete", pk)
    suspend fun delete(pk: String) = delete(JsonPrimitive(pk))
    suspend fun delete(pk: Number) = delete(JsonPrimitive(pk))
    /** The table's rows now and after every local or synced change. */
    fun observe(): Flow<List<JsonObject>> = c.changes.filter { it == name }.map { all() }.onStart { emit(all()) }
}

/**
 * The offline client. Create it with `stitchdb.offline()`. All state changes run on one serial dispatcher, so
 * reads ([OfflineTable.all], [OfflineTable.get]) are safe from any thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineClient internal constructor(
    private val transport: OfflineTransport,
    private val store: OfflineStore,
    private val tables: List<String>? = null,
    private val interval: Duration = 30.seconds,
    private val batch: Int = 500,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val serial = dispatcher.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)

    private var client = ""
    private var cursors = mutableMapOf<String, Long>()
    private var next = 1L
    @Volatile private var pk: Map<String, List<String>> = emptyMap()
    @Volatile private var data: PersistentMap<String, PersistentMap<String, Entry>> = persistentMapOf()
    private val queue = mutableListOf<Mutation>()
    private var timer: Job? = null
    private var running: Deferred<Unit>? = null
    @Volatile private var closed = false

    private val _status = MutableStateFlow(SyncStatus())
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _conflicts = MutableSharedFlow<ConflictEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** online, syncing, pending (queued writes), lastSyncAt, error. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()
    /** The name of a table after every local or synced change to it. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()
    /** A local write that lost or was refused; the local copy now holds the server's row. */
    val conflicts: SharedFlow<ConflictEvent> = _conflicts.asSharedFlow()

    /** Completes once the local copy is loaded. */
    val ready: Deferred<Unit> = scope.async { open() }

    suspend fun awaitReady() = ready.await()

    /** One synced table. */
    fun table(name: String) = OfflineTable(this, name)

    private suspend fun open() {
        store.get("m").obj?.let { m ->
            client = m["client"].str ?: ""
            cursors = m["cursors"].obj?.mapValues { it.value.long ?: 0 }?.toMutableMap() ?: mutableMapOf()
            next = m["next"].long ?: 1
        }
        if (client.isEmpty()) { client = rid(); saveMeta() }
        pk = store.get("pk").obj?.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } } ?: emptyMap()
        for ((k, v) in store.entries("r/")) {
            val rest = k.removePrefix("r/")
            val slash = rest.indexOf('/')
            if (slash <= 0) continue
            val e = Entry.from(v) ?: continue
            val t = rest.substring(0, slash)
            data = data.putting(t, (data[t] ?: persistentMapOf()).putting(rest.substring(slash + 1), e))
        }
        queue.addAll(store.entries("q/").map { Mutation.from(it.second) }.sortedBy { it.n })
        setStatus { it.copy(pending = queue.size) }
        try { loadTables() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (pk.isEmpty()) setStatus { it.copy(online = false, error = e.message) }
        }
        if (interval.isPositive()) timer = scope.launch {
            while (isActive) { delay(interval); if (_status.value.online) syncQuietly() }
        }
        scope.launch { syncQuietly() }
    }

    private suspend fun syncQuietly() { try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) {} }

    private suspend fun saveMeta() = store.set("m", buildJsonObject {
        put("client", client)
        put("cursors", JsonObject(cursors.mapValues { JsonPrimitive(it.value) }))
        put("next", next)
    })

    private suspend fun loadTables() {
        val list = transport.get("/tables")["tables"]?.jsonArray ?: JsonArray(emptyList())
        val found = mutableMapOf<String, List<String>>()
        for (t in list) {
            val o = t.jsonObject
            val name = o["name"].str ?: continue
            if (tables == null || name in tables) found[name] = o["pk"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }
        for (name in tables.orEmpty()) if (name !in found) throw OfflineException("$name does not sync. Turn it on in Studio › Database › Offline sync.", 404)
        pk = found
        store.set("pk", JsonObject(found.mapValues { (_, cols) -> JsonArray(cols.map { JsonPrimitive(it) }) }))
    }

    internal fun rows(table: String): Map<String, Entry> = data[table] ?: persistentMapOf()

    internal fun key(table: String, v: JsonElement): String {
        val cols = pk[table] ?: throw OfflineException("$table is not synced offline", 404)
        val o = v as? JsonObject ?: JsonObject(mapOf(cols[0] to v))
        return JsonArray(cols.map { c ->
            val x = o[c]
            if (x == null || x is JsonNull) throw OfflineException("$table: the primary key column $c is missing", 400)
            x
        }).toString()
    }

    private fun pkObj(table: String, v: JsonElement): JsonObject {
        val cols = pk[table].orEmpty()
        val o = v as? JsonObject ?: JsonObject(mapOf(cols[0] to v))
        return JsonObject(cols.associateWith { o[it] ?: JsonNull })
    }

    private suspend fun put(table: String, key: String, v: Entry?) {
        val rows = data[table] ?: persistentMapOf()
        if (v != null) { data = data.putting(table, rows.putting(key, v)); store.set("r/$table/$key", v.toJson()) }
        else { data = data.putting(table, rows.removing(key)); store.delete("r/$table/$key") }
    }

    internal suspend fun write(table: String, op: String, v: JsonElement) = withContext(serial) {
        ready.await()
        val key = key(table, v)
        val cur = rows(table)[key]
        val id = "$client.${next++}"
        val m = Mutation(
            id = id, n = next, table = table, op = op,
            row = if (op == "upsert") JsonObject((cur?.row ?: emptyMap()) + v.jsonObject) else null,
            pk = if (op == "delete") pkObj(table, v) else null,
            base = cur?.seq, at = nowIso(),
        )
        if (op == "upsert") put(table, key, Entry(m.row!!, cur?.seq ?: 0)) else put(table, key, null)
        queue.add(m)
        store.set(qKey(m.n), m.toStored())
        saveMeta()
        setStatus { it.copy(pending = queue.size) }
        _changes.tryEmit(table)
        if (_status.value.online) scope.launch { syncQuietly() }
    }

    /** Push queued writes, then pull until up to date. Throws [OfflineException] if the server can't be reached. */
    suspend fun sync(): Unit = withContext(serial) {
        if (closed) return@withContext
        val current = running
        if (current != null) {
            current.await()
            if (queue.isNotEmpty()) sync()
            return@withContext
        }
        val job = scope.async(start = CoroutineStart.LAZY) { try { run() } finally { running = null } }
        running = job
        job.await()
    }

    private suspend fun run() {
        try { ready.await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        setStatus { it.copy(syncing = true) }
        try {
            if (pk.isEmpty()) loadTables()
            pushAll()
            pullAll()
            setStatus { it.copy(online = true, syncing = false, error = null, lastSyncAt = nowIso()) }
        } catch (e: CancellationException) {
            setStatus { it.copy(syncing = false) }
            throw e
        } catch (e: Exception) {
            setStatus { it.copy(syncing = false, online = !(e is OfflineException && e.status == 0), error = e.message) }
            throw e
        }
    }

    private fun sameRow(m: Mutation) = m.row ?: m.pk ?: JsonObject(emptyMap())

    private suspend fun pushAll() {
        while (queue.isNotEmpty()) {
            val b = queue.take(PUSH_BATCH)
            val body = buildJsonObject { put("client", client); put("mutations", JsonArray(b.map { it.toWire() })) }
            val results = transport.post("/push", body)["results"]?.jsonArray.orEmpty()
            if (results.isEmpty()) throw OfflineException("Sync failed (empty push response)", 500)
            for ((i, re) in results.withIndex()) {
                val m = b.getOrNull(i) ?: break
                val r = re.jsonObject
                val key = key(m.table, sameRow(m))
                val later = queue.any { it !== m && it.table == m.table && key(it.table, sameRow(it)) == key }
                val status = r["status"].str
                val serverRow = r["row"].obj
                if (status == "conflict" || status == "rejected") {
                    _conflicts.tryEmit(ConflictEvent(m.table, r["pk"].obj ?: pkObj(m.table, sameRow(m)), m.row, serverRow, status, r["message"].str))
                }
                val seq = r["seq"].long
                if (!later) put(m.table, key, serverRow?.let { Entry(it, seq ?: 0) })
                else if (seq != null && seq != 0L) for (q in queue) if (q !== m && q.table == m.table && key(q.table, sameRow(q)) == key) q.base = seq
                queue.remove(m)
                store.delete(qKey(m.n))
                _changes.tryEmit(m.table)
            }
            setStatus { it.copy(pending = queue.size) }
        }
    }

    private suspend fun pullAll() {
        var more = true
        while (more) {
            val body = buildJsonObject {
                put("tables", JsonObject(pk.keys.associateWith { JsonPrimitive(cursors[it] ?: 0) }))
                put("limit", batch)
            }
            val r = transport.post("/pull", body)
            val reset = r["reset"]?.jsonArray?.mapNotNull { it.str }.orEmpty()
            for (t in reset) {
                for (k in rows(t).keys.toList()) put(t, k, null)
                cursors[t] = 0
            }
            for ((t, listEl) in r["changes"].obj.orEmpty()) {
                val list = listEl.jsonArray
                val pending = queue.filter { it.table == t }.map { key(t, sameRow(it)) }.toSet()
                for (chEl in list) {
                    val ch = chEl.jsonObject
                    val key = key(t, ch["pk"] ?: JsonNull)
                    if (key in pending) continue // the local write wins until it is pushed
                    put(t, key, if (ch["op"].str == "u") ch["row"].obj?.let { Entry(it, ch["seq"].long ?: 0) } else null)
                }
                if (list.isNotEmpty() || t in reset) _changes.tryEmit(t)
            }
            r["cursors"].obj?.forEach { (t, v) -> v.long?.let { cursors[t] = it } }
            saveMeta()
            more = (r["more"] as? JsonPrimitive)?.contentOrNull == "true"
        }
    }

    private fun setStatus(fn: (SyncStatus) -> SyncStatus) = _status.update(fn)

    /** Tell the client the device went online (syncs now) or offline. */
    fun setOnline(online: Boolean) {
        setStatus { it.copy(online = online) }
        if (online && !closed) scope.launch { syncQuietly() }
    }

    /** Stop syncing. With [clear] also delete the local copy and the queued writes (e.g. on sign-out). */
    suspend fun close(clear: Boolean = false) {
        withContext(serial) {
            closed = true
            timer?.cancel()
            try { running?.await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            if (clear) store.clear()
        }
        scope.cancel()
    }
}
