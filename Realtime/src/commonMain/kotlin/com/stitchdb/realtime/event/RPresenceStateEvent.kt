package com.stitchdb.realtime.event

import com.stitchdb.decodeIfNotEmptyOrDefault
import com.stitchdb.realtime.Presence
import com.stitchdb.realtime.RealtimeChannel
import com.stitchdb.realtime.RealtimeMessage

/**
 * Event that handles the presence state event
 */
data object RPresenceStateEvent : RealtimeEvent {

    override suspend fun handle(channel: RealtimeChannel, message: RealtimeMessage) {
        val joins = message.payload.decodeIfNotEmptyOrDefault(mapOf<String, Presence>())
        channel.callbackManager.triggerPresenceDiff(joins, mapOf())
    }

    override fun appliesTo(message: RealtimeMessage): Boolean {
        return message.event == RealtimeChannel.CHANNEL_EVENT_PRESENCE_STATE
    }

}