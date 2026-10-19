package com.stitchdb.postgrest.query.request

import com.stitchdb.postgrest.Postgrest
import com.stitchdb.postgrest.PropertyConversionMethod
import com.stitchdb.postgrest.RpcMethod
import com.stitchdb.postgrest.query.PostgrestRequestBuilder
import io.ktor.http.HttpMethod

/**
 * Request builder for [Postgrest.rpc]
 */
class RpcRequestBuilder(defaultSchema: String, propertyConversionMethod: PropertyConversionMethod): PostgrestRequestBuilder(
    defaultSchema,
    propertyConversionMethod
) {

    /**
     * The HTTP method to use. Default is POST
     */
    var method: RpcMethod = RpcMethod.POST
        set(value) {
            httpMethod = value.httpMethod
            field = value
        }

    init {
        httpMethod = HttpMethod.Post
    }

}