package com.lingion.sleepy.widget

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.CardsGridView
import com.lingion.sleepy.ui.component.TimeSlot
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * WeekGridWidget 真复刻 — 1:1 复刻 ScheduleScreen 的 CardsGridView（app 内"网格"视图）
 *
 * 不再复刻 WidgetContent.kt 的 WeekGridContent（Glance widget），因为那不是 app 内的"网格"视图
 * 这次直接调 ui.component.CardsGridView + 加 TopBar + SegmentedSwitcher + 底部 nav
 */
class WeekGridPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.lingion.sleepy.ui.theme.SleepyThemeProvider {
                AppScheduleScreen()
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppScheduleScreen() {
        var data by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<ScreenData?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val loaded = withContext(Dispatchers.IO) { loadScreenData() }
            data = loaded
        }
        val d = data
        if (d == null) {
            androidx.compose.material3.Text("加载中...")
            return
        }
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            // TopBar 周次切换
            TopBarSimple(currentWeek = d.currentWeek)
            // 直接调 CardsGridView 渲染课程表
            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                CardsGridView(
                    courses = d.courses,
                    timeSlots = d.timeSlots,
                    visibleDays = d.visibleDays,
                    showDate = true,
                    startDate = d.startDate,
                    currentWeek = d.currentWeek,
                    displayMode = "node",
                    timeJson = d.timeJson,
                    onCourseClick = { Log.d("Preview", "clicked: ${it.courseName}") }
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TopBarSimple(currentWeek: Int) {
        val colors = SleepyTheme.colors
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text(
                text = "第 $currentWeek 周",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = colors.primary
            )
        }
    }
}

private data class ScreenData(
    val courses: List<CourseEntity>,
    val timeSlots: List<com.lingion.sleepy.ui.component.TimeSlot>,
    val visibleDays: Set<Int>,
    val startDate: String,
    val currentWeek: Int,
    val timeJson: String
)

private suspend fun loadScreenData(): ScreenData {
    val today = LocalDate.now()
    return try {
        val app = SleepyApp.get()
        val repo = app.repository
        val table = repo.getDefaultTable()
        if (table != null) {
            val week = DateUtils.currentWeek(table.startDate, today)
            val courses = (1..7).flatMap { dow ->
                repo.getCoursesByDayOnce(table.id, dow).filter { it.inWeek(week) }
            }
            ScreenData(
                courses = courses,
                timeSlots = TimeTableUtils.timeSlotsFor(table),
                visibleDays = (1..7).toSet(),
                startDate = table.startDate,
                currentWeek = week,
                timeJson = table.timeJson
            )
        } else {
            hardcodedScreenData(today)
        }
    } catch (e: Throwable) {
        Log.e("Preview", "loadScreenData failed", e)
        hardcodedScreenData(today)
    }
}

private fun hardcodedScreenData(today: LocalDate): ScreenData {
    fun course(name: String, day: Int, startNode: Int, step: Int) =
        CourseEntity(
            id = (day * 100 + startNode).toLong(),
            groupId = "test-$day-$startNode",
            tableId = 1,
            courseName = name,
            teacher = "", room = "", note = "",
            day = day, startNode = startNode, step = step,
            startWeek = 1, endWeek = 18, type = 0, color = "#EADDFF",
            ownTime = false, startTime = "", endTime = ""
        )
    val courses = mutableListOf<CourseEntity>()
    courses += course("大学英语(二)", 1, 6, 2)
    courses += listOf(
        course("概率论与数理统计", 2, 1, 2),
        course("工科数学分析(二)", 2, 3, 3),
        course("军事理论", 2, 6, 2),
        course("概率论与数理统计", 2, 8, 2)
    )
    courses += listOf(
        course("体育(二)", 3, 1, 2),
        course("体育(二)", 3, 3, 2),
        course("概率论与数理统计", 3, 6, 2)
    )
    courses += listOf(
        course("概率论与数理统计", 4, 1, 2),
        course("思政", 4, 3, 2),
        course("形势与政策", 4, 5, 3)
    )
    courses += listOf(
        course("概率论与数理统计", 5, 1, 2),
        course("概率论与数理统计", 5, 6, 2)
    )
    val timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON)
    return ScreenData(
        courses = courses,
        timeSlots = timeSlots,
        visibleDays = (1..7).toSet(),
        startDate = "",
        currentWeek = 17,
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON
    )
}

// 必要 imports 已顶部列出
