package com.stitchdb.postgrest.query.request

import com.stitchdb.postgrest.PropertyConversionMethod
import com.stitchdb.postgrest.query.PostgrestQueryBuilder
import com.stitchdb.postgrest.query.PostgrestRequestBuilder
import io.ktor.http.HttpMethod

/**
 * Request builder for [PostgrestQueryBuilder.delete]
 */
class DeleteRequestBuilder(defaultSchema: String, propertyConversionMethod: PropertyConversionMethod): PostgrestRequestBuilder(
    defaultSchema,
    propertyConversionMethod
) {

    init {
        httpMethod = HttpMethod.Delete
    }

    override fun customPrefer(): List<String> = withReturning()

}