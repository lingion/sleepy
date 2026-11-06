package com.lingion.sleepy.ui.screen.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OccurrenceEditor 节次行滑杆改造源码契约 (spec-slider-ghost-appwide-2026-10-29
 * §3.2 第八站点; 先例 DisplaySliderGhostContractTest — loadSource 双根回退)。
 *
 * 锁「除节次行以外一克不动」:
 * 1. save() 校验链逐字保留 (n/count/rows/require 区间连续) — 滑杆只改 UI 层
 *    node/step 字符串态的驱动源, 落库校验零改动;
 * 2. custom HH:mm 两个文本框仍在 (用户裁决只删「连续节数」与「起始节次」的
 *    滑块化替代, 自定义时间通路不动);
 * 3. 域外/非常规回退分支仍引用 occurrence_node/occurrence_step 资源 (删干净 = 假
 *    绿, 回退分支必须存在);
 * 4. 幽灵基线 = remember(course) 打开时刻原值对, 接线只经由 IntRangeSlider 的
 *    initialStart/initialEnd (组件内部判据, 调用点禁手抄 sliderGhostVisible)。
 */
class OccurrenceNodeSliderContractTest {

    private fun loadSource(): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            java.io.File(root, "ui/screen/edit/OccurrenceEditor.kt").takeIf { it.isFile }?.readText()
        } ?: error("Unable to load OccurrenceEditor.kt")

    private val src by lazy { loadSource() }

    @Test fun save_validation_chain_untouched() {
        assertTrue(
            "save() 区间连续校验链必须逐字保留",
            Regex("""require\(n != null && rows\.any \{ it\.node == n \} && count != null && count > 0""").containsMatchIn(src),
        )
        assertTrue(
            "(n until n+count) 全档存在断言必须保留",
            src.contains("(n until n + count).all { value -> rows.any { it.node == value } }"),
        )
    }

    @Test fun custom_time_fields_intact() {
        assertTrue(src.contains("R.string.occurrence_custom_time"))
        // HH:mm 两框 (start/end) 仍在 custom 分支
        assertTrue(Regex("OutlinedTextField\\(\\s*start,").containsMatchIn(src))
        assertTrue(Regex("OutlinedTextField\\(\\s*end,").containsMatchIn(src))
    }

    @Test fun fallback_branch_keeps_both_label_resources() {
        assertTrue("域外回退必须仍引用 occurrence_node", src.contains("R.string.occurrence_node"))
        assertTrue("域外回退必须仍引用 occurrence_step", src.contains("R.string.occurrence_step"))
        // 两资源各只允许出现一次 (else 分支) — slider 分支若还挂着 = 双份 UI
        assertEquals("occurrence_node 只允许在回退分支出现一次", 1, Regex("R\\.string\\.occurrence_node\\b").findAll(src).count())
        assertEquals("occurrence_step 只允许在回退分支出现一次", 1, Regex("R\\.string\\.occurrence_step\\b").findAll(src).count())
    }

    @Test fun ghost_baseline_recomputes_on_course_reopen() {
        assertTrue(
            "幽灵基线必须 = remember(course) 原值对 (重开随实体重算)",
            Regex("""val\s+ghostBaseline\s*=\s*remember\(course\)\s*\{\s*course\.startNode\s+to\s+course\.step""").containsMatchIn(src),
        )
        assertTrue("滑杆必须接 initialStart = 基线首", src.contains("initialStart = ghostBaseline.first"))
        assertTrue("滑杆必须接 initialEnd = 基线区间尾 (start+step-1)",
            src.contains("initialEnd = ghostBaseline.first + ghostBaseline.second - 1"))
        // 判据归组件 — 调用点禁手抄 sliderGhostVisible / SingleSliderGhostMark
        assertTrue("slider 分支禁手抄幽灵判据", !src.contains("sliderGhostVisible(") && !src.contains("SingleSliderGhostMark"))
        assertTrue("滑杆必须挂管线定位锚 testTag(\"occurrence-node-slider\")",
            src.contains("""testTag("occurrence-node-slider")"""))
    }

    @Test fun domain_guard_matches_add_course_parity() {
        // 域判据 = 用户裁决原文: 非常规卡归专门机器管; 标准域 = startNode in 1..maxStd
        assertTrue(Regex("""val\s+standardRange\s*=\s*course\.startNode in 1\.\.maxStd && !course\.isIrregularNode""").containsMatchIn(src))
        assertTrue(Regex("""val\s+maxStd\s*=\s*remember\(table\)\s*\{\s*TimeTableUtils\.maxStandardNode\(table\.timeJson\)""").containsMatchIn(src))
    }
}
