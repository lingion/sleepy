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

        if (intent.getBooleanExtra("test_before_class", false)) {
            val name = intent.getStringExtra("courseName") ?: "高等数学"
            val startTime = intent.getStringExtra("startTime") ?: "14:00"
            val room = intent.getStringExtra("room") ?: "A101"
            val teacher = intent.getStringExtra("teacher") ?: "张老师"
            val mode = intent.getStringExtra("mode") ?: FluidCloudService.MODE_B
            val svc = Intent(this, FluidCloudService::class.java).apply {
                putExtra("courseName", name)
                putExtra("startTime", startTime)
                putExtra("room", room)
                putExtra("teacher", teacher)
                putExtra("mode", mode)
            }
            ContextCompat.startForegroundService(this, svc)
            Thread.sleep(60000)
            return
        }

        if (intent.getBooleanExtra("schedule_before_class_now", false)) {
            runBlocking {
                SleepyApp.get().notificationScheduler.scheduleTodayBeforeClassAlarms()
            }
            Thread.sleep(500)
            finish()
            return
        }

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
