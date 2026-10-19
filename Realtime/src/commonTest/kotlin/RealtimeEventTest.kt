import com.stitchdb.realtime.RealtimeChannel
import com.stitchdb.realtime.RealtimeMessage
import com.stitchdb.realtime.event.RBroadcastEvent
import com.stitchdb.realtime.event.RCloseEvent
import com.stitchdb.realtime.event.RErrorEvent
import com.stitchdb.realtime.event.RPostgresChangesEvent
import com.stitchdb.realtime.event.RPostgresServerChangesEvent
import com.stitchdb.realtime.event.RPresenceDiffEvent
import com.stitchdb.realtime.event.RPresenceStateEvent
import com.stitchdb.realtime.event.RSystemEvent
import com.stitchdb.realtime.event.RSystemReplyEvent
import com.stitchdb.realtime.event.RTokenExpiredEvent
import com.stitchdb.realtime.event.RealtimeEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class RealtimeEventTest {

    @Test
    fun testSystemType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_SYSTEM,
            payload = buildJsonObject {
                put("status", "ok")
            },
            ref = ""
        )
        assertEquals(RSystemEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testSystemReplyType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_REPLY,
            payload = buildJsonObject {
                put("status", "ok")
            },
            ref = ""
        )
        assertEquals(RSystemReplyEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testPostgresServerChangesType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_REPLY,
            payload = buildJsonObject {
                put("response", buildJsonObject {
                    put(RealtimeChannel.CHANNEL_EVENT_POSTGRES_CHANGES, "")
                })
            },
            ref = ""
        )
        assertEquals(RPostgresServerChangesEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testPostgresChangesType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_POSTGRES_CHANGES,
            payload = buildJsonObject {
                put("key", "value")
            },
            ref = ""
        )
        assertEquals(RPostgresChangesEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testBroadcastType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_BROADCAST,
            payload = buildJsonObject {
                put("key", "value")
            },
            ref = ""
        )
        assertEquals(RBroadcastEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testCloseType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_CLOSE,
            payload = buildJsonObject {
                put("key", "value")
            },
            ref = ""
        )
        assertEquals(RCloseEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testErrorType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_ERROR,
            payload = buildJsonObject {
                put("key", "value")
            },
            ref = ""
        )
        assertEquals(RErrorEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testPresenceDiffType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_PRESENCE_DIFF,
            payload = buildJsonObject {
                put("key", "value")
            },
            ref = ""
        )
        assertEquals(RPresenceDiffEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testPresenceStateType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_PRESENCE_STATE,
            payload = buildJsonObject {
                put("key", "value")
            },
            ref = ""
        )
        assertEquals(RPresenceStateEvent, RealtimeEvent.resolveEvent(message))
    }

    @Test
    fun testTokenExpiredType() {
        val message = RealtimeMessage(
            topic = "",
            event = RealtimeChannel.CHANNEL_EVENT_SYSTEM,
            payload = buildJsonObject {
                put("message", "access token has expired")
            },
            ref = ""
        )
        assertEquals(RTokenExpiredEvent, RealtimeEvent.resolveEvent(message))
    }

}