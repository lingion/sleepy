package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入草稿箱 UI 接线契约 — 交叉验证发现两处断链后锁死(先例:
 * ScheduleViewModeSessionContractTest — 仓库无 Robolectric/Compose UI 测试,
 * 声明式接线读源头文件等价于读编译产物):
 *
 * 断链① ManagementPage 收到 drafts/onRestoreDraft/onDeleteDraft 后调 ImportSheet
 *   时没透传, 落默认 emptyList()/no-op → 草稿图标能开面板但列表永远空,
 *   恢复/删除按钮全部无效。锁: ImportSheet( 调用位必须出现三参数实名传递。
 * 断链② SelectSchool stage 无 BackHandler → 系统返回键直接 finish Activity
 *   绕过退出确认(activeImport 置位后误触返回 = 静默丢进度, issue#39 痛点本体)。
 *   锁: JwImportActivity 必须存在 SelectSchool 条件 BackHandler。
 */
class ImportDraftWiringContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    /**
     * 从 [src] 提取 marker 起始的调用块: 实参表含嵌套 lambda(onJwImportRequested = { ... }),
     * 纯括号配对会被 lambda 内的 () 提前截断 — 必须 () 与 {} 双计数,
     * 括号深度归零且不在任何花括号块内才算调用结束。
     */
    private fun balancedBlock(src: String, marker: String): String {
        val start = src.indexOf(marker)
        var parenDepth = 0
        var braceDepth = 0
        for (i in start until src.length) {
            when (src[i]) {
                '(' -> parenDepth++
                ')' -> {
                    parenDepth--
                    // 实参级 '(' 已闭 + 无未闭 lambda → 本次调用结束
                    if (parenDepth == 0 && braceDepth == 0) return src.substring(start, i + 1)
                }
                '{' -> braceDepth++
                '}' -> braceDepth--
            }
        }
        return src.substring(start)
    }

    @Test
    fun `ManagementPage forwards draft params into ImportSheet call site`() {
        val src = loadSource("ui/screen/manage/ManagementPage.kt")
        val callSite = balancedBlock(src, "ImportSheet(")
        // 实名透传三件套必须出现在 ImportSheet( 的实参表里
        assertTrue(
            "ImportSheet( 调用位缺少 drafts = drafts 实名透传",
            Regex("""drafts\s*=\s*drafts""").containsMatchIn(callSite),
        )
        assertTrue(
            "ImportSheet( 调用位缺少 onRestoreDraft 透传",
            Regex("""onRestoreDraft\s*=\s*onRestoreDraft""").containsMatchIn(callSite),
        )
        assertTrue(
            "ImportSheet( 调用位缺少 onDeleteDraft 透传",
            Regex("""onDeleteDraft\s*=\s*onDeleteDraft""").containsMatchIn(callSite),
        )
    }

    @Test
    fun `SelectSchool stage has conditional BackHandler for exit confirmation`() {
        val src = loadSource("ui/screen/imports/JwImportActivity.kt")
        assertTrue(
            "SelectSchool stage 缺 BackHandler — 系统返回键会绕过退出确认直接 finish",
            Regex(
                """BackHandler\s*\(\s*enabled\s*=.*Stage\.SelectSchool"""
            ).containsMatchIn(src),
        )
        // BackHandler 回调必须走 requestExit() (弹三选确认), 禁直连 finish()
        val handler = src.substringAfter("BackHandler(enabled").substringBefore("LaunchedEffect(incomingDraftId)")
        assertTrue(
            "SelectSchool BackHandler 必须经 requestExit() 弹确认, 禁直连 finish()",
            handler.contains("requestExit()"),
        )
    }

    @Test
    fun `ImportSheet declares draft params with forwarding-ready names`() {
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        assertTrue(
            "ImportSheet 签名缺 drafts 形参",
            Regex("""drafts:\s*List<ImportDraft>""").containsMatchIn(src),
        )
        assertTrue(
            "ImportSheet 签名缺 onRestoreDraft 形参",
            Regex("""onRestoreDraft:\s*\(String\)\s*->\s*Unit""").containsMatchIn(src),
        )
        assertTrue(
            "ImportSheet 必须把 onRestoreDraft 接到 ImportDraftSheet 的 onRestore",
            Regex("""onRestore\s*=\s*\{[^}]*onRestoreDraft""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(src),
        )
    }

    // --- issue#23 Task 5: 导入边界共享推断契约 ---

    @Test
    fun `ImportConfirmDialog seeds smartConfig from imported rows via shared inference`() {
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        // 初值必须从导入解析行推断; 旧 fresh 45-min SmartPeriodConfig(...) 形态属回归。
        assertTrue(
            "ImportConfirmDialog 应走 resolveAutoPeriodConfig(rows, null) 播种; 旧 fresh 45-min 默认值属回归",
            Regex(
                """resolveAutoPeriodConfig\(\s*rows\.toList\(\)\s*,\s*null\s*\)"""
            ).containsMatchIn(src),
        )
        assertTrue(
            "ImportConfirmDialog 缺失保底默认 (totalPeriods/startTime 兜底) — 推断 null 时必须回退",
            Regex("""totalPeriods\s*=\s*rows\.size\.coerceAtLeast\(1\)""").containsMatchIn(src),
        )
    }

    @Test
    fun `JwImportActivity seeds draft restoration and fresh parse via shared inference`() {
        val src = loadSource("ui/screen/imports/JwImportActivity.kt")
        // 三个种子站点: 初始空行 (null 兜底), 草稿恢复 (解析 stored 后推断),
        // 新解析 draft 快照 (推断 -> encode).
        assertTrue(
            "JwImportActivity 初始 configSmartConfig 应走 TimeTableUtils.inferSmartPeriodConfig",
            Regex(
                """TimeTableUtils\.inferSmartPeriodConfig\(\s*configRows\s*\)"""
            ).containsMatchIn(src),
        )
        assertTrue(
            "JwImportActivity 草稿恢复应走 resolveAutoPeriodConfig(rows, restoredStored)",
            Regex(
                """resolveAutoPeriodConfig\(\s*configRows\s*,\s*restoredStored\s*\)"""
            ).containsMatchIn(src),
        )
        assertTrue(
            "JwImportActivity 新建草稿快照应把推断结果 encode 进 smartConfigJson",
            Regex(
                """TimeTableUtils\.inferSmartPeriodConfig\(\s*newRows\s*\)"""
            ).containsMatchIn(src),
        )
        assertTrue(
            "JwImportActivity 新解析路径必须同步更新 live configSmartConfig",
            Regex(
                """val inferredSmartConfig\s*=.*?configSmartConfig\s*=\s*inferredSmartConfig""",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(src),
        )
        assertTrue(
            "JwImportActivity snapshot 必须与 live config 使用同一个 inferredSmartConfig",
            Regex("""smartConfigJson\s*=\s*Json\.encodeToString\(inferredSmartConfig\)""")
                .containsMatchIn(src),
        )
        // 旧 fresh SmartPeriodConfig() 形态不允许出现在四个 seed site
        assertNull(
            "草稿恢复种子点不应再写裸 SmartPeriodConfig() 默认",
            Regex("""restoredStored\s*\n\s*\?:\s*SmartPeriodConfig\(\)""")
                .find(src)?.value,
        )
    }

    @Test
    fun `imported rows with 45-min majority and one 30-min row yield correct smart config`() {
        // 真实复刻从教务抓回的混合时长节次: 4 节, 第 3 节 30 分钟.
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(3, "09:50", "10:20"),
            TimeTableUtils.TimeSlotRow(4, "10:30", "11:15"),
        )

        val inferred = TimeTableUtils.inferSmartPeriodConfig(rows)

        assertEquals(45, inferred!!.periodMinutes)
        assertEquals(4, inferred.totalPeriods)
        assertEquals(listOf(DurationOption(30, isLong = false)), inferred.durations)
        assertEquals(
            listOf<Int?>(null, null, 0, null),
            inferred.periodAssignments,
        )
        assertEquals(
            listOf("08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:20", "10:30" to "11:15"),
            inferred.derive().map { it.start to it.end },
        )
    }

    @Test
    fun `incomplete imported rows keep manual mode and do not write guessed defaults`() {
        // 第 2 节 start 空白 — inferSmartPeriodConfig 必须返回 null, 调用方回退最简默认,
        // TimeSlotEditor 保持手动模式 + 既有校验 (RegExp 兜底), 不写猜测值.
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "", "09:30"),
        )

        assertNull(TimeTableUtils.inferSmartPeriodConfig(rows))
    }

    @Test
    fun `edge rows are excluded from imported inference`() {
        // 真实导入边界: 早读/晚自习节点 (edgeClass) 不应进入主时长推断,
        // 否则衍生时会扭曲节次数.
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(0, "07:00", "07:59", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(3, "09:50", "10:35"),
            TimeTableUtils.TimeSlotRow(4, "22:00", "23:00", TimeTableUtils.EdgeClass.After),
        )

        val inferred = TimeTableUtils.inferSmartPeriodConfig(rows)

        assertEquals(3, inferred!!.totalPeriods)
        assertEquals(
            listOf("08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:35"),
            inferred.derive().map { it.start to it.end },
        )
    }

    @Test
    fun `import inference round trip preserves every valid standard pair`() {
        // 任一可推断的导入行 -> derive 回到原行 (node/start/end 三元组逐行相等).
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(3, "10:00", "10:30"),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(4, "10:40", "11:25"),
        )

        val inferred = TimeTableUtils.inferSmartPeriodConfig(rows)
        val derived = inferred!!.derive()

        assertEquals(listOf(1, 2, 3, 4), derived.map { it.node })
        assertEquals(
            listOf("08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:30", "10:40" to "11:25"),
            derived.map { it.start to it.end },
        )
    }
}
