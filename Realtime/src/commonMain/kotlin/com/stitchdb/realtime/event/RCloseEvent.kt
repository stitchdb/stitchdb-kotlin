package com.stitchdb.realtime.event

import com.stitchdb.logging.d
import com.stitchdb.realtime.RealtimeChannel
import com.stitchdb.realtime.RealtimeMessage

/**
 * Event that handles the closing of a channel
 */
data object RCloseEvent : RealtimeEvent {

    override suspend fun handle(channel: RealtimeChannel, message: RealtimeMessage) {
        channel.realtime.removeChannel(channel)
        channel.logger.d { "Unsubscribed from channel ${message.topic}" }
        channel.updateStatus(RealtimeChannel.Status.UNSUBSCRIBED)
    }

    override fun appliesTo(message: RealtimeMessage): Boolean {
        return message.event == RealtimeChannel.CHANNEL_EVENT_CLOSE
    }

}