package com.stitchdb.auth

import com.russhwolf.settings.Settings
import com.stitchdb.annotations.StitchInternal
import io.ktor.util.PlatformUtils.IS_NODE

@StitchInternal
fun createDefaultSettings() = try {
    Settings()
} catch(e: Exception) {
    error("Failed to create default settings for SettingsSessionManager. You might have to provide a custom settings instance or a custom session manager. Learn more at https://github.com/stitchdb/stitchdb-kotlin")
}

@StitchInternal
fun createDefaultSettingsKey(stitchdbUrl: String) =
    "stitchdb-${stitchdbUrl.removeSuffix("/").replace('/', '-').replace('.', '-')}"

@StitchInternal
actual fun Auth.createDefaultSessionManager(): SessionManager =
    if(!IS_NODE)
        SettingsSessionManager(
            key = "${createDefaultSettingsKey(stitchdbClient.stitchdbUrl)}-${SettingsSessionManager.SETTINGS_KEY}",
        )
    else
        MemorySessionManager()
@StitchInternal
actual fun Auth.createDefaultCodeVerifierCache(): CodeVerifierCache =
    if(!IS_NODE)
        SettingsCodeVerifierCache(
            key = "${createDefaultSettingsKey(stitchdbClient.stitchdbUrl)}-${SettingsCodeVerifierCache.SETTINGS_KEY}",
        )
    else
        MemoryCodeVerifierCache()