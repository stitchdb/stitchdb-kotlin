package com.stitchdb.auth.providers.builtin

import com.stitchdb.StitchClient
import com.stitchdb.auth.auth
import com.stitchdb.auth.providers.AuthProvider
import com.stitchdb.auth.startExternalAuth
import com.stitchdb.auth.user.UserSession
import kotlinx.serialization.Serializable

/**
 * Single Sign On (SSO) auth provider for stitchdb.
 *
 * Check the [docs](https://stitchdb.com/docs) for more information.
 *
 */
data object SSO: AuthProvider<SSO.Config, Unit> {

    /**
     * The SSO config
     *
     * Use only one of [providerId] or [domain]
     *
     * @param providerId The provider id of the SSO provider
     * @param captchaToken Optional captcha token
     * @param domain The domain of the SSO provider
     */
    data class Config(
        var providerId: String? = null,
        var captchaToken: String? = null,
        var domain: String? = null,
    )

    /**
     * The result of an SSO login
     * @param url The url to redirect to
     */
    @Serializable
    data class Result(
        val url: String
    )

    override suspend fun login(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (Config.() -> Unit)?
    ) = signUp(stitchdbClient, onSuccess, redirectUrl, config)

    override suspend fun signUp(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (Config.() -> Unit)?
    ) = loginWithSSO(
        stitchdbClient,
        onSuccess,
        redirectUrl,
        config
    )

}

internal suspend fun loginWithSSO(
    stitchdbClient: StitchClient,
    onSuccess: suspend (UserSession) -> Unit,
    redirectUrl: String?,
    config: (SSO.Config.() -> Unit)?
) {
    stitchdbClient.auth.startExternalAuth(
        redirectUrl = redirectUrl,
        getUrl = {
            stitchdbClient.auth.retrieveSSOUrl(it) {
                config?.invoke(this)
            }.url
        },
        onSessionSuccess = onSuccess
    )
}