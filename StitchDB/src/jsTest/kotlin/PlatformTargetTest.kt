import com.stitchdb.CurrentPlatformTarget
import com.stitchdb.PlatformTarget
import kotlin.test.Test
import kotlin.test.assertEquals

actual class PlatformTargetTest {

    @Test
    actual fun testPlatformTarget() {
        assertEquals(PlatformTarget.JS, CurrentPlatformTarget, "The current platform target should be WEB")
    }

}