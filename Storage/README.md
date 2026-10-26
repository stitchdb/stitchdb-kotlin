# stitchdb-kt Storage

Extends stitchdb-kt with a multiplatform Storage client.

Supported targets:

| Target | **JVM** | **Android** | **JS** | **Wasm** | **Apple** | **Windows** | **Linux** |
|--------|---------|-------------|--------|----------|-----------|-------------|-----------|
| Status | ✅       | ✅           | ✅      | ✅        | ✅         | ✅           | ✅         |

> Linux has no support for persistent resumable upload url caching.

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

Newest version: 1.3.0 (use the BOM: `com.stitchdb:bom`)

```kotlin
dependencies {
    implementation("com.stitchdb:storage-kt:VERSION")
}
```

Install the plugin in your StitchClient. See the [documentation](https://stitchdb.com/docs/sdk/kotlin) for more information

```kotlin
val stitchdb = createStitchClient(
    stitchdbUrl = "https://<project-ref>.p.stitchdb.com",
    stitchdbKey = "your-anon-key" // Settings → API keys
) {

    //...

    install(Storage) {
        // settings
    }

}
```

# Usage

See [Storage documentation](https://stitchdb.com/docs/sdk/kotlin) for usage
