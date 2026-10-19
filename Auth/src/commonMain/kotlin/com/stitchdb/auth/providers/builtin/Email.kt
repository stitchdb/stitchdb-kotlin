package com.stitchdb.auth.providers.builtin

import com.stitchdb.auth.user.UserInfo
import com.stitchdb.exceptions.StitchEncodingException
import com.stitchdb.stitchdbJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Authentication method with email and password
 */
data object Email : DefaultAuthProvider<Email.Config, UserInfo> {

    override val grantType: String = "password"

    /**
     * The configuration for the email authentication method
     * @param email The email of the user
     * @param password The password of the user
     * @param data Extra user metadata to include on signup
     */
    @Serializable
    data class Config(var email: String = "", var password: String = "", var data: JsonObject? = null): DefaultAuthProvider.Config()

    @OptIn(ExperimentalSerializationApi::class)
    override fun decodeResult(json: JsonObject): UserInfo = try {
        stitchdbJson.decodeFromJsonElement(json)
    } catch(e: MissingFieldException) {
        throw StitchEncodingException("Couldn't decode sign up email result. Input: $json")
    }

    override fun encodeCredentials(credentials: Config.() -> Unit): JsonObject = stitchdbJson.encodeToJsonElement(Config().apply(credentials)).jsonObject

}