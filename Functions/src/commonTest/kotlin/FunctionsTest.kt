import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder
import com.stitchdb.auth.Auth
import com.stitchdb.auth.auth
import com.stitchdb.auth.minimalConfig
import com.stitchdb.functions.FunctionRegion
import com.stitchdb.functions.Functions
import com.stitchdb.functions.functions
import com.stitchdb.testing.createMockedStitchClient
import com.stitchdb.testing.pathAfterVersion
import com.stitchdb.testing.toJsonElement
import io.ktor.client.engine.mock.respond
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal val configuration: StitchClientBuilder.() -> Unit = {
    install(Functions)
}

class FunctionsTest {

    private lateinit var stitchdb: StitchClient

    @Test
    fun testAuthorizationHeaderAuth() {
        runTest {
            val expectedJWT = "jwt"
            stitchdb = createMockedStitchClient(
                configuration ={
                    install(Auth) {
                        minimalConfig()
                    }
                    configuration()
                },
                requestHandler = {
                    assertEquals("Bearer $expectedJWT", it.headers[HttpHeaders.Authorization])
                    respond("")
                }
            )
            stitchdb.auth.awaitInitialization()
            stitchdb.auth.importAuthToken(expectedJWT)
            stitchdb.functions.invoke(
                function = ""
            )
        }
    }

    @Test
    fun testNewApiKeyFallback() {
        runTest {
            stitchdb = createMockedStitchClient(
                stitchdbKey = "sdb_publishable_",
                configuration ={
                    install(Auth) {
                        minimalConfig()
                    }
                    configuration()
                },
                requestHandler = {
                    assertNull(it.headers[HttpHeaders.Authorization])
                    respond("")
                }
            )
            stitchdb.auth.awaitInitialization()
            stitchdb.functions.invoke(
                function = ""
            )
        }
    }

    @Test
    fun testAuthorizationHeaderCustomToken() {
        runTest {
            val expectedJWT = "jwt"
            stitchdb = createMockedStitchClient(
                configuration = {
                    install(Functions) {
                        jwtToken = expectedJWT
                    }
                },
                requestHandler = {
                    assertEquals("Bearer $expectedJWT", it.headers[HttpHeaders.Authorization])
                    respond("")
                }
            )
            stitchdb.functions.invoke(
                function = ""
            )
        }
    }

    @Test
    fun testInvokingWithoutBody() {
        runTest {
            val expectedName = "myFunction"
            val expectedRegion = FunctionRegion.EU_WEST_1
            val expectedHeaders = Headers.build {
                append("myHeader", "myValue")
            }
            stitchdb = createMockedStitchClient(
                configuration = configuration,
                requestHandler = {
                    assertEquals("POST", it.method.value)
                    assertEquals("/$expectedName", it.url.pathAfterVersion())
                    assertEquals(expectedRegion.value, it.headers["x-region"])
                    assertEquals("myValue", it.headers["myHeader"])
                    respond("")
                }
            )
            stitchdb.functions.invoke(
                function = expectedName,
                region = expectedRegion,
                headers = expectedHeaders
            )
        }
    }

    @Test
    fun testInvokingWithBody() {
        runTest {
            val expectedName = "myFunction"
            val expectedRegion = FunctionRegion.EU_WEST_1
            val expectedHeaders = Headers.build {
                set(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            val expectedBody = buildJsonObject {
                put("key", "value")
            }
            stitchdb = createMockedStitchClient(
                configuration = configuration,
                requestHandler = {
                    val body = it.body.toJsonElement().jsonObject
                    assertEquals(expectedBody, body)
                    assertEquals("POST", it.method.value)
                    assertEquals("/$expectedName", it.url.pathAfterVersion())
                    assertEquals(expectedRegion.value, it.headers["x-region"])
                    respond("")
                }
            )
            stitchdb.functions.invoke(
                function = expectedName,
                region = expectedRegion,
                body = expectedBody,
                headers = expectedHeaders
            )
        }
    }

    // invokeSSE() cannot be unit-tested with MockEngine because it lacks SSECapability.
    // SSE streaming is covered by FunctionsIntegrationTest instead.

    @Test
    fun testPrepareInvoke() {
        runTest {
            val expectedName = "myFunction"
            val expectedRegion = FunctionRegion.EU_WEST_1
            stitchdb = createMockedStitchClient(
                configuration = configuration,
                requestHandler = {
                    assertEquals("POST", it.method.value)
                    assertEquals("/$expectedName", it.url.pathAfterVersion())
                    assertEquals(expectedRegion.value, it.headers["x-region"])
                    respond("streaming content")
                }
            )
            val statement = stitchdb.functions.prepareInvoke(
                function = expectedName,
                region = expectedRegion
            )
            statement.execute { response ->
                assertEquals("streaming content", response.bodyAsText())
            }
        }
    }

    @AfterTest
    fun cleanup() {
        runTest {
            if(::stitchdb.isInitialized) {
                stitchdb.close()
            }
        }
    }

}
