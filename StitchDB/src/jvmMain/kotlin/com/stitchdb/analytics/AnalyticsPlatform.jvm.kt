package com.stitchdb.analytics

import com.stitchdb.crashes.CrashStore
import com.stitchdb.crashes.FileCrashStore
import java.io.File
import java.util.Locale

/**
 * Default: `~/.stitchdb/analytics/<project host>`, or `<java.io.tmpdir>/stitchdb-analytics/<project host>` when
 * there is no writable home directory (containers, services).
 */
internal actual fun analyticsStore(directory: String?, project: String): CrashStore {
    if (directory != null) return FileCrashStore(File(directory))
    val home = System.getProperty("user.home")?.let { File(it) }?.takeIf { it.isDirectory && it.canWrite() }
    val dir = if (home != null) {
        File(home, ".stitchdb/analytics/$project")
    } else {
        File(System.getProperty("java.io.tmpdir"), "stitchdb-analytics/$project")
    }
    return FileCrashStore(dir)
}

internal actual fun deviceCountry(): String? = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase()
