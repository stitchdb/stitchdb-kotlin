package com.stitchdb.auth.providers

import com.stitchdb.auth.Auth
import platform.Foundation.NSURL

internal expect fun Auth.openUrl(url: NSURL)