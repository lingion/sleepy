package com.lingion.sleepy.ui.component

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lingion.sleepy.ui.component.liquidglass.BackdropLumaSample
import com.lingion.sleepy.ui.component.liquidglass.DockBackdropLuma
import com.lingion.sleepy.ui.component.liquidglass.DockMaterialDark
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassNavigationBar
import com.lingion.sleepy.ui.component.liquidglass.LocalDockBackdropAppearanceEnabled
import com.lingion.sleepy.ui.component.liquidglass.LocalDockLumaObserver
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.ui.theme.UiStyle
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual source sampling/state integration; native optical differences remain descriptive. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DockBackdropAppearanceRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var surface: OpticalCaptureWindow
    private val samples = ConcurrentLinkedQueue<BackdropLumaSample>()
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "dock-appearance-94").apply { check(isDirectory || mkdirs()) }

    @Test fun productionDockRetainsItsAppearanceAcrossTheGrayRiseAndReturn() {
        assumeTrue("Enable the fixed-budget capture with glassDockAppearance=true",
            InstrumentationRegistry.getArguments().getString("glassDockAppearance") == "true")
        var gray by mutableIntStateOf(179)
        var adaptive by mutableStateOf(false)
        val stages = JSONArray()
        compose.setContent {
            val view = LocalView.current
            SideEffect { surface = OpticalCaptureWindow.from(view) }
            SleepyThemeProvider(darkTheme = true) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalDockBackdropAppearanceEnabled provides adaptive,
                    LocalDockLumaObserver provides { samples.add(it) }) {
                    val source = rememberLayerBackdrop()
                    Box(Modifier.requiredSize(375.dp, 667.dp).background(Color(0xFFF0F0F5))) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(source)) {
                            drawRect(Color(0xFF1A1A1A))
                            drawRect(Color(gray, gray, gray), Offset(0f, 550.dp.toPx()),
                                Size(size.width, 117.dp.toPx()))
                        }
                        LiquidGlassNavigationBar(
                            listOf(PillNavItemSpec(Icons.Filled.CalendarToday, "Schedule"),
                                PillNavItemSpec(Icons.Filled.WbSunny, "Today"),
                                PillNavItemSpec(Icons.Filled.GridView, "Manage"),
                                PillNavItemSpec(Icons.Filled.Person, "Mine")),
                            0, {}, Modifier.offset(y = 572.dp).requiredSize(375.dp, 86.dp), source,
                        )
                    }
                }
            }
        }
        val grays = listOf(179, 204, 217, 230, 204, 179, 128, 77, 26)
        val expected = listOf(true, true, true, false, false, false, true, true, true)
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { adaptive = enabled }
            grays.forEachIndexed { index, value ->
                val changedAt = System.nanoTime()
                compose.runOnIdle { gray = value }
                surface.awaitHwuiCommit()
                // Fixed observation budget, independent of the desired state or score.
                SystemClock.sleep(3_000)
                val node = compose.onNodeWithTag("dock-material", useUnmergedTree = true).fetchSemanticsNode()
                val captureStarted = System.nanoTime()
                val bitmap = surface.capture()
                bitmap.saveOpticalPng(File(directory(), "${if (enabled) "adaptive" else "baseline"}-gray-$index-$value.png"))
                bitmap.recycle()
                stages.put(JSONObject().put("stage", index).put("gray", value).put("adaptive", enabled)
                    .put("expected_dark", if (enabled) expected[index] else true)
                    .put("observed_dark", node.config[DockMaterialDark])
                    .put("luma", node.config.getOrNull(DockBackdropLuma))
                    .put("request_sequence", samples.lastOrNull()?.requestSequence)
                    .put("changed_at_ns", changedAt).put("capture_started_ns", captureStarted)
                    .put("capture_completed_ns", System.nanoTime())
                    .put("material_bounds_root_px", JSONArray(listOf(node.boundsInRoot.left, node.boundsInRoot.top,
                        node.boundsInRoot.right, node.boundsInRoot.bottom)))
                    .put("window_geometry", surface.geometry()))
                File(directory(), "production-gray.json").writeText(JSONObject().put("status", "running")
                    .put("stages", stages).put("samples", JSONArray(samples.map(::sampleJson))).toString(2))
                assertEquals("stage$index gray$value", if (enabled) expected[index] else true, node.config[DockMaterialDark])
                if (enabled) assertTrue(abs(node.config[DockBackdropLuma] - value / 255f) < .012f)
            }
        }
        File(directory(), "production-gray.json").writeText(JSONObject().put("scope", "Engineering source/hysteresis integration, not native material/timing parity")
            .put("stages", stages).put("status", "passed").put("samples", JSONArray(samples.map(::sampleJson))).toString(2))
    }

    private fun sampleJson(s: BackdropLumaSample) = JSONObject().put("luma", s.meanLuma)
        .put("sequence", s.requestSequence).put("generation", s.generation)
        .put("started_at_ns", s.startedAtNanos).put("completed_at_ns", s.completedAtNanos)
        .put("snapshot_completed_ns", s.snapshotCompletedAtNanos).put("end_to_end_ns", s.endToEndNanos)
        .put("snapshot_main_ns", s.snapshotMainNanos).put("readback_ns", s.readbackNanos)
        .put("source_dirty_while_busy", s.sourceDirtyWhileBusy).put("accepted_count", s.acceptedCount)
        .put("discarded_count", s.discardedCount).put("argb", JSONArray(s.sampledArgb))
}
