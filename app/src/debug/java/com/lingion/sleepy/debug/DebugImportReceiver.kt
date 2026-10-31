package com.lingion.sleepy.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppClock
import kotlinx.coroutines.runBlocking
import java.io.File

class DebugScheduleReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CourseScheduler", "debug receiver entered action=${intent.action}")
        if (intent.action != "com.lingion.sleepy.debug.SCHEDULE_NOW") return
        try {
            runBlocking {
                SleepyApp.get().notificationScheduler.scheduleTodayBeforeClassAlarms()
            }
            Log.d("CourseScheduler", "debug broadcast schedule completed")
        } catch (t: Throwable) {
            Log.e("CourseScheduler", "debug broadcast failed", t)
        }
    }
}

/**
 * adb 广播入口薄壳（历史形态保留）— 六能力逻辑全部转调 [DebugTriggers]，
 * 与 console /v1/trigger 共享单一实现（Task 5 系统化裁决：不再两套漂移）。
 * receiver 侧维持原有线程语义（后台线程执行、部分路径驻留），console 侧不阻塞。
 */
class DebugImportReceiver : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.view.View(this).apply { setBackgroundColor(0x00000000) })

        val extras = intent
        fun bg(body: () -> Unit) = Thread {
            try { body() } catch (t: Throwable) { Log.e("DebugImport", "trigger failed", t) }
        }.start()

        if (extras.getBooleanExtra("restore_and_cleanup", false)) {
            bg { runBlocking { DebugTriggers.restoreAndCleanup(this@DebugImportReceiver) } }
            finish(); return
        }
        if (extras.getBooleanExtra("delete_non_default_tables", false)) {
            bg { runBlocking { DebugTriggers.deleteNonDefaultTables(this@DebugImportReceiver) } }
            finish(); return
        }
        if (extras.getBooleanExtra("clear_fluid_notification", false)) {
            Log.d("CourseScheduler", DebugTriggers.clearFluidNotification(this))
            finish(); return
        }
        if (extras.getBooleanExtra("test_before_class", false)) {
            Log.d("CourseScheduler", DebugTriggers.testBeforeClass(
                this,
                courseName = extras.getStringExtra("courseName") ?: "高等数学",
                startTime = extras.getStringExtra("startTime") ?: "14:00",
                room = extras.getStringExtra("room") ?: "A101",
                teacher = extras.getStringExtra("teacher") ?: "张老师",
            ))
            // 旧形态驻留 60s 让流体云窗口走完（adb 手工触发需要活体观察期）
            Thread.sleep(60_000)
            return
        }
        if (extras.getBooleanExtra("schedule_before_class_now", false)) {
            runBlocking { DebugTriggers.scheduleBeforeClassNow(this@DebugImportReceiver) }
            Thread.sleep(500)
            finish(); return
        }
        if (extras.getBooleanExtra("add_soonest_course", false)) {
            bg {
                runBlocking {
                    DebugTriggers.addSoonestCourse(
                        this@DebugImportReceiver,
                        delayMin = extras.getIntExtra("delay_min", 5),
                        courseName = extras.getStringExtra("courseName") ?: "流体云测试课",
                        room = extras.getStringExtra("room") ?: "A101",
                    )
                }
            }
            finish(); return
        }

        // 从 base64 / 文件 / 剪贴板导入课表文本
        val b64 = extras.getStringExtra("b64")
        val path = extras.getStringExtra("path")
        Log.d(TAG, "importing b64=${b64?.length ?: 0} chars, path=$path")
        bg {
            val text = readSourceText(this, b64, path)
            Log.d(TAG, "read ${text.length} bytes")
            val repo = SleepyApp.get().repository
            val result = ScheduleParser.parse(text, 0L)
            val parseResult = result.getOrThrow()
            Log.d(TAG, "parsed ${parseResult.courses.size} courses, tableName=${parseResult.tableName}")
            val tableId = runBlocking {
                repo.insertTable(
                    TimeTableEntity(
                        name = parseResult.tableName.ifBlank { "导入的课表" },
                        startDate = parseResult.startDate.ifBlank { java.time.LocalDate.now(AppClock).toString() },
                        maxWeek = 20,
                        nodesPerDay = 13,
                        timeJson = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON,
                        color = "#FF6750A4",
                        isDefault = false
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
        }
        finish()
    }

    private fun readSourceText(context: Context, b64: String?, path: String?): String {
        if (!b64.isNullOrBlank()) {
            return String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
        if (!path.isNullOrBlank() && path != "clipboard") {
            val f = File(path)
            if (f.exists() && f.canRead()) return f.readText(Charsets.UTF_8)
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val projection = arrayOf(android.provider.MediaStore.Files.FileColumns._ID)
            val sel = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            val cursor = context.contentResolver.query(uri, projection, sel, arrayOf(f.name), null)
            val resolvedUri = cursor?.use { c ->
                if (c.moveToFirst()) android.content.ContentUris.withAppendedId(uri, c.getLong(0)) else null
            }
            if (resolvedUri != null) {
                return context.contentResolver.openInputStream(resolvedUri)!!.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }
            throw java.io.FileNotFoundException("cannot read $path")
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: throw java.io.FileNotFoundException("clipboard empty")
        if (clip.itemCount == 0) throw java.io.FileNotFoundException("clipboard no items")
        return clip.getItemAt(0).coerceToText(context).toString()
    }

    companion object {
        private const val TAG = "DebugImport"
    }
}
