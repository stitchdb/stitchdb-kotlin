package com.stitchdb

import com.stitchdb.logging.LogLevel
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Duration

data class StitchClientConfig(
    val stitchdbUrl: String,
    val stitchdbKey: String,
    val loggingConfig: StitchLoggingConfig,
    val networkConfig: StitchNetworkConfig,
    val defaultSerializer: StitchSerializer,
    val coroutineDispatcher: CoroutineDispatcher,
    val accessToken: AccessTokenProvider?,
    val plugins: Map<String, PluginProvider>,
    val osInformation: OSInformation?
)

data class StitchLoggingConfig(
    val defaultLogLevel: LogLevel,
    val defaultLoggingFactory: StitchLoggingProcessorFactory
)

data class StitchNetworkConfig(
    val useHTTPS: Boolean,
    val httpEngine: HttpClientEngine?,
    val httpConfigOverrides: List<HttpConfigOverride>,
    val requestTimeout: Duration
)
