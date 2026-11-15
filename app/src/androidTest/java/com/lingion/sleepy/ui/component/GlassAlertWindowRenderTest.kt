package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassMaterialScope
import com.lingion.sleepy.ui.component.liquidglass.GlassTextButton
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassMaterialBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

/** Opt in with -e glassAlertWindowCheck true; default platform theme, without synthetic caps. */
@RunWith(AndroidJUnit4::class)
class GlassAlertWindowRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedPlatformWindowCannotConsumeThePanesDrawingRoom() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass glassAlertWindowCheck=true for the real Window regression",
            arguments.getString("glassAlertWindowCheck") == "true")
        val runId = arguments.getString("glassAlertWindowRunId") ?: System.currentTimeMillis().toString()
        require(runId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "glass-alert-window/$runId").apply { check(mkdirs() || isDirectory) }
        val report = JSONObject().put("sdk", Build.VERSION.SDK_INT).put("passed", false)
            .put("platform_reference_policy", "Default Compose Dialog requests 340dp. Run this special-cap regression only if its actual Window measures 320dp; no injected theme, private resource or synthetic width cap.")
        var referenceView: View? = null
        var productView: View? = null
        var material: GlassMaterialScope? = null
        var activations = 0
        val reference = mutableStateOf(true)
        try {
            assumeTrue("This reference fixture is recorded on API 36", Build.VERSION.SDK_INT == 36)
            compose.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDarkTheme provides false) {
                        GlassHost(Modifier.fillMaxSize()) {
                            GlassBackground(0f, Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFE1E4EB)))
                            }
                            if (reference.value) {
                                Dialog(onDismissRequest = {}) {
                                    referenceView = LocalView.current
                                    Box(Modifier.width(340.dp).height(100.dp))
                                }
                            } else {
                                GlassAlertDialog(onDismissRequest = {}, title = {
                                    productView = LocalView.current
                                    material = LocalGlassMaterialBackdrop.current as? GlassMaterialScope
                                    Text("Window regression")
                                }, text = { Text("The pane keeps its width and drawing room.") },
                                    dismissButton = { GlassTextButton({}, Modifier.testTag("window-cancel")) { Text("Cancel") } },
                                    confirmButton = { GlassTextButton({ activations++ }, Modifier.testTag("window-confirm")) { Text("Confirm") } })
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val density = compose.density.density
            report.put("density", density)
            val referenceSurface = compose.runOnIdle { OpticalCaptureWindow.from(checkNotNull(referenceView)) }
            capture(referenceSurface, directory, "reference", report)
            val actualReference = compose.runOnIdle { referenceSurface.root.width }
            val expectedReference = (320f * density).roundToInt()
            report.put("reference_expected_320dp_px", expectedReference).put("reference_actual_px", actualReference)
            assumeTrue("Default platform theme does not establish the required 320dp cap: expected $expectedReference px, actual $actualReference px; actual exported to $directory",
                actualReference == expectedReference)
            compose.runOnIdle {
                assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, checkNotNull(referenceSurface.window).attributes.width)
                reference.value = false
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val product = compose.runOnIdle { OpticalCaptureWindow.from(checkNotNull(productView)) }
            fun pane(): Rect = compose.runOnIdle {
                val coordinates = checkNotNull(checkNotNull(material).coordinates)
                Rect(coordinates.localToWindow(Offset.Zero), coordinates.localToWindow(
                    Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
            }
            fun checkPane(phase: String, expectedDp: Float): Rect {
                val actual = pane()
                val geometry = compose.runOnIdle {
                    val window = checkNotNull(product.window)
                    assertEquals("$phase keeps the product optical Window unconstrained", WindowManager.LayoutParams.MATCH_PARENT, window.attributes.width)
                    assertTrue("$phase Window includes room beyond its 320dp pane", product.root.width > expectedReference)
                    assertEquals("$phase painted pane width", expectedDp, actual.width / density, 1f / density)
                    assertTrue("$phase left halo", actual.left >= .5f)
                    assertTrue("$phase top halo", actual.top >= .5f)
                    assertTrue("$phase right halo", actual.right <= product.root.width - .5f)
                    assertTrue("$phase bottom halo", actual.bottom <= product.root.height - .5f)
                    JSONObject().put("pane_window_rect_px", JSONArray(listOf(actual.left, actual.top, actual.right, actual.bottom)))
                        .put("pane_width_dp", actual.width / density)
                }
                report.put("$phase-pane", geometry)
                capture(product, directory, phase, report, requireHalo = true)
                return actual
            }
            val rest = checkPane("rest", 320f)
            compose.onNodeWithTag("window-confirm").performTouchInput { down(center) }
            settle()
            checkPane("held", 324f)
            compose.runOnIdle { assertEquals("press never activates early", 0, activations) }
            compose.onNodeWithTag("window-confirm").performTouchInput { cancel() }
            settle()
            val released = checkPane("released", 320f)
            assertEquals(rest.left, released.left, 1f)
            assertEquals(rest.top, released.top, 1f)
            compose.runOnIdle { assertEquals("cancellation never activates", 0, activations) }
            report.put("status", "passed").put("passed", true)
        } catch (unsupported: AssumptionViolatedException) {
            report.put("status", "unsupported").put("reason", unsupported.message)
            throw unsupported
        } catch (failure: Throwable) {
            report.put("status", "failed").put("reason", failure.toString())
            throw failure
        } finally {
            File(directory, "result.json").writeText(report.toString(2))
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", "Glass Alert Window regression: ${report.optString("status")} — $directory\n")
            })
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
    }

    private fun capture(surface: OpticalCaptureWindow, directory: File, phase: String,
        report: JSONObject, requireHalo: Boolean = false) {
        val pixels = surface.capture()
        try {
            pixels.saveOpticalPng(File(directory, "$phase-window.png"))
            val scene = surface.geometry()
            report.put(phase, scene)
            if (requireHalo) {
                val core = opaqueBounds(pixels)
                scene.put("opaque_core_bbox_px", JSONArray(listOf(core.left, core.top, core.right, core.bottom)))
                assertTrue("$phase actual alpha core has left/top drawing room", core.left >= 1 && core.top >= 1)
                assertTrue("$phase actual alpha core has right/bottom drawing room", core.right <= pixels.width - 1 && core.bottom <= pixels.height - 1)
            }
        } finally { pixels.recycle() }
    }

    private fun opaqueBounds(bitmap: Bitmap): android.graphics.Rect {
        var left = bitmap.width
        var top = bitmap.height
        var right = 0
        var bottom = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) < 254) continue
            left = minOf(left, x); top = minOf(top, y)
            right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
        }
        assertTrue("actual Window contains an opaque painted pane", right > left && bottom > top)
        return android.graphics.Rect(left, top, right, bottom)
    }
}
