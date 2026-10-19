@file:Suppress("RedundantSuspendModifier")
package com.stitchdb.auth

import com.stitchdb.auth.user.UserSession

internal actual suspend fun Auth.startExternalAuth(
    redirectUrl: String?,
    getUrl: suspend (redirectTo: String?) -> String,
    onSessionSuccess: suspend (UserSession) -> Unit
) {
    config.urlLauncher.openUrl(stitchdbClient, getUrl(redirectUrl))
}