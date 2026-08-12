package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // DB 读必须在 IO 线程
        val data = withContext(Dispatchers.IO) { loadWidgetData(context) }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            WidgetContent(
                data = data,
                openAppAction = actionStartActivity(openAppIntent),
                openCourseAction = { courseId ->
                    actionStartActivity(MainActivity.intentForCourse(context, courseId))
                }
            )
        }
    }

    /**
     * 拉数据组装 [WidgetData]：
     * 1. 找默认课表
     * 2. 算当前周次
     * 3. 拉今天 day-of-week 的所有课程
     * 4. 过滤掉不在当前周次的
     * 5. 按节次排序
     * 6. 读 app 的深色模式 → 喂给小组件配色
     *
     * 任何一步失败 / 无数据都返回安全的空状态。
     */
    private suspend fun loadWidgetData(context: Context): WidgetData {
        val today = LocalDate.now()
        val dayOfWeek = DateUtils.todayDayOfWeek(today)
        val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
        val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
        val themeMode = com.lingion.sleepy.util.AppPrefs.getThemeMode(context)
        android.util.Log.d("TodayWidget", "DIAG: isDark=$isDark isSystemDark=$isSystemDark themeMode=$themeMode themeKey=$themeKey")
        return try {
            val app = SleepyApp.get()
            val repo = app.repository

            val table = WidgetTableResolver.resolveCurrentTable()
            if (table == null) {
                WidgetData(date = today, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey)
            } else {
                val week = DateUtils.currentWeek(table.startDate, today)
                val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                WidgetData(date = today, courses = visible, timeJson = table.timeJson, hasTable = true, isDark = isDark, themeKey = themeKey)
            }
        } catch (_: Throwable) {
            WidgetData(date = today, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    /**
     * ★ OPPO 救援: 父类 onUpdate 走 GlanceAppWidgetManager 查 glanceId → OPPO 上返回空
     * → 静默跳过 → Glance widget 不重渲染 → 用户点刷新没反应 / 主题不跟随。
     *
     * 这里完全接管 onUpdate: goAsync() 续命 + 后台直接 widget.update(gid) 绕过该 bug。
     * ★ 不调 super.onUpdate(): 父类内部会再走一遍有 OPPO bug 的管线(且抢同一 session 锁
     *   与 rescue 串行排队 → 多等一轮 provideGlance)。rescue 已覆盖父类唯一职责(触发更新),
     *   跳过它消除重复路径 → 刷新更快。
     *
     * 仅系统直发的 APPWIDGET_UPDATE(主题切换/系统周期刷新)走这里;
     * App 内"刷新小组件"按钮走 WidgetUpdater.notifyDataChanged 的 Path B 直更,不经本方法。
     */
    private val rescueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        rescueScope.launch {
            try {
                WidgetUpdater.updateGlanceWidgetDirect(context, appWidgetIds, glanceAppWidget)
            } finally {
                pending.finish()
            }
        }
    }
}
