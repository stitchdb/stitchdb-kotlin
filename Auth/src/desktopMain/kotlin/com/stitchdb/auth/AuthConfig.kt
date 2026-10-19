package com.stitchdb.auth

import com.stitchdb.auth.server.HttpCallbackHtml
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The configuration for [Auth]
 */
actual class AuthConfig : AuthConfigDefaults() {

    /**
     * The current http callback configuration for the web server, when logging in with OAuth or SSO.
     */
    var httpCallbackConfig: HttpCallbackConfig = HttpCallbackConfig()
        internal set

    /**
     * Configures the http callback for the web server, when logging in with OAuth or SSO.
     */
    fun httpCallbackConfig(block: HttpCallbackConfig.() -> Unit) {
        httpCallbackConfig = HttpCallbackConfig().apply(block)
    }

}

/**
 * Http callback configuration for the web server, when logging in with OAuth.
 *
 * @property httpPort The port the web server is running on, when logging in with OAuth. Defaults to 0 (random port).
 * @property timeout The timeout for the web server, when logging in with OAuth. Defaults to 1 minute.
 * @property htmlTitle The title of the html page, when logging in with OAuth. Defaults to "StitchDB Auth".
 * @property redirectHtml The html content of the redirect page, when logging in with OAuth. Defaults to a page with a title, text and icon.
 */
data class HttpCallbackConfig(
    var httpPort: Int = 0,
    var timeout: Duration = 1.minutes,
    var htmlTitle: String = "StitchDB Auth",
    var redirectHtml: String = HttpCallbackHtml.redirectPage("https://stitchdb.com/icon-192.png", "StitchDB Auth", "Logged in. You may continue in your app")
)