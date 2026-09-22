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
        assertTrue(
            "导出进度卡允许存在于错误弹窗内, 但必须由 dumpProgress 控制显隐",
            block.contains("dumpProgress?.let { progress ->")
        )
    }

    @Test
    fun export_progress_expands_inside_error_dialog_below_action_buttons() {
        val block = errorDialogBlock()
        val buttons = block.indexOf("DialogActionButtons(")
        val progress = block.indexOf("dumpProgress?.let { progress ->")
        assertTrue("进度区必须在错误弹窗内", progress >= 0)
        assertTrue("进度区必须位于导出按钮之后", progress > buttons)
        assertTrue("弹窗内必须显示阶段文案", block.contains("progress.stage.labelRes"))
        assertTrue("弹窗内必须显示百分比", block.contains("jw_diag_progress_percent"))
        assertFalse("页面底部不应再渲染第二份进度卡", source.substringAfter("LaunchedEffect(statusMsg)").contains("dumpProgress?.let"))
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

    @Test
    fun export_reports_atomic_stage_progress_not_black_box() {
        // 2026-09-21 用户: 点导出必须看到每个原子步骤的进度+百分比, 禁止无止境
        // "正在生成"黑箱。锁三件事: ①dumpProgress 状态存在并被推进 ②每个采集段
        // 执行前调 entering ③UI 有进度渲染(LinearProgressIndicator + 百分比文案)。
        val fn = exportFn()
        assertTrue(
            "导出函数必须推进 dumpProgress 原子步骤状态",
            fn.contains("dumpProgress = DiagDumpProgress(")
        )
        // 管线全部 9 段都必须有 advance 调用 — 缺一段 = 用户在该段处于黑箱
        DumpStage.entries.forEach { stage ->
            assertTrue(
                "采集管线缺 ${stage.name} 段的 advance 调用(用户会在此段黑箱等待)",
                fn.contains("entering(DumpStage.${stage.name})")
            )
        }
        assertTrue(
            "必须有 LinearProgressIndicator 渲染百分比进度",
            source.contains("LinearProgressIndicator(") &&
                source.contains("R.string.jw_diag_progress_percent")
        )
        assertTrue(
            "必须显示 步骤 x/n 文案(jw_diag_progress_step)",
            source.contains("R.string.jw_diag_progress_step")
        )
        // 旧的单一"正在生成"文案已被原子步骤取代, 不许复活
        assertFalse(
            "禁止回退到无进度的单一 jw_diag_exporting 文案",
            source.contains("R.string.jw_diag_exporting")
        )
        // 终态必须清进度卡: 成功与失败分支都要 dumpProgress = null
        val okBranch = fn.substringAfter("is JwCaptureDump.DumpResult.Ok")
        val failBranch = fn.substringAfter("is JwCaptureDump.DumpResult.Fail")
        assertTrue("成功分支必须清 dumpProgress", okBranch.contains("dumpProgress = null"))
        assertTrue("失败分支必须清 dumpProgress", failBranch.contains("dumpProgress = null"))
    }

    private fun statusBlock(): String {
        val start = source.indexOf("LaunchedEffect(statusMsg)")
        return if (start < 0) "" else source.substring(start)
    }

    private fun errorDialogBlock(): String {
        val marker = "errorMsg?.let { msg ->"
        val start = source.indexOf(marker)
        assertTrue("errorMsg 渲染块缺失", start >= 0)
        // 边界 = 本弹窗块之后的下一个顶层 composable 结构 (LaunchedEffect(statusMsg) /
        // Box(进度卡+Snackbar))。旧版以 "statusMsg?.let" 为界, 但该写法早已不存在,
        // end 一直静默落到 source.length, 把整个文件尾部都算进弹窗块 —
        // 导出进度卡(Card)加进来后旧边界误伤。收窄到真实块尾。
        val candidates = listOf(
            source.indexOf("LaunchedEffect(statusMsg)", start),
            source.indexOf("\n                Box(", start),
        ).filter { it > start }
        val end = if (candidates.isEmpty()) source.length else candidates.min()
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
