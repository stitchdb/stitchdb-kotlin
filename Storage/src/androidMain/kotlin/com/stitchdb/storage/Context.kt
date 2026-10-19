package com.stitchdb.storage

import android.content.Context
import androidx.startup.Initializer

private var appContext: Context? = null

internal class StitchInitializer : Initializer<Context> {
    override fun create(context: Context): Context = context.applicationContext.also { appContext = it }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

}

internal fun applicationContext(): Context = appContext ?: error("Application context not initialized")