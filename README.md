# StitchDB for Kotlin

The official Kotlin Multiplatform client for [StitchDB](https://stitchdb.com): Postgres database, auth, storage, realtime and Edge Functions — for Android, JVM, iOS and the other Kotlin targets.

## Install

Use the BOM so all modules stay on the same version:

```kotlin
dependencies {
    implementation(platform("com.stitchdb:bom:1.0.0"))
    implementation("com.stitchdb:postgrest-kt")
    implementation("com.stitchdb:auth-kt")
    implementation("com.stitchdb:storage-kt")
    implementation("com.stitchdb:realtime-kt")
    implementation("com.stitchdb:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.4.3") // or any other Ktor engine
}
```

| Module | Artifact |
|---|---|
| Core client | `com.stitchdb:stitchdb-kt` |
| Database (REST) | `com.stitchdb:postgrest-kt` |
| Authentication | `com.stitchdb:auth-kt` |
| Storage | `com.stitchdb:storage-kt` |
| Realtime | `com.stitchdb:realtime-kt` |
| Edge Functions | `com.stitchdb:functions-kt` |
| BOM | `com.stitchdb:bom` |
| Serializers | `com.stitchdb:serializer-moshi`, `com.stitchdb:serializer-jackson` |

## Quick start

```kotlin
val stitchdb = createStitchClient(
    stitchdbUrl = System.getenv("STITCHDB_URL"),     // https://<project-ref>.stitchdb.com
    stitchdbKey = System.getenv("STITCHDB_ANON_KEY") // Settings → API keys
) {
    install(Auth)
    install(Postgrest)
    install(Storage)
    install(Realtime)
    install(Functions)
}

@Serializable
data class Todo(val id: Long, val task: String, val done: Boolean)

// Database
val todos = stitchdb.from("todos").select { filter { eq("done", false) } }.decodeList<Todo>()

// Auth
stitchdb.auth.signInWith(Email) { email = "ada@example.com"; password = "a-strong-password" }

// Storage
stitchdb.storage.from("avatars").upload("user-1/avatar.png", bytes) { upsert = true }

// Realtime
val channel = stitchdb.channel("room-1")
val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "todos" }
channel.subscribe()

// Edge Functions
val res = stitchdb.functions.invoke("hello-world", body = buildJsonObject { put("name", "Ada") })
```

Find your project URL and anon key in the StitchDB dashboard under **Settings → API keys**. Never ship the `service_role` key in an app.

## Documentation

https://stitchdb.com/docs/sdk/kotlin

## Building

```sh
./gradlew jvmTest            # JVM tests
./gradlew publishToMavenLocal -DDisableSigning=true
```

## License

MIT — see [LICENSE](LICENSE).
