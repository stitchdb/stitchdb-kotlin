package com.stitchdb.serializer

import com.stitchdb.StitchSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * A [StitchSerializer] that uses kotlinx.serialization
 */
class KotlinXSerializer(private val json: Json = Json) : StitchSerializer {

    override fun <T> encode(type: KType, value: T): String = json.encodeToString(json.serializersModule.serializer(type), value)

    @Suppress("UNCHECKED_CAST")
    override fun <T> decode(type: KType, value: String): T =
        json.decodeFromString(json.serializersModule.serializer(type), value) as T

}