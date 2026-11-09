package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h1800dp-mdpi")
class SmartPeriodLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowGridReservesReadableCellsAndSeparatesDeleteFromInput() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 1.5f)) {
                    GlassHost {
                        Box(Modifier.width(264.dp)) {
                            SmartPeriodEditor(
                                config = SmartPeriodConfig(totalPeriods = 12, durations = listOf(DurationOption(30, false))),
                                onConfigChange = {}, dbgTagPrefix = "smart",
                            )
                        }
                    }
                }
            }
        }
        val cells = (0..11).map { compose.onNodeWithTag("smart_dur#0_pos#$it", true).fetchSemanticsNode().boundsInRoot }
        cells.forEach { assertTrue("readable cell width: $it", it.width >= 47.9f && it.height >= 47.9f) }
        assertTrue("narrow grids must wrap before the eighth cell", cells[7].top > cells[0].top)
        for (a in cells.indices) for (b in a + 1 until cells.size) {
            assertTrue("cells $a and $b cannot overlap", !cells[a].overlaps(cells[b]))
        }
        val number = compose.onNodeWithTag("smart_dur#0_min", true).fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithTag("smart_dur#0_del", true).fetchSemanticsNode().boundsInRoot
        assertTrue("field and deletion action need an 8dp gutter", delete.left - number.right >= 7.9f)
        assertTrue("delete touch target", delete.width >= 47.9f && delete.height >= 47.9f)
    }
}
