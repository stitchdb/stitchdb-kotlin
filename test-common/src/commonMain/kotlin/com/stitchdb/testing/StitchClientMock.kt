package com.stitchdb.testing

import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder
import com.stitchdb.createStitchClient
import com.stitchdb.logging.LogLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData

fun createMockedStitchClient(
    stitchdbUrl: String = "https://projectref.stitchdb.com",
    stitchdbKey: String = "project-anon-key",
    configuration: StitchClientBuilder.() -> Unit = {},
    requestHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("") },
): StitchClient {
    return createStitchClient(
        stitchdbUrl = stitchdbUrl,
        stitchdbKey = stitchdbKey,
    ) {
        defaultLogLevel = LogLevel.DEBUG
        httpEngine = MockEngine {
            requestHandler(this, it)
        }
        configuration()
    }
}

suspend fun <T> withMockedStitchClient(
    stitchdbUrl: String = "https://projectref.stitchdb.com",
    stitchdbKey: String = "project-anon-key",
    configuration: StitchClientBuilder.() -> Unit = {},
    requestHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("") },
    clientHandler: suspend (StitchClient) -> T,
): T {
    val client = createMockedStitchClient(
        stitchdbUrl = stitchdbUrl,
        stitchdbKey = stitchdbKey,
        configuration = configuration,
        requestHandler = requestHandler
    )
    return try {
        clientHandler(client)
    } finally {
        client.close()
    }
}
