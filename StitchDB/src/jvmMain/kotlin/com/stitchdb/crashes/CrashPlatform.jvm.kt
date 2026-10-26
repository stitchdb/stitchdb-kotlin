package com.stitchdb.crashes

import java.io.File
import java.io.IOException
import java.util.Locale

internal actual val crashPlatform: String = "kotlin"

internal actual fun crashDevice(): Map<String, String> = buildMap {
    System.getProperty("os.name")?.let { put("os", it) }
    System.getProperty("os.version")?.let { put("os_version", it) }
    System.getProperty("os.arch")?.let { put("arch", it) }
    put("model", listOfNotNull(System.getProperty("java.vm.vendor"), System.getProperty("java.vm.name")).joinToString(" "))
    put("runtime", "jvm")
    System.getProperty("java.version")?.let { put("runtime_version", it) }
    put("locale", Locale.getDefault().toLanguageTag())
}

/**
 * Default: `~/.stitchdb/crashes/<project host>`, or `<java.io.tmpdir>/stitchdb-crashes/<project host>` when there is no
 * writable home directory (containers, services).
 */
internal actual fun crashStore(directory: String?, project: String): CrashStore {
    if (directory != null) return FileCrashStore(File(directory))
    val home = System.getProperty("user.home")?.let { File(it) }?.takeIf { it.isDirectory && it.canWrite() }
    val dir = if (home != null) File(home, ".stitchdb/crashes/$project") else File(System.getProperty("java.io.tmpdir"), "stitchdb-crashes/$project")
    return FileCrashStore(dir)
}

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
            // What the JVM prints without a handler (ThreadGroup would call us again).
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
