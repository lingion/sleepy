package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程配色和谐化契约测试（spec: docs/architecture/spec-course-color-harmony-2026-10-18.md §6 测试 1/2/3′）。
 *
 * 锁的不变量：
 *  1. 禁区不变量 — 对任意 anchor × 任意 groupId，safeArcHue / resolveHueWithSpacing
 *     产出与 anchor 的环上距恒在 (30°,150°)∪(210°,330°) 允许弧内；
 *     违反 = 用户肉眼可见的「紫配黄扎眼」或「课色与 UI 主色混淆」回归。
 *  2. 确定性 — 同输入重复计算逐位相等（落库值/兜底值同源的前提）。
 *  3. 间距保证 — 增量避让后 13 门课两两环距 ≥ 10°（红绿验证：MIN_SEP 传 90° 必须挂满）。
 *  4. 钉死性 — 已有课 hue 不因新课加入而改变（taken 只增不改的语义前提）。
 *  5. 饱和退化 — 超过弧容量(>24门)不抛异常、仍在允许弧。
 */
class CourseColorHarmonyTest {

    private fun ringDelta(a: Float, b: Float): Float {
        val d = ((a - b) % 360f + 360f) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun zoneDist(hue: Float, anchor: Float): Float =
        ((hue - anchor) % 360f + 360f) % 360f

    private fun assertInAllowedArc(hue: Float, anchor: Float, ctx: String) {
        // spec §2.2 允许带为半开 [30,150)∪[210,330)（+30° 边界本身已可辨，归允许带）
        val dist = zoneDist(hue, anchor)
        val inAllowedA = dist >= 30f && dist < 150f
        val inAllowedB = dist >= 210f && dist < 330f
        assertTrue("$ctx hue=$hue anchor=$anchor dist=$dist 落入禁区", inAllowedA || inAllowedB)
    }

    private val groupIds: List<String> = (1..200).map { i ->
        java.util.UUID.nameUUIDFromBytes("course-$i".toByteArray()).toString()
    }

    // ───────── 不变量 1: 禁区 ─────────

    @Test
    fun safeArcHue_neverFallsIntoForbiddenZones() {
        var anchor = 0f
        while (anchor < 360f) {
            for (gid in groupIds) {
                val hue = CourseColorUtil.safeArcHue(anchor, gid)
                assertInAllowedArc(hue, anchor, "safeArcHue gid=$gid")
            }
            anchor += 15f
        }
    }

    @Test
    fun resolveHue_neverFallsIntoForbiddenZones_evenWithTakenCrowding() {
        // 30 门课顺序 assign（超弧容量触发 fallback），全程每个产出必须在允许弧
        val anchor = 264f
        val taken = mutableListOf<Float>()
        for (gid in groupIds.take(30)) {
            val hue = CourseColorUtil.resolveHueWithSpacing(anchor, gid, taken)
            assertInAllowedArc(hue, anchor, "resolve gid=$gid")
            taken += hue
        }
    }

    // ───────── 不变量 2: 确定性 ─────────

    @Test
    fun safeArcHue_isDeterministic() {
        for (gid in groupIds.take(50)) {
            assertEquals(CourseColorUtil.safeArcHue(264f, gid),
                CourseColorUtil.safeArcHue(264f, gid), 0f)
        }
    }

    @Test
    fun resolveHue_isDeterministic() {
        val taken = groupIds.take(5).map { CourseColorUtil.safeArcHue(120f, it) }
        for (rep in 0 until 3) {
            assertEquals(CourseColorUtil.resolveHueWithSpacing(120f, groupIds[42], taken),
                CourseColorUtil.resolveHueWithSpacing(120f, groupIds[42], taken), 0f)
        }
    }

    @Test
    fun safeArcHue_outputRangeIsAlwaysNormalized() {
        for (anchor in floatArrayOf(0f, 90f, 180f, 270f, 350f)) {
            for (gid in groupIds) {
                val hue = CourseColorUtil.safeArcHue(anchor, gid)
                assertTrue("hue=$hue 越界", hue >= 0f && hue < 360f)
            }
        }
    }

    // ───────── 不变量 3: 间距 ─────────

    @Test
    fun incrementalAssign_13Courses_keepMinimumSeparation() {
        val anchor = 264f
        val assigned = mutableListOf<Float>()
        for (gid in groupIds.take(13)) {
            assigned += CourseColorUtil.resolveHueWithSpacing(anchor, gid, assigned)
        }
        for (i in assigned.indices) {
            for (j in i + 1 until assigned.size) {
                assertTrue(
                    "第 $i/$j 门课 hue 间距不足: ${assigned[i]} vs ${assigned[j]}",
                    ringDelta(assigned[i], assigned[j]) >= 10f
                )
            }
        }
    }

    @Test
    fun emptyAnchor_alsoSeparates() {
        // 第二 anchor 交叉验证间距不变量不靠运气
        val anchor = 45f
        val assigned = mutableListOf<Float>()
        for (gid in groupIds.take(13)) {
            assigned += CourseColorUtil.resolveHueWithSpacing(anchor, gid, assigned)
        }
        for (i in assigned.indices) {
            for (j in i + 1 until assigned.size) {
                assertTrue(ringDelta(assigned[i], assigned[j]) >= 10f)
            }
        }
    }

    @Test
    fun spacingAssertionIsNotVacuous_minSep90MustFailToHold() {
        // 红绿验证: 240° 弧容不下 13 门课两两 ≥90°（每弧至多 2 点），
        // 若此测试反而「间距全 ≥90」= 上面的 10° 断言根本没在约束什么。
        val anchor = 264f
        val assigned = mutableListOf<Float>()
        for (gid in groupIds.take(13)) {
            assigned += CourseColorUtil.resolveHueWithSpacing(anchor, gid, assigned, minSep = 90f)
        }
        val anyViolating = assigned.indices.any { i ->
            (i + 1 until assigned.size).any { j -> ringDelta(assigned[i], assigned[j]) < 90f }
        }
        assertTrue("minSep=90° 下必存在违反对（否则 10° 断言是空断言）", anyViolating)
    }

    // ───────── 不变量 4: 钉死性 ─────────

    @Test
    fun existingHues_arePinned_whenNewCourseJoins() {
        val anchor = 264f
        val first10 = mutableListOf<Float>()
        for (gid in groupIds.take(10)) {
            first10 += CourseColorUtil.resolveHueWithSpacing(anchor, gid, first10)
        }
        val with11 = first10.toMutableList()
        with11 += CourseColorUtil.resolveHueWithSpacing(anchor, groupIds[10], with11)
        // 前 10 门课 hue 一位不变
        for (i in 0 until 10) assertEquals(first10[i], with11[i], 0f)
    }

    // ───────── 不变量 5: 饱和退化安全 ─────────

    @Test
    fun overSaturatedArc_doesNotThrow_andStaysInArc() {
        val anchor = 180f
        val taken = mutableListOf<Float>()
        for (gid in groupIds.take(40)) {
            val hue = CourseColorUtil.resolveHueWithSpacing(anchor, gid, taken)
            assertInAllowedArc(hue, anchor, "over-sat gid=$gid")
            taken += hue
        }
    }

    // ───────── hueOf 解析（JVM 安全自实现） ─────────

    @Test
    fun hueOf_parsesArgbIntToHue() {
        assertEquals(0f, CourseColorUtil.hueOf(0xFFFF0000.toInt()), 0.01f)
        assertEquals(120f, CourseColorUtil.hueOf(0xFF00FF00.toInt()), 0.01f)
        assertEquals(240f, CourseColorUtil.hueOf(0xFF0000FF.toInt()), 0.01f)
        val purpleHue = CourseColorUtil.hueOf(0xFF6750A4.toInt())
        assertTrue("6750A4 应在紫色区 240..300, 实测 $purpleHue", purpleHue in 240f..300f)
        assertEquals(0f, CourseColorUtil.hueOf(0xFF808080.toInt()), 0f)
        assertEquals(60f, CourseColorUtil.hueOf(0xFFFFFF00.toInt()), 0.01f)
    }
}
