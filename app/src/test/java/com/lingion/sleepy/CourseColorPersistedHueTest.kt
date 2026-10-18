package com.lingion.sleepy

import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.entity.CourseColorMode
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * step-3 契约测试（spec docs/spec-course-color-harmony-2026-10-18.md §2.3/§6 测试3′/4/8）:
 *   1. CLASSIC 零变化 — persistedHue=null 时四入口与 v1.0.61 stableHue 路径逐值相等（测试8）
 *   2. persistedHue 注入 — 非 null 时派生档取持久 hue, 明暗只切 S/L 不动 hue（测试3′）
 *   3. resolveAnchor 三档决策 + 三路径 hue 解析一致（测试4）
 *   4. AppPrefs 两新键字面量稳定（纯 JVM 先例: AppPrefsIsolationTest — Context 系行为留给 UI 层测试）
 *
 * 优先级红线不变: 单课自定义色 > colorless > 派生取色（spec §2.1）。
 */
class CourseColorPersistedHueTest {

    private val neutral = Color(0xFF00FF00)

    private fun course(color: String = "#FF6750A4", groupId: String = "grp-A") = CourseEntity(
        id = 1L, groupId = groupId, tableId = 1L, courseName = "高等数学",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = color
    )

    private fun groupRow(
        id: Long, gid: String = "g1", color: String = "",
        mode: Int = CourseColorMode.GROUP
    ) = CourseEntity(
        id = id, groupId = gid, tableId = 1, courseName = "x", teacher = "", room = "r$id",
        note = "", day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16,
        color = color, colorMode = mode
    )

    // ───────── 1. CLASSIC 零变化（spec §7 升级安装逐像素一致的机器证明） ─────────

    @Test
    fun classicPath_nullPersistedHue_equalsStableHue_v1_0_61() {
        for (gid in listOf("grp-A", "grp-B", "英语", "物理实验", "0")) {
            val c = course(groupId = gid)
            // 旧签名（无 persistedHue）与新签名显式 null 必须逐位相等 = 默认参数零行为变化
            val legacy = CourseColorUtil.pickCourseColorCompose(c, false, neutral, false)
            val withNull = CourseColorUtil.pickCourseColorCompose(c, false, neutral, false, persistedHue = null)
            assertEquals("CLASSIC 渲染必须与 stableHue 实时算逐值相等", legacy, withNull)
            // 且确实等于 hslToColor(stableHue, S_LIGHT=0.55, L_LIGHT=0.82) — 常量漂移即挂
            assertEquals(
                "默认档 hue/S/L 钉死 v1.0.61",
                CourseColorUtil.hslToColor(CourseColorUtil.stableHue(gid), 0.55f, 0.82f),
                withNull
            )
        }
    }

    @Test
    fun classicCanvasPath_nullPersistedHue_equalsStableHue() {
        val gid = "grp-X"
        val c = course(groupId = gid)
        val legacy = CourseColorUtil.pickCourseColorInt(c, true, 0xFF112233.toInt(), false)
        val withNull = CourseColorUtil.pickCourseColorInt(c, true, 0xFF112233.toInt(), false, persistedHue = null)
        assertEquals(legacy, withNull)
        assertEquals(CourseColorUtil.hslToColorInt(CourseColorUtil.stableHue(gid), 0.40f, 0.28f), withNull)
    }

    @Test
    fun withGroupRows_classic_unchanged_by_null() {
        val rows = listOf(
            groupRow(1, color = "#FF6750A4", mode = CourseColorMode.GROUP),
            groupRow(2, color = "", mode = CourseColorMode.AUTO)
        )
        val legacy = CourseColorUtil.pickCourseColorComposeWithGroupRows(rows[0], rows, false, neutral)
        val withNull = CourseColorUtil.pickCourseColorComposeWithGroupRows(rows[0], rows, false, neutral, false, persistedHue = null)
        assertEquals(legacy, withNull)
        // AUTO 行同理: 无 persistedHue 时与旧路径逐位一致
        assertEquals(
            CourseColorUtil.pickCourseColorComposeWithGroupRows(rows[1], rows, false, neutral),
            CourseColorUtil.pickCourseColorComposeWithGroupRows(rows[1], rows, false, neutral, false, persistedHue = null)
        )
    }

    // ───────── 2. persistedHue 注入 + 明暗只切 S/L（测试3′） ─────────

    @Test
    fun persistedHue_overrides_stableHue_in_derived_mode() {
        val c = course(groupId = "grp-A")
        val persisted = 100f
        val derived = CourseColorUtil.pickCourseColorCompose(c, false, neutral, false, persistedHue = persisted)
        val classic = CourseColorUtil.pickCourseColorCompose(c, false, neutral, false)
        assertNotEquals("派生档 persistedHue 必须覆盖实时 stableHue", classic, derived)
        assertEquals(CourseColorUtil.hslToColor(persisted, 0.55f, 0.82f), derived)
    }

    @Test
    fun persistedHue_darkFlip_only_SL_changes_hue_constant() {
        val c = course(groupId = "grp-A")
        val persisted = 233f
        val light = CourseColorUtil.pickCourseColorCompose(c, false, neutral, false, persistedHue = persisted)
        val dark = CourseColorUtil.pickCourseColorCompose(c, true, neutral, false, persistedHue = persisted)
        assertEquals(CourseColorUtil.hslToColor(persisted, 0.55f, 0.82f), light)
        assertEquals(CourseColorUtil.hslToColor(persisted, 0.40f, 0.28f), dark)
        // hue 跨深浅恒定：两产出解析回 hue 必须相等（自实现 hueOf 反查;
        // 容差 0.5° = HSL→8bit RGB 量化抖动的物理上限, 非逻辑误差）
        assertEquals(
            CourseColorUtil.hueOf(light), CourseColorUtil.hueOf(dark), 0.5f
        )
    }

    @Test
    fun custom_course_color_still_wins_over_persistedHue() {
        // 决策树优先级红线（spec §2.1 ①）: 单课自定义色永远赢
        val custom = course(color = "#FF5722", groupId = "grp-A")
        val r = CourseColorUtil.pickCourseColorCompose(custom, false, neutral, false, persistedHue = 100f)
        assertNotEquals("自定义色不得被 persistedHue 覆盖", CourseColorUtil.hslToColor(100f, 0.55f, 0.82f), r)
    }

    @Test
    fun colorless_still_wins_over_persistedHue() {
        // 决策树 ②: colorless > 派生取色（spec §2.1）
        val c = course(groupId = "grp-A")
        val r = CourseColorUtil.pickCourseColorCompose(c, false, neutral, true, persistedHue = 100f)
        assertEquals("colorless=true 必须回 neutral, 不消费 persistedHue", neutral, r)
        val ri = CourseColorUtil.pickCourseColorInt(c, false, 0xFF112233.toInt(), true, persistedHue = 100f)
        assertEquals(0xFF112233.toInt(), ri)
    }

    @Test
    fun withGroupRows_AUTO_baseHue_follows_persistedHue_when_present() {
        // 派生档下多地点 AUTO 行: 基色 = 持久 group hue, 行偏移仍黄金角（组色变则自动跟随语义保持）
        val rows = listOf(
            groupRow(1, color = "#FF6750A4", mode = CourseColorMode.GROUP),
            groupRow(2, color = "", mode = CourseColorMode.AUTO),
            groupRow(3, color = "", mode = CourseColorMode.AUTO)
        )
        val persisted = 70f
        val c2 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            rows[1], rows, false, neutral, false, persistedHue = persisted
        )
        val c3 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            rows[2], rows, false, neutral, false, persistedHue = persisted
        )
        // idx1 = 70 + 137.508 = 207.508; idx2 = 70 + 275.016 = 345.016（mod 360）
        assertEquals(CourseColorUtil.hslToColor(207.508f, 0.55f, 0.82f), c2)
        assertEquals(CourseColorUtil.hslToColor(345.016f, 0.55f, 0.82f), c3)
        // GROUP 行本身: persistedHue 即组 hue
        val c1 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            rows[0], rows, false, neutral, false, persistedHue = persisted
        )
        assertEquals(CourseColorUtil.hslToColor(persisted, 0.55f, 0.82f), c1)
    }

    // ───────── 3. resolveAnchor 三档决策 + 三路径一致（测试4） ─────────

    @Test
    fun resolveAnchor_classic_is_null() {
        assertNull(CourseColorUtil.resolveAnchor(AppPrefs.COURSE_COLOR_MODE_CLASSIC, 0xFF6750A4.toInt(), 120f))
    }

    @Test
    fun resolveAnchor_followTheme_uses_theme_primary_hue() {
        val themePurple = 0xFF6750A4.toInt()
        val anchor = CourseColorUtil.resolveAnchor(AppPrefs.COURSE_COLOR_MODE_FOLLOW_THEME, themePurple, -1f)
        assertEquals(CourseColorUtil.hueOf(themePurple), anchor!!, 0.001f)
    }

    @Test
    fun resolveAnchor_custom_uses_seed_but_falls_back_to_theme_when_unset() {
        val themePurple = 0xFF6750A4.toInt()
        assertEquals(300f, CourseColorUtil.resolveAnchor(AppPrefs.COURSE_COLOR_MODE_CUSTOM, themePurple, 300f)!!, 0.001f)
        // seed 未初始化(-1) → 回落主题 hue（升级首进 CUSTOM 无种子不得崩/不得回 0 红）
        assertEquals(CourseColorUtil.hueOf(themePurple),
            CourseColorUtil.resolveAnchor(AppPrefs.COURSE_COLOR_MODE_CUSTOM, themePurple, AppPrefs.COURSE_COLOR_SEED_UNSET)!!, 0.001f)
    }

    @Test
    fun hueOf_compose_and_canvas_paths_agree() {
        // 三路径 anchor 同源契约: Int 版(Widget/重算) 与 Color 版(Compose) 同色输入必同 hue
        val argb = 0xFF6750A4.toInt()
        val compose = Color(0xFF6750A4)
        assertEquals(CourseColorUtil.hueOf(argb), CourseColorUtil.hueOf(compose), 1.5f) // 8bit 量化误差内
        assertTrue("anchor 必须在 [0,360)", CourseColorUtil.hueOf(compose) in 0f..360f)
    }

    // ───────── 4. AppPrefs 新键字面量稳定（纯 JVM 先例） ─────────

    @Test
    fun course_color_mode_keys_are_stable_literals() {
        assertEquals("course_color_mode", AppPrefs.KEY_COURSE_COLOR_MODE)
        assertEquals("course_color_seed_hue", AppPrefs.KEY_COURSE_COLOR_SEED_HUE)
        assertEquals(0, AppPrefs.COURSE_COLOR_MODE_CLASSIC)   // 默认档 = 老行为, 升级零变化
        assertEquals(1, AppPrefs.COURSE_COLOR_MODE_FOLLOW_THEME)
        assertEquals(2, AppPrefs.COURSE_COLOR_MODE_CUSTOM)
        assertEquals(-1f, AppPrefs.COURSE_COLOR_SEED_UNSET, 0f)
    }
}
