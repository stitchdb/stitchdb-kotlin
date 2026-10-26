package com.stitchdb.analytics

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.collections.update
import com.stitchdb.crashes.CrashStore
import com.stitchdb.crashes.crashDevice
import com.stitchdb.crashes.crashPlatform
import com.stitchdb.crashes.nowIso
import com.stitchdb.crashes.reportingToken
import com.stitchdb.crashes.toJsonObject
import com.stitchdb.logging.d
import com.stitchdb.plugins.SessionTokenSource
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Product analytics (`stitchdb.analytics`): the events your app tracks, linked to the signed-in user (an anonymous id
 * before sign-in, merged into the user when they sign in), for active users, funnels, retention and breakdowns in
 * Studio › Analytics. Events go to `{projectUrl}/analytics/v1`.
 */

/** One tracked event as it is sent; [AnalyticsClient.start]'s `beforeSend` may change it with [copy]. */
@Serializable
data class AnalyticsEvent(
    val name: String,
    val props: JsonObject = JsonObject(emptyMap()),
    /** ISO-8601 time the event was tracked. */
    val at: String = nowIso(),
)

private const val MAX_QUEUE = 1000
private const val MAX_EARLY = 100
private const val BATCH = 100
private const val FLUSH_AT = 20
private const val MAX_PROPS_BYTES = 8192
private const val HTTP_ERROR = 400
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_LARGE = 413
private const val QUEUE_FILE = "queue.json"
private const val ANON_FILE = "anon"
private const val OPT_OUT_FILE = "optout"
private val MIN_INTERVAL = 1.seconds

/** What the server accepts as an event name: letters, digits, `_ $ . : - /` and spaces, up to 64 characters. */
private val NAME_RE = Regex("^[\$A-Za-z0-9_][\\w\$.: /-]{0,63}\$")

private val analyticsJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

private val events = ListSerializer(AnalyticsEvent.serializer())

private class AnalyticsOptions(
    val release: String?,
    val beforeSend: ((AnalyticsEvent) -> AnalyticsEvent?)?,
    val flushInterval: Duration,
)

/**
 * Product analytics, one per client: `stitchdb.analytics`.
 *
 * ```kotlin
 * stitchdb.analytics.start(release = "1.4.0")
 * stitchdb.analytics.track("purchase", mapOf("plan" to "pro", "amount" to 29))
 * stitchdb.analytics.optOut() // the user does not want to be tracked
 * ```
 *
 * Nothing is sent before [start] and nothing unless the project has analytics turned on (Studio › Analytics); events
 * tracked before [start] wait in memory (at most 100). Events are sent in batches in the background as the signed-in
 * user (session token, else the project key) with the anonymous id of this device, and kept on disk (at most 1000)
 * while offline. Passwords, tokens, keys and e-mail addresses are removed by StitchDB before anything is stored.
 */
@OptIn(StitchInternal::class, ExperimentalUuidApi::class)
class AnalyticsClient internal constructor(private val stitch: StitchClient) {

    private val base = stitch.stitchdbHttpUrl.trimEnd('/') + "/analytics/v1"
    internal val projectHost: String = Url(stitch.stitchdbHttpUrl).host
    private val scope = CoroutineScope(SupervisorJob() + stitch.coroutineDispatcher)
    private val sendLock = Mutex()
    private val persistLock = Mutex()
    private val persistPending = AtomicBoolean(false)
    private val queue = AtomicReference<List<AnalyticsEvent>>(emptyList())
    private val _queued = MutableStateFlow(0)
    private val jobs = AtomicReference<List<Job>>(emptyList())
    private val sessionId = Uuid.random().toString()
    private val device: Map<String, String> by lazy { platformInfo() }

    @Volatile private var options = AnalyticsOptions(null, null, 10.seconds)
    @Volatile private var storeRef: CrashStore? = null
    @Volatile private var anonId: String? = null
    @Volatile private var optedOut: Boolean? = null
    @Volatile private var disabled = false
    @Volatile private var startJob: Job? = null
    @Volatile private var storeChosen = false // by the first start; events tracked before it wait in memory

    /** Whether [start] was called (and [stop] was not). */
    @Volatile
    var isStarted = false
        private set

    /** The number of events waiting to be sent. */
    val queued: StateFlow<Int> = _queued.asStateFlow()

    /** Whether the user opted out on this device ([optOut]): [track] does nothing and nothing is sent. */
    val isOptedOut: Boolean
        get() = optedOut ?: (store().read(OPT_OUT_FILE) != null).also { optedOut = it }

    /** The id of this device before sign-in (kept with the queue); merged into the user when they sign in. */
    val anonymousId: String
        get() = anonId ?: loadAnonId(store())

    /**
     * Starts sending: the events tracked so far and the ones kept from earlier runs go out in the background, then every
     * [flushInterval] and whenever 20 events wait, and when the signed-in user changes (sign-in, sign-out).
     *
     * @param release your app's version, for the breakdown by release
     * @param beforeSend change an event or return null to drop it. Runs synchronously in [track].
     * @param flushInterval how often waiting events are sent (default 10 s, at least 1 s)
     * @param directory where the queue, the anonymous id and the opt-out are kept. Default on the JVM:
     * `~/.stitchdb/analytics/<project host>` (or under `java.io.tmpdir` when the home directory is not writable); on
     * Android use the `start(context, ...)` overload (`filesDir/stitchdb-analytics/<project host>`), without it the app's
     * cache directory is used. Other targets keep events in memory.
     */
    fun start(
        release: String? = null,
        beforeSend: ((AnalyticsEvent) -> AnalyticsEvent?)? = null,
        flushInterval: Duration = 10.seconds,
        directory: String? = null,
    ) {
        options = AnalyticsOptions(release, beforeSend, flushInterval.coerceAtLeast(MIN_INTERVAL))
        if (isStarted) return
        if (directory != null || storeRef == null) {
            val optedOutBefore = optedOut == true
            storeRef = analyticsStore(directory, projectHost)
            anonId = null
            optedOut = null
            if (optedOutBefore) storeRef?.write(OPT_OUT_FILE, "1")
        }
        val store = store()
        storeChosen = true
        if (isOptedOut) queue.store(emptyList())
        val saved = if (isOptedOut) emptyList() else loadQueue(store)
        queue.update { (saved + it).takeLast(MAX_QUEUE) }
        disabled = false
        isStarted = true
        writeQueue()
        val interval = options.flushInterval
        val timer = scope.launch {
            while (isActive) {
                delay(interval)
                flushNow()
            }
        }
        // a batch belongs to one user: send what is waiting when the user changes
        val userChanges = stitch.pluginManager.installedPlugins.values.firstNotNullOfOrNull { (it as? SessionTokenSource)?.signedInUserIds }
        val auth = userChanges?.let { flow -> scope.launch(start = CoroutineStart.UNDISPATCHED) { flow.drop(1).collect { flushNow() } } }
        jobs.update { it + listOfNotNull(timer, auth) }
        startJob = scope.launch { flushNow() }
    }

    /** Stops sending (timer and listeners); waiting events stay on the device for the next [start]. */
    fun stop() {
        isStarted = false
        jobs.exchange(emptyList()).forEach { it.cancel() }
        writeQueue()
        schedulePersist()
    }

    /**
     * Tracks an event. [name]: letters, digits, `_ $ . : - /` and spaces, up to 64 characters (other names are dropped).
     * [props]: strings, numbers, booleans, lists and maps (flat values are easiest to break down by), at most 8 KB.
     */
    fun track(name: String, props: Map<String, Any?> = emptyMap()) {
        if (disabled || isOptedOut) return
        if (!NAME_RE.matches(name)) {
            stitch.logger.d { "Analytics: \"$name\" is not a valid event name (letters, digits, _ \$ . : - / and spaces, up to 64); dropped" }
            return
        }
        val event = AnalyticsEvent(name, props.toJsonObject())
        val beforeSend = options.beforeSend
        @Suppress("TooGenericExceptionCaught")
        val ready = try {
            if (beforeSend == null) event else beforeSend(event)
        } catch (e: Exception) {
            stitch.logger.d(e) { "Analytics: beforeSend threw, the event is dropped" }
            null
        } ?: return
        if (ready.props.toString().encodeToByteArray().size > MAX_PROPS_BYTES) {
            stitch.logger.d { "Analytics: the props of \"${ready.name}\" are larger than 8 KB; dropped" }
            return
        }
        val started = isStarted
        val max = if (started) MAX_QUEUE else MAX_EARLY
        queue.update { (it + ready).takeLast(max) }
        val size = queue.load().size
        _queued.value = size
        if (!started) return
        schedulePersist()
        if (size >= FLUSH_AT) scope.launch { flushNow() }
    }

    /** Sends what is waiting now (before the app or a job ends, for example). Unsent events are kept for later. */
    suspend fun flush() {
        startJob?.join()
        flushNow()
    }

    private suspend fun flushNow() {
        if (!isStarted || isOptedOut) return
        sendLock.withLock { sendQueue() }
        persist()
    }

    /** The user does not want to be tracked: their events are deleted and no new ones are sent (remembered on this device). */
    suspend fun optOut() {
        optedOut = true
        store().write(OPT_OUT_FILE, "1")
        queue.store(emptyList())
        persist()
        send(HttpMethod.Post, "opt-out", "{}") // the device remembers it even when this fails
    }

    /** Tracks again after [optOut]. */
    suspend fun optIn() {
        optedOut = false
        store().write(OPT_OUT_FILE, null)
        send(HttpMethod.Delete, "opt-out", null)
    }

    private suspend fun sendQueue() {
        while (!disabled && isStarted && !isOptedOut) {
            val batch = queue.load().take(BATCH)
            if (batch.isEmpty()) return
            val body = buildJsonObject {
                put("anon_id", anonymousId)
                put("session_id", sessionId)
                put("release", options.release)
                device.forEach { (k, v) -> put(k, v) }
                put("events", analyticsJson.encodeToJsonElement(events, batch))
            }
            val status = send(HttpMethod.Post, "track", body.toString())?.status?.value ?: return // offline: later
            when {
                status == HTTP_NOT_FOUND || status == HTTP_FORBIDDEN -> { // analytics is off for the project
                    disabled = true
                    queue.store(emptyList())
                }
                status < HTTP_ERROR || status == HTTP_BAD_REQUEST || status == HTTP_TOO_LARGE ->
                    queue.update { list -> list.filterNot { e -> batch.any { it === e } } }
                else -> return // 429 (the month's events are used up) or the server is busy: keep them for later
            }
        }
    }

    private suspend fun send(method: HttpMethod, path: String, body: String?): HttpResponse? = try {
        val token = stitch.reportingToken()
        stitch.httpClient.request("$base/$path") {
            this.method = method
            bearerAuth(token)
            jsonBody(body)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        stitch.logger.d(e) { "Analytics: ${method.value} $path could not be sent" }
        null
    }

    private fun HttpRequestBuilder.jsonBody(body: String?) {
        if (body == null) return
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    /** The store analytics uses (the chosen directory once [start] ran, else the default), for the deep links flag. */
    internal fun deviceStore(): CrashStore = store()

    private fun store(): CrashStore = storeRef ?: analyticsStore(null, projectHost).also { storeRef = it }

    private fun loadAnonId(store: CrashStore): String {
        val id = store.read(ANON_FILE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: Uuid.random().toString().also { store.write(ANON_FILE, it) }
        anonId = id
        return id
    }

    // Writing the queue on every change, off the caller's thread; at most one write waits.
    private fun schedulePersist() {
        if (persistPending.compareAndSet(false, true)) scope.launch { persist() }
    }

    private suspend fun persist() = persistLock.withLock {
        persistPending.store(false)
        writeQueue()
    }

    private fun writeQueue() {
        val list = queue.load()
        _queued.value = list.size
        if (!storeChosen) return
        store().write(QUEUE_FILE, if (list.isEmpty()) null else analyticsJson.encodeToString(events, list))
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadQueue(store: CrashStore): List<AnalyticsEvent> = try {
        store.read(QUEUE_FILE)?.let { analyticsJson.decodeFromString(events, it) }?.takeLast(MAX_QUEUE) ?: emptyList()
    } catch (e: Exception) {
        emptyList() // a file cut off by a kill: start over
    }

    private fun platformInfo(): Map<String, String> = buildMap {
        put("platform", crashPlatform)
        val dev = crashDevice()
        listOfNotNull(dev["os"], dev["os_version"]).joinToString(" ").takeIf { it.isNotEmpty() }?.let { put("os", it) }
        deviceCountry()?.let { put("country", it) }
    }
}
