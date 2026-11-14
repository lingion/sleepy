package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.shapes.Capsule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DockPlatterContourTest {
    @Test fun actualKyantControlsAndAllFourLinesReachTheExactUniformsAtCurrentSizeAndDensity() {
        val shape = DockPlatterShape()
        listOf(Size(274f, 62f), Size(309f, 62f), Size(287.25f, 61.75f)).forEach { sizeDp ->
            listOf(1f, 2.75f, 2.975f).forEach { density ->
                listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
                    val sizePx = Size(sizeDp.width * density, sizeDp.height * density)
                    val outline = shape.createOutline(sizePx, direction, Density(density)) as Outline.Generic
                    val original = Capsule().createOutline(sizePx, direction, Density(density)) as Outline.Generic
                    assertArrayEquals("Adding optical metadata must leave the Kyant CPU clip unchanged",
                        original.path.toAndroidPath().approximate(.01f), outline.path.toAndroidPath().approximate(.01f), 0f)
                    val exact = checkNotNull(shape.exactContoursFor(outline)).single()
                    val optical = checkNotNull(shape.opticalPath(sizePx, direction, Density(density)))
                    val packed = checkNotNull(exact.pack(density))
                    assertNotSame("Converting to dp must not mutate the CPU clip", outline.path, optical)
                    assertEquals(sizePx.width, outline.path.getBounds().width, .001f)
                    assertEquals(sizePx.height, outline.path.getBounds().height, .001f)
                    assertEquals(sizeDp.width, optical.getBounds().width, .001f)
                    assertEquals(sizeDp.height, optical.getBounds().height, .001f)
                    assertEquals(16f, packed[4], 0f)
                    assertEquals(2f, packed[6], 0f)
                    assertEquals(0x8888.toFloat(), packed[7], 0f)

                    // Read the original Kyant CPU path independently of descriptor.operations.
                    val iterator = PathIterator(outline.path, PathIterator.ConicEvaluation.AsConic)
                    val points = FloatArray(8)
                    var segment = 0
                    var cubics = 0
                    var lines = 0
                    while (iterator.hasNext()) {
                        when (iterator.next(points)) {
                            PathSegment.Type.Move -> assertArrayEquals(
                                floatArrayOf(points[0] / density, points[1] / density), packed.copyOfRange(0, 2), 0f)
                            PathSegment.Type.Cubic -> {
                                val base = 8 + segment * 8
                                assertArrayEquals(FloatArray(6) { points[it + 2] / density },
                                    packed.copyOfRange(base, base + 6), 0f)
                                assertEquals(0, 0x8888 and (1 shl segment))
                                cubics++
                                segment++
                            }
                            PathSegment.Type.Line -> {
                                val base = 8 + segment * 8
                                assertArrayEquals(floatArrayOf(points[2] / density, points[3] / density),
                                    packed.copyOfRange(base + 4, base + 6), 0f)
                                assertTrue(0x8888 and (1 shl segment) != 0)
                                lines++
                                segment++
                            }
                            PathSegment.Type.Close -> {
                                // Close supplies the actual straight top edge, not an invented cubic.
                                val base = 8 + segment * 8
                                assertArrayEquals(packed.copyOfRange(0, 2), packed.copyOfRange(base + 4, base + 6), 0f)
                                assertTrue(0x8888 and (1 shl segment) != 0)
                                lines++
                                segment++
                            }
                            PathSegment.Type.Done -> Unit
                            else -> error("Kyant Capsule changed its actual segment types")
                        }
                    }
                    assertEquals(12, cubics)
                    assertEquals(4, lines)
                    assertEquals(16, segment)
                    val uniforms = LiquidGlassShader.build(size = sizeDp,
                        corners = LiquidGlassCornerRadii(31f, 31f, 31f, 31f), style = LiquidGlassStyle(Color.White),
                        density = density, atlasSizePx = sizePx,
                        sampling = LiquidGlassShader.sampling(Offset.Zero, density, Rect(Offset.Zero, sizePx)),
                        outline = optical, exactContours = listOf(exact))
                    assertEquals("Dock must reach exact dispatch, not the default rounded box or profile", 4f,
                        uniforms.getValue("uOutlined")[0], 0f)
                    assertArrayEquals(packed, uniforms.getValue("uExact1"), 0f)
                    assertEquals(0f, uniforms.getValue("uCircularCorners")[0], 0f)
                }
            }
        }
    }

    @Test fun descriptorBelongsToTheActualCachedOutlineAndDoesNotLeakAcrossResize() {
        val shape = DockPlatterShape()
        val density = Density(2.75f)
        val first = shape.createOutline(Size(753.5f, 170.5f), LayoutDirection.Ltr, density)
        val exact = checkNotNull(shape.exactContoursFor(first)).single()
        assertSame(first, shape.createOutline(Size(753.5f, 170.5f), LayoutDirection.Ltr, density))
        assertNull(shape.exactContoursFor(Outline.Generic((first as Outline.Generic).path)))
        val resized = shape.createOutline(Size(849.75f, 170.5f), LayoutDirection.Ltr, density)
        val changed = checkNotNull(shape.exactContoursFor(resized)).single()
        assertTrue(exact.operations != changed.operations)
        assertSame(exact, checkNotNull(shape.exactContoursFor(first)).single())
    }

    @Test fun unmeasuredGeometryDoesNotBuildAnOpticalPathAndActualRoundOutlinesStayCircular() {
        val shape = DockPlatterShape()
        listOf(Size.Unspecified, Size.Zero, Size(-1f, 62f)).forEach {
            assertNull(shape.opticalPath(it, LayoutDirection.Ltr, Density(1f)))
        }
        assertNull(shape.opticalPath(Size(274f, 62f), LayoutDirection.Ltr, Density(Float.NaN)))
        assertNull(shape.opticalPath(Size(274f, 62f), LayoutDirection.Ltr, Density(0f)))
        val circle = shape.createOutline(Size(62f, 62f), LayoutDirection.Ltr, Density(1f))
        assertTrue(circle is Outline.Rounded)
        assertNull(shape.exactContoursFor(circle))
        assertNull(shape.opticalPath(Size(62f, 62f), LayoutDirection.Ltr, Density(1f)))
        assertEquals(LiquidGlassCornerRadii(31f, 31f, 31f, 31f), circularGlassCornerRadii(circle, 1f))
    }
}
