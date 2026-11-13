package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LiquidGlassExactContourTest {
    @Test fun actualOutlineAndDescriptorShareOneCachedCommandList() {
        val shape = FlClashGlassShape(32f)
        val size = Size(251f, 147f)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        assertSame(outline, shape.createOutline(size, LayoutDirection.Rtl, Density(1f)))
        val exact = checkNotNull(shape.exactContoursFor(outline)).single()
        assertSame(exact, checkNotNull(shape.exactContoursFor(outline)).single())
        assertEquals(raw(Rect(0f, 0f, 251f, 147f), 32f), exact.operations)
        // An unrelated Outline cannot borrow cached controls based only on its nominal radius.
        assertNull(shape.exactContoursFor(Outline.Generic((outline as Outline.Generic).path)))
        val dense = shape.createOutline(size, LayoutDirection.Ltr, Density(2.75f))
        assertTrue(checkNotNull(shape.exactContoursFor(dense)).single().operations != exact.operations)
    }

    @Test fun everyTransferredControlAndClosingLineMatchesRawCpuCommandsAtRealDensities() {
        val fixtures = listOf(
            raw(Rect(0f, 0f, 251f, 147f), 32f),
            raw(Rect(0f, 0f, 321f, 173f), 34f),
            raw(Rect(0f, 0f, 343f, 420f), 36f, 20f),
            raw(Rect(10.5f, 10.5f, 380.5f, 61.833332f), 8f),
        )
        fixtures.forEach { operations ->
            val source = LiquidGlassContourOutline(operations)
            val exact = checkNotNull(source.exact)
            listOf(1f, 2.75f, 3f).forEach { density ->
                val packed = checkNotNull(exact.pack(density))
                val move = operations.first() as LiquidGlassSuperellipse.PathOp.Move
                assertEquals(move.x / density, packed[0], 0f)
                assertEquals(move.y / density, packed[1], 0f)
                assertEquals(source.path.getBounds().center.x / density, packed[2], 0f)
                assertEquals(source.path.getBounds().center.y / density, packed[3], 0f)
                assertEquals(17f, packed[4], 0f)
                assertEquals(16f, packed[5], 0f)
                for (index in 0 until 16) {
                    val op = operations[index + 1] as LiquidGlassSuperellipse.PathOp.Cubic
                    assertArrayEquals(floatArrayOf(op.c1x / density, op.c1y / density, op.c2x / density,
                        op.c2y / density, op.x / density, op.y / density),
                        packed.copyOfRange(8 + index * 8, 14 + index * 8), 0f)
                }
                val line = operations[17] as LiquidGlassSuperellipse.PathOp.Line
                assertEquals(line.x / density, packed[140], 0f)
                assertEquals(line.y / density, packed[141], 0f)
                assertTrue(packed.all { it.isFinite() })
            }
        }
    }

    @Test fun aTinyActualClosingCorrectionIsRetainedRatherThanSmoothedAway() {
        val operations = raw(Rect(0f, 0f, 371f, 51.333332f), 8f).toMutableList()
        val last = operations[16] as LiquidGlassSuperellipse.PathOp.Cubic
        operations[16] = last.copy(x = last.x + .000015f, y = last.y + .000003f)
        val exact = checkNotNull(LiquidGlassContourOutline(operations).exact)
        val packed = checkNotNull(exact.pack(1f))
        val line = operations[17] as LiquidGlassSuperellipse.PathOp.Line
        assertEquals((operations[16] as LiquidGlassSuperellipse.PathOp.Cubic).x, packed[132], 0f)
        assertEquals(line.x, packed[140], 0f)
        assertEquals(line.y, packed[141], 0f)
        assertTrue(packed[132] != packed[140] || packed[133] != packed[141])
    }

    @Test fun dualContoursKeepTheirIndependentTranslatedCentersAndExplicitMode() {
        val first = LiquidGlassContourOutline(raw(Rect(20.5f, 20.5f, 100.5f, 60.5f), 8f))
        val second = LiquidGlassContourOutline(raw(Rect(10.5f, 90.5f, 150.5f, 170.5f), 26f))
        val uniforms = uniforms(first, second)
        assertEquals(5f, uniforms.getValue("uOutlined")[0], 0f)
        assertArrayEquals(floatArrayOf(60.5f, 40.5f), uniforms.getValue("uExact1").copyOfRange(2, 4), .001f)
        assertArrayEquals(floatArrayOf(80.5f, 130.5f), uniforms.getValue("uExact2").copyOfRange(2, 4), .001f)
        val translatedFirst = LiquidGlassContourOutline(raw(Rect(57.5f, 39.5f, 137.5f, 79.5f), 8f))
        val translatedSecond = LiquidGlassContourOutline(raw(Rect(47.5f, 109.5f, 187.5f, 189.5f), 26f))
        val translated = uniforms(translatedFirst, translatedSecond)
        assertArrayEquals(floatArrayOf(97.5f, 59.5f), translated.getValue("uExact1").copyOfRange(2, 4), .001f)
        assertArrayEquals(floatArrayOf(117.5f, 149.5f), translated.getValue("uExact2").copyOfRange(2, 4), .001f)
    }

    @Test fun unsupportedCommandsAndInvalidDensityRetainTheLegacyGenericPath() {
        val unsupported = LiquidGlassContourOutline(raw(Rect(0f, 0f, 200f, 100f), 0f))
        assertNull(unsupported.exact)
        val supported = LiquidGlassContourOutline(raw(Rect(0f, 0f, 200f, 100f), 24f))
        assertNotNull(supported.exact)
        assertNull(supported.exact?.pack(0f))
        assertNull(supported.exact?.pack(Float.NaN))
        assertEquals(1f, uniforms(unsupported).getValue("uOutlined")[0], 0f)
        assertEquals(4f, uniforms(supported).getValue("uOutlined")[0], 0f)
    }

    private fun raw(rect: Rect, top: Float, bottom: Float = top) = LiquidGlassSuperellipse.buildPath(
        LiquidGlassSuperellipse.Rect2(rect.left, rect.top, rect.right, rect.bottom),
        LiquidGlassSuperellipse.Radii(LiquidGlassSuperellipse.Size2(top, top), LiquidGlassSuperellipse.Size2(top, top),
            LiquidGlassSuperellipse.Size2(bottom, bottom), LiquidGlassSuperellipse.Size2(bottom, bottom)))

    private fun uniforms(first: LiquidGlassContourOutline, second: LiquidGlassContourOutline? = null) = LiquidGlassShader.build(
        size = Size(200f, 200f), corners = LiquidGlassCornerRadii(0f, 0f, 0f, 0f), style = LiquidGlassStyle(Color.White),
        atlasSizePx = Size(200f, 200f), sampling = LiquidGlassShader.sampling(Offset.Zero, 1f, Rect(0f, 0f, 200f, 200f)),
        outline = first.path, dualContours = second?.let { first.path to it.path },
        exactContours = if (second == null) first.exact?.let(::listOf) else
            first.exact?.let { a -> second.exact?.let { b -> listOf(a, b) } },
    )
}
