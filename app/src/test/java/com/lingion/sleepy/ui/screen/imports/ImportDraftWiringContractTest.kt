package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** New imports bypass drafts; old persisted drafts remain readable. */
class ImportDraftWiringContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    @Test
    fun `new table and course routes have distinct import targets without draft UI`() {
        val nav = loadSource("ui/nav/SleepyNavHost.kt")
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        assertTrue(nav.contains("entry<SleepyRoute.AddTable>"))
        assertTrue(nav.contains("entry<SleepyRoute.AddCourseEntry>"))
        assertTrue(nav.contains("targetTableId = targetId"))
        assertTrue(nav.contains("addingCourses = true"))
        assertTrue(!src.contains("ImportDraftSheet("))
        assertTrue(!src.contains("onRestoreDraft"))
    }

    @Test
    fun `new academic imports go directly to configuration and do not save drafts`() {
        val src = loadSource("ui/screen/imports/JwImportActivity.kt")
        val parse = src.substringAfter("configSmartConfig = inferredSmartConfig").substringBefore("onCaptureError =")
        assertTrue(parse.contains("stage = if (targetTableId > 0L) Stage.Preview else Stage.ConfigureConfirm"))
        assertTrue(!parse.contains("draftRepository.save"))
        assertTrue(src.contains("if (incomingDraftId == null) { finish(); return }"))
        assertTrue(src.contains("applyCourseImport("))
        assertTrue(!src.contains("pendingMode"))
    }

    @Test
    fun `new table confirmation cannot replace an existing table`() {
        val src = loadSource("ui/screen/imports/ImportSheet.kt")
        assertTrue(Regex("""if \(!addingCourses\) \{\s+ImportConfirmDialog\(""").containsMatchIn(src))
        assertTrue(src.contains("repo.importNewTable("))
        assertTrue(!src.contains("replaceCourses("))
        assertTrue(!src.contains("ImportApplyMode"))
        assertTrue(src.contains("onApply: (ImportDecision) -> Unit"))
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
        // 旧 fresh SmartPeriodConfig() 形态不允许出现在四个 seed site
        assertNull(
            "草稿恢复种子点不应再写裸 SmartPeriodConfig() 默认",
            Regex("""restoredStored\s*\n\s*\?:\s*SmartPeriodConfig\(\)""")
                .find(src)?.value,
        )
    }

    @Test
    fun `keeping a draft creates one when leaving before courses are parsed`() {
        val src = loadSource("ui/screen/imports/JwImportActivity.kt")

        assertTrue(
            "保存草稿必须允许只有已选学校、尚未解析课程的 WebView 阶段",
            "val school = selectedSchool ?: parsedSchool ?: return null" in src,
        )
        assertTrue(
            "保存草稿必须为无 draftId 的首次保存调用 repository.save",
            "if (draftId == null)" in src && "draftRepository.save(" in src,
        )
        assertTrue(
            "WebView 草稿恢复必须使用已测试的学校准入规则；确认前留在选择页，已支持入口直接恢复",
            Regex("""JwImportDraftPhase\.WEBVIEW_LOGIN\s*->\s*if\s*\(snapshot\.requiresSchoolConfirmation\)\s*\{\s*catalogNotice\s*=\s*snapshot\.school\s*Stage\.SelectSchool\s*}\s*else\s*Stage\.WebViewLogin""")
                .containsMatchIn(src),
        )
        assertTrue(
            "待验证入口只能从用户明确确认按钮进入 WebView",
            Regex("""val mayTry = school\.isUnverified && school\.canImport.*?confirmButton\s*=.*?TextButton\(onClick\s*=\s*\{.*?if \(mayTry\).*?selectedSchool = school.*?stage = Stage\.WebViewLogin""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(src),
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
