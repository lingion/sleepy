package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入恒建作息表接线契约(用户 2026-10-05「功能不统一」2a: 文本/文件导入课表时
 * 也必须创建并绑定对应作息表; 先例: ImportDraftWiringContractTest 源码契约口径)。
 *
 * 锁两点:
 * ① 新建导入: 解析结果无 P 区块(旧格式/粘贴文本)时, 必须以确认页最终
 *   confirmedTimeJson 兜底建作息表并绑定 — 不允许再出现「无 periodTable 就不建」。
 * ② 教务导入: 复用同一新建流程，保留确认数据和解析警告。
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
    fun newImportsUseConfirmedScheduleAndAtomicRepositoryOperation() {
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        val branch = balancedBlock(src, "internal suspend fun applyNewTableImport(")
        assertTrue(branch.contains("importedPeriodTableDraft("))
        assertTrue(branch.contains("confirmedTimeJson = confirmedTimeJson"))
        assertTrue(branch.contains("bindPeriodTableId = bindPeriodTableId"))
        assertTrue(branch.contains("repo.importNewTable("))
        assertTrue(!branch.contains("repo.insertPeriodTable("))
    }

    @Test
    fun academicImportsUseSharedNewTableFlowAndKeepWarningsOnBothPaths() {
        val src = loadSource("ui/screen/imports/JwImportActivity.kt")
        assertTrue(src.contains("applyNewTableImport("))
        assertTrue(src.contains("confirmedSmartConfigJson = Json.encodeToString(configSmartConfig)"))
        val preview = src.substringAfter("stage is Stage.Preview").substringBefore("stage is Stage.ConfigureConfirm")
        val confirm = src.substringAfter("stage is Stage.ConfigureConfirm").substringBefore("stage is Stage.SelectSchool ->")
        assertTrue(preview.contains("R.string.jw_thu_skipped_courses"))
        assertTrue(confirm.contains("R.string.jw_thu_skipped_courses"))
    }

    @Test
    fun newImportTransactionIncludesAllRowsBeforeRefresh() {
        val src = loadSource("data/repository/ScheduleRepository.kt")
        assertTrue(src.contains("override suspend fun atomic(block: suspend () -> Long): Long = db.withTransaction { block() }"))
        val body = src.substringAfter("suspend fun importNewTable(").substringBefore("/** Reminder settings")
        assertTrue(body.indexOf("importNewTableAtomically(") < body.indexOf("onDataChanged()"))
    }
}
