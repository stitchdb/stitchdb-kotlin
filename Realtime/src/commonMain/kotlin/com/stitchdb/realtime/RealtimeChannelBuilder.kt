package com.stitchdb.realtime

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.realtime.annotations.ChannelDsl

/**
 * Used to build a realtime channel
 */
@ChannelDsl
class RealtimeChannelBuilder @PublishedApi internal constructor(
    private val topic: String,
) {

    private var broadcastJoinConfig = BroadcastJoinConfig(acknowledgeBroadcasts = false, receiveOwnBroadcasts = false, replicationReady = false)
    private var presenceJoinConfig = PresenceJoinConfig("", false)

    /**
     * Whether this channel should be private.
     */
    var isPrivate = false

    /**
     * Sets the broadcast join config
     */
    fun broadcast(block: BroadcastJoinConfig.() -> Unit) {
        broadcastJoinConfig = BroadcastJoinConfig(acknowledgeBroadcasts = false, receiveOwnBroadcasts = false, replicationReady = false).apply(block)
    }

    /**
     * Sets the presence join config
     * @param block The presence join config
     */
    fun presence(block: PresenceJoinConfig.() -> Unit) {
        presenceJoinConfig = PresenceJoinConfig("", false).apply(block)
    }

    @StitchInternal
    fun build(realtime: Realtime): RealtimeChannel {
        return RealtimeChannelImpl(
            realtime,
            topic,
            broadcastJoinConfig,
            presenceJoinConfig,
            isPrivate
        )
    }

}