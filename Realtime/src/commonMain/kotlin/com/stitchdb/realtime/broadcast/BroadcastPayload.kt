package com.stitchdb.realtime.broadcast

import com.stitchdb.realtime.RealtimeChannel
import kotlinx.serialization.json.JsonElement

/**
 * Represents the payload used in [RealtimeChannel.httpSend] and [RealtimeChannel.broadcast]
 */
sealed interface BroadcastPayload {

    /**
     * JSON-encoded payload via [value]
     */
    data class Json(val value: JsonElement): BroadcastPayload

    /**
     * Binary payload via [data]
     */
    class Binary(val data: ByteArray): BroadcastPayload

}