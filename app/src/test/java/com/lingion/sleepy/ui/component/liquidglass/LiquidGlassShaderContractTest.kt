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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.abs

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
        style: LiquidGlassStyle = sampleStyle(),
        frostRadiusSampling: Boolean = false,
    ): Map<String, FloatArray> = LiquidGlassShader.build(
        size = Size(120f, 40f),
        corners = LiquidGlassCornerRadii(12f, 12f, 12f, 12f),
        style = style,
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
        frostRadiusSampling = frostRadiusSampling,
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
            "uSize" to 2, "uFrame" to 4, "uSample" to 4, "uClamp" to 4, "uTexels" to 2, "uFrostRadiusMode" to 1,
            "uHazeSample" to 4, "uHazeClamp" to 4, "uHaze" to 1, "uVeil" to 4,
            "uShape" to 4, "uCorners" to 4, "uCircularCorners" to 1, "uCurves" to 48, "uOptics" to 3,
            "uRefractionLimit" to 1, "uRefractionProfilePower" to 1, "uLateralRefractionGain" to 1, "uTint" to 4,
            "uDockTone" to 4, "uDockToneBright" to 3, "uDockToneDark" to 3,
            "uDockDarkLowTone" to 4, "uDockDarkLowToneBlend" to 1,
            "uDockBrightRim" to 4, "uDockBrightRimChroma" to 1, "uDockBrightRimAngular" to 2,
            "uDockBrightRimCosine" to 1, "uDockBrightRimCubicShoulder" to 1,
            "uDockBrightRimLogit" to 1,
            "uDockBrightRimHeadroom" to 1,
            "uDockDarkRim" to 4, "uDockDarkRimChroma" to 1,
            "uDockDarkRimAngular" to 2, "uDockDarkRimCosine" to 1, "uDockDarkRimCubicShoulder" to 1,
            "uDockDarkRimLogit" to 1,
            "uDockDarkRimHeadroom" to 1,
            "uDockBaseRimCubicShoulder" to 1,
            "uLight" to 3, "uGlow" to 4, "uGlowSpread" to 2, "uEdge" to 3, "uRim" to 4,
            "uOutlined" to 1, "uProfile" to 64, "uProfile2" to 64, "uProfileCenters" to 4,
            "uExact1" to 144, "uExact2" to 144,
            "uShineTurns" to 8, "uShineLit" to 8, "uShineFloorLit" to 8,
            "uShineEnds" to 12, "uShineShares" to 12, "uPills" to 24, "uGoo" to 8,
            "uPillCurves" to 24, "uPillCenters" to 12,
        )
        assertEquals(expected, LiquidGlassShader.SIZES)
        assertEquals(expected, declaredUniforms())
    }

    @Test
    fun dockResponseHasExplicitModeAndDoesNotRebindSamplingOrOtherMaterials() {
        val original = build(haze = true)
        val keys = setOf("uDockTone", "uDockToneBright", "uDockToneDark")
        keys.forEach { name -> assertTrue(original.getValue(name).all { it == 0f }) }
        val tone = DockToneResponse(brightWeight = 0.0, darkChromaGain = 1.1475795432456875,
            adaptiveThresholds = DockToneThresholds(.12, .24))
        val candidate = build(haze = true, style = sampleStyle().copy(dockToneResponse = tone))
        assertArrayEquals(floatArrayOf(1f, 0f, .12f, .24f), candidate.getValue("uDockTone"), 0f)
        assertArrayEquals(floatArrayOf(.55f, .45f, 1.0575f), candidate.getValue("uDockToneBright"), 0f)
        assertArrayEquals(floatArrayOf((14.0392 / 255).toFloat(), .821569f, tone.darkChromaGain.toFloat()),
            candidate.getValue("uDockToneDark"), 0f)
        original.filterKeys { it !in keys }.forEach { (name, values) ->
            assertArrayEquals("$name must remain independent of the Dock response", values, candidate.getValue(name), 0f)
        }
    }

    @Test
    fun nullableDockResponseKeepsLegacyEndpointsAndContinuousBlend() {
        val legacy = sampleStyle()
        val tone = legacy.copy(dockToneResponse = DockToneResponse(1.0, 1.1475795432456875))
        for ((endpoints, t, expected) in listOf(
            Triple(legacy to tone, 0.0, legacy), Triple(tone to legacy, 1.0, legacy),
            Triple(legacy to tone, 1.0, tone),
        )) {
            val actual = LiquidGlassStyle.lerp(endpoints.first, endpoints.second, t)
            assertEquals(expected.dockToneResponse, actual.dockToneResponse)
            build(style = expected).forEach { (name, values) ->
                assertArrayEquals("$name preserves nullable material endpoints", values, build(style = actual).getValue(name), 0f)
            }
        }
        for (t in listOf(.001, .25, .5, .999)) {
            val forward = LiquidGlassStyle.lerp(legacy, tone, t)
            val backward = LiquidGlassStyle.lerp(tone, legacy, t)
            assertEquals(t, forward.dockToneResponse!!.blend, 1e-12)
            assertEquals(1.0 - t, backward.dockToneResponse!!.blend, 1e-12)
            assertEquals(tone.dockToneResponse!!.brightGain, forward.dockToneResponse!!.brightGain, 0.0)
            assertEquals(t.toFloat(), build(style = forward).getValue("uDockTone")[0], 0f)
        }
    }

    @Test
    fun finiteBrightRimIsOptionalSignedAndIndependentOfSamplingAndBody() {
        val response = DockToneResponse(1.0, 1.1475795432456875)
        val originalStyle = sampleStyle().copy(dockToneResponse = response)
        val rim = DockBrightRimResponse(1.30, .33764193046657226, -.31647464547587895, .2804228205880503)
        val candidateStyle = originalStyle.copy(dockToneResponse = response.copy(brightRim = rim))
        val original = build(haze = true, style = originalStyle)
        val candidate = build(haze = true, style = candidateStyle)
        val keys = setOf("uDockBrightRim", "uDockBrightRimChroma")
        keys.forEach { assertTrue(original.getValue(it).all { value -> value == 0f }) }
        assertArrayEquals(floatArrayOf(1f, 1.3f, rim.intercept.toFloat(), rim.lumaGain.toFloat()),
            candidate.getValue("uDockBrightRim"), 0f)
        assertEquals(rim.chromaGain.toFloat(), candidate.getValue("uDockBrightRimChroma")[0], 0f)
        original.filterKeys { it !in keys }.forEach { (name, values) ->
            assertArrayEquals(name, values, candidate.getValue(name), 0f)
        }
        for (t in listOf(0.0, .001, .5, .999, 1.0)) {
            val style = LiquidGlassStyle.lerp(originalStyle, candidateStyle, t)
            assertEquals(t.toFloat(), build(style = style).getValue("uDockBrightRim")[0], 0f)
        }
        assertEquals(0f, build(style = candidateStyle.scale(0.0)).getValue("uDockBrightRim")[0], 0f)
    }

    @Test
    fun finiteBrightRimRejectsNonfiniteAndDegenerateInputs() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 8.01)) {
            assertTrue(runCatching { DockBrightRimResponse(bad, .1, -.1, .1) }.isFailure)
        }
        for (bad in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -8.01, 8.01)) {
            assertTrue(runCatching { DockBrightRimResponse(1.3, bad, -.1, .1) }.isFailure)
        }
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -2.01, 2.01)) {
            assertTrue(runCatching { DockBrightRimResponse(1.3, .1, -.1, .1, normalCrossGain = bad) }.isFailure)
        }
        for (bad in listOf(Double.NaN, -.01, 1.01)) {
            assertTrue(runCatching { DockBrightRimResponse(1.3, .1, -.1, .1, angularBlend = bad) }.isFailure)
        }
    }

    @Test
    fun dockAngularCandidateKeepsBodySamplingAndLegacyEndpointsIndependent() {
        val rim = DockBrightRimResponse(1.3, .33764193046657226, -.31647464547587895, .2804228205880503)
        val response = DockToneResponse(1.0, 1.1475795432456875, brightRim = rim)
        val legacy = sampleStyle().copy(dockToneResponse = response)
        val angular = legacy.copy(dockToneResponse = response.copy(brightRim = rim.copy(angularBlend = 1.0, normalCrossGain = 1.0)))
        val before = build(haze = true, style = legacy)
        val after = build(haze = true, style = angular)
        assertArrayEquals(floatArrayOf(0f, 0f), before.getValue("uDockBrightRimAngular"), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f), after.getValue("uDockBrightRimAngular"), 0f)
        before.filterKeys { it != "uDockBrightRimAngular" }.forEach { (key, value) ->
            assertArrayEquals(key, value, after.getValue(key), 0f)
        }
        for (t in listOf(0.0, .001, .5, .999, 1.0)) {
            val mixed = build(style = LiquidGlassStyle.lerp(legacy, angular, t))
            assertArrayEquals(floatArrayOf(t.toFloat(), t.toFloat()), mixed.getValue("uDockBrightRimAngular"), 0f)
        }
    }

    @Test
    fun dockCosineCandidateKeepsBodySamplingAndLegacyEndpointsIndependent() {
        val rim = DockBrightRimResponse(1.3, .33764193046657226, -.31647464547587895, .2804228205880503,
            angularBlend = 1.0, normalCrossGain = 1.0)
        val response = DockToneResponse(1.0, 1.1475795432456875, brightRim = rim)
        val linear = sampleStyle().copy(dockToneResponse = response)
        val cosine = linear.copy(dockToneResponse = response.copy(brightRim = rim.copy(cosineBlend = 1.0)))
        val before = build(haze = true, style = linear)
        val after = build(haze = true, style = cosine)
        assertArrayEquals(floatArrayOf(0f), before.getValue("uDockBrightRimCosine"), 0f)
        assertArrayEquals(floatArrayOf(1f), after.getValue("uDockBrightRimCosine"), 0f)
        before.filterKeys { it != "uDockBrightRimCosine" }.forEach { (key, value) ->
            assertArrayEquals(key, value, after.getValue(key), 0f)
        }
        for (t in listOf(0.0, .001, .5, .999, 1.0)) {
            val style = LiquidGlassStyle.lerp(linear, cosine, t)
            assertArrayEquals(floatArrayOf(t.toFloat()), build(style = style).getValue("uDockBrightRimCosine"), 0f)
        }
        assertEquals(rim, LiquidGlassStyle.lerp(linear, cosine, 0.0).dockToneResponse!!.brightRim)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.01, 1.01)) {
            assertTrue(runCatching { rim.copy(cosineBlend = bad) }.isFailure)
        }
    }

    @Test
    fun cubicShoulderBindsBothRimsWithoutChangingExistingCosineOrOtherUniforms() {
        val rim = DockBrightRimResponse(1.2, .1, -.1, .1, cosineBlend = .5)
        val response = DockToneResponse(.5, 1.0, brightRim = rim, darkRim = rim)
        val style = sampleStyle().copy(dockToneResponse = response)
        val before = build(haze = true, style = style)
        val keys = setOf("uDockBrightRimCubicShoulder", "uDockDarkRimCubicShoulder")
        keys.forEach { assertEquals(0f, before.getValue(it)[0], 0f) }
        val after = build(haze = true, style = style.copy(dockToneResponse = response.copy(
            brightRim = rim.copy(cubicShoulderBlend = 1.0),
            darkRim = rim.copy(cubicShoulderBlend = .25))))
        assertEquals(1f, after.getValue("uDockBrightRimCubicShoulder")[0], 0f)
        assertEquals(.25f, after.getValue("uDockDarkRimCubicShoulder")[0], 0f)
        assertEquals(1.2f, after.getValue("uDockBrightRim")[1], 0f)
        assertEquals(1.2f, after.getValue("uDockDarkRim")[1], 0f)
        before.filterKeys { it !in keys }.forEach { (name, value) ->
            assertArrayEquals(name, value, after.getValue(name), 0f)
        }
        val shared = agsl.substringAfter("float3 dockSignedRim(").substringBefore("half4 main(")
        val brightOnly = agsl.substringAfter("} else if (dockBrightRimWeight > 0.0) {")
            .substringBefore("// iOS 27 outlines")
        assertTrue(shared.contains("if (cubicShoulderBlend > 0.0) {"))
        assertTrue(brightOnly.contains("if (uDockBrightRimCubicShoulder > 0.0) {"))
        for ((source, width) in listOf(shared to "rim.y", brightOnly to "uDockBrightRim.y")) {
            assertTrue(source.contains("max(edge, 0.0) / max($width, 1e-3)"))
            assertEquals(1, Regex("dockCubicShoulder\\(normalizedDepth\\)").findAll(source).count())
        }
        assertTrue(agsl.contains("uDockBrightRimAngular, uDockBrightRimCosine, uDockBrightRimCubicShoulder, uDockBrightRimLogit);"))
        assertTrue(agsl.contains("uDockDarkRimAngular, uDockDarkRimCosine, uDockDarkRimCubicShoulder, uDockDarkRimLogit);"))
    }

    @Test
    fun headroomCompositionBindsOnlyItsTwoOptInUniforms() {
        val rim = DockBrightRimResponse(1.2, .11130893001790144, .24841754457691995, 1.63092875493563,
            cosineBlend = 1.0)
        val tone = DockToneResponse(.5, 1.0, brightRim = rim, darkRim = rim)
        val style = sampleStyle().copy(dockToneResponse = tone)
        val before = build(haze = true, style = style)
        val keys = setOf("uDockBrightRimHeadroom", "uDockDarkRimHeadroom")
        for (key in keys) {
            assertArrayEquals(floatArrayOf(0f), before.getValue(key), 0f)
            assertArrayEquals(floatArrayOf(0f), build().getValue(key), 0f)
        }
        val after = build(haze = true, style = style.copy(dockToneResponse = tone.copy(
            brightRim = rim.copy(headroomBlend = .75), darkRim = rim.copy(headroomBlend = 1.0))))
        assertArrayEquals(floatArrayOf(.75f), after.getValue("uDockBrightRimHeadroom"), 0f)
        assertArrayEquals(floatArrayOf(1f), after.getValue("uDockDarkRimHeadroom"), 0f)
        before.filterKeys { it !in keys }.forEach { (name, value) ->
            assertArrayEquals(name, value, after.getValue(name), 0f)
        }
    }

    @Test
    fun shippedHeadroomResponseUnlocksEndpointsAndApproximatesStableExpm1() {
        val helper = agsl.substringAfter("float dockOneMinusExp(float x) {").substringBefore("\n}")
        val small = Regex("if \\(x < 0.01\\) return ([^;]+);").find(helper)!!.groupValues[1]
        val large = Regex("return ([^;]+);").findAll(helper).last().groupValues[1]
        fun oneMinusExp(x: Double) = ScalarExpression(if (x < .01) small else large,
            mapOf("x" to x)).evaluate()
        val channel = agsl.substringAfter("float dockHeadroomChannel(float body, float shift) {")
            .substringBefore("\n}")
        val negative = Regex("if \\(shift <= 0.0\\) return ([^;]+);").find(channel)!!.groupValues[1]
        val room = Regex("float room = ([^;]+);").find(channel)!!.groupValues[1]
        val positive = Regex("return ([^;]+);").findAll(channel).last().groupValues[1]
        assertTrue(channel.indexOf("if (body >= 1.0) return 1.0;") < channel.indexOf("shift / room"))
        fun transfer(body: Double, shift: Double): Double {
            val values = mutableMapOf("body" to body, "shift" to shift)
            if (shift <= 0.0) return ScalarExpression(negative, values).evaluate()
            if (body >= 1.0) return 1.0
            values["room"] = ScalarExpression(room, values).evaluate()
            val expression = positive.replace("dockOneMinusExp(shift / room)", "response")
            values["response"] = oneMinusExp(shift / values.getValue("room"))
            return ScalarExpression(expression, values).evaluate()
        }
        for (x in listOf(1e-15, 1e-8, .001, .009999999, .01, .010000001, .1, 1.0, 32.0, 1000.0)) {
            assertEquals(-Math.expm1(-x), oneMinusExp(x), 9e-13)
        }
        val shifts = listOf(-1000.0, -1.0, -.1, -1e-8, 0.0, 1e-8, .1, 1.0, 1000.0)
        for (body in listOf(0.0, .001, .5, .999, 1.0)) {
            var prior = -1.0
            for (shift in shifts) {
                val actual = transfer(body, shift)
                val expected = if (shift <= 0.0) maxOf(body + shift, 0.0)
                    else if (body == 1.0) 1.0 else body - (1.0 - body) * Math.expm1(-shift / (1.0 - body))
                assertEquals(expected, actual, 9e-13)
                assertTrue(actual.isFinite() && actual in 0.0..1.0 && actual + 1e-12 >= prior)
                prior = actual
            }
        }
        assertEquals(.09516258196404043, transfer(0.0, .1), 1e-15)
        assertEquals(.9, transfer(1.0, -.1), 0.0)
        assertEquals(.590634623461009, transfer(.5, .1), 1e-15)
    }

    @Test
    fun headroomWrapperPreservesLowCoverageAndBlendsPremultipliedResults() {
        val wrapper = agsl.substringAfter("float3 dockHeadroomRim(").substringBefore("float3 dockSignedRim(")
        assertTrue(wrapper.contains("if (headroomBlend <= 0.0) return previousRim;"))
        assertTrue(wrapper.indexOf("if (coverage > 0.0) {") < wrapper.indexOf("unlitGlass / coverage"))
        assertTrue(wrapper.indexOf("if (profile != 0.0) {") < wrapper.indexOf("unlitGlass / coverage"))
        assertTrue(wrapper.contains("float3 shift = profile * amplitude;"))
        assertTrue(wrapper.contains("dockHeadroomChannel(body.b, shift.b)) * coverage;"))
        assertTrue(!wrapper.contains("max(coverage,"))
        assertTrue(wrapper.contains("if (headroomBlend >= 1.0) return headroomRim;"))
        val initial = Regex("float3 headroomRim = ([^;]+);").find(wrapper)!!.groupValues[1]
        val blend = Regex("return (mix\\(previousRim, headroomRim, headroomBlend\\));")
            .find(wrapper)!!.groupValues[1]
        for (coverage in listOf(0.0, 1e-8, 5e-5, .5, 1.0)) {
            val original = .5 * coverage
            val previous = original / maxOf(coverage, 1e-4) * coverage
            val values = mutableMapOf("unlitGlass" to original, "previousRim" to previous)
            values["headroomRim"] = ScalarExpression(initial, values).evaluate()
            for (amount in listOf(0.0, 1e-9, .5, 1.0)) {
                values["headroomBlend"] = amount
                val actual = ScalarExpression(blend, values).evaluate()
                assertEquals(previous * (1.0 - amount) + original * amount, actual, 0.0)
                if (coverage == 5e-5 && amount == .5) assertEquals(1.875e-5, actual, 1e-20)
            }
        }
        val old = agsl.substringAfter("float3 dockSignedRim(").substringBefore("half4 main(")
        assertTrue(!old.contains("headroom", ignoreCase = true))
        val brightOnly = agsl.substringAfter("} else if (dockBrightRimWeight > 0.0) {")
            .substringBefore("// iOS 27 outlines")
        assertTrue(!brightOnly.contains("headroom", ignoreCase = true))
    }

    @Test
    fun logitCompositionBindsOnlyItsTwoOptInUniforms() {
        val rim = DockBrightRimResponse(1.2, .6425691964508747, .4944993509984069, 7.021637119286862,
            cosineBlend = 1.0)
        val tone = DockToneResponse(.5, 1.0, brightRim = rim, darkRim = rim)
        val style = sampleStyle().copy(dockToneResponse = tone)
        val before = build(haze = true, style = style)
        val keys = setOf("uDockBrightRimLogit", "uDockDarkRimLogit")
        for (key in keys) {
            assertArrayEquals(floatArrayOf(0f), before.getValue(key), 0f)
            assertArrayEquals(floatArrayOf(0f), build().getValue(key), 0f)
        }
        val after = build(haze = true, style = style.copy(dockToneResponse = tone.copy(
            brightRim = rim.copy(logitBlend = .75), darkRim = rim.copy(logitBlend = 1.0))))
        assertArrayEquals(floatArrayOf(.75f), after.getValue("uDockBrightRimLogit"), 0f)
        assertArrayEquals(floatArrayOf(1f), after.getValue("uDockDarkRimLogit"), 0f)
        before.filterKeys { it !in keys }.forEach { (name, value) ->
            assertArrayEquals(name, value, after.getValue(name), 0f)
        }
    }

    @Test
    fun logitCompositionGuardsIdentityBeforeDivisionAndRetainsTheLegacyReturn() {
        val shared = agsl.substringAfter("float3 dockSignedRim(").substringBefore("half4 main(")
        val guard = shared.indexOf("if (logitBlend > 0.0) {")
        val legacy = "return clamp(unlitGlass / max(coverage, 1e-4)\n      + materialFacing * finiteBand * amplitude, 0.0, 1.0) * coverage;"
        assertTrue(guard >= 0 && shared.indexOf(legacy) > guard)
        assertTrue(!shared.substring(0, guard).contains("dockLogitChannel("))
        val optedIn = shared.substringAfter("if (logitBlend > 0.0) {").substringBefore(legacy)
        val identity = "if (coverage <= 0.0) return unlitGlass;"
        assertTrue(optedIn.contains("float profile = materialFacing * finiteBand;"))
        val identityOffset = optedIn.indexOf(identity)
        assertTrue(identityOffset >= 0 && identityOffset < optedIn.indexOf("unlitGlass / coverage"))
        assertTrue(optedIn.contains("float3 logitRim = unlitGlass;"))
        val nonzeroProfileOffset = optedIn.indexOf("if (profile != 0.0) {")
        assertTrue(nonzeroProfileOffset >= 0 && nonzeroProfileOffset < optedIn.indexOf("unlitGlass / coverage"))
        assertTrue(optedIn.contains("float3 shift = profile * amplitude;"))
        assertTrue(optedIn.contains("if (logitBlend >= 1.0) return logitRim;"))
        assertTrue(optedIn.contains("return mix(additiveRim, logitRim, logitBlend);"))
        val brightOnly = agsl.substringAfter("} else if (dockBrightRimWeight > 0.0) {")
            .substringBefore("// iOS 27 outlines")
        assertTrue(!brightOnly.contains("Logit") && !brightOnly.contains("logit"))
    }

    @Test
    fun zeroProfileCompositionStaysContinuousAtTinyCoverageUsingTheShippedExpressions() {
        val shared = agsl.substringAfter("float3 dockSignedRim(").substringBefore("half4 main(")
        val optedIn = shared.substringAfter("if (logitBlend > 0.0) {")
        val initialTarget = Regex("float3 logitRim = ([^;]+);").find(optedIn)!!.groupValues[1]
        val additive = Regex("float3 additiveRim = ([^;]+);").find(optedIn)!!.groupValues[1]
        val blendExpression = Regex("return (mix\\(additiveRim, logitRim, logitBlend\\));")
            .find(optedIn)!!.groupValues[1]
        for (coverage in listOf(1e-8, 5e-5, 1e-4)) {
            val original = .5 * coverage
            val values = mutableMapOf("unlitGlass" to original, "coverage" to coverage,
                "materialFacing" to 1.0, "finiteBand" to 0.0, "amplitude" to .75)
            values["logitRim"] = ScalarExpression(initialTarget, values).evaluate()
            values["additiveRim"] = ScalarExpression(additive, values).evaluate()
            val legacy = values.getValue("additiveRim")
            if (coverage == 5e-5) assertEquals(1.25e-5, legacy, 0.0)
            assertEquals(original, values.getValue("logitRim"), 0.0)
            for (blend in listOf(0.0, 1e-9, .25, .5, 1.0)) {
                values["logitBlend"] = blend
                val actual = ScalarExpression(blendExpression, values).evaluate()
                assertEquals(legacy + blend * (original - legacy), actual, 1e-20)
                if (blend == 0.0) assertEquals(legacy, actual, 0.0)
                if (blend == 1.0) assertEquals(original, actual, 0.0)
            }
            values["logitBlend"] = 1e-9
            val nearZero = ScalarExpression(blendExpression, values).evaluate()
            assertTrue(abs(nearZero - legacy) <= abs(original - legacy) * 1.001e-9 + 1e-20)
        }
    }

    @Test
    fun shippedLogitChannelKeepsExactEndpointsAndIsFiniteMonotoneWithoutEpsilon() {
        val helper = agsl.substringAfter("float dockLogitChannel(float body, float shift) {")
            .substringBefore("\n}")
        assertTrue(helper.contains("if (body <= 0.0 || body >= 1.0 || shift == 0.0) return body;"))
        assertTrue(!helper.contains("clamp(") && !helper.contains("1e-"))
        val qExpression = Regex("float q = ([^;]+);").find(helper)!!.groupValues[1]
        val positive = Regex("if \\(shift >= 0.0\\) return ([^;]+);").find(helper)!!.groupValues[1]
        val negative = Regex("return ([^;]+);").findAll(helper).last().groupValues[1]
        fun transfer(body: Double, shift: Double): Double {
            if (body <= 0.0 || body >= 1.0 || shift == 0.0) return body
            val values = mutableMapOf("body" to body, "shift" to shift)
            values["q"] = ScalarExpression(qExpression, values).evaluate()
            return ScalarExpression(if (shift >= 0.0) positive else negative, values).evaluate()
        }
        val shifts = listOf(-1000.0, -32.0, -8.0, -1.0, 0.0, 1.0, 8.0, 32.0, 1000.0)
        val bodies = listOf(0.0, .001, .2, .5, .8, .999, 1.0)
        for (shift in shifts) {
            assertEquals(0.0, transfer(0.0, shift), 0.0)
            assertEquals(1.0, transfer(1.0, shift), 0.0)
            var prior = -1.0
            for (body in bodies) {
                val actual = transfer(body, shift)
                assertTrue(actual.isFinite() && actual in 0.0..1.0 && actual >= prior)
                prior = actual
            }
        }
        for (body in bodies) {
            assertEquals(body, transfer(body, 0.0), 0.0)
            var prior = -1.0
            for (shift in shifts) {
                val actual = transfer(body, shift)
                assertTrue(actual >= prior)
                prior = actual
                if (body > 0.0 && body < 1.0 && abs(shift) <= 32.0) {
                    val reference = 1.0 / (1.0 + exp(-(ln(body / (1.0 - body)) + shift)))
                    assertEquals(reference, actual, 3e-15)
                }
            }
        }
    }

    @Test
    fun dockBaseShoulderBindsWithoutToneOrChangingAnyOtherUniform() {
        val legacy = sampleStyle()
        val original = build(haze = true, style = legacy)
        assertEquals(null, legacy.dockToneResponse)
        assertArrayEquals(floatArrayOf(0f), original.getValue("uDockBaseRimCubicShoulder"), 0f)
        for (blend in listOf(0.0, .001, .25, .5, .999, 1.0)) {
            val candidate = build(haze = true, style = legacy.copy(dockBaseRimCubicShoulderBlend = blend))
            assertArrayEquals(floatArrayOf(blend.toFloat()), candidate.getValue("uDockBaseRimCubicShoulder"), 0f)
            original.filterKeys { it != "uDockBaseRimCubicShoulder" }.forEach { (name, values) ->
                assertArrayEquals(name, values, candidate.getValue(name), 0f)
            }
        }
    }

    @Test
    fun dockBaseShoulderBindingBoundsOnlyTheNewProfileWithoutRejectingStyles() {
        for ((requested, expected) in listOf(
            -1.0 to 0f, 0.0 to 0f, .5 to .5f, 1.0 to 1f, 2.0 to 1f,
            Double.MAX_VALUE to 1f, Double.NaN to 0f,
            Double.POSITIVE_INFINITY to 0f, Double.NEGATIVE_INFINITY to 0f,
        )) {
            val actual = build(style = sampleStyle().copy(dockBaseRimCubicShoulderBlend = requested))
                .getValue("uDockBaseRimCubicShoulder").single()
            assertEquals(expected, actual, 0f)
            assertTrue(actual.isFinite() && actual in 0f..1f)
        }
    }

    @Test
    fun dockBaseShoulderKeepsLegacyShineBehindAnIndependentZeroGuard() {
        val whiteRim = agsl.substringAfter("float band =").substringBefore("float3 unlitGlass = glass;")
        val legacy = "float shine = min(uLight.x * facing * mix(band, tail, uRim.x), 1.0);"
        val guard = "if (uDockBaseRimCubicShoulder > 0.0) {"
        assertTrue(whiteRim.indexOf(legacy) >= 0)
        assertTrue(whiteRim.indexOf(guard) > whiteRim.indexOf(legacy))
        val shoulderOverride = whiteRim.substringAfter(guard).substringBefore("}")
            .replace(Regex("\\s+"), " ").trim()
        assertTrue(shoulderOverride.contains("float shoulderShine = min(uLight.x * facing * dockCubicShoulder(max(edge, 0.0) / max(uLight.y, 1e-3)), 1.0);"))
        assertTrue(shoulderOverride.contains("shine = mix(shine, shoulderShine, uDockBaseRimCubicShoulder);"))
        listOf("within", "tail", "uDockTone", "uDockBrightRim", "uDockDarkRim").forEach {
            assertTrue("The base shoulder must stay independent of $it", !shoulderOverride.contains(it))
        }
    }

    @Test
    fun fixedCubicShoulderHasFiniteSupportAndZeroSlopesAtBothEnds() {
        val helper = agsl.substringAfter("float dockCubicShoulder(float t) {").substringBefore("\n}")
        assertTrue(helper.contains("if (t < 0.0 || t >= 1.0) return 0.0;"))
        val expression = Regex("float shoulder = ([^;]+);").find(helper)!!.groupValues[1]
        val returned = Regex("return ([^;]+);").findAll(helper).last().groupValues[1]
        fun profile(t: Double): Double {
            if (t < 0.0 || t >= 1.0) return 0.0
            val shoulder = ScalarExpression(expression, mapOf("t" to t)).evaluate()
            return ScalarExpression(returned, mapOf("shoulder" to shoulder)).evaluate()
        }
        assertEquals(1.0, profile(0.0), 0.0)
        assertEquals(49.0 / 64.0, profile(.5), 0.0)
        for (t in listOf(-1.0, -.001, 1.0, 1.001, 2.0, 100.0)) {
            assertEquals("No secondary ring outside finite support at $t", 0.0, profile(t), 0.0)
        }
        (0 until 1000).forEach { i ->
            assertTrue(profile((i + 1) / 1000.0) <= profile(i / 1000.0))
        }
        val widthDp = 1.2
        val stepDp = 1e-5
        fun atDepth(dp: Double) = profile(dp / widthDp)
        assertEquals(0.0, (atDepth(stepDp) - atDepth(0.0)) / stepDp, 1e-8)
        assertEquals(0.0, (atDepth(widthDp) - atDepth(widthDp - stepDp)) / stepDp, 1e-4)
        assertEquals(0.0, (atDepth(widthDp + stepDp) - atDepth(widthDp)) / stepDp, 0.0)
    }

    @Test
    fun darkRimBindsIndependentlyOfTheBrightRimBodyAndSampling() {
        val bright = DockBrightRimResponse(1.0, .25, -.5, .75)
        val dark = DockBrightRimResponse(2.0, -.25, .5, -.75, blend = .5,
            angularBlend = .25, normalCrossGain = -.5, cosineBlend = .75)
        val response = DockToneResponse(.25, 1.0, brightRim = bright)
        val style = sampleStyle().copy(dockToneResponse = response)
        val before = build(haze = true, style = style)
        val after = build(haze = true, style = style.copy(dockToneResponse = response.copy(darkRim = dark)))
        val keys = setOf("uDockDarkRim", "uDockDarkRimChroma", "uDockDarkRimAngular", "uDockDarkRimCosine")
        keys.forEach { assertTrue(before.getValue(it).all { value -> value == 0f }) }
        assertArrayEquals(floatArrayOf(.5f, 2f, -.25f, .5f), after.getValue("uDockDarkRim"), 0f)
        assertArrayEquals(floatArrayOf(-.75f), after.getValue("uDockDarkRimChroma"), 0f)
        assertArrayEquals(floatArrayOf(.25f, -.5f), after.getValue("uDockDarkRimAngular"), 0f)
        assertArrayEquals(floatArrayOf(.75f), after.getValue("uDockDarkRimCosine"), 0f)
        before.filterKeys { it !in keys }.forEach { (key, value) ->
            assertArrayEquals(key, value, after.getValue(key), 0f)
        }
    }

    @Test
    fun darkRimNullAndZeroBlendKeepTheLegacyBrightOnlyShaderPath() {
        val branch = agsl.substringAfter("if (dockDarkRimWeight > 0.0) {")
        val legacy = branch.substringAfter("} else if (dockBrightRimWeight > 0.0) {")
            .substringBefore("// iOS 27 outlines")
        assertTrue(legacy.contains("glass = mix(glass, materialRim, dockBrightRimWeight);"))
        assertTrue(!legacy.contains("uDockDarkRim"))
        assertTrue(agsl.contains("dockDarkRimWeight = uDockTone.x * (1.0 - brightWeight) * uDockDarkRim.x;"))
        val dark = DockBrightRimResponse(1.0, -.5, .25, .75, blend = 0.0)
        val style = sampleStyle().copy(dockToneResponse = DockToneResponse(0.0, 1.0, darkRim = dark))
        assertEquals(0f, build(style = style).getValue("uDockDarkRim")[0], 0f)
    }

    @Test
    fun darkAndBrightRimsUseBoundedSharesOfTheSameOriginalRim() {
        fun assignment(name: String) = Regex("(?m)^\\s*$name = ([^;]+);").find(agsl)!!.groupValues[1]
        val brightExpression = assignment("dockBrightRimWeight")
        val darkExpression = assignment("dockDarkRimWeight")
        val branch = agsl.substringAfter("if (dockDarkRimWeight > 0.0) {")
            .substringBefore("} else if (dockBrightRimWeight > 0.0) {")
        val legacyExpression = Regex("float legacyRimWeight = ([^;]+);").find(branch)!!.groupValues[1]
        val resultExpression = Regex("glass = ([^;]+);").find(branch)!!.groupValues[1]
        for (tone in listOf(0.0, .25, 1.0)) for (weight in listOf(0.0, .001, .5, .999, 1.0)) {
            for (brightBlend in listOf(0.0, .5, 1.0)) for (darkBlend in listOf(0.0, .5, 1.0)) {
                val variables = mutableMapOf("uDockTone.x" to tone, "brightWeight" to weight,
                    "uDockBrightRim.x" to brightBlend, "uDockDarkRim.x" to darkBlend)
                val brightShare = ScalarExpression(brightExpression, variables).evaluate()
                val darkShare = ScalarExpression(darkExpression, variables).evaluate()
                variables["dockBrightRimWeight"] = brightShare
                variables["dockDarkRimWeight"] = darkShare
                val legacyShare = ScalarExpression(legacyExpression, variables).evaluate()
                assertTrue(brightShare >= 0.0 && darkShare >= 0.0 && brightShare + darkShare <= 1.0 + 1e-12)
                assertEquals(1.0, legacyShare + brightShare + darkShare, 1e-12)
                variables.putAll(mapOf("legacyRimWeight" to legacyShare, "glass" to .2,
                    "brightMaterialRim" to .8, "darkMaterialRim" to .1))
                val actual = ScalarExpression(resultExpression, variables).evaluate()
                val expected = .2 + tone * (weight * brightBlend * (.8 - .2)
                    + (1.0 - weight) * darkBlend * (.1 - .2))
                assertEquals(expected, actual, 1e-12)
            }
        }
    }

    @Test
    fun darkLowToneBindsIndependentlyOfTheHighBodyRimsAndSampling() {
        val response = DockToneResponse(.25, 1.0,
            brightRim = DockBrightRimResponse(1.0, .25, -.5, .75),
            darkRim = DockBrightRimResponse(2.0, -.25, .5, -.75))
        val curve = DockDarkLowToneResponse(.015625, .5, DockToneThresholds(.25, .5), .75)
        val style = sampleStyle().copy(dockToneResponse = response)
        val before = build(haze = true, style = style)
        val after = build(haze = true, style = style.copy(dockToneResponse = response.copy(darkLowTone = curve)))
        val keys = setOf("uDockDarkLowTone", "uDockDarkLowToneBlend")
        keys.forEach { assertTrue(before.getValue(it).all { value -> value == 0f }) }
        assertArrayEquals(floatArrayOf(.015625f, .5f, .25f, .5f), after.getValue("uDockDarkLowTone"), 0f)
        assertArrayEquals(floatArrayOf(.75f), after.getValue("uDockDarkLowToneBlend"), 0f)
        before.filterKeys { it !in keys }.forEach { (key, value) ->
            assertArrayEquals(key, value, after.getValue(key), 0f)
        }
    }

    @Test
    fun darkLowToneRejectsNonfiniteOutOfRangeAndFloatCollapsedInputs() {
        val transition = DockToneThresholds(.25, .5)
        val curve = DockDarkLowToneResponse(0.0, 0.0, transition, 0.0)
        assertEquals(1.0, curve.copy(linearIntercept = 1.0, linearGain = 8.0, blend = 1.0).blend, 0.0)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.001, 1.001)) {
            assertTrue(runCatching { curve.copy(linearIntercept = bad) }.isFailure)
            assertTrue(runCatching { curve.copy(blend = bad) }.isFailure)
        }
        for (bad in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -.001, 8.001)) {
            assertTrue(runCatching { curve.copy(linearGain = bad) }.isFailure)
        }
        for ((low, high) in listOf(.5 to .5, .5 to (.5 + 1e-10), .5 to .25,
            Double.NaN to .5, .25 to Double.POSITIVE_INFINITY, -.1 to .5, .25 to 1.1)) {
            assertTrue(runCatching { DockToneThresholds(low, high) }.isFailure)
        }
    }

    @Test
    fun darkLowToneNullAndZeroBlendKeepTheOriginalGammaAffineExpression() {
        val main = agsl.substringAfter("half4 main(float2 fragCoord)")
        val base = main.indexOf("float materialTone = response.x + response.y * colorLuma;")
        val guard = main.indexOf("if (uDockDarkLowToneBlend > 0.0) {")
        assertTrue(base >= 0 && guard > base)
        assertTrue(!main.substring(0, guard).contains("dockSrgbDecode("))
        assertTrue(main.contains("color = clamp(float3(materialTone)\n        + response.z * (color - float3(colorLuma)), 0.0, 1.0);"))
        val original = sampleStyle().copy(dockToneResponse = DockToneResponse(0.0, 1.0))
        val disabled = original.copy(dockToneResponse = original.dockToneResponse!!.copy(
            darkLowTone = DockDarkLowToneResponse(.015625, .5, DockToneThresholds(.25, .5), blend = 0.0)))
        assertEquals(0f, build(style = original).getValue("uDockDarkLowToneBlend")[0], 0f)
        assertEquals(0f, build(style = disabled).getValue("uDockDarkLowToneBlend")[0], 0f)
    }

    private fun dockTransfer(function: String, input: Double): Double {
        val source = agsl.substringAfter("float $function(float value) {").substringBefore("\n}")
        val local = Regex("float (\\w+) = ([^;]+);").find(source)!!
        val values = mutableMapOf("value" to input)
        values[local.groupValues[1]] = ScalarExpression(local.groupValues[2], values).evaluate()
        val low = Regex("if \\(${local.groupValues[1]} <= ([^)]+)\\) return ([^;]+);").find(source)!!
        val limit = ScalarExpression(low.groupValues[1], values).evaluate()
        val expression = if (values.getValue(local.groupValues[1]) <= limit) low.groupValues[2]
            else Regex("return ([^;]+);").findAll(source).last().groupValues[1]
        return ScalarExpression(expression, values).evaluate()
    }

    private fun darkLowToneStages(luma: Double, brightWeight: Double = 0.0,
        curveBlend: Double = 1.0, linearIntercept: Double = .015625, linearGain: Double = .5,
    ): Map<String, Double> {
        val main = agsl.substringAfter("half4 main(float2 fragCoord)")
        val source = main.substringAfter("if (uDockDarkLowToneBlend > 0.0) {").substringBefore("\n    }")
        fun expression(name: String) = Regex("float $name = ([^;]+);").find(source)!!.groupValues[1]
        val values = mutableMapOf("colorLuma" to luma, "brightWeight" to brightWeight,
            "uDockDarkLowTone.x" to linearIntercept, "uDockDarkLowTone.y" to linearGain,
            "uDockDarkLowTone.z" to .25, "uDockDarkLowTone.w" to .5,
            "uDockDarkLowToneBlend" to curveBlend, "uDockToneDark.x" to .0625, "uDockToneDark.y" to .75,
            "response.x" to (.0625 * (1.0 - brightWeight) + .5 * brightWeight),
            "response.y" to (.75 * (1.0 - brightWeight) + .5 * brightWeight))
        values["linearLuma"] = dockTransfer("dockSrgbDecode", luma)
        values["lowLinear"] = ScalarExpression(expression("lowLinear"), values).evaluate()
        values["lowTone"] = dockTransfer("dockSrgbEncode", values.getValue("lowLinear"))
        values["highAffine"] = ScalarExpression(expression("highAffine"), values).evaluate()
        values["hybridDark"] = ScalarExpression(expression("hybridDark"), values).evaluate()
        values["baseTone"] = ScalarExpression(Regex("float materialTone = ([^;]+);")
            .find(main)!!.groupValues[1], values).evaluate()
        values["delta"] = ScalarExpression(Regex("materialTone \\+= ([^;]+);")
            .find(source)!!.groupValues[1], values).evaluate()
        values["materialTone"] = values.getValue("baseTone") + values.getValue("delta")
        return values
    }

    @Test
    fun darkLowToneUsesTheStandardNonnegativeSrgbTransferBoundaries() {
        for (negative in listOf(-100.0, -1.0, -.001, 0.0)) {
            assertEquals(0.0, dockTransfer("dockSrgbDecode", negative), 0.0)
            assertEquals(0.0, dockTransfer("dockSrgbEncode", negative), 0.0)
        }
        assertEquals(.0031308049535603713, dockTransfer("dockSrgbDecode", .04045), 1e-15)
        assertEquals(.040449936, dockTransfer("dockSrgbEncode", .0031308), 1e-15)
        assertEquals(.21404114048223255, dockTransfer("dockSrgbDecode", .5), 1e-15)
        assertEquals(.5, dockTransfer("dockSrgbEncode", .21404114048223255), 1e-15)
        for (value in listOf(.0, .0001, .0031308, .04045, .25, .5, .75, 1.0)) {
            val linear = dockTransfer("dockSrgbDecode", value)
            assertTrue(linear.isFinite() && linear in 0.0..1.0)
            assertEquals(value, dockTransfer("dockSrgbEncode", linear), 3e-8)
        }
        for ((function, boundary) in listOf("dockSrgbDecode" to .04045, "dockSrgbEncode" to .0031308)) {
            assertEquals(dockTransfer(function, boundary - 1e-10),
                dockTransfer(function, boundary + 1e-10), 3e-8)
        }
    }

    @Test
    fun darkLowToneKeepsLowHighTransitionAndIndependentBlendEndpoints() {
        for (luma in listOf(0.0, .1, .25)) {
            val stages = darkLowToneStages(luma)
            assertEquals(stages.getValue("lowTone"), stages.getValue("materialTone"), 1e-12)
        }
        for (luma in listOf(.5, .75, 1.0)) {
            val stages = darkLowToneStages(luma)
            assertEquals(stages.getValue("highAffine"), stages.getValue("materialTone"), 1e-12)
        }
        val middle = darkLowToneStages(.375)
        assertEquals(.5 * (middle.getValue("lowTone") + middle.getValue("highAffine")),
            middle.getValue("materialTone"), 1e-12)
        for (luma in listOf(-1.0, 0.0, .04045, .25, .375, .5, 1.0, 1.1)) {
            for (weight in listOf(0.0, .25, 1.0)) for (blend in listOf(0.0, .5, 1.0)) {
                val stages = darkLowToneStages(luma, weight, blend)
                assertTrue(stages.values.all { it.isFinite() })
                assertTrue(stages.getValue("lowLinear") in 0.0..1.0)
                assertTrue(stages.getValue("lowTone") in 0.0..1.0)
                assertEquals(stages.getValue("baseTone") + (1.0 - weight) * blend
                    * (stages.getValue("hybridDark") - stages.getValue("highAffine")),
                    stages.getValue("materialTone"), 1e-12)
                if (weight == 1.0 || blend == 0.0) {
                    assertEquals(stages.getValue("baseTone"), stages.getValue("materialTone"), 0.0)
                }
            }
        }
        assertEquals(0.0, darkLowToneStages(.1, linearIntercept = 0.0, linearGain = 0.0).getValue("lowLinear"), 0.0)
        assertEquals(1.0, darkLowToneStages(1.0, linearGain = 8.0).getValue("lowLinear"), 0.0)
        for (boundary in listOf(.25, .5)) {
            assertEquals(darkLowToneStages(boundary - 1e-7).getValue("materialTone"),
                darkLowToneStages(boundary + 1e-7).getValue("materialTone"), 1e-5)
        }
    }

    @Test
    fun refractionLimitBindsAsAnIndependentScalarWithoutChangingOptics() {
        val original = build()
        assertArrayEquals(floatArrayOf(1f), original.getValue("uRefractionLimit"), 0f)
        for (limit in listOf(-1.0, 0.0, 1.5, 2.0)) {
            val changed = build(style = sampleStyle().copy(refractionLimit = limit))
            assertArrayEquals(floatArrayOf(limit.toFloat()), changed.getValue("uRefractionLimit"), 0f)
            original.filterKeys { it != "uRefractionLimit" }.forEach { (name, values) ->
                assertArrayEquals("$name stays independent of the cap", values, changed.getValue(name), 0f)
            }
        }
    }

    @Test
    fun refractionProfilePowerBindsIndependentlyOfEveryOtherUniform() {
        val original = build(haze = true, rim = LiquidGlassRim.menu(false))
        assertArrayEquals(floatArrayOf(1f), original.getValue("uRefractionProfilePower"), 0f)
        listOf(.5, 1.0, 2.0, 4.0).forEach { power ->
            val changed = build(haze = true, rim = LiquidGlassRim.menu(false),
                style = sampleStyle().copy(refractionProfilePower = power))
            assertArrayEquals(floatArrayOf(power.toFloat()), changed.getValue("uRefractionProfilePower"), 0f)
            original.filterKeys { it != "uRefractionProfilePower" }.forEach { (name, values) ->
                assertArrayEquals("$name must not borrow the profile power", values, changed.getValue(name), 0f)
            }
        }
    }

    @Test
    fun refractionProfilePowerBindingIsFiniteAndStrictlyPositive() {
        listOf(
            -1.0 to .001f, 0.0 to .001f, Double.MIN_VALUE to .001f,
            Double.MAX_VALUE to Float.MAX_VALUE,
            Double.NaN to 1f, Double.POSITIVE_INFINITY to 1f, Double.NEGATIVE_INFINITY to 1f,
        ).forEach { (requested, expected) ->
            val actual = build(style = sampleStyle().copy(refractionProfilePower = requested))
                .getValue("uRefractionProfilePower").single()
            assertEquals(expected, actual, 0f)
            assertTrue(actual.isFinite() && actual > 0f)
        }
    }

    @Test
    fun lateralSamplingBendsBothHalvesWithoutMovingTheCenterOrFlatFace() {
        // Read the shipped scalar field, so the check also catches a disconnected
        // or oppositely signed AGSL implementation. This is not a native fit.
        val expression = Regex("float panelLateralShift\\([^)]*\\)\\s*\\{\\s*return ([^;]+);")
            .find(agsl)?.groupValues?.get(1)
        assertNotNull("The positive-refraction path needs a horizontal sampling field", expression)
        fun shift(x: Double, depth: Double, normalY: Double = 1.0, gain: Double = .025): Double =
            ScalarExpression(expression!!, mapOf("x" to x, "halfWidth" to 160.0,
                "depth" to depth, "normalY" to normalY, "gain" to gain)).evaluate()
        assertTrue("Left source samples move inward so its displayed stripe can bow outward", shift(-120.0, 60.0) > 0.0)
        assertEquals(-shift(-120.0, 60.0), shift(120.0, 60.0), 1e-12)
        assertEquals(0.0, shift(0.0, 60.0), 0.0)
        assertEquals(0.0, shift(-120.0, 0.0), 0.0)
        assertEquals(0.0, shift(-120.0, 60.0, normalY = 0.0), 0.0)
        assertEquals(0.0, shift(-120.0, 60.0, gain = 0.0), 0.0)
        assertEquals(shift(-120.0, 60.0), shift(-120.0, 60.0, normalY = -1.0), 0.0)
        assertTrue(shift(-120.0, 12.0) < shift(-120.0, 60.0))
        // The added field must not fold a previously monotone straight-edge
        // horizontal mapping, including the supported 1.5x depth cap.
        var previous = Double.NEGATIVE_INFINITY
        for (x in -160..160) {
            val mapped = x + shift(x.toDouble(), 240.0, gain = .5)
            assertTrue("Horizontal source order at x=$x", mapped > previous)
            previous = mapped
        }
    }

    @Test
    fun lateralGainIsFiniteBoundedAndIndependentOfOtherUniforms() {
        val baseline = build()
        listOf(-1.0 to 0f, .45 to .45f, 1.0 to .5f,
            Double.NaN to 0f, Double.POSITIVE_INFINITY to 0f).forEach { (gain, expected) ->
            val changed = build(style = sampleStyle().copy(lateralRefractionGain = gain))
            assertEquals(expected, changed.getValue("uLateralRefractionGain").single(), 0f)
            baseline.forEach { (name, values) ->
                if (name != "uLateralRefractionGain") assertArrayEquals(name, values, changed.getValue(name), 0f)
            }
        }
        val main = agsl.substringAfter("half4 main(float2 fragCoord)")
        val lens = main.substringAfter("if (lens) {").substringBefore("} else {")
        assertTrue(!lens.contains("uLateralRefractionGain"))
        assertTrue(main.contains("shift.x += panelLateralShift(centered.x, halfSize.x, depth, normal.y, uLateralRefractionGain)"))
        listOf("sampleFrosted(point + shift, frost)", "sampleFrosted(point + shift + part, frost)",
            "sampleFrosted(point + shift - part, frost)", "sampleHaze(point + shift)").forEach {
            assertTrue("All optical texture reads share the corrected source field: $it", main.contains(it))
        }
    }

    private fun positiveRefractionBranch(): String = agsl
        .substringAfter("bool lens = uOptics.x < 0.0;")
        .substringAfter("} else {")
        .substringBefore("shift = -normal * depth;")

    @Test
    fun defaultProfileExplicitlyKeepsTheLegacyExpressionWithoutPow() {
        val positive = positiveRefractionBranch()
        val legacy = "float depth = min(uOptics.x, max(uRefractionLimit, 0.0) * halfMinor) * (1.0 - sqrt(1.0 - rise * rise));"
        val originalOffset = positive.indexOf(legacy)
        val guardOffset = positive.indexOf("if (uRefractionProfilePower != 1.0)")
        assertTrue("power1 must execute the old expression", originalOffset >= 0)
        assertTrue("pow belongs only to a non-default guarded override", guardOffset > originalOffset)
        assertTrue(!positive.substring(0, guardOffset).contains("pow("))
        assertTrue(positive.substring(guardOffset).contains("pow(profile, uRefractionProfilePower)"))
    }

    @Test
    fun profilePowerOnlyReshapesPositiveRefractionAfterAllShapeGeometry() {
        val main = agsl.substringAfter("half4 main(float2 fragCoord)")
        val lens = main.substringAfter("if (lens) {").substringBefore("} else {")
        assertTrue(lens.contains("shift = normal * -uOptics.x * smoothstep(0.0, bevel, inset)"))
        assertTrue(lens.contains("* (1.0 - smoothstep(bevel, halfMinor, inset));"))
        assertTrue(!lens.contains("uRefractionProfilePower"))
        val geometry = main.substringBefore("bool lens = uOptics.x < 0.0;")
        assertTrue(!geometry.contains("uRefractionProfilePower"))
        listOf("exactOutlineGeometry", "dualOutlineGeometry", "gooGeometry", "outlineGeometry",
            "roundedBoxDistance").forEach { assertTrue(geometry.contains(it)) }
        val afterShift = main.substringAfter("shift = -normal * depth;")
        assertTrue("rim, dispersion, haze and tint keep their old profile inputs",
            !afterShift.contains("uRefractionProfilePower"))
        assertTrue(main.contains("float bevel = max(min(uShape.z, halfMinor * 0.5), 1e-3);"))
        assertTrue(main.contains("float rise = 1.0 - clamp(inset / bevel, 0.0, 1.0);"))
    }

    @Test
    fun positiveProfileFormulaKeepsBothEndpointsAndChangesOnlyTheInterior() {
        // Evaluate expressions read from the shipped AGSL, not a second copy of
        // the profile formula. This checks scalar algebra, not GPU pixel parity.
        val positive = positiveRefractionBranch()
        val oldDepth = Regex("float depth = ([^;]+);").find(positive)!!.groupValues[1]
        val profile = Regex("float profile = ([^;]+);").find(positive)!!.groupValues[1]
        val shapedDepth = Regex("(?m)^\\s*depth = ([^;]+);").find(positive)!!.groupValues[1]
        fun depth(rise: Double, power: Double, requestedDepth: Double = 60.0): Double {
            val variables = mutableMapOf("rise" to rise, "uOptics.x" to requestedDepth,
                "uRefractionLimit" to 1.5, "halfMinor" to 20.0, "uRefractionProfilePower" to power)
            if (power == 1.0) return ScalarExpression(oldDepth, variables).evaluate()
            variables["profile"] = ScalarExpression(profile, variables).evaluate()
            return ScalarExpression(shapedDepth, variables).evaluate()
        }
        listOf(.001, .5, 1.0, 2.0, 4.0).forEach { power ->
            assertEquals("flat face remains unshifted", 0.0, depth(0.0, power), 0.0)
            assertEquals("rim depth stays D and cap stays1.5", 30.0, depth(1.0, power), 0.0)
            assertEquals("shape does not change uncapped D", 10.0, depth(1.0, power, 10.0), 0.0)
            assertEquals("zero refraction cannot become a lens", 0.0, depth(.6, power, 0.0), 0.0)
        }
        // At rise=.6 the circular section is exactly .2: changing gamma
        // redistributes the interior while the two endpoint constraints stay put.
        assertEquals(6.0, depth(.6, 1.0), 1e-12)
        assertEquals(1.2, depth(.6, 2.0), 1e-12)
        assertEquals(30.0 / sqrt(5.0), depth(.6, .5), 1e-12)
        assertTrue(depth(.6, 2.0) < depth(.6, 1.0))
        assertTrue(depth(.6, .5) > depth(.6, 1.0))
    }

    /** Small scalar-only AGSL expression reader for the source contract above. */
    private class ScalarExpression(expression: String, private val variables: Map<String, Double>) {
        private val tokens = Regex("[A-Za-z_]\\w*(?:\\.[xyzw])?|(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?|[-+*/(),]")
            .findAll(expression).map { it.value }.toList()
        private var index = 0

        init {
            require(tokens.joinToString("") == expression.filterNot { it.isWhitespace() })
        }

        fun evaluate(): Double {
            val value = sum()
            require(index == tokens.size)
            return value
        }

        private fun take(token: String): Boolean =
            (tokens.getOrNull(index) == token).also { if (it) index++ }

        private fun sum(): Double {
            var value = product()
            while (true) value = when {
                take("+") -> value + product()
                take("-") -> value - product()
                else -> return value
            }
        }

        private fun product(): Double {
            var value = atom()
            while (true) value = when {
                take("*") -> value * atom()
                take("/") -> value / atom()
                else -> return value
            }
        }

        private fun atom(): Double {
            if (take("-")) return -atom()
            if (take("+")) return atom()
            if (take("(")) return sum().also { require(take(")")) }
            val token = tokens[index++]
            if (!take("(")) return token.toDoubleOrNull() ?: variables.getValue(token)
            val arguments = mutableListOf(sum())
            while (take(",")) arguments += sum()
            require(take(")"))
            val a = arguments[0]
            return when (token) {
                "sqrt" -> sqrt(a)
                "exp" -> exp(a)
                "abs" -> abs(a)
                "max" -> maxOf(a, arguments[1])
                "min" -> minOf(a, arguments[1])
                "pow" -> a.pow(arguments[1])
                "clamp" -> a.coerceIn(arguments[1], arguments[2])
                "mix" -> a * (1.0 - arguments[2]) + arguments[1] * arguments[2]
                "smoothstep" -> ((arguments[2] - a) / (arguments[1] - a)).coerceIn(0.0, 1.0)
                    .let { it * it * (3.0 - 2.0 * it) }
                else -> error("Unsupported scalar AGSL function: $token")
            }
        }
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
    fun radiusSamplingIsExplicitAndKeepsEveryOtherUniform() {
        val legacy = build()
        val optedIn = build(frostRadiusSampling = true)
        assertEquals(0f, legacy.getValue("uFrostRadiusMode")[0], 0f)
        assertEquals(1f, optedIn.getValue("uFrostRadiusMode")[0], 0f)
        for (name in legacy.keys - "uFrostRadiusMode") {
            assertArrayEquals(name, legacy.getValue(name), optedIn.getValue(name), 0f)
        }
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

    @Test
    fun sheetEdgeFitPreservesMenuAndAlertDirectionalCoefficients() {
        // Independent reference values from the existing FLClash angular-light
        // contract, including the unlit side endpoint. The Sheet-only floor
        // must never brighten this endpoint for menus or alerts.
        val referenceLight = floatArrayOf(.16f, .1088f, .0768f, .0608f, .0272f, .0112f, .0048f, 0f)
        val referenceDark = floatArrayOf(.22f, .1496f, .1056f, .0836f, .0374f, .0154f, .0066f, 0f)
        listOf(false, true).forEach { dark ->
            listOf(LiquidGlassRim.menu(dark), LiquidGlassRim.alert(dark)).forEach { rim ->
                assertEquals(0.0, rim.minimumShine, 0.0)
                assertArrayEquals(
                    if (dark) referenceDark else referenceLight,
                    build(rim = rim).getValue("uShineLit"),
                    1e-7f,
                )
            }
        }
    }

    @Test
    fun screenFloorOptInPreservesAllExistingUniformsAndSeparatesOnlyAddedLight() {
        val rim = LiquidGlassRim.alert(false).copy(minimumShine = .08)
        val additive = build(rim = rim)
        val screened = build(rim = rim.copy(screenMinimumShine = true))
        assertArrayEquals(FloatArray(8), additive.getValue("uShineFloorLit"), 0f)
        val base = build(rim = rim.copy(minimumShine = 0.0)).getValue("uShineLit")
        val total = screened.getValue("uShineLit")
        val extra = screened.getValue("uShineFloorLit")
        assertEquals(0f, extra.first(), 0f)
        assertEquals(.08f, extra.last(), 1e-7f)
        for (i in 0..7) {
            assertEquals(total[i] - base[i], extra[i], 1e-7f)
            assertTrue(extra[i] >= 0f)
        }
        for (name in additive.keys - "uShineFloorLit") {
            assertArrayEquals(name, additive.getValue(name), screened.getValue(name), 0f)
        }
    }

    @Test
    fun screenFlagWithoutAnAddedFloorLeavesTheLegacyPathDisabled() {
        val variants = listOf<LiquidGlassRim?>(null, LiquidGlassRim.alert(false),
            LiquidGlassRim.alert(false).copy(screenMinimumShine = true))
        variants.forEach { rim ->
            assertArrayEquals(FloatArray(8), build(rim = rim).getValue("uShineFloorLit"), 0f)
        }
    }
}
