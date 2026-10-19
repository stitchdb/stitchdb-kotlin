package com.stitchdb.auth.url

import com.stitchdb.auth.user.UserSession

internal sealed interface UrlValidationResult {

    data object ErrorFound: UrlValidationResult

    data class SessionFound(val session: UserSession): UrlValidationResult

    data object Skipped: UrlValidationResult

}