package com.stitchdb

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.analytics.AnalyticsClient
import com.stitchdb.crashes.CrashesClient
import com.stitchdb.links.LinksClient
import com.stitchdb.logging.StitchLogger
import com.stitchdb.logging.createLogger
import com.stitchdb.logging.i
import com.stitchdb.logging.w
import com.stitchdb.network.KtorStitchHttpClient
import com.stitchdb.plugins.PluginManager
import com.stitchdb.plugins.StitchPlugin
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The main class to interact with StitchDB.
 *
 * To add functionality, add plugins like **Auth** or **Functions** within the [StitchClientBuilder]
 */
interface StitchClient {

    /**
     * The configuration for the StitchDB Client.
     */
    val config: StitchClientConfig

    /**
     * The stitchdb url with either a http or https scheme.
     */
    val stitchdbHttpUrl: String

    /**
     * The base stitchdb url without any scheme
     */
    val stitchdbUrl: String

    /**
     * The api key for interacting with the stitchdb api
     */
    val stitchdbKey: String

    /**
     * The plugin manager is used to manage installed plugins
     */
    val pluginManager: PluginManager

    /**
     * The http client used to interact with the StitchDB api
     */
    val httpClient: KtorStitchHttpClient

    /**
     * Whether [stitchdbHttpUrl] uses https
     */
    val useHTTPS: Boolean

    /**
     * The default serializer used to serialize and deserialize custom data types.
     */
    val defaultSerializer: StitchSerializer

    /**
     * The custom access token provider used to provide custom access tokens for requests. Configured within the [StitchClientBuilder]
     */
    @StitchInternal
    val accessToken: AccessTokenProvider?

    /**
     * The default dispatcher used for launching long running jobs
     */
    @StitchInternal
    val coroutineDispatcher: CoroutineDispatcher

    val logger: StitchLogger

    /**
     * Crash and error reporting for this client: nothing is captured or sent until [CrashesClient.start].
     */
    val crashes: CrashesClient

    /**
     * Product analytics for this client: nothing is sent until [AnalyticsClient.start].
     */
    val analytics: AnalyticsClient

    /**
     * Deep links and short links (Studio › Links): resolve the link the app was opened with, the deferred link after an
     * install, and make links.
     */
    val links: LinksClient

    /**
     * Releases all resources held by the [httpClient] and all plugins the [pluginManager]
     */
    suspend fun close()

    companion object {

        /**
         * The tag for the StitchDB logger.
         */
        const val LOGGING_TAG = "StitchDB-Core"

        internal const val TEMP_KEY_PREFIX = "sdb_temp_"

        @StitchInternal
        fun checkIsNewApiKey(key: String) = key.startsWith("sdb_publishable_") || key.startsWith("sdb_secret_")

    }

}

internal class StitchClientImpl(
    override val config: StitchClientConfig,
) : StitchClient {

    override val accessToken: AccessTokenProvider? = config.accessToken
    override val defaultSerializer: StitchSerializer = config.defaultSerializer
    override val stitchdbUrl: String = config.stitchdbUrl
    override val stitchdbKey: String = config.stitchdbKey
    override val useHTTPS: Boolean = config.networkConfig.useHTTPS
    override val coroutineDispatcher: CoroutineDispatcher = config.coroutineDispatcher
    override val logger: StitchLogger = createLogger(
        StitchClient.LOGGING_TAG,
        config.loggingConfig.defaultLogLevel,
        config.loggingConfig.defaultLoggingFactory
    )

    init {
        logger.i {
            "StitchClient created! Please report any bugs you find."
        }
        try {
            getOSInformation()
        } catch(e: Exception) {
            logger.i(e) {
                "Failed to get OS information. If this is not intentional, please report this issue."
            }
        }
        checkApiKeyFormat(config.stitchdbKey)
    }

    /**
     * Warn (once per subtype) when an `sdb_` key isn't a subtype this SDK version recognizes.
     * Never throws — the server, not the SDK, decides key validity. The key value is never
     * included in the message.
     */
    private fun checkApiKeyFormat(stitchdbKey: String) {
        if(!stitchdbKey.startsWith("sdb_") || StitchClient.checkIsNewApiKey(stitchdbKey) || stitchdbKey.startsWith(
                StitchClient.TEMP_KEY_PREFIX)) {
            return
        }
        logger.w {
            """
                Unrecognized StitchDB API key format. 
                The client will proceed and send this key as-is; 
                if you see authentication errors you may need to upgrade stitchdb-kt to a version that recognizes this key type.
            """.trimIndent()
        }
    }

    override val stitchdbHttpUrl: String = if (useHTTPS) {
        "https://$stitchdbUrl"
    } else {
        "http://$stitchdbUrl"
    }

    @OptIn(StitchInternal::class)
    override val httpClient = KtorStitchHttpClient(
        this
    )

    override val pluginManager = PluginManager(config.plugins.toList().associate { (key, value) ->
        key to value(this)
    }, logger.appendTag(" [PluginManager]"))

    init {
        pluginManager.installedPlugins.values.forEach(StitchPlugin<*>::init)
    }

    private val crashesDelegate = lazy { CrashesClient(this) }
    override val crashes: CrashesClient by crashesDelegate

    private val analyticsDelegate = lazy { AnalyticsClient(this) }
    override val analytics: AnalyticsClient by analyticsDelegate

    override val links: LinksClient by lazy { LinksClient(this) }

    override suspend fun close() {
        logger.i { "Closing StitchClient" }
        if (crashesDelegate.isInitialized()) crashes.stop()
        if (analyticsDelegate.isInitialized()) analytics.stop()
        httpClient.close()
        pluginManager.closeAllPlugins()
    }

}

