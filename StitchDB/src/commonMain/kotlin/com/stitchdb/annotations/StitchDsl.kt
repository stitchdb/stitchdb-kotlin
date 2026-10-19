package com.stitchdb.annotations

/**
 * Used to mark DSL functions
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@DslMarker
annotation class StitchDsl
