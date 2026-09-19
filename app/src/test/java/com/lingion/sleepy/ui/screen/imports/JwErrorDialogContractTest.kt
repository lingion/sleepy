package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 报错弹窗化契约 (2026-09-18 用户: 所有报错必须统一弹窗, 确定 + 导出排查全量包)。
 *
 * 锁三件事:
 *  1. JwImportActivity 错误渲染走 AlertDialog(不再是 Box+Card)
 *  2. 弹窗双按钮 = 确定(jw_err_dismiss) + 导出排查全量包(jw_diag_export_btn)
 *  3. 导出链 = DOM_INVENTORY_JS 现抓 → JwCaptureDump.exportDump → share
 */
class JwErrorDialogContractTest {

    private val source: String = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt"),
        File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwImportActivity.kt source")

    @Test
    fun error_rendering_uses_alert_dialog_not_card() {
        val block = errorDialogBlock()
        assertTrue("错误提示必须走 AlertDialog", block.contains("AlertDialog("))
        assertFalse(
            "旧 Box+Card 错误渲染必须移除",
            block.contains("Card(")
        )
    }

    @Test
    fun error_dialog_has_dismiss_and_export_buttons() {
        val block = errorDialogBlock()
        assertTrue("弹窗必须有确定按钮(jw_err_dismiss)", block.contains("R.string.jw_err_dismiss"))
        assertTrue(
            "弹窗必须有导出排查全量包按钮(jw_diag_export_btn)",
            block.contains("R.string.jw_diag_export_btn")
        )
        assertTrue("双按钮必须走 DialogActionButtons 色块入口", block.contains("DialogActionButtons("))
    }

    @Test
    fun export_button_wires_dom_inventory_capture_dump_share_chain() {
        assertTrue(
            "导出必须现抓 DOM 清单(DOM_INVENTORY_JS)",
            source.contains("DOM_INVENTORY_JS")
        )
        assertTrue(
            "导出必须走 JwCaptureDump.exportDump",
            Regex("""JwCaptureDump\.exportDump\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "存完必须拉分享面板 JwCaptureDump.share",
            Regex("""JwCaptureDump\.share\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "必须保留 FrameCaptureResult 供导出按钮消费(lastCaptureResult)",
            source.contains("lastCaptureResult")
        )
    }

    @Test
    fun status_message_uses_auto_dismissing_snackbar_lifecycle() {
        assertTrue(
            "状态提示必须使用 SnackbarHostState.showSnackbar 自动结束生命周期",
            source.contains("SnackbarHostState") && source.contains("showSnackbar")
        )
        assertTrue(
            "状态提示必须在 Snackbar 结束后清空",
            Regex("""statusMsg\s*=\s*null""").containsMatchIn(statusBlock())
        )
        assertTrue(
            "状态提示必须由 SnackbarHost 渲染",
            statusBlock().contains("SnackbarHost(")
        )
        assertFalse(
            "状态提示不能直接永久渲染 Snackbar",
            statusBlock().contains("statusMsg?.let { msg ->")
        )
    }

    @Test
    fun export_runs_off_main_thread() {
        // zip 组装是 IO 阻塞, 必须在 Dispatchers.IO
        val fn = exportFn()
        assertTrue("exportDump 必须在 Dispatchers.IO 执行", fn.contains("Dispatchers.IO"))
    }

    private fun statusBlock(): String {
        val start = source.indexOf("LaunchedEffect(statusMsg)")
        return if (start < 0) "" else source.substring(start)
    }

    private fun errorDialogBlock(): String {
        val marker = "errorMsg?.let { msg ->"
        val start = source.indexOf(marker)
        assertTrue("errorMsg 渲染块缺失", start >= 0)
        val end = source.indexOf("statusMsg?.let { msg ->", start).let {
            if (it < 0) source.length else it
        }
        return source.substring(start, end)
    }

    private fun exportFn(): String {
        val start = source.indexOf("fun exportDiagnosticDump(")
        assertTrue("exportDiagnosticDump 函数缺失", start >= 0)
        val end = source.indexOf("fun handleExitChoice", start).let {
            if (it < 0) source.length else it
        }
        return source.substring(start, end)
    }
}
