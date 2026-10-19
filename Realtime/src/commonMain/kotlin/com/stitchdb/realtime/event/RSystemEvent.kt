package com.stitchdb.realtime.event

import com.stitchdb.logging.d
import com.stitchdb.realtime.RealtimeChannel
import com.stitchdb.realtime.RealtimeMessage
import com.stitchdb.realtime.RealtimeSystemPayload
import com.stitchdb.stitchdbJson
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Event that handles the system event
 */
data object RSystemEvent : RealtimeEvent {

    override suspend fun handle(channel: RealtimeChannel, message: RealtimeMessage) {
        channel.logger.d { "Subscribed to channel ${message.topic}" }
        channel.updateStatus(RealtimeChannel.Status.SUBSCRIBED)
        val payload = stitchdbJson.decodeFromJsonElement<RealtimeSystemPayload>(message.payload)
        channel.callbackManager.triggerSystem(payload)
    }

    override fun appliesTo(message: RealtimeMessage): Boolean {
        return  message.event == RealtimeChannel.CHANNEL_EVENT_SYSTEM && message.payload["status"]?.jsonPrimitive?.content == "ok"
    }

}
