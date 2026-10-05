package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手动↔自动同步三入口接线统一契约(用户 2026-10-05「功能不统一」2b;
 * 先例: ImportDraftWiringContractTest 源码契约口径)。
 *
 * 症状复刻: 手动把某节 45→44 分钟再切自动, 多出一节"非标准 44" —
 * 根因是三个入口把 stored smartConfig 传丢了一个:
 * ① EditTableScreen 两处草稿同步被 `effectivePeriodTable != null` 守卫挡掉
 *    未绑定课表的草稿更新;
 * ② PeriodTableEditScreen onRowsChange 只改行不回填 smartConfig →
 *    TimeSlotEditor LaunchedEffect 拿着陈旧 config derive, 覆盖手动行并让
 *    switchToAuto 拿到脱节的 stored 引用。
 * 修 = 三入口都把 stored 传进 resolveAutoPeriodConfig(定点保留/重推断语义不变)。
 */
class EditSyncWiringContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    /** 从 marker 起花括号配平地取回调 lambda 体。 */
    private fun balancedBlock(src: String, marker: String): String {
        val start = src.indexOf(marker)
        assertTrue("未找到: $marker", start >= 0)
        var braceDepth = 0
        var seenOpen = false
        for (i in start until src.length) {
            when (src[i]) {
                '{' -> { braceDepth++; seenOpen = true }
                '}' -> {
                    braceDepth--
                    if (seenOpen && braceDepth == 0) return src.substring(start, i + 1)
                }
            }
        }
        return src.substring(start)
    }

    @Test
    fun `EditTableScreen draft sync no longer gated on bound period table`() {
        val src = loadSource("ui/screen/mine/EditTableScreen.kt")
        val rowsBlock = balancedBlock(src, "onRowsChange = {")
        val cfgBlock = balancedBlock(src, "onSmartConfigChange = {")
        assertFalse(
            "onRowsChange 仍有 effectivePeriodTable != null 守卫 — 未绑定课表草稿不同步",
            Regex("""effectivePeriodTable\s*!=\s*null""").containsMatchIn(rowsBlock),
        )
        assertFalse(
            "onSmartConfigChange 仍有 effectivePeriodTable != null 守卫",
            Regex("""effectivePeriodTable\s*!=\s*null""").containsMatchIn(cfgBlock),
        )
    }

    @Test
    fun `PeriodTableEditScreen backfills smartConfig when manual rows change`() {
        val src = loadSource("ui/screen/mine/PeriodTableEditScreen.kt")
        val block = balancedBlock(src, "onRowsChange = {")
        assertTrue(
            "PeriodTableEditScreen onRowsChange 必须回填 smartConfig(resolveAutoPeriodConfig(rows, stored))",
            Regex("""resolveAutoPeriodConfig\(""").containsMatchIn(block),
        )
    }
}
