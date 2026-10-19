package com.stitchdb.auth

import com.stitchdb.StringMasking
import com.stitchdb.auth.user.UserSession

internal fun StringMasking.maskSession(value: UserSession): UserSession {
    return value.copy(
        accessToken = maskString(value.accessToken, showLength = true),
        refreshToken = maskString(value.refreshToken, showLength = true),
        providerRefreshToken = value.providerRefreshToken?.let { maskString(it, showLength = true) },
        providerToken = value.providerToken?.let { maskString(it, showLength = true) }
    )
}