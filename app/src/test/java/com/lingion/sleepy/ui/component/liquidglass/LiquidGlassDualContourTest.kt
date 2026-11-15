package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LiquidGlassDualContourTest {
    @Test fun independentCentersAndRadiiIgnoreTheCarrierCenter() {
        val first = path(Rect(20.5f, 20.5f, 100.5f, 60.5f), 8f)
        val second = path(Rect(10.5f, 90.5f, 150.5f, 170.5f), 26f)
        val uniforms = uniforms(first to second)
        assertEquals(3f, uniforms.getValue("uOutlined")[0], 0f)
        assertArrayEquals(floatArrayOf(60.5f, 40.5f, 80.5f, 130.5f),
            uniforms.getValue("uProfileCenters"), .001f)
        listOf("uProfile", "uProfile2").forEach { name ->
            assertTrue("Every convex contour must retain a positive radius in every direction",
                uniforms.getValue(name).all { it.isFinite() && it > 0f })
        }
        val firstProfile = uniforms.getValue("uProfile")
        val secondProfile = uniforms.getValue("uProfile2")
        assertEquals(40f, firstProfile[0], .04f)
        assertEquals(20f, firstProfile[16], .04f)
        assertEquals(70f, secondProfile[0], .04f)
        assertEquals(40f, secondProfile[16], .04f)

        val translation = Offset(37f, 19f)
        val translated = uniforms(Path().apply { addPath(first, translation) } to
            Path().apply { addPath(second, translation) })
        assertArrayEquals(floatArrayOf(97.5f, 59.5f, 117.5f, 149.5f),
            translated.getValue("uProfileCenters"), .001f)
        assertArrayEquals(firstProfile, translated.getValue("uProfile"), .04f)
        assertArrayEquals(secondProfile, translated.getValue("uProfile2"), .04f)
    }

    private fun path(rect: Rect, radius: Float): Path {
        val native = android.graphics.Path()
        LiquidGlassSuperellipse.buildPath(
            LiquidGlassSuperellipse.Rect2(rect.left, rect.top, rect.right, rect.bottom),
            LiquidGlassSuperellipse.Radii.makeRadius(radius),
        ).toAndroidPath(native)
        return native.asComposePath()
    }

    private fun uniforms(contours: Pair<Path, Path>): Map<String, FloatArray> = LiquidGlassShader.build(
        size = Size(200f, 200f),
        corners = LiquidGlassCornerRadii(0f, 0f, 0f, 0f),
        style = LiquidGlassStyle(Color.White),
        atlasSizePx = Size(200f, 200f),
        sampling = LiquidGlassShader.sampling(Offset.Zero, 1f, Rect(0f, 0f, 200f, 200f)),
        dualContours = contours,
    )

}
