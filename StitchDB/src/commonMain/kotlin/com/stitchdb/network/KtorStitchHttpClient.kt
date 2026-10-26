@file:Suppress("UndocumentedPublicFunction")
package com.stitchdb.network

import com.stitchdb.BuildConfig
import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.collections.update
import com.stitchdb.exceptions.HttpRequestException
import com.stitchdb.logging.d
import com.stitchdb.stitchdbJson
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

private const val HTTPS_PORT = 443
private const val HTTP_ERROR = 400

/**
 * A function that can be used to override the default request configuration
 */
typealias HttpRequestOverride = HttpRequestBuilder.() -> Unit

/**
 * A [StitchHttpClient] that uses ktor to send requests
 */
@OptIn(StitchInternal::class)
class KtorStitchHttpClient @StitchInternal constructor(
    private val stitchdb: StitchClient
): StitchHttpClient() {
    
    val logger = stitchdb.logger.appendTag(" [Network]")
    private val stitchdbKey = stitchdb.stitchdbKey
    private val osInformation = stitchdb.config.osInformation

    private val networkConfig = stitchdb.config.networkConfig
    private val requestTimeout = networkConfig.requestTimeout
    private val engine = networkConfig.httpEngine
    private val modifiers = networkConfig.httpConfigOverrides

    private val apiCallListeners = AtomicReference<List<ApiCallListener>>(emptyList())

    internal fun addApiCallListener(listener: ApiCallListener) {
        apiCallListeners.update { it + listener }
    }

    internal fun removeApiCallListener(listener: ApiCallListener) {
        apiCallListeners.update { list -> list.filterNot { it === listener } }
    }

    override suspend fun getDefaultHeaders(): Headers {
        return io.ktor.http.headers {
            defaultHeaders()
        }
    }

    init {
        logger.d { "Creating KtorStitchHttpClient with request timeout $requestTimeout, HttpClientEngine: $engine" }
    }

    @StitchInternal
    val httpClient =
        if(engine != null) HttpClient(engine) { applyDefaultConfiguration(modifiers) }
        else HttpClient { applyDefaultConfiguration(modifiers) }

    override suspend fun request(url: String, builder: HttpRequestBuilder.() -> Unit): HttpResponse {
        val request = HttpRequestBuilder().apply {
            url(url)
            builder()
        }
        val endPoint = request.url.encodedPath
        logger.d { "Starting ${request.method.value} request to endpoint $endPoint" }
        val started = TimeSource.Monotonic.markNow()
        val response = try {
            httpClient.request(url, builder)
        } catch(e: HttpRequestTimeoutException) {
            logger.d(e) { "${request.method.value} request to endpoint $endPoint timed out after $requestTimeout" }
            throw e
        } catch(e: CancellationException) {
            logger.d(e) { "${request.method.value} request to endpoint $endPoint was cancelled"}
            throw e
        } catch(e: Exception) {
            logger.d(e) { "${request.method.value} request to endpoint $endPoint failed with exception ${e.message}" }
            notifyApiCall(request, 0, null, e.message ?: e.toString(), started)
            throw HttpRequestException(e.message ?: "", request)
        }
        val responseTime = (response.responseTime.timestamp - response.requestTime.timestamp).milliseconds
        logger.d { "${request.method.value} request to endpoint $endPoint successfully finished in $responseTime" }
        notifyApiCall(request, response.status.value, response, null, started)
        return response
    }

    private suspend fun notifyApiCall(
        request: HttpRequestBuilder,
        status: Int,
        response: HttpResponse?,
        error: String?,
        started: TimeSource.Monotonic.ValueTimeMark,
    ) {
        val listeners = apiCallListeners.load()
        if (listeners.isEmpty()) return
        val errorCode = if (response != null && status >= HTTP_ERROR) errorCodeOf(response) else null
        val ms = started.elapsedNow().inWholeMilliseconds
        val url = request.url.build()
        listeners.forEach {
            try {
                it.onApiCall(request.method.value, url, status, errorCode, error, ms)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.d(e) { "An api call listener failed" }
            }
        }
    }

    // The body of a finished request is kept in memory, so it can be read again here.
    @Suppress("SwallowedException")
    private suspend fun errorCodeOf(response: HttpResponse): String? = try {
        val json = stitchdbJson.parseToJsonElement(response.bodyAsText()) as? JsonObject
        (json?.get("code") ?: json?.get("error_code"))?.let { (it as? JsonPrimitive)?.content ?: it.toString() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun prepareRequest(
        url: String,
        builder: HttpRequestBuilder.() -> Unit
    ): HttpStatement {
        val request = HttpRequestBuilder().apply {
            url(url)
            builder()
        }
        val response = try {
            httpClient.prepareRequest(url, builder)
        } catch(e: HttpRequestTimeoutException) {
            logger.d(e) { "Request timed out after $requestTimeout on url $url" }
            throw e
        } catch(e: CancellationException) {
            logger.d(e) { "Request was cancelled on url $url" }
            throw e
        } catch(e: Exception) {
            logger.d(e) { "Request failed with ${e.message} on url $url" }
            throw HttpRequestException(e.message ?: "", request)
        }
        return response
    }

    fun close() = httpClient.close()

    private fun HeadersBuilder.defaultHeaders() {
        if(stitchdbKey.isNotBlank()) {
            append("apikey", stitchdbKey)
        }
        val metadata = buildMap {
            osInformation?.let { os ->
                put("platform", os.name)
                os.version?.let { version ->
                    put("platform-version", version)
                }
            }
            put("runtime", "kotlin")
            put("runtime-version", KotlinVersion.CURRENT.toString())
        }
        append("X-Client-Info", "stitchdb-kt/${BuildConfig.PROJECT_VERSION}" + metadata.toList().joinToString("; ", prefix = "; ") { "${it.first}=${it.second}" })
    }

    private fun HttpClientConfig<*>.applyDefaultConfiguration(modifiers: List<HttpClientConfig<*>.() -> Unit>) {
        install(DefaultRequest) {
            headers {
                defaultHeaders()
            }
            port = HTTPS_PORT
        }
        install(ContentNegotiation) {
            json(stitchdbJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
        }
        modifiers.forEach { it.invoke(this) }
    }

}