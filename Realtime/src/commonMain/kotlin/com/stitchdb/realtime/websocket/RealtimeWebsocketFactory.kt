package com.stitchdb.realtime.websocket

import com.stitchdb.annotations.StitchInternal

/**
 * Interface for creating a websocket connection to the StitchDB Realtime service.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(StitchInternal::class)
interface RealtimeWebsocketFactory {

    /**
     * Create a new websocket connection to the given URL.
     * @param url The URL to connect to.
     */
    suspend fun create(url: String): RealtimeWebsocket

}