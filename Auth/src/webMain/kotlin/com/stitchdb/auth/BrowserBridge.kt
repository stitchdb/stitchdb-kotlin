package com.stitchdb.auth

import com.stitchdb.annotations.StitchInternal
import org.w3c.dom.Window
import kotlin.js.ExperimentalWasmJsInterop

@StitchInternal
interface BrowserBridge {

    val hash: String
    val href: String

    fun replaceCurrentUrl(newUrl: String)

}

internal class BrowserBridgeImpl(
    private val window: Window = kotlinx.browser.window
): BrowserBridge {

    override val hash: String
        get() = window.location.hash

    override val href: String
        get() = window.location.href

    @OptIn(ExperimentalWasmJsInterop::class)
    override fun replaceCurrentUrl(newUrl: String) {
        window.history.replaceState(null, window.document.title, newUrl)
    }

}