package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.status.SessionSource
import com.stitchdb.auth.url.UrlValidationResult
import com.stitchdb.auth.url.validateHash
import com.stitchdb.auth.user.UserSession
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.launch

@StitchInternal
fun Auth.parseFragmentAndImportSession(fragment: String, onFinish: (UserSession?) -> Unit = {}) {
    val res = validateHash(fragment)
    if(res !is UrlValidationResult.SessionFound) {
        onFinish(null)
        return
    }
    val session = res.session
    authScope.launch {
        val user = tryToGetUser(session.accessToken)
        val newSession = session.copy(user = user)
        onFinish(newSession)
        importSession(newSession, source = SessionSource.External)
    }
}

@StitchInternal
fun HttpRequestBuilder.redirectTo(url: String) {
    this.url.parameters["redirect_to"] = url
}