package com.stitchdb.postgrest

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.api.authenticatedStitchApi
import com.stitchdb.bodyOrNull
import com.stitchdb.exceptions.RestException
import com.stitchdb.logging.StitchLogger
import com.stitchdb.logging.createLogger
import com.stitchdb.postgrest.exception.PostgrestRestException
import com.stitchdb.postgrest.executor.RestRequestExecutor
import com.stitchdb.postgrest.query.PostgrestQueryBuilder
import com.stitchdb.postgrest.query.request.RpcRequestBuilder
import com.stitchdb.postgrest.result.PostgrestResult
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.JsonObject

internal class PostgrestImpl(override val stitchdbClient: StitchClient, override val config: Postgrest.Config) : Postgrest {

    override val logger: StitchLogger = stitchdbClient.createLogger(Postgrest.LOGGING_TAG, config)
    override val apiVersion: Int
        get() = Postgrest.API_VERSION

    override val pluginKey: String
        get() = Postgrest.key

    override var serializer = config.serializer ?: stitchdbClient.defaultSerializer

    @OptIn(StitchInternal::class)
    val api = stitchdbClient.authenticatedStitchApi(this, urlLengthLimit = config.urlLengthLimit).withDefaultRequest {
        timeout {
            this.requestTimeoutMillis = config.timeout.inWholeMilliseconds
        }
    }

    override fun from(table: String): PostgrestQueryBuilder {
        return PostgrestQueryBuilder(
            postgrest = this,
            table = table,
        )
    }

    override fun from(schema: String, table: String): PostgrestQueryBuilder {
        return PostgrestQueryBuilder(
            postgrest = this,
            table = table,
            schema = schema,
        )
    }

    override suspend fun parseErrorResponse(response: HttpResponse): RestException {
        val body = stitchdbClient.bodyOrNull<PostgrestErrorResponse>(response) ?: PostgrestErrorResponse("Unknown error")
        return PostgrestRestException(body.message, body.hint, body.details, body.code, response)
    }

    override suspend fun rpc(
        function: String,
        parameters: JsonObject,
        request: RpcRequestBuilder.() -> Unit
    ): PostgrestResult = rpcRequest(function, parameters, request)

    override suspend fun rpc(function: String, request: RpcRequestBuilder.() -> Unit): PostgrestResult = rpcRequest(function, null, request)

    private suspend fun rpcRequest(function: String, body: JsonObject? = null, request: RpcRequestBuilder.() -> Unit): PostgrestResult {
        val requestBuilder = RpcRequestBuilder(config.defaultSchema, config.propertyConversionMethod).apply {
            this.body = body
            if(method != RpcMethod.POST && body != null) {
                params.putAll(body.mapValues { listOf(it.value.toString()) })
            }
            request()
        }
        return RestRequestExecutor.execute(postgrest = this, path = "rpc/$function", request = requestBuilder)
    }

}