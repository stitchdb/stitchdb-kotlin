import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra

val Project.stitchdbVersion get() = System.getProperty("StitchVersion") ?: extra["stitchdb-version"].toString()

fun Project.applyPublishing() {
    extensions.configure(MavenPublishBaseExtension::class) {
        publishToMavenCentral()

        if (System.getProperty("DisableSigning") != "true") {
            signAllPublications()
        }
        coordinates(extra["base-group"].toString(), this@applyPublishing.name, stitchdbVersion)

        pom {
            name.set(this@applyPublishing.name)
            description.set(this@applyPublishing.description ?: "A Kotlin Multiplatform StitchDB SDK")
            inceptionYear.set("2024")
            url.set("https://github.com/stitchdb/stitchdb-kotlin")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://github.com/stitchdb/stitchdb-kotlin")
                    distribution.set("https://github.com/stitchdb/stitchdb-kotlin")
                }
            }
            developers {
                developer {
                    id.set("stitchdb")
                    name.set("StitchDB")
                    url.set("https://github.com/stitchdb/")
                }
            }
            scm {
                url.set("https://github.com/stitchdb/stitchdb-kotlin")
                connection.set("scm:git:git://github.com/stitchdb/stitchdb-kotlin.git")
                developerConnection.set("scm:git:ssh://git@github.com/stitchdb/stitchdb-kt.git")
            }
        }
    }
}