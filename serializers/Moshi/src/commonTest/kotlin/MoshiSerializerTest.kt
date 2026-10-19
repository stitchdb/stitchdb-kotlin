import com.stitchdb.StitchClient
import com.stitchdb.decode
import com.stitchdb.encode
import com.stitchdb.serializer.MoshiSerializer
import com.stitchdb.testing.createMockedStitchClient
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals

class MoshiSerializerTest {

    private lateinit var stitchdbClient: StitchClient
    
    @Test
    fun testMoshiSerializer() {
        val serializer = MoshiSerializer()
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
    fun testMoshiSerializerForNulls() {
        val serializer = MoshiSerializer()
        stitchdbClient = createMockedStitchClient(
            configuration = {
                defaultSerializer = serializer
            })
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