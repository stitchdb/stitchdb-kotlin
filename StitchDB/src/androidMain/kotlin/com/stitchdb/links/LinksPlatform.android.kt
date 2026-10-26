package com.stitchdb.links

import android.content.Context
import android.content.Intent
import java.io.File

internal actual val linkPlatform: String? = "android"

/**
 * [LinksClient.resolve] for the [Intent] that opened the app (an App Link): its `data`, or null when the intent has none
 * or it is not a short link.
 */
suspend fun LinksClient.resolve(intent: Intent, platform: String? = "android"): AppLink? =
    intent.data?.toString()?.let { resolve(it, platform) }

/**
 * [LinksClient.deferred] keeping the "already asked" flag in `context.filesDir/stitchdb-analytics/<project host>` (next
 * to the analytics queue of `analytics.start(context, ...)`), which the system does not clear.
 *
 * @param referrer the Play install referrer: `ReferrerDetails.installReferrer` from the Play Install Referrer library,
 * which your app adds and reads itself
 */
suspend fun LinksClient.deferred(context: Context, referrer: String? = null, force: Boolean = false): AppLink? =
    deferred(platform = "android", referrer = referrer, force = force, directory = File(context.filesDir, "stitchdb-analytics/$projectHost").path)
