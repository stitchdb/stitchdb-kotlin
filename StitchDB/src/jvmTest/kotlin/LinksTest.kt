import com.stitchdb.StitchClient
import com.stitchdb.createStitchClient
import com.stitchdb.links.LinkInput
import com.stitchdb.links.LinksClient
import com.stitchdb.links.LinksException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LinksTest {

    private class Sent(val method: String, val url: String, val body: String, val authorization: String?, val apikey: String?) {
        val json: JsonObject get() = Json.parseToJsonElement(body).jsonObject
    }

    private val link = """{"code":"aB3kP9x","url":"https://go.shop.test/l/aB3kP9x","path":"/product/42","data":{"promo":"SPRING"}}"""
    private val full = """{"code":"summer","url":"https://go.shop.test/l/summer","path":"/invite/7","data":{"from":"ana"},
        "web_url":null,"ios_url":null,"android_url":null,"title":"Hi","description":null,"image_url":null,
        "expires_at":"2026-12-01T00:00:00.000Z","disabled":false,"source":"api","created_by":null,"clicks":3,"opens":2,
        "installs":1,"last_click_at":null,"created_at":"2026-10-26T00:00:00.000Z","updated_at":"2026-10-26T00:00:00.000Z"}"""

    /** Records every request; [deferredMatch] answers POST /links/v1/deferred (null: `{"match":null}`). */
    private class FakeServer(var deferredMatch: String? = null, var deferredStatus: HttpStatusCode = HttpStatusCode.OK) {
        val sent: MutableList<Sent> = Collections.synchronizedList(mutableListOf())
        fun deferredCalls() = sent.filter { it.url.endsWith("/links/v1/deferred") }
    }

    private val dirs = mutableListOf<File>()
    private val clients = mutableListOf<StitchClient>()

    private fun tempDir(): File = Files.createTempDirectory("stitchdb-links-test").toFile().also { dirs += it }

    private fun client(server: FakeServer): StitchClient = createStitchClient("https://projectref.stitchdb.com", "project-anon-key") {
        httpEngine = MockEngine { req ->
            server.sent += Sent(req.method.value, req.url.toString(), (req.body as? TextContent)?.text ?: "", req.headers[HttpHeaders.Authorization], req.headers["apikey"])
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath
            when {
                path == "/links/v1/resolve/old" -> respond("""{"message":"This link has expired","code":"old"}""", HttpStatusCode.Gone, json)
                path.startsWith("/links/v1/resolve/") -> respond(link, HttpStatusCode.OK, json)
                path == "/links/v1/deferred" -> respond("""{"match":${server.deferredMatch}}""", server.deferredStatus, json)
                path == "/links/v1/links" && req.method == HttpMethod.Post -> respond(full, HttpStatusCode.Created, json)
                path == "/links/v1/links" -> respond("""{"links":[$full],"total":1}""", HttpStatusCode.OK, json)
                path == "/links/v1/links/taken" -> respond("""{"message":"There is a link with the code taken already"}""", HttpStatusCode.Conflict, json)
                path == "/links/v1/links/summer" && req.method == HttpMethod.Get -> respond(
                    """{"link":$full,"days":7,"daily":[{"day":"2026-10-25","clicks":3,"opens":2,"installs":1}],
                        "platforms":[{"k":"ios","n":2}],"countries":[{"k":null,"n":3}],"outcomes":[],"referrers":[]}""",
                    HttpStatusCode.OK, json,
                )
                path == "/links/v1/links/summer" && req.method == HttpMethod.Delete -> respond("""{"deleted":"summer"}""", HttpStatusCode.OK, json)
                else -> respond(full, HttpStatusCode.OK, json)
            }
        }
    }.also { clients += it }

    @AfterTest
    fun cleanUp() = runBlocking<Unit> {
        clients.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun codeFromShortLinksAndBareCodes() {
        assertEquals("aB3kP9x", LinksClient.codeFrom("https://go.shop.test/l/aB3kP9x?utm=1"))
        assertEquals("aB3kP9x", LinksClient.codeFrom("https://go.shop.test/l/aB3kP9x/"))
        assertEquals("aB3kP9x", LinksClient.codeFrom("https://projectref.p.stitchdb.com/l/aB3kP9x#top"))
        assertEquals("summer", LinksClient.codeFrom("summer"))
        assertEquals("a_b-C", LinksClient.codeFrom("  a_b-C "))
        assertNull(LinksClient.codeFrom("https://go.shop.test/product/42"))
        assertNull(LinksClient.codeFrom("https://go.shop.test/l/"))
        assertNull(LinksClient.codeFrom("https://go.shop.test/l/a/b"))
        assertNull(LinksClient.codeFrom("/l/abc"))
        assertNull(LinksClient.codeFrom("not a code"))
        assertNull(LinksClient.codeFrom("x".repeat(65)))
    }

    @Test
    fun resolveSendsThePlatformAndSkipsOtherUrls() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        val r = stitch.links.resolve("https://go.shop.test/l/aB3kP9x", platform = "ios")
        assertNotNull(r)
        assertEquals("aB3kP9x", r.code)
        assertEquals("/product/42", r.path)
        assertEquals("SPRING", r.data["promo"]!!.jsonPrimitive.content)
        assertNull(r.matchedBy)
        val call = server.sent.single()
        assertEquals("GET", call.method)
        assertEquals("https://projectref.stitchdb.com/links/v1/resolve/aB3kP9x?platform=ios", call.url)
        assertEquals("project-anon-key", call.apikey)
        assertEquals("Bearer project-anon-key", call.authorization)

        assertNull(stitch.links.resolve("https://shop.test/about"))
        assertEquals(1, server.sent.size, "not a short link: no request")

        stitch.links.resolve("summer") // JVM: no platform by default
        assertEquals("https://projectref.stitchdb.com/links/v1/resolve/summer", server.sent.last().url)

        val gone = assertFailsWith<LinksException> { stitch.links.resolve("old") }
        assertEquals(410, gone.statusCode)
        assertTrue(gone.message!!.startsWith("This link has expired"), gone.message)
    }

    @Test
    fun deferredAsksOncePerInstall() = runBlocking<Unit> {
        val server = FakeServer(deferredMatch = """{"code":"aB3kP9x","url":"https://go.shop.test/l/aB3kP9x","path":"/product/42","data":{},"matched_by":"referrer"}""")
        val dir = tempDir()
        val stitch = client(server)
        val first = stitch.links.deferred(platform = "android", referrer = "stitch_link=aB3kP9x", directory = dir.path)
        assertNotNull(first)
        assertEquals("referrer", first.matchedBy)
        assertEquals("/product/42", first.path)
        val call = server.deferredCalls().single()
        assertEquals("POST", call.method)
        assertEquals("android", call.json["platform"]!!.jsonPrimitive.content)
        assertEquals("stitch_link=aB3kP9x", call.json["referrer"]!!.jsonPrimitive.content)
        assertTrue(File(dir, "deferred_link_checked").exists())

        assertNull(stitch.links.deferred(platform = "android", directory = dir.path))
        assertEquals(1, server.deferredCalls().size, "already asked: no request")
        stitch.close()

        // remembered on the device; force asks again
        val next = client(server)
        assertNull(next.links.deferred(platform = "android", directory = dir.path))
        assertEquals(1, server.deferredCalls().size)
        server.deferredMatch = null
        assertNull(next.links.deferred(platform = "ios", force = true, directory = dir.path))
        assertEquals(2, server.deferredCalls().size)
        assertEquals(setOf("platform"), server.deferredCalls().last().json.keys)
    }

    @Test
    fun deferredIsAskedAgainAfterAnError() = runBlocking<Unit> {
        val server = FakeServer(deferredStatus = HttpStatusCode.TooManyRequests)
        val dir = tempDir()
        val stitch = client(server)
        val e = assertFailsWith<LinksException> { stitch.links.deferred(platform = "ios", directory = dir.path) }
        assertEquals(429, e.statusCode)
        assertFalse(File(dir, "deferred_link_checked").exists(), "checked only after a 200 answer")
        server.deferredStatus = HttpStatusCode.OK
        assertNull(stitch.links.deferred(platform = "ios", directory = dir.path))
        assertEquals(2, server.deferredCalls().size)
        assertFailsWith<IllegalArgumentException> { stitch.links.deferred(directory = dir.path, force = true) } // JVM: no default platform
    }

    @Test
    fun deferredUsesTheAnalyticsDirectoryByDefault() = runBlocking<Unit> {
        val server = FakeServer()
        val dir = tempDir()
        val stitch = client(server)
        stitch.analytics.start(directory = dir.path)
        assertNull(stitch.links.deferred(platform = "android"))
        assertTrue(File(dir, "deferred_link_checked").exists())
    }

    @Test
    fun createListGetUpdateRemove() = runBlocking<Unit> {
        val server = FakeServer()
        val stitch = client(server)
        val made = stitch.links.create(
            LinkInput(path = "/invite/7", data = mapOf("from" to "ana", "n" to 2), title = "Hi", expiresAt = Instant.parse("2026-12-01T00:00:00Z")),
        )
        assertEquals("summer", made.code)
        assertEquals("https://go.shop.test/l/summer", made.url)
        assertEquals(3, made.clicks)
        assertEquals("api", made.source)
        var call = server.sent.last()
        assertEquals("POST", call.method)
        assertEquals("https://projectref.stitchdb.com/links/v1/links", call.url)
        assertEquals(setOf("path", "data", "title", "expires_at"), call.json.keys)
        assertEquals("2026-12-01T00:00:00Z", call.json["expires_at"]!!.jsonPrimitive.content)
        assertEquals("2", call.json["data"]!!.jsonObject["n"]!!.jsonPrimitive.content)

        val page = stitch.links.list(search = "spring sale", limit = 5)
        assertEquals(1, page.total)
        assertEquals("summer", page.links.single().code)
        assertEquals("https://projectref.stitchdb.com/links/v1/links?q=spring%20sale&limit=5", server.sent.last().url)
        stitch.links.list()
        assertEquals("https://projectref.stitchdb.com/links/v1/links", server.sent.last().url)

        val stats = stitch.links.get("summer", days = 7)
        assertEquals("https://projectref.stitchdb.com/links/v1/links/summer?days=7", server.sent.last().url)
        assertEquals(7, stats.days)
        assertEquals(3, stats.daily.single().clicks)
        assertEquals("ios", stats.platforms.single().k)
        assertNull(stats.countries.single().k)

        stitch.links.update("summer", LinkInput(disabled = true, removeExpiry = true, title = ""))
        call = server.sent.last()
        assertEquals("PATCH", call.method)
        assertEquals("https://projectref.stitchdb.com/links/v1/links/summer", call.url)
        assertEquals(JsonNull, call.json["expires_at"])
        assertEquals("true", call.json["disabled"]!!.jsonPrimitive.content)
        assertEquals(setOf("title", "expires_at", "disabled"), call.json.keys)

        stitch.links.remove("summer")
        assertEquals("DELETE", server.sent.last().method)

        val taken = assertFailsWith<LinksException> { stitch.links.update("taken", LinkInput(code = "taken")) }
        assertEquals(409, taken.statusCode)
    }
}
