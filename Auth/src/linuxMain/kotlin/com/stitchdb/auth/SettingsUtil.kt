package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal

@StitchInternal
actual fun Auth.createDefaultSessionManager(): SessionManager = MemorySessionManager()

@StitchInternal
actual fun Auth.createDefaultCodeVerifierCache(): CodeVerifierCache = MemoryCodeVerifierCache()