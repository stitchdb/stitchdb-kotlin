package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal

@StitchInternal
actual suspend fun Auth.setupPlatform() = initDone()