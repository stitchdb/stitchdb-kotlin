package com.stitchdb.auth

import com.stitchdb.StitchClient
import com.stitchdb.auth.providers.openUrl
import platform.Foundation.NSURL

internal actual suspend fun StitchClient.openExternalUrl(url: String) {
    auth.openUrl(NSURL(string = url))
}