package com.stitchdb.auth.providers.builtin

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.providers.Apple
import com.stitchdb.auth.providers.Azure
import com.stitchdb.auth.providers.Facebook
import com.stitchdb.auth.providers.Google
import com.stitchdb.auth.providers.IDTokenProvider
import com.stitchdb.auth.user.UserInfo
import com.stitchdb.exceptions.StitchEncodingException
import com.stitchdb.stitchdbJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Allows signing in with an OIDC ID token. The authentication provider used should be enabled and configured.
 *
 * Only [Apple], [Google], [Facebook] and [Azure] are supported as providers.
 *
 */
data object IDToken : DefaultAuthProvider<IDToken.Config, UserInfo> {

    override val grantType: String = "id_token"

    /**
     * The configuration for the id token authentication method
     * @param idToken OIDC ID token issued by the specified provider. The `iss` claim in the ID token must match the supplied provider. Some ID tokens contain an `at_hash` which require that you provide an `access_token` value to be accepted properly. If the token contains a `nonce` claim you must supply the nonce used to obtain the ID token.
     * @param provider The provider of the id token. Only [Apple], [Google], [Facebook] and [Azure] are supported
     * @param accessToken If the ID token contains an `at_hash` claim, then the hash of this value is compared to the value in the ID token.
     * @param nonce If the ID token contains a `nonce` claim, then the hash of this value is compared to the value in the ID token.
     */
    @Serializable
    data class Config(
        @SerialName("id_token") var idToken: String = "",
        var provider: IDTokenProvider? = null,
        @SerialName("access_token") var accessToken: String? = null,
        var nonce: String? = null,
        @property:StitchInternal @SerialName("link_identity") var linkIdentity: Boolean = false
    ) : DefaultAuthProvider.Config() {

        /**
         * This property is ignored by the ID token endpoint and has no effect.
         * It exists only for backward compatibility and will be removed in a future release.
         * Use [Email.Config.data] or [Phone.Config.data] for signup metadata instead.
         */
        @Deprecated("data is ignored by the ID token endpoint. It exists only for backward compatibility.", level = DeprecationLevel.WARNING)
        var data: JsonObject? = null
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun decodeResult(json: JsonObject): UserInfo = try {
        stitchdbJson.decodeFromJsonElement(json)
    } catch(e: MissingFieldException) {
        throw StitchEncodingException("Couldn't decode sign up id token result. Input: $json")
    }

    override fun encodeCredentials(credentials: Config.() -> Unit): JsonObject = stitchdbJson.encodeToJsonElement(Config().apply(credentials)).jsonObject

}