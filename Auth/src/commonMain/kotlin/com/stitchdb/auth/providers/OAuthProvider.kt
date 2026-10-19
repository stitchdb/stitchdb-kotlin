package com.stitchdb.auth.providers

import com.stitchdb.StitchClient
import com.stitchdb.auth.auth
import com.stitchdb.auth.startExternalAuth
import com.stitchdb.auth.user.UserSession

/**
 * Represents an OAuth provider.
 */
abstract class OAuthProvider : AuthProvider<ExternalAuthConfig, Unit> {

    /**
     * The name of the provider.
     */
    abstract val name: String

    override suspend fun login(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (ExternalAuthConfig.() -> Unit)?
    ) {
        val authConfig = ExternalAuthConfig()
        if (config != null) {
            authConfig.config()
        }
        stitchdbClient.auth.startExternalAuth(
            redirectUrl = redirectUrl,
            getUrl = {
                stitchdbClient.auth.getOAuthUrl(this, it) {
                    scopes.addAll(authConfig.scopes)
                    queryParams.putAll(authConfig.queryParams)
                }
            },
            onSessionSuccess = onSuccess
        )
    }

    override suspend fun signUp(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (ExternalAuthConfig.() -> Unit)?
    ) = login(stitchdbClient, onSuccess, redirectUrl, config)

    companion object

}