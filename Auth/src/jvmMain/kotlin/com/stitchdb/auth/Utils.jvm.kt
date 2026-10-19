package com.stitchdb.auth

import com.stitchdb.StitchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

internal actual suspend fun StitchClient.openExternalUrl(url: String) {
    withContext(Dispatchers.IO) {
        Desktop.getDesktop()
            .browse(URI(url))
    }
}
