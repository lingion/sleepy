package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.withTransformCompensation
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w240dp-h240dp-xxxhdpi")
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
            val single = visualCarrier("single").transformedBoundsInRoot()
            val range = visualCarrier("range").transformedBoundsInRoot()
            val singleOwner = compose.onNodeWithTag("single", true).transformedBoundsInRoot()
            val scale = 1f + .55f * lift
            val y = NativeGlassDragDeformation.Spec.Slider.let {
                -stretch * if (stretch >= 0f) it.positiveXAspect.toFloat() else it.negativeXAspect.toFloat()
            }
            assertEquals(37f * scale * (1f + stretch), (single.right - single.left).value, 1f)
            assertEquals(24f * scale * (1f + y), (single.bottom - single.top).value, 1f)
            assertEquals("the semantic/touch owner retains isotropic width", 37f * scale,
                (singleOwner.right - singleOwner.left).value, 1f)
            assertEquals("the semantic/touch owner retains isotropic height", 24f * scale,
                (singleOwner.bottom - singleOwner.top).value, 1f)
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

    @Test fun releasedWhiteThumbTransformsItsCircularCapsuleEndsInsteadOfRedrawingThemAtTheFinalSize() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSlider)
        // A release-side carrier may still deform after the opaque white body
        // returns. This isolates actual contour pixels from optical shader color.
        motion.elevation.jumpTo(0f)
        motion.jelly.jumpTo(.20f)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.size(120.dp, 96.dp), background = Color.Black) {
                        Box(Modifier.size(120.dp, 96.dp).background(Color.Black).testTag("outline"),
                            contentAlignment = Alignment.Center) {
                            GlassSliderThumb(null, Modifier.testTag("single"), motionOverride = motion)
                        }
                    }
                }
            }
        }

        fun verify(stretch: Float) {
            val image = compose.onNodeWithTag("outline").captureToImage().asAndroidBitmap()
            val pixelsPerDp = image.width / 120f
            val sx = 1f + stretch
            val spec = NativeGlassDragDeformation.Spec.Slider
            val sy = 1f - stretch * if (stretch >= 0f) spec.positiveXAspect.toFloat() else spec.negativeXAspect.toFloat()
            val width = 37f * sx * pixelsPerDp
            val height = 24f * sy * pixelsPerDp
            val left = (image.width - width) / 2f
            val top = (image.height - height) / 2f
            val rx = 12f * sx * pixelsPerDp
            val ry = 12f * sy * pixelsPerDp
            var discriminatingPixels = 0
            val perCorner = IntArray(4)
            // Circle-vs-ellipse points well clear of both anti-aliased boundaries.
            // A resized capsule has identical bounds but circular radii height/2.
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val px = x + .5f - left
                val py = y + .5f - top
                val edgeX = min(px, width - px)
                if (edgeX !in 0f..min(rx, height / 2f) || py !in 0f..height) continue
                val ellipse = ((edgeX - rx) / rx).let { it * it } + ((py - ry) / ry).let { it * it }
                val r = height / 2f
                val circular = ((edgeX - r) / r).let { it * it } + ((py - r) / r).let { it * it }
                val expectedWhite = ellipse < .94f && circular > 1.06f
                val expectedBlack = ellipse > 1.06f && circular < .94f
                if (!expectedWhite && !expectedBlack) continue
                val pixel = image.getPixel(x, y)
                val red = AndroidColor.red(pixel)
                assertTrue("affine shoulder pixel ($x,$y) stretch=$stretch ellipse=$ellipse circular=$circular red=$red",
                    if (expectedWhite) red > 220 else red < 80)
                discriminatingPixels++
                perCorner[(if (px < width / 2) 0 else 1) + (if (py < height / 2) 0 else 2)]++
            }
            assertTrue("the assertion must distinguish resizing from transforming", discriminatingPixels >= 8)
            assertTrue("all four actual rendered corners are checked", perCorner.all { it >= 2 })
            assertTrue("exercise anisotropy instead of a round-equivalent transform", abs(sx - sy) > .35f)
        }

        verify(.20f)
        compose.runOnIdle { motion.jelly.jumpTo(-.20f) }
        verify(-.20f)
    }

    @Test fun canonicalTrackCompensationKeepsWorldStripesFixedUnderAnisotropicCarrier() {
        compose.setContent {
            val track = rememberLayerBackdrop()
            val compensated = androidx.compose.runtime.remember(track) { track.withTransformCompensation() }
            Box(Modifier.size(120.dp, 96.dp).testTag("mapping"), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp, 96.dp).layerBackdrop(track)) {
                    val stripe = 4.dp.toPx()
                    for (index in 0 until 30) drawRect(
                        if (index % 2 == 0) Color.Black else Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(index * stripe, 0f),
                        size = androidx.compose.ui.geometry.Size(stripe, size.height),
                    )
                }
                Box(Modifier.size(37.dp, 24.dp)
                    .graphicsLayer { scaleX = 1.2f; scaleY = .8f }
                    .testTag("mapping-carrier")
                    .drawPlainBackdrop(compensated, { RectangleShape }, effects = {},
                        onDrawBehind = { drawRect(Color.Red) }))
            }
        }
        val image = compose.onNodeWithTag("mapping").captureToImage().asAndroidBitmap()
        val parent = compose.onNodeWithTag("mapping").transformedBoundsInRoot()
        val child = compose.onNodeWithTag("mapping-carrier").transformedBoundsInRoot()
        val density = image.width / 120f
        val left = ((child.left - parent.left).value * density).toInt() + 3
        val right = ((child.right - parent.left).value * density).toInt() - 3
        val y = image.height / 2
        val stripe = 4f * density
        var checked = 0
        for (x in left until right) {
            val withinStripe = (x + .5f) % stripe
            if (min(withinStripe, stripe - withinStripe) < 2f) continue
            val expected = if (((x + .5f) / stripe).toInt() % 2 == 0) AndroidColor.BLACK else AndroidColor.WHITE
            assertEquals("the carrier samples world x=$x rather than stretching the track texture", expected, image.getPixel(x, y))
            checked++
        }
        assertTrue("the world-coordinate assertion covers multiple stripes", checked >= 40)
    }

    private fun visualCarrier(owner: String) = compose.onNode(
        hasTestTag("glass-slider-thumb-carrier") and hasAnyAncestor(hasTestTag(owner)),
        useUnmergedTree = true,
    )

    private fun SemanticsNodeInteraction.transformedBoundsInRoot(): DpRect {
        // The unclipped helper combines a transformed position with the original
        // layout size. This fixture fits inside its unclipped ancestors, so the
        // actual semantic root bounds include the carrier's graphicsLayer axes.
        val bounds = fetchSemanticsNode().boundsInRoot
        return with(compose.density) {
            DpRect(bounds.left.toDp(), bounds.top.toDp(), bounds.right.toDp(), bounds.bottom.toDp())
        }
    }
}
