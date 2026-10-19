import com.stitchdb.CurrentPlatformTarget
import com.stitchdb.PlatformTarget
import com.stitchdb.getOSInformation
import kotlin.test.Test
import kotlin.test.assertEquals

actual class PlatformTargetTest {

    @Test
    actual fun testPlatformTarget() {
        assertEquals(PlatformTarget.JVM, CurrentPlatformTarget, "The current platform target should be DESKTOP")
    }

    @Test
    fun testGetOSInformation() {
        System.setProperty("os.name", "Linux")
        System.setProperty("os.version", "5.4.0")
        val osInfo = getOSInformation()
        assertEquals("Linux", osInfo?.name, "OS name should be Linux")
        assertEquals("5.4.0", osInfo?.version, "OS version should be 5.4.0")
    }

}