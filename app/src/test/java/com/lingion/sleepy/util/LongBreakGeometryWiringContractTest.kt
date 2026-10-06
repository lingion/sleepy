package com.lingion.sleepy.util

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交叉验证 blocker #1 接线锁 (2026-10-06, 先例: ImportDraftWiringContractTest):
 * 长课间折权必须满足两表模型, 否则回归:
 *  ① slotWeights==null(无占位行, 最常见) 时也必须按均匀表折叠 —— 旧代码
 *     `?: return@run null` 让折权被 yOfRows 的兜底表整体丢弃 = 开关开了没效果;
 *  ② 行边界坐标 (yOfRows) 读 boundaryWeights, 卡片/表头内容高读 naturalWeights —
 *     空隙不进卡片高度, 空白落在课程卡之后;
 *  ③ ConflictCard 高度经 contentSpanDpOf(natural), 位置仍经 spanDpOf(边界)。
 */
class LongBreakGeometryWiringContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    @Test
    fun uniform_weights_are_folded_even_without_placeholder_rows() {
        val src = loadSource("ui/component/CourseTableView.kt")
        assertTrue(
            "naturalWeightsBase 必须在 slotWeights=null 时回退均匀表 (blocker #1)",
            Regex("""naturalWeightsBase\s*:\s*List<Float>\s*=\s*renderPlan\.slotWeights\s*\?:\s*List\(renderSlots\.size\)""")
                .containsMatchIn(src),
        )
    }

    @Test
    fun yOfRows_reads_boundary_table_and_cards_read_natural_table() {
        val src = loadSource("ui/component/CourseTableView.kt")
        val yBody = src.substringAfter("fun yOfRows(r: Float): Dp {").substringBefore("fun naturalSpan")
        assertTrue("yOfRows 必须读 boundaryWeights", yBody.contains("val ws = boundaryWeights"))
        assertTrue(
            "行内小数推进必须用 natural 权重 (空隙贴行尾, 禁摊进行内)",
            Regex("""naturalWeights\.getOrNull\(full\)""").containsMatchIn(yBody),
        )
        val rowH = src.substringAfter("fun rowHeightAt(i: Int)").substringBefore("\n")
        assertTrue("rowHeightAt 必须读 naturalWeights", rowH.contains("naturalWeights"))
    }

    @Test
    fun conflict_card_heights_thread_content_span() {
        val src = loadSource("ui/component/ConflictCard.kt")
        assertTrue(
            "ConflictCard 缺 contentSpanDpOf(natural) 高度通道",
            Regex("""val heightSpan = contentSpanDpOf \?: spanDpOf""").containsMatchIn(src),
        )
        assertTrue(
            "cardHOf 高度必须走 contentSpanDpOf ?: spanDpOf",
            Regex("""\(contentSpanDpOf \?: spanDpOf\)\?\.invoke\(g\.first""").containsMatchIn(src),
        )
        // 位置通道不能被替换掉 (y/clusterYOffset/cardYOf 仍用 spanDpOf)
        assertTrue("位置仍须走 spanDpOf(边界)", Regex("""val y = spanDpOf\?\.invoke""").containsMatchIn(src))
        val grid = loadSource("ui/component/CourseTableView.kt")
        assertTrue(
            "网格簇调用位必须同时透传 spanDpOf 与 contentSpanDpOf",
            Regex("""contentSpanDpOf = \{ from, to -> contentSpanDpOf\(from, to\) \}""").containsMatchIn(grid),
        )
    }
}
