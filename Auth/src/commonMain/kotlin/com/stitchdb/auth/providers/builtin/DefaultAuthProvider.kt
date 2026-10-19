package com.stitchdb.auth.providers.builtin

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.AuthImpl
import com.stitchdb.auth.FlowType
import com.stitchdb.auth.auth
import com.stitchdb.auth.generateCodeChallenge
import com.stitchdb.auth.generateCodeVerifier
import com.stitchdb.auth.providers.AuthProvider
import com.stitchdb.auth.putCodeChallenge
import com.stitchdb.auth.redirectTo
import com.stitchdb.auth.user.UserSession
import com.stitchdb.logging.w
import com.stitchdb.putJsonObject
import com.stitchdb.safeBody
import com.stitchdb.stitchdbJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * A default authentication provider
 * @see Email
 * @see Phone
 * @see IDToken
 */
sealed interface DefaultAuthProvider<C, R> : AuthProvider<C, R> {

    /**
     * The grant type of the provider.
     */
    val grantType: String

    /**
     * The default configuration for the provider.
     * @param captchaToken The captcha token when having captcha enabled
     */
    @Serializable
    sealed class Config(
        @Serializable(with = CaptchaTokenSerializer::class)
        @SerialName("gotrue_meta_security")
        var captchaToken: String? = null,
    )

    override suspend fun login(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (C.() -> Unit)?
    ) {
        require(config != null) { "Credentials are required" }
        val encodedCredentials = encodeCredentials(config)
        val gotrue = stitchdbClient.auth as AuthImpl
        val url = "token?grant_type=$grantType"
        val response = gotrue.publicApi.postJson(url, encodedCredentials) {
            redirectUrl?.let { redirectTo(it) }
        }
        response.safeBody<UserSession>().also {
            onSuccess(it)
        }
    }

    override suspend fun signUp(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (C.() -> Unit)?
    ): R? {
        require(config != null) { "Credentials are required" }
        val body = encodeCredentials(config)
        val gotrue = stitchdbClient.auth as AuthImpl
        var codeChallenge: String? = null
        if(gotrue.config.flowType == FlowType.PKCE) {
            val codeVerifier = generateCodeVerifier()
            gotrue.codeVerifierCache.saveCodeVerifier(codeVerifier)
            codeChallenge = generateCodeChallenge(codeVerifier)
        }
        val url = when (this) {
            Email -> "signup"
            Phone -> "signup"
            IDToken -> "token?grant_type=id_token"
        }
        val response = gotrue.publicApi.postJson(url, buildJsonObject {
            putJsonObject(body)
            if (codeChallenge != null) {
                putCodeChallenge(codeChallenge)
            }
        }) {
            redirectUrl?.let { redirectTo(it) }
        }
        val json = response.safeBody<JsonObject>()
        if (json.containsKey("access_token")) {
            runCatching {
                val userSession = stitchdbJson.decodeFromJsonElement<UserSession>(json)
                onSuccess(userSession)
                val userJson = json["user"]?.jsonObject ?: buildJsonObject { }
                return decodeResult(userJson)
            }.onFailure { exception ->
                stitchdbClient.auth.logger.w(exception) { "Failed to decode user info" }
                return null
            }
        }
        return decodeResult(json)
    }

    @StitchInternal
    fun decodeResult(json: JsonObject): R

    @StitchInternal
    fun encodeCredentials(credentials: C.() -> Unit): JsonObject

}