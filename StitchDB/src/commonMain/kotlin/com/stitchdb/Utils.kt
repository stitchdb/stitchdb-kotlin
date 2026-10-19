package com.stitchdb

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.exceptions.StitchEncodingException
import com.stitchdb.logging.i
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.decodeFromJsonElement

@StitchInternal
val stitchdbJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

@OptIn(ExperimentalSerializationApi::class)
@StitchInternal
suspend inline fun <reified T> HttpResponse.safeBody(context: String? = null): T {
    val text = bodyAsText()
    val contextMessage = if(context != null) " in $context" else ""
    return try {
        stitchdbJson.decodeFromString(text)
    } catch(e: MissingFieldException) {
        throw StitchEncodingException("Couldn't decode payload$contextMessage as ${T::class.simpleName}. Input: ${text.replace("\n", "")}")
    }
}

@StitchInternal
fun String.toJsonObject(): JsonObject = stitchdbJson.decodeFromString(this)

@StitchInternal
fun JsonObjectBuilder.putJsonObject(jsonObject: JsonObject) {
    for (key in jsonObject.keys) {
        put(key, jsonObject[key]!!)
    }
}

@StitchInternal
inline fun <reified T> JsonObject.decodeIfNotEmptyOrDefault(default: T): T {
    return if(isEmpty()) {
        default
    } else {
        stitchdbJson.decodeFromJsonElement<T>(this)
    }
}

@StitchInternal
suspend inline fun <reified T> StitchClient.bodyOrNull(response: HttpResponse): T? {
    val text = response.bodyAsText()
    return try {
        stitchdbJson.decodeFromString<T>(text)
    } catch(e: SerializationException) {
        logger.i(e) { "Could not decode ${text.take(200)} as ${T::class.simpleName}." }
        null
    }
}