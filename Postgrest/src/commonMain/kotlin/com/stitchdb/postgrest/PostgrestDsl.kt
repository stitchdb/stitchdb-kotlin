package com.stitchdb.postgrest

import com.stitchdb.annotations.StitchInternal

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@StitchInternal
annotation class PostgrestDsl
