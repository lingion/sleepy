package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeFormatOrderContractTest {
    private val root = File(System.getProperty("sleepy.test.root") ?: ".")

    private fun source(path: String): String = File(root, path).readText()

    private fun titleKeys(source: String): List<String> = Regex(
        """\btitle\s*=\s*stringResource\(R\.string\.([a-z_]+)\)"""
    ).findAll(source).map { it.groupValues[1] }.toList()

    @Test
    fun `Sleepy native format is first in the import format list`() {
        val src = source("app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt")
        val listStart = src.indexOf("text = stringResource(R.string.import_supported_formats)")
        assertTrue("Import supported-formats heading is missing", listStart >= 0)
        val listEnd = src.indexOf("Spacer(modifier = Modifier.height(24.dp))", listStart)
        assertTrue("Import supported-formats list boundary is missing", listEnd > listStart)

        val rows = Regex(
            """FormatRow\(\s*name\s*=\s*stringResource\(R\.string\.([a-z_]+)\)"""
        ).findAll(src.substring(listStart, listEnd)).map { it.groupValues[1] }.toList()

        assertEquals("format_plain", rows.firstOrNull())
        val strings = source("app/src/main/res/values-zh-rCN/strings.xml")
        assertTrue(
            "The first format must be identified as Sleepy native",
            strings.contains("<string name=\"format_plain_desc\">Sleepy 原生格式")
        )
    }

    @Test
    fun `Sleepy native formats precede compatible formats in course export`() {
        val src = source("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ExportScreen.kt")
        val listStart = src.indexOf("} else if (table != null) {")
        assertTrue("Course export list is missing", listStart >= 0)
        val listEnd = src.indexOf(
            "settingsSection(R.string.settings_export_calendar_assistants)",
            listStart
        )
        assertTrue("Course export list boundary is missing", listEnd > listStart)

        assertEquals(
            listOf("settings_export_native_file_title", "export_share_title", "export_json_title"),
            titleKeys(src.substring(listStart, listEnd))
        )
    }

    @Test
    fun `Sleepy native format is first in period table export`() {
        val src = source("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ExportScreen.kt")
        val listStart = src.indexOf("// 作息表保留原生与 JSON；课表按分享、日历与助手分组。")
        assertTrue("Export list grouping marker is missing", listStart >= 0)
        val listEnd = src.indexOf("} else if (table != null) {", listStart)
        assertTrue("Period-table export list boundary is missing", listEnd > listStart)

        assertEquals(
            listOf("period_table_share_native_title", "period_table_share_json_title"),
            titleKeys(src.substring(listStart, listEnd))
        )
    }

    @Test
    fun `Sleepy native formats are first in the schedule share sheet`() {
        val src = source("app/src/main/java/com/lingion/sleepy/ui/component/ShareScheduleSheet.kt")

        assertEquals(
            listOf("export_native_title", "export_share_title", "export_json_title", "export_ics_title"),
            titleKeys(src)
        )
    }

    @Test
    fun `Sleepy native format is first in period table share sheet`() {
        val src = source("app/src/main/java/com/lingion/sleepy/ui/component/PeriodTableShareSheet.kt")

        assertEquals(
            listOf("period_table_share_native_title", "period_table_share_json_title"),
            titleKeys(src)
        )
    }
}
