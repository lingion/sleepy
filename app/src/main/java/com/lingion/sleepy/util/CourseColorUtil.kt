package com.lingion.sleepy.util

import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.entity.CourseEntity

/**
 * 课程底色单一事实来源 — 三层结构（决策 D3）
 *
 * 收敛原来分散在 TodayScreen / CourseTableView / WeekGridWidgetProvider /
 * WidgetBitmapRenderers 四处的课程配色私有副本，统一为一份逻辑：
 *
 * 决策树（所有入口 100% 同源）：
 *   ① 用户自定义颜色（color 非空且非哨兵值）→ 直接返回，colorless 不覆盖手动设色
 *   ② colorless=true → 返回中性灰（surfaceVariant，与网格线同色保持一致）
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
     * 判定课程是否有用户自定义颜色：color 非空且非哨兵值。
     * 哨兵判定收敛到此单点，为后续铺路。
     *
     * TODO(Phase2): 哨兵值 #FF6750A4 与主题默认紫完全相同，用户主动选紫主题或导入指定此色
     *   会被误判为「未设置」→ 需改为 isCustomColor 布尔字段 + DB migration。
     */
    fun hasCustomColor(course: CourseEntity): Boolean =
        course.color.isNotBlank() && !course.color.equals(SENTINEL_COLOR, ignoreCase = true)

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
     * @param neutralColor colorless 灰底，即 WakeUpColorScheme.surfaceVariant（勿从 CoursePalette 取，它无此字段）
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
     * @param neutralColorInt colorless 灰底，即 scheme.surfaceVariant 的 Int 值
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
}
