package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h600dp-mdpi")
class ClassicToolbarFillTest {
    @get:Rule val compose = createComposeRule()
    @After fun clear() { UndoManager.clear() }
    private fun verify(dark: Boolean, compact: Boolean) {
        val colors = if (dark) darkColorScheme() else lightColorScheme()
        if (compact) UndoManager.capture(emptyList(), emptyList(), null)
        compose.setContent {
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    GlassHost {
                        Box(Modifier.width(if (compact) 360.dp else 840.dp)) {
                            TopBar(2, 20, "2099-09-01", null, {}, {}, {}, compact, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        listOf("schedule_switch_table", "schedule_add_course", "schedule_share").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val center = bounds.center
            assertEquals("$tag needs its classic filled circular background", colors.surfaceContainerHigh.toArgb(), image.getPixel((center.x + bounds.width / 4).roundToInt(), center.y.roundToInt()))
        }
    }
    @Test fun lightNormalToolbarKeepsFilledDiscs() = verify(false, false)
    @Test fun darkNormalToolbarKeepsFilledDiscs() = verify(true, false)
    @Test fun lightCompactToolbarKeepsFilledDiscs() = verify(false, true)
    @Test fun darkCompactToolbarKeepsFilledDiscs() = verify(true, true)
}
