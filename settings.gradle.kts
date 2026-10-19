
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

// Main Modules
include("Auth")
include("Postgrest")
include("Storage")
include("Realtime")
include("Functions")
include("StitchDB")
include("bom")

// Test module
include("test-common")

// Serializers
include(":serializers:Moshi")
project(":serializers:Moshi").name = "serializer-moshi"
include(":serializers:Jackson")
project(":serializers:Jackson").name = "serializer-jackson"


// Renames
project(":Auth").name = "auth-kt"
project(":Postgrest").name = "postgrest-kt"
project(":Storage").name = "storage-kt"
project(":Realtime").name = "realtime-kt"
project(":Functions").name = "functions-kt"
project(":StitchDB").name = "stitchdb-kt"
rootProject.name = "stitchdb-kt"

