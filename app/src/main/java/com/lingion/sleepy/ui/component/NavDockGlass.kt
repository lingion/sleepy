package com.lingion.sleepy.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * 悬浮 Dock **玻璃分支**运动学纯函数 — 1:1 移植 FlClash
 * `lib/ui/navigation_dock.dart` (v0.9.1) 中不依赖 Flutter 运行时的那部分。
 *
 * 与 [DockDragLogic] 分工: DockDragLogic=几何布位/位移映射(经典+玻璃共用),
 * 本文件=玻璃特有的连续形变物理(果冻速度态/触点光晕/整条压弹变换/悬停磁铁)。
 * 全部无状态、单位无关(调用方统一换算), JVM 单测 NavDockGlassTest 逐条红绿。
 *
 * Dart→Kotlin 语义前提(勿"顺手简化"):
 *  1. Dart `sign(0.0)==0.0` → braking 项在 v=0 时恒 0(Float.sign 同为 0→0, ✓);
 *  2. `_saturate(x)=1-2/(e^{2x}+1)` 即数学上的 tanh(x), 按原式直译不换
 *     kotlin 标准库函数(双曲函数在 Float 域实现有精度差);
 *  3. pressSwell 的 min() 两项在面板 ≥64dp 时由 16/longestSide 取胜(手机实况),
 *     <64dp 才轮到 pressGrowth*2 — 与 _RenderPressTransform line 721 一致。
 */
object DockGlassLogic {

    /** FlClash `_saturate`: 软饱和(0..1 渐近, 一阶导在 0 处=1)。 */
    fun saturate(x: Float): Float = 1f - 2f / (exp(2f * x) + 1f)

    /**
     * FlClash `_wobble` 的 braking 输入 (line 855):
     * `max(0, -acceleration * velocity.sign)` — 只统计"逆着当前速度"的加速度
     * (正在减速=压扁, 仍在加速=不算刹车)。弹簧物理的 a 由调用方从 Compose
     * SpringSimulation 取(mass=1: a = k*(target-value) - c*velocity, 与
     * Flutter _Spring.acceleration line 170-177 同式)。
     */
    fun brakingTerm(velocity: Float, acceleration: Float): Float =
        max(0f, -acceleration * velocity.sign)

    /**
     * FlClash `_wobble` 目标 (line 856-858):
     * `_jellyStretch*saturate(|v|/jellySpeed) − _jellySquash*saturate(braking/jellyBrake)`。
     * 正=横向拉伸(快滑), 负=纵向压扁(急停)。
     */
    fun jellyTarget(
        velocity: Float,
        acceleration: Float,
        jellySpeed: Float,
        jellyStretch: Float,
        jellyBrake: Float,
        jellySquash: Float,
    ): Float = jellyStretch * saturate(abs(velocity) / jellySpeed) -
        jellySquash * saturate(brakingTerm(velocity, acceleration) / jellyBrake)

    /** FlClash `_shine` strength (line 871): `_glowPeak * glow * glow` — 平方淡入更"点亮"感。 */
    fun glowStrength(glow: Float, glowPeak: Float): Float = glowPeak * glow * glow

    /** FlClash `_shine` reach (line 870): `_glowReach*(1+_glowGrowth*spread)` — 释放时随淡出扩散。 */
    fun glowReach(reach: Float, spread: Float, growth: Float): Float =
        reach * (1f + growth * spread)

    /**
     * FlClash `_RenderPressTransform` swell (line 720-721):
     * `1 + lift*min(_pressGrowth*2, _maxPressGrowth/panel.longestSide)` —
     * 按压时整条(含内容)微微膨胀, 大面板按绝对增长封顶 16dp。
     */
    fun pressSwell(
        lift: Float,
        panelLongestSide: Float,
        pressGrowth: Float,
        maxPressGrowth: Float,
    ): Float = 1f + lift * min(pressGrowth * 2f, maxPressGrowth / panelLongestSide)

    /**
     * FlClash `_RenderPressTransform` 单轴缩放 (line 722-723):
     * `swell*(1+|pull|/panelSize*_pullStretch)` — 条体被拖出边界时的横向/纵向拉伸。
     * pull=橡皮筋后的越界位移(像素), panelSize=该轴面板尺寸。
     */
    fun pullScale(swell: Float, pull: Float, panelSize: Float, pullStretch: Float): Float =
        if (panelSize <= 0f) swell else swell * (1f + abs(pull) / panelSize * pullStretch)

    /**
     * FlClash `_leanAt` (line 936-939): `index + (position-index)*_hoverMagnet` —
     * 悬停时色块只向光标偏 20%, 不整块吸附(magnet 越小越"粘"在原座位)。
     */
    fun hoverLean(position: Float, magnet: Float, lastIndex: Int = Int.MAX_VALUE): Float {
        val index = DockDragLogic.indexAt(position, lastIndex)
        return index + (position - index) * magnet
    }

    /**
     * FlClash `_leanContent` 单项目标 (line 889-892):
     * 光标距该 tab 超半座位 → 0; 否则 `offset*_hoverMagnet*_hoverParallax` —
     * 图标随色块微视差(10% 磁吸再 50% 视差 = 5% 总位移)。
     */
    fun hoverParallax(
        hoverAt: Float,
        index: Int,
        magnet: Float,
        parallax: Float,
    ): Float {
        val offset = hoverAt - index
        return if (abs(offset) > 0.5f) 0f else offset * magnet * parallax
    }
}

/**
 * FlClash `LiquidGlassStyle` 的纯数据镜像 (liquid_glass.dart line 79-260)。
 *
 * 只存数值不存 Color: tint 的实际色相由运行时 colorScheme 解析
 * (pill/regular 用 surfaceContainerHighest/surfaceBright 等), 这里只带 alpha
 * 与"主题色 or 字面色"标记; 接线见 GlassUniforms.build(AGSL uniform 装配)。
 * 数值全部取自 v0.9.1 四预设原文, [scale] 逐字段对齐 line 262-285。
 */
data class GlassStyleSpec(
    val tintAlpha: Float,
    /** true = 明暗都取 colorScheme.surfaceContainerHighest; false = 字面色(注释标明)。 */
    val tintFromContainerHighest: Boolean,
    val contrast: Float,
    val blur: Float,
    val haze: Float,
    val hazeBlur: Float,
    val refractionStrength: Float,
    val bevel: Float,
    val saturation: Float,
    val brightness: Float,
    val rim: Float,
    val rimWidth: Float,
    val rimTail: Float,
    val rimFocus: Float,
    val rimAdded: Float,
    val endShade: Float,
    val dispersion: Float,
    val rimBlur: Float,
    val hairline: Float,
    /** Dart `hairlineSide` (liquid_glass.dart line 259/384): uRim.w 优先取此,
     *  null 时退 [hairline]; Dock 三 pane(lens/lensContent/pill)均 null,
     *  仅 regular 预设携带 0.75/0.5/0.73。字段名沿用装配层(勿"顺手改名")。 */
    val lightEdgeFraction: Float?,
) {
    /** FlClash `scale(t)` — 透镜从"空气"里浮现: 尺寸场乘 t, hazeBlur/bevel/rimWidth 保持,
     *  saturation/brightness 向 1 收敛(line 262-285 逐行)。 */
    fun scale(t: Float): GlassStyleSpec {
        val a = t.coerceIn(0f, 1f)
        return copy(
            tintAlpha = tintAlpha * a,
            contrast = contrast * a,
            blur = blur * a,
            haze = haze * a,
            // hazeBlur 源码原样保持
            refractionStrength = refractionStrength * a,
            // bevel 源码原样保持
            saturation = 1f + (saturation - 1f) * a,
            brightness = 1f + (brightness - 1f) * a,
            rim = rim * a,
            // rimWidth/rimTail/rimFocus/rimAdded 源码原样保持
            endShade = endShade * a,
            dispersion = dispersion * a,
            rimBlur = rimBlur * a,
            hairline = hairline * a,
            lightEdgeFraction = lightEdgeFraction?.let { it * a },
        )
    }
}

/** 玻璃三件套 + regular 预设数值 — 全部对齐 liquid_glass.dart v0.9.1 构造默认值。 */
object DockGlassStyles {

    // 构造默认 (line 83-97)
    private const val DEF_BEVEL = 20f
    private const val DEF_REFRACTION = 60f
    private const val DEF_BLUR = 2.5f
    private const val DEF_RIM = 0.6f
    private const val DEF_RIM_WIDTH = 1.3f

    /** LiquidGlassStyle.pill (line 130-149) — Dock 条体。 */
    fun pill(dark: Boolean) = if (dark) {
        GlassStyleSpec(
            tintAlpha = 0.48f, tintFromContainerHighest = true,
            contrast = -0.4f, blur = 0f, haze = 0.72f, hazeBlur = 15.6f,
            refractionStrength = DEF_REFRACTION, bevel = DEF_BEVEL,
            saturation = 1.925f, brightness = 1f,
            rim = 0.35f, rimWidth = DEF_RIM_WIDTH,
            rimTail = 0f, rimFocus = 0f, rimAdded = 0f,
            endShade = 0f, dispersion = 0f, rimBlur = 0f,
            hairline = 0f, lightEdgeFraction = null,
        )
    } else {
        GlassStyleSpec(
            // 字面色 0x93F4F4F4 → alpha 147/255
            tintAlpha = 147f / 255f, tintFromContainerHighest = false,
            contrast = 0.4f, blur = 0f, haze = 0.72f, hazeBlur = 15.6f,
            refractionStrength = DEF_REFRACTION, bevel = DEF_BEVEL,
            saturation = 2.1f, brightness = 1f,
            rim = DEF_RIM, rimWidth = DEF_RIM_WIDTH,
            rimTail = 0f, rimFocus = 0f, rimAdded = 0f,
            endShade = 0f, dispersion = 0f, rimBlur = 0f,
            hairline = 0.35f, lightEdgeFraction = null,
        )
    }

    /** LiquidGlassStyle.lens (line 192-208) — 抬起的选中透镜(凸, 负折射, 强色散)。 */
    fun lens(dark: Boolean) = GlassStyleSpec(
        tintAlpha = 0f, tintFromContainerHighest = false,
        contrast = 0f, blur = 0f, haze = 0f, hazeBlur = 0f,
        refractionStrength = -4.3f, bevel = 5.5f,
        saturation = 1f, brightness = if (dark) 1.33f else 1f,
        rim = if (dark) 0.3f else 0.6f, rimWidth = 1.1f,
        rimTail = 0f, rimFocus = 0f, rimAdded = 0f,
        endShade = 0.045f, dispersion = 6.5f, rimBlur = 1.5f,
        hairline = 0.42f, lightEdgeFraction = null,
    )

    /** LiquidGlassStyle.lensContent (line 210-223) — 色块内内容的二次弯折层。 */
    fun lensContent(dark: Boolean) = GlassStyleSpec(
        tintAlpha = 0f, tintFromContainerHighest = false,
        contrast = 0f, blur = 0f, haze = 0f, hazeBlur = 0f,
        refractionStrength = 14f, bevel = 11.2f,
        saturation = 1f, brightness = if (dark) 1.33f else 1f,
        rim = 0f, rimWidth = DEF_RIM_WIDTH,
        rimTail = 0f, rimFocus = 0f, rimAdded = 0f,
        endShade = 0f, dispersion = 3.6f, rimBlur = 2.5f,
        hairline = 0f, lightEdgeFraction = null,
    )

    /** LiquidGlassStyle.regular (line 101-128) — 备查(本 Dock 未用, 数值锁进测试防漂移)。 */
    fun regular(dark: Boolean) = if (dark) {
        GlassStyleSpec(
            tintAlpha = 0.62f, tintFromContainerHighest = true,
            contrast = -0.4f, blur = DEF_BLUR, haze = 0f, hazeBlur = 0f,
            refractionStrength = DEF_REFRACTION, bevel = DEF_BEVEL,
            saturation = 2.84f, brightness = 1f,
            rim = 0.28f, rimWidth = 1.2f,
            rimTail = 0.17f, rimFocus = 1f, rimAdded = 0.38f,
            endShade = 0f, dispersion = 0f, rimBlur = 0f,
            hairline = 0f, lightEdgeFraction = 0.75f,
        )
    } else {
        GlassStyleSpec(
            // 字面色 0x8AFDFDFD → alpha 138/255
            tintAlpha = 138f / 255f, tintFromContainerHighest = false,
            contrast = 0.4f, blur = DEF_BLUR, haze = 0f, hazeBlur = 0f,
            refractionStrength = DEF_REFRACTION, bevel = DEF_BEVEL,
            saturation = 2.08f, brightness = 1f,
            rim = 0.28f, rimWidth = 1.2f,
            rimTail = 0.17f, rimFocus = 1f, rimAdded = 0.38f,
            endShade = 0f, dispersion = 0f, rimBlur = 0f,
            hairline = 0.25f, lightEdgeFraction = 0.75f,
        )
    }

    // ══ 阴影/底色 alpha (navigation_dock.dart) ══

    /** dockGlassShadows (line 237-243): iOS 27 式 — 仅下方, 淡, 明暗同值。 */
    const val dockGlassShadowAlpha = 0.013f
    const val dockGlassShadowBlur = 3.8f
    const val dockGlassShadowOffsetY = 6.5f

    /** _lensShadows (line 1404-1412): 0.055*strength*lift, strength 暗=3 明=1。 */
    fun lensShadowAlpha(lift: Float, strength: Float): Float =
        0.055f * strength * lift.coerceIn(0f, 1f)

    /** _lensShadows blurRadius (dp, Dart line 1410)。 */
    const val lensShadowBlurDp = 5f
    /** _lensShadows 向下偏移 (dp, Dart line 1411 Offset(0,8))。 */
    const val lensShadowOffsetYDp = 8f

    /** _Lens glass 托盘 (line 1349-1354): platter α .85 随 lift 清空(交给玻璃透镜)。 */
    fun platterAlpha(lift: Float): Float = 0.85f * (1f - lift.coerceIn(0f, 1f))
}

/**
 * FlClash 弹簧表 (navigation_dock.dart line 57-98) → Compose `spring()`。
 *
 * 两族换算(源: flutter SpringDescription, spring_simulation.dart line 40-82;
 * ODE 与 androidx SpringSimulation 完全一致, mass=1):
 *  - withDampingRatio: 原值直用 (stiffness, ratio);
 *  - withDurationAndBounce: stiffness = 4π²/T², ratio = bounce>0 ? 1-bounce : 1/(1+bounce)。
 * Compose spring() 即 spring(dampingRatio, stiffness, mass=1) 同 ODE。
 */
object DockSprings {

    data class Spec(val stiffness: Float, val dampingRatio: Float)

    private fun duration(ms: Float, bounce: Float = 0f) = Spec(
        stiffness = (4f * PI_SQ) / (ms / 1000f * (ms / 1000f)),
        dampingRatio = if (bounce > 0f) 1f - bounce else 1f / (bounce + 1f),
    )

    private const val PI_SQ = 3.14159265358979f * 3.14159265358979f

    /** _trackSpring: 120ms 无回弹 — 条体被拖出边界的跟手回中。 */
    val track = duration(120f)

    /** _pressSpring: 311/0.593 — 按压膨胀/松手回弹。 */
    val press = Spec(stiffness = 311f, dampingRatio = 0.593f)

    /** _followSpring: 1024/0.9 — 拖动中色块跟手(极硬, 近无滞后)。 */
    val follow = Spec(stiffness = 1024f, dampingRatio = 0.9f)

    /** _liftSpring: 250ms — 透镜抬升/落回。 */
    val lift = duration(250f)

    /** _travelSpring: 400ms bounce .15 — 色块跨座位行程(轻微过冲)。 */
    val travel = duration(400f, 0.15f)

    /** _jellySpring: 100/0.4 — 果冻形变量本身(软弹簧, 形变拖尾)。 */
    val jelly = Spec(stiffness = 100f, dampingRatio = 0.4f)

    /** _settleSpring: 500ms bounce .32 — 悬停 swell 落定。 */
    val settle = duration(500f, 0.32f)

    /** _hoverSpring: 260ms bounce .18 — 悬停磁铁/视差。 */
    val hover = duration(260f, 0.18f)

    /** _fadeSpring: 200ms — 悬停高亮淡入淡出。 */
    val fade = duration(200f)

    /** _glowInSpring: 100ms — 触点光晕点亮。 */
    val glowIn = duration(100f)

    /** _glowOutSpring: 500ms — 光晕淡出+扩散。 */
    val glowOut = duration(500f)

    /**
     * 松手路径 _release/_handlePointerEnd 中 stretch/pull/swell 归位实际传
     * _pressSpring (line 1041,1046) — 别名固化"回弹用 press 非 travel",
     * 与 travel 区分(行程弹簧只管色块跨座)。
     */
    val pullBack = press

    /**
     * 弹簧实时速度(Compose 侧): 由 target-value 与 velocity 反解 ODE 加速度 —
     * Flutter _Spring.acceleration line 170-177: a = k*(target−value) − c*velocity,
     * c = ratio*2*√(mass*k), mass=1 → c = 2*ratio*√k。喂给 [DockGlassLogic.jellyTarget]。
     */
    fun acceleration(spec: Spec, target: Float, value: Float, velocity: Float): Float {
        val damping = 2f * spec.dampingRatio * sqrt(spec.stiffness)
        return spec.stiffness * (target - value) - damping * velocity
    }
}
