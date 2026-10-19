package com.stitchdb.auth

import com.stitchdb.auth.server.createServer
import com.stitchdb.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

internal actual suspend fun Auth.startExternalAuth(
    redirectUrl: String?,
    getUrl: suspend (redirectTo: String?) -> String,
    onSessionSuccess: suspend (UserSession) -> Unit
) {
    withContext(Dispatchers.IO) {
        if(redirectUrl != null) {
            config.urlLauncher.openUrl(stitchdbClient, getUrl(redirectUrl))
            return@withContext
        }
        createServer({
            getUrl(it)
        }, stitchdbClient.auth, onSessionSuccess)
    }
}