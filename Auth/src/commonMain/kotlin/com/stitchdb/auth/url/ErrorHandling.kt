package com.stitchdb.auth.url

import com.stitchdb.auth.Auth
import com.stitchdb.auth.event.AuthEvent
import com.stitchdb.auth.status.SessionStatus
import com.stitchdb.logging.d

internal fun checkForUrlParameterError(parameters: (String) -> String?): AuthEvent.OtpError? {
    val error = parameters("error")
    val errorCode = parameters("error_code")
    val errorDescription = parameters("error_description")
    return if(errorCode != null) {
        AuthEvent.OtpError(
            error = errorCode,
            errorDescription = "$errorDescription ($error)",
        )
    } else null
}

internal fun Auth.handledUrlParameterError(parameters: (String) -> String?): Boolean {
    val error = checkForUrlParameterError(parameters)
    return if(error != null) {
        if(sessionStatus.value !is SessionStatus.Authenticated) {
            logger.d { "Found error code in the URL Parameters: $error. Emitting event..." }
            emitEvent(error)
        }
        true
    } else false
}