package com.stitchdb.postgrest.executor

import com.stitchdb.postgrest.Postgrest
import com.stitchdb.postgrest.result.PostgrestResult
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

internal suspend fun HttpResponse.asPostgrestResult(postgrest: Postgrest): PostgrestResult =
    PostgrestResult(bodyAsText(), headers, postgrest)