package com.stitchdb.auth

import com.stitchdb.OSInformation
import com.stitchdb.StringMasking
import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.exception.TokenExpiredException
import com.stitchdb.logging.d
import com.stitchdb.logging.e
import com.stitchdb.plugins.MainConfig
import com.stitchdb.plugins.MainPlugin
import kotlin.time.Clock

/**
 * Returns the access token used for requests. The token is resolved in the following order:
 * 1. [jwtToken] if not null
 * 2. [StitchClient.accessToken] if not null
 * 3. [Auth.currentAccessTokenOrNull] if the Auth plugin is installed. This method also checks if the token expired, tries to force-refresh it and returns the refreshed token.
 * 4. [StitchClient.stitchdbKey] if [keyAsFallback] is true
 */
@StitchInternal
suspend fun StitchClient.resolveAccessToken(
    jwtToken: String? = null,
    keyAsFallback: Boolean = true
): String? {
    val key = if(keyAsFallback) stitchdbKey else null
    val auth = pluginManager.getPluginOrNull(Auth)
    return jwtToken ?: accessToken?.invoke()
    ?: auth?.currentAccessTokenOrNull()?.let { token ->
        checkAccessToken(token)
        auth.currentAccessTokenOrNull()
    } ?: key
}

/**
 * Returns the access token used for requests. The token is resolved in the following order:
 * 1. [MainConfig.jwtToken] if not null
 * 2. [StitchClient.resolveAccessToken] if not null
 * 3. [Auth.currentAccessTokenOrNull] if the Auth plugin is installed. This method also checks if the token expired and tries to force-refresh it.
 * 4. [StitchClient.stitchdbKey] if [keyAsFallback] is true
 */
@StitchInternal
suspend fun <C : MainConfig> StitchClient.resolveAccessToken(
    plugin: MainPlugin<C>,
    keyAsFallback: Boolean = true
) = resolveAccessToken(plugin.config.jwtToken, keyAsFallback)

private suspend fun StitchClient.checkAccessToken(token: String) {
    val auth = pluginManager.getPluginOrNull(Auth) ?: return
    val currentSession = auth.currentSessionOrNull()
    val now = Clock.System.now()
    val sessionExistsAndExpired =
        token == currentSession?.accessToken && currentSession.expiresAt < now
    val autoRefreshEnabled = auth.config.alwaysAutoRefresh
    if (sessionExistsAndExpired && autoRefreshEnabled) {
        val autoRefreshRunning = auth.isAutoRefreshRunning
        auth.logger.e {
            """
                Authenticated request attempted with expired access token. This should not happen. Please report this issue. Trying to refresh session before...
                Auto refresh running: $autoRefreshRunning
                Auto-refresher information: ${auth.autoRefreshInformation()}
                OS: ${OSInformation.CURRENT}
                Session: ${StringMasking.maskSession(currentSession)}
            """.trimIndent()
        }

        try {
            auth.refreshCurrentSession()
        } catch (e: Exception) {
            auth.logger.d(e) { "Failed to force-refresh session before making a request with an expired access token" }
            throw TokenExpiredException()
        }
    }
}