package com.stitchdb.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stitchdb.StitchSerializer
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * A [StitchSerializer] that uses jackson-module-kotlin
 */
class JacksonSerializer(private val mapper: ObjectMapper = jacksonObjectMapper()) : StitchSerializer {

    override fun <T> encode(type: KType, value: T): String = mapper.writeValueAsString(value)

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T> decode(type: KType, value: String): T = mapper.readValue(value, mapper.constructType(type.javaType)) as T

}