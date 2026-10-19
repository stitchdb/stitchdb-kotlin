package com.stitchdb.auth

import com.stitchdb.StitchClient
import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.SW_SHOWNORMAL
import platform.windows.ShellExecuteW

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun StitchClient.openExternalUrl(url: String) {
    ShellExecuteW(null, "open", url, null, null, SW_SHOWNORMAL);
}