package com.stitchdb.realtime.event

import com.stitchdb.realtime.PostgresAction
import com.stitchdb.realtime.RealtimeChannel
import com.stitchdb.realtime.RealtimeMessage
import com.stitchdb.realtime.data.PostgresActionData
import com.stitchdb.realtime.realtime
import com.stitchdb.stitchdbJson
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles postgres changes events
 */
data object RPostgresChangesEvent : RealtimeEvent {

    override suspend fun handle(channel: RealtimeChannel, message: RealtimeMessage) {
        val data = message.payload["data"]?.jsonObject ?: return
        val ids = message.payload["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList() //the ids of the matching postgres changes
        val postgresAction = stitchdbJson.decodeFromJsonElement<PostgresActionData>(data)
        val action = when(data["type"]?.jsonPrimitive?.content ?: "") {
            "UPDATE" -> PostgresAction.Update(
                postgresAction.record ?: error("Received no record on update event"),
                postgresAction.oldRecord ?: error("Received no old record on update event"),
                postgresAction.columns,
                postgresAction.commitTimestamp,
                channel.stitchdbClient.realtime.serializer
            )
            "DELETE" -> PostgresAction.Delete(
                postgresAction.oldRecord ?: error("Received no old record on delete event"),
                postgresAction.columns,
                postgresAction.commitTimestamp,
                channel.stitchdbClient.realtime.serializer
            )
            "INSERT" -> PostgresAction.Insert(
                postgresAction.record ?: error("Received no record on update event"),
                postgresAction.columns,
                postgresAction.commitTimestamp,
                channel.stitchdbClient.realtime.serializer
            )
            "SELECT" -> PostgresAction.Select(
                postgresAction.record ?: error("Received no record on update event"),
                postgresAction.columns,
                postgresAction.commitTimestamp,
                channel.stitchdbClient.realtime.serializer
            )
            else -> error("Unknown event type ${message.event}")
        }
        channel.callbackManager.triggerPostgresChange(ids, action)
    }

    override fun appliesTo(message: RealtimeMessage): Boolean {
        return message.event == RealtimeChannel.CHANNEL_EVENT_POSTGRES_CHANGES
    }

}