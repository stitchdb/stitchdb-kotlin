package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal
import kotlin.time.Instant

@StitchInternal
data class SessionRefreshInformation(
    val autoRefreshStartedAt: Instant,
    val lastRefreshedAt: Instant?,
    val refreshingAt: Instant?
)
