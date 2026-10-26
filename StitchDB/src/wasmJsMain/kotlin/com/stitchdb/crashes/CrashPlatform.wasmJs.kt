package com.stitchdb.crashes

internal actual val crashPlatform: String = "kotlin"

internal actual fun crashDevice(): Map<String, String> = mapOf("runtime" to "kotlin-wasm")

// No file system the SDK writes to on this target: reports wait in memory until they are sent.
internal actual fun crashStore(directory: String?, project: String): CrashStore = MemoryCrashStore()

// No uncaught exception hook on this target; report errors with captureException.
internal actual fun installCrashHandler(onCrash: (thread: String, error: Throwable) -> Unit): (() -> Unit)? = null

internal actual fun crashTypeName(error: Throwable): String = error::class.simpleName ?: "Throwable"
