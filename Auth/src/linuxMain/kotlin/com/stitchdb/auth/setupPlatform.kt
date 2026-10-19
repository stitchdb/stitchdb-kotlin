package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal
import com.stitchdb.logging.w

@StitchInternal
actual suspend fun Auth.setupPlatform() {
    logger.w { "Linux support is experimental, please report any bugs you find!" }
    initDone()
}