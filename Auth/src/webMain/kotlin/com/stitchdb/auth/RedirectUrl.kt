package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal
import io.ktor.util.PlatformUtils.IS_BROWSER
import kotlinx.browser.window

@StitchInternal
actual fun Auth.defaultPlatformRedirectUrl(): String? = if(IS_BROWSER) window.location.origin else null