package com.lingion.sleepy.debug

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug-only 测试 Activity — 启动时从 Intent extra `path` 读 JSON 文件、走
 * ScheduleParser.parse + repo.insertTable/insertCourses 真实链路插入数据库。
 *
 *   adb shell am start -n com.lingion.sleepy.debug/com.lingion.sleepy.debug.DebugImportReceiver \
 *     -e path /sdcard/Download/test_sleepy.json
 *
 * 用 Activity 而不是 BroadcastReceiver 是因为 Android 8+ 禁止 manifest receiver
 * 在后台执行，而 Activity 启动时一定在前台 process。
 *
 * 不可见 — finish() 后不留 UI。
 */
class DebugImportReceiver : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Theme.NoDisplay 要求 finish() 在 onCreate 内返回前调用 — 异步执行工作。
        // Sources (priority order):
        //   1) extra "b64": base64-encoded JSON
        //   2) extra "path": file path, "clipboard", or file name in MediaStore
        val b64 = intent.getStringExtra("b64")
        val path = intent.getStringExtra("path")
        Log.d(TAG, "importing b64=${b64?.length ?: 0} chars, path=$path")
        Thread {
            try {
                val text = readSourceText(b64, path)
                Log.d(TAG, "read ${text.length} bytes")
                val repo = SleepyApp.get().repository
                val result = ScheduleParser.parse(text, 0L)
                val parseResult = result.getOrThrow()
                Log.d(TAG, "parsed ${parseResult.courses.size} courses, tableName=${parseResult.tableName}")
                val tableId = runBlocking {
                    repo.insertTable(
                        TimeTableEntity(
                            name = parseResult.tableName.ifBlank { "导入的课表" },
                            startDate = parseResult.startDate.ifBlank { java.time.LocalDate.now().toString() },
                            maxWeek = 20,
                            nodesPerDay = 13,
                            timeJson = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON,
                            color = "#FF6750A4",
                            isDefault = true
                        )
                    )
                }
                Log.d(TAG, "created table $tableId")
                val ids = runBlocking {
                    repo.insertCourses(
                        parseResult.courses.map { it.copy(id = 0, tableId = tableId) }
                    )
                }
                Log.d(TAG, "inserted ${ids.size} courses into table $tableId")
            } catch (e: Throwable) {
                Log.e(TAG, "import failed", e)
            }
        }.start()
        // 立即 finish — import 在后台线程跑，不阻塞。
        finish()
    }

    private fun readSourceText(b64: String?, path: String?): String {
        // 1) inline base64 (preferred for adb-shell driven import)
        if (!b64.isNullOrBlank()) {
            return String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
        // 2) file path
        if (!path.isNullOrBlank() && path != "clipboard") {
            val f = File(path)
            if (f.exists() && f.canRead()) return f.readText(Charsets.UTF_8)
            // Try MediaStore
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val projection = arrayOf(android.provider.MediaStore.Files.FileColumns._ID)
            val sel = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            val cursor = contentResolver.query(uri, projection, sel, arrayOf(f.name), null)
            val resolvedUri = cursor?.use { c ->
                if (c.moveToFirst()) android.content.ContentUris.withAppendedId(uri, c.getLong(0)) else null
            }
            if (resolvedUri != null) {
                return contentResolver.openInputStream(resolvedUri)!!.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }
            throw java.io.FileNotFoundException("cannot read $path")
        }
        // 3) clipboard
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: throw java.io.FileNotFoundException("clipboard empty")
        if (clip.itemCount == 0) throw java.io.FileNotFoundException("clipboard no items")
        return clip.getItemAt(0).coerceToText(this).toString()
    }

    companion object {
        private const val TAG = "DebugImport"
    }
}
