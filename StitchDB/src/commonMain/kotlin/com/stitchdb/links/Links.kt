package com.stitchdb.links

import com.stitchdb.StitchClient
import com.stitchdb.analytics.analyticsStore
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.crashes.CrashStore
import com.stitchdb.crashes.reportingToken
import com.stitchdb.crashes.toJsonObject
import com.stitchdb.exceptions.RestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/*
 * Deep links and short links (`stitchdb.links`, Studio › Links): `https://<project host or your domain>/l/<code>` opens
 * your app on the right screen when it is installed (Android App Links, iOS Universal Links) and otherwise sends people
 * to Google Play, the App Store or a web page. Requests go to `{projectUrl}/links/v1`.
 */

/** What the app gets for a link. */
@Serializable
data class AppLink(
    val code: String,
    /** The short link. */
    val url: String,
    /** The screen to show, as you stored it (for example `/product/42`), or null for the start screen. */
    val path: String? = null,
    /** The data stored with the link. */
    val data: JsonObject = JsonObject(emptyMap()),
    /** [LinksClient.deferred] only: how the install was matched to the click, `referrer` or `network`. */
    @SerialName("matched_by") val matchedBy: String? = null,
)

/**
 * A new link or the fields to change ([LinksClient.update]). Null fields are left out (unchanged on update); an empty
 * string clears a text field on update, [removeExpiry] clears [expiresAt].
 */
data class LinkInput(
    /** The screen the app shows (a path your router knows). */
    val path: String? = null,
    /** Up to 4 KB of JSON for the app: strings, numbers, booleans, lists and maps. */
    val data: Map<String, Any?>? = null,
    /** Service key only: choose the code (letters, digits, _ and -); default a random 7-letter code. */
    val code: String? = null,
    /** Where people on a computer go (default: the project's web page). */
    val webUrl: String? = null,
    /** Instead of the App Store when the app is not installed. */
    val iosUrl: String? = null,
    /** Instead of Google Play when the app is not installed. */
    val androidUrl: String? = null,
    /** Shown when the link is shared in chats and social posts. */
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val expiresAt: Instant? = null,
    /** Update only: the link no longer expires. */
    val removeExpiry: Boolean = false,
    val disabled: Boolean? = null,
) {
    internal fun toJson(): JsonObject = buildJsonObject {
        path?.let { put("path", it) }
        data?.let { put("data", it.toJsonObject()) }
        code?.let { put("code", it) }
        webUrl?.let { put("web_url", it) }
        iosUrl?.let { put("ios_url", it) }
        androidUrl?.let { put("android_url", it) }
        title?.let { put("title", it) }
        description?.let { put("description", it) }
        imageUrl?.let { put("image_url", it) }
        if (removeExpiry) put("expires_at", JsonNull) else expiresAt?.let { put("expires_at", it.toString()) }
        disabled?.let { put("disabled", it) }
    }
}

/** A link as Studio shows it. */
@Serializable
data class Link(
    val code: String,
    val url: String,
    val path: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
    @SerialName("web_url") val webUrl: String? = null,
    @SerialName("ios_url") val iosUrl: String? = null,
    @SerialName("android_url") val androidUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    /** ISO-8601 time, or null when it does not expire. */
    @SerialName("expires_at") val expiresAt: String? = null,
    val disabled: Boolean = false,
    /** `studio`, `api` or `user`. */
    val source: String = "api",
    @SerialName("created_by") val createdBy: String? = null,
    val clicks: Long = 0,
    val opens: Long = 0,
    val installs: Long = 0,
    @SerialName("last_click_at") val lastClickAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

/** One page of [LinksClient.list]: the links (newest first) and how many there are. */
@Serializable
data class LinkList(val links: List<Link>, val total: Long)

/** [LinksClient.get]: one link with its clicks over the last [days] days. */
@Serializable
data class LinkStats(
    val link: Link,
    val days: Int,
    val daily: List<Day> = emptyList(),
    val platforms: List<Count> = emptyList(),
    val countries: List<Count> = emptyList(),
    /** Where people went (app, store, web page). */
    val outcomes: List<Count> = emptyList(),
    val referrers: List<Count> = emptyList(),
) {
    @Serializable
    data class Day(val day: String, val clicks: Long = 0, val opens: Long = 0, val installs: Long = 0)

    /** A key ([k], null when unknown) and how often it occurred ([n]). */
    @Serializable
    data class Count(val k: String? = null, val n: Long = 0)
}

/**
 * A failed links request: [statusCode] 410 for an expired or turned-off link, 404 for an unknown one, 403 when the
 * caller may not do this, 402 when the plan's links are used up, 409 when the code is taken, 429 when rate limited.
 */
class LinksException(message: String, response: HttpResponse) : RestException(message, null, response)

private val linksJson = Json { ignoreUnknownKeys = true }

private const val DEFERRED_FILE = "deferred_link_checked"
private const val MAX_CODE = 64
private val CODE_RE = Regex("^[A-Za-z0-9_-]{1,$MAX_CODE}\$")
private val SHORT_PATH_RE = Regex("^/l/([A-Za-z0-9_-]{1,$MAX_CODE})/?\$")
private val ABSOLUTE_RE = Regex("^[A-Za-z][A-Za-z0-9+.-]*://[^/?#]*([^?#]*)")

/**
 * Deep links, one per client: `stitchdb.links`.
 *
 * ```kotlin
 * // the app was opened by a link (Android: intent.data, or stitchdb.links.resolve(intent))
 * stitchdb.links.resolve(url)?.let { navigate(it.path, it.data) }
 *
 * // first launch after an install: the link that led to it (once per install)
 * stitchdb.links.deferred(referrer = installReferrer)?.let { navigate(it.path, it.data) }
 *
 * // make a link: signed-in users when the project allows it, any link with the service key
 * val link = stitchdb.links.create(LinkInput(path = "/invite/7", data = mapOf("from" to "ana")))
 * share(link.url)
 * ```
 *
 * Requests go as the signed-in user (session token, else the project key). Failed requests throw [LinksException]
 * (network errors: [com.stitchdb.exceptions.HttpRequestException]).
 */
@OptIn(StitchInternal::class)
class LinksClient internal constructor(private val stitch: StitchClient) {

    private val base = stitch.stitchdbHttpUrl.trimEnd('/') + "/links/v1"
    internal val projectHost: String = Url(stitch.stitchdbHttpUrl).host

    companion object {
        /**
         * The code of a short link (`https://…/l/<code>`, any host, a trailing slash and the query ignored), a bare
         * code as is, or null for anything else.
         */
        fun codeFrom(urlOrCode: String): String? {
            val s = urlOrCode.trim()
            if (CODE_RE.matches(s)) return s
            val path = ABSOLUTE_RE.find(s)?.groupValues?.get(1) ?: return null
            return SHORT_PATH_RE.find(path)?.groupValues?.get(1)
        }
    }

    /**
     * The link the app was opened with: its path and data (counted as an open in Studio), or null without a request
     * when [urlOrCode] is not a short link. An expired or turned-off link throws [LinksException] with status 410.
     *
     * @param platform `ios`, `android` or `web` for Studio's breakdown; default `android` on Android, `ios` on iOS
     */
    suspend fun resolve(urlOrCode: String, platform: String? = linkPlatform): AppLink? {
        val code = codeFrom(urlOrCode) ?: return null
        val query = platform?.let { "?platform=${it.encodeURLParameter()}" } ?: ""
        return linksJson.decodeFromString(AppLink.serializer(), request(HttpMethod.Get, "/resolve/$code$query"))
    }

    /**
     * First launch after an install: the link that led to it, or null. Asked once per install: after the first answer
     * a flag is kept on the device (next to the analytics queue) and later calls return null without a request.
     *
     * @param platform `android` or `ios` (default: `android` on Android, `ios` on iOS; required on other targets)
     * @param referrer Android: the Play install referrer (`ReferrerDetails.installReferrer` from the Play Install
     * Referrer library, which your app adds and reads itself) for an exact match. Without it the click is matched by
     * network and platform within 2 hours (best effort, each click is handed out once).
     * @param force ask even when this install asked before (tests)
     * @param directory where the flag is kept; default the analytics directory (`analytics.start(directory = ...)`, else
     * its default). JS, Wasm and native targets keep it in memory only.
     */
    suspend fun deferred(platform: String? = linkPlatform, referrer: String? = null, force: Boolean = false, directory: String? = null): AppLink? {
        requireNotNull(platform) { "deferred(): pass platform = \"android\" or \"ios\"" }
        val store = flagStore(directory)
        if (!force && store.read(DEFERRED_FILE) != null) return null
        val body = buildJsonObject {
            put("platform", platform)
            referrer?.let { put("referrer", it) }
        }
        val answer = linksJson.parseToJsonElement(request(HttpMethod.Post, "/deferred", body)).jsonObject
        store.write(DEFERRED_FILE, "1")
        val match = answer["match"]?.takeIf { it != JsonNull } ?: return null
        return linksJson.decodeFromJsonElement(AppLink.serializer(), match)
    }

    /** Makes a short link (signed-in users when the project allows it in Studio › Links, any link with the service key). */
    suspend fun create(link: LinkInput): Link = linksJson.decodeFromString(Link.serializer(), request(HttpMethod.Post, "/links", link.toJson()))

    /** Links, newest first (service key). [search] matches the code, path and title. */
    suspend fun list(search: String? = null, limit: Int? = null, offset: Int? = null): LinkList {
        val query = listOfNotNull(
            search?.takeIf { it.isNotEmpty() }?.let { "q=${it.encodeURLParameter()}" },
            limit?.let { "limit=$it" },
            offset?.let { "offset=$it" },
        ).joinToString("&")
        return linksJson.decodeFromString(LinkList.serializer(), request(HttpMethod.Get, "/links" + if (query.isEmpty()) "" else "?$query"))
    }

    /** One link with its clicks per day, platform, country, where people went and referrers over [days] days (service key). */
    suspend fun get(code: String, days: Int? = null): LinkStats =
        linksJson.decodeFromString(LinkStats.serializer(), request(HttpMethod.Get, "/links/${code.encodeURLPathPart()}" + (days?.let { "?days=$it" } ?: "")))

    /** Changes the fields set in [patch] (service key). */
    suspend fun update(code: String, patch: LinkInput): Link =
        linksJson.decodeFromString(Link.serializer(), request(HttpMethod.Patch, "/links/${code.encodeURLPathPart()}", patch.toJson()))

    /** Deletes a link (service key). */
    suspend fun remove(code: String) {
        request(HttpMethod.Delete, "/links/${code.encodeURLPathPart()}")
    }

    private fun flagStore(directory: String?): CrashStore = if (directory != null) analyticsStore(directory, projectHost) else stitch.analytics.deviceStore()

    private suspend fun request(method: HttpMethod, path: String, body: JsonObject? = null): String {
        val token = stitch.reportingToken()
        val response = stitch.httpClient.request(base + path) {
            this.method = method
            bearerAuth(token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val text = response.bodyAsText()
        if (response.status.value >= HTTP_ERROR) throw LinksException(errorMessage(text) ?: "Request failed (${response.status.value})", response)
        return text
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun errorMessage(text: String): String? = try {
        (linksJson.parseToJsonElement(text).jsonObject["message"] as? JsonPrimitive)?.contentOrNull
    } catch (e: Exception) {
        null
    }
}

private const val HTTP_ERROR = 400

/** The platform [LinksClient.resolve] and [LinksClient.deferred] send by default: `android`, `ios` or null. */
internal expect val linkPlatform: String?
