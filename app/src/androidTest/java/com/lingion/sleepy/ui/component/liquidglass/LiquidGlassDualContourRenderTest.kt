package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestName
import java.io.File

/** Actual AGSL execution requires a hardware canvas; software Bitmap Canvas cannot run RuntimeShader. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassDualContourRenderTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()

    @Test fun separatedContoursKeepMaterialOnBothSidesAndPreserveTheGap() {
        val image = geometry(path(Rect(20.5f, 20.5f, 100.5f, 60.5f), 8f) to
            path(Rect(20.5f, 90.5f, 140.5f, 170.5f), 26f))
        assertTrue("Source contour has positive optical depth", depth(image, 60, 40) > 10)
        assertTrue("Menu contour has positive optical depth", depth(image, 80, 130) > 10)
        assertEquals("The space between contours must remain outside both", 0, depth(image, 60, 75))
        assertNormal(image, 30, 40, -1f, 0f)
        assertNormal(image, 130, 130, 1f, 0f)
    }

    @Test fun overlappingContoursChooseTheDeeperMember() {
        val image = geometry(path(Rect(20.5f, 20.5f, 100.5f, 80.5f), 8f) to
            path(Rect(20.5f, 70.5f, 100.5f, 150.5f), 26f))
        assertTrue("Overlap remains inside the union", depth(image, 60, 75) > 2)
        // Here the lower contour is deeper; clamping the upper contour's
        // outside distance first must not turn the lower material into an edge.
        assertTrue("The lower contour survives above-contour exclusion", depth(image, 60, 100) > 15)
        assertNormal(image, 60, 140, 0f, 1f)
    }

    @Test fun containedContourDoesNotAddAnInternalEdge() {
        val image = geometry(path(Rect(20.5f, 20.5f, 140.5f, 140.5f), 8f) to
            path(Rect(50.5f, 50.5f, 110.5f, 110.5f), 8f))
        assertTrue("The smaller contour's boundary stays deep inside the larger one", depth(image, 110, 80) > 30)
        assertNormal(image, 110, 80, 1f, 0f)
    }

    @Test fun exactContourCentersKeepFiniteUnitNormals() {
        val image = geometry(path(Rect(20.5f, 20.5f, 100.5f, 60.5f), 8f) to
            path(Rect(20.5f, 90.5f, 140.5f, 170.5f), 26f))
        assertNormal(image, 60, 40, 1f, 0f)
        assertNormal(image, 80, 130, 1f, 0f)
        assertTrue(depth(image, 60, 40) > 10)
        assertTrue(depth(image, 80, 130) > 10)
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

    /** Runs the actual production AGSL geometry functions in Skia's native renderer. */
    private fun geometry(contours: Pair<Path, Path>): Bitmap {
        val source = InstrumentationRegistry.getInstrumentation().targetContext.assets.open("shaders/liquid_glass.agsl")
            .reader().use { it.readText() }
        val functions = source.substring(source.indexOf("void addTurn("), source.indexOf("// How far inside a pill"))
        val geometryShader = RuntimeShader("""
            uniform float4 uProfile[16];
            uniform float4 uProfile2[16];
            uniform float4 uProfileCenters;
            const float kTau = 6.28318530718;
            const float kTurns = 64.0;
            $functions
            half4 main(float2 point) {
                float inset;
                float2 normal;
                dualOutlineGeometry(point, inset, normal);
                return half4(normal * 0.5 + 0.5, clamp(inset / 200.0, 0.0, 1.0), 1.0);
            }
        """.trimIndent())
        val uniforms = uniforms(contours)
        listOf("uProfile", "uProfile2", "uProfileCenters").forEach { name ->
            geometryShader.setFloatUniform(name, uniforms.getValue(name))
        }
        compose.setContent {
            val density = LocalDensity.current
            val paint = remember { Paint().apply { shader = geometryShader } }
            Canvas(Modifier.requiredSize(with(density) { 200.toDp() }).testTag("dual-contour-geometry")) {
                drawIntoCanvas { canvas ->
                    check(canvas.nativeCanvas.isHardwareAccelerated) { "RuntimeShader validation requires a hardware canvas" }
                    canvas.nativeCanvas.drawRect(0f, 0f, 200f, 200f, paint)
                }
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag("dual-contour-geometry").captureToImage().asAndroidBitmap().also { image ->
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "glass-dual-contour-geometry").apply { mkdirs() }
            File(directory, "${testName.methodName}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun depth(image: Bitmap, x: Int, y: Int): Int = android.graphics.Color.blue(image.getPixel(x, y))

    private fun assertNormal(image: Bitmap, x: Int, y: Int, normalX: Float, normalY: Float) {
        val color = image.getPixel(x, y)
        assertEquals("Normal X at ($x, $y)", normalX, android.graphics.Color.red(color) / 255f * 2f - 1f, .025f)
        assertEquals("Normal Y at ($x, $y)", normalY, android.graphics.Color.green(color) / 255f * 2f - 1f, .025f)
    }
}
