package com.stitchdb.storage.vectors.index

import com.stitchdb.auth.api.AuthenticatedStitchApi
import com.stitchdb.safeBody
import com.stitchdb.storage.vectors.data.VectorDataApi
import com.stitchdb.storage.vectors.data.VectorDataApiImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Interface for index management and access to vector operations.
 */
interface VectorIndexApi {

    /**
     * Creates a new vector index in this bucket
     * Convenience method that automatically includes the bucket name
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @param options - Index configuration (vectorBucketName is automatically set)
     */
    suspend fun createIndex(options: CreateIndexOptions.() -> Unit)

    /**
     * Lists indexes in this bucket
     * Convenience method that automatically includes the bucket name
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @param options Listing options (vectorBucketName is automatically set)
     */
    suspend fun listIndexes(options: ListIndexesOptions.() -> Unit = {}): ListIndexesResponse

    /**
     * Retrieves metadata for a specific index in this bucket
     * Convenience method that automatically includes the bucket name
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @param indexName Name of the index to retrieve
     */
    suspend fun getIndex(indexName: String): VectorIndex

    /**
     * Deletes an index from this bucket
     * Convenience method that automatically includes the bucket name
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @param indexName Name of the index to delete
     */
    suspend fun deleteIndex(indexName: String)

    /**
     * Access operations for a specific index within this bucket
     * Returns a scoped client for vector data operations
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @param indexName Name of the index
     * @returns Index-scoped client with vector data operations
     */
    fun index(indexName: String): VectorDataApi

}

internal class VectorIndexApiImpl(
    private val bucketName: String,
    private val api: AuthenticatedStitchApi
): VectorIndexApi {

    override suspend fun createIndex(options: CreateIndexOptions.() -> Unit) {
        api.postJson("CreateIndex", CreateIndexOptions(bucketName).apply(options).build())
    }

    override suspend fun getIndex(indexName: String): VectorIndex {
        val responseBody = api.postJson("GetIndex", buildJsonObject {
            put("vectorBucketName", bucketName)
            put("indexName", indexName)
        }).safeBody<JsonObject>()
        return Json.decodeFromJsonElement(responseBody["index"]?.jsonObject ?: error("Invalid response body: $responseBody"))
    }

    override suspend fun listIndexes(options: ListIndexesOptions.() -> Unit): ListIndexesResponse {
        val options = ListIndexesOptions(bucketName).apply(options)
        return api.postJson("ListIndexes", options.build()).safeBody()
    }

    override suspend fun deleteIndex(indexName: String) {
        api.postJson("DeleteIndex", buildJsonObject {
            put("vectorBucketName", bucketName)
            put("indexName", indexName)
        })
    }

    override fun index(indexName: String): VectorDataApi {
        return VectorDataApiImpl(bucketName, indexName, api)
    }

}