import com.stitchdb.BuildConfig
import com.stitchdb.OSInformation
import com.stitchdb.StitchClient
import com.stitchdb.testing.createMockedStitchClient
import com.stitchdb.testing.withMockedStitchClient
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class StitchClientTest {

    private lateinit var client: StitchClient
    
    @Test
    fun testClientInfoHeader() {
        runTest {
            withMockedStitchClient(
                stitchdbUrl = "https://example.stitchdb.com",
                stitchdbKey = "somekey",
                requestHandler = {
                    assertEquals(
                        "stitchdb-kt/${BuildConfig.PROJECT_VERSION}; platform=TestOS; platform-version=1.0.0; runtime=kotlin; runtime-version=${KotlinVersion.CURRENT}",
                        it.headers["X-Client-Info"]
                    )
                    respond("")
                },
                configuration = {
                    osInformation = OSInformation(
                        name = "TestOS",
                        version = "1.0.0",
                    )
                },
                clientHandler = { it.httpClient.get("") }
            )
        }
    }

    @Test
    fun testAccessTokenProvider() {
        runTest {
            client = createMockedStitchClient(
                configuration = {
                    accessToken = {
                        "myToken"
                    }
                }
            )
            assertEquals("myToken", client.accessToken?.invoke())
        }
    }

    @Test
    fun testDefaultSerializer() {
        client = createMockedStitchClient(
            configuration = {
                defaultSerializer = DummySerializer()
            }
        )
        assertIs<DummySerializer>(client.defaultSerializer, "Default serializer should be an instance of DummySerializer")
    }

    @Test
    fun testClientBuilderParametersWithHttpsUrl() {
        client = createMockedStitchClient(
            stitchdbUrl = "https://example.stitchdb.com",
            stitchdbKey = "somekey"
        )
        assertEquals("example.stitchdb.com", client.stitchdbUrl, "StitchDB url should not contain https://")
        assertEquals("somekey", client.stitchdbKey, "StitchDB key should be set to somekey")
        assertEquals(
            "https://example.stitchdb.com",
            client.stitchdbHttpUrl,
            "StitchDB http url should be https://example.stitchdb.com"
        )
    }

    @Test
    fun testClientBuilderPlugins() {
        client = createMockedStitchClient(
            stitchdbUrl = "example.stitchdb.com",
            stitchdbKey = "somekey",
            configuration =  {
                install(TestPlugin) {
                    testValue = true
                }
            }
        )
        val plugin = client.pluginManager.getPluginOrNull(TestPlugin)
        //test if the plugin was correctly installed
        assertNotNull(plugin, "Plugin 'test' should not be null")
        //test if the plugin correctly modified the 'useHTTPS' parameter
        assertEquals(
            "https://example.stitchdb.com",
            client.stitchdbHttpUrl,
            "StitchDB http url should be https://example.stitchdb.com because the plugin modifies it"
        )
    }

    @AfterTest
    fun cleanup() {
        runTest {
            if(::client.isInitialized) {
                client.close()
            }
        }
    }

}