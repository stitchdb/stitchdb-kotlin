package com.stitchdb.realtime

import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder
import com.stitchdb.StitchSerializer
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.AuthDependentPluginConfig
import com.stitchdb.auth.resolveAccessToken
import com.stitchdb.plugins.CustomSerializationConfig
import com.stitchdb.plugins.CustomSerializationPlugin
import com.stitchdb.plugins.MainConfig
import com.stitchdb.plugins.MainPlugin
import com.stitchdb.plugins.StitchPluginProvider
import com.stitchdb.realtime.websocket.RealtimeWebsocket
import com.stitchdb.realtime.websocket.RealtimeWebsocketFactory
import com.stitchdb.serializer.KotlinXSerializer
import com.stitchdb.stitchdbJson
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

/**
 * Plugin for interacting with the stitchdb realtime api
 *
 * To use it you need to install it to the [StitchClient]:
 * ```kotlin
 * val stitchdb = createStitchClient(stitchdbUrl, stitchdbKey) {
 *    install(Realtime)
 * }
 * ```
 *
 * You can then create a channel:
 * ```kotlin
 * val channel = stitchdb.realtime.channel("channelId")
 * ```
 * Then listen to events on the channel:
 * ```kotlin
 * val productChangeFlow = channel.postgrestChangeFlow<PostgrestAction.Insert>(schema = "public") {
 *    table = "products"
 * }.map { it.decodeRecord<Product>() }
 * ```
 * And at last you have to subscribe to the channel:
 * ```kotlin
 * channel.subscribe()
 * ```
 */
interface Realtime : MainPlugin<Realtime.Config>, CustomSerializationPlugin {

    /**
     * The current status of the realtime connection
     */
    val status: StateFlow<Status>

    /**
     * A map of all active the subscriptions
     */
    val subscriptions: Map<String, RealtimeChannel>

    @StitchInternal
    val websocket: RealtimeWebsocket

    /**
     * Connects to the realtime websocket. The url will be taken from the custom provided [Realtime.Config.customUrl] or [StitchClient]
     */
    suspend fun connect()

    /**
     * Disconnects from the realtime websocket
     */
    fun disconnect()

    /**
     * Unsubscribes and removes a channel from the [subscriptions]
     * @param channel The channel to remove
     */
    suspend fun removeChannel(channel: RealtimeChannel)

    /**
     * Adds a channel to the [subscriptions] without subscribing to it.
     */
    @StitchInternal
    fun addChannel(channel: RealtimeChannel)

    /**
     * Unsubscribes and removes all channels from the [subscriptions]
     */
    suspend fun removeAllChannels()

    /**
     * Blocks your current coroutine until the websocket connection is closed
     */
    suspend fun block()

    /**
     * Sends a message to the realtime websocket
     * @param message The message to send
     */
    @StitchInternal
    suspend fun send(message: RealtimeMessage)

    /**
     * Sends a binary payload to the realtime websocket
     * @param data The data to send
     */
    @StitchInternal
    suspend fun send(data: ByteArray)

    /**
     * Sets the JWT access token used for channel subscription authorization and Realtime RLS.
     *
     * If [token] is null, the token will be resolved using the [Realtime.Config.accessToken] provider.
     *
     * @param token The JWT access token
     */
    suspend fun setAuth(token: String? = null)

    /**
     * Creates a new [RealtimeChannel] and adds it to the [subscriptions]
     *
     * - This method does not subscribe to the channel. You have to call [RealtimeChannel.subscribe] to do so.
     * - If a channel with the same [channelId] already exists, it will be returned
     *
     * @param channelId The id of the channel
     * @param builder The builder for the channel
     */
    fun channel(channelId: String, builder: RealtimeChannelBuilder): RealtimeChannel

    /**
     * @property websocketConfig Custom configuration for the Ktor Websocket Client. This only applies if [Realtime.Config.websocketFactory] is null.
     * @property secure Whether to use wss or ws. Defaults to [StitchClient.useHTTPS] when null
     * @property disconnectOnSessionLoss Whether to disconnect from the websocket when the session is lost. Defaults to true
     * @property reconnectDelay The delay between reconnect attempts. Defaults to 7 seconds
     * @property heartbeatInterval The interval between heartbeat messages. Defaults to 15 seconds
     * @property connectOnSubscribe Whether to connect to the websocket when subscribing to a channel. Defaults to true
     * @property disconnectOnNoSubscriptions Whether to disconnect from the websocket when there are no more subscriptions. Defaults to true
     * @property serializer A serializer used for serializing/deserializing objects e.g. in [PresenceAction.decodeJoinsAs] or [RealtimeChannel.broadcast]. Defaults to [KotlinXSerializer]
     * @property websocketFactory A custom websocket factory. If this is set, the [websocketConfig] will be ignored
     * @property rejoinDelay The interval between channel rejoin attempts
     * @property maxAttempts The maximum number of times a channel will try to rejoin after an error before giving up. Defaults to 5
     * @property disconnectOnEmptyChannelsAfter Delay before disconnecting from the realtime socket after the last channel was removed. If null, it defaults to `2*heartbeatInterval`
     * @property vsn The realtime protocol version. [RealtimeProtocolVersion.V2] supports binary payloads and is more efficient.
     */
    @Suppress("MagicNumber")
    class Config: MainConfig(), CustomSerializationConfig, AuthDependentPluginConfig {

        var websocketConfig: WebSockets.Config.() -> Unit = {}
        var secure: Boolean? = null
        var heartbeatInterval: Duration = 15.seconds
        var reconnectDelay: Duration = 7.seconds
        var rejoinDelay: Duration = 2.seconds
        var disconnectOnEmptyChannelsAfter: Duration? = null
        var maxAttempts: Int = 5
        var disconnectOnSessionLoss: Boolean = true
        var connectOnSubscribe: Boolean = true
        @StitchInternal var websocketFactory: RealtimeWebsocketFactory? = null
        var disconnectOnNoSubscriptions: Boolean = true
        var vsn = RealtimeProtocolVersion.V2
        override var requireValidSession: Boolean = true

        internal var customAccessTokenProvider = false
        internal var coroutineScope: CoroutineScope? = null

        internal val disconnectDelay by lazy {
            disconnectOnEmptyChannelsAfter ?: (2 * heartbeatInterval)
        }

        /**
         * A custom access token provider. If this is set, the [StitchClient] will not be used to resolve the access token.
         */
        var accessToken: suspend StitchClient.() -> String? = { resolveAccessToken(realtime, keyAsFallback = false) }
            set(value) {
                field = value
                customAccessTokenProvider = true
            }
        override var serializer: StitchSerializer? = null

    }

    companion object : StitchPluginProvider<Config, Realtime> {

        override val key = "realtime"

        /**
         * The tag for the Realtime logger.
         */
        const val LOGGING_TAG = "StitchDB-Realtime"

        /**
         * The current realtime api version
         */
        const val API_VERSION = 1

        override fun createConfig(init: Config.() -> Unit) = Config().apply(init)
        override fun setup(builder: StitchClientBuilder, config: Config) {
            if(config.websocketFactory == null) {
                builder.httpConfig {
                    install(WebSockets) {
                        contentConverter = KotlinxWebsocketSerializationConverter(stitchdbJson)
                        config.websocketConfig(this)
                    }
                }
            }
        }

        override fun create(stitchdbClient: StitchClient, config: Config): Realtime = RealtimeImpl(stitchdbClient, config)

    }

    /**
     * The current status of the realtime connection
     */
    enum class Status {
        /**
         * [Realtime] is disconnected from StitchDB Realtime
         */
        DISCONNECTED,

        /**
         * [Realtime] is connecting to StitchDB Realtime
         */
        CONNECTING,

        /**
         * [Realtime] is connected to StitchDB Realtime
         */
        CONNECTED,
    }

}

/**
 * Creates a new [RealtimeChannel] and adds it to the [Realtime.subscriptions]
 *
 * - This method does not subscribe to the channel. You have to call [RealtimeChannel.subscribe] to do so.
 * - If a channel with the same [channelId] already exists, it will be returned
 *
 * @param channelId The id of the channel
 * @param builder The builder for the channel
 */
inline fun Realtime.channel(channelId: String, builder: RealtimeChannelBuilder.() -> Unit = {}): RealtimeChannel = channel(channelId, RealtimeChannelBuilder(RealtimeTopic.withChannelId(channelId)).apply(builder))


/**
 * Creates a new [RealtimeChannel]
 */
inline fun StitchClient.channel(channelId: String, builder: RealtimeChannelBuilder.() -> Unit = {}): RealtimeChannel = realtime.channel(channelId, builder)

/**
 * StitchDB Realtime is a way to listen to changes in the PostgreSQL database via websockets
 */
val StitchClient.realtime: Realtime
    get() = pluginManager.getPlugin(Realtime)