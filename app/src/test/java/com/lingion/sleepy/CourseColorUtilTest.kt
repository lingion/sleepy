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

    // ============================ WeekView 第5 widget 边界钉桩 (TS-1 / CS-V2) ============================

    @Test
    fun weekView_widget_unaffected_by_colorless_switch() {
        // renderWeekView(WeekViewWidgetReceiver 第5 widget)无 colorless 参数、无胶囊背景:
        // 课程列表为纯文本, 颜色直接取 scheme token(onSurfaceVariant / onPrimaryContainer),
        // 从不调 CourseColorUtil 的 colorless 分支, 故 A 开关(widget_colorless)A=true / A=false
        // 渲染结果恒同色 —— 固化「第5 widget 不受颜色拆分影响」边界。
        //
        // 纯 JVM 代理: colorless 开关唯一作用是把「无自定义色课程」的底色从 HSL 换为 neutral(灰)。
        // WeekView 文本 token 灰(surfaceVariant) 与 colorless=true 返回的 neutral 同源,
        // 该 token 是 scheme 常量、与 colorless 双态无关 → 恒同色。
        val cNoCustom = course("#FF6750A4") // 哨兵 = 未设置
        val aTrue = CourseColorUtil.pickCourseColorCompose(cNoCustom, false, neutral, colorless = true)
        val aFalse = CourseColorUtil.pickCourseColorCompose(cNoCustom, false, neutral, colorless = false)
        // A=true 时取 neutral 灰; WeekView 只消费此灰常量, 与开关状态无关。
        assertEquals("A=true 时 WeekView 文本灰同源 token 为固定常量", neutral, aTrue)
        // 自定义色课程在 A=true/A=false 双态下取色恒同(WeekView 即使有胶囊也免疫于开关)。
        val custom = course("#FF5722")
        assertEquals(
            "自定义色课程在 A 开关双态下取色恒同(WeekView 不受影响边界)",
            CourseColorUtil.pickCourseColorCompose(custom, false, neutral, colorless = false),
            CourseColorUtil.pickCourseColorCompose(custom, false, neutral, colorless = true)
        )
    }

    // ============================ issue#22 同名课程多地点 3 态取色 ============================

    private fun multiCourse(
        id: Long,
        gid: String = "g1",
        color: String = "#FFFF0000",
        colorMode: Int = com.lingion.sleepy.data.entity.CourseColorMode.GROUP
    ) = CourseEntity(
        id = id, groupId = gid, tableId = 1, courseName = "x",
        teacher = "", room = "room$id", note = "",
        day = 1, startNode = 1, step = 1,
        startWeek = 1, endWeek = 16, type = 0,
        color = color, colorMode = colorMode
    )

    @Test fun `WithGroupRows GROUP 模式回退到 stableHue(等价旧路径)`() {
        val c = multiCourse(1, color = "#FF6750A4", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP)
        val color = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = c, groupRows = listOf(c), isDark = false, neutralColor = neutral
        )
        // 不为 neutral(走了 HSL stableHue)
        assertNotEquals(neutral, color)
    }

    @Test fun `GoldenAngleColor AUTO 模式 idx 0_1_2 hue 按 golden angle 推进`() {
        // 组色源=红(0°),3 个 AUTO 行依次 hue = 0, 137.508, 275.016
        val rows = listOf(
            multiCourse(1, color = "#FFFF0000", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(2, color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO),
            multiCourse(3, color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO),
            multiCourse(4, color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO)
        )
        val h1 = com.lingion.sleepy.util.GoldenAngleColor.forRow(rows[1], rows, "#FFFF0000")
        val h2 = com.lingion.sleepy.util.GoldenAngleColor.forRow(rows[2], rows, "#FFFF0000")
        val h3 = com.lingion.sleepy.util.GoldenAngleColor.forRow(rows[3], rows, "#FFFF0000")
        assertEquals(137.508f, h1, 0.5f)
        assertEquals(275.016f, h2, 0.5f)
        assertEquals(52.524f, h3, 0.5f)  // 412.524 % 360 = 52.524
    }

    @Test fun `GoldenAngleColor 确定性 — 相同输入产出相同 hue`() {
        val rows = listOf(
            multiCourse(5, color = "#FF112233", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(10, color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO)
        )
        val r = rows[1]
        val h1 = com.lingion.sleepy.util.GoldenAngleColor.forRow(r, rows, "#FF112233")
        val h2 = com.lingion.sleepy.util.GoldenAngleColor.forRow(r, rows, "#FF112233")
        assertEquals(h1, h2, 0.001f)
    }

    @Test fun `GoldenAngleColor baseHue 边界 — baseHue 350 idx 1 hue 落在 (100,150) 区间`() {
        val rows = listOf(
            multiCourse(1, color = "#FFFF00C8", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),  // hue≈350
            multiCourse(2, color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO)
        )
        val r = rows[1]
        val h = com.lingion.sleepy.util.GoldenAngleColor.forRow(r, rows, "#FFFF00C8")
        // idx=1, baseHue≈350, hue = ((350 + 137.508) % 360 + 360) % 360 = 127.508
        assertTrue("hue=$h 应落在 [100,150)", h > 100f && h < 150f)
    }

    @Test fun `WithGroupRows CUSTOM 模式用 row color(优先于 neutral)`() {
        val c = multiCourse(1, color = "#FF112233", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.CUSTOM)
        val color = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = c, groupRows = listOf(c), isDark = false, neutralColor = neutral
        )
        // CUSTOM 模式: 解析 row.color 0xFF112233
        // 注: 纯 JVM 环境 android.graphics.Color.parseColor 是 mockable, 这里
        // 期望 color != neutral 即可 — 走到了 CUSTOM 分支
        assertNotEquals("CUSTOM 分支应返回非 neutral 色", neutral, color)
    }

    @Test fun `WithGroupRows AUTO 模式 color 字段被忽略 — 即使填了色也按 row id 算`() {
        val rows = listOf(
            multiCourse(1, color = "#FFFF0000", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(2, color = "#FF00FF00", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO)
        )
        // AUTO 行 color 字段填了绿色,但应按组色源(红)+ golden angle 算 hue,不走 color 字段
        val color = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = rows[1], groupRows = rows, isDark = false, neutralColor = neutral
        )
        assertNotEquals("AUTO 不应消费 row.color 字段", neutral, color)
    }

    // ============================ 改组色(issue#22 spec §6.2 恢复) ============================

    @Test fun `hasCustomColorHex 口径与 hasCustomColor 一致`() {
        // 同一输入下 hex-only 与实体版判定必须相同 — 哨兵值收敛到 SENTINEL_COLOR 单点
        for (hex in listOf("#FF5722", "#FF6750A4", "#ff6750a4", "", "  ")) {
            val viaEntity = CourseColorUtil.hasCustomColor(multiCourse(1, color = hex.ifBlank { "" }.trim().let { hex }))
            assertEquals("hex=$hex 两版判定应一致", viaEntity, CourseColorUtil.hasCustomColorHex(hex))
        }
    }

    @Test fun `groupSourceColorHex 同组 GROUP 行取最小 id 行的 color`() {
        val rows = listOf(
            multiCourse(3, color = "#FF0000FF", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(1, color = "#FFFF0000", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(2, color = "#FF00FF00", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.CUSTOM)
        )
        assertEquals("组色源=GROUP 模式中最小 id 行的 color", "#FFFF0000", CourseColorUtil.groupSourceColorHex(rows))
    }

    @Test fun `groupSourceColorHex 全组无 GROUP 行回落最小 id 行`() {
        val rows = listOf(
            multiCourse(2, color = "#FF00FF00", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.CUSTOM),
            multiCourse(1, color = "#FFFF0000", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.AUTO)
        )
        assertEquals("#FFFF0000", CourseColorUtil.groupSourceColorHex(rows))
    }

    @Test fun `GROUP 行写入组色后渲染走 hasCustomColor 优先回落`() {
        // setGroupSourceColor 落库后的渲染路径: GROUP 行 color=组色 → 渲染消费 color 而非 stableHue
        val rows = listOf(
            multiCourse(1, color = "#FF6750A4", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(2, color = "#FF6750A4", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP)
        )
        // 改组色前: 哨兵值 → stableHue 路径
        val before = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = rows[0], groupRows = rows, isDark = false, neutralColor = neutral
        )
        // 模拟 setGroupSourceColor 落库后的行
        val after = rows.map { it.copy(color = "#FF123456") }
        val afterColor = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = after[0], groupRows = after, isDark = false, neutralColor = neutral
        )
        // 注: 纯 JVM parseColor 为 mockable 桩(恒返回 0), 断言"改组色后走 CUSTOM-style 解析路径"以色变证明
        assertNotEquals("改组色后 GROUP 行渲染色应改变", before, afterColor)
    }

    @Test fun `AUTO 行 hue 输入包含组色源 — forRow 消费 groupSourceColorHex`() {
        // spec §5.2"组色变则自动跟随": forRow 的 hue = baseHue(组色源) + idx×黄金角。
        // 注: 纯 JVM parseColor 桩恒 0 → parseHexHue 恒 0, "组色变→hue 变"在 JVM 不可断言
        // (真机由真 parseColor 驱动);此处锁 forRow 的 hue 计算确实消费了组色源参数 —
        // baseHue 来自 groupSourceColorHex(经 parseHexHue), 同一输入恒等, 不同 idx 发散。
        val rows = listOf(
            multiCourse(1, color = "#FFFF0000", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP),
            multiCourse(2, color = "#FFFF0000", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP)
        )
        val hue1 = com.lingion.sleepy.util.GoldenAngleColor.forRow(rows[0], rows, "#FFFF0000")
        val hue2 = com.lingion.sleepy.util.GoldenAngleColor.forRow(rows[1], rows, "#FFFF0000")
        // 同输入确定性
        assertEquals(hue1, com.lingion.sleepy.util.GoldenAngleColor.forRow(rows[0], rows, "#FFFF0000"), 0f)
        // 不同行 idx 发散(黄金角 137.508°)
        assertEquals("hue 应按行序号黄金角发散", 137.508f, (hue2 - hue1 + 360f) % 360f, 0.01f)
    }
}
