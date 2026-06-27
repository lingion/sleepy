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
        val path = intent.getStringExtra("path")
        if (path.isNullOrBlank()) {
            Log.e(TAG, "no path extra")
            finish()
            return
        }
        Log.d(TAG, "importing from $path")
        Thread {
            try {
                val text = File(path).readText(Charsets.UTF_8)
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

    companion object {
        private const val TAG = "DebugImport"
    }
}
