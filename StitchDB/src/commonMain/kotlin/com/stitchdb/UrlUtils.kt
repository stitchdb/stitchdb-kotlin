package com.stitchdb

import com.stitchdb.annotations.StitchInternal
import io.ktor.http.URLBuilder
import io.ktor.http.Url

@StitchInternal
inline fun buildUrl(baseUrl: String, init: URLBuilder.() -> Unit): String {
    val builder = URLBuilder(baseUrl)
    builder.init()
    return builder.buildString()
}

@StitchInternal
inline fun buildUrl(baseUrl: Url, init: URLBuilder.() -> Unit): String {
    val builder = URLBuilder(baseUrl)
    builder.init()
    return builder.buildString()
}