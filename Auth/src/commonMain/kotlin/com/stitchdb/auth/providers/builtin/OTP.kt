package com.stitchdb.auth.providers.builtin

import com.stitchdb.StitchClient
import com.stitchdb.StitchSerializer
import com.stitchdb.auth.AuthImpl
import com.stitchdb.auth.FlowType
import com.stitchdb.auth.auth
import com.stitchdb.auth.generateCodeChallenge
import com.stitchdb.auth.generateCodeVerifier
import com.stitchdb.auth.providers.AuthProvider
import com.stitchdb.auth.putCaptchaToken
import com.stitchdb.auth.user.UserSession
import com.stitchdb.encodeToJsonElement
import com.stitchdb.putJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One Time Password (OTP) [AuthProvider] for StitchDB.
 *
 * You need to provide either an email or a phone number.
 */
data object OTP: AuthProvider<OTP.Config, Unit> {

    /**
     * The configuration for the OTP authentication method
     *
     * Note: Only [email] or [phone] can be set
     */
    class Config(
        @PublishedApi internal val serializer: StitchSerializer,
    ) {
        /** The email of the user */
        var email: String? = null

        /** The phone number of the user */
        var phone: String? = null

        /** Additional data to store with the user */
        var data: JsonObject? = null

        /** Whether to create a new user if the user doesn't exist */
        var createUser: Boolean = true

        /** The captcha token for the request */
        var captchaToken: String? = null

        /** The channel to send the OTP to. Only applies when [phone] is set. Defaults to SMS when not specified. */
        var channel: Phone.Channel? = null

        /**
         * Sets [data] to the given value.
         *
         * Encoded using [Auth.serializer]
         */
        inline fun <reified T : Any> data(data: T) {
            this.data = serializer.encodeToJsonElement(data) as JsonObject
        }
    }

    private fun buildOtpRequestBody(config: Config): JsonObject = buildJsonObject {
        put("create_user", config.createUser)
        config.data?.let { put("data", it) }
        config.email?.let {
            put("email", it)
        } ?: config.phone?.let {
            put("phone", it)
            config.channel?.let { channel -> put("channel", channel.value) }
        }
    }

    override suspend fun login(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (Config.() -> Unit)?
    ) {
        val otpConfig = Config(stitchdbClient.auth.serializer).apply(config ?: {})

        require((otpConfig.email != null && otpConfig.email!!.isNotBlank()) || (otpConfig.phone != null && otpConfig.phone!!.isNotBlank())) { "You need to provide either an email or a phone number" }
        require(!(otpConfig.email != null && otpConfig.phone != null)) { "You can only provide either an email or a phone number" }

        val body = buildOtpRequestBody(otpConfig)
        var codeChallenge: String? = null
        if (stitchdbClient.auth.config.flowType == FlowType.PKCE) {
            val codeVerifier = generateCodeVerifier()
            stitchdbClient.auth.codeVerifierCache.saveCodeVerifier(codeVerifier)
            codeChallenge = generateCodeChallenge(codeVerifier)
        }
        (stitchdbClient.auth as AuthImpl).publicApi.postJson("otp", buildJsonObject {
            putJsonObject(body)
            codeChallenge?.let {
                put("code_challenge", it)
                put("code_challenge_method", "s256")
            }
            otpConfig.captchaToken?.let { putCaptchaToken(it) }
        }) {
            redirectUrl?.let { url.parameters.append("redirect_to", it) }
        }
    }

    override suspend fun signUp(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String?,
        config: (Config.() -> Unit)?
    ): Unit = login(stitchdbClient, onSuccess, redirectUrl, config)

}