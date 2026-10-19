package com.stitchdb.realtime.websocket

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.realtime.RealtimeMessage
import com.stitchdb.realtime.RealtimeProtocolVersion
import io.ktor.websocket.Frame

/**
 * Interface for a websocket connection to the StitchDB Realtime service.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(StitchInternal::class)
interface RealtimeWebsocket {

    /**
     * Whether there are incoming messages that can be received.
     */
    val hasIncomingMessages: Boolean

    /**
     * Generates a new ref for a realtime message
     */
    fun makeRef(): String

    /**
     * Receive a frame from the websocket.
     */
    suspend fun receive(): Frame

    /**
     * Send a message to the websocket.
     * @param message The message to send
     * @param vsn Depending on the vsn, the message gets encoded differently
     */
    suspend fun send(message: RealtimeMessage, vsn: RealtimeProtocolVersion)

    /**
     * Sends a binary message to the websocket.
     */
    suspend fun send(data: ByteArray)

    /**
     * Block the current coroutine until the websocket is disconnected.
     */
    suspend fun blockUntilDisconnect()

    /**
     * Disconnect the websocket.
     */
    fun disconnect()

}