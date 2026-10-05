package com.lingion.sleepy.ui.component

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧式表头隐藏时间行接线契约(先例: ImportDraftWiringContractTest —
 * 仓库无 Robolectric/Compose UI 测试, 声明式接线读源头文件等价于读编译产物):
 *
 * 锁: PeriodHeaderCellContent legacy 分支必须用 `if (!hideTime)` 守卫 slot.timeString,
 * 组件签名与 threeLineWidthDp 必须带 hideTime 入参, CourseTableView 的 changeBus
 * filter 必须监听 KEY_PERIOD_HEADER_HIDE_TIME(否则设置页开关不刷新网格)。
 */
class PeriodHeaderHideTimeContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    @Test
    fun cell_content_guards_legacy_time_string_behind_hideTime() {
        val src = loadSource("ui/component/PeriodHeaderCellContent.kt")
        assertTrue(
            "PeriodHeaderCellContent 签名缺 hideTimeOverride 形参",
            Regex("""hideTimeOverride:\s*Boolean\?\s*=\s*null""").containsMatchIn(src),
        )
        assertTrue(
            "组件必须默认读 AppPrefs.isPeriodHeaderHideTime",
            Regex("""hideTime\s*\?[:=].*AppPrefs\.isPeriodHeaderHideTime""").containsMatchIn(src) ||
                Regex("""hideTime\s*=\s*hideTimeOverride\s*\?:\s*AppPrefs\.isPeriodHeaderHideTime""").containsMatchIn(src),
        )
        // legacy 结构: if (hideTime) 只画居中标签; 时间串 (start/dash 分段) 必须
        // 整体落在 else 分支 — 隐藏时一行时间都不能出现; else 里必须真的画时间串。
        val ifIdx = src.indexOf("if (hideTime)")
        assertTrue("legacy 缺 if (hideTime) 分支", ifIdx >= 0)
        val elseIdx = src.indexOf("} else {", ifIdx)
        assertTrue("hideTime 分支缺配套 else", elseIdx > ifIdx)
        val hiddenBranch = src.substring(ifIdx, elseIdx)
        assertTrue(
            "hideTime=true 分支内禁渲染时间串 (displayStart/timeString)",
            !hiddenBranch.contains("text = slot.displayStart") &&
                !hiddenBranch.contains("slot.timeString"),
        )
        val elseBranch = src.substring(elseIdx, minOf(elseIdx + 3000, src.length))
        assertTrue(
            "else 分支缺时间串渲染",
            Regex("""text = slot\.displayStart|text = slot\.timeString""").containsMatchIn(elseBranch),
        )
    }

    @Test
    fun legacy_width_helper_shrinks_when_time_row_hidden() {
        val src = loadSource("ui/component/PeriodHeaderCellContent.kt")
        // 旧式列宽收口共用 helper: 隐藏时间行时按标签实测, 不再死钉 68dp。
        assertTrue(
            "缺 legacyTimeWidthDp 共享 helper",
            Regex("""fun legacyTimeWidthDp\([\s\S]{0,600}?hideTime:\s*Boolean""")
                .containsMatchIn(src),
        )
    }

    @Test
    fun grid_listens_for_hide_time_pref_changes() {
        val src = loadSource("ui/component/CourseTableView.kt")
        assertTrue(
            "CourseTableView changeBus filter 缺 KEY_PERIOD_HEADER_HIDE_TIME",
            Regex("""KEY_PERIOD_HEADER_HIDE_TIME""").containsMatchIn(src),
        )
        assertTrue(
            "CourseTableView legacy 列宽必须读 isPeriodHeaderHideTime",
            Regex("""isPeriodHeaderHideTime\(""").containsMatchIn(src),
        )
    }
}
