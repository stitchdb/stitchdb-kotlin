import com.stitchdb.auth.AuthConfig

actual fun AuthConfig.platformSettings() {
    enableLifecycleCallbacks = false
}