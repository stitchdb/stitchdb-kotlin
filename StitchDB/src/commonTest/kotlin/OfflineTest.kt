import com.stitchdb.offline.MemoryOfflineStore
import com.stitchdb.offline.OfflineClient
import com.stitchdb.offline.OfflineException
import com.stitchdb.offline.OfflineTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class OfflineTest {

    /** The sync server: 503 while [up] is false. */
    private class FakeSyncServer : OfflineTransport {
        var up = false
        val pushed = mutableListOf<JsonObject>()

        override suspend fun get(path: String): JsonObject {
            if (!up) throw OfflineException("down", 503)
            check(path == "/tables") { "unexpected GET $path" }
            return buildJsonObject {
                putJsonArray("tables") {
                    add(buildJsonObject { put("name", "todos"); putJsonArray("pk") { add(JsonPrimitive("id")) }; put("conflict", "last_write_wins") })
                }
            }
        }

        override suspend fun post(path: String, body: JsonObject): JsonObject {
            if (!up) throw OfflineException("down", 503)
            return when (path) {
                "/push" -> {
                    val mutations = body["mutations"]!!.jsonArray.map { it.jsonObject }
                    pushed += mutations
                    buildJsonObject {
                        put("results", buildJsonArray {
                            for (m in mutations) add(buildJsonObject {
                                put("id", m["id"]!!)
                                put("status", "applied")
                                put("seq", 7)
                                putJsonObject("pk") { put("id", m["row"]!!.jsonObject["id"]!!) }
                                put("row", m["row"]!!)
                            })
                        })
                    }
                }
                "/pull" -> buildJsonObject {
                    putJsonObject("changes") {
                        put("todos", buildJsonArray {
                            add(buildJsonObject {
                                put("seq", 8); put("op", "u")
                                putJsonObject("pk") { put("id", "b") }
                                putJsonObject("row") { put("id", "b"); put("title", "from server") }
                            })
                        })
                    }
                    putJsonObject("cursors") { put("todos", 8) }
                    put("more", false)
                    put("reset", JsonArray(emptyList()))
                }
                else -> error("unexpected POST $path")
            }
        }
    }

    @Test
    fun writesQueueWhileTheServerIsDownAndSurviveARestart() = runTest {
        val server = FakeSyncServer()
        val store = MemoryOfflineStore()
        server.up = true
        var off = OfflineClient(server, store, interval = Duration.ZERO)
        off.awaitReady()
        runCatching { off.sync() }
        server.up = false
        off.table("todos").upsert(buildJsonObject { put("id", "a"); put("title", "offline") })
        assertEquals(1, off.status.value.pending)
        off.close()

        off = OfflineClient(server, store, interval = Duration.ZERO)
        off.awaitReady()
        assertEquals(1, off.status.value.pending, "queue persisted")
        assertEquals("offline", off.table("todos").get("a")?.get("title")?.jsonPrimitive?.content)
        runCatching { off.sync() } // still down: the write stays queued
        assertEquals(1, off.status.value.pending)

        server.up = true
        off.sync()
        assertEquals(0, off.status.value.pending)
        assertEquals(
            listOf(listOf("todos", "upsert", "offline", "null")),
            server.pushed.map { m ->
                listOf(m["table"]!!.jsonPrimitive.content, m["op"]!!.jsonPrimitive.content, m["row"]!!.jsonObject["title"]!!.jsonPrimitive.content, m["base"].toString())
            },
        )
        assertEquals(JsonNull, server.pushed[0]["base"])
        val id = server.pushed[0]["id"]!!.jsonPrimitive.content
        assertTrue(Regex("^[A-Za-z0-9_-]{16}\\.\\d+$").matches(id), "mutation id $id")
        assertEquals("from server", off.table("todos").get("b")?.get("title")?.jsonPrimitive?.content)
        assertEquals("offline", off.table("todos").get("a")?.get("title")?.jsonPrimitive?.content)
        off.close(clear = true)
        assertTrue(store.entries("").isEmpty())
    }
}
