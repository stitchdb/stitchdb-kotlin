package com.stitchdb.analytics

import android.content.Context
import com.stitchdb.crashes.CrashStore
import com.stitchdb.crashes.FileCrashStore
import java.io.File
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Default without a [Context]: `stitchdb-analytics/<project host>` in the app's cache directory (`java.io.tmpdir` on
 * Android). Pass a [Context] to [start] to use `filesDir` instead, which the system does not clear.
 */
internal actual fun analyticsStore(directory: String?, project: String): CrashStore =
    FileCrashStore(if (directory != null) File(directory) else File(System.getProperty("java.io.tmpdir"), "stitchdb-analytics/$project"))

internal actual fun deviceCountry(): String? = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase()

/**
 * [AnalyticsClient.start] keeping the queue, the anonymous id and the opt-out in
 * `context.filesDir/stitchdb-analytics/<project host>`, so events survive the app being killed while offline.
 */
fun AnalyticsClient.start(
    context: Context,
    release: String? = null,
    beforeSend: ((AnalyticsEvent) -> AnalyticsEvent?)? = null,
    flushInterval: Duration = 10.seconds,
) = start(
    release = release,
    beforeSend = beforeSend,
    flushInterval = flushInterval,
    directory = File(context.filesDir, "stitchdb-analytics/$projectHost").path,
)
