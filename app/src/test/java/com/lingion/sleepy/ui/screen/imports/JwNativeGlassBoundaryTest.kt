package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.testutil.readProjectSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent a native WebView functor re-entering retained/offscreen glass render passes. */
class JwNativeGlassBoundaryTest {
    private val browser = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")

    @Test fun `import samples a safe native backdrop instead of recording live webview`() {
        assertFalse("Live WebView must not be a retained source", browser.contains(".glassBackdropSource("))
        assertTrue("Native content requires the isolated bitmap bridge", browser.contains("GlassNativeViewBackdrop("))
    }

    @Test fun `host must allow native content to bypass scene recording`() {
        val host = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/component/liquidglass/GlassHost.kt")
        assertTrue("Only bitmap scenes may be replayed while a native view is attached", host.contains("nativeScene"))
    }

    @Test fun `browser keeps hardware features and login rendering enabled`() {
        assertFalse("Software WebView rendering disables video and WebGL", browser.contains("LAYER_TYPE_SOFTWARE"))
        assertTrue(browser.contains("javaScriptEnabled = true"))
        assertTrue(browser.contains("domStorageEnabled = true"))
    }
}
