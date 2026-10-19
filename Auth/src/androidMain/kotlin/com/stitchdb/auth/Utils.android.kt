@file:Suppress("RedundantSuspendModifier")
package com.stitchdb.auth

import android.net.Uri
import com.stitchdb.StitchClient
import com.stitchdb.auth.user.UserSession

internal actual suspend fun StitchClient.openExternalUrl(url: String) {
    openUrl(Uri.parse(url), auth.config.defaultExternalAuthAction)
}

internal actual suspend fun Auth.startExternalAuth(
    redirectUrl: String?,
    getUrl: suspend (redirectTo: String?) -> String,
    onSessionSuccess: suspend (UserSession) -> Unit
) {
    config.urlLauncher.openUrl(stitchdbClient, getUrl(redirectUrl))
}