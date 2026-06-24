package com.lingion.sleepy.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WidgetRenderActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_render)

        scope.launch {
            try {
                // seed mock data (24 节)
                seedMockData()

                // load data
                val data = WeekGridWidgetProvider.loadWeekData(this@WidgetRenderActivity)
                Log.d("WidgetRender", "loaded data: hasTable=${data.hasTable}, " +
                    "days=${data.days.size}, maxNode=${data.days.flatMap { it.courses }.maxOfOrNull { it.startNode + it.step - 1 } ?: 0}")

                // render bitmap (360x600dp fixed size = real widget size)
                val density = resources.displayMetrics.density
                val w = (360 * density).toInt()
                val h = (600 * density).toInt()
                Log.d("WidgetRender", "density=$density, px=${w}x${h}")
                val bmp = WeekGridWidgetProvider.renderBitmap(this@WidgetRenderActivity, data, w, h)

                // show
                findViewById<ImageView>(R.id.widget_bitmap).setImageBitmap(bmp)
                Log.d("WidgetRender", "render complete")
            } catch (e: Exception) {
                Log.e("WidgetRender", "FAILED", e)
                findViewById<ImageView>(R.id.widget_bitmap).setBackgroundColor(Color.RED)
            }
        }
    }

    private suspend fun seedMockData() {
        val app = SleepyApp.get()
        val repo = app.repository

        val existing = repo.getDefaultTable()
        if (existing != null) {
            Log.d("WidgetRender", "DB already has default table id=${existing.id}, nodesPerDay=${existing.nodesPerDay}")
            val allCourses = (1..7).flatMap { dow ->
                repo.getCoursesByDayOnce(existing.id, dow)
            }
            if (allCourses.isNotEmpty()) {
                Log.d("WidgetRender", "DB has ${allCourses.size} courses, maxNode=${existing.nodesPerDay}")
                return
            }
            seedCourses(repo, existing.id)
            return
        }

        val table = TimeTableEntity(
            name = "测试课表",
            startDate = "2026-02-23",
            maxWeek = 20,
            nodesPerDay = 24,
            timeJson = TIME_JSON_24,
            isDefault = true
        )
        val tableId = repo.insertTable(table)
        Log.d("WidgetRender", "inserted table id=$tableId (24 节课表)")
        seedCourses(repo, tableId)
    }

    /** 24 节课表 timeJson (用户原话: "我让你是24个节次是测试的") */
    private val TIME_JSON_24: String = """
        [
            {"node":1,"start":"08:00","end":"08:45"},
            {"node":2,"start":"08:55","end":"09:40"},
            {"node":3,"start":"10:00","end":"10:45"},
            {"node":4,"start":"10:55","end":"11:40"},
            {"node":5,"start":"14:00","end":"14:45"},
            {"node":6,"start":"14:55","end":"15:40"},
            {"node":7,"start":"16:00","end":"16:45"},
            {"node":8,"start":"16:55","end":"17:40"},
            {"node":9,"start":"19:00","end":"19:45"},
            {"node":10,"start":"19:55","end":"20:40"},
            {"node":11,"start":"20:50","end":"21:35"},
            {"node":12,"start":"21:45","end":"22:30"},
            {"node":13,"start":"08:00","end":"08:45"},
            {"node":14,"start":"08:55","end":"09:40"},
            {"node":15,"start":"10:00","end":"10:45"},
            {"node":16,"start":"10:55","end":"11:40"},
            {"node":17,"start":"14:00","end":"14:45"},
            {"node":18,"start":"14:55","end":"15:40"},
            {"node":19,"start":"16:00","end":"16:45"},
            {"node":20,"start":"16:55","end":"17:40"},
            {"node":21,"start":"19:00","end":"19:45"},
            {"node":22,"start":"19:55","end":"20:40"},
            {"node":23,"start":"20:50","end":"21:35"},
            {"node":24,"start":"21:45","end":"22:30"}
        ]
    """.trimIndent()

    private suspend fun seedCourses(repo: com.lingion.sleepy.data.repository.ScheduleRepository, tableId: Long) {
        fun course(courseName: String, day: Int, startNode: Int, step: Int, color: String) =
            CourseEntity(
                id = 0, groupId = "mock-$day-$startNode", tableId = tableId,
                courseName = courseName, room = "教室$day", day = day,
                startWeek = 1, endWeek = 18, startNode = startNode, step = step,
                type = 0, color = color
            )

        // ★ v19 暴力测试 — 24 节课, 24 个时间段
        val mockCourses = listOf(
            // ========== Period 1-6: 短课 ==========
            course("P1-1节", 1, 1, 1, "#FF6750A4"),
            course("P2-2节", 1, 2, 2, "#FF7D5260"),
            course("P4-1节", 2, 4, 1, "#FF4A6741"),
            course("P1-3节", 3, 1, 3, "#FFFBE4C6"),
            course("P5-2节", 4, 5, 2, "#FFB4A8"),
            course("P3-1节", 5, 3, 1, "#FF59CD6E"),

            // ========== Period 7-12: 中等长度 ==========
            course("P7-5节", 1, 7, 5, "#FFB69DF8"),
            course("P8-3节", 2, 8, 3, "#FFB4A8"),
            course("P10-1节", 3, 10, 1, "#FF006A6A"),
            course("P9-2节", 4, 9, 2, "#FF59CD6E"),
            course("P11-1节", 5, 11, 1, "#FFFBE4C6"),
            course("P12-12节", 6, 1, 12, "#FF8B5CF6"),

            // ========== Period 13-18: 较长 ==========
            course("P13-5节", 1, 13, 5, "#FF59CD6E"),
            course("P14-1节", 2, 14, 1, "#FF7D5260"),
            course("P15-2节", 3, 15, 2, "#FFB69DF8"),
            course("P16-3节", 5, 16, 3, "#FF4A6741"),
            course("P18-7节", 7, 12, 7, "#FF0061A4"),

            // ========== Period 19-24: 超长 ==========
            course("P19-1节", 1, 19, 1, "#FFFBE4C6"),
            course("P20-2节", 2, 20, 2, "#FF0061A4"),
            course("P22-1节", 3, 22, 1, "#FF6750A4"),
            course("P24-24节", 4, 1, 24, "#FFE91E63")
        )

        val ids = repo.insertCourses(mockCourses)
        Log.d("WidgetRender", "inserted ${ids.size} mock courses")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}