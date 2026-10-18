package com.lingion.sleepy.util

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import com.lingion.sleepy.data.entity.CourseColorMode
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.CoursePalette

/**
 * 课程底色单一事实来源 — 三层结构（决策 D3）
 *
 * 收敛原来分散在 TodayScreen / CourseTableView / WeekGridWidgetProvider /
 * WidgetBitmapRenderers 四处的课程配色私有副本，统一为一份逻辑：
 *
 * 决策树（所有入口 100% 同源）：
 *   ① 用户自定义颜色（color 非空且非哨兵值）→ 直接返回，colorless 不覆盖手动设色
 *   ② colorless=true → 返回当前主题最浅的 surface container
 *   ③ 否则 → 黄金角 137.508° 基于 groupId 撒 hue，同门课永远同色
 *
 * 三层结构：
 *   常量层     — GOLDEN_ANGLE / SENTINEL_COLOR / S_LIGHT / S_DARK / L_LIGHT / L_DARK（各定义一次）
 *   纯逻辑层   — stableHue / hasCustomColor（无平台依赖）
 *   平台适配层 — pickCourseColorCompose / pickCourseColorInt（两套返回类型，同一份逻辑）
 */
object CourseColorUtil {

    // ============================ 第一层 · 常量 ============================

    /** 黄金角 137.508°，相邻 id 色差最大化（13 门课最少差 ~27°） */
    private const val GOLDEN_ANGLE = 137.508f

    /** 允许弧参数域总长 = 两段 120° (360° − 主题保护带 60° − 补色带 60°)，1 单位 = 1° */
    private const val ARC_UNITS = 240

    /** 避让扫描步长（度）— 2° 网格兼顾槽位密度与重算耗时（≤120 次/课） */
    private const val ARC_STEP_DEG = 2

    /** 课色最小环距保证（重算路径）— 肉眼可辨下限; 敏感度证明见契约测试 spacingBound_isWhatGuaranteesSeparation */
    internal const val MIN_SEP_DEG = 10f

    /** 哨兵色 "#FF6750A4"，标记「未设置颜色」，与主题默认紫完全相同（Phase2 换 isCustomColor 布尔 + DB migration） */
    private const val SENTINEL_COLOR = "#FF6750A4"

    /** 亮色模式饱和度（柔和粉彩，不刺眼） */
    private const val S_LIGHT = 0.55f

    /** 暗色模式饱和度（沉稳低饱和，可读性好） */
    private const val S_DARK = 0.40f

    /** 亮色模式亮度 */
    private const val L_LIGHT = 0.82f

    /** 暗色模式亮度 */
    private const val L_DARK = 0.28f

    // ============================ 第二层 · 纯逻辑（无平台依赖） ============================

    /**
     * 基于课程组 ID 计算稳定色相（0°~360°）。
     * hue 种子必须是 groupId（课程身份标识），不能用 course.id（数据库自增主键，随导入顺序漂移）。
     */
    fun stableHue(groupId: String): Float =
        ((groupId.hashCode().toLong() * GOLDEN_ANGLE) % 360f + 360f) % 360f

    /**
     * 主题和谐化色相 — 安全弧公式（spec docs/spec-course-color-harmony-2026-10-18.md §2.2）。
     *
     * 以 anchor（主题 primary / 用户种子色的色相）为原点，色环挖掉两段禁区，
     * 把 [0,240) 均匀映射进剩余两允许弧：
     *   禁区1 [anchor-30°, anchor+30°]   主题保护带 — 课色不与 UI 主色混淆
     *   禁区2 [anchor+150°, anchor+210°] 补色带     — 杜绝「紫配黄」扎眼（M3 同源规则）
     *
     * 纯函数确定性：同 (anchor, groupId) 永远同 hue，故落库值与渲染兜底值同源。
     * 明暗不参与本函数（只切 S/L），hue 跨深浅恒定。
     */
    fun safeArcHue(anchor: Float, groupId: String): Float =
        hueFromT(anchor, seedT(groupId))

    /** safeArcHue 的 t 种子: hashCode → [0,240) 均匀映射（两段 120° 允许弧的参数域）。 */
    private fun seedT(groupId: String): Int = (groupId.hashCode() and Int.MAX_VALUE) % ARC_UNITS

    /** t ∈ [0,240) → hue: [0,120) 落允许弧A (anchor+30..150)，[120,240) 落允许弧B (anchor+210..330)。 */
    private fun hueFromT(anchor: Float, t: Int): Float {
        val offset = if (t < ARC_UNITS / 2) 30f + t else 210f + (t - ARC_UNITS / 2)
        return (((anchor + offset) % 360f) + 360f) % 360f
    }

    /**
     * 增量避让取色 — 重算/写表路径专用（spec §2.3 触发 1/2/3/6, §2.4 新课补色）。
     *
     * 纯哈希 safeArcHue 在 240° 弧上对任意 13 门课做不到 ≥10° 保证间距
     * （碰撞模拟实证 worst-case ≈0°），故落库时按 taken 列表增量滑位：
     *   ① 以 safeArcHue 产出为起点 t0，沿弧参数 t 每次 +2 扫描整圈；
     *   ② 首个与所有 taken 环距 ≥ minSep 的槽位即返回 → 已放置课永不受影响（钉死性：
     *      taken 只增不改，同前缀重算逐位复现）；
     *   ③ 弧饱和（>~24 门）时无解不抛异常，退化为「与 taken 最小环距最大」的槽位 —
     *      仍在允许弧，只是间距妥协（可见行为：课极多时相邻课色趋近，不撞主题/补色区）。
     *
     * 两允许弧之间的跨弧环距恒 ≥ 60°（禁区几何保证），间距约束只在同弧内起作用。
     * 纯函数确定性：同 (anchor, groupId, taken) 永远同 hue。
     */
    fun resolveHueWithSpacing(
        anchor: Float,
        groupId: String,
        taken: List<Float>,
        minSep: Float = MIN_SEP_DEG
    ): Float {
        val t0 = seedT(groupId)
        if (taken.isEmpty()) return hueFromT(anchor, t0)
        var t = t0
        var bestT = t0
        var bestGap = -1f
        repeat(ARC_UNITS / ARC_STEP_DEG) {
            val hue = hueFromT(anchor, t)
            val gap = taken.minOf { ringDelta(hue, it) }
            if (gap >= minSep) return hue
            if (gap > bestGap) { bestGap = gap; bestT = t }
            t = (t + ARC_STEP_DEG) % ARC_UNITS
        }
        return hueFromT(anchor, bestT)
    }

    /** 环上最短角距 (0..180)。 */
    private fun ringDelta(a: Float, b: Float): Float {
        val d = ((a - b) % 360f + 360f) % 360f
        return if (d > 180f) 360f - d else d
    }

    /**
     * ARGB Int → HSL 色相 (0..360)。无彩度（黑白灰）回 0f — colorToHSL 对灰阶的
     * hue 是未定义噪声，归零避免 anchor 抖动。供三路径统一解析主题 primary 色相。
     *
     * 自实现而非 ColorUtils.colorToHSL：平台实现对纯绿 (00FF00) 返回 hue=0
     * （契约测试实测），且自实现保证 Compose/Canvas/重算三路解析逐位一致。
     */
    fun hueOf(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        if (delta < 0.0001f) return 0f
        val h = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return ((h % 360f) + 360f) % 360f
    }

    /** [hueOf] 的 Color 重载（Compose 路径）。 */
    fun hueOf(color: Color): Float =
        hueOf(android.graphics.Color.rgb(
            (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
        ))

    /**
     * 判定课程是否有用户自定义颜色：color 非空且非哨兵值。
     * 哨兵判定收敛到此单点，为后续铺路。
     *
     * TODO(Phase2): 哨兵值 #FF6750A4 与主题默认紫完全相同，用户主动选紫主题或导入指定此色
     *   会被误判为「未设置」→ 需改为 isCustomColor 布尔字段 + DB migration。
     */
    fun hasCustomColor(course: CourseEntity): Boolean =
        course.color.isNotBlank() && !course.color.equals(SENTINEL_COLOR, ignoreCase = true)

    /**
     * hex-only 版哨兵判定 — 无实体上下文的调用方(改组色 UI 显示组色源)取用,
     * 判定口径与 [hasCustomColor] 完全一致,哨兵值仍收敛到 SENTINEL_COLOR 单点。
     */
    fun hasCustomColorHex(hex: String): Boolean =
        hex.isNotBlank() && !hex.equals(SENTINEL_COLOR, ignoreCase = true)

    /**
     * 明暗探针 — 读 CoursePalette.primary 亮度（原四副本中 isPaletteDark 的唯一收敛点）。
     * 注意: 只能用 CoursePalette（亮=0xFFEADDFF / 暗=0xFF4F378B），不能用 WakeUpColorScheme.primary
     * （亮色=0xFF6750A4 加权亮度 0.38 会被误判为暗色）。
     */
    fun isPaletteDark(p: CoursePalette): Boolean {
        val c = p.primary
        val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return lum < 0.5f
    }

    // ============================ 第二层 · 文字色亮度自适应（决策 D5-13） ============================

    /**
     * BT.601 加权亮度（0f~1f）— 纯函数，权重与人眼感光曲线一致（绿最敏感）。
     * 与 WeekGridWidgetProvider 原 isDarkOn 私有实现同一算法，收敛至此单点。
     */
    fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Compose Color 版本 — 同 BT.601 权重 */
    fun luminance(color: Color): Float =
        0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

    /**
     * 按背景亮度自适应选文字色 — 深色自定义课色上文字必须可读（决策 D5-13）：
     *   深色底（luminance<0.5） → 白字（原固定 onSurface 在浅色主题下是深字，深字压深底不可见）
     *   浅色底+浅色主题         → onSurface（浅色 HSL 默认底 L=0.82 恰好可读，行为不变）
     *   浅色底+暗色主题         → 黑字（暗色主题 onSurface 是浅色，用户自定义亮色课色上不可读）
     * HSL 默认底（亮 0.82 / 暗 0.28）与 colorless 统一底色均落在「行为不变」分支，仅自定义色跨界时切换。
     *
     * @param onSurface 当前主题的 onSurface（Compose 路径传 WakeUpColorScheme.onSurface）
     */
    fun textColorOn(bg: Color, isDark: Boolean, onSurface: Color): Color = when {
        luminance(bg) < 0.5f -> Color.White
        isDark -> Color.Black
        else -> onSurface
    }

    /** Canvas 路径版本（ARGB Int）— 同一决策树，供 WidgetBitmapRenderers 等画布渲染 */
    fun textColorOn(bg: Int, isDark: Boolean, onSurface: Int): Int = when {
        luminance(bg) < 0.5f -> android.graphics.Color.WHITE
        isDark -> android.graphics.Color.BLACK
        else -> onSurface
    }

    // ============================ 第二层 · HSL 转换 ============================

    /** HSL → Compose Color（供 TodayScreen / CourseTableView 的 Compose 路径） */
    fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f   -> Triple(c, x, 0f)
            h < 120f  -> Triple(x, c, 0f)
            h < 180f  -> Triple(0f, c, x)
            h < 240f  -> Triple(0f, x, c)
            h < 300f  -> Triple(x, 0f, c)
            else      -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }

    /** HSL → ARGB Int（供 WeekGridWidgetProvider / WidgetBitmapRenderers 的 Canvas 路径） */
    fun hslToColorInt(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f   -> Triple(c, x, 0f)
            h < 120f  -> Triple(x, c, 0f)
            h < 180f  -> Triple(0f, c, x)
            h < 240f  -> Triple(0f, x, c)
            h < 300f  -> Triple(x, 0f, c)
            else      -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or
            ((r + m).coerceIn(0f, 1f).times(255f).toInt() shl 16) or
            ((g + m).coerceIn(0f, 1f).times(255f).toInt() shl 8) or
            (b + m).coerceIn(0f, 1f).times(255f).toInt()
    }

    // ============================ 第三层 · 平台适配入口 ============================

    /**
     * Compose 路径取色入口（TodayScreen / CourseTableView）。
     * @param neutralColor colorless 统一底色，即当前主题的最浅 surface container（勿从 CoursePalette 取，它无此字段）
     */
    fun pickCourseColorCompose(
        course: CourseEntity,
        isDark: Boolean,
        neutralColor: Color,
        colorless: Boolean = false
    ): Color {
        if (hasCustomColor(course)) {
            runCatching { return Color(android.graphics.Color.parseColor(course.color)) }
        }
        if (colorless) return neutralColor
        val hue = stableHue(course.groupId)
        val s = if (isDark) S_DARK else S_LIGHT
        val l = if (isDark) L_DARK else L_LIGHT
        return hslToColor(hue, s, l)
    }

    /**
     * Canvas 路径取色入口（WeekGridWidgetProvider / WidgetBitmapRenderers）。
     * @param neutralColorInt colorless 统一底色，即当前主题的最浅 surface container 的 Int 值
     */
    fun pickCourseColorInt(
        course: CourseEntity,
        isDark: Boolean,
        neutralColorInt: Int,
        colorless: Boolean = false
    ): Int {
        if (hasCustomColor(course)) {
            runCatching { return android.graphics.Color.parseColor(course.color) }
        }
        if (colorless) return neutralColorInt
        val hue = stableHue(course.groupId)
        val s = if (isDark) S_DARK else S_LIGHT
        val l = if (isDark) L_DARK else L_LIGHT
        return hslToColorInt(hue, s, l)
    }

    // ============================ 第三层 · issue#22 3 态取色入口 ============================
    //
    // 旧 [pickCourseColorCompose]/[pickCourseColorInt] 不动 — 它们的语义是
    // "整门课一个色",被 WidgetContent 之类纯 group-level 渲染路径使用。
    // issue#22 同名课程多地点修复后,WeekGrid/WeekList/Widget/Todo 等渲染
    // 每个 block 独立色的入口走下方 *WithGroupRows 版本。

    /**
     * Compose 路径 · issue#22 3 态取色入口。
     *
     * @param row 目标行
     * @param groupRows 同 groupId 所有行(含 row 自身) — 用于 AUTO 模式按行号顺序算 hue
     * @param neutralColor colorless 统一底色
     */
    fun pickCourseColorComposeWithGroupRows(
        row: CourseEntity,
        groupRows: List<CourseEntity>,
        isDark: Boolean,
        neutralColor: Color,
        colorless: Boolean = false
    ): Color {
        return when (row.colorMode) {
            CourseColorMode.CUSTOM -> runCatching {
                Color(android.graphics.Color.parseColor(row.color))
            }.getOrElse { neutralColor }
            CourseColorMode.AUTO -> {
                if (colorless) return neutralColor
                val hue = GoldenAngleColor.forRow(row, groupRows, groupSourceColorHex(groupRows))
                val (s, l) = GoldenAngleColor.hsl(hue, isDark)
                hslToColor(hue, s, l)
            }
            else -> {  // GROUP(0) 或未知
                if (hasCustomColor(row)) {
                    runCatching { return Color(android.graphics.Color.parseColor(row.color)) }
                }
                if (colorless) return neutralColor
                val hue = stableHue(row.groupId)
                val s = if (isDark) S_DARK else S_LIGHT
                val l = if (isDark) L_DARK else L_LIGHT
                hslToColor(hue, s, l)
            }
        }
    }

    /** Canvas 路径 · issue#22 3 态取色入口 */
    fun pickCourseColorIntWithGroupRows(
        row: CourseEntity,
        groupRows: List<CourseEntity>,
        isDark: Boolean,
        neutralColorInt: Int,
        colorless: Boolean = false
    ): Int {
        return when (row.colorMode) {
            CourseColorMode.CUSTOM -> runCatching {
                android.graphics.Color.parseColor(row.color)
            }.getOrElse { neutralColorInt }
            CourseColorMode.AUTO -> {
                if (colorless) return neutralColorInt
                val hue = GoldenAngleColor.forRow(row, groupRows, groupSourceColorHex(groupRows))
                val (s, l) = GoldenAngleColor.hsl(hue, isDark)
                hslToColorInt(hue, s, l)
            }
            else -> {
                if (hasCustomColor(row)) {
                    runCatching { return android.graphics.Color.parseColor(row.color) }
                }
                if (colorless) return neutralColorInt
                val hue = stableHue(row.groupId)
                val s = if (isDark) S_DARK else S_LIGHT
                val l = if (isDark) L_DARK else L_LIGHT
                hslToColorInt(hue, s, l)
            }
        }
    }

    /** 组色源 — 同 groupId 内 colorMode=GROUP 中 id 最小的行的 color */
    fun groupSourceColorHex(groupRows: List<CourseEntity>): String {
        val source = groupRows
            .filter { it.colorMode == CourseColorMode.GROUP }
            .minByOrNull { it.id }
            ?: groupRows.minByOrNull { it.id }
        return source?.color ?: ""
    }
}

/**
 * 自动色算法 — golden angle 137.508° 在 HSV 色环上按 row 序号推进。
 * 不落库: 仅用 row 自身 id + 同组行 id 顺序 + 组色源色相 算色相,确定性重算。
 * (issue#22 同名课程多地点修复的 AUTO 模式取色)
 */
object GoldenAngleColor {

    private const val GOLDEN_ANGLE_DEG = 137.508f
    private const val SATURATION_LIGHT = 0.55f
    private const val SATURATION_DARK = 0.40f
    private const val LIGHTNESS_LIGHT = 0.82f
    private const val LIGHTNESS_DARK = 0.28f

    /**
     * @param row 目标行(只读,用于取 id)
     * @param groupRows 同 groupId 的所有行(含 row 自身)
     * @param groupSourceColorHex 组色源行(通常是 colorMode=GROUP 中 id 最小)的 color 字段值
     */
    fun forRow(row: CourseEntity, groupRows: List<CourseEntity>, groupSourceColorHex: String?): Float {
        val baseHue = parseHexHue(groupSourceColorHex)
        val sorted = groupRows.sortedBy { it.id }
        val idx = sorted.indexOfFirst { it.id == row.id }
        val safeIdx = if (idx < 0) 0 else idx
        return (((baseHue + safeIdx * GOLDEN_ANGLE_DEG) % 360f) + 360f) % 360f
    }

    /** 给定 hue 返回 (saturation, lightness) — 由 isDark 决定亮/暗主题 */
    fun hsl(hue: Float, isDark: Boolean): Pair<Float, Float> =
        (if (isDark) SATURATION_DARK else SATURATION_LIGHT) to
            (if (isDark) LIGHTNESS_DARK else LIGHTNESS_LIGHT)

    private fun parseHexHue(hex: String?): Float {
        if (hex.isNullOrBlank()) return 0f
        return runCatching {
            val argb = android.graphics.Color.parseColor(hex)
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(argb, hsl)
            hsl[0]
        }.getOrDefault(0f)
    }
}
