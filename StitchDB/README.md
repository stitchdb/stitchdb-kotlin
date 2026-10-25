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

Newest version: 1.0.0 (use the BOM: `com.stitchdb:bom`)

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
