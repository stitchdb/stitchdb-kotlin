package com.stitchdb.auth

import com.stitchdb.StitchClient
import com.stitchdb.auth.status.SessionStatus
import com.stitchdb.auth.user.UserSession
import com.stitchdb.logging.e
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun invalidArg(message: String): Nothing = throw IllegalArgumentException(message)

internal expect suspend fun StitchClient.openExternalUrl(url: String)

internal expect suspend fun Auth.startExternalAuth(
    redirectUrl: String?,
    getUrl: suspend (redirectTo: String?) -> String,
    onSessionSuccess: suspend (UserSession) -> Unit
)

internal fun Auth.initDone() {
    if(sessionStatus.value is SessionStatus.Initializing) {
        setSessionStatus(SessionStatus.NotAuthenticated())
    }
}

internal suspend fun Auth.tryToGetUser(accessToken: String) = try {
    retrieveUser(accessToken)
} catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    logger.e(e) { "Couldn't retrieve user using access token $accessToken.\nIf you use the project secret, ignore this message." }
    null
}