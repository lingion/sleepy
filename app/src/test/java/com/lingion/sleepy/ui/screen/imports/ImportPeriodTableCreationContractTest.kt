package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入恒建作息表接线契约(用户 2026-10-05「功能不统一」2a: 文本/文件导入课表时
 * 也必须创建并绑定对应作息表; 先例: ImportDraftWiringContractTest 源码契约口径)。
 *
 * 锁两点:
 * ① ImportAsNew: 解析结果无 P 区块(旧格式/粘贴文本)时, 必须以确认页最终
 *   confirmedTimeJson 兜底建作息表并绑定 — 不允许再出现「无 periodTable 就不建」。
 * ② AppendAsNew: 新建合并课表同样必须建+绑(以 mergedTimeJson 为源)。
 */
class ImportPeriodTableCreationContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    /** 取 marker 起始的花括号配平块(when 分支体)。 */
    private fun balancedBlock(src: String, marker: String): String {
        val start = src.indexOf(marker)
        assertTrue("未找到分支: $marker", start >= 0)
        var depth = 0
        for (i in start until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
        }
        return src.substring(start)
    }

    @Test
    fun `ImportAsNew falls back to creating a period table from confirmedTimeJson`() {
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        val branch = balancedBlock(src, "ImportApplyMode.ImportAsNew -> {")
        assertTrue(
            "ImportAsNew 解析器无 P 区块时必须以 confirmedTimeJson 兜底建作息表 (?: run { insertPeriodTable … })",
            Regex(
                """\?:\s*run\s*\{[\s\S]{0,600}?insertPeriodTable[\s\S]{0,400}?confirmedTimeJson"""
            ).containsMatchIn(branch),
        )
    }

    @Test
    fun `AppendAsNew creates and binds a period table for the merged table`() {
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        val branch = balancedBlock(src, "ImportApplyMode.AppendAsNew -> {")
        assertTrue(
            "AppendAsNew 必须以 mergedTimeJson 建作息表",
            Regex(
                """insertPeriodTable[\s\S]{0,500}?mergedTimeJson"""
            ).containsMatchIn(branch),
        )
        assertTrue(
            "AppendAsNew 的 TimeTableEntity 必须写 periodTableId",
            Regex("""TimeTableEntity\([\s\S]{0,900}?periodTableId\s*=""").containsMatchIn(branch),
        )
    }
}
