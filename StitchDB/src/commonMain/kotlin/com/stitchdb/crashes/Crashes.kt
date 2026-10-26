package com.stitchdb.crashes

import com.stitchdb.BuildConfig
import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.collections.update
import com.stitchdb.logging.d
import com.stitchdb.network.ApiCallListener
import com.stitchdb.plugins.SessionTokenSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Crash and error reporting (`stitchdb.crashes`): uncaught exceptions and errors you report yourself, grouped into
 * issues in Studio › Crashes with the stack, app version, device, the signed-in user and the last breadcrumbs (what the
 * user did and the API calls the SDK made). Reports go to `{projectUrl}/crashes/v1`.
 */

/** Something the user did or the app went through; the last ones are sent with every report. */
@Serializable
data class Breadcrumb(
    /** `api`, `navigation`, `ui`, `log` or your own word. */
    val type: String = "log",
    val message: String? = null,
    /** `debug`, `info`, `warning` or `error`. */
    val level: String = "info",
    val data: JsonObject? = null,
    /** ISO-8601 time. */
    val at: String = nowIso(),
)

/** The exception of a [CrashReport]. [stack] is [Throwable.stackTraceToString] (with the "Caused by" chain). */
@Serializable
data class ExceptionInfo(
    val type: String,
    val message: String,
    val stack: String? = null,
    val mechanism: String? = null,
)

/** One report as it is sent; [CrashesClient.start]'s `beforeSend` may change it with [copy]. */
@Serializable
data class CrashReport(
    val platform: String,
    val release: String?,
    val environment: String,
    @SerialName("install_id") val installId: String,
    /** ISO-8601 time of the crash. */
    val at: String,
    val handled: Boolean,
    val exception: ExceptionInfo,
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val device: Map<String, String> = emptyMap(),
    val context: JsonObject = JsonObject(emptyMap()),
    /** Your own grouping; by default reports are grouped by type and the top stack frames. */
    val fingerprint: List<String>? = null,
)

private const val MAX_QUEUE = 30
private const val BATCH = 20
private const val DEFAULT_BREADCRUMBS = 50
private const val MAX_BREADCRUMBS = 100
private const val HTTP_ERROR = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_TOO_LARGE = 413
private const val HTTP_TOO_MANY = 429
private const val ISO_DAY = 10
private const val QUEUE_FILE = "queue.json"
private const val INSTALL_FILE = "install"
private const val SESSION_FILE = "session"
private const val CRASHES_PATH = "/crashes/v1/"
private const val ANALYTICS_PATH = "/analytics/v1/"

internal fun nowIso(): String = Clock.System.now().toString()

/** `Authorization: Bearer` for crash reports and analytics: the signed-in user's session token, else the project key. */
@OptIn(StitchInternal::class)
internal suspend fun StitchClient.reportingToken(): String = accessToken?.invoke()
    ?: pluginManager.installedPlugins.values.firstNotNullOfOrNull { (it as? SessionTokenSource)?.currentAccessTokenOrNull() }
    ?: stitchdbKey

private val crashJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** A map of strings, numbers, booleans, lists and maps as JSON; anything else becomes its `toString()`. */
internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

internal fun Map<String, Any?>.toJsonObject() = JsonObject(mapValues { it.value.toJsonElement() })

/** `/rest/v1/orders?select&id`: the path and the query keys, no values and no host. */
internal fun breadcrumbUrl(url: Url): String {
    val keys = url.parameters.names()
    return url.encodedPath + if (keys.isEmpty()) "" else "?" + keys.joinToString("&")
}

private class CrashOptions(
    val release: String?,
    val environment: String,
    val maxBreadcrumbs: Int,
    val beforeSend: ((CrashReport) -> CrashReport?)?,
)

/**
 * Crash and error reporting, one per client: `stitchdb.crashes`.
 *
 * ```kotlin
 * stitchdb.crashes.start(release = "1.4.0")
 * try { checkout() } catch (e: Exception) { stitchdb.crashes.captureException(e, context = mapOf("step" to "payment")) }
 * stitchdb.crashes.addBreadcrumb(type = "ui", message = "tapped Checkout")
 * ```
 *
 * Nothing is captured, recorded or sent before [start] (ask for consent first where you need it, or decide per report
 * in `beforeSend`), and nothing is sent unless the project has crash reporting turned on (Studio › Crashes). Reports
 * are sent as the signed-in user (session token, else the project key). Passwords, tokens, keys and e-mail addresses
 * are removed by StitchDB before anything is stored. Reports that cannot be sent (offline, the app crashing) are kept on
 * disk (at most 30) and sent on the next [start].
 */
@OptIn(StitchInternal::class, ExperimentalUuidApi::class)
class CrashesClient internal constructor(private val stitch: StitchClient) {

    private val base = stitch.stitchdbHttpUrl.trimEnd('/') + "/crashes/v1"
    internal val projectHost: String = Url(stitch.stitchdbHttpUrl).host
    private val scope = CoroutineScope(SupervisorJob() + stitch.coroutineDispatcher)
    private val sendLock = Mutex()
    private val queue = AtomicReference<List<CrashReport>>(emptyList())
    private val crumbs = AtomicReference<List<Breadcrumb>>(emptyList())
    private val _queued = MutableStateFlow(0)
    private val removers = AtomicReference<List<() -> Unit>>(emptyList())

    @Volatile private var options = CrashOptions(null, "production", DEFAULT_BREADCRUMBS, null)
    @Volatile private var store: CrashStore = MemoryCrashStore()
    @Volatile private var installId = ""
    @Volatile private var startJob: Job? = null
    @Volatile private var disabled = false

    /** Whether [start] was called (and [stop] was not). */
    @Volatile
    var isStarted = false
        private set

    /** The number of reports waiting to be sent (kept on disk until they are). */
    val queued: StateFlow<Int> = _queued.asStateFlow()

    private val apiListener = ApiCallListener { method, url, status, errorCode, error, ms ->
        // our own calls (and the analytics batches) are not what the user did
        if (CRASHES_PATH in url.encodedPath || ANALYTICS_PATH in url.encodedPath) return@ApiCallListener
        val path = breadcrumbUrl(url)
        addBreadcrumb(
            Breadcrumb(
                type = "api",
                level = if (status >= HTTP_ERROR || status == 0) "error" else "info",
                message = "$method $path ${if (status == 0) "failed" else status.toString()}",
                data = buildJsonObject {
                    put("method", method)
                    put("url", path)
                    put("status", status)
                    errorCode?.let { put("error_code", it) }
                    error?.let { put("error", it) }
                    put("ms", ms)
                },
            )
        )
    }

    /**
     * Starts reporting: records the SDK's API calls as breadcrumbs, catches uncaught exceptions (unless
     * [captureUnhandled] is false; the previous handler still runs) and sends the reports kept from earlier runs and the
     * daily session (for crash-free users per release) in the background.
     *
     * @param release your app's version, e.g. `1.4.0` or a build number: issues and crash-free users are per release
     * @param environment default `production`
     * @param captureUnhandled install an uncaught exception handler (JVM and Android)
     * @param maxBreadcrumbs breadcrumbs kept in memory (default 50, at most 100)
     * @param directory where reports wait to be sent. Default on the JVM: `~/.stitchdb/crashes/<project host>` (or
     * under `java.io.tmpdir` when the home directory is not writable); on Android use the `start(context, ...)` overload
     * (`filesDir/stitchdb-crashes/<project host>`), without it the app's cache directory is used. Other targets keep
     * reports in memory.
     * @param beforeSend change a report or return null to not send it (consent, filtering). Runs synchronously, also
     * while the app is crashing.
     */
    @Suppress("LongParameterList")
    fun start(
        release: String? = null,
        environment: String = "production",
        captureUnhandled: Boolean = true,
        maxBreadcrumbs: Int = DEFAULT_BREADCRUMBS,
        directory: String? = null,
        beforeSend: ((CrashReport) -> CrashReport?)? = null,
    ) {
        options = CrashOptions(release, environment, maxBreadcrumbs.coerceIn(0, MAX_BREADCRUMBS), beforeSend)
        if (isStarted) return
        val newStore = crashStore(directory, projectHost)
        store = newStore
        installId = newStore.read(INSTALL_FILE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: Uuid.random().toString().also { newStore.write(INSTALL_FILE, it) }
        queue.store(loadQueue(newStore))
        _queued.value = queue.load().size
        disabled = false
        isStarted = true
        stitch.httpClient.addApiCallListener(apiListener)
        removers.update { it + { stitch.httpClient.removeApiCallListener(apiListener) } }
        if (captureUnhandled) {
            installCrashHandler(::onUncaught)?.let { remove -> removers.update { it + remove } }
        }
        startJob = scope.launch {
            sendSession()
            sendQueue()
        }
    }

    /** Stops reporting and removes the handlers (for example when the user withdraws consent). */
    fun stop() {
        isStarted = false
        removers.exchange(emptyList()).forEach { it() }
    }

    /** Reports an error you caught. Returns once it is queued (on disk); it is sent in the background (or [flush]). */
    fun captureException(
        error: Throwable,
        context: Map<String, Any?> = emptyMap(),
        fingerprint: List<String>? = null,
        handled: Boolean = true,
    ) {
        if (!isStarted) return
        enqueue(exceptionOf(error, null), handled, context.toJsonObject(), fingerprint)
        sendInBackground()
    }

    /** Reports a message as an error, e.g. a state that should never happen. */
    fun captureMessage(message: String, context: Map<String, Any?> = emptyMap(), fingerprint: List<String>? = null) {
        if (!isStarted) return
        val stack = Exception(message).stackTraceToString().lines().drop(1).joinToString("\n")
        enqueue(ExceptionInfo("Message", message, stack), true, context.toJsonObject(), fingerprint)
        sendInBackground()
    }

    /** Adds a breadcrumb; the last ones are sent with the next report. */
    fun addBreadcrumb(type: String = "log", message: String? = null, data: Map<String, Any?> = emptyMap(), level: String = "info") {
        addBreadcrumb(Breadcrumb(type, message, level, if (data.isEmpty()) null else data.toJsonObject()))
    }

    /** Adds a breadcrumb; the last ones are sent with the next report. */
    fun addBreadcrumb(breadcrumb: Breadcrumb) {
        if (!isStarted) return
        val max = options.maxBreadcrumbs
        crumbs.update { (it + breadcrumb).takeLast(max) }
    }

    /** Sends what is queued now (and waits for the sending [start] began). Unsent reports are retried on the next start. */
    suspend fun flush() {
        startJob?.join()
        sendQueue()
    }

    // The process is dying: build the report and write it to disk before the previous handler runs.
    private fun onUncaught(thread: String, error: Throwable) {
        if (!isStarted) return
        enqueue(exceptionOf(error, "UncaughtExceptionHandler"), false, buildJsonObject { put("thread", thread) }, null)
        sendInBackground()
    }

    private fun exceptionOf(error: Throwable, mechanism: String?) = ExceptionInfo(
        type = crashTypeName(error),
        message = error.message ?: "",
        stack = error.stackTraceToString(),
        mechanism = mechanism,
    )

    @Suppress("TooGenericExceptionCaught")
    private fun enqueue(exception: ExceptionInfo, handled: Boolean, context: JsonObject, fingerprint: List<String>?) {
        if (disabled) return // crash reporting is off for the project (404/403): nothing is kept for this run
        val opts = options
        val report = CrashReport(
            platform = crashPlatform,
            release = opts.release,
            environment = opts.environment,
            installId = installId,
            at = nowIso(),
            handled = handled,
            exception = exception,
            breadcrumbs = crumbs.load(),
            device = crashDevice() + ("sdk" to "stitchdb-kotlin/${BuildConfig.PROJECT_VERSION}"),
            context = context,
            fingerprint = fingerprint,
        )
        val beforeSend = opts.beforeSend
        val ready = try {
            if (beforeSend == null) report else beforeSend(report)
        } catch (e: Exception) {
            stitch.logger.d(e) { "beforeSend threw, the report is dropped" }
            null
        } ?: return
        queue.update { (it + ready).takeLast(MAX_QUEUE) }
        persist()
    }

    private fun sendInBackground() {
        if (!disabled) scope.launch { sendQueue() }
    }

    private suspend fun sendQueue() = sendLock.withLock {
        try {
            while (!disabled) {
                val batch = queue.load().take(BATCH)
                if (batch.isEmpty()) break
                val body = buildJsonObject { put("reports", crashJson.encodeToJsonElement(ListSerializer(CrashReport.serializer()), batch)) }
                val status = post("report", body).status.value
                when {
                    status == HTTP_NOT_FOUND || status == HTTP_FORBIDDEN -> { // crash reporting is off for the project
                        disabled = true
                        queue.store(emptyList())
                    }
                    status < HTTP_ERROR || status == HTTP_BAD_REQUEST || status == HTTP_TOO_LARGE || status == HTTP_TOO_MANY ->
                        queue.update { list -> list.filterNot { r -> batch.any { it === r } } }
                    else -> break // the server is busy: keep them for the next start
                }
            }
        } catch (e: CancellationException) {
            persist()
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            stitch.logger.d(e) { "Crash reports could not be sent, they are kept for the next start" }
        }
        persist()
    }

    // Once a day per install and release, for crash-free users in Studio.
    private suspend fun sendSession() {
        val opts = options
        val mark = nowIso().take(ISO_DAY) + "|" + opts.release.orEmpty()
        if (store.read(SESSION_FILE) == mark) return
        val body = buildJsonObject {
            put("release", opts.release)
            put("install_id", installId)
            put("environment", opts.environment)
        }
        val status = try {
            post("session", body).status
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            stitch.logger.d(e) { "The crash reporting session could not be sent" }
            return
        }
        when {
            status.value == HTTP_NOT_FOUND || status.value == HTTP_FORBIDDEN -> disabled = true
            status.isSuccess() -> store.write(SESSION_FILE, mark)
        }
    }

    private suspend fun post(path: String, body: JsonObject): HttpResponse {
        val token = stitch.reportingToken()
        return stitch.httpClient.request("$base/$path") {
            method = HttpMethod.Post
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }

    private fun persist() {
        val list = queue.load()
        store.write(QUEUE_FILE, if (list.isEmpty()) null else crashJson.encodeToString(ListSerializer(CrashReport.serializer()), list))
        _queued.value = list.size
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadQueue(store: CrashStore): List<CrashReport> = try {
        store.read(QUEUE_FILE)?.let { crashJson.decodeFromString(ListSerializer(CrashReport.serializer()), it) }?.takeLast(MAX_QUEUE)
            ?: emptyList()
    } catch (e: Exception) {
        emptyList() // a file cut off by a crash: start over
    }

}
