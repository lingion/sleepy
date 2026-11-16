package com.lingion.sleepy.ui.component

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassHostState
import com.lingion.sleepy.ui.component.liquidglass.rememberGlassHostState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.lingion.sleepy.ui.screen.imports.JwWebViewLoginScreen
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Real import screen and renderer, local HTML only; never logs in or changes a timetable. */
class JwImportGlassRenderTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val web = AtomicReference<WebView>()
    private val host = AtomicReference<GlassHostState>()
    private val showDialog = mutableStateOf(false)
    private val html = """<html><meta name="viewport" content="width=device-width"><body style="margin:0;background:rgb(240,30,30);height:4000px"><input aria-label="Login" placeholder="Login"><div style="margin-top:1400px">Scroll target</div></body></html>"""

    @Test fun glassImportSurvivesPaintScrollAndReload() {
        render(UiStyle.LiquidGlass)
        expectColor(red = true)
        val original = web.get()
        onMain {
            original.evaluateJavascript("document.body.style.background='rgb(30,30,240)';window.scrollTo(0,600)", null)
        }
        expectColor(red = false)
        onMain { showDialog.value = true }
        await(10_000) {
            // Wait for the real popup window, not an idealized animation clock.
            containsText(
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow,
                "Import rendering regression",
            )
        }
        onMain { showDialog.value = false }
        expectColor(red = false)
        onMain {
            original.loadDataWithBaseURL("https://example.invalid/", html, "text/html", "UTF-8", null)
        }
        expectColor(red = true)
        // The browser and its window must retain GPU rendering; isolate sampling instead.
        onMain {
            assertTrue(web.get().rootView.isHardwareAccelerated)
            assertTrue(web.get().layerType != android.view.View.LAYER_TYPE_SOFTWARE)
        }
    }

    @Test fun glassBackdropIsNotBlankOnTheFirstScrolledFrame() {
        render(UiStyle.LiquidGlass)
        expectColor(red = true)
        await(10_000) {
            var color = 0
            onMain { color = sampleNativeBackdrop() }
            AndroidColor.alpha(color) > 200
        }
        for (scroll in listOf(300, 600, 900, 1200)) {
            val frame = java.util.concurrent.CountDownLatch(1)
            val color = java.util.concurrent.atomic.AtomicInteger()
            val failure = AtomicReference<Throwable>()
            val draw = android.view.ViewTreeObserver.OnDrawListener {
                if (frame.count > 0) {
                    try { color.set(sampleNativeBackdrop()) } catch (error: Throwable) { failure.set(error) }
                    frame.countDown()
                }
            }
            onMain {
                web.get().viewTreeObserver.addOnDrawListener(draw)
                web.get().scrollTo(0, scroll)
                web.get().invalidate()
            }
            assertTrue("A scrolled frame must arrive", frame.await(10, java.util.concurrent.TimeUnit.SECONDS))
            onMain { web.get().viewTreeObserver.removeOnDrawListener(draw) }
            failure.get()?.let { throw it }
            assertTrue("The first frame at scroll=$scroll must retain an opaque webpage source, color=${color.get()}",
                AndroidColor.alpha(color.get()) > 200)
            assertTrue("The retained source must still be the red page", AndroidColor.red(color.get()) > AndroidColor.blue(color.get()) + 100)
        }
    }

    @Test fun nativeBackdropTracksTheVisibleScrolledPageCoordinates() {
        render(UiStyle.LiquidGlass)
        expectColor(red = true)
        onMain {
            web.get().evaluateJavascript("document.body.style.background='repeating-linear-gradient(to bottom,rgb(240,30,30) 0px,rgb(240,30,30) 120px,rgb(30,30,240) 120px,rgb(30,30,240) 240px)'", null)
        }
        for (scroll in listOf(300, 600, 900, 1200)) {
            onMain { web.get().scrollTo(0, scroll); web.get().invalidate() }
            await(10_000) {
                var backdrop = 0
                val location = IntArray(2)
                var x = 0
                var y = 0
                onMain {
                    backdrop = sampleNativeBackdrop()
                    web.get().getLocationOnScreen(location)
                    x = location[0] + web.get().width / 2
                    y = location[1] + web.get().height / 2
                }
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return@await false
                try {
                    val visible = screenshot.getPixel(x, y)
                    AndroidColor.alpha(backdrop) > 200 &&
                        kotlin.math.abs(AndroidColor.red(backdrop) - AndroidColor.red(visible)) < 12 &&
                        kotlin.math.abs(AndroidColor.blue(backdrop) - AndroidColor.blue(visible)) < 12
                } finally { screenshot.recycle() }
            }
        }
    }

    private fun sampleNativeBackdrop(): Int {
        val current = host.get()
        val coordinates = checkNotNull(current.coordinates)
        val view = web.get()
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val probe = coordinates.windowToLocal(Offset(location[0] + view.width / 2f, location[1] + view.height / 2f))
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            CanvasDrawScope().draw(
                Density(view.resources.displayMetrics.density), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap()), Size(view.width.toFloat(), view.height.toFloat()),
            ) {
                translate(-probe.x, -probe.y) {
                    current.nativeScenes.last().drawViewport(this, coordinates)
                }
            }
            return bitmap.getPixel(0, 0)
        } finally { bitmap.recycle() }
    }

    @Test fun materialImportKeepsItsNormalHardwareRendering() {
        render(UiStyle.Classic)
        expectColor(red = true)
        onMain {
            assertTrue(web.get().isHardwareAccelerated)
            assertTrue(web.get().layerType != android.view.View.LAYER_TYPE_SOFTWARE)
        }
    }

    private fun render(style: UiStyle) {
        activity.scenario.onActivity { screen -> screen.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    val scene = rememberGlassHostState()
                    host.set(scene)
                    GlassHost(state = scene, enabled = style == UiStyle.LiquidGlass) {
                        JwWebViewLoginScreen(
                            school = JwSchoolInfo("T", "Local render regression", "about:blank", JwProtocol.TYPE_ZF_NEW),
                            onHtmlCaptured = { _, _, _, _ -> },
                            onCaptureError = { _, _ -> },
                            onBack = {},
                            onWebViewReady = { view ->
                                web.set(view)
                                view.loadDataWithBaseURL("https://example.invalid/", html, "text/html", "UTF-8", null)
                            },
                        )
                        if (showDialog.value) {
                            GlassAlertDialog(onDismissRequest = { showDialog.value = false },
                                confirmButton = { Text("OK") }, title = { Text("Import rendering regression") },
                                text = { Text("Local content stays behind this glass popup.") })
                        }
                    }
                }
            }
        }
        }
        await(20_000) { web.get() != null }
    }

    private fun containsText(node: android.view.accessibility.AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == text) return true
        return (0 until node.childCount).any { containsText(node.getChild(it), text) }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            android.os.SystemClock.sleep(50)
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.takeScreenshot()?.let { shot ->
            val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "import-render-failure.png")
            file.outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            shot.recycle()
        }
        throw AssertionError("Import renderer did not produce the expected frame within ${timeoutMs}ms; active root=${automation.rootInActiveWindow}")
    }

    private fun onMain(block: () -> Unit) {
        val finished = java.util.concurrent.CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { block() } catch (e: Throwable) { failure.set(e) } finally { finished.countDown() }
        }
        assertTrue("Import UI thread must remain responsive", finished.await(10, java.util.concurrent.TimeUnit.SECONDS))
        failure.get()?.let { throw it }
    }

    private fun expectColor(red: Boolean) {
        val location = IntArray(2)
        await(20_000) {
            val current = web.get() ?: return@await false
            var x = 0
            var y = 0
            onMain {
                current.getLocationOnScreen(location)
                x = location[0] + current.width / 2
                y = location[1] + current.height / 2
            }
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: return@await false
            try {
                if (x !in 0 until shot.width || y !in 0 until shot.height) return@await false
                val color = shot.getPixel(x, y)
                if (red) AndroidColor.red(color) > AndroidColor.blue(color) + 100
                else AndroidColor.blue(color) > AndroidColor.red(color) + 100
            } finally { shot.recycle() }
        }
    }
}
