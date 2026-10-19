package com.stitchdb.auth

import com.stitchdb.StitchClient
import io.ktor.util.PlatformUtils.IS_BROWSER
import kotlinx.browser.window

internal actual suspend fun StitchClient.openExternalUrl(url: String) {
    if(IS_BROWSER) {
        window.location.href = url
    } else {
        //TODO: Unsupported for now
    }
}