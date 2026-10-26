package com.stitchdb.crashes

import kotlin.concurrent.Volatile

/*
 * What crash reporting needs from the platform: a small synchronous file store (the uncaught exception handler runs
 * while the process is dying, so writing must not suspend), the device description and the uncaught exception hook.
 */

/**
 * Where reports, the install id and the last session day are kept: a few small text files by name. Synchronous on
 * purpose, see above. Implementations swallow I/O errors (a full disk must not crash the app twice).
 */
internal interface CrashStore {
    fun read(name: String): String?

    /** Writes [text], or deletes the file when [text] is null. */
    fun write(name: String, text: String?)
}

/** A [CrashStore] in memory, for targets without a file system the SDK uses (reports do not survive a restart). */
internal class MemoryCrashStore : CrashStore {
    @Volatile
    private var files: Map<String, String> = emptyMap()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String?) {
        files = if (text == null) files - name else files + (name to text)
    }
}

/** The report's `platform`: `android` on Android, `kotlin` everywhere else. */
internal expect val crashPlatform: String

/** `os`, `os_version`, `model`, `locale` and what else the platform knows about the device. */
internal expect fun crashDevice(): Map<String, String>

/**
 * The store for reports of the project [project] (its host name). [directory] is the directory the app chose, null for
 * the platform default.
 */
internal expect fun crashStore(directory: String?, project: String): CrashStore

/**
 * Installs the uncaught exception hook. [onCrash] must return quickly and synchronously; the previous handler runs
 * afterwards. Returns the function that removes the hook, or null when the platform has none.
 */
internal expect fun installCrashHandler(onCrash: (thread: String, error: Throwable) -> Unit): (() -> Unit)?

/** The exception's class name (fully qualified where the platform knows it). */
internal expect fun crashTypeName(error: Throwable): String
