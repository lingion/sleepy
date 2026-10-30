package com.lingion.sleepy.ui.component

/**
 * 玻璃 pane → FlClash shader uniform 序列的装配(切片 S2, 纯 JVM 可测)。
 *
 * 数组顺序 1:1 对齐 `liquid_glass.dart` `_captureFrom`(1218-1224):
 *   `[uSize(2), uFrame(4)] ++ mapping(19) ++ _look(184)` = 209。
 * mapping 来自 `_publish`(635-647):
 *   `_sampling(8) ++ uTexels(2) ++ _samplingHaze(8) ++ uHaze(1)`;
 * `_look`(1145-1181):
 *   uShape(4) ++ uCorners(4) ++ uCurves(48) ++ uOptics(3) ++ uTint(4) ++
 *   uLight(3) ++ uGlow(4) ++ uGlowSpread(2) ++ uEdge(3) ++ uRim(4) ++
 *   uOutlined(1) ++ uProfile(64) ++ uShine(40)。
 * 两处易被"顺手纠正"的源码事实, 勿改:
 *  - uGlow 第 4 位 = style.contrast(clash 可读性), 不是 glow 数据(`_look` 1168);
 *  - uRim 第 4 位 = `_hairlineSide`(= hairlineSide ?? hairline, 1177)。
 *
 * AGSL 适配(与 Dart 的唯一坐标差, 全部收进 uFrame/uTexels 两处):
 *  - Flutter 的 FlutterFragCoord 已是 pane 本地 dp、uFrame=(−rect.left,−rect.top,1,1);
 *    Android RuntimeShader 的 fragCoord 是绘制节点本地设备 px(未经 drawPatch 的
 *    translate 变换)→ uFrame=(−rectOriginPx/density, −rectOriginPx/density,
 *    1/density, 1/density): point=(0,0) 恒指 pane 左上, shape/edge/normal 以
 *    pane 左上为原点 = FlClash −rect 同义。rect 原点=(0,0) 的 pane(pill)退化为
 *    uFrame.xy=0 — 契约注释(LiquidGlassModifier 坐标契约)与此一致。
 *  - 两侧纹理 y 向都与 fragCoord 同向(向下), 无需翻转。
 */
object GlassUniforms {

    const val COUNT = 209

    // 与 liquid_glass.dart/_look 同步的段起点(index 表)。
    const val I_SIZE = 0
    const val I_FRAME = 2
    const val I_SAMPLE = 6
    const val I_CLAMP = 10
    const val I_TEXELS = 14
    const val I_HAZE_SAMPLE = 16
    const val I_HAZE_CLAMP = 20
    const val I_HAZE = 24
    const val I_SHAPE = 25
    const val I_CORNERS = 29
    const val I_CURVES = 33
    const val I_OPTICS = 81
    const val I_TINT = 84
    const val I_LIGHT = 88
    const val I_GLOW = 91
    const val I_GLOW_SPREAD = 95
    const val I_EDGE = 97
    const val I_RIM = 100
    const val I_OUTLINED = 104
    const val I_PROFILE = 105
    const val I_SHINE = 169

    /**
     * Dart `_sampling`(650-673)在"单 pane 单纹理"下的代数坍缩(storage=identity、
     * place=全纹理、pane 本地坐标): uSample.xy = −padDp、zw = 1/scale(=dp 每纹理
     * 像素)、uClamp = 像素中心 [0.5, 0.5, texW−0.5, texH−0.5]。
     * sampleGlass(pointDp) → pixel = (pointDp + padDp) × scale, 即 pane 左上角
     * 在纹理内缩 pad 处 —— FlClash atlas 里"pane 在原点、槽带边距"的同一形状。
     */
    fun sampling(padDp: Float, texelsPerDp: Float, texW: Float, texH: Float): FloatArray =
        floatArrayOf(-padDp, -padDp, 1f / texelsPerDp, 1f / texelsPerDp,
            0.5f, 0.5f, texW - 0.5f, texH - 0.5f)

    /** Dart `_noHaze`(liquid_glass_capture.dart:23)。 */
    val NO_HAZE = floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f)

    /** uGlow 的输入(pane 本地 dp); strength/reach/wash 由 _shine() 弹簧值算好。 */
    data class Glow(val centerX: Float, val centerY: Float, val strength: Float, val reach: Float, val wash: Float) {
        companion object {
            /** Dart LiquidGlassGlow.none: center 0, reach 1, strength 0, wash 0。 */
            val NONE = Glow(0f, 0f, 0f, 1f, 0f)
        }
    }

    /**
     * 装配全 209 float。style 须已过 scale(lift)。cornerRadius 内部再按
     * shortestSide/2 钳位(LiquidGlassPane.paint 965-972 的同一钳位)。
     * haze=null → _noHaze + share 0(Dart `_publish` 642-643)。
     *
     * @param rectOriginXPx rectOriginYPx pane 矩形左上的**当前绘制节点局部 px**
     *   (= drawPatch 的 translate 量)。fragCoord 是节点局部 px(不受该 translate
     *   影响), uFrame.xy 必须带负号折回 pane 本地 —— 否则 rect≠(0,0,..) 的 pane
     *   (_GlassLens=outer、lensContent=r)shape/normal/采样全错位(审计 U-3 P0)。
     * @param tintAlphaF 若 ≥0, uTint.w 用它(Dart 浮点直通); 缺省(-1)取 tintArgb
     *   的 8bit alpha —— 深色 tint .48/.62 经 toArgb 量化成 122/255=0.4784314,
     *   与 Dart 常量差 0.15%(审计 2 P0)。
     */
    fun build(
        rectWdp: Float,
        rectHdp: Float,
        cornerRadiusDp: Float,
        style: GlassStyleSpec,
        tintArgb: Int,
        glow: Glow,
        density: Float,
        stretch: Float = 1f,
        sampling: FloatArray,
        heldFrostTexelPx: Float,
        hazePadSampling: FloatArray? = null,
        rectOriginXPx: Float = 0f,
        rectOriginYPx: Float = 0f,
        tintAlphaF: Float = -1f,
    ): FloatArray {
        require(sampling.size == 8 && (hazePadSampling == null || hazePadSampling.size == 8))
        val v = FloatArray(COUNT)
        // uSize: 纹理像素尺寸 = clamp 末位 + 0.5(sampling 携带, 免双传)。
        v[0] = sampling[6] + 0.5f
        v[1] = sampling[7] + 0.5f
        // uFrame: Android 换算(见头注)。Pane.drawPatch 先 translate(rect.left,
        // top) 再画 pane 本地 clip → fragCoord 以**节点**左上为 0, 故 uFrame.xy
        // = −rect原点/density 把 (0,0) 原点折回 pane 本地(契约: LiquidGlassModifier
        // 坐标契约; rect=(0,0,..) 时与旧行为逐位一致)。
        v[2] = -rectOriginXPx / density; v[3] = -rectOriginYPx / density
        v[4] = 1f / density; v[5] = 1f / density
        // mapping 19。
        sampling.copyInto(v, I_SAMPLE)
        v[I_TEXELS] = 1f / (density * stretch)           // dp 每 fragCoord px
        v[I_TEXELS + 1] = heldFrostTexelPx              // 纹理已含的 sigma(纹理 px)
        if (hazePadSampling != null) {
            hazePadSampling.copyInto(v, I_HAZE_SAMPLE)
            v[I_HAZE] = style.haze
        } else {
            NO_HAZE.copyInto(v, I_HAZE_SAMPLE)
        }
        // _look 184。
        v[I_SHAPE] = rectWdp; v[I_SHAPE + 1] = rectHdp; v[I_SHAPE + 2] = style.bevel; v[I_SHAPE + 3] = style.blur
        val r = cornerRadiusDp.coerceAtMost(minOf(rectWdp, rectHdp) / 2f)
        v[I_CORNERS] = r; v[I_CORNERS + 1] = r; v[I_CORNERS + 2] = r; v[I_CORNERS + 3] = r
        val curves = LiquidGlassSuperellipse.cornerCurves(
            rectWdp.toDouble(), rectHdp.toDouble(),
            r.toDouble(), r.toDouble(), r.toDouble(), r.toDouble(),
        )
        for (c in 0 until 48) v[I_CURVES + c] = curves[c].toFloat()
        v[I_OPTICS] = style.refractionStrength; v[I_OPTICS + 1] = style.saturation; v[I_OPTICS + 2] = style.brightness
        v[I_TINT] = ((tintArgb shr 16) and 0xFF) / 255f
        v[I_TINT + 1] = ((tintArgb shr 8) and 0xFF) / 255f
        v[I_TINT + 2] = (tintArgb and 0xFF) / 255f
        v[I_TINT + 3] = if (tintAlphaF >= 0f) tintAlphaF
        else ((tintArgb ushr 24) and 0xFF) / 255f
        v[I_LIGHT] = style.rim; v[I_LIGHT + 1] = style.rimWidth; v[I_LIGHT + 2] = style.endShade
        v[I_GLOW] = glow.centerX; v[I_GLOW + 1] = glow.centerY; v[I_GLOW + 2] = glow.strength
        v[I_GLOW + 3] = style.contrast
        v[I_GLOW_SPREAD] = glow.reach; v[I_GLOW_SPREAD + 1] = glow.wash
        v[I_EDGE] = style.hairline; v[I_EDGE + 1] = style.dispersion; v[I_EDGE + 2] = style.rimBlur
        v[I_RIM] = style.rimTail; v[I_RIM + 1] = style.rimFocus; v[I_RIM + 2] = style.rimAdded
        v[I_RIM + 3] = style.lightEdgeFraction ?: style.hairline
        v[I_OUTLINED] = 0f // Dock 三 pane 无 clipper(liquid_glass.dart 899-901 else 分支)
        // uProfile 64 + uShine 40: Dock 恒零(默认数组已置), 菜单 morph 到来时在此接参。
        return v
    }
}
