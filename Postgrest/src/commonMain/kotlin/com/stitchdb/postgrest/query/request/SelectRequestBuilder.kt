package com.stitchdb.postgrest.query.request

import com.stitchdb.postgrest.PropertyConversionMethod
import com.stitchdb.postgrest.query.PostgrestQueryBuilder
import com.stitchdb.postgrest.query.PostgrestRequestBuilder
import io.ktor.http.HttpMethod

/**
 * Request builder for [PostgrestQueryBuilder.select]
 */
class SelectRequestBuilder(defaultSchema: String, propertyConversionMethod: PropertyConversionMethod): PostgrestRequestBuilder(
    defaultSchema,
    propertyConversionMethod
) {

    /**
     * If true, no body will be returned. Useful when using count.
     */
    var head: Boolean = false
        set(value) {
            httpMethod = if (value) HttpMethod.Head else HttpMethod.Get
            field = value
        }

}