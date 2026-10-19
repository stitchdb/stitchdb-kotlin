package com.stitchdb

import io.stitchdb.stitchdb.getOSVersion

internal actual fun getOSInformation(): OSInformation? = OSInformation(
    name = "iOS",
    version = getOSVersion()
)