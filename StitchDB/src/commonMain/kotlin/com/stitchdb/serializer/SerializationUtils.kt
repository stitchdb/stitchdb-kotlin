package com.stitchdb.serializer

import com.stitchdb.annotations.StitchInternal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@StitchInternal
fun JsonArray.mapValue(key: String) =
    map { it.jsonObject[key]?.jsonPrimitive?.contentOrNull ?: throw SerializationException("Key $key missing in object.\n Body: $this") }
