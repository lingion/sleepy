package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.CourseColorUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * step-4 契约测试（spec docs/architecture/spec-course-color-harmony-2026-10-18.md §2.4/§6 测试7）:
 *   1. 认亲 — 二次导入同名课 → groupId 继承 → 持久 hue 直接命中 → 颜色跨导入不变
 *   2. 新课只增不改 — 增量 hue 规划永不移动既有组的色相（钉死性, spec §2.3）
 *   3. CLASSIC 档零写入 — anchor=null 时规划产出空表（升级用户 course_colors 保持空）
 *
 * 认亲 key 采用仓库既有身份口径 courseName 规范化（trim/折叠空格/小写,
 * NewTableImport.normalizedImportGroups 先例）, 严于 spec §2.4 的原文精确匹配表述:
 * 同一身份规则单点, 两处导入路径（教务/文件）行为一致。
 */
class CourseColorKinshipTest {

    private fun row(name: String, gid: String = UUID.randomUUID().toString()) = CourseEntity(
        id = 0, groupId = gid, tableId = 1, courseName = name,
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = ""
    )

    // ───────── 1. 认亲: 同名继承旧 groupId ─────────

    @Test
    fun knownNameHit_inheritsOldGroupId_evenWithFreshGidOnRows() {
        // 第一次导入（无名册）: 每名字一个新 gid
        val first = normalizedImportGroups(listOf(row("高等数学"), row("线性代数")))
        val known = knownNameToGroupId(first)
        // 删表重导入: 解析端给的全是新鲜 UUID, 必须被名册接管
        val second = normalizedImportGroups(
            listOf(row("高等数学"), row("线性代数")), known
        )
        assertEquals("同名课必须继承旧 groupId",
            first[0].groupId, second[0].groupId)
        assertEquals(first[1].groupId, second[1].groupId)
        assertNotEquals("不同名课仍各自独立", second[0].groupId, second[1].groupId)
    }

    @Test
    fun kinshipKey_usesExistingNormalization_whitespaceCaseInsensitive() {
        // 仓库身份口径 = trim + 空白折叠 + 小写（不剥空格, "离散数学"≠"离散 数学" 属不同身份）
        val first = normalizedImportGroups(listOf(row(" Discrete  Math ")))
        val known = knownNameToGroupId(first)
        val second = normalizedImportGroups(listOf(row("discrete math")), known)
        assertEquals("大小写/空白差异按仓库身份口径视为同名",
            first[0].groupId, second[0].groupId)
    }

    @Test
    fun unknownName_stillFallsBackToRowGidOrNewUuid() {
        val preset = "preset-gid-1"
        val kept = normalizedImportGroups(listOf(row("新课", gid = preset)))
        assertEquals("无命中时保留行自带 gid（v1.0.61 行为）", preset, kept[0].groupId)
        val fresh = normalizedImportGroups(listOf(row("全新课", gid = "")))
        assertTrue("空 gid → 分配新 UUID", fresh[0].groupId.isNotBlank())
        // 同批同名仍归一组
        val pair = normalizedImportGroups(listOf(row("全新课", gid = ""), row("全新课", gid = "")))
        assertEquals(pair[0].groupId, pair[1].groupId)
    }

    @Test
    fun knownNameToGroupId_firstWinsOnNameCollision() {
        val rows = listOf(row("复变函数", gid = "gid-A"), row("复变函数", gid = "gid-B"))
        val known = knownNameToGroupId(rows)
        assertEquals("同名冲突取首个（getAll 按 id 升序 → 确定性）", "gid-A", known["复变函数"])
    }

    // ───────── 2. 增量 hue 规划: 新课只增不改（spec §6 测试7 色相层） ─────────

    @Test
    fun secondImport_inheritedGroups_hitPersistedHue_newCourseOnlyAppends() {
        val anchor = 275f // 紫主题
        val firstRows = listOf(row("高等数学"), row("线性代数"), row("大学英语"))
        val first = normalizedImportGroups(firstRows)
        val hues1 = CourseColorUtil.planHuesForGroups(
            anchor, emptyList(), first.map { it.groupId }.distinct()
        )
        assertEquals(3, hues1.size)

        // 重导入 + 一门真新课
        val known = knownNameToGroupId(first)
        val secondRows = firstRows.map { row(it.courseName) } + row("概率论")
        val second = normalizedImportGroups(secondRows, known)
        val newGids = second.map { it.groupId }.distinct().filter { it !in hues1 }
        assertEquals("同名课全部命中旧组, 仅新课待补色", 1, newGids.size)

        val hues2 = CourseColorUtil.planHuesForGroups(
            anchor, hues1.values.toList(), newGids
        )
        // 钉死性 = 规划只产新课, 旧组不在写集合内（taken 只增不改, spec §2.3）
        assertEquals("只规划新课, 旧组 hue 零移动", setOf(newGids[0]), hues2.keys)
        // 新课与所有既有 hue 保证 ≥10°（满足性区间内）
        val all = hues1.values + hues2.values
        for (i in all.indices) for (j in i + 1 until all.size) {
            val d = ringDist(all[i], all[j])
            assertTrue("hue 间距 ≥10°: $d", d >= 10f)
        }
        // 全部仍在允许弧（禁区不变量）
        for (h in all) assertTrue("hue ∉ 禁区: anchor=$anchor h=$h", inAllowedArc(anchor, h))
    }

    @Test
    fun planHuesForGroups_classicNull_anchor_writesNothing() {
        // CLASSIC 档 resolveAnchor=null → 空规划, course_colors 零写入
        assertTrue(CourseColorUtil.planHuesForGroups(null, emptyList(), listOf("a", "b")).isEmpty())
    }

    @Test
    fun planHuesForGroups_isDeterministic_sameInputSameOutput() {
        val gids = (1..8).map { "g$it" }
        val p1 = CourseColorUtil.planHuesForGroups(200f, emptyList(), gids)
        val p2 = CourseColorUtil.planHuesForGroups(200f, emptyList(), gids)
        assertEquals(p1, p2)
    }

    // ───────── 端到端: 两次导入渲染色逐值相等（persistedHue 注入闭环） ─────────

    @Test
    fun renderColor_identicalAcrossReimport_viaKinshipAndPersistedHue() {
        val anchor = 275f
        val neutral = androidx.compose.ui.graphics.Color(0xFF00FF00)
        val first = normalizedImportGroups(listOf(row("机械设计基础")))
        val hues = CourseColorUtil.planHuesForGroups(anchor, emptyList(), first.map { it.groupId })
        val color1 = CourseColorUtil.pickCourseColorCompose(first[0], false, neutral, false,
            persistedHue = hues[first[0].groupId])
        val second = normalizedImportGroups(
            listOf(row("机械设计基础")), knownNameToGroupId(first)
        )
        val color2 = CourseColorUtil.pickCourseColorCompose(second[0], false, neutral, false,
            persistedHue = hues[second[0].groupId])
        assertEquals("重导入同课渲染色必须逐 bit 相等", color1, color2)
    }

    // 环距与允许弧判定（与 CourseColorHarmonyTest 同一几何定义, 本地复制保持测试自包含 DAMP）
    private fun ringDist(a: Float, b: Float): Float {
        val d = ((a - b) % 360f + 360f) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun inAllowedArc(anchor: Float, hue: Float): Boolean {
        fun inArc(startDeg: Float, endDeg: Float, h: Float): Boolean {
            val s = ((startDeg % 360f) + 360f) % 360f
            val e = ((endDeg % 360f) + 360f) % 360f
            val d = ((h - s) % 360f + 360f) % 360f
            val span = ((e - s) % 360f + 360f) % 360f
            return d < span
        }
        return inArc(anchor + 30f, anchor + 150f, hue) ||
            inArc(anchor + 210f, anchor + 330f, hue)
    }
}
