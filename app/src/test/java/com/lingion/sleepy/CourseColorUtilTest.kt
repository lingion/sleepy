package com.lingion.sleepy

import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.CourseColorUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM test for CourseColorUtil decision tree (TS-1).
 *
 * 覆盖四块:
 *   1. stableHue — 纯函数:同 groupId 恒同色、值域 [0,360)
 *   2. hasCustomColor — 哨兵色 #FF6750A4 与空串判「未设置」、非哨兵判「自定义」(大小写不敏感)
 *   3. 4 态矩阵 — {自定义×无色} 四组合:自定义色在 colorless=true 下仍优先不被覆盖
 *   4. 自定义色优先 — hasCustomColor 在 colorless 判定之前短路返回
 *
 * 注:纯 JVM 环境 android.graphics.Color.parseColor 为 mockable 桩返回 0,
 * 故「自定义分支」的返回色用 !=neutral 断言(而非精确 ARGB),以证分支走向而非具体色值。
 * 使用 Color.equals() 而非 toArgb() 以兼容纯 JVM 环境。
 */
class CourseColorUtilTest {

    private val neutral = Color(0xFF00FF00) // 哨兵灰底,与 HSL/自定义分支皆可区分

    private fun course(color: String, groupId: String = "grp-A") = CourseEntity(
        id = 1L,
        groupId = groupId,
        tableId = 1L,
        courseName = "高等数学",
        day = 1,
        startNode = 1,
        step = 2,
        startWeek = 1,
        endWeek = 16,
        color = color
    )

    // ============================ stableHue ============================

    @Test
    fun stableHue_same_groupId_is_deterministic() {
        assertEquals(CourseColorUtil.stableHue("grp-A"), CourseColorUtil.stableHue("grp-A"), 0f)
    }

    @Test
    fun stableHue_value_in_360_range() {
        val hues = listOf("grp-A", "grp-B", "grp-C", "高等数学", "英语", "物理实验")
        for (g in hues) {
            val h = CourseColorUtil.stableHue(g)
            assertTrue("hue 必须在 [0,360): $g -> $h", h >= 0f && h < 360f)
        }
    }

    // 注:stableHue 的保证是「确定性 + 值域」,不保证不同 groupId 必异色(黄金角模 360 存在哈希碰撞)。

    // ============================ hasCustomColor (哨兵色) ============================

    @Test
    fun hasCustomColor_custom_value_is_true() {
        assertTrue(CourseColorUtil.hasCustomColor(course("#FF5722")))
    }

    @Test
    fun hasCustomColor_sentinel_is_false() {
        // 哨兵色 #FF6750A4 与主题默认紫相同,标记「未设置」
        assertEquals(false, CourseColorUtil.hasCustomColor(course("#FF6750A4")))
    }

    @Test
    fun hasCustomColor_sentinel_case_insensitive_is_false() {
        assertEquals(false, CourseColorUtil.hasCustomColor(course("#ff6750a4")))
    }

    @Test
    fun hasCustomColor_blank_is_false() {
        assertEquals(false, CourseColorUtil.hasCustomColor(course("")))
    }

    // ============================ 4 态矩阵 ============================

    @Test
    fun matrix_custom_without_colorless_returns_custom_branch() {
        val r = CourseColorUtil.pickCourseColorCompose(
            course = course("#FF5722"),
            isDark = false,
            neutralColor = neutral,
            colorless = false
        )
        assertTrue("自定义色分支应返回非 neutral 色", r != neutral)
    }

    @Test
    fun matrix_custom_with_colorless_still_returns_custom_branch() {
        // 核心红线:自定义色在 colorless=true 下仍优先,不被灰底覆盖
        val r = CourseColorUtil.pickCourseColorCompose(
            course = course("#FF5722"),
            isDark = false,
            neutralColor = neutral,
            colorless = true
        )
        assertTrue("自定义色分支应返回非 neutral 色", r != neutral)
    }

    @Test
    fun matrix_noCustom_with_colorless_returns_neutral() {
        val r = CourseColorUtil.pickCourseColorCompose(
            course = course("#FF6750A4"), // 哨兵 = 未设置
            isDark = false,
            neutralColor = neutral,
            colorless = true
        )
        assertEquals("无自定义色且 colorless=true 应返回 neutral 灰底", neutral, r)
    }

    @Test
    fun matrix_noCustom_without_colorless_returns_hsl() {
        val r = CourseColorUtil.pickCourseColorCompose(
            course = course("#FF6750A4"),
            isDark = false,
            neutralColor = neutral,
            colorless = false
        )
        assertTrue("无自定义色且 colorless=false 应返回 HSL 色而非 neutral", r != neutral)
    }

    // ============================ 自定义色优先(四态皆不覆盖) ============================

    @Test
    fun custom_priority_not_overridden_in_any_colorless_state() {
        // 同一自定义课程,colorless 四种开关态返回色一致(皆为自定义分支,不随 colorless 变化)
        val custom = course("#FF5722")
        val rFalse = CourseColorUtil.pickCourseColorCompose(custom, false, neutral, colorless = false)
        val rTrue = CourseColorUtil.pickCourseColorCompose(custom, false, neutral, colorless = true)
        assertEquals("colorless 开关不应改变自定义色的返回值", rFalse, rTrue)
        assertTrue("自定义色应返回非 neutral", rFalse != neutral)
    }

    @Test
    fun same_groupId_default_hsl_is_stable_across_calls() {
        // 同 groupId 的默认 HSL 分支,两次调用取色一致(对齐 stableHue 确定性)
        val a = CourseColorUtil.pickCourseColorCompose(course(""), isDark = false, neutralColor = neutral)
        val b = CourseColorUtil.pickCourseColorCompose(course(""), isDark = false, neutralColor = neutral)
        assertEquals("同 groupId 的 HSL 颜色应稳定", a, b)
    }
}
