# stitchdb-kt Auth


Extends stitchdb-kt with a multiplatform Auth client.

Supported targets:

| Target | **JVM** | **Android** | **JS** | **Wasm** | **Apple** | **Windows** | **Linux** |
|--------|---------|-------------|--------|----------|-----------|-------------|-----------|
| Status | ✅       | ✅           | ✅      | ✅        | ☑️*       | ☑️          | ☑️        |

> ☑️ = No built-in OAuth support. Linux has no support for persistent session storage.

\* **iOS and macOS are fully supported**

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
    implementation("com.stitchdb:auth-kt:VERSION")
}
```

Install the plugin in your StitchClient. See the [documentation](https://stitchdb.com/docs/sdk/kotlin) for more information

```kotlin
val stitchdb = createStitchClient(
    stitchdbUrl = "https://<project-ref>.p.stitchdb.com",
    stitchdbKey = "your-anon-key" // Settings → API keys
) {

    //...

    install(Auth) {
        // settings
    }

}
```

# Usage

See [Auth documentation](https://stitchdb.com/docs/sdk/kotlin) for usage
