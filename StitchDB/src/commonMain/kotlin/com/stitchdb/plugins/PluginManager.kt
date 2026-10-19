package com.stitchdb.plugins

import com.stitchdb.StitchClientBuilder
import com.stitchdb.logging.StitchLogger
import com.stitchdb.logging.d

/**
 * The plugin manager is used to manage installed plugins
 * @param installedPlugins A map of installed plugins. You can install plugins by using the [StitchClientBuilder.install] method
 */
class PluginManager(
    val installedPlugins: Map<String, StitchPlugin<*>>,
    val logger: StitchLogger
) {

    init {
        logger.d { "PluginManager initialized with plugins: ${installedPlugins.keys}" }
    }

    /**
     * Retrieve an installed plugin using it's [Provider] or null if no such plugin is installed
     */
    inline fun <reified Plugin: StitchPlugin<Config>, Config, Provider : StitchPluginProvider<Config, Plugin>> getPluginOrNull(provider: Provider): Plugin? {
        return installedPlugins[provider.key] as? Plugin
    }

    /**
     * Retrieve an installed plugin using it's [Provider]
     */
    inline fun <reified Plugin: StitchPlugin<Config>, Config, Provider : StitchPluginProvider<Config, Plugin>> getPlugin(provider: Provider): Plugin {
        return getPluginOrNull(provider) ?: error("Plugin ${provider.key} not installed or not of type ${Plugin::class.simpleName}. Consider installing ${Plugin::class.simpleName} within your StitchClientBuilder")
    }

    /**
     * Closes all installed plugins
     */
    suspend inline fun closeAllPlugins() {
        installedPlugins.values.forEach { it.close() }
    }

}