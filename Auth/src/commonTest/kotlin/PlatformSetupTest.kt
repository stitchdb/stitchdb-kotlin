import com.stitchdb.auth.Auth
import com.stitchdb.auth.auth
import com.stitchdb.auth.minimalConfig
import com.stitchdb.testing.createMockedStitchClient

internal suspend fun createMinimalAuthClient(
    autoSetup: Boolean,
    authHandler: suspend (Auth) -> Unit
) {
    val client = createMockedStitchClient(
        configuration = {
            install(Auth) {
                autoSetupPlatform = autoSetup
                minimalConfig()
            }
        }
    )
    try {
        authHandler(client.auth)
    } finally {
        client.close()
    }
}
