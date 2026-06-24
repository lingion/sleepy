package com.lingion.sleepy.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 真实 Glance Widget 渲染验证（带 mock 数据 + 正确尺寸）。
 * 流程：seed → allocate → bind(onUpdate→Glance用默认options) → updateOptions → re-trigger update → createView
 */
class WidgetRenderActivity : ComponentActivity() {

    private var host: AppWidgetHost? = null
    private var allocatedId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                seedMockData()

                val awm = AppWidgetManager.getInstance(this@WidgetRenderActivity)
                val cn = ComponentName(this@WidgetRenderActivity, WeekGridWidgetReceiver::class.java)
                val providerInfo = awm.installedProviders.find { it.provider == cn }
                if (providerInfo == null) {
                    Log.e("WidgetRender", "provider not found"); redScreen(); return@launch
                }

                host = AppWidgetHost(this@WidgetRenderActivity, 1976)
                host?.startListening()
                allocatedId = host?.allocateAppWidgetId() ?: -1
                Log.d("WidgetRender", "allocatedId=$allocatedId")
                if (allocatedId == -1) { redScreen(); return@launch }

                // ★ 注入尺寸到 WeekGridWidget（绕过 AppWidgetManager 默认 options）
                val widgetW_dp = 418
                val widgetH_dp = 643
                WeekGridWidget.overrideSizeDp = Pair(widgetW_dp, widgetH_dp)

                // 1. bind → 触发 onUpdate（此时 overrideSizeDp 已设好）
                val bound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    awm.bindAppWidgetIdIfAllowed(allocatedId, cn)
                } else false
                Log.d("WidgetRender", "bound=$bound")

                // 2. updateAppWidgetOptions
                val opts = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widgetW_dp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, widgetH_dp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widgetW_dp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, widgetH_dp)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                    }
                }
                try {
                    awm.updateAppWidgetOptions(allocatedId, opts)
                    Log.d("WidgetRender", "updateAppWidgetOptions SUCCESS: ${widgetW_dp}x${widgetH_dp}")
                } catch (e: Exception) {
                    Log.e("WidgetRender", "updateAppWidgetOptions failed", e)
                }

                // 3. 重触发 widget update → provideGlance 重新读 options
                val ids = intArrayOf(allocatedId)
                val widget = WeekGridWidget()
                val updateIntent = Intent(this@WidgetRenderActivity, WeekGridWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                sendBroadcast(updateIntent)
                Log.d("WidgetRender", "sent ACTION_APPWIDGET_UPDATE for id=$allocatedId")

                // 等待 Glance 异步渲染
                delay(3000)

                // 4. createView
                val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getMetrics(metrics)
                val density = metrics.density
                val widgetW_px = (widgetW_dp.toFloat() * density).toInt()
                val widgetH_px = (widgetH_dp.toFloat() * density).toInt()
                Log.d("WidgetRender", "density=$density, px=${widgetW_px}x${widgetH_px}")

                val hostView = host?.createView(this@WidgetRenderActivity, allocatedId, providerInfo)
                    ?: run { Log.e("WidgetRender", "createView failed"); redScreen(); return@launch }

                hostView.updateAppWidgetOptions(opts)

                val container = FrameLayout(this@WidgetRenderActivity).apply {
                    setBackgroundColor(0xFF1A1A2E.toInt())
                    addView(hostView, FrameLayout.LayoutParams(widgetW_px, widgetH_px).apply {
                        gravity = android.view.Gravity.CENTER
                    })
                }
                setContentView(container)
                Log.d("WidgetRender", "hostView ready")

                hostView.postDelayed({
                    hostView.invalidate()
                    Log.d("WidgetRender", "refresh done")
                }, 2000)

            } catch (e: Exception) {
                Log.e("WidgetRender", "FAILED", e)
                redScreen()
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
        fun course(name: String, day: Int, startNode: Int, step: Int, color: String) =
            CourseEntity(
                id = 0, groupId = "mock-$day-$startNode", tableId = tableId,
                courseName = name, teacher = "张老师",
                room = "A${100 + day * 10 + startNode}",
                day = day, startNode = startNode, step = step,
                startWeek = 1, endWeek = 18, type = 0, color = color
            )

        // ★ v18 暴力测试 — 24 节课, 24 个时间段 (用户原话: "我让你是24个节次是测试的")
        // 要求: 1节/2节/3节/5节/10节/12节/24节 各种长度混合, 全部撑满 Period 1-24
        val mockCourses = listOf(
            // ========== Period 1-6: 短课 + 起始节 ==========
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
            course("P12-12节", 6, 1, 12, "#FF8B5CF6"),  // 整上午跨 P1-P12

            // ========== Period 13-18: 较长 ==========
            course("P13-5节", 1, 13, 5, "#FF59CD6E"),
            course("P14-1节", 2, 14, 1, "#FF7D5260"),
            course("P15-2节", 3, 15, 2, "#FFB69DF8"),
            course("P16-3节", 5, 16, 3, "#FF4A6741"),
            course("P18-7节", 7, 12, 7, "#FF0061A4"),  // 跨 P12-P18 (7节)

            // ========== Period 19-24: 超长 ==========
            course("P19-1节", 1, 19, 1, "#FFFBE4C6"),
            course("P20-2节", 2, 20, 2, "#FF0061A4"),
            course("P22-1节", 3, 22, 1, "#FF6750A4"),
            course("P24-24节", 4, 1, 24, "#FFE91E63")  // ★ 整 widget 撑满 1门课跨 P1-P24
        )

        val ids = repo.insertCourses(mockCourses)
        Log.d("WidgetRender", "inserted ${ids.size} violent test courses, tableId=$tableId")
    }

    private fun redScreen() {
        runOnUiThread {
            setContentView(FrameLayout(this@WidgetRenderActivity).apply {
                setBackgroundColor(0xFFFF0000.toInt())
            })
        }
    }

    override fun onStart() { super.onStart(); host?.startListening() }
    override fun onStop() { super.onStop(); host?.stopListening() }
    override fun onDestroy() {
        super.onDestroy()
        host?.stopListening()
        if (allocatedId != -1) host?.deleteAppWidgetId(allocatedId)
    }
}
