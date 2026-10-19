package com.stitchdb.postgrest

import com.stitchdb.annotations.StitchInternal
import kotlin.reflect.KProperty1

@StitchInternal
actual fun <T, V> getSerialName(property: KProperty1<T, V>) = property.name
