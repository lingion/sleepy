package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [LiquidGlassShader] 与 `assets/shaders/liquid_glass.agsl` 的契约测试。
 *
 * 契约有三层, 任何一层被破坏都会让运行时静默出错(而不是编译失败):
 *  1. **名字**: 装配出的每个 uniform 名都必须在 AGSL 里声明过, 反之亦然;
 *  2. **长度**: 每个数组 uniform 的 float 个数必须等于
 *     `SkRuntimeEffect::Uniform::sizeInBytes() / 4`
 *     (= `uniform_element_size(type) * count`, Skia `SkRuntimeEffect.cpp`);
 *     少一个字节 AOSP 就抛 `IllegalArgumentException`(AOSP `RuntimeEffectUtils.cpp`:
 *     `uniform.set<float>(values, count)` 校验 `sizeof(float)*count == sizeInBytes()`);
 *  3. **值序**: 装配顺序与 FLClash 的 `_look` / `_publish` 一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiquidGlassShaderContractTest {

    private val agsl: String by lazy {
        RuntimeEnvironment.getApplication().assets
            .open("shaders/liquid_glass.agsl").reader().readText()
    }

    /** AGSL 里声明的 uniform 名 → float 个数。 */
    private fun declaredUniforms(): Map<String, Int> {
        val sizes = mutableMapOf<String, Int>()
        val decl = Regex(
            """^uniform\s+(float2|float3|float4|float)\s+(\w+)\s*(\[\s*(\d+)\s*\])?\s*;""",
            RegexOption.MULTILINE,
        )
        decl.findAll(agsl).forEach { m ->
            val name = m.groupValues[2]
            val arrayLen = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
            val element = when (m.groupValues[1]) {
                "float" -> 1
                "float2" -> 2
                "float3" -> 3
                else -> 4
            }
            sizes[name] = element * (arrayLen ?: 1)
        }
        return sizes
    }

    private fun sampleStyle() = LiquidGlassStyle(
        tint = Color(0x33FFFFFF),
        contrast = 0.2,
        blur = 2.5,
        refraction = 60.0,
        bevel = 20.0,
        saturation = 1.5,
        brightness = 1.0,
        rim = 0.6,
        rimWidth = 1.3,
        hairline = 0.12,
    )

    private fun sampleSampling() = LiquidGlassShader.sampling(
        originPx = Offset(12f, 8f),
        texelsPerDp = 2f,
        clampPx = Rect(0f, 0f, 320f, 64f),
    )

    private fun build(
        outline: Path? = null,
        goo: LiquidGlassShader.Goo? = null,
        rim: LiquidGlassRim? = null,
        haze: Boolean = false,
    ): Map<String, FloatArray> = LiquidGlassShader.build(
        size = Size(120f, 40f),
        corners = LiquidGlassCornerRadii(12f, 12f, 12f, 12f),
        style = sampleStyle(),
        originPx = Offset(30f, 400f),
        density = 2.75f,
        stretch = 1f,
        atlasSizePx = Size(320f, 64f),
        sampling = sampleSampling(),
        hazeSampling = if (haze) sampleSampling() else null,
        outline = outline,
        goo = goo,
        rim = rim,
        veil = Color(0x11223344),
    )

    @Test
    fun everyPackedUniformIsDeclaredInTheShader() {
        val declared = declaredUniforms()
        val packed = build().keys
        assertEquals(
            "装配出的 uniform 名必须与 AGSL 声明一一对应",
            declared.keys - setOf("uTexture"),
            packed.toSet(),
        )
    }

    @Test
    fun everyPackedUniformHasTheShaderDeclaredFloatCount() {
        val declared = declaredUniforms()
        build().forEach { (name, values) ->
            assertEquals(
                "$name 的 float 个数与 AGSL 声明不符",
                declared.getValue(name),
                values.size,
            )
        }
    }

    @Test
    fun packedLengthsMatchTheSkiaUniformTable() {
        // Skia: `sizeInBytes() = uniform_element_size(type) * count`, AOSP 要求
        // `sizeof(float) * count == sizeInBytes()` ⇒ float 个数 = 元素宽 × 数组长。
        val expected = mapOf(
            "uSize" to 2, "uFrame" to 4, "uSample" to 4, "uClamp" to 4, "uTexels" to 2,
            "uHazeSample" to 4, "uHazeClamp" to 4, "uHaze" to 1, "uVeil" to 4,
            "uShape" to 4, "uCorners" to 4, "uCurves" to 48, "uOptics" to 3, "uTint" to 4,
            "uLight" to 3, "uGlow" to 4, "uGlowSpread" to 2, "uEdge" to 3, "uRim" to 4,
            "uOutlined" to 1, "uProfile" to 64, "uProfile2" to 64, "uProfileCenters" to 4,
            "uShineTurns" to 8, "uShineLit" to 8,
            "uShineEnds" to 12, "uShineShares" to 12, "uPills" to 24, "uGoo" to 8,
            "uPillCurves" to 24, "uPillCenters" to 12,
        )
        assertEquals(expected, LiquidGlassShader.SIZES)
        assertEquals(expected, declaredUniforms())
    }

    @Test
    fun frameFoldsThePaneOriginAndTheDevicePixelScale() {
        val uFrame = build().getValue("uFrame")
        // uFrame = (−originPx/density, −originPx/density, 1/density, 1/density)
        assertEquals(-30f / 2.75f, uFrame[0], 1e-6f)
        assertEquals(-400f / 2.75f, uFrame[1], 1e-6f)
        assertEquals(1f / 2.75f, uFrame[2], 1e-6f)
        assertEquals(1f / 2.75f, uFrame[3], 1e-6f)
    }

    @Test
    fun texelsCarryTheDevicePixelStepAndTheHeldFrost() {
        val uTexels = build().getValue("uTexels")
        assertEquals(1f / 2.75f, uTexels[0], 1e-6f)
        assertEquals(0f, uTexels[1], 1e-6f)
    }

    @Test
    fun lookSectionFollowsTheDartOrder() {
        val values = build()
        // uShape = (w, h, bevel, blur)
        assertEquals(120f, values.getValue("uShape")[0], 1e-6f)
        assertEquals(40f, values.getValue("uShape")[1], 1e-6f)
        assertEquals(20f, values.getValue("uShape")[2], 1e-6f)
        assertEquals(2.5f, values.getValue("uShape")[3], 1e-6f)
        // uOptics = (refraction, saturation, brightness)
        assertEquals(60f, values.getValue("uOptics")[0], 1e-6f)
        assertEquals(1.5f, values.getValue("uOptics")[1], 1e-6f)
        assertEquals(1f, values.getValue("uOptics")[2], 1e-6f)
        // uLight = (rim, rimWidth, endShade)
        assertEquals(0.6f, values.getValue("uLight")[0], 1e-6f)
        assertEquals(1.3f, values.getValue("uLight")[1], 1e-6f)
        // uEdge = (hairline, dispersion, rimBlur)
        assertEquals(0.12f, values.getValue("uEdge")[0], 1e-6f)
        // uGlow 第 4 位是 style.contrast, 不是 glow 数据。
        assertEquals(0.2f, values.getValue("uGlow")[3], 1e-6f)
        // uRim 第 4 位是 hairlineSide(此处未单列 ⇒ 取 hairline)。
        assertEquals(0.12f, values.getValue("uRim")[3], 1e-6f)
        // uTint 是直 alpha。
        assertEquals(0x33 / 255f, values.getValue("uTint")[3], 1e-6f)
        // uVeil 是直 alpha。
        assertEquals(0x11 / 255f, values.getValue("uVeil")[3], 1e-6f)
    }

    @Test
    fun outlineSelectsTheProfilePath() {
        assertEquals(0f, build().getValue("uOutlined")[0], 1e-6f)
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, 120f, 40f), CornerRadius(8f, 8f)))
        }
        assertEquals(1f, build(outline = path).getValue("uOutlined")[0], 1e-6f)
        val goo = LiquidGlassShader.Goo(listOf(Rect(0f, 0f, 60f, 40f)), listOf(0.5), 12.0)
        assertEquals(2f, build(outline = path, goo = goo).getValue("uOutlined")[0], 1e-6f)
    }

    @Test
    fun absentHazeGoesToTheDartNoHazeSentinel() {
        val values = build()
        // Dart `_noHaze = [0, 0, 1, 1, 0, 0, 0, 0]`
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), values.getValue("uHazeSample"), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), values.getValue("uHazeClamp"), 1e-6f)
        assertEquals(0f, values.getValue("uHaze")[0], 1e-6f)
    }

    @Test
    fun presentHazeCarriesTheStyleHazeAndItsOwnSampling() {
        val values = build(haze = true)
        assertArrayEquals(
            floatArrayOf(-12f / 2f, -8f / 2f, 1f / 2f, 1f / 2f),
            values.getValue("uHazeSample"),
            1e-6f,
        )
        assertArrayEquals(
            floatArrayOf(0.5f, 0.5f, 319.5f, 63.5f),
            values.getValue("uHazeClamp"),
            1e-6f,
        )
        assertEquals(sampleStyle().haze.toFloat(), values.getValue("uHaze")[0], 1e-6f)
    }

    @Test
    fun centeredTrackSamplingHasSymmetricPixelsAroundLensCenter() {
        val trackOriginPx = Offset(4f, 4f)
        val lensCenterWithinTrackDp = Offset(31.5f, 14f)
        val s = LiquidGlassShader.sampling(
            trackOriginPx,
            texelsPerDp = 3f,
            clampPx = Rect(0f, 0f, 189f, 84f),
        )
        // sampleGlass uses (point - uSample.xy) / uSample.zw. With a glass pane centered over the track,
        // equal offsets above/below its center must map to equidistant source pixels.
        fun sourcePx(pointDp: Offset) = Offset((pointDp.x - s[0]) / s[2], (pointDp.y - s[1]) / s[3])
        val center = sourcePx(Offset(31.5f, 14f))
        val above = sourcePx(Offset(31.5f, 6f))
        val below = sourcePx(Offset(31.5f, 22f))
        assertEquals("lens center must map to the absolute track center x", 98.5f, center.x, 0.001f)
        assertEquals("lens center must map to the absolute track center y", 46f, center.y, 0.001f)
        assertEquals("top/bottom track samples must be symmetric about the thumb center", center.y - above.y, below.y - center.y, 0.001f)

    }
    @Test
    fun samplingMapsPaneDpOntoTexturePixels() {
        // sampleGlass(pointDp) → 纹理 px = pointDp * texelsPerDp + originPx
        val s = LiquidGlassShader.sampling(Offset(12f, 8f), texelsPerDp = 2f, clampPx = Rect(0f, 0f, 320f, 64f))
        // uSample.xy = −originPx(折成 dp); uSample.zw = dp 每纹理 px
        assertEquals(-6f, s[0], 1e-6f)
        assertEquals(-4f, s[1], 1e-6f)
        assertEquals(0.5f, s[2], 1e-6f)
        assertEquals(0.5f, s[3], 1e-6f)
        assertEquals(0.5f, s[4], 1e-6f)
        assertEquals(0.5f, s[5], 1e-6f)
        assertEquals(319.5f, s[6], 1e-6f)
        assertEquals(63.5f, s[7], 1e-6f)
    }


    @Test
    fun rimOnlyContributesItsShine() {
        val without = build()
        val with = build(rim = LiquidGlassRim(side = 0.75, corner = 0.1, flat = 0.03, shine = 0.22))
        assertEquals(0f, without.getValue("uShineLit").sum(), 1e-6f)
        assertTrue("有 rim 时高光必须非零", with.getValue("uShineLit").sum() > 0f)
        // side/corner/flat 是 CPU 画的渐变发丝线, 不进着色器。
        assertEquals(without.getValue("uRim").toList(), with.getValue("uRim").toList())
    }
}
