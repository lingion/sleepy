package com.lingion.sleepy.ui.screen.mine

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the real settings callbacks, with a recomposition between every pointer move. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassDisplaySliderInputTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun gridScaleKeepsDraggingAndPersistsItsOriginalQuantizationOnRelease() {
        AppPrefs.setGridScale(context, 1f)
        show(DisplayStyleSection.GRID)
        dragAcrossFrames(thumbIndex = 0, minimumAdvance = .04f)
        val current = currentValue(0)
        assertEquals("Release persists the accepted value", current, AppPrefs.getGridScale(context), 0f)
        assertEquals("Persisted scale retains .05 increments", (current * 20f).toInt().toFloat(), current * 20f, .0001f)
    }

    @Test fun railInsetKeepsDraggingAndPersistsAnIntegerOnRelease() {
        AppPrefs.setConflictStyle(context, "rail")
        AppPrefs.setConflictRailInset(context, 8f)
        show(DisplayStyleSection.CONFLICT)
        dragAcrossFrames(thumbIndex = 0, minimumAdvance = 1f)
        val current = currentValue(0)
        assertEquals(current, AppPrefs.getConflictRailInset(context), 0f)
        assertEquals("Persisted inset retains integer dp", current.toInt().toFloat(), current, 0f)
    }

    @Test fun customCourseHueKeepsDraggingAndCommitsOneIntegerSeedOnRelease() {
        val seed = mutableFloatStateOf(120f)
        val commits = mutableListOf<Float>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    GlassHost {
                        Box(Modifier.size(320.dp, 500.dp).testTag("page").padding(16.dp)) {
                            CourseColorSection(
                                mode = AppPrefs.COURSE_COLOR_MODE_CUSTOM,
                                seed = seed.floatValue, colorless = false,
                                onColorlessSelect = {}, onModeChange = {},
                                onSeedCommit = { seed.floatValue = it; commits += it },
                                onRecalc = {}, previewGids = emptyList(),
                            )
                        }
                    }
                }
            }
        }
        // At this layout's 240dp control width, 48dp steps map to exact integer
        // degrees and hide the rounding mismatch. A fractional step exposes it.
        dragAcrossFrames(thumbIndex = 0, minimumAdvance = 20f, deltaDp = 47.25f)
        compose.runOnIdle {
            assertEquals("A drag commits exactly once", 1, commits.size)
            assertEquals("The seed retains integer-degree persistence", commits.single().toInt().toFloat(), commits.single(), 0f)
        }
        assertEquals("The released thumb accepts the persisted seed", commits.single(), currentValue(0), 0f)
    }

    private fun show(section: DisplayStyleSection) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    GlassHost {
                        Box(Modifier.size(320.dp, 400.dp).testTag("page").padding(16.dp)) {
                            ScheduleDisplayContent(section = section)
                        }
                    }
                }
            }
        }
    }

    private fun currentValue(index: Int) = compose.onAllNodesWithTag("glass-slider-end")[index]
        .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun dragAcrossFrames(thumbIndex: Int, minimumAdvance: Float, deltaDp: Float = 48f) {
        val page = compose.onNodeWithTag("page")
        val origin = page.fetchSemanticsNode().boundsInRoot.topLeft
        val thumb = compose.onAllNodesWithTag("glass-slider-end")[thumbIndex]
            .fetchSemanticsNode().boundsInRoot.center - origin
        val delta = Offset(deltaDp * compose.density.density, 0f)
        page.performTouchInput { down(thumb) }
        page.performTouchInput { moveBy(delta) }
        compose.waitForIdle()
        val first = currentValue(thumbIndex)
        page.performTouchInput { moveBy(delta) }
        compose.waitForIdle()
        assertTrue("A controlled settings echo must preserve the next MOVE", currentValue(thumbIndex) > first + minimumAdvance)
        page.performTouchInput { up() }
        compose.waitForIdle()
    }
}
