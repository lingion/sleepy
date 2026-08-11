package com.lingion.sleepy.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.widget.notification.FluidCloudService
import androidx.core.content.ContextCompat
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
            Log.e("CourseScheduler", "debug broadcast schedule failed", t)
        }
    }
}

class DebugImportReceiver : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.view.View(this).apply { setBackgroundColor(0x00000000) })

        // 清理：把非测试表设为默认，删除名字含"测试"/"流体"的表
        if (intent.getBooleanExtra("restore_and_cleanup", false)) {
            Thread {
                try {
                    val repo = SleepyApp.get().repository
                    runBlocking {
                        val all = repo.getAllTables()
                        val keep = all.firstOrNull { !it.name.contains("测试") && !it.name.contains("fluid") }
                        if (keep != null) repo.setDefault(keep.id)
                        all.filter { it.name.contains("测试") || it.name.contains("流体") }
                            .forEach { repo.deleteTable(it.id) }
                    }
                    Log.d("DebugImport", "restore_and_cleanup done")
                } catch (t: Throwable) {
                    Log.e("DebugImport", "restore_and_cleanup failed", t)
                }
            }.start()
            finish()
            return
        }

        // 删除所有非默认表
        if (intent.getBooleanExtra("delete_non_default_tables", false)) {
            Thread {
                try {
                    val repo = SleepyApp.get().repository
                    val before = runBlocking { repo.getAllTables().size }
                    runBlocking {
                        repo.getAllTables().filter { !it.isDefault }.forEach { repo.deleteTable(it.id) }
                    }
                    val after = runBlocking { repo.getAllTables().size }
                    Log.d("DebugImport", "deleted non-default tables: $before -> $after")
                } catch (t: Throwable) {
                    Log.e("DebugImport", "delete_non_default_tables failed", t)
                }
            }.start()
            finish()
            return
        }

        // 清流体云通知（用已过期的窗口触发 FluidCloudService 自停）
        if (intent.getBooleanExtra("clear_fluid_notification", false)) {
            val now = System.currentTimeMillis()
            val expired = Intent(this, FluidCloudService::class.java).apply {
                putExtra("courseName", "")
                putExtra("room", "")
                putExtra("startTime", "")
                putExtra("notifyEpoch", now - 2_000L)
                putExtra("classEpoch", now - 1_000L)
            }
            ContextCompat.startForegroundService(this, expired)
            Log.d("CourseScheduler", "requested expired fluid service cleanup")
            finish()
            return
        }

        // 直接启动 FluidCloudService 测试流体云（前台服务 + Handler 循环刷进度）
        if (intent.getBooleanExtra("test_before_class", false)) {
            val name = intent.getStringExtra("courseName") ?: "高等数学"
            val startTime = intent.getStringExtra("startTime") ?: "14:00"
            val room = intent.getStringExtra("room") ?: "A101"
            val teacher = intent.getStringExtra("teacher") ?: "张老师"
            val now = System.currentTimeMillis()
            val svc = Intent(this, FluidCloudService::class.java).apply {
                putExtra("courseName", name)
                putExtra("startTime", startTime)
                putExtra("room", room)
                putExtra("teacher", teacher)
                putExtra("notifyEpoch", now)
                putExtra("classEpoch", now + 120_000L)  // 2 分钟窗口
            }
            ContextCompat.startForegroundService(this, svc)
            Thread.sleep(60_000)
            return
        }

        // 立即触发一次排课前提醒 alarm
        if (intent.getBooleanExtra("schedule_before_class_now", false)) {
            runBlocking {
                SleepyApp.get().notificationScheduler.scheduleTodayBeforeClassAlarms()
            }
            Thread.sleep(500)
            finish()
            return
        }

        // 新建默认测试课表 + 一节"现在+N分钟"上课的 ownTime 课（用于端到端触发流体云）
        if (intent.getBooleanExtra("add_soonest_course", false)) {
            Thread {
                try {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    val delayMin = intent.getIntExtra("delay_min", 5)
                    val cls = java.time.LocalTime.now().plusMinutes(delayMin.toLong())
                    val end = cls.plusMinutes(45)
                    val today = java.time.LocalDate.now()
                    val monday = today.with(java.time.DayOfWeek.MONDAY)
                    val tid = runBlocking {
                        val id = repo.insertTable(
                            TimeTableEntity(
                                name = "流体云触发测试",
                                startDate = monday.toString(),
                                isDefault = true
                            )
                        )
                        repo.setDefault(id)
                        id
                    }
                    val c = com.lingion.sleepy.data.entity.CourseEntity(
                        groupId = java.util.UUID.randomUUID().toString(),
                        tableId = tid,
                        courseName = intent.getStringExtra("courseName") ?: "流体云测试课",
                        teacher = "测试",
                        room = intent.getStringExtra("room") ?: "A101",
                        day = today.dayOfWeek.value,
                        startNode = 1, step = 1,
                        startWeek = 1, endWeek = 20, type = 0,
                        color = "#FF6750A4",
                        ownTime = true,
                        startTime = String.format("%02d:%02d", cls.hour, cls.minute),
                        endTime = String.format("%02d:%02d", end.hour, end.minute)
                    )
                    runBlocking { repo.insertCourse(c) }
                    Log.d("DebugImport", "created table $tid, course at ${c.startTime} ownTime")
                } catch (t: Throwable) {
                    Log.e("DebugImport", "add_soonest_course failed", t)
                }
            }.start()
            finish()
            return
        }

        // 从 base64 / 文件 / 剪贴板导入课表文本
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
            } catch (e: Throwable) {
                Log.e(TAG, "import failed", e)
            }
        }.start()
        finish()
    }

    private fun readSourceText(b64: String?, path: String?): String {
        if (!b64.isNullOrBlank()) {
            return String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
        if (!path.isNullOrBlank() && path != "clipboard") {
            val f = File(path)
            if (f.exists() && f.canRead()) return f.readText(Charsets.UTF_8)
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
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: throw java.io.FileNotFoundException("clipboard empty")
        if (clip.itemCount == 0) throw java.io.FileNotFoundException("clipboard no items")
        return clip.getItemAt(0).coerceToText(this).toString()
    }

    companion object {
        private const val TAG = "DebugImport"
    }
}
