package com.stitchdb.auth

import com.stitchdb.StitchClient
import platform.posix.system

internal actual suspend fun StitchClient.openExternalUrl(url: String) {
    system("xdg-open $url")
}