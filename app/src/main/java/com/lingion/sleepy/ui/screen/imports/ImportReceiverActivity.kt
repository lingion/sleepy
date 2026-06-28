package com.lingion.sleepy.ui.screen.imports

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 外部 app (文件管理器、邮件附件、其他课表 app) 通过 ACTION_VIEW 打开 json 课表时, 先到这里:
 * 1. 读 URI 内容
 * 2. 解析为 ParseResult
 * 3. 跳到 MainActivity 并把解析结果通过 Intent extra 传过去, 由 ImportSheet 接收并弹预览对话框
 *
 * 这样:
 * - intent-filter 干净, MainActivity 不需要处理 VIEW, 避免 singleTask launchMode 边界问题
 * - 用户体验: 外部打开 json -> Sleepy 启动 -> 自动弹导入预览 -> 一键确认
 */
class ImportReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        if (uri == null) {
            finishWithError("no_uri")
            return
        }

        lifecycleScope.launch {
            try {
                // file:// URI 在 Android 7+ 直接 openInputStream 会 EACCES (Scoped Storage)，
                // 先复制到 cacheDir 再读; content:// URI (SAF picker) 直接读即可, 已被授权。
                val text = withContext(Dispatchers.IO) {
                    if (uri.scheme == "file") {
                        val src = uri.path
                        if (src == null) return@withContext null
                        val cacheFile = java.io.File(cacheDir, "shared_import_${System.currentTimeMillis()}.json")
                        cacheFile.outputStream().use { out ->
                            java.io.File(src).inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        cacheFile.readText().also {
                            cacheFile.delete()
                        }
                    } else {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                }
                if (text.isNullOrBlank()) {
                    finishWithError("empty")
                    return@launch
                }

                // 通过 companion.pendingImportText 透传到 MainActivity, 不依赖 Intent extra
                com.lingion.sleepy.MainActivity.pendingImportText = text

                val forward = Intent(this@ImportReceiverActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_FROM_IMPORT_RECEIVER, true)
                    putExtra(EXTRA_IMPORT_TEXT, text)
                }
                startActivity(forward)
                finish()
            } catch (e: Exception) {
                android.util.Log.e("Sleepy", "external import failed", e)
                finishWithError(e.message ?: "unknown")
            }
        }
    }

    private fun finishWithError(msg: String) {
        // 跳回 MainActivity 让用户看到主界面, 不弹任何 dialog (避免惊吓)
        val fallback = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            startActivity(fallback)
        } catch (_: Exception) {
            // ignore
        }
        finish()
    }

    companion object {
        const val EXTRA_FROM_IMPORT_RECEIVER = "extra_from_import_receiver"
        const val EXTRA_IMPORT_TEXT = "extra_import_text"
    }
}