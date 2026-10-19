import com.stitchdb.StitchClient
import com.stitchdb.auth.Auth
import com.stitchdb.auth.auth
import com.stitchdb.auth.minimalConfig
import com.stitchdb.auth.resolveAccessToken
import com.stitchdb.testing.createMockedStitchClient
import com.stitchdb.testing.respondJson
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessTokenTest {

    private lateinit var client: StitchClient
    
    @Test
    fun testAccessTokenWithJwtToken() = runTest {
        client = createMockedStitchClient(
            configuration = {
                install(Auth) {
                    minimalConfig()
                }
            }
        )
        client.auth.awaitInitialization()
        client.auth.importAuthToken("myAuth") //this should be ignored as per plugin tokens override the used access token
        assertEquals("myJwtToken", client.resolveAccessToken("myJwtToken"))
    }

    @Test
    fun testAccessTokenWithKeyAsFallback() = runTest {
        client = createMockedStitchClient(stitchdbKey = "myKey")
        assertEquals("myKey", client.resolveAccessToken())
    }

    @Test
    fun testAccessTokenWithoutKey() = runTest {
        client = createMockedStitchClient()
        assertNull(client.resolveAccessToken(keyAsFallback = false))
    }

    @Test
    fun testAccessTokenWithCustomAccessToken() = runTest {
        client = createMockedStitchClient(
            configuration = {
                accessToken = {
                    "myCustomToken"
                }
            }
        )
        assertEquals("myCustomToken", client.resolveAccessToken())
    }

    @Test
    fun testAccessTokenWithAuth() = runTest {
        client = createMockedStitchClient(
            configuration = {
                install(Auth) {
                    minimalConfig()
                }
            }
        )
        client.auth.awaitInitialization()
        client.auth.importAuthToken("myAuth")
        assertEquals("myAuth", client.resolveAccessToken())
    }

    @Test
    fun testAccessTokenWithAuthReturnsRefreshedTokenWhenExpired() = runTest {
        val newSession = userSession(customToken = "newToken")
        client = createMockedStitchClient(
            configuration = {
                install(Auth) {
                    minimalConfig()
                    alwaysAutoRefresh = true
                }
            }
        ) {
            respondJson(newSession)
        }
        client.auth.awaitInitialization()
        client.auth.importSession(userSession(expiresIn = 0), autoRefresh = false)
        assertEquals("newToken", client.resolveAccessToken())
    }

    @AfterTest
    fun cleanup() {
        runTest {
            if(::client.isInitialized) {
                client.close()
            }
        }
    }

}