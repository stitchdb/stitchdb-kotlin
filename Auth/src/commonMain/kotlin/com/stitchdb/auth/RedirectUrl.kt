package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal

@StitchInternal
internal expect fun Auth.defaultPlatformRedirectUrl(): String?

@StitchInternal
fun Auth.defaultRedirectUrl(): String? {
    return config.defaultRedirectUrl ?: defaultPlatformRedirectUrl()
}