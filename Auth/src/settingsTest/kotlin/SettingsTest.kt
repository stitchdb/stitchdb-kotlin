import com.stitchdb.auth.createDefaultSettingsKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {

    @Test
    fun testSettingsKey() {
        val stitchdbUrl = "id.stitchdb.com"
        val key = createDefaultSettingsKey(stitchdbUrl)
        assertEquals("stitchdb-id-stitchdb-com", key)
    }

}