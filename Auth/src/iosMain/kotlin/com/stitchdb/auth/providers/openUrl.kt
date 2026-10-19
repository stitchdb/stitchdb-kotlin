package com.stitchdb.auth.providers

import com.stitchdb.auth.Auth
import com.stitchdb.logging.d
import com.stitchdb.logging.e
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

internal actual fun Auth.openUrl(url: NSURL) {
    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) {
        if(it) logger.d { "Successfully opened provider url in safari" } else logger.e { "Failed to open provider url in safari" }
    }
}