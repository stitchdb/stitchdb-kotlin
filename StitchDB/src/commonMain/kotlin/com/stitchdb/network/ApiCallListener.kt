package com.stitchdb.network

import io.ktor.http.Url

/**
 * Told about every request that went through [KtorStitchHttpClient.request] (crash reporting turns them into
 * breadcrumbs). [status] is 0 when the server could not be reached, [error] is then the reason. [errorCode] is the JSON
 * body's `code` or `error_code` of an error response.
 */
internal fun interface ApiCallListener {
    fun onApiCall(method: String, url: Url, status: Int, errorCode: String?, error: String?, ms: Long)
}
