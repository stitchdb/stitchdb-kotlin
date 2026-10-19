package com.stitchdb.plugins

import com.stitchdb.StitchClient

/**
 * A plugin is a feature that can be installed into the stitchdb client
 */
interface StitchPlugin<Config> {

    /**
     * The config for this plugin
     */
    val config: Config

    /**
     * The corresponding [StitchClient]
     */
    val stitchdbClient: StitchClient

    /**
     * Free all resources used by this plugin
     */
    suspend fun close() {}

    /**
     * Initialize the plugin. Use this function, if you want to execute code after the plugin has been created but also have to access other plugins.
     *
     * **Note:** This function is called by the [StitchClient] after all plugins have been created. Do not call this function manually.
     */
    fun init() {}

}