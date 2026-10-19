package com.stitchdb.realtime.event

import com.stitchdb.decodeIfNotEmptyOrDefault
import com.stitchdb.realtime.Presence
import com.stitchdb.realtime.RealtimeChannel
import com.stitchdb.realtime.RealtimeMessage
import kotlinx.serialization.json.jsonObject

/**
 * Event that handles the presence diff event
 */
data object RPresenceDiffEvent : RealtimeEvent {

    override suspend fun handle(channel: RealtimeChannel, message: RealtimeMessage) {
        val joins = message.payload["joins"]?.jsonObject?.decodeIfNotEmptyOrDefault(mapOf<String, Presence>()) ?: emptyMap()
        val leaves = message.payload["leaves"]?.jsonObject?.decodeIfNotEmptyOrDefault(mapOf<String, Presence>()) ?: emptyMap()
        channel.callbackManager.triggerPresenceDiff(joins, leaves)
    }

    override fun appliesTo(message: RealtimeMessage): Boolean {
        return message.event == RealtimeChannel.CHANNEL_EVENT_PRESENCE_DIFF
    }

}