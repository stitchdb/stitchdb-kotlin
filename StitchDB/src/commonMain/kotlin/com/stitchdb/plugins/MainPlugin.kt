package com.stitchdb.plugins

import com.stitchdb.StitchClient
import com.stitchdb.StitchLoggingProcessorFactory
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.buildUrl
import com.stitchdb.exceptions.RestException
import com.stitchdb.logging.LogLevel
import com.stitchdb.logging.StitchLogger
import io.ktor.client.statement.HttpResponse
import io.ktor.http.appendEncodedPathSegments

/**
 * Config for [MainPlugin]s
 */
open class MainConfig {

    /**
     * The url used for this module
     */
    var customUrl: String? = null

    /**
     * The jwt token used for this module. If null, the client will use the token from Auth's current session
     */
    var jwtToken: String? = null

    var logLevel: LogLevel? = null

    var loggingFactory: StitchLoggingProcessorFactory? = null

}

/**
 * Represents main plugins like Auth or Functions
 */
interface MainPlugin <Config : MainConfig> : StitchPlugin<Config> {

    /**
     * The version for the api the plugin is using
     */
    val apiVersion: Int

    /**
     * The unique key for this plugin
     */
    val pluginKey: String

    val logger: StitchLogger

    /**
     * Gets the auth url from either [MainConfig.customUrl] or [StitchClient.stitchdbHttpUrl] and adds [path] to it
     */
    @OptIn(StitchInternal::class)
    fun resolveUrl(path: String = ""): String {
        val isBase = config.customUrl == null
        return buildUrl(config.customUrl ?: stitchdbClient.stitchdbHttpUrl) {
            if(isBase) {
                appendEncodedPathSegments(pluginKey, "v${apiVersion}")
            }
            if(path.isNotBlank()) {
                appendEncodedPathSegments(path)
            }
        }
    }

    /**
     * Parses the response from the server and builds a [RestException]
     */
    suspend fun parseErrorResponse(response: HttpResponse): RestException

}