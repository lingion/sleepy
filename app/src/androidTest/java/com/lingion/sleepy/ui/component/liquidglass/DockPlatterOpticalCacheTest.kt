package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DockPlatterOpticalCacheTest {
    @Test fun stableGeometryReusesPathAndProfileWhileGlowUniformsStayLive() {
        listOf(Size(274f, 62f), Size(309f, 61.75f)).forEach { sizeDp ->
            listOf(1f, 2.975f).forEach { density ->
                listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
                    val shape = DockPlatterShape()
                    val sizePx = Size(sizeDp.width * density, sizeDp.height * density)
                    val fixture = fixture(shape, sizePx, density, direction)
                    val radial = checkNotNull(fixture.cache.matchingProfile(fixture.cache.path, fixture.exact))
                    assertSame(fixture.cache, shape.opticalProfile(sizePx, direction, Density(density)))
                    assertSame(fixture.cache.path, shape.opticalPath(sizePx, direction, Density(density)))
                    assertSame(radial, fixture.cache.matchingProfile(fixture.cache.path, fixture.exact))

                    val idle = uniforms(fixture, cached = fixture.cache)
                    assertUniformsEqual(uniforms(fixture), idle)
                    val glow = LiquidGlassGlow(Offset(76f, 28f), reach = 90.0, strength = 0.7, wash = 0.15)
                    val pressed = uniforms(fixture, cached = fixture.cache, glow = glow)
                    assertUniformsEqual(uniforms(fixture, glow = glow), pressed)
                    assertFalse(idle.getValue("uGlow").contentEquals(pressed.getValue("uGlow")))
                    assertArrayEquals(idle.getValue("uProfile"), pressed.getValue("uProfile"), 0f)
                    assertArrayEquals(idle.getValue("uProfileCenters"), pressed.getValue("uProfileCenters"), 0f)
                    assertSame(radial, fixture.cache.matchingProfile(fixture.cache.path, fixture.exact))
                }
            }
        }
    }

    @Test fun sizeDensityDirectionAndEvictionInvalidateTheRetainedOpticsTogether() {
        val shape = DockPlatterShape()
        val sizePx = Size(753.5f, 170.5f)
        val first = fixture(shape, sizePx, 2.75f)
        val firstRadial = checkNotNull(first.cache.matchingProfile(first.cache.path, first.exact))
        val resized = fixture(shape, Size(849.75f, 170.5f), 2.75f)
        val denser = fixture(shape, sizePx, 2f)
        val rtl = fixture(shape, sizePx, 2.75f, LayoutDirection.Rtl)
        listOf(resized, denser, rtl).forEach { changed ->
            assertNotSame(first.cache, changed.cache)
            assertNotSame(first.cache.path, changed.cache.path)
            assertNotSame(firstRadial, changed.cache.matchingProfile(changed.cache.path, changed.exact))
            val bounds = changed.cache.path.getBounds()
            assertEquals(changed.sizePx.width / changed.density, bounds.width, .001f)
            assertEquals(changed.sizePx.height / changed.density, bounds.height, .001f)
            assertEquals(bounds.center,
                checkNotNull(changed.cache.matchingProfile(changed.cache.path, changed.exact)).center)
        }
        assertSame(first.cache, shape.opticalProfile(sizePx, LayoutDirection.Ltr, Density(2.75f)))
        fixture(shape, Size(900f, 170.5f), 2.75f)
        val recreated = fixture(shape, sizePx, 2.75f)
        assertNotSame(first.cache, recreated.cache)
        assertNotSame(first.cache.path, recreated.cache.path)
        assertNotSame(firstRadial, recreated.cache.matchingProfile(recreated.cache.path, recreated.exact))
        assertUniformsEqual(uniforms(first), uniforms(recreated, cached = recreated.cache))
    }

    @Test fun cacheRequiresBothItsOwnedPathAndItsOwnExactDescriptor() {
        val first = fixture(DockPlatterShape(), Size(753.5f, 170.5f), 2.75f)
        val foreign = fixture(DockPlatterShape(), first.sizePx, first.density)
        val copy = Path().apply { addPath(first.cache.path) }
        assertNull(first.cache.matchingProfile(copy, first.exact))
        assertNull(first.cache.matchingProfile(first.cache.path, foreign.exact))
        assertNull(first.cache.matchingProfile(first.cache.path, null))
        assertUniformsEqual(uniforms(first, outline = copy),
            uniforms(first, outline = copy, cached = first.cache))
        assertUniformsEqual(uniforms(first, exactContours = listOf(foreign.exact)),
            uniforms(first, exactContours = listOf(foreign.exact), cached = first.cache))
    }

    @Test fun otherGeometryModesKeepTheirUncachedUniforms() {
        val first = fixture(DockPlatterShape(), Size(274f, 62f), 1f)
        val second = fixture(DockPlatterShape(), Size(196f, 54f), 1f)
        val guarded = LiquidGlassCachedOutlineProfile(first.cache.path) {
            error("Only a corresponding single exact contour may consult the dock cache")
        }
        val goo = LiquidGlassShader.Goo(listOf(Rect(0f, 0f, 60f, 40f)), listOf(0.5), 12.0)
        assertUniformsEqual(uniforms(first, exactContours = null),
            uniforms(first, exactContours = null, cached = guarded))
        assertUniformsEqual(uniforms(first, goo = goo),
            uniforms(first, goo = goo, cached = guarded))
        assertUniformsEqual(uniforms(first, outline = null),
            uniforms(first, outline = null, cached = guarded))
        val dual = first.cache.path to second.cache.path
        listOf(listOf(first.exact), listOf(first.exact, second.exact)).forEach { exact ->
            assertUniformsEqual(uniforms(first, dualContours = dual, exactContours = exact),
                uniforms(first, dualContours = dual, exactContours = exact, cached = guarded))
        }
    }

    private data class Fixture(
        val sizePx: Size,
        val density: Float,
        val cache: LiquidGlassCachedOutlineProfile,
        val exact: LiquidGlassExactContour,
    )

    private fun fixture(
        shape: DockPlatterShape,
        sizePx: Size,
        density: Float,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): Fixture {
        val actual = shape.createOutline(sizePx, direction, Density(density))
        return Fixture(sizePx, density,
            checkNotNull(shape.opticalProfile(sizePx, direction, Density(density))),
            checkNotNull(shape.exactContoursFor(actual)).single())
    }

    private fun uniforms(
        fixture: Fixture,
        cached: LiquidGlassCachedOutlineProfile? = null,
        glow: LiquidGlassGlow = LiquidGlassGlow.None,
        outline: Path? = fixture.cache.path,
        dualContours: Pair<Path, Path>? = null,
        goo: LiquidGlassShader.Goo? = null,
        exactContours: List<LiquidGlassExactContour>? = listOf(fixture.exact),
    ): Map<String, FloatArray> = LiquidGlassShader.build(
        size = Size(fixture.sizePx.width / fixture.density, fixture.sizePx.height / fixture.density),
        corners = LiquidGlassCornerRadii(31f, 31f, 31f, 31f),
        style = LiquidGlassStyle(Color.White),
        glow = glow,
        density = fixture.density,
        atlasSizePx = fixture.sizePx,
        sampling = LiquidGlassShader.sampling(Offset.Zero, fixture.density, Rect(Offset.Zero, fixture.sizePx)),
        outline = outline,
        dualContours = dualContours,
        goo = goo,
        exactContours = exactContours,
        cachedOutlineProfile = cached,
    )

    private fun assertUniformsEqual(expected: Map<String, FloatArray>, actual: Map<String, FloatArray>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, values) -> assertArrayEquals(name, values, actual.getValue(name), 0f) }
    }
}
