package com.stitchdb.functions

import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder
import com.stitchdb.StitchSerializer
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.Auth
import com.stitchdb.auth.AuthDependentPluginConfig
import com.stitchdb.auth.api.authenticatedStitchApi
import com.stitchdb.encode
import com.stitchdb.exceptions.BadRequestRestException
import com.stitchdb.exceptions.HttpRequestException
import com.stitchdb.exceptions.NotFoundRestException
import com.stitchdb.exceptions.RestException
import com.stitchdb.exceptions.UnauthorizedRestException
import com.stitchdb.exceptions.UnknownRestException
import com.stitchdb.logging.StitchLogger
import com.stitchdb.logging.createLogger
import com.stitchdb.plugins.CustomSerializationConfig
import com.stitchdb.plugins.CustomSerializationPlugin
import com.stitchdb.plugins.MainConfig
import com.stitchdb.plugins.MainPlugin
import com.stitchdb.plugins.StitchPluginProvider
import com.stitchdb.serializer.KotlinXSerializer
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Plugin to interact with the stitchdb Edge Functions API
 *
 * To use it you need to install it to the [StitchClient]:
 * ```kotlin
 * val stitchdb = createStitchClient(stitchdbUrl, stitchdbKey) {
 *    install(Functions)
 * }
 * ```
 *
 * then you can use it like this:
 * ```kotlin
 * val response = stitchdb.functions("myFunction")
 * //or store it in a variable
 * val function = stitchdb.functions.buildEdgeFunction("myFunction")
 * val response = function()
 * ```
 */
class Functions(override val config: Config, override val stitchdbClient: StitchClient) : MainPlugin<Functions.Config>, CustomSerializationPlugin {

    override val logger: StitchLogger = stitchdbClient.createLogger(Functions.LOGGING_TAG, config)
    override val apiVersion: Int
        get() = API_VERSION

    override val pluginKey: String
        get() = key

    override val serializer = config.serializer ?: stitchdbClient.defaultSerializer

    @OptIn(StitchInternal::class)
    @PublishedApi
    internal val api = stitchdbClient.authenticatedStitchApi(this) {
        useNewApiKeyAsFallback = false
    }

    /**
     * Invokes a remote edge function. The authorization token is automatically added to the request.
     * @param function The function to invoke. If name of the function is renamed, use the slug after URL
     * @param builder The request builder to configure the request
     * @param region The region where the function is invoked
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend inline operator fun invoke(function: String, region: FunctionRegion = config.defaultRegion, crossinline builder: HttpRequestBuilder.() -> Unit): HttpResponse {
        return api.post(function) {
            builder()
            header(X_REGION_HEADER, region.value)
        }
    }

    /**
     * Invokes a remote edge function. The authorization token is automatically added to the request.
     * Note, if you want to serialize [body] to json, you need to add the [HttpHeaders.ContentType] header yourself.
     * @param function The function to invoke. If name of the function is renamed, use the slug after URL
     * @param body The body of the request
     * @param headers Headers to add to the request
     * @param region The region where the function is invoked
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend inline operator fun <reified T : Any> invoke(function: String, body: T, region: FunctionRegion = config.defaultRegion, headers: Headers = Headers.Empty): HttpResponse = invoke(function) {
        this.headers.appendAll(headers)
        header(X_REGION_HEADER, region.value)
        setBody(serializer.encode(body))
    }

    /**
     * Invokes a remote edge function. The authorization token is automatically added to the request.
     * @param function The function to invoke. If name of the function is renamed, use the slug after URL
     * @param headers Headers to add to the request
     * @param region The region where the function is invoked
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend inline operator fun invoke(function: String, region: FunctionRegion = config.defaultRegion, headers: Headers = Headers.Empty): HttpResponse = invoke(function) {
        this.headers.appendAll(headers)
        header(X_REGION_HEADER, region.value)
    }

    /**
     * Invokes a remote edge function that returns Server-Sent Events (SSE) and returns
     * a [Flow] of [FunctionServerSentEvent]. Each event provides access to [data][FunctionServerSentEvent.data],
     * [event][FunctionServerSentEvent.event], and [id][FunctionServerSentEvent.id] fields, as well as
     * a [decodeAs][FunctionServerSentEvent.decodeAs] method for deserializing the data payload.
     *
     * The authorization token is automatically added to the request.
     * Ktor's SSE plugin requires direct [io.ktor.client.HttpClient] access, so this method uses the raw client
     * with [api.getDefaultHeaders] to include the authentication token (base headers like apikey
     * are already applied by Ktor's DefaultRequest plugin).
     * @param function The function to invoke. If name of the function is renamed, use the slug after URL
     * @param region The region where the function is invoked
     * @param builder The request builder to configure the request
     * @return A [Flow] of [FunctionServerSentEvent] representing the SSE stream
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    @OptIn(StitchInternal::class)
    fun invokeSSE(
        function: String,
        region: FunctionRegion = config.defaultRegion,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): Flow<FunctionServerSentEvent> = flow {
        val defaultHeaders = api.getDefaultHeaders()
        val resolvedUrl = resolveUrl(function)
        val ktorClient = stitchdbClient.httpClient.httpClient
        ktorClient.sse(
            urlString = resolvedUrl,
            request = {
                method = HttpMethod.Post
                headers.appendAll(defaultHeaders)
                header(X_REGION_HEADER, region.value)
                builder()
            }
        ) {
            incoming.collect { event ->
                emit(FunctionServerSentEvent(
                    data = event.data,
                    event = event.event,
                    id = event.id,
                    serializer = this@Functions.serializer
                ))
            }
        }
    }

    /**
     * Prepares an invocation of a remote edge function, returning an [HttpStatement] for streaming
     * or other advanced response handling. The authorization token is automatically added to the request.
     * @param function The function to invoke. If name of the function is renamed, use the slug after URL
     * @param region The region where the function is invoked
     * @param builder The request builder to configure the request
     */
    suspend inline fun prepareInvoke(function: String, region: FunctionRegion = config.defaultRegion, crossinline builder: HttpRequestBuilder.() -> Unit = {}): HttpStatement {
        return api.prepareRequest(function) {
            method = HttpMethod.Post
            builder()
            header(X_REGION_HEADER, region.value)
        }
    }

    /**
     * Builds an [EdgeFunction] which can be invoked multiple times
     * @param function The function name. If name of the function is renamed, use the slug after URL
     * @param headers Headers to add to the requests when invoking the function
     * @param region The region where the function is invoked
     */
    @OptIn(StitchInternal::class)
    fun buildEdgeFunction(
        function: String,
        region: FunctionRegion = config.defaultRegion,
        headers: Headers = Headers.Empty
    ) = EdgeFunction(
        functionName = function,
        headers = Headers.build {
            appendAll(headers)
            append(X_REGION_HEADER, region.value)
        },
        stitchdbClient = stitchdbClient
    )

    override suspend fun parseErrorResponse(response: HttpResponse): RestException {
        val error = response.bodyAsText()
        return when(response.status) {
            HttpStatusCode.Unauthorized -> UnauthorizedRestException(error, response)
            HttpStatusCode.NotFound -> NotFoundRestException(error, response)
            HttpStatusCode.BadRequest -> BadRequestRestException(error, response)
            else -> UnknownRestException(error, response)
        }
    }

    /**
     * The config for the [Functions] plugin
     * @param customUrl A custom url to use for the requests. If not provided, the default url will be used
     * @param jwtToken A jwt token to use for the requests. If not provided, the token from the [Auth] plugin, or the stitchdbKey will be used
     * @property serializer A serializer used for serializing/deserializing objects e.g. in [Functions.invoke] or [EdgeFunction.invoke]. Defaults to [KotlinXSerializer]
     */
    class Config : MainConfig(), CustomSerializationConfig, AuthDependentPluginConfig {

        override var serializer: StitchSerializer? = null

        /**
         * The default region to use when invoking a function
         */
        var defaultRegion: FunctionRegion = FunctionRegion.ANY

        override var requireValidSession: Boolean = false

    }

    /**
     * @see Functions
     */
    companion object : StitchPluginProvider<Config, Functions> {

        override val key = "functions"

        /**
         * The tag for the Functions logger.
         */
        const val LOGGING_TAG = "StitchDB-Functions"

        /**
         * The header for specifying which region the function should be called in
         */
        const val X_REGION_HEADER = "x-region"

        /**
         * The current functions api version
         */
        const val API_VERSION = 1

        override fun create(stitchdbClient: StitchClient, config: Config): Functions {
            return Functions(config, stitchdbClient)
        }

        override fun createConfig(init: Config.() -> Unit) = Config().apply(init)

        override fun setup(builder: StitchClientBuilder, config: Config) {
            builder.httpConfig {
                install(SSE)
            }
        }

    }

}

/**
 * The Functions plugin handles everything related to stitchdb's edge functions
 */
val StitchClient.functions: Functions
    get() = pluginManager.getPlugin(Functions)

