package com.stitchdb.realtime

import com.stitchdb.annotations.StitchInternal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a message retrieved by the [RealtimeChannel]
 */
@Serializable
@StitchInternal
data class RealtimeMessage(val topic: String, val event: String, val payload: JsonObject, val ref: String?, val joinRef: String? = null)