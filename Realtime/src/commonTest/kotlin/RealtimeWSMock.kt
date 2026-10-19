import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder
import com.stitchdb.createStitchClient
import com.stitchdb.logging.LogLevel
import com.stitchdb.realtime.Realtime
import com.stitchdb.realtime.RealtimeMessage
import com.stitchdb.realtime.RealtimeProtocolVersion
import com.stitchdb.realtime.broadcast.encodeV2Text
import com.stitchdb.realtime.websocket.RealtimeWebsocket
import com.stitchdb.realtime.websocket.RealtimeWebsocketFactory
import com.stitchdb.stitchdbJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.websocket.Frame
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

class MockWSFactory(
    private val clientChannel: Channel<Frame>,
    private val serverChannel: Channel<Frame>
) : RealtimeWebsocketFactory {

    override suspend fun create(url: String): RealtimeWebsocket {
        return MockWS(clientChannel, serverChannel)
    }

}

@OptIn(DelicateCoroutinesApi::class)
class MockWS(
    private val sendChannel: SendChannel<Frame>,
    private val receiveChannel: ReceiveChannel<Frame>
) : RealtimeWebsocket {

    override val hasIncomingMessages: Boolean
        get() = !receiveChannel.isClosedForReceive
    private var ref = 0

    override suspend fun receive(): Frame {
        return receiveChannel.receive()
    }

    override suspend fun send(message: RealtimeMessage, vsn: RealtimeProtocolVersion) {
        when(vsn) {
            RealtimeProtocolVersion.V1 -> sendChannel.send(Frame.Text(stitchdbJson.encodeToString(message)))
            RealtimeProtocolVersion.V2 -> sendChannel.send(Frame.Text(message.encodeV2Text()))
        }
    }

    override suspend fun send(data: ByteArray) {
        sendChannel.send(Frame.Binary(false, data))
    }

    override fun makeRef(): String {
        return (ref++).toString()
    }

    override suspend fun blockUntilDisconnect() {
        TODO()
    }

    override fun disconnect() {
        sendChannel.close()
        receiveChannel.cancel()
    }

}

suspend fun createTestClient(
    wsHandler: suspend (incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit,
    stitchdbHandler: suspend (StitchClient) -> Unit,
    realtimeConfig: Realtime.Config.() -> Unit = {},
    stitchdbConfig: StitchClientBuilder.() -> Unit = {},
    mockEngineHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("") }
) {
    val serverChannel = Channel<Frame>()
    val clientChannel = Channel<Frame>()
    val stitchdb = createStitchClient("", "") {
        defaultLogLevel = LogLevel.DEBUG
        httpEngine = MockEngine(mockEngineHandler)
        install(Realtime) {
            websocketFactory = MockWSFactory(serverChannel, clientChannel)
            heartbeatInterval = 1.hours
            disconnectOnNoSubscriptions = false
            realtimeConfig()
        }
        stitchdbConfig()
    }
    try {
        coroutineScope {
            launch {
                stitchdbHandler(stitchdb)
            }
            launch {
                wsHandler(serverChannel, clientChannel)
            }.join()
        }
    } finally {
        stitchdb.close()
        serverChannel.close()
        serverChannel.cancel()
        clientChannel.close()
        clientChannel.cancel()
    }
}