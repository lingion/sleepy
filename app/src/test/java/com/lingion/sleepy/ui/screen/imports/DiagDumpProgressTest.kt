package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排查全量包导出进度模型 (2026-09-21 用户: 导出必须有原子步骤进度+百分比,
 * 禁止无止境"正在生成"黑箱)。
 *
 * 锁: 步骤数/顺序=管线真实顺序、百分比单调、终态=100%、超时也推进。
 */
class DiagDumpProgressTest {

    @Test
    fun pipeline_declares_nine_atomic_stages_in_real_capture_order() {
        // 顺序 = JwImportActivity.exportDiagnosticDump 的真实管线:
        // JS 现抓三段 → 网络快照 → 网络重放 → 资源重取 → Cookie → 打包 → 保存分享
        assertEquals(
            listOf(
                "DomInventory", "Storage", "Links",
                "NetworkSnapshot", "NetworkReplay", "ResourceReplay",
                "Cookies", "ZipAssembly", "SaveShare",
            ),
            DumpStage.entries.map { it.name }
        )
    }

    @Test
    fun starting_stage_reports_zero_percent() {
        val p = DiagDumpProgress(DumpStage.DomInventory, DumpStage.entries.indexOf(DumpStage.DomInventory))
        assertEquals(0, p.percent)
    }

    @Test
    fun percent_is_monotonic_across_stage_advancement() {
        var last = -1
        DumpStage.entries.forEachIndexed { index, stage ->
            val begin = DiagDumpProgress(stage, index)
            val done = DiagDumpProgress(stage, index + 1)
            assertTrue("begin($stage)=$begin 不得倒退", begin.percent >= last)
            assertTrue("done($stage)=$done 必须不小于 begin", done.percent >= begin.percent)
            last = done.percent
        }
    }

    @Test
    fun final_stage_completion_reports_100_percent() {
        val last = DumpStage.entries.last()
        val done = DiagDumpProgress(last, DumpStage.entries.size)
        assertEquals(100, done.percent)
    }

    @Test
    fun percent_never_exceeds_100_even_if_counter_overruns() {
        // 防御: 计数器被重复推进时百分比钳在 100, 不出现 120% 这类荒唐值
        val p = DiagDumpProgress(DumpStage.SaveShare, DumpStage.entries.size + 5)
        assertEquals(100, p.percent)
    }

    @Test
    fun zero_total_is_guarded_against_division_by_zero() {
        assertEquals(0, DiagDumpProgress(DumpStage.DomInventory, 0, totalCount = 0).percent)
    }
}
