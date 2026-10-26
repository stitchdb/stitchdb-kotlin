import com.stitchdb.StitchClient
import com.stitchdb.createStitchClient
import com.stitchdb.crashes.CrashHandler
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrashesTest {

    private class Sent(val path: String, val body: String, val authorization: String?, val apikey: String?)

    /** A server that records every request; [reportStatus] answers POST /crashes/v1/report. */
    private class FakeServer(var reportStatus: HttpStatusCode = HttpStatusCode.OK) {
        val sent: MutableList<Sent> = Collections.synchronizedList(mutableListOf())
        fun reports() = sent.filter { it.path == "/crashes/v1/report" }.flatMap { Json.parseToJsonElement(it.body).jsonObject["reports"]!!.jsonArray.map { r -> r.jsonObject } }
        fun count(path: String) = sent.count { it.path == path }
    }

    private val dirs = mutableListOf<File>()
    private val clients = mutableListOf<StitchClient>()

    private fun tempDir(): File = Files.createTempDirectory("stitchdb-crashes-test").toFile().also { dirs += it }

    private fun client(server: FakeServer): StitchClient = createStitchClient("https://projectref.stitchdb.com", "project-anon-key") {
        httpEngine = MockEngine { req ->
            server.sent += Sent(req.url.encodedPath, (req.body as? TextContent)?.text ?: "", req.headers[HttpHeaders.Authorization], req.headers["apikey"])
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (req.url.encodedPath) {
                "/crashes/v1/report" -> respond("""{"received":1,"stored":1,"dropped":0,"issues":[]}""", server.reportStatus, json)
                "/crashes/v1/session" -> respond("{}", HttpStatusCode.OK, json)
                "/rest/v1/orders" -> respond("""{"code":"PGRST116","message":"nope"}""", HttpStatusCode.InternalServerError, json)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }
    }.also { clients += it }

    @AfterTest
    fun cleanUp() = runBlocking<Unit> {
        clients.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun reportIsSentWithApiBreadcrumbWithoutQueryValues() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        stitch.crashes.start(release = "1.4.0", directory = tempDir().path)
        stitch.httpClient.get("https://projectref.stitchdb.com/rest/v1/orders?select=*&id=eq.secret5")
        stitch.crashes.addBreadcrumb(type = "ui", message = "tapped Checkout")
        stitch.crashes.captureException(IllegalStateException("checkout failed", IllegalArgumentException("bad card")), context = mapOf("step" to "payment", "attempt" to 2))
        stitch.crashes.flush()

        val report = server.reports().single()
        assertEquals("kotlin", report["platform"]!!.jsonPrimitive.content)
        assertEquals("1.4.0", report["release"]!!.jsonPrimitive.content)
        assertEquals("production", report["environment"]!!.jsonPrimitive.content)
        assertTrue(report["handled"]!!.jsonPrimitive.content.toBoolean())
        val exception = report["exception"]!!.jsonObject
        assertEquals("java.lang.IllegalStateException", exception["type"]!!.jsonPrimitive.content)
        assertEquals("checkout failed", exception["message"]!!.jsonPrimitive.content)
        val stack = exception["stack"]!!.jsonPrimitive.content
        assertTrue("at CrashesTest" in stack, stack)
        assertTrue("Caused by: java.lang.IllegalArgumentException: bad card" in stack, stack)
        assertEquals("payment", report["context"]!!.jsonObject["step"]!!.jsonPrimitive.content)
        assertEquals("2", report["context"]!!.jsonObject["attempt"]!!.jsonPrimitive.content)
        assertTrue(report["device"]!!.jsonObject["sdk"]!!.jsonPrimitive.content.startsWith("stitchdb-kotlin/"))
        assertNotNull(report["device"]!!.jsonObject["os"])

        val crumbs = report["breadcrumbs"]!!.jsonArray.map { it.jsonObject }
        val api = crumbs.single { it["type"]!!.jsonPrimitive.content == "api" }
        assertEquals("GET /rest/v1/orders?select&id 500", api["message"]!!.jsonPrimitive.content)
        assertEquals("error", api["level"]!!.jsonPrimitive.content)
        val data = api["data"] as JsonObject
        assertEquals("/rest/v1/orders?select&id", data["url"]!!.jsonPrimitive.content)
        assertEquals("GET", data["method"]!!.jsonPrimitive.content)
        assertEquals("500", data["status"]!!.jsonPrimitive.content)
        assertEquals("PGRST116", data["error_code"]!!.jsonPrimitive.content)
        assertNotNull(data["ms"])
        assertFalse("secret5" in report.toString())
        assertEquals("tapped Checkout", crumbs.last()["message"]!!.jsonPrimitive.content)
        // calls to /crashes/v1/ itself are no breadcrumbs
        assertTrue(crumbs.none { "/crashes/v1/" in it.toString() })

        val sent = server.sent.first { it.path == "/crashes/v1/report" }
        assertEquals("Bearer project-anon-key", sent.authorization)
        assertEquals("project-anon-key", sent.apikey)
        assertEquals(0, stitch.crashes.queued.value)
    }

    @Test
    fun nothingBeforeStartAndBeforeSendNullDrops() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        val dir = tempDir()
        stitch.crashes.captureException(RuntimeException("before start"))
        stitch.crashes.flush()
        assertTrue(server.sent.isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty())

        var seen = 0
        stitch.crashes.start(directory = dir.path, beforeSend = { seen++; null })
        stitch.crashes.captureException(RuntimeException("not consented"))
        stitch.crashes.captureMessage("should never happen")
        stitch.crashes.flush()
        assertEquals(2, seen)
        assertEquals(0, server.count("/crashes/v1/report"))
        assertEquals(0, stitch.crashes.queued.value)
        assertFalse(File(dir, "queue.json").exists())
    }

    @Test
    fun notFoundStopsSending() = runBlocking<Unit> {
        val server = FakeServer(HttpStatusCode.NotFound)
        val stitch = client(server)
        val dir = tempDir()
        stitch.crashes.start(directory = dir.path)
        stitch.crashes.flush()
        stitch.crashes.captureException(RuntimeException("one"))
        stitch.crashes.flush()
        assertEquals(1, server.count("/crashes/v1/report"))
        stitch.crashes.captureException(RuntimeException("two"))
        stitch.crashes.captureException(RuntimeException("three"))
        stitch.crashes.flush()
        assertEquals(1, server.count("/crashes/v1/report"))
        // the queue is cleared, so these are not sent on the next start either
        assertEquals(0, stitch.crashes.queued.value)
        assertFalse(File(dir, "queue.json").exists())
    }

    @Test
    fun failedSendIsKeptOnDiskAndSentOnNextStart() = runBlocking<Unit> {
        val dir = tempDir()
        val down = FakeServer(HttpStatusCode.ServiceUnavailable)
        val first = client(down)
        first.crashes.start(release = "2.0.0", directory = dir.path)
        first.crashes.captureException(RuntimeException("while the server was down"))
        first.crashes.flush()
        assertEquals(1, first.crashes.queued.value)
        assertTrue(File(dir, "queue.json").readText().contains("while the server was down"))
        val installId = File(dir, "install").readText()
        first.close()

        val up = FakeServer()
        val second = client(up)
        second.crashes.start(release = "2.0.0", directory = dir.path)
        assertEquals(1, second.crashes.queued.value)
        second.crashes.flush()
        val report = up.reports().single()
        assertEquals("while the server was down", report["exception"]!!.jsonObject["message"]!!.jsonPrimitive.content)
        assertEquals(installId, report["install_id"]!!.jsonPrimitive.content)
        assertEquals(0, second.crashes.queued.value)
        assertFalse(File(dir, "queue.json").exists())
    }

    @Test
    fun uncaughtHandlerWritesFileAndChainsToPreviousHandler() = runBlocking<Unit> {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val chained = mutableListOf<Throwable>()
        val previous = Thread.UncaughtExceptionHandler { _, e -> chained += e }
        Thread.setDefaultUncaughtExceptionHandler(previous)
        try {
            val dir = tempDir()
            val stitch = client(FakeServer(HttpStatusCode.ServiceUnavailable)) // keep the report on disk
            stitch.crashes.start(release = "3.1.0", directory = dir.path)
            stitch.crashes.flush()
            stitch.crashes.addBreadcrumb(type = "navigation", message = "/checkout")
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            assertTrue(handler is CrashHandler)

            val crash = IllegalStateException("crashed on a worker")
            handler.uncaughtException(Thread({}, "worker-7"), crash)

            // written before the handler returned, without waiting for any coroutine
            val queued = Json.parseToJsonElement(File(dir, "queue.json").readText()).jsonArray.single().jsonObject
            assertEquals("false", queued["handled"]!!.jsonPrimitive.content)
            assertEquals("crashed on a worker", queued["exception"]!!.jsonObject["message"]!!.jsonPrimitive.content)
            assertEquals("UncaughtExceptionHandler", queued["exception"]!!.jsonObject["mechanism"]!!.jsonPrimitive.content)
            assertEquals("worker-7", queued["context"]!!.jsonObject["thread"]!!.jsonPrimitive.content)
            assertEquals("/checkout", queued["breadcrumbs"]!!.jsonArray.last().jsonObject["message"]!!.jsonPrimitive.content)
            assertEquals(listOf<Throwable>(crash), chained)

            stitch.crashes.stop()
            assertSame(previous, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun sessionIsSentOnceADay() = runBlocking<Unit> {
        val dir = tempDir()
        val server = FakeServer()
        val first = client(server)
        first.crashes.start(release = "1.0.0", directory = dir.path)
        first.crashes.flush()
        assertEquals(1, server.count("/crashes/v1/session"))
        val body = Json.parseToJsonElement(server.sent.first { it.path == "/crashes/v1/session" }.body).jsonObject
        assertEquals("1.0.0", body["release"]!!.jsonPrimitive.content)
        assertEquals(File(dir, "install").readText(), body["install_id"]!!.jsonPrimitive.content)
        first.close()

        val second = client(server)
        second.crashes.start(release = "1.0.0", directory = dir.path)
        second.crashes.flush()
        assertEquals(1, server.count("/crashes/v1/session"))

        // a new release counts again
        val third = client(server)
        third.crashes.start(release = "1.0.1", directory = dir.path)
        third.crashes.flush()
        assertEquals(2, server.count("/crashes/v1/session"))
    }
}
