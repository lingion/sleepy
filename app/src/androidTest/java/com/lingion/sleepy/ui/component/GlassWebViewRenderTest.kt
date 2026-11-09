package com.lingion.sleepy.ui.component

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.glassControl
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** Local HTML only. Proves AndroidView pixels, not a blank placeholder, enter glass sampling. */
class GlassWebViewRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun localWebViewPixelsReachGlassAndUpdateAfterJavascriptPaint() {
        val loaded = AtomicBoolean(false)
        lateinit var web: WebView
        compose.setContent {
            val observed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(160.dp), background = Color.White) {
                        com.lingion.sleepy.ui.component.liquidglass.BindGlassAndroidViewSource(observed.value)
                        GlassBackground(0f, modifier = Modifier.size(160.dp)) {
                            AndroidView(factory = { context ->
                                WebView(context).apply {
                                    web = this
                                    observed.value = this
                                    settings.javaScriptEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String) { loaded.set(true) }
                                    }
                                    loadDataWithBaseURL("https://example.invalid/", "<html><body style='margin:0;background:rgb(255,0,0);height:100vh'></body></html>", "text/html", "UTF-8", null)
                                }
                            }, modifier = Modifier.size(160.dp))
                        }
                        Box(Modifier.size(160.dp).glassControl(enabled = false))
                    }
                }
            }
        }
        compose.waitUntil(20_000) { loaded.get() }
        visualFrame(web)
        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        val red = before.getPixel(before.width / 2, before.height / 2)
        assertTrue("WebView red pixels must reach the optical surface: ${Integer.toHexString(red)}", android.graphics.Color.red(red) > android.graphics.Color.blue(red) + 30)
        compose.runOnIdle { web.evaluateJavascript("document.body.style.background='rgb(0,0,255)'", null) }
        visualFrame(web)
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        val blue = after.getPixel(after.width / 2, after.height / 2)
        assertTrue("updated AndroidView pixels must replace the previous source: ${Integer.toHexString(blue)}", android.graphics.Color.blue(blue) > android.graphics.Color.red(blue) + 30)
    }

    private fun visualFrame(web: WebView) {
        val ready = AtomicBoolean(false)
        compose.runOnIdle { web.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { ready.set(true) }
        }) }
        compose.waitUntil(20_000) { ready.get() }
    }
}
