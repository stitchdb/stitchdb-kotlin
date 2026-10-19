package com.stitchdb.plugins

import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder

/**
 * A plugin provider is used to create a plugin instance. Typically inherited by a companion object of the plugin
 */
interface StitchPluginProvider<Config, PluginInstance : StitchPlugin<Config>> {

    /**
     * The key of this plugin. This key is used to identify the plugin within the [PluginManager]
     */
    val key: String

    /**
     * Create a config for this plugin using the [init] function
     */
    fun createConfig(init: Config.() -> Unit): Config

    /**
     * Change the [StitchClientBuilder] using the [config]
     */
    fun setup(builder: StitchClientBuilder, config: Config) {}

    /**
     * Create an instance of this plugin using the [stitchdbClient] and [config]
     */
    fun create(stitchdbClient: StitchClient, config: Config) : PluginInstance

}
