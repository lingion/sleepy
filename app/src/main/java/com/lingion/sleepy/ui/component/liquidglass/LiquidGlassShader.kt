package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/** A retained shape entry owns this dp path; callers must not mutate it. */
internal class LiquidGlassCachedOutlineProfile(
    val path: Path,
    private val exactContour: () -> LiquidGlassExactContour?,
) {
    private val profile by lazy { liquidGlassContourProfile(path) }

    // Descriptor identity keeps a cached profile tied to the actual CPU outline that produced it.
    fun matchingProfile(outline: Path, contour: LiquidGlassExactContour?): LiquidGlassContourProfile? =
        if (outline === path && contour != null && contour === exactContour()) profile else null
}

/**
 * FLClash 液态玻璃的 uniform 装配。
 *
 * 真源: `lib/ui/liquid_glass.dart`
 *  - `_captureFrom`(1340-1386): `uSize` / `uFrame` 与 mapping 前缀;
 *  - `_look`(1279-1320): pane 自身的外观段;
 *  - `lib/ui/liquid_glass_capture.dart` `_publish`(761-783): mapping 与 `uVeil`。
 *
 * AGSL 按**名字**绑定 uniform(Skia `SkRuntimeEffectBuilder::uniform(name)`), 故这里
 * 按名字产出 `Map<String, FloatArray>`, 不复刻 Flutter 的位置序。数组 uniform 必须一次
 * 喂满它的全部 float(Skia `SkRuntimeEffect.h` 的 `BuilderUniform::set` 校验
 * `sizeof(float) * count == sizeInBytes`), 否则 `setFloatUniform` 抛
 * `IllegalArgumentException`。
 *
 * 坐标契约(与 Dart 的唯一差异, 全部收在 `uFrame` / `uTexels`):
 *  - Flutter 的 `FlutterFragCoord` 是宿主局部**逻辑 px(dp)**, `uFrame=(−rect.left,
 *    −rect.top, 1, 1)` 把原点折回 pane 本地;
 *  - Android `RuntimeShader` 的 `main(float2 fragCoord)` 是**绘制节点局部设备 px**
 *    (AOSP `RuntimeShader` javadoc: 坐标 "local to the canvas"; Skia 的坐标文档:
 *    canvas 的 CTM 变换会一并作用于 shader 坐标), 故
 *    `uFrame=(−originPx/density, −originPx/density, 1/density, 1/density)`:
 *    (0,0) 恒指 pane 左上, `uShape`/`uEdge`/`uNormal` 以 pane 左上为原点 =
 *    FlClash 的 `−rect` 同义;
 *  - 因此调用方**不得**再对 canvas 做 `translate(pane 原点)`(那会二次平移
 *    `fragCoord`), clip 路径须直接用节点局部 px 构造;
 *  - `uTexels.x` = dp 每**设备** px = `1/(density * stretch)`;
 *    `uSample.zw` = dp 每**纹理** px = `1/texelsPerDp`(纹理可能被降采样, 与设备 px
 *    不同档, 两者不可混用)。
 *
 * `uVeil` 在 FLClash 里由重叠的 veil 图层算出(见 `liquid_glass_capture.dart` 的
 * `_liesEvenly` / `_encloses` / `Color.alphaBlend`), Android 侧没有等价物, 故由调用方
 * 显式给出, 默认全透明(= 不加 veil)。
 */
internal object LiquidGlassShader {

    /**
     * Android exposes one RenderNode input per RuntimeShader RenderEffect. Pack two independent
     * GPU branches into disjoint slots so the optical shader can read clear and Gaussian haze
     * without blurring the clear branch. The packing itself never filters or rescales pixels.
     */
    const val HAZE_ATLAS_SHADER = """
        uniform shader uTexture;
        uniform float4 uSlot;

        half4 main(float2 point) {
            float2 local = point - uSlot.xy;
            if (local.x < 0.0 || local.y < 0.0 ||
                local.x >= uSlot.z || local.y >= uSlot.w) {
                return half4(0.0);
            }
            return uTexture.eval(point);
        }
    """

    data class HazeAtlas(
        val paddingPx: Float,
        val sizePx: Size,
        val sourceOriginPx: Offset,
        val clearBoundsPx: Rect,
        val hazeBoundsPx: Rect,
        val remainingBlurPx: Float,
    )

    /** Slots run along the short axis; a wide Dock does not need a second full-width margin. */
    fun hazeAtlas(
        sizePx: Size,
        effectOriginPx: Offset,
        hazeBlurPx: Float,
        sampledBlurPx: Float,
        minimumPaddingPx: Float = 0f,
    ): HazeAtlas {
        require(sizePx.width.isFinite() && sizePx.height.isFinite() && sizePx.minDimension > 0f)
        require(hazeBlurPx.isFinite() && hazeBlurPx >= 0f)
        require(sampledBlurPx.isFinite() && sampledBlurPx >= 0f)
        val width = ceil(sizePx.width)
        val height = ceil(sizePx.height)
        val horizontal = width <= height
        val shortSpan = if (horizontal) width else height
        val remainingBlur = sqrt(max(hazeBlurPx * hazeBlurPx - sampledBlurPx * sampledBlurPx, 0f))
        // kyant records the source with this symmetric margin. It both fits the second slot
        // and gives the Gaussian its surrounding scene before either branch is packed.
        val padding = ceil(max(max(shortSpan * 0.5f, remainingBlur * 3f), minimumPaddingPx))
        return HazeAtlas(
            paddingPx = padding,
            sizePx = Size(ceil(sizePx.width + padding * 2f), ceil(sizePx.height + padding * 2f)),
            sourceOriginPx = effectOriginPx + Offset(padding, padding),
            clearBoundsPx = Rect(0f, 0f, width, height),
            hazeBoundsPx = if (horizontal) Rect(width, 0f, width * 2f, height)
                else Rect(0f, height, width, height * 2f),
            remainingBlurPx = remainingBlur,
        )
    }

    /**
     * uniform 名 → float 个数。与 `assets/shaders/liquid_glass.agsl` 的声明一一对应。
     *
     * `uPillCenters` 在 AGSL 里是 `float2[6]`(源码的 `float4[3]` 因 AGSL"数组只能用常量
     * 或循环变量索引"而重述, 值序不变), 故为 12 个 float。
     */
    val SIZES: Map<String, Int> = linkedMapOf(
        "uSize" to 2,
        "uFrame" to 4,
        "uSample" to 4,
        "uClamp" to 4,
        "uTexels" to 2,
        "uHazeSample" to 4,
        "uHazeClamp" to 4,
        "uHaze" to 1,
        "uVeil" to 4,
        "uShape" to 4,
        "uCorners" to 4,
        "uCircularCorners" to 1,
        "uCurves" to 48,
        "uOptics" to 3,
        "uTint" to 4,
        "uLight" to 3,
        "uGlow" to 4,
        "uGlowSpread" to 2,
        "uEdge" to 3,
        "uRim" to 4,
        "uOutlined" to 1,
        "uProfile" to 64,
        "uProfile2" to 64,
        "uProfileCenters" to 4,
        "uExact1" to LIQUID_GLASS_EXACT_FLOATS,
        "uExact2" to LIQUID_GLASS_EXACT_FLOATS,
        "uShineTurns" to 8,
        "uShineLit" to 8,
        "uShineEnds" to 12,
        "uShineShares" to 12,
        "uPills" to 24,
        "uGoo" to 8,
        "uPillCurves" to 24,
        "uPillCenters" to 12,
    )

    @Volatile
    private var cachedSource: String? = null

    fun shaderSource(context: android.content.Context): String {
        return cachedSource ?: synchronized(this) {
            cachedSource ?: context.assets.open("shaders/liquid_glass.agsl")
                .bufferedReader()
                .use { it.readText() }
                .also { cachedSource = it }
        }
    }

    /**
     * Dart 的 `_noHaze`(liquid_glass_capture.dart:24) = `[0, 0, 1, 1, 0, 0, 0, 0]`,
     * 覆盖 `uHazeSample`(4) + `uHazeClamp`(4) 两个槽位, 故这里拆成两段。
     */
    private val NO_HAZE_SAMPLE = floatArrayOf(0f, 0f, 1f, 1f)
    private val NO_HAZE_CLAMP = floatArrayOf(0f, 0f, 0f, 0f)

    /** Dart 的 `_flatProfile`(_noGoo / _noShine 同理, 全零)。 */
    private val FLAT_PROFILE = FloatArray(64)

    /** Flutter `GooSurface` 的药丸布局, 对应 `uPills` / `uGoo` / `uPillCurves` / `uPillCenters`。 */
    internal data class Goo(
        val pills: List<Rect>,
        val blends: List<Double>,
        val firstRadius: Double?,
    )

    /**
     * Dart 的 `_sampling` 在"单 pane 单纹理"下的代数坍缩(storage = identity、place =
     * 全纹理、pane 本地坐标):
     *  `uSample.xy` = pane 原点在纹理中的 px 折成 dp 取负, `uSample.zw` = dp 每纹理 px,
     *  `uClamp` = 槽位的像素中心 `[0.5, 0.5, texW−0.5, texH−0.5]`。
     *
     * `sampleGlass(pointDp)` → 纹理 px = `pointDp * texelsPerDp + originPx`, 即 pane
     * 左上落在纹理内 `originPx` 处 —— 与 FLClash atlas 里"pane 在原点、槽带边距"同形。
     *
     * @param originPx pane 原点在纹理中的 px
     * @param texelsPerDp 纹理 px 每 dp(= devicePixelRatio / divisor)
     * @param clampPx 槽位在纹理中的 px 范围
     */
    fun sampling(originPx: Offset, texelsPerDp: Float, clampPx: Rect): FloatArray {
        val perDp = 1f / texelsPerDp
        return floatArrayOf(
            -originPx.x * perDp, -originPx.y * perDp,
            perDp, perDp,
            clampPx.left + 0.5f, clampPx.top + 0.5f,
            clampPx.right - 0.5f, clampPx.bottom - 0.5f,
        )
    }

    /**
     * 装配全部 uniform(按名字)。
     *
     * @param size pane 尺寸(dp)
     * @param corners pane 的四个圆角(dp)
     * @param style 外观参数(须已过 [LiquidGlassStyle.scale])
     * @param glow 按压光(pane 本地 dp)
     * @param originPx pane 左上在**绘制节点局部**的 px(见类头坐标契约)
     * @param density 节点的 density(设备 px 每 dp)
     * @param stretch 宿主对该 pane 的额外缩放(FLClash 的 `max(|storage[0]|,|storage[5]|)`)
     * @param atlasSizePx 纹理(backdrop atlas)的 px 尺寸
     * @param sampling `uSample` + `uClamp`(8 个 float, 见 [sampling])
     * @param heldFrostTexelPx 纹理里已含的 frost sigma(纹理 px)
     * @param hazeSampling haze 槽位的 `uHazeSample` + `uHazeClamp`; null = 无 haze
     * @param outline pane 的轮廓路径; null = 圆角矩形
     * @param cachedOutlineProfile retained fallback profile, used only for its matching single exact contour
     * @param circularCorners the CPU outline uses circular arcs, rather than a rounded superellipse
     * @param dualContours two convex contours in pane-local dp, with independent radial centers
     * @param goo goo 药丸布局; null = 无 goo
     * @param rim 外圈; [LiquidGlassRim.shine] 与方向亮度下限进入着色器(side/corner/flat 是 CPU 画的
     *   渐变发丝线, 不属 uniform)
     * @param veil 覆盖在纹理之上的均匀遮罩(直 alpha)
     */
    fun build(
        size: Size,
        corners: LiquidGlassCornerRadii,
        style: LiquidGlassStyle,
        glow: LiquidGlassGlow = LiquidGlassGlow.None,
        originPx: Offset = Offset.Zero,
        density: Float = 1f,
        stretch: Float = 1f,
        atlasSizePx: Size,
        sampling: FloatArray,
        heldFrostTexelPx: Float = 0f,
        hazeSampling: FloatArray? = null,
        outline: Path? = null,
        dualContours: Pair<Path, Path>? = null,
        goo: Goo? = null,
        rim: LiquidGlassRim? = null,
        veil: Color = Color.Transparent,
        circularCorners: Boolean = false,
        exactContours: List<LiquidGlassExactContour>? = null,
        cachedOutlineProfile: LiquidGlassCachedOutlineProfile? = null,
    ): Map<String, FloatArray> {
        require(sampling.size == 8) { "sampling 须为 8 个 float, 实为 ${sampling.size}" }
        require(hazeSampling == null || hazeSampling.size == 8) {
            "hazeSampling 须为 8 个 float, 实为 ${hazeSampling?.size}"
        }
        val values = LinkedHashMap<String, FloatArray>(SIZES.size)
        fun put(name: String, vararg floats: Float) {
            val expected = SIZES.getValue(name)
            require(floats.size == expected) {
                "$name 须为 $expected 个 float, 实为 ${floats.size}"
            }
            values[name] = floats.copyOf()
        }

        // uSize: 纹理 px 尺寸(Dart: atlas.width / atlas.height)。
        put("uSize", atlasSizePx.width, atlasSizePx.height)
        // uFrame: 把 fragCoord(节点局部设备 px)折回 pane 本地 dp。
        put(
            "uFrame",
            -originPx.x / density, -originPx.y / density,
            1f / density, 1f / density,
        )
        put("uSample", sampling[0], sampling[1], sampling[2], sampling[3])
        put("uClamp", sampling[4], sampling[5], sampling[6], sampling[7])
        // uTexels: dp 每设备 px, 与纹理里已含的 sigma(纹理 px)。
        put("uTexels", 1f / (density * stretch), heldFrostTexelPx)
        if (hazeSampling != null) {
            put("uHazeSample", hazeSampling[0], hazeSampling[1], hazeSampling[2], hazeSampling[3])
            put("uHazeClamp", hazeSampling[4], hazeSampling[5], hazeSampling[6], hazeSampling[7])
            put("uHaze", style.haze.toFloat())
        } else {
            put("uHazeSample", *NO_HAZE_SAMPLE)
            put("uHazeClamp", *NO_HAZE_CLAMP)
            put("uHaze", 0f)
        }
        put("uVeil", veil.red, veil.green, veil.blue, veil.alpha)
        // uShape: 宽、高、bevel 宽、frost sigma。
        put(
            "uShape",
            size.width, size.height,
            style.bevel.toFloat(), style.blur.toFloat(),
        )
        put(
            "uCorners",
            corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft,
        )
        put("uCurves", *liquidGlassCornerCurves(size, corners))
        put("uCircularCorners", if (circularCorners) 1f else 0f)
        put(
            "uOptics",
            style.refraction.toFloat(), style.saturation.toFloat(), style.brightness.toFloat(),
        )
        put("uTint", style.tint.red, style.tint.green, style.tint.blue, style.tint.alpha)
        put("uLight", style.rim.toFloat(), style.rimWidth.toFloat(), style.endShade.toFloat())
        // uGlow 第 4 位 = style.contrast(clash 可读性), 不是 glow 数据(Dart `_look`)。
        put(
            "uGlow",
            glow.center.x, glow.center.y, glow.strength.toFloat(), style.contrast.toFloat(),
        )
        put("uGlowSpread", glow.reach.toFloat(), glow.wash.toFloat())
        put("uEdge", style.hairline.toFloat(), style.dispersion.toFloat(), style.rimBlur.toFloat())
        // uRim 第 4 位 = hairlineSide(Dart 的 `_hairlineSide` = hairlineSide ?? hairline)。
        put(
            "uRim",
            style.rimTail.toFloat(), style.rimFocus.toFloat(), style.rimAdded.toFloat(),
            style.hairlineSideOrHairline.toFloat(),
        )
        val exact = exactContours?.let { contours ->
            contours.mapNotNull { it.pack(density) }.takeIf { packed ->
                packed.size == contours.size && goo == null &&
                    ((packed.size == 1 && dualContours == null && outline != null) ||
                        (packed.size == 2 && dualContours != null))
            }
        }
        // Profiles remain available as an exceptional solver fallback. Exact mode and its
        // diagnostic found flag are separate, so a fallback is never reported as exact.
        val firstContour = (dualContours?.first ?: outline?.takeIf { exact?.size == 1 })
            ?.let { path ->
                cachedOutlineProfile?.takeIf { dualContours == null && exact?.size == 1 }
                    ?.matchingProfile(path, exactContours?.singleOrNull())
                    ?: liquidGlassContourProfile(path)
            }
        val secondContour = dualContours?.second?.let(::liquidGlassContourProfile)
        // 4/5 = one/two actual CPU cubic contours; 0/1/2/3 retain their previous meanings.
        put(
            "uOutlined",
            when {
                exact?.size == 2 -> 5f
                exact?.size == 1 -> 4f
                dualContours != null -> 3f
                outline == null -> 0f
                goo == null -> 1f
                else -> 2f
            },
        )
        put(
            "uProfile",
            *when {
                firstContour != null -> firstContour.radii
                outline == null -> FLAT_PROFILE
                else -> liquidGlassProfile(outline, Offset(size.width / 2f, size.height / 2f))
            },
        )
        put("uProfile2", *(secondContour?.radii ?: FLAT_PROFILE))
        put("uProfileCenters", firstContour?.center?.x ?: 0f, firstContour?.center?.y ?: 0f,
            secondContour?.center?.x ?: 0f, secondContour?.center?.y ?: 0f)
        put("uExact1", *(exact?.firstOrNull() ?: FloatArray(LIQUID_GLASS_EXACT_FLOATS)))
        put("uExact2", *(exact?.getOrNull(1) ?: FloatArray(LIQUID_GLASS_EXACT_FLOATS)))
        if (rim == null) {
            put("uShineTurns", *FloatArray(8))
            put("uShineLit", *FloatArray(8))
            put("uShineEnds", *FloatArray(12))
            put("uShineShares", *FloatArray(12))
        } else {
            val shine = liquidGlassShineUniforms(size, rim.shine.toFloat(), corners.topLeft)
            // A neutral system sheet retains an edge highlight along its long sides.
            // This empirical floor reuses the existing angular interpolation, depth
            // bands and per-pixel coverage. Other materials retain their exact table.
            if (rim.minimumShine > 0.0) {
                for (index in 8 until 16) shine[index] = max(shine[index], rim.minimumShine.toFloat())
            }
            put("uShineTurns", *shine.copyOfRange(0, 8))
            put("uShineLit", *shine.copyOfRange(8, 16))
            put("uShineEnds", *shine.copyOfRange(16, 28))
            put("uShineShares", *shine.copyOfRange(28, 40))
        }
        if (goo == null) {
            put("uPills", *FloatArray(24))
            put("uGoo", *FloatArray(8))
            put("uPillCurves", *FloatArray(24))
            put("uPillCenters", *FloatArray(12))
        } else {
            val packed = liquidGlassGooUniforms(goo.pills, goo.blends, goo.firstRadius)
            put("uPills", *packed.copyOfRange(0, 24))
            put("uGoo", *packed.copyOfRange(24, 32))
            put("uPillCurves", *packed.copyOfRange(32, 56))
            put("uPillCenters", *packed.copyOfRange(56, 68))
        }
        return values
    }
}
