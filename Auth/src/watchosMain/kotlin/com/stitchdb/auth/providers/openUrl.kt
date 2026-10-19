package com.stitchdb.auth.providers

import com.stitchdb.auth.Auth
import platform.Foundation.NSURL

internal actual fun Auth.openUrl(url: NSURL) {
    error("Can't open url on watchOS")
}