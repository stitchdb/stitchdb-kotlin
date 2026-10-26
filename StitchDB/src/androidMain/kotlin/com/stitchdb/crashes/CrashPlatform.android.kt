package com.stitchdb.crashes

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.Locale

internal actual val crashPlatform: String = "android"

internal actual fun crashDevice(): Map<String, String> = buildMap {
    put("os", "Android")
    Build.VERSION.RELEASE?.let { put("os_version", it) }
    put("api_level", Build.VERSION.SDK_INT.toString())
    Build.MODEL?.let { put("model", it) }
    Build.MANUFACTURER?.let { put("manufacturer", it) }
    put("locale", Locale.getDefault().toLanguageTag())
}

/**
 * Default without a [Context]: `stitchdb-crashes/<project host>` in the app's cache directory (`java.io.tmpdir` on
 * Android). Pass a [Context] to [start] to use `filesDir` instead, which the system does not clear.
 */
internal actual fun crashStore(directory: String?, project: String): CrashStore =
    FileCrashStore(if (directory != null) File(directory) else File(System.getProperty("java.io.tmpdir"), "stitchdb-crashes/$project"))

/**
 * [CrashesClient.start] keeping reports in `context.filesDir/stitchdb-crashes/<project host>`, so a crash report
 * survives until the next start of the app.
 */
@Suppress("LongParameterList")
fun CrashesClient.start(
    context: Context,
    release: String? = null,
    environment: String = "production",
    captureUnhandled: Boolean = true,
    maxBreadcrumbs: Int = 50,
    beforeSend: ((CrashReport) -> CrashReport?)? = null,
) = start(
    release = release,
    environment = environment,
    captureUnhandled = captureUnhandled,
    maxBreadcrumbs = maxBreadcrumbs,
    directory = File(context.filesDir, "stitchdb-crashes/$projectHost").path,
    beforeSend = beforeSend,
)

internal actual fun installCrashHandler(onCrash: (thread: String, error: Throwable) -> Unit): (() -> Unit)? {
    val handler = CrashHandler(Thread.getDefaultUncaughtExceptionHandler(), onCrash)
    Thread.setDefaultUncaughtExceptionHandler(handler)
    return {
        // Only undo our own hook: someone may have installed theirs on top of it.
        if (Thread.getDefaultUncaughtExceptionHandler() === handler) Thread.setDefaultUncaughtExceptionHandler(handler.previous)
    }
}

internal actual fun crashTypeName(error: Throwable): String = error.javaClass.name

/** Writes the report (synchronously, inside [onCrash]) and then hands the exception to the [previous] handler. */
internal class CrashHandler(
    val previous: Thread.UncaughtExceptionHandler?,
    private val onCrash: (thread: String, error: Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            onCrash(thread.name, error)
        } catch (e: Throwable) {
            // never let reporting hide the crash
        }
        if (previous != null) {
            previous.uncaughtException(thread, error)
        } else {
            // No handler before ours (Android always has one): print like the JVM does.
            System.err.print("Exception in thread \"${thread.name}\" ")
            error.printStackTrace(System.err)
        }
    }
}

/** Small text files in [dir], written in place with java.io (synchronous, safe to call from a crashing thread). */
internal class FileCrashStore(private val dir: File) : CrashStore {
    @Synchronized
    @Suppress("SwallowedException")
    override fun read(name: String): String? = try {
        File(dir, name).takeIf { it.isFile }?.readText()
    } catch (e: IOException) {
        null
    }

    @Synchronized
    @Suppress("SwallowedException")
    override fun write(name: String, text: String?) {
        try {
            val file = File(dir, name)
            if (text == null) {
                file.delete()
                return
            }
            dir.mkdirs()
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: IOException) {
            // a full or read-only disk: the report stays in memory for this run
        } catch (e: SecurityException) {
            // not allowed to write here
        }
    }
}
