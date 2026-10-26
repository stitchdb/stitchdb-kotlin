import com.stitchdb.StitchClient
import com.stitchdb.createStitchClient
import com.stitchdb.plugins.SessionTokenSource
import com.stitchdb.plugins.StitchPlugin
import com.stitchdb.plugins.StitchPluginProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AnalyticsTest {

    private class Sent(val method: String, val path: String, val body: String, val authorization: String?, val apikey: String?) {
        val json: JsonObject get() = Json.parseToJsonElement(body).jsonObject
    }

    /** A server that records every request; [trackStatus] answers POST /analytics/v1/track. */
    private class FakeServer(var trackStatus: HttpStatusCode = HttpStatusCode.OK) {
        val sent: MutableList<Sent> = Collections.synchronizedList(mutableListOf())
        fun batches() = sent.filter { it.path == "/analytics/v1/track" }
        fun events() = batches().flatMap { it.json["events"]!!.jsonArray.map { e -> e.jsonObject } }
        fun names() = events().map { it["name"]!!.jsonPrimitive.content }
    }

    /** Stands in for Auth: the signed-in user and the session token. */
    private class FakeAuth(override val stitchdbClient: StitchClient, override val config: Unit) : StitchPlugin<Unit>, SessionTokenSource {
        override fun currentAccessTokenOrNull(): String? = user.value?.let { "token-of-$it" }
        override val signedInUserIds = user

        companion object : StitchPluginProvider<Unit, FakeAuth> {
            val user = MutableStateFlow<String?>(null)
            override val key = "fake-auth"
            override fun createConfig(init: Unit.() -> Unit) = Unit
            override fun create(stitchdbClient: StitchClient, config: Unit) = FakeAuth(stitchdbClient, config)
        }
    }

    private val dirs = mutableListOf<File>()
    private val clients = mutableListOf<StitchClient>()

    private fun tempDir(): File = Files.createTempDirectory("stitchdb-analytics-test").toFile().also { dirs += it }

    private fun client(server: FakeServer, withAuth: Boolean = false): StitchClient = createStitchClient("https://projectref.stitchdb.com", "project-anon-key") {
        if (withAuth) install(FakeAuth)
        httpEngine = MockEngine { req ->
            server.sent += Sent(req.method.value, req.url.encodedPath, (req.body as? TextContent)?.text ?: "", req.headers[HttpHeaders.Authorization], req.headers["apikey"])
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (req.url.encodedPath) {
                "/analytics/v1/track" -> respond("""{"received":1,"dropped":0}""", server.trackStatus, json)
                "/analytics/v1/opt-out" -> respond("""{"opted_out":true,"deleted":3}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
    }.also { clients += it }

    @AfterTest
    fun cleanUp() = runBlocking<Unit> {
        clients.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
        FakeAuth.user.value = null
    }

    @Test
    fun queuedBeforeStartAndSentAsOneBatch() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        val dir = tempDir()
        stitch.analytics.track("app_open")
        stitch.analytics.track("bad name!")
        stitch.analytics.track("")
        stitch.analytics.track("purchase", mapOf("plan" to "pro", "amount" to 29, "tags" to listOf("a", "b"), "gift" to null))
        stitch.analytics.flush()
        assertTrue(server.sent.isEmpty(), "nothing is sent before start")
        assertEquals(2, stitch.analytics.queued.value)
        assertTrue(dir.listFiles()!!.none { it.name == "queue.json" }, "events before start wait in memory")

        stitch.analytics.start(release = "2.0.0", flushInterval = 60.seconds, directory = dir.path)
        stitch.analytics.flush()

        val batch = server.batches().single()
        val body = batch.json
        assertEquals(stitch.analytics.anonymousId, body["anon_id"]!!.jsonPrimitive.content)
        assertEquals(File(dir, "anon").readText(), stitch.analytics.anonymousId)
        assertNotNull(body["session_id"]!!.jsonPrimitive.content)
        assertEquals("2.0.0", body["release"]!!.jsonPrimitive.content)
        assertEquals("kotlin", body["platform"]!!.jsonPrimitive.content)
        assertTrue(body["os"]!!.jsonPrimitive.content.isNotBlank())
        java.util.Locale.getDefault().country.takeIf { it.length == 2 }?.let { assertEquals(it, body["country"]!!.jsonPrimitive.content) }
        assertEquals(listOf("app_open", "purchase"), server.names())
        val purchase = server.events()[1]
        val props = purchase["props"]!!.jsonObject
        assertEquals("pro", props["plan"]!!.jsonPrimitive.content)
        assertEquals("29", props["amount"]!!.jsonPrimitive.content)
        assertEquals(2, props["tags"]!!.jsonArray.size)
        assertEquals(JsonNull, props["gift"])
        assertTrue(purchase["at"]!!.jsonPrimitive.content.startsWith("20"))
        assertEquals("Bearer project-anon-key", batch.authorization)
        assertEquals("project-anon-key", batch.apikey)
        assertEquals(0, stitch.analytics.queued.value)
        assertFalse(File(dir, "queue.json").exists())

        // the same session id for the whole run
        stitch.analytics.track("checkout")
        stitch.analytics.flush()
        assertEquals(body["session_id"], server.batches().last().json["session_id"])
    }

    @Test
    fun twentyEventsAreSentWithoutWaitingForTheTimer() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        stitch.analytics.start(flushInterval = 60.seconds, directory = tempDir().path)
        stitch.analytics.flush()
        repeat(20) { stitch.analytics.track("step $it") }
        withTimeout(5.seconds) { while (server.events().size < 20) delay(20) }
        assertEquals(JsonNull, server.batches().first().json["release"])
    }

    @Test
    fun beforeSendCanChangeOrDropEvents() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        stitch.analytics.start(
            flushInterval = 60.seconds,
            directory = tempDir().path,
            beforeSend = { e -> if (e.props["secret"] != null) null else e.copy(name = e.name.uppercase()) },
        )
        stitch.analytics.track("kept")
        stitch.analytics.track("dropped", mapOf("secret" to "x"))
        stitch.analytics.track("too big", mapOf("blob" to "x".repeat(9000)))
        stitch.analytics.flush()
        assertEquals(listOf("KEPT"), server.names())
    }

    @Test
    fun optOutDeletesServerSideAndStopsSending() = runBlocking<Unit> {
        val server = FakeServer()
        val dir = tempDir()
        val stitch = client(server)
        stitch.analytics.start(flushInterval = 60.seconds, directory = dir.path)
        stitch.analytics.flush()
        stitch.analytics.track("before")
        stitch.analytics.optOut()
        val optOut = server.sent.single { it.path == "/analytics/v1/opt-out" }
        assertEquals("POST", optOut.method)
        assertEquals("{}", optOut.body)
        assertTrue(stitch.analytics.isOptedOut)
        stitch.analytics.track("after")
        stitch.analytics.flush()
        assertTrue(server.batches().isEmpty())
        assertEquals(0, stitch.analytics.queued.value)
        stitch.close()

        // remembered on the device
        val next = client(server)
        next.analytics.start(flushInterval = 60.seconds, directory = dir.path)
        assertTrue(next.analytics.isOptedOut)
        next.analytics.track("still opted out")
        next.analytics.flush()
        assertTrue(server.batches().isEmpty())

        next.analytics.optIn()
        assertEquals("DELETE", server.sent.last { it.path == "/analytics/v1/opt-out" }.method)
        assertFalse(next.analytics.isOptedOut)
        next.analytics.track("back")
        next.analytics.flush()
        assertEquals(listOf("back"), server.names())
    }

    @Test
    fun notFoundClearsTheQueueAndStopsSending() = runBlocking<Unit> {
        val server = FakeServer(HttpStatusCode.NotFound)
        val dir = tempDir()
        val stitch = client(server)
        stitch.analytics.start(flushInterval = 60.seconds, directory = dir.path)
        stitch.analytics.track("one")
        stitch.analytics.flush()
        assertEquals(1, server.batches().size)
        stitch.analytics.track("two")
        stitch.analytics.track("three")
        stitch.analytics.flush()
        assertEquals(1, server.batches().size)
        assertEquals(0, stitch.analytics.queued.value)
        assertFalse(File(dir, "queue.json").exists())
    }

    @Test
    fun serverBusyKeepsTheQueueOnDiskForTheNextStart() = runBlocking<Unit> {
        val dir = tempDir()
        val down = FakeServer(HttpStatusCode.ServiceUnavailable)
        val first = client(down)
        first.analytics.start(release = "1.0.0", flushInterval = 60.seconds, directory = dir.path)
        first.analytics.track("offline_event", mapOf("n" to 1))
        first.analytics.flush()
        assertTrue(down.batches().isNotEmpty()) // the start-up send and flush() may both try
        assertEquals(1, first.analytics.queued.value)
        assertTrue(File(dir, "queue.json").readText().contains("offline_event"))
        val anon = first.analytics.anonymousId
        first.close()

        val up = FakeServer()
        val second = client(up)
        second.analytics.start(release = "1.0.0", flushInterval = 60.seconds, directory = dir.path)
        assertEquals(1, second.analytics.queued.value)
        second.analytics.flush()
        assertEquals(listOf("offline_event"), up.names())
        assertEquals(anon, up.batches().single().json["anon_id"]!!.jsonPrimitive.content)
        assertEquals(0, second.analytics.queued.value)
        assertFalse(File(dir, "queue.json").exists())
    }

    @Test
    fun signInFlushesAndBatchesUseTheSessionToken() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server, withAuth = true)
        stitch.analytics.start(flushInterval = 60.seconds, directory = tempDir().path)
        stitch.analytics.flush()
        stitch.analytics.track("browsed")
        FakeAuth.user.value = "user-1" // sign-in: what waits is sent without waiting for the timer
        withTimeout(5.seconds) { while (server.batches().isEmpty()) delay(20) }
        assertEquals(listOf("browsed"), server.names())
        assertEquals("Bearer token-of-user-1", server.batches().single().authorization)
    }

    @Test
    fun analyticsCallsAreNoCrashBreadcrumbs() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        val dir = tempDir()
        stitch.crashes.start(directory = File(dir, "crashes").path, captureUnhandled = false)
        stitch.analytics.start(flushInterval = 60.seconds, directory = File(dir, "analytics").path)
        stitch.analytics.track("tapped")
        stitch.analytics.flush()
        assertEquals(1, server.batches().size)
        stitch.crashes.captureMessage("check")
        stitch.crashes.flush()
        val report = server.sent.single { it.path == "/crashes/v1/report" }
        assertFalse("/analytics/v1/" in report.body, report.body)
    }
}
