package com.stitchdb.auth

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchExperimental

/**
 * A [UrlLauncher] is used to open a URL in the system browser.
 */
@StitchExperimental
fun interface UrlLauncher {

    /**
     * Open the given URL in the system browser.
     * @param url The URL to open.
     */
    suspend fun openUrl(stitchdb: StitchClient, url: String)

    companion object {

        /**
         * Default implementation of [UrlLauncher] that opens the URL in the system browser.
         */
        val DEFAULT = UrlLauncher { stitchdb, url ->
            stitchdb.openExternalUrl(url)
        }

    }

}