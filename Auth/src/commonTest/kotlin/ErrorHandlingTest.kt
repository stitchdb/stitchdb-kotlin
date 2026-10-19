import app.cash.turbine.test
import com.stitchdb.StitchClient
import com.stitchdb.auth.Auth
import com.stitchdb.auth.auth
import com.stitchdb.auth.event.AuthEvent
import com.stitchdb.auth.exception.AuthErrorCode
import com.stitchdb.auth.minimalConfig
import com.stitchdb.auth.status.SessionStatus
import com.stitchdb.auth.url.checkForUrlParameterError
import com.stitchdb.auth.url.handledUrlParameterError
import com.stitchdb.auth.user.UserSession
import com.stitchdb.testing.createMockedStitchClient
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ErrorHandlingTest {

    private lateinit var stitchdb: StitchClient
    
    // Tests for checkForUrlParameterError
    @Test
    fun testCheckForUrlParameterErrorWithAllParameters() {
        val url =
            Url("https://example.com/?error=invalid_request&error_code=otp_expired&error_description=Invalid+request")
        val error = checkForUrlParameterError { url.parameters[it] }

        assertNotNull(error)
        assertEquals("otp_expired", error.error)
        assertEquals("Invalid request (invalid_request)", error.errorDescription)
        assertEquals(AuthErrorCode.OtpExpired, error.errorCode)
    }

    @Test
    fun testCheckForUrlParameterErrorWithNoError() {
        val url = Url("https://example.com/?access_token=token123")
        val error = checkForUrlParameterError { url.parameters[it] }

        assertNull(error)
    }

    @Test
    fun testCheckForUrlParameterErrorWithOnlyErrorCode() {
        val url = Url("https://example.com/?error_code=otp_expired")
        val error = checkForUrlParameterError { url.parameters[it] }

        assertNotNull(error)
        assertEquals("otp_expired", error.error)
        assertEquals("null (null)", error.errorDescription)
    }

    @Test
    fun testCheckForUrlParameterErrorWithMissingErrorCode() {
        val url = Url("https://example.com/?error=invalid_request&error_description=Something+went+wrong")
        val error = checkForUrlParameterError { url.parameters[it] }

        assertNull(error)
    }

    @Test
    fun testCheckForUrlParameterErrorWithDifferentErrorCode() {
        val url =
            Url("https://example.com/?error=bad_request&error_code=validation_failed&error_description=Validation+error")
        val error = checkForUrlParameterError { url.parameters[it] }

        assertNotNull(error)
        assertEquals("validation_failed", error.error)
        assertEquals("Validation error (bad_request)", error.errorDescription)
    }

    // Tests for handledUrlParameterError
    @Test
    fun testHandledUrlParameterErrorWithValidError() = runTest {
        val url =
            Url("https://example.com/?error=invalid_request&error_code=otp_expired&error_description=Invalid+request")
        stitchdb = createMockedStitchClient(
            configuration = {
                install(Auth) {
                    minimalConfig()
                }
            }
        )
        stitchdb.auth.awaitInitialization()
        val handled = stitchdb.auth.handledUrlParameterError {
            url.parameters[it]
        }

        assertTrue { handled }
        assertIs<SessionStatus.NotAuthenticated>(stitchdb.auth.sessionStatus.value)
        stitchdb.auth.events.test {
            val event = expectMostRecentItem()
            assertIs<AuthEvent.OtpError>(event)
            assertEquals(AuthErrorCode.OtpExpired, event.errorCode)
            assertEquals("Invalid request (invalid_request)", event.errorDescription)
        }
    }

    @Test
    fun testHandledUrlParameterErrorWithNoError() = runTest {
        val url = Url("https://example.com/")
        stitchdb = createMockedStitchClient(
            configuration = {
                install(Auth) {
                    minimalConfig()
                }
            }
        )

        stitchdb.auth.awaitInitialization()
        val handled = stitchdb.auth.handledUrlParameterError {
            url.parameters[it]
        }

        assertFalse { handled }
        assertIs<SessionStatus.NotAuthenticated>(stitchdb.auth.sessionStatus.value)
        stitchdb.auth.events.test(timeout = 1.seconds) {
            expectNoEvents()
        }
    }

    @Test
    fun testHandledUrlParameterErrorWithEmptyParameters() = runTest {
        stitchdb = createMockedStitchClient(
            configuration = {
                install(Auth) {
                    minimalConfig()
                }
            }
        )

        val handled = stitchdb.auth.handledUrlParameterError { null }

        assertFalse { handled }
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

private fun userSession(customToken: String = "accessToken", expiresIn: Long = 3600) = UserSession(
    accessToken = customToken,
    refreshToken = "refreshToken",
    expiresIn = expiresIn,
    tokenType = "Bearer",
    user = null
)
