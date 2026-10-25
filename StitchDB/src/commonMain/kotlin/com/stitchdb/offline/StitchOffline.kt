package com.stitchdb.offline

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.exceptions.HttpRequestException
import com.stitchdb.plugins.SessionTokenSource
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The ktor glue for [OfflineClient]: `{projectUrl}/sync/v1/...` with the `apikey` header (added by the client's
 * http client) and `Authorization: Bearer` of the session token, or the anon key when nobody is signed in.
 */
@OptIn(StitchInternal::class)
internal class KtorOfflineTransport(
    private val stitch: StitchClient,
    private val accessToken: (suspend () -> String?)?,
) : OfflineTransport {

    private val base = stitch.stitchdbHttpUrl.trimEnd('/') + "/sync/v1"

    private suspend fun token(): String = accessToken?.invoke()
        ?: stitch.accessToken?.invoke()
        ?: stitch.pluginManager.installedPlugins.values.firstNotNullOfOrNull { (it as? SessionTokenSource)?.currentAccessTokenOrNull() }
        ?: stitch.stitchdbKey

    override suspend fun get(path: String) = send(path) { method = HttpMethod.Get }

    override suspend fun post(path: String, body: JsonObject) = send(path) {
        method = HttpMethod.Post
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

    private suspend fun send(path: String, builder: HttpRequestBuilder.() -> Unit): JsonObject {
        val auth = token()
        val res: HttpResponse = try {
            stitch.httpClient.request(base + path) {
                builder()
                bearerAuth(auth)
            }
        } catch (e: HttpRequestTimeoutException) {
            throw OfflineException(e.message ?: "Sync timed out", 0)
        } catch (e: HttpRequestException) {
            throw OfflineException(e.message ?: "The server could not be reached", 0)
        }
        val json = try { Json.parseToJsonElement(res.bodyAsText()) as? JsonObject } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            ?: JsonObject(emptyMap())
        if (!res.status.isSuccess()) {
            val message = (json["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            throw OfflineException(message ?: "Sync failed (${res.status.value})", res.status.value)
        }
        return json
    }
}

/**
 * Offline-first sync: a local copy of the rows the signed-in user may see in the tables that sync
 * (Studio › Database › Offline sync). Reads come from the local copy, writes apply locally at once and are queued;
 * the queue is pushed and changes are pulled whenever the device is online.
 *
 * @param tables tables to keep offline (default: every table that syncs)
 * @param store where the local copy lives (default: in memory). Use one store per signed-in user.
 * @param interval sync this often while online (default 30 s; [Duration.ZERO] = only on [OfflineClient.sync])
 * @param batch rows per pull request (default 500)
 * @param accessToken the token sent as `Authorization: Bearer` (default: the session token, else the anon key)
 */
fun StitchClient.offline(
    tables: List<String>? = null,
    store: OfflineStore = MemoryOfflineStore(),
    interval: Duration = 30.seconds,
    batch: Int = 500,
    accessToken: (suspend () -> String?)? = null,
): OfflineClient = OfflineClient(KtorOfflineTransport(this, accessToken), store, tables, interval, batch)
