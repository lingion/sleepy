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
                courseName = name, teacher = "张老师",
                room = "A${100 + day * 10 + startNode}",
                day = day, startNode = startNode, step = step,
                startWeek = 1, endWeek = 18, type = 0, color = color
            )

        // ★ v1.0.16-rebuild-10 暴力测试 — 25 节课跨所有长度
        // step 范围: 1(短) / 2 / 3 / 5 / 10(超长，整上午)
        val mockCourses = listOf(
            // 周一 (5 节课: 1节/2节/3节/5节/10节)
            course("短课-单节", 1, 1, 1, "#FF6750A4"),
            course("大学英语(二)", 1, 2, 2, "#FF6750A4"),
            course("工科数学-3节", 1, 4, 3, "#FFB4A8"),
            course("长课-5节", 1, 7, 5, "#FFE91E63"),

            // 周二 (4 节课)
            course("概率论-2节", 2, 1, 2, "#FF6750A4"),
            course("数学分析-10节-全上午", 2, 3, 10, "#FFB4A8"),
            course("军事理论-2节", 2, 8, 2, "#FF59CD6E"),

            // 周三 (4 节课)
            course("体育-2节", 3, 1, 2, "#FF59CD6E"),
            course("概率论-2节", 3, 3, 2, "#FF6750A4"),
            course("短课-单节2", 3, 5, 1, "#FF6750A4"),
            course("体育-5节", 3, 6, 5, "#FF59CD6E"),

            // 周四 (4 节课)
            course("短课-1节", 4, 1, 1, "#FF6750A4"),
            course("思政-2节", 4, 2, 2, "#FFFBE4C6"),
            course("形势政策-3节", 4, 5, 3, "#FFFBE4C6"),
            course("军事理论-2节", 4, 9, 2, "#FF59CD6E"),

            // 周五 (4 节课)
            course("大学英语-2节", 5, 1, 2, "#FF6750A4"),
            course("工科数学-2节", 5, 3, 2, "#FFB4A8"),
            course("体育-2节", 5, 5, 2, "#FF59CD6E"),
            course("短课-1节", 5, 9, 1, "#FF6750A4"),

            // 周六 (2 节课)
            course("短课-单节", 6, 1, 1, "#FF6750A4"),
            course("长课-5节", 6, 4, 5, "#FFE91E63"),

            // 周日 (2 节课)
            course("短课-单节", 7, 1, 1, "#FF6750A4"),
            course("长课-5节", 7, 6, 5, "#FFE91E63"),
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
