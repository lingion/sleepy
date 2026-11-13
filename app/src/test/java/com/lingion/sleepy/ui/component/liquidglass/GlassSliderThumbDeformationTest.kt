package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassSliderThumbDeformationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun carrierUsesIndependentAxesAndRetainsReleaseDeformationWhileRangeStaysIsotropic() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSlider)
        motion.elevation.jumpTo(1f)
        motion.jelly.jumpTo(.15f)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Column {
                            Box(Modifier.size(120.dp, 96.dp), contentAlignment = Alignment.Center) {
                                GlassSliderThumb(null, Modifier.testTag("single"), motionOverride = motion)
                            }
                            Box(Modifier.size(120.dp, 96.dp), contentAlignment = Alignment.Center) {
                                GlassSliderThumb(null, Modifier.testTag("range"), range = true, motionOverride = motion)
                            }
                        }
                    }
                }
            }
        }

        fun verify(lift: Float, stretch: Float) {
            val single = compose.onNodeWithTag("single", true).getUnclippedBoundsInRoot()
            val range = compose.onNodeWithTag("range", true).getUnclippedBoundsInRoot()
            val scale = 1f + .55f * lift
            val y = NativeGlassDragDeformation.Spec.Slider.let {
                -stretch * if (stretch >= 0f) it.positiveXAspect.toFloat() else it.negativeXAspect.toFloat()
            }
            assertEquals(37f * scale * (1f + stretch), (single.right - single.left).value, 1f)
            assertEquals(24f * scale * (1f + y), (single.bottom - single.top).value, 1f)
            assertEquals("size changes retain the allocated horizontal center", 60f, (single.left.value + single.right.value) / 2, 1f)
            assertEquals("size changes retain the allocated vertical center", 48f, (single.top.value + single.bottom.value) / 2, 1f)
            assertEquals(27.75f * scale, (range.right - range.left).value, 1f)
            assertEquals(18f * scale, (range.bottom - range.top).value, 1f)
        }

        verify(1f, .15f)
        compose.runOnIdle { motion.jelly.jumpTo(-.15f) }
        verify(1f, -.15f)
        compose.runOnIdle { motion.elevation.jumpTo(0f) }
        verify(0f, -.15f)
    }
}
