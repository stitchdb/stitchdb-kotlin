import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

enum class StitchModule(val module: String, val extern: Boolean = false) {
    STITCHDB("stitchdb-kt"),
    AUTH("auth-kt"),
    STORAGE("storage-kt"),
    REALTIME("realtime-kt"),
    FUNCTIONS("functions-kt"),
    POSTGREST("postgrest-kt"),
}

fun KotlinDependencyHandler.addModules(vararg modules: StitchModule) {
    modules.forEach {
        api(project(":${it.module}"))
    }
}

fun DesktopExtension.configureComposeDesktop(
    name: String,
) {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = name
            packageVersion = "1.0.0"
        }
    }
}