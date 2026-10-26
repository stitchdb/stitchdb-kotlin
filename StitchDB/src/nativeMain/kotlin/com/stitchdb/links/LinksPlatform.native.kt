@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.stitchdb.links

import kotlin.native.OsFamily
import kotlin.native.Platform

internal actual val linkPlatform: String? = if (Platform.osFamily == OsFamily.IOS) "ios" else null
