package com.stitchdb.auth.api

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.Auth
import com.stitchdb.auth.AuthDependentPluginConfig
import com.stitchdb.exceptions.RestException
import com.stitchdb.plugins.MainConfig
import com.stitchdb.plugins.MainPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse

/**
 * Creates a [AuthenticatedStitchApi] with the given [baseUrl]. Requires [Auth] to authenticate requests.
 * All requests will be resolved relative to this url.
 */
@StitchInternal
fun StitchClient.authenticatedStitchApi(
    baseUrl: String,
    parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null,
    config: AuthenticatedApiConfig
): AuthenticatedStitchApi = authenticatedStitchApi(
    resolveUrl = { baseUrl + it },
    parseErrorResponse = parseErrorResponse,
    config = config
)

/**
 * Creates a [AuthenticatedStitchApi] with the given [baseUrl]. Requires [Auth] to authenticate requests.
 * All requests will be resolved relative to this url.
 */
@StitchInternal
fun StitchClient.authenticatedStitchApi(
    baseUrl: String,
    parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null,
    requireSession: Boolean,
    configure: AuthenticatedApiConfig.Builder.() -> Unit
): AuthenticatedStitchApi = authenticatedStitchApi(
    resolveUrl = { baseUrl + it },
    parseErrorResponse = parseErrorResponse,
    requireSession = requireSession,
    configure = configure
)

/**
 * Creates a [AuthenticatedStitchApi] for the given [plugin]. Requires [Auth] to authenticate requests.
 * All requests will be resolved using the [MainPlugin.resolveUrl] function.
 */
@StitchInternal
fun <C> StitchClient.authenticatedStitchApi(
    plugin: MainPlugin<C>,
    defaultRequest: (HttpRequestBuilder.() -> Unit)? = null,
    requireSession: Boolean = plugin.config.requireValidSession,
    urlLengthLimit: Int? = null
): AuthenticatedStitchApi where C : MainConfig, C : AuthDependentPluginConfig =
    authenticatedStitchApi(plugin, requireSession) {
        this.defaultRequest = defaultRequest
        this.urlLengthLimit = urlLengthLimit
    }

/**
 * Creates a [AuthenticatedStitchApi] for the given [plugin]. Requires [Auth] to authenticate requests.
 * All requests will be resolved using the [MainPlugin.resolveUrl] function.
 */
@StitchInternal
fun <C> StitchClient.authenticatedStitchApi(
    plugin: MainPlugin<C>,
    requireSession: Boolean = plugin.config.requireValidSession,
    configure: AuthenticatedApiConfig.Builder.() -> Unit
): AuthenticatedStitchApi where C : MainConfig, C : AuthDependentPluginConfig {
    val client = this
    return authenticatedStitchApi(
        resolveUrl = plugin::resolveUrl,
        parseErrorResponse = plugin::parseErrorResponse,
        requireSession = requireSession
    ) {
        jwtToken = plugin.config.jwtToken
        getAccessToken = Auth.defaultResolveAccessToken(client)
        configure()
    }
}

/**
 * Creates a [AuthenticatedStitchApi] with the given [resolveUrl] function. Requires [Auth] to authenticate requests.
 * All requests will be resolved using this function.
 */
@StitchInternal
fun StitchClient.authenticatedStitchApi(
    resolveUrl: (path: String) -> String,
    parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null,
    config: AuthenticatedApiConfig
): AuthenticatedStitchApi = AuthenticatedStitchApi(
    httpClient = this.httpClient,
    config = config.withContext(resolveUrl, parseErrorResponse)
)

/**
 * Creates a [AuthenticatedStitchApi] with the given [resolveUrl] function. Requires [Auth] to authenticate requests.
 * All requests will be resolved using this function.
 */
@StitchInternal
fun StitchClient.authenticatedStitchApi(
    resolveUrl: (path: String) -> String,
    parseErrorResponse: (suspend (response: HttpResponse) -> RestException)? = null,
    requireSession: Boolean,
    configure: AuthenticatedApiConfig.Builder.() -> Unit
): AuthenticatedStitchApi = AuthenticatedStitchApi(
    httpClient = this.httpClient,
    config = buildAuthenticatedApiConfig(resolveUrl, parseErrorResponse, requireSession, configure)
)

private fun StitchClient.buildAuthenticatedApiConfig(
    resolveUrl: (path: String) -> String,
    parseErrorResponse: (suspend (response: HttpResponse) -> RestException)?,
    requireSession: Boolean,
    configure: AuthenticatedApiConfig.Builder.() -> Unit
): AuthenticatedApiConfig = AuthenticatedApiConfig.Builder().apply {
    this.resolveUrl = resolveUrl
    this.parseErrorResponse = parseErrorResponse
    this.requireSession = requireSession
    this.getAccessToken = Auth.defaultResolveAccessToken(this@buildAuthenticatedApiConfig)
    configure()
}.build()
