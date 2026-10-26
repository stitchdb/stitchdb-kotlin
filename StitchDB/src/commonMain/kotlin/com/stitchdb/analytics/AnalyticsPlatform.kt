package com.stitchdb.analytics

import com.stitchdb.crashes.CrashStore

/*
 * What analytics needs from the platform: the small synchronous file store crash reporting uses (in its own directory)
 * and the device's region.
 */

/**
 * The store for the queue, the anonymous id and the opt-out of the project [project] (its host name). [directory] is the
 * directory the app chose, null for the platform default.
 */
internal expect fun analyticsStore(directory: String?, project: String): CrashStore

/** The 2-letter region of the device locale (`DE` for de-DE), or null. */
internal expect fun deviceCountry(): String?
