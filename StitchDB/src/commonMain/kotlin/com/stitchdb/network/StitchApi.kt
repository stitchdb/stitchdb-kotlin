@file:Suppress("UndocumentedPublicFunction", "UndocumentedPublicClass")
package com.stitchdb.network

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.exceptions.RestException
import com.stitchdb.plugins.MainPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.http.Headers
import io.ktor.http.isSuccess

open class StitchApi @StitchInternal constructor(
    val resolveUrl: (path: String) -> String,
    val parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null,
    val httpClient: StitchHttpClient
) : StitchHttpClient() {

    override suspend fun getDefaultHeaders(): Headers {
        return httpClient.getDefaultHeaders()
    }

    final override suspend fun request(url: String, builder: HttpRequestBuilder.() -> Unit): HttpResponse {
        return rawRequest(resolveUrl(url), builder)
    }

    open suspend fun rawRequest(url: String, builder: HttpRequestBuilder.() -> Unit): HttpResponse {
        return httpClient.request(url, builder).also {
            if(!it.status.isSuccess() && parseErrorResponse != null) throw parseErrorResponse.invoke(it)
        }
    }

    override suspend fun prepareRequest(
        url: String,
        builder: HttpRequestBuilder.() -> Unit
    ): HttpStatement = httpClient.prepareRequest(resolveUrl(url), builder)

    suspend fun prepareRequest(builder: HttpRequestBuilder.() -> Unit): HttpStatement = prepareRequest("", builder)

}

/**
 * Creates a [StitchApi] with the given [baseUrl]
 * All requests will be resolved relative to this url
 */
@StitchInternal
fun StitchClient.stitchdbApi(baseUrl: String, parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null) = stitchdbApi({ baseUrl + it }, parseErrorResponse)

/**
 * Creates a [StitchApi] for the given [plugin]
 * All requests will be resolved using the [MainPlugin.resolveUrl] function
 */
@StitchInternal
fun StitchClient.stitchdbApi(plugin: MainPlugin<*>) = stitchdbApi(plugin::resolveUrl, plugin::parseErrorResponse)

/**
 * Creates a [StitchApi] with the given [resolveUrl] function
 * All requests will be resolved using this function
 */
@StitchInternal
fun StitchClient.stitchdbApi(resolveUrl: (path: String) -> String, parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null) = StitchApi(resolveUrl, parseErrorResponse, this.httpClient)