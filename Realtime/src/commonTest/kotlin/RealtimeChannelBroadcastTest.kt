import app.cash.turbine.test
import com.stitchdb.realtime.broadcast
import com.stitchdb.realtime.broadcast.BroadcastPayload
import com.stitchdb.realtime.broadcastFlow
import com.stitchdb.realtime.channel
import com.stitchdb.testing.assertPathIs
import com.stitchdb.testing.pathAfterVersion
import com.stitchdb.testing.toJsonElement
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealtimeChannelBroadcastTest {

    @Test
    fun testReceivingBroadcastsMixed() {
        val event = "event"
        val channelId = "channelId"
        val amount = 10
        runTest {
            createTestClient(
                wsHandler = { i, o ->
                    handleSubscribe(i, o, channelId)
                    for(i in 0..amount) {
                        o.sendBroadcast(channelId, event, buildJsonObject { put("key", i) })
                        o.sendBroadcastPayload(channelId, event, BroadcastPayload.Binary(byteArrayOf(1,2,3,4)))
                    }
                },
                stitchdbHandler = {
                    val channel = it.channel("channelId")
                    coroutineScope {
                        launch {
                            val broadcastFlow = channel.broadcastFlow(event)
                            broadcastFlow.test(FLOW_TIMEOUT) {
                                for(i in 0..amount) {
                                    val item1 = awaitItem()
                                    assertIs<BroadcastPayload.Json>(item1.payload)
                                    assertEquals(i, item1.payload.value.jsonObject["key"]?.jsonPrimitive?.int)
                                    val item2 = awaitItem()
                                    assertIs<BroadcastPayload.Binary>(item2.payload)
                                    assertContentEquals(byteArrayOf(1,2,3,4), item2.payload.data)
                                }
                            }
                        }
                        launch {
                            channel.subscribe(true)
                        }
                    }.join()
                }
            )
        }
    }

    @Test
    fun testReceivingBroadcastsSerialization() {
        val event = "event"
        val channelId = "channelId"
        val amount = 10
        runTest {
            createTestClient(
                wsHandler = { i, o ->
                    handleSubscribe(i, o, channelId)
                    for(i in 0..amount) {
                        o.sendBroadcast(channelId, event, buildJsonObject { put("key", i) })
                    }
                },
                stitchdbHandler = {
                    val channel = it.channel("channelId")
                    coroutineScope {
                        launch {
                            val broadcastFlow = channel.broadcastFlow<JsonObject>(event)
                            broadcastFlow.test(FLOW_TIMEOUT) {
                                for(i in 0..amount) {
                                    assertEquals(i, awaitItem()["key"]?.jsonPrimitive?.int)
                                }
                            }
                        }
                        launch {
                            channel.subscribe(true)
                        }
                    }.join()
                }
            )
        }
    }

    @Test
    fun testSendingBroadcastsUnsubscribed() {
        runTest {
            val expectedEvent = "event"
            val expectedMessage = buildJsonObject {
                put("key", "value")
            }
            val expectedChannelId = "channelId"
            val isPrivate = true
            createTestClient(
                wsHandler = { _, _ ->
                },
                stitchdbHandler = {
                    val channel = it.channel(expectedChannelId) {
                        this.isPrivate = isPrivate
                    }
                    channel.broadcast(expectedEvent, expectedMessage)
                },
                mockEngineHandler = {
                    assertPathIs("/api/broadcast/$expectedChannelId/events/$expectedEvent", it.url.pathAfterVersion())
                    assertEquals(expectedMessage, it.body.toJsonElement())
                    assertEquals(isPrivate.toString(), it.url.parameters["private"])
                    respond("", HttpStatusCode.Accepted)
                }
            )
        }
    }

}