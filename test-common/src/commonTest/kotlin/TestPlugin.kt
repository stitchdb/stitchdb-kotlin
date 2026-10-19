import com.stitchdb.StitchClient
import com.stitchdb.StitchClientBuilder
import com.stitchdb.plugins.StitchPlugin
import com.stitchdb.plugins.StitchPluginProvider

class TestPlugin(override val config: Config, override val stitchdbClient: StitchClient) :
    StitchPlugin<TestPlugin.Config> {

    data class Config(var testValue: Boolean = false)

    companion object : StitchPluginProvider<Config, TestPlugin> {

        override val key = "test"

        override fun createConfig(init: Config.() -> Unit): Config {
            return Config().apply(init)
        }

        override fun create(stitchdbClient: StitchClient, config: Config): TestPlugin {
            return TestPlugin(config, stitchdbClient)
        }

        override fun setup(builder: StitchClientBuilder, config: Config) {
            builder.useHTTPS = config.testValue
        }

    }

}