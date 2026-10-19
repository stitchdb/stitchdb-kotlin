package com.stitchdb.storage.resumable

import com.stitchdb.annotations.StitchInternal

@StitchInternal
actual fun createDefaultResumableCache(): ResumableCache = MemoryResumableCache()