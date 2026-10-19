package com.stitchdb.postgrest.executor

import com.stitchdb.postgrest.Postgrest
import com.stitchdb.postgrest.query.PostgrestRequestBuilder
import com.stitchdb.postgrest.result.PostgrestResult

/**
 * Request executor interface
 *
 */
sealed interface RequestExecutor {

    /**
     * Execute given PostgrestRequest
     *
     * @param path [String]
     * @param request [PostgrestRequestBuilder]
     * @return [PostgrestResult]
     */
    suspend fun execute(postgrest: Postgrest, path: String, request: PostgrestRequestBuilder): PostgrestResult
}