import com.stitchdb.StitchClient
import com.stitchdb.decode
import com.stitchdb.encode
import com.stitchdb.serializer.JacksonSerializer
import com.stitchdb.testing.createMockedStitchClient
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JacksonSerializerTest {

    private lateinit var stitchdbClient: StitchClient
    
    @Test
    fun testJacksonSerializer() {
        val serializer = JacksonSerializer()
        stitchdbClient = createMockedStitchClient(
            configuration = {
                defaultSerializer = serializer
            }
        )
        assertEquals(serializer, stitchdbClient.defaultSerializer)
        val value = mapOf("key" to "value")
        val encoded = serializer.encode(value)
        val decoded = serializer.decode<Map<String, String>>(encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun testJacksonSerializerForNulls() {
        val serializer = JacksonSerializer()
        stitchdbClient = createMockedStitchClient(
            configuration = {
                defaultSerializer = serializer
            }
        )
        assertEquals(serializer, stitchdbClient.defaultSerializer)
        val value = mapOf<String, String?>("key" to null)
        val encoded = serializer.encode(value)
        val decoded = serializer.decode<Map<String, String?>>(encoded)
        assertEquals(value, decoded)
    }

    @AfterTest
    fun cleanup() {
        runTest {
            if(::stitchdbClient.isInitialized) {
                stitchdbClient.close()
            }
        }
    }
}