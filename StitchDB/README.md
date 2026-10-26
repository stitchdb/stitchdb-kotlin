# stitchdb-kt

The main stitchdb-kt library. It provides a plugin system to extend the client with additional features and provides basic functionality to interact with the StitchDB API.

Supported targets:

| Target | **JVM** | **Android** | **JS** | **Wasm** | **Apple** | **Windows** | **Linux** |
|--------|---------|-------------|--------|----------|-----------|-------------|-----------|
| Status | ✅       | ✅           | ✅      | ✅        | ✅         | ✅           | ✅         |

<details>

<summary>In-depth Kotlin targets</summary>

**JS**: Browser, NodeJS

**Wasm**: wasm-js

**Apple:**

- iOS: iosArm64, iosSimulatorArm64, iosX64

- tvOS: tvosArm64, tvosX64, tvosSimulatorArm64

- watchOS: watchosArm64, watchosX64, watchosSimulatorArm64

- MacOS: macosX64, macosArm64

**Windows**: mingwX64

**Linux**: linuxX64

</details>

# Installation

*You don't necessarily need to install this library directly. It is a dependency of the other stitchdb-kt libraries.*

Newest version: 1.3.0 (use the BOM: `com.stitchdb:bom`)

```kotlin
dependencies {
    implementation("com.stitchdb:stitchdb-kt:VERSION")
}
```

# Usage

```kotlin
//Create a StitchDB client
val stitchdb = createStitchClient(
    stitchdbUrl = "https://<project-ref>.p.stitchdb.com",
    stitchdbKey = "your-anon-key" // Settings → API keys
) {
    //Change default settings
    defaultSerializer = MyCustomSerializer()
    defaultLogLevel = LogLevel.DEBUG
    
    //Install a plugin
    install(Auth) //from auth-kt

}

//Access a plugin via the client
val auth = stitchdb.auth
```

# Offline sync

Keep a local copy of the tables that sync (Studio › Database › Offline sync). Reads come from the local copy, writes
apply locally at once and are queued; the queue is pushed and changes are pulled whenever the device is online.
Requests go to `{projectUrl}/sync/v1` as the signed-in user (session token, else the anon key), so row level security
decides which rows a device gets and which writes it may make.

```kotlin
val offline = stitchdb.offline(
    tables = listOf("todos"),       // default: every table that syncs
    store = MemoryOfflineStore(),   // or your own OfflineStore (one per signed-in user)
    interval = 30.seconds,          // Duration.ZERO = only when you call sync()
)
offline.awaitReady()

val todos = offline.table("todos")
todos.upsert(buildJsonObject { put("id", "a"); put("title", "Buy milk") }) // works offline, queued
todos.get("a")                     // JsonObject? by primary key
todos.where { it["done"]?.jsonPrimitive?.booleanOrNull == true }
todos.delete("a")
todos.observe().collect { rows -> /* every change */ }

offline.status.collect { println("pending ${it.pending}, online ${it.online}") }
offline.conflicts.collect { println("${it.table} ${it.pk}: ${it.status}, kept the server row") }
offline.sync()                     // push, then pull until up to date
offline.close(clear = true)        // e.g. on sign-out: stop and delete the local copy
```

A local write wins over a pulled change until it is pushed. If the server reports a conflict or rejects the write,
the local copy takes the server's row and `conflicts` emits an event. Implement `OfflineStore` (`get`, `set`,
`delete`, `entries(prefix)`, `clear` over string keys and `JsonElement` values) to persist the copy on disk;
call `setOnline(true/false)` from your connectivity callback to sync as soon as the device is back online.

# Crash reporting

Uncaught exceptions (JVM and Android) and errors you report yourself, grouped into issues in Studio › Crashes with
the stack (`stackTraceToString()`, including "Caused by"), app version, device, the signed-in user and the last
breadcrumbs: what the user did and the API calls the SDK made ("crashed right after `GET /rest/v1/orders` returned
500"). Nothing is captured, recorded or sent before `start()`, and nothing is sent unless crash reporting is turned on
for the project (Studio › Crashes). Ask for consent first where you need it, or decide per report in `beforeSend`.

```kotlin
stitchdb.crashes.start(
    release = "1.4.0",            // issues and crash-free users are shown per release
    environment = "production",
    captureUnhandled = true,      // Thread.setDefaultUncaughtExceptionHandler; the previous handler still runs
    maxBreadcrumbs = 50,          // at most 100
    beforeSend = { report -> if (userConsented) report.copy(context = JsonObject(emptyMap())) else null },
)
// Android: stitchdb.crashes.start(context, release = "1.4.0") keeps reports in filesDir

try { checkout() } catch (e: Exception) {
    stitchdb.crashes.captureException(e, context = mapOf("step" to "payment"))
}
stitchdb.crashes.captureMessage("cart total is negative")
stitchdb.crashes.addBreadcrumb(type = "ui", message = "tapped Checkout", data = mapOf("items" to 3))
stitchdb.crashes.queued.collect { println("$it reports waiting") } // StateFlow<Int>
stitchdb.crashes.flush()          // send now (suspends)
stitchdb.crashes.stop()           // remove the handlers, e.g. when consent is withdrawn
```

- Every request the SDK makes (database, auth, storage, functions, ...) becomes an `api` breadcrumb with the method,
  the path and only the *names* of the query parameters (`GET /rest/v1/orders?select&id 500`), the status, the error
  code of an error response and the duration. No bodies, no query values.
- A crash is written to disk synchronously (with `java.io`) before the previous handler runs, and sent on the next
  `start()`. Handled reports are queued on disk and sent in the background. At most 30 reports wait; reports that
  could not be sent (offline, server busy) are retried on the next start.
- Where reports wait: on the JVM `~/.stitchdb/crashes/<project host>` (or `<java.io.tmpdir>/stitchdb-crashes/<project
  host>` without a writable home directory), or `start(directory = ...)`. On Android `filesDir/stitchdb-crashes/<project
  host>` with `start(context, ...)`, else the app's cache directory. On JS, Wasm and native targets reports wait in
  memory and only `captureException`/`captureMessage` report (no uncaught hook).
- Reports carry `platform` `android` or `kotlin`, an install id kept in that directory, and the device (`os`,
  `os_version`, `model`, `locale`). Once a day per install and release a session is sent for crash-free users.
- Requests go to `{projectUrl}/crashes/v1` as the signed-in user (session token, else the project key). Passwords,
  tokens, keys and e-mail addresses are removed by StitchDB before anything is stored.

# Analytics

Product analytics: the events your app tracks, linked to the signed-in user (an anonymous id before sign-in, merged
into the user when they sign in), for active users, funnels, retention and breakdowns in Studio › Analytics. Nothing
is sent before `start()`, and nothing unless analytics is turned on for the project (Studio › Analytics).

```kotlin
stitchdb.analytics.start(
    release = "1.4.0",                  // for the breakdown by release
    flushInterval = 10.seconds,         // also sent when 20 events wait and on sign-in / sign-out
    beforeSend = { event -> if (userConsented) event else null }, // change an event or drop it
)
// Android: stitchdb.analytics.start(context, release = "1.4.0") keeps the queue in filesDir

stitchdb.analytics.track("purchase", mapOf("plan" to "pro", "amount" to 29))
stitchdb.analytics.queued.collect { println("$it events waiting") } // StateFlow<Int>
stitchdb.analytics.flush()              // send now (suspends)
stitchdb.analytics.optOut()             // deletes the user's events, nothing is tracked any more (suspends)
stitchdb.analytics.optIn()              // track again
stitchdb.analytics.isOptedOut           // remembered on the device
stitchdb.analytics.anonymousId          // the id of this device before sign-in
stitchdb.analytics.stop()               // stop sending; waiting events stay on the device
```

- Event names: letters, digits, `_ $ . : - /` and spaces, up to 64 characters; other names are dropped (with a debug
  log). Props: strings, numbers, booleans, lists and maps, at most 8 KB (flat values are easiest to break down by).
- Events tracked before `start()` wait in memory (at most 100). After it they are sent in batches of up to 100 in the
  background and written to disk on every change, so they survive the app being killed while offline (at most 1000,
  the oldest go first). When the server is busy, the month's events are used up (429) or the device is offline, they
  are retried later; when analytics is off for the project (404/403) the queue is cleared for this run.
- Where events wait: on the JVM `~/.stitchdb/analytics/<project host>` (or `<java.io.tmpdir>/stitchdb-analytics/<project
  host>` without a writable home directory), or `start(directory = ...)`. On Android `filesDir/stitchdb-analytics/<project
  host>` with `start(context, ...)`, else the app's cache directory. On JS, Wasm and native targets events wait in memory.
- Every batch carries the anonymous id, a session id per app launch, the release, `platform` (`android` or `kotlin`),
  `os` and `country` (the region of the device locale). Requests go to `{projectUrl}/analytics/v1` as the signed-in user
  (session token, else the project key); a batch is sent when the user signs in or out, so it belongs to one user.
  Passwords, tokens, keys and e-mail addresses are removed by StitchDB before anything is stored.
- Analytics requests are not recorded as crash reporting breadcrumbs.

# Deep links

Short links `https://<project host or your domain>/l/<code>` (Studio › Links) open your app on the right screen when it
is installed (Android App Links, iOS Universal Links) and otherwise send people to Google Play, the App Store or a web
page. The app resolves the link it was opened with to its path and data, and on its first launch after an install can
ask which link led to the install (deferred deep link, best effort).

```kotlin
// the app was opened by a link: Android, in onCreate / onNewIntent
stitchdb.links.resolve(intent)?.let { link -> navigate(link.path, link.data) }
stitchdb.links.resolve("https://go.shop.com/l/aB3kP9x")   // any URL or a bare code; null when it is not a short link

// first launch after an install (asked once per install, later calls return null without a request)
val first = stitchdb.links.deferred(context, referrer = referrerDetails.installReferrer) // Android
val first = stitchdb.links.deferred(platform = "ios")                                    // other targets

// make a link: signed-in users when the project allows it, any link with the service key
val link = stitchdb.links.create(LinkInput(path = "/invite/7", data = mapOf("from" to "ana"), title = "Join me"))
share(link.url)

// service key: manage links
stitchdb.links.list(search = "spring", limit = 20)       // LinkList(links, total), newest first
stitchdb.links.get("summer", days = 30)                  // LinkStats: clicks, opens, installs per day, platforms, countries
stitchdb.links.update("summer", LinkInput(disabled = true))
stitchdb.links.remove("summer")
LinksClient.codeFrom("https://go.shop.com/l/summer")     // "summer"
```

- `resolve` counts an open in Studio and sends the platform (`android` on Android, `ios` on iOS, none elsewhere; pass
  `platform =` to choose). An expired or turned-off link throws `LinksException` with `statusCode` 410, an unknown one
  404. Every failed request throws `LinksException` (402 the plan's links are used up, 403 not allowed, 409 the code is
  taken, 429 rate limited); network errors throw `HttpRequestException`.
- `deferred`: on Android pass the Play install referrer (`ReferrerDetails.installReferrer` from the Play Install
  Referrer library, which your app adds and reads itself; the SDK has no dependency on it) for an exact match. Without
  it the click is matched by network and platform within 2 hours of it, and each click is handed out once. After the
  first answer a flag is kept next to the analytics queue (`deferred(context, ...)`: `filesDir/stitchdb-analytics/<project
  host>`; JVM: the analytics directory, or `deferred(directory = ...)`; JS, Wasm and native targets: in memory only, so
  remember it in the app). It is set only after a successful answer; `force = true` asks again (tests).
- `LinkInput`: null fields are left out (unchanged on `update`), an empty string clears a text field, `removeExpiry =
  true` clears `expiresAt`. `code` can only be chosen with the service key.
- Requests go to `{projectUrl}/links/v1` as the signed-in user (session token, else the project key).
