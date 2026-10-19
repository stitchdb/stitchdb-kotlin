import com.stitchdb.StitchClient
import com.stitchdb.exceptions.BadRequestRestException
import com.stitchdb.exceptions.NotFoundRestException
import com.stitchdb.exceptions.RestException
import com.stitchdb.exceptions.UnauthorizedRestException
import com.stitchdb.exceptions.UnknownRestException
import com.stitchdb.functions.Functions
import com.stitchdb.functions.functions
import com.stitchdb.testing.createMockedStitchClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val ERROR_BODY = "Missing authorization header"

class FunctionsExceptionTest {

    private lateinit var stitchdb: StitchClient

    @Test
    fun testUnauthorizedError() = runTest {
        mockErrorResponse(HttpStatusCode.Unauthorized)
        val exception = assertFailsWith<UnauthorizedRestException> {
            stitchdb.functions.invoke(function = "function")
        }
        assertEquals(ERROR_BODY, exception.error)
        assertEquals(HttpStatusCode.Unauthorized.value, exception.statusCode)
    }

    @Test
    fun testNotFoundError() = runTest {
        mockErrorResponse(HttpStatusCode.NotFound)
        val exception = assertFailsWith<NotFoundRestException> {
            stitchdb.functions.invoke(function = "function")
        }
        assertEquals(ERROR_BODY, exception.error)
        assertEquals(HttpStatusCode.NotFound.value, exception.statusCode)
    }

    @Test
    fun testBadRequestError() = runTest {
        mockErrorResponse(HttpStatusCode.BadRequest)
        val exception = assertFailsWith<BadRequestRestException> {
            stitchdb.functions.invoke(function = "function")
        }
        assertEquals(ERROR_BODY, exception.error)
        assertEquals(HttpStatusCode.BadRequest.value, exception.statusCode)
    }

    @Test
    fun testOtherErrorsAreUnknown() = runTest {
        mockErrorResponse(HttpStatusCode.InternalServerError)
        val exception = assertFailsWith<UnknownRestException> {
            stitchdb.functions.invoke(function = "function")
        }
        assertEquals(ERROR_BODY, exception.error)
        assertEquals(HttpStatusCode.InternalServerError.value, exception.statusCode)
    }

    @Test
    fun testErrorUsesRawResponseBody() = runTest {
        //the functions plugin does not parse the error body, it uses the raw body as the error
        mockErrorResponse(HttpStatusCode.BadRequest, """{"error":"boom"}""")
        val exception = assertFailsWith<RestException> {
            stitchdb.functions.invoke(function = "function")
        }
        assertEquals("""{"error":"boom"}""", exception.error)
        assertNull(exception.description)
    }

    private fun mockErrorResponse(status: HttpStatusCode, body: String = ERROR_BODY) {
        stitchdb = createMockedStitchClient(
            configuration = {
                install(Functions)
            },
            requestHandler = {
                respond(body, status)
            }
        )
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
