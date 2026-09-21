package com.lingion.sleepy.ui.screen.mine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.widget.WidgetTableResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class DailyReminderTimeTarget { Today, Tomorrow }

/** 真实课表存在时，提醒设置页用来生成示例的最小数据集。 */
private data class ReminderSchedulePreview(
    val today: ReminderDayPreview,
    val tomorrow: ReminderDayPreview,
    val nextClass: ReminderCoursePreview?
)

private data class ReminderDayPreview(
    val date: LocalDate,
    val courses: List<CourseEntity>,
    val firstCourse: ReminderCoursePreview?
)

private data class ReminderCoursePreview(
    val date: LocalDate,
    val course: CourseEntity,
    val startTime: String
)

private suspend fun loadReminderSchedulePreview(): ReminderSchedulePreview? = withContext(Dispatchers.IO) {
    val table = runCatching { WidgetTableResolver.resolveCurrentTable() }.getOrNull() ?: return@withContext null
    val allCourses = runCatching {
        SleepyApp.get().repository.getCourses(table.id)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext null
    val nodes = TimeTableUtils.parseNodes(table.timeJson)
    val today = LocalDate.now()

    fun coursesOn(date: LocalDate): List<CourseEntity> {
        if (DateUtils.semesterStatus(table.startDate, table.maxWeek, date) != DateUtils.SemesterStatus.IN_RANGE) {
            return emptyList()
        }
        val week = DateUtils.currentWeek(table.startDate, date)
        return allCourses
            .filter { it.day == DateUtils.todayDayOfWeek(date) && it.inWeek(week) }
            .sortedWith(compareBy<CourseEntity> { courseStartTime(it, nodes) ?: LocalTime.MAX }.thenBy { it.startNode })
    }

    fun dayPreview(date: LocalDate): ReminderDayPreview {
        val courses = coursesOn(date)
        val first = courses.firstOrNull()?.let { course ->
            ReminderCoursePreview(
                date = date,
                course = course,
                startTime = courseStartTime(course, nodes)?.format(PREVIEW_TIME_FORMAT) ?: "--:--"
            )
        }
        return ReminderDayPreview(date = date, courses = courses, firstCourse = first)
    }

    var nextClass: ReminderCoursePreview? = null
    val searchDays = table.maxWeek.coerceAtLeast(1) * 7 + 7
    for (offset in 0..searchDays) {
        val date = today.plusDays(offset.toLong())
        val candidate = coursesOn(date)
            .mapNotNull { course ->
                val start = courseStartTime(course, nodes) ?: return@mapNotNull null
                if (offset == 0 && !start.isAfter(LocalTime.now())) return@mapNotNull null
                ReminderCoursePreview(date, course, start.format(PREVIEW_TIME_FORMAT))
            }
            .minByOrNull { it.startTime }
        if (candidate != null) {
            nextClass = candidate
            break
        }
    }

    ReminderSchedulePreview(
        today = dayPreview(today),
        tomorrow = dayPreview(today.plusDays(1)),
        nextClass = nextClass
    )
}

private fun courseStartTime(course: CourseEntity, nodes: List<TimeTableUtils.NodeTime>): LocalTime? {
    if (course.ownTime && course.startTime.isNotBlank()) {
        return runCatching {
            LocalTime.parse(course.startTime, DateTimeFormatter.ofPattern("H:mm"))
        }.getOrNull()
    }
    return nodes.find { it.node == course.startNode }?.start
}

private val PREVIEW_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun buildDailyPreviewText(
    context: Context,
    day: ReminderDayPreview,
    dateLabelRes: Int
): String {
    val dateLabel = context.getString(
        dateLabelRes,
        DateUtils.shortDateSlash(day.date),
        DateUtils.localizedDay(day.date.dayOfWeek.value, context)
    )
    val first = day.firstCourse ?: return context.getString(
        R.string.reminder_daily_preview_dynamic_no_course,
        dateLabel
    )
    val courseName = first.course.courseName.ifBlank { context.getString(R.string.default_course_name) }
    val room = first.course.room.ifBlank { context.getString(R.string.notif_room_unknown) }
    val teacher = first.course.teacher.trim().takeIf { it.isNotEmpty() }?.let {
        context.getString(R.string.reminder_preview_teacher, it)
    }.orEmpty()
    return context.getString(
        R.string.reminder_daily_preview_dynamic,
        dateLabel,
        day.courses.size,
        courseName,
        first.startTime,
        room,
        teacher
    )
}

private fun buildBeforeClassPreviewText(
    context: Context,
    preview: ReminderSchedulePreview
): String {
    val next = preview.nextClass ?: return context.getString(R.string.reminder_before_class_preview_dynamic_no_course)
    val dateLabel = context.getString(
        R.string.reminder_preview_date,
        DateUtils.shortDateSlash(next.date),
        DateUtils.localizedDay(next.date.dayOfWeek.value, context)
    )
    val courseName = next.course.courseName.ifBlank { context.getString(R.string.default_course_name) }
    val room = next.course.room.ifBlank { context.getString(R.string.notif_room_unknown) }
    val teacher = next.course.teacher.trim().takeIf { it.isNotEmpty() }?.let {
        context.getString(R.string.reminder_preview_teacher, it)
    }.orEmpty()
    return context.getString(
        R.string.reminder_before_class_preview_dynamic,
        dateLabel,
        courseName,
        next.startTime,
        room,
        teacher
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var masterEnabled by remember { mutableStateOf(AppPrefs.isReminderEnabled(context)) }
    var dailyEnabled by remember { mutableStateOf(AppPrefs.isDailyReminderEnabled(context)) }
    var todayEnabled by remember { mutableStateOf(AppPrefs.isTodayReminderEnabled(context)) }
    var dailyTime by remember { mutableStateOf(AppPrefs.getDailyReminderTime(context)) }
    var tomorrowEnabled by remember { mutableStateOf(AppPrefs.isTomorrowReminderEnabled(context)) }
    var tomorrowTime by remember { mutableStateOf(AppPrefs.getTomorrowReminderTime(context)) }
    var beforeClassEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassEnabled(context)) }
    var beforeClassMinutes by remember { mutableStateOf(AppPrefs.getBeforeClassMinutes(context)) }
    var timePickerTarget by remember { mutableStateOf<DailyReminderTimeTarget?>(null) }
    var minutesInput by remember { mutableStateOf(beforeClassMinutes.toString()) }
    var fluidEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassFluidEnabled(context)) }
    var bannerEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassBannerEnabled(context)) }
    var fluidPrimary by remember { mutableStateOf(AppPrefs.getBeforeClassFluidPrimary(context)) }
    var fieldsMenuExpanded by remember { mutableStateOf(false) }
    var schedulePreview by remember { mutableStateOf<ReminderSchedulePreview?>(null) }

    // 示例只读取当前课表，不参与提醒调度；无可分析课表时保留资源中的通用示例。
    LaunchedEffect(Unit) {
        schedulePreview = loadReminderSchedulePreview()
    }

    val todayPreviewText = schedulePreview?.let {
        buildDailyPreviewText(context, it.today, R.string.reminder_preview_today_date)
    } ?: stringResource(R.string.reminder_daily_preview)
    val tomorrowPreviewText = schedulePreview?.let {
        buildDailyPreviewText(context, it.tomorrow, R.string.reminder_preview_tomorrow_date)
    } ?: stringResource(R.string.reminder_tomorrow_preview)
    val beforeClassPreviewText = schedulePreview?.let {
        buildBeforeClassPreviewText(context, it)
    } ?: stringResource(R.string.reminder_before_class_preview)

    // debounce：分钟输入停止 500ms 后才持久化并重排提醒，
    //   避免每敲一键就触发一次全量 cancelAll + scheduleAll（查库 + 重排全部闹钟）。
    LaunchedEffect(minutesInput) {
        if (minutesInput.isBlank()) return@LaunchedEffect
        delay(500)
        val v = minutesInput.toIntOrNull()?.coerceIn(1, 999) ?: return@LaunchedEffect
        beforeClassMinutes = v
        AppPrefs.setBeforeClassMinutes(context, v)
        SleepyApp.get().notificationScheduler.scheduleAll()
    }

    // Permission launcher — NOT one-shot, can be re-triggered by clicking toggle again
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            SleepyApp.get().notificationScheduler.scheduleAll()
        } else {
            // Permission denied → revert to off
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13: permission auto-granted at install
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            SleepyApp.get().notificationScheduler.scheduleAll()
        }
    }

    fun onMasterToggle(on: Boolean) {
        if (on) {
            // Check if already granted
            val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true

            if (alreadyGranted) {
                masterEnabled = true
                AppPrefs.setReminderEnabled(context, true)
                SleepyApp.get().notificationScheduler.scheduleAll()
            } else {
                requestNotificationPermission()
            }
        } else {
            // 关闭 master 只设 reminder_master=false + cancelAll(); scheduleAll 与各 Receiver 均双重检查
            //   isReminderEnabled, 无需覆写子开关(否则重开 master 后 daily/beforeClass 配置全丢)。
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
            // cancelAll 现为 suspend，由 IO 协程调用，避免主线程查库阻塞
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                SleepyApp.get().notificationScheduler.cancelAll()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master toggle card
            item {
                ReminderCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconBox(icon = Icons.Outlined.Notifications, color = colors.primary)
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.reminder_master_title),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.reminder_master_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = { onMasterToggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary
                            )
                        )
                    }
                }
            }

            // Sub-settings — only visible when master is on
            if (masterEnabled) {
                // Daily reminder — single card with parent switch in header + expandable sub items
                item {
                    ReminderCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBox(icon = Icons.Outlined.AccessTime, color = colors.primary)
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.reminder_daily_title),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.reminder_daily_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dailyEnabled,
                                onCheckedChange = { enabled ->
                                    dailyEnabled = enabled
                                    AppPrefs.setDailyReminderEnabled(context, enabled)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }

                        if (dailyEnabled) {
                            SubDivider()
                            ReminderToggleRow(
                                title = stringResource(R.string.reminder_daily_today_toggle_title),
                                subtitle = stringResource(R.string.reminder_daily_today_toggle_sub),
                                checked = todayEnabled,
                                onCheckedChange = { enabled ->
                                    todayEnabled = enabled
                                    AppPrefs.setTodayReminderEnabled(context, enabled)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                }
                            )
                            ReminderTimeRow(
                                label = stringResource(R.string.reminder_daily_time_label),
                                time = dailyTime,
                                onClick = { timePickerTarget = DailyReminderTimeTarget.Today }
                            )
                            Text(
                                text = todayPreviewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp, end = 4.dp)
                            )
                            SubDivider()
                            ReminderToggleRow(
                                title = stringResource(R.string.reminder_tomorrow_toggle_title),
                                subtitle = stringResource(R.string.reminder_tomorrow_toggle_sub),
                                checked = tomorrowEnabled,
                                onCheckedChange = { enabled ->
                                    tomorrowEnabled = enabled
                                    AppPrefs.setTomorrowReminderEnabled(context, enabled)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                }
                            )
                            ReminderTimeRow(
                                label = stringResource(R.string.reminder_tomorrow_time_label),
                                time = tomorrowTime,
                                onClick = { timePickerTarget = DailyReminderTimeTarget.Tomorrow }
                            )
                            Text(
                                text = tomorrowPreviewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp, end = 4.dp)
                            )
                        }
                    }
                }

                // Before-class reminder
                item {
                    ReminderCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconBox(icon = Icons.Outlined.School, color = colors.primary)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.reminder_before_class_title),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.reminder_before_class_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = beforeClassEnabled,
                                onCheckedChange = { on ->
                                    beforeClassEnabled = on
                                    AppPrefs.setBeforeClassEnabled(context, on)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }

                        if (beforeClassEnabled) {
                            SubDivider()
                            // Free-input minutes field
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_before_minutes_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextField(
                                    value = minutesInput,
                                    onValueChange = { txt ->
                                        val digits = txt.filter { it.isDigit() }
                                        if (digits.isEmpty()) {
                                            minutesInput = ""
                                        } else {
                                            val v = digits.toIntOrNull() ?: 0
                                            if (v <= 999) minutesInput = digits
                                        }
                                    },
                                    modifier = Modifier.width(120.dp),
                                    shape = SleepyTheme.fieldShape,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    suffix = {
                                        Text(
                                            text = stringResource(R.string.reminder_before_minutes_unit),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurfaceVariant
                                        )
                                    },
                                    colors = SleepyTheme.fieldColors()
                                )
                            }
                            SubDivider()
                            Text(
                                text = beforeClassPreviewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                            )
                            SubDivider()
                            ReminderToggleRow(
                                title = stringResource(R.string.reminder_banner_title),
                                subtitle = stringResource(R.string.reminder_banner_sub),
                                checked = bannerEnabled,
                                onCheckedChange = {
                                    bannerEnabled = it
                                    AppPrefs.setBeforeClassBannerEnabled(context, it)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                }
                            )
                            SubDivider()
                            ReminderToggleRow(
                                title = stringResource(R.string.reminder_fluid_title),
                                subtitle = stringResource(R.string.reminder_fluid_sub),
                                checked = fluidEnabled,
                                onCheckedChange = {
                                    fluidEnabled = it
                                    AppPrefs.setBeforeClassFluidEnabled(context, it)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                }
                            )
                            if (fluidEnabled) {
                                SubDivider()
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.reminder_fluid_fields),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SleepyTheme.fieldShape)
                                            .noRippleClickable { fieldsMenuExpanded = true }
                                    ) {
                                        TextField(
                                            value = fluidPrimaryLabel(context, fluidPrimary),
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.reminder_fluid_fields_hint)) },
                                            trailingIcon = {
                                                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.onSurfaceVariant)
                                            },
                                            shape = SleepyTheme.fieldShape,
                                            colors = SleepyTheme.fieldColors()
                                        )
                                        DropdownMenu(
                                            expanded = fieldsMenuExpanded,
                                            onDismissRequest = { fieldsMenuExpanded = false },
                                            // 菜单浮在 surfaceContainer 卡片上, 用 Highest 拉开对比(默认 High 与卡片几乎同色=隐形)
                                            containerColor = colors.surfaceContainerHighest
                                        ) {
                                            listOf(
                                                "name" to R.string.reminder_fluid_field_name,
                                                "time" to R.string.reminder_fluid_field_time,
                                                "room" to R.string.reminder_fluid_field_room
                                            ).forEach { (key, labelRes) ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(labelRes)) },
                                                    onClick = {
                                                        fluidPrimary = key
                                                        AppPrefs.setBeforeClassFluidPrimary(context, key)
                                                        SleepyApp.get().notificationScheduler.scheduleAll()
                                                        fieldsMenuExpanded = false
                                                    },
                                                    leadingIcon = {
                                                        RadioButton(
                                                            selected = key == fluidPrimary,
                                                            onClick = null
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.reminder_fluid_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // The same picker edits either daily-summary time without duplicating its behavior.
    timePickerTarget?.let { target ->
        val selectedTime = when (target) {
            DailyReminderTimeTarget.Today -> dailyTime
            DailyReminderTimeTarget.Tomorrow -> tomorrowTime
        }
        val parts = selectedTime.split(":")
        val timeState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: if (target == DailyReminderTimeTarget.Today) 7 else 22,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(stringResource(R.string.reminder_pick_time)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    // 默认 TimePicker 配色 — 与 TimePickerField 弹窗一致, 不再单独覆写表盘色
                    TimePicker(state = timeState)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.action_confirm),
                        onConfirm = {
                            val h = String.format("%02d", timeState.hour)
                            val m = String.format("%02d", timeState.minute)
                            val newTime = "$h:$m"
                            when (target) {
                                DailyReminderTimeTarget.Today -> {
                                    dailyTime = newTime
                                    AppPrefs.setDailyReminderTime(context, newTime)
                                }
                                DailyReminderTimeTarget.Tomorrow -> {
                                    tomorrowTime = newTime
                                    AppPrefs.setTomorrowReminderTime(context, newTime)
                                }
                            }
                            SleepyApp.get().notificationScheduler.scheduleAll()
                            timePickerTarget = null
                        },
                        dismissText = stringResource(R.string.action_cancel),
                        onDismiss = { timePickerTarget = null }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {},
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ReminderTimeRow(label: String, time: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        Text(
            text = time,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.primary
        )
    }
}

@Composable
private fun ReminderToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        // 补主题色：之前无 colors 参数走默认 Material3 蓝，与同屏三个主开关不一致
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary
            )
        )
    }
}

private fun fluidPrimaryLabel(context: android.content.Context, primary: String): String =
    context.getString(
        when (primary) {
            "name" -> R.string.reminder_fluid_field_name
            "time" -> R.string.reminder_fluid_field_time
            else -> R.string.reminder_fluid_field_room
        }
    )

@Composable
private fun ReminderCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun IconBox(icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(SleepyTheme.shapes.small)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SubDivider() {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = colors.outline.copy(alpha = SleepyTheme.Alpha.hairline)
    )
}
