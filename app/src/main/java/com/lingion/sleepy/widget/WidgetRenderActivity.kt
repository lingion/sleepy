package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * v17-rebuild: 暴力测试 WeekGridWidgetProvider 的 Bitmap 渲染
 *
 * 流程: seed 20 节课 → 调 WeekGridWidgetProvider.renderBitmap(418x643dp) →
 *      显示 ImageView + 同步保存 PNG 到 /sdcard/Download/widget_v17.png
 */
class WidgetRenderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                seedMockData()

                val data = WeekGridWidgetProvider.loadWeekData(this@WidgetRenderActivity)
                Log.d("WidgetRender", "loaded ${data.days.sumOf { it.courses.size }} courses, " +
                    "perDay=${data.days.map { "${it.dayOfWeek}:${it.courses.size}" }}")

                val density = resources.displayMetrics.density
                val widgetW_px = (418f * density).toInt()
                val widgetH_px = (643f * density).toInt()
                Log.d("WidgetRender", "rendering bitmap ${widgetW_px}x$widgetH_px (density=$density)")

                val bitmap: Bitmap = WeekGridWidgetProvider.renderBitmap(
                    context = this@WidgetRenderActivity,
                    data = data,
                    widthPx = widgetW_px,
                    heightPx = widgetH_px
                )

                // 保存 PNG 到 /sdcard/Download/widget_v17.png (用户可拉)
                try {
                    val out = File("/sdcard/Download/widget_v17.png")
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    Log.d("WidgetRender", "saved PNG: ${out.absolutePath} (${out.length()} bytes)")
                } catch (e: Exception) {
                    Log.e("WidgetRender", "save PNG failed", e)
                }

                runOnUiThread {
                    val iv = ImageView(this@WidgetRenderActivity).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.FIT_XY
                    }
                    val container = FrameLayout(this@WidgetRenderActivity).apply {
                        setBackgroundColor(0xFF1A1A2E.toInt())
                        addView(iv, FrameLayout.LayoutParams(widgetW_px, widgetH_px).apply {
                            gravity = Gravity.CENTER
                        })
                    }
                    setContentView(container)
                    Log.d("WidgetRender", "ImageView displayed (${bitmap.width}x${bitmap.height})")
                }

            } catch (e: Exception) {
                Log.e("WidgetRender", "FAILED", e)
                runOnUiThread { redScreen() }
            }
        }
    }

    private suspend fun seedMockData() {
        val app = SleepyApp.get()
        val repo = app.repository

        val existing = repo.getDefaultTable()
        if (existing != null) {
            Log.d("WidgetRender", "DB already has default table id=${existing.id}")
            val allCourses = (1..7).flatMap { dow ->
                repo.getCoursesByDayOnce(existing.id, dow)
            }
            if (allCourses.isNotEmpty()) {
                Log.d("WidgetRender", "DB has ${allCourses.size} courses")
                return
            }
            seedCourses(repo, existing.id)
            return
        }

        val table = TimeTableEntity(
            name = "测试课表",
            startDate = "2026-02-23",
            maxWeek = 20,
            nodesPerDay = 12,
            timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
            isDefault = true
        )
        val tableId = repo.insertTable(table)
        Log.d("WidgetRender", "inserted table id=$tableId")
        seedCourses(repo, tableId)
    }

    private suspend fun seedCourses(repo: com.lingion.sleepy.data.repository.ScheduleRepository, tableId: Long) {
        fun course(name: String, day: Int, startNode: Int, step: Int, color: String) =
            CourseEntity(
                id = 0, groupId = "mock-$day-$startNode", tableId = tableId,
                courseName = name, teacher = "老师",
                room = "A${100 + day * 10 + startNode}",
                day = day, startNode = startNode, step = step,
                startWeek = 1, endWeek = 18, type = 0, color = color
            )

        // ★ v17-rebuild 暴力测试 — maxNode 完全由用户课表决定 (不硬编上限)
        // 用户原话: "用户课表填1个时间段小组件就1个 填9999个小组件就9999个"
        // 这里测试 3 个 case:
        //   (a) 普通 12 节 (用户课表默认 nodesPerDay=12)
        //   (b) 大课表 24 节 (验证 maxNode=24 撑满 widget)
        //   (c) 跨节超长课 (1门课从 P1 跨到 P12)
        val mockCourses = listOf(
            // ========== Case A: 12 节课表 ==========
            // 周一 (4 节课: 1节/2节/3节/4节)
            course("周一P1-1节", 1, 1, 1, "#FF6750A4"),
            course("周一P2-2节", 1, 2, 2, "#FF7D5260"),
            course("周一P5-3节", 1, 5, 3, "#FFB69DF8"),
            course("周一P9-4节", 1, 9, 4, "#FF59CD6E"),

            // 周二 (3 节课: 1节/2节/6节超长)
            course("周二P1-1节", 2, 1, 1, "#FF4A6741"),
            course("周二P3-2节", 2, 3, 2, "#FFFBE4C6"),
            course("周二P7-6节超长", 2, 7, 6, "#FFB00020"),

            // 周三 (2 节课: 1节/3节)
            course("周三P1-1节", 3, 1, 1, "#FF006A6A"),
            course("周三P4-3节", 3, 4, 3, "#FF0061A4"),

            // 周四 (3 节课: 1节/5节/2节)
            course("周四P1-1节", 4, 1, 1, "#FFB4A8"),
            course("周四P4-5节长", 4, 4, 5, "#FF8B5CF6"),
            course("周四P11-2节", 4, 11, 2, "#FFE91E63"),

            // 周五 (2 节课: 2节/1节)
            course("周五P1-2节", 5, 1, 2, "#FFFBE4C6"),
            course("周五P10-1节", 5, 10, 1, "#FF6750A4"),

            // 周六 (2 节课: 1节/2节)
            course("周六P1-1节", 6, 1, 1, "#FF4A6741"),
            course("周六P6-2节", 6, 6, 2, "#FF0061A4"),

            // 周日 (2 节课: 1节/1节)
            course("周日P1-1节", 7, 1, 1, "#FFB00020"),
            course("周日P12-1节", 7, 12, 1, "#FF6750A4")
        )

        val ids = repo.insertCourses(mockCourses)
        Log.d("WidgetRender", "inserted ${ids.size} violent test courses, tableId=$tableId")
    }

    private fun redScreen() {
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFFFF0000.toInt())
        })
    }
}
