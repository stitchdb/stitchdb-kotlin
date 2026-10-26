package com.stitchdb.analytics

import com.stitchdb.crashes.CrashStore
import com.stitchdb.crashes.MemoryCrashStore

// No file system the SDK writes to on this target: events wait in memory until they are sent.
internal actual fun analyticsStore(directory: String?, project: String): CrashStore = MemoryCrashStore()

internal actual fun deviceCountry(): String? = null
