package com.stitchdb.dsl

import com.stitchdb.annotations.StitchInternal
import kotlin.reflect.KProperty

@StitchInternal
class Required<T> {
    private var value: T? = null

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T =
        value ?: error("${prop.name} is required")

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, v: T) {
        value = v
    }
}

@StitchInternal
fun <T> required() = Required<T>()