package com.stitchdb

import com.stitchdb.annotations.StitchDsl
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.logging.KermitLoggingProcessor
import com.stitchdb.logging.LogLevel
import com.stitchdb.logging.StitchLoggingProcessor
import com.stitchdb.plugins.PluginManager
import com.stitchdb.plugins.StitchPlugin
import com.stitchdb.plugins.StitchPluginProvider
import com.stitchdb.serializer.KotlinXSerializer
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal typealias PluginProvider = (StitchClient) -> StitchPlugin<*>
internal typealias HttpConfigOverride = HttpClientConfig<*>.() -> Unit
typealias StitchLoggingProcessorFactory = (level: LogLevel) -> StitchLoggingProcessor

/**
 * Creates a new [StitchClient] with the given options.
 *
 * Use [createStitchClient] to create a new instance of [StitchClient].
 */
@StitchDsl
class StitchClientBuilder @PublishedApi internal constructor(
    private val stitchdbUrl: String,
    private val stitchdbKey: String
) {

    /**
     * Whether to use HTTPS for network requests with the [stitchdbUrl]
     *
     * Default: true
     */
    var useHTTPS = true

    /**
     * Custom ktor http client engine. If null, an engine from the dependencies will be used.
     */
    var httpEngine: HttpClientEngine? = null

    /**
     * Whether to ignore if [stitchdbUrl] contains modules like 'realtime' or 'auth'. If false, an exception will be thrown.
     *
     * Default: false
     */
    var ignoreModulesInUrl = false

    /**
     * [Duration] after network requests throw a [HttpRequestTimeoutException]
     *
     * Default: 10 seconds
     */
    var requestTimeout = 10.seconds

    /**
     * The default log level used for plugins.
     *
     * Can be overridden by the plugin's config
     */
    var defaultLogLevel: LogLevel = LogLevel.INFO

    /**
     * The default logging factory. Used for creating a [StitchLoggingProcessor] using a specified [LogLevel]
     */
    var defaultLoggingFactory: StitchLoggingProcessorFactory = { level -> KermitLoggingProcessor(level) }

    /**
     * The default serializer used to serialize and deserialize custom data types.
     *
     * Default: [KotlinXSerializer]
     */
    var defaultSerializer: StitchSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })

    /**
     * The CoroutineDispatcher used for launching long running jobs.
     *
     * Default: [defaultDispatcher], the IO Dispatcher on supported targets.
     */
    var coroutineDispatcher: CoroutineDispatcher = defaultDispatcher

    /**
     * Optional function for using a third-party authentication system with
     * StitchDB. The function should return an access token or ID token (JWT) by
     * obtaining it from the third-party auth client library. Note that this
     * function may be called concurrently and many times. Use memoization and
     * locking techniques if this is not supported by the client libraries.
     *
     * When set, the Auth plugin from `auth-kt` cannot be used.
     * Create another client if you wish to use StitchDB Auth and third-party
     * authentications concurrently in the same application.
     */
    var accessToken: AccessTokenProvider? = null

    /**
     * The current operating system information.
     */
    var osInformation: OSInformation? = OSInformation.CURRENT
    private val httpConfigOverrides = mutableListOf<HttpConfigOverride>()
    private val plugins = mutableMapOf<String, PluginProvider>()

    init {
        val module = when {
            stitchdbUrl.contains("realtime/v1") -> "realtime/v1"
            stitchdbUrl.contains("auth/v1") -> "auth/v1"
            stitchdbUrl.contains("storage/v1") -> "storage/v1"
            stitchdbUrl.contains("rest/v1") -> "rest/v1"
            else -> null
        }
        if(!ignoreModulesInUrl && module != null) {
            error("The StitchDB URL should not contain ($module), stitchdb-kt handles the url endpoints. If you want to use a custom url for a module, specify it within their builder but that's not necessary for normal StitchDB projects")
        }
        if(stitchdbUrl.startsWith("http://")) {
            useHTTPS = false
        }
    }

    @PublishedApi
    internal fun build(): StitchClient {
        val config = StitchClientConfig(
            stitchdbUrl = stitchdbUrl.split("//").last(),
            stitchdbKey = stitchdbKey,
            loggingConfig = StitchLoggingConfig(
                defaultLogLevel,
                defaultLoggingFactory
            ),
            networkConfig = StitchNetworkConfig(
                useHTTPS = useHTTPS,
                httpEngine = httpEngine,
                httpConfigOverrides = httpConfigOverrides,
                requestTimeout = requestTimeout
            ),
            defaultSerializer = defaultSerializer,
            coroutineDispatcher = coroutineDispatcher,
            accessToken = accessToken,
            plugins = plugins,
            osInformation = osInformation
        )
        return StitchClientImpl(
            config
        )
    }

    /**
     * Add your own http configuration to [StitchClient.httpClient]
     *
     * **Warning:** This is an internal function and should only be used if you know what you are doing. You don't have to specify the HTTP client engine, it will be automatically set by Ktor.
     */
    @StitchInternal
    @StitchDsl
    fun httpConfig(block: @StitchDsl HttpClientConfig<*>.() -> Unit) {
        httpConfigOverrides.add(block)
    }

    /**
     * Installs a plugin to the [StitchClient]
     *
     * Plugins can be either retrieved by calling [PluginManager.getPlugin] within your [StitchClient] instance or by using the corresponding **StitchClient.plugin** extension property.
     */
    @StitchDsl
    fun <Config, PluginInstance : StitchPlugin<Config>, Provider : StitchPluginProvider<Config, PluginInstance>> install(plugin: Provider, init: @StitchDsl Config.() -> Unit = {}) {
        val config = plugin.createConfig(init)
        plugin.setup(this, config)
        plugins[plugin.key] = {
            plugin.create(it, config)
        }
    }

}

/**
 * Creates a new [StitchClient] instance using [builder]
 * @param stitchdbUrl The stitchdb url. Example: '<project-ref>.p.stitchdb.com' or 'https://<project-ref>.p.stitchdb.com' (no 'https://<project-ref>.p.stitchdb.com/auth/v1')
 * @param stitchdbKey The stitchdb api key
 */
inline fun createStitchClient(stitchdbUrl: String, stitchdbKey: String, builder: StitchClientBuilder.() -> Unit) = StitchClientBuilder(stitchdbUrl, stitchdbKey).apply(builder).build()