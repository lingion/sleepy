package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.flow.filter
import java.time.LocalTime

/**
 * 时段定义 — 5 个时段（对应 HTML 里的 5 个 slot-row）
 * 与 WakeUp 默认 12 节对应: 1-2 / 3-5 / 6-7 / 8-10 / 11-13
 */
data class TimeSlot(
    val label: String,         // "1-2节"
    val start: LocalTime,
    val end: LocalTime,
    val displayStart: String,  // "08:00"
    val displayEnd: String,    // "09:35"
    val nodeStart: Int,
    val nodeEnd: Int
) {
    // nodeString 死属性已删（恒返回 "N-N" 且全库零调用; 界面用的是 CourseEntity.nodeString 本地化版本）
    val timeString: String get() = "$displayStart-$displayEnd"
}

/**
 * Cards 网格视图
 *
 * 架构：
 *   BoxWithConstraints(fillMaxSize) → 算出 colW (dp)
 *     Column(fillMaxWidth, verticalScroll)
 *       Row(表头)            — Compose 自然排版
 *       Box(固定高度 = maxNode * rowH) — 时间栏 + 课程卡片全用 Modifier.offset 绝对定位
 *
 * 关键点：
 * - Column 用 fillMaxWidth（不是 fillMaxSize），内容高度 = 表头 + 固定 gridH，超出视口 → 可滚动
 * - 时间栏 / 卡片都在同一个 Box 内，Modifier.offset 定位 → 滚动完全同步
 * - 全用 dp 算 offset，不碰 px，不碰 Layout measure/place
 */
@Composable
fun CardsGridView(
    courses: List<CourseEntity>,
    timeSlots: List<TimeSlot>,
    visibleDays: Set<Int> = (1..7).toSet(),
    showDate: Boolean = false,
    startDate: String = "",
    currentWeek: Int = 1,
    today: Int = DateUtils.todayDayOfWeek(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet(),  // 本周应灰显的星期几 (1-7)
    topOverrides: Map<String, Long> = emptyMap(),           // v7.10.5: 会话级置顶 override,上提到 ScheduleScreen(radio 瞬时更新写入同一真相源)
    onSetTopOverride: (String, Long?) -> Unit = { _, _ -> } // (clusterKey, layerRepId|null)
) {
    val colors = SleepyTheme.colors
    val maxNode = timeSlots.maxOfOrNull { it.nodeEnd } ?: 12
    val sortedDays = visibleDays.sorted()
    val dayCount = sortedDays.size

    // 设置页改 scale / cornerRatio 后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_GRID_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO
        }.collect { prefVersion++ }
    }

    // issue#8 网格整体缩放: 0.7~1.3, 字号/行高/间距/圆角/内边距等比联动
    // (12节连堂课表缩到 0.7 可一屏放下; 只影响本 Cards 视图, 小组件与列表视图不受影响)
    val scale = AppPrefs.getGridScale(androidx.compose.ui.platform.LocalContext.current)
    val cornerRatio = AppPrefs.getGridCornerRatio(androidx.compose.ui.platform.LocalContext.current)
    val d = { v: Float -> (v * scale).dp }

    // 布局常量（全 dp, 乘 scale）
    val headH = d(52f)
    val timeW = d(68f)
    val slotH = d(52f)
    val gapH = d(4f)
    val gapW = d(5f)
    val rowH = slotH + gapH

    val gridBgShape = SleepyTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh, gridBgShape)
            .padding(d(8f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 算出每列宽度 (dp)
            val colW = (maxWidth - timeW - gapW * (dayCount + 1)) / dayCount
            val gridH = rowH * maxNode   // grid 内容区固定高度

            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // ---- 表头：自然 Compose Row ----
                Row(
                    modifier = Modifier.fillMaxWidth().height(headH),
                    horizontalArrangement = Arrangement.spacedBy(gapW),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(timeW))
                    for (day in sortedDays) {
                        val dateStr = if (showDate && startDate.isNotBlank()) {
                            try {
                                val ds = DateUtils.dateOfWeek(startDate, currentWeek, day)
                                DateUtils.shortDate(ds)
                            } catch (_: Exception) { null }
                        } else null
                        DayHeadCell(
                            day = day,
                            isToday = day == today,
                            isGrey = day in greyDays,
                            courseCount = courses.count { it.day == day },
                            dateStr = dateStr,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            modifier = Modifier.width(colW).fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(gapH))

                // ---- Grid 主体：固定高度 Box，内部全用 Modifier.offset 绝对定位 ----
                Box(modifier = Modifier.fillMaxWidth().height(gridH)) {
                    // 时间栏：每个节次一个 Row，用 offset 定位到正确 y
                    for ((i, slot) in timeSlots.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(slotH)
                                .offset(y = rowH * i),
                            horizontalArrangement = Arrangement.spacedBy(gapW),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleTimeHeadCell(
                                slot = slot,
                                scale = scale,
                                modifier = Modifier.width(timeW).fillMaxHeight(),
                                cornerRatio = cornerRatio
                            )
                            // 透明占位：保证行宽和表头一致
                            for (day in sortedDays) {
                                Spacer(modifier = Modifier.width(colW).fillMaxHeight())
                            }
                        }
                    }

                    // 课程卡片：用 offset 绝对定位 — 冲突簇整簇走 ConflictClusterCard,
                    // 非簇课保持原 CourseOverlayCard 单卡路径(回归保护)
                    val context = LocalContext.current
                    val conflictStyle = AppPrefs.getConflictStyle(context)
                    // v7.10.5: 交换置顶状态上提到 ScheduleScreen — 详情弹窗 radio 点击
                    // 与网格 onPickTop 写同一真相源,radio 勾选瞬间网格同帧换层。
                    // (此前 radio 只写持久化 defaultTopMap,被会话级 topOverrides 遮蔽 → 看似不生效)
                    fun setTopOverride(key: String, courseId: Long?) = onSetTopOverride(key, courseId)

                    // v7.9 默认置顶:跨会话持久化偏好,由详情弹窗 radio 写入。
                    // 与 topOverrides 同簇键,作 topOverrideId fallback — 当次切换仍由 topOverrides 主导。
                    val defaultTopMap by AppPrefs.conflictDefaultTopFlow(context).collectAsState(
                        initial = AppPrefs.getConflictDefaultTop(context)
                    )

                    // 引擎聚簇: 同天区间相交(含链式)课程归簇,簇键=主课判定序首课三元组
                    // (override 不改变首课——findClusters 输出固定,键稳定)
                    val clusters = ConflictLayoutEngine.findClusters(courses)
                    val clusteredIds = clusters.flatMap { c -> c.courses.map { it.id } }.toSet()

                    for (cluster in clusters) {
                        // 簇内课若因 visibleDays/maxNode 过滤全出界则整簇跳过
                        if (cluster.day !in visibleDays) continue
                        val inGrid = cluster.courses.filter {
                            it.startNode in 1..maxNode
                        }
                        if (inGrid.isEmpty()) continue
                        val anchor = cluster.courses.first() // 主课判定序首位,决定簇基点
                        val dayIdx = sortedDays.indexOf(cluster.day)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        val cardY = rowH * (anchor.startNode - 1)
                        val clusterKey = "${cluster.day}:${anchor.startNode}:${anchor.step}"

                        ConflictClusterCard(
                            cluster = cluster,
                            style = conflictStyle,
                            topOverrideId = topOverrides[clusterKey] ?: defaultTopMap[clusterKey],
                            onPickTop = { id -> setTopOverride(clusterKey, id) },
                            onCourseClick = onCourseClick,
                            colW = colW,
                            rowH = rowH,
                            maxNode = maxNode,
                            timeW = timeW,
                            gapW = gapW,
                            gapH = gapH,
                            isGrey = cluster.day in greyDays,
                            modifier = Modifier.offset(x = cardX, y = cardY)
                        )
                    }

                    for (course in courses) {
                        if (course.day !in visibleDays) continue
                        if (course.startNode !in 1..maxNode) continue
                        if (course.id in clusteredIds) continue // 簇内课已由 ConflictClusterCard 绘制
                        val dayIdx = sortedDays.indexOf(course.day)
                        val steps = course.step.coerceAtLeast(1)
                            .coerceAtMost(maxNode - course.startNode + 1)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        val cardY = rowH * (course.startNode - 1)
                        val cardH = rowH * steps - gapH

                        CourseOverlayCard(
                            course = course,
                            onClick = { onCourseClick(course) },
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .width(colW)
                                .height(cardH),
                            isGrey = course.day in greyDays,
                            scale = scale,
                            cornerRatio = cornerRatio
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleTimeHeadCell(slot: TimeSlot, scale: Float = 1f, modifier: Modifier = Modifier, cornerRatio: Float = 1f) {
    val colors = SleepyTheme.colors
    val sd = { v: Float -> (v * scale).dp }
    val shape = RoundedCornerShape(sd(12f * cornerRatio))
    Box(
        modifier = modifier.padding(sd(2f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(shape)
                .background(colors.surfaceContainerLow)
                .padding(sd(4f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.period_format_node, slot.label),
                    style = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold, fontSize = (10 * scale).sp, lineHeight = (14 * scale).sp),
                    color = colors.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(sd(1f)))
                Text(
                    text = slot.timeString,
                    style = SleepyTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// EmptyGridCell 死组件已删（注释自述弃用, 全库零调用——Cards 网格走 SingleTimeHeadCell + CourseOverlayCard）。

@Composable
private fun CourseOverlayCard(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f
) {
    val palette = SleepyTheme.palette
    val colors = SleepyTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    // 统一取色入口（决策 D3）— colorless 读取 AppPrefs course_colorless 独立开关
    val bg = CourseColorUtil.pickCourseColorCompose(
        course = course,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context)
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val shape = RoundedCornerShape((12 * scale * cornerRatio).dp)
    val sd = { v: Float -> (v * scale).dp }
    // 副信息（教室/教师/无）— 左栏 SingleTimeHeadCell 已有节次+时间，卡片 y 位置本身编码节次，
    // 故卡内不再显示节次/时间，改由 grid_sub_info 设置决定
    val subInfo = AppPrefs.getGridSubInfo(context)
    val subText = when (subInfo) {
        "room" -> course.room
        "teacher" -> course.teacher
        else -> ""
    }

    // 节假日灰显：色块叠 alpha + 文字应用 strikethrough 样式
    val effectiveBg = if (isGrey) bg.copy(alpha = SleepyTheme.Alpha.inactive) else bg
    val effectiveFg = if (isGrey) fg.copy(alpha = SleepyTheme.Alpha.inactive) else fg
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") {
        androidx.compose.ui.text.style.TextDecoration.LineThrough
    } else null

    Box(
        modifier = modifier
            .padding(sd(2f))
            .clip(shape)
            .background(effectiveBg)
            .noRippleClickable(onClick)
            .padding(sd(4f))
    ) {
        if (subText.isBlank()) {
            // 无副信息: 课程名整体居中(原行为)
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (10 * scale).sp,
                    lineHeight = (13 * scale).sp,
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 有副信息: 课程名在剩余空间内居中, 副信息贴卡底 — 主文字不再紧贴副文字
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 用 weight(1f) 占位让课程名在上半区居中, 避免正正好好贴住副文字
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (10 * scale).sp,
                            lineHeight = (13 * scale).sp,
                            textDecoration = textDecoration
                        ),
                        color = effectiveFg,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subText,
                    style = SleepyTextStyle.micro().copy(
                        fontSize = (9 * scale).sp,
                        lineHeight = (11 * scale).sp,
                        textDecoration = textDecoration
                    ),
                    color = effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DayHeadCell(day: Int, isToday: Boolean, isGrey: Boolean = false, courseCount: Int, dateStr: String? = null, dayLabel: String = DateUtils.localizedDay(day, androidx.compose.ui.platform.LocalContext.current), modifier: Modifier = Modifier, scale: Float = 1f, cornerRatio: Float = 1f) {
    val colors = SleepyTheme.colors
    val sd = { v: Float -> (v * scale).dp }
    val bg = if (isToday) colors.primaryContainer else colors.surface
    val fg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer else colors.onSurface
    val subFg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent) else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .height(if (dateStr != null) sd(56f) else sd(52f))
            .clip(RoundedCornerShape((16 * scale * cornerRatio).dp))
            .background(bg)
            .padding(vertical = sd(6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sd(1f))
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (14 * scale).sp,
                    lineHeight = (20 * scale).sp
                ),
                color = fg,
                maxLines = 1
            )
            if (dateStr != null) {
                Text(
                    text = dateStr,
                    style = SleepyTextStyle.micro().copy(fontSize = (10 * scale).sp, lineHeight = (11 * scale).sp),
                    color = subFg,
                    maxLines = 1
                )
            } else {
                Text(
                    text = if (courseCount == 0) stringResource(R.string.no_course) else stringResource(R.string.course_count_format, courseCount),
                    style = SleepyTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = subFg,
                    maxLines = 1
                )
            }
        }
    }
}


// TimeHeadCell / SpannedTimeHeadCell 死组件已删（SpannedTimeHeadCell 注释自述弃用,
// TimeHeadCell 被 SingleTimeHeadCell 取代, 两者全库零调用; CELL_H 常量随之删除）。

// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）。
// 原注释 S/L 值写错（0.48/0.88、0.35/0.26），实际为亮色 S=0.55 L=0.82 / 暗色 S=0.40 L=0.28，正确值见 CourseColorUtil 常量。

// =====================================================================================
// 7days full 视图 — switchable.html #fullView
// =====================================================================================

@Composable
fun FullWeekView(
    courses: List<CourseEntity>,
    visibleDays: Set<Int> = (1..7).toSet(),
    displayMode: String = "node",
    timeJson: String = "",
    today: Int = DateUtils.todayDayOfWeek(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 设置页改 weekScale/cornerRatio/twoColumn/hideEmptyDays 后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_WEEK_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO ||
            it == AppPrefs.KEY_WEEK_TWO_COLUMN || it == AppPrefs.KEY_WEEK_TWO_COLUMN_MODE ||
            it == AppPrefs.KEY_WEEK_HIDE_EMPTY_DAYS
        }.collect { prefVersion++ }
    }
    val scale = AppPrefs.getWeekScale(context)
    val cornerRatio = AppPrefs.getGridCornerRatio(context)
    val twoColumn = AppPrefs.isWeekTwoColumn(context)
    val twoColumnMode = AppPrefs.getWeekTwoColumnMode(context)
    val hideEmptyDays = AppPrefs.isWeekHideEmptyDays(context)
    val byDay = courses.groupBy { it.day }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        WeekStrip(
            byDay = byDay,
            visibleDays = visibleDays,
            today = today,
            greyDays = greyDays,
            scale = scale,
            cornerRatio = cornerRatio,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        DetailPanel(
            byDay = byDay,
            visibleDays = visibleDays,
            displayMode = displayMode,
            timeJson = timeJson,
            today = today,
            onCourseClick = onCourseClick,
            greyDays = greyDays,
            scale = scale,
            cornerRatio = cornerRatio,
            twoColumn = twoColumn,
            twoColumnMode = twoColumnMode,
            hideEmptyDays = hideEmptyDays
        )
    }
}

@Composable
private fun WeekStrip(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    visibleDays: Set<Int>,
    greyDays: Set<Int> = emptySet(),
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    modifier: Modifier = Modifier
) {
    val sd = { v: Float -> (v * scale).dp }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(sd(6f))
    ) {
        for (day in visibleDays.sorted()) {
            val dayCourses = byDay[day].orEmpty()
            val isToday = day == today
            DaySummaryCell(
                day = day,
                courses = dayCourses,
                isToday = isToday,
                isGrey = day in greyDays,
                scale = scale,
                cornerRatio = cornerRatio,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DaySummaryCell(
    day: Int,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isGrey: Boolean = false,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    cornerRatio: Float = 1f
) {
    val colors = SleepyTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val sd = { v: Float -> (v * scale).dp }
    val bg = if (isToday) colors.primaryContainer else colors.surfaceContainer
    val fg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer else colors.onSurface
    // Chip: solid surfaceVariant with full alpha for dark mode readability
    val chipBg = colors.surfaceVariant
    val chipFg = colors.onSurfaceVariant.copy(alpha = if (isGrey) SleepyTheme.Alpha.inactive else 1f)

    Column(
        modifier = modifier
            .height((132 * scale).dp)
            .clip(RoundedCornerShape((12 * scale * cornerRatio).dp))
            .background(bg)
            .padding(horizontal = sd(6f), vertical = sd(8f))
    ) {
        // 日期
        Text(
            text = DateUtils.localizedDay(day, context),
            style = SleepyTextStyle.dayLabel().copy(fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp),
            color = fg,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(sd(6f)))

        // Chip: 课程数 — 完整文字在列宽内换行就退化为纯数字，胶囊自动缩到数字宽度
        if (courses.isEmpty()) {
            Spacer(modifier = Modifier.height(sd(14f)))
        } else {
            val fullText = stringResource(R.string.course_count_format, courses.size)
            val numberText = courses.size.toString()
            val chipStyle = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold, fontSize = (10 * scale).sp, lineHeight = (14 * scale).sp)
            val textMeasurer = rememberTextMeasurer()
            BoxWithConstraints(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                val chipPaddingPx = with(LocalDensity.current) { sd(14f).toPx() }.toInt()
                val availablePx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
                val layoutResult = textMeasurer.measure(
                    text = fullText,
                    style = chipStyle,
                    constraints = Constraints(maxWidth = (availablePx - chipPaddingPx).coerceAtLeast(0))
                )
                val showNumberOnly = layoutResult.lineCount > 1
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipBg)
                        .padding(horizontal = sd(7f), vertical = sd(2f))
                ) {
                    Text(
                        text = if (showNumberOnly) numberText else fullText,
                        style = chipStyle,
                        color = chipFg,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(sd(4f)))

        // Mini-list: 前 5 门课名（改掉 take(3)，空间够就全显示）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sd(2f))
        ) {
            courses.take(5).forEach { c ->
                Text(
                    text = c.courseName,
                    style = SleepyTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = if (isToday) colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent) else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailPanel(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    visibleDays: Set<Int>,
    displayMode: String,
    timeJson: String,
    onCourseClick: (CourseEntity) -> Unit,
    greyDays: Set<Int> = emptySet(),
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    twoColumn: Boolean = false,
    twoColumnMode: String = "days",
    hideEmptyDays: Boolean = false
) {
    val colors = SleepyTheme.colors
    val sd = { v: Float -> (v * scale).dp }
    // issue#8 隐藏无课日 — 单栏/两栏都生效
    val sortedDays = visibleDays.sorted().let {
        if (hideEmptyDays) it.filter { d -> byDay[d].orEmpty().isNotEmpty() } else it
    }

    // issue#8 周视图两栏, 省纵向滚动; 分栏标准由设置选择:
    //   days    = 按天对半分 — 前半周左/后半周右, 天数固定
    //   balance = 按课程数动态平衡 — 逐天放进当天卡片(约)更矮的栏, 两栏高度接近
    // 隐藏无课日后按剩余天数平分: 6 天=3+3, 4 天=2+2; 奇数天多的一天落左栏
    if (twoColumn && sortedDays.size >= 2) {
        val split: Pair<List<Int>, List<Int>> = if (twoColumnMode == "balance") {
            // 贪心: 按天序遍历, 每天记权重 = 课程数(权重按 DetailDayCard 高度近似, 空天也有卡头所以记 1)
            var l = 0; var r = 0
            val left = mutableListOf<Int>(); val right = mutableListOf<Int>()
            for (day in sortedDays) {
                val w = (byDay[day].orEmpty().size).coerceAtLeast(1)
                if (l <= r) { left.add(day); l += w } else { right.add(day); r += w }
            }
            left to right
        } else {
            val splitIdx = (sortedDays.size + 1) / 2
            sortedDays.subList(0, splitIdx) to sortedDays.subList(splitIdx, sortedDays.size)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sd(12f)),
            horizontalArrangement = Arrangement.spacedBy(sd(10f)),
            verticalAlignment = Alignment.Top
        ) {
            DayColumn(
                days = split.first,
                byDay = byDay, today = today, displayMode = displayMode, timeJson = timeJson,
                onCourseClick = onCourseClick, greyDays = greyDays,
                scale = scale, cornerRatio = cornerRatio,
                modifier = Modifier.weight(1f)
            )
            if (split.second.isNotEmpty()) {
                DayColumn(
                    days = split.second,
                    byDay = byDay, today = today, displayMode = displayMode, timeJson = timeJson,
                    onCourseClick = onCourseClick, greyDays = greyDays,
                    scale = scale, cornerRatio = cornerRatio,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // 单栏(或两栏下过滤后不足 2 天) — 显示剩余星期(全空周时=全部所选星期, 不吃掉无课日)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((16 * scale * cornerRatio).dp))
                .background(colors.surfaceContainerHigh)
                .padding(sd(12f)),
            verticalArrangement = Arrangement.spacedBy(sd(10f))
        ) {
            for (day in visibleDays.sorted()) {
                val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
                DetailDayCard(
                    day = day,
                    courses = dayCourses,
                    isToday = day == today,
                    displayMode = displayMode,
                    timeJson = timeJson,
                    onCourseClick = onCourseClick,
                    isGrey = day in greyDays,
                    scale = scale,
                    cornerRatio = cornerRatio
                )
            }
        }
    }
}

/** 两栏模式的单侧栏 — 半周的天卡片竖排在一个独立面板里 */
@Composable
private fun DayColumn(
    days: List<Int>,
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    displayMode: String,
    timeJson: String,
    onCourseClick: (CourseEntity) -> Unit,
    greyDays: Set<Int>,
    scale: Float,
    cornerRatio: Float,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val sd = { v: Float -> (v * scale).dp }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape((16 * scale * cornerRatio).dp))
            .background(colors.surfaceContainerHigh)
            .padding(sd(10f)),
        verticalArrangement = Arrangement.spacedBy(sd(10f))
    ) {
        for (day in days) {
            val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
            DetailDayCard(
                day = day,
                courses = dayCourses,
                isToday = day == today,
                displayMode = displayMode,
                timeJson = timeJson,
                onCourseClick = onCourseClick,
                isGrey = day in greyDays,
                scale = scale,
                cornerRatio = cornerRatio
            )
        }
    }
}

@Composable
private fun DetailDayCard(
    day: Int,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isGrey: Boolean = false,
    displayMode: String = "node",
    timeJson: String = "",
    onCourseClick: (CourseEntity) -> Unit,
    scale: Float = 1f,
    cornerRatio: Float = 1f
) {
    val colors = SleepyTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val sd = { v: Float -> (v * scale).dp }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((12 * scale * cornerRatio).dp))
            .background(if (courses.isEmpty()) colors.surfaceContainerLow else colors.surface)
            .padding(sd(10f)),
        verticalArrangement = Arrangement.spacedBy(sd(8f))
    ) {
        // 头部：星期 + 今天标记
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateUtils.localizedDay(day, context) + if (isToday) stringResource(R.string.today_suffix) else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else colors.onSurface
            )
        }

        if (courses.isEmpty()) {
            Text(
                text = DateUtils.localizedDay(day, context) + stringResource(R.string.no_course_today),
                style = SleepyTextStyle.smallMeta().copy(fontSize = (12 * scale).sp, lineHeight = (16 * scale).sp),
                color = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else colors.onSurfaceVariant
            )
        } else {
            // v7.10.6 行分组下沉引擎: mergeOverlapping 区域是划分(每课恰属一区域),
            // 冲突区域整区域一行(横向 laneCount 栏,同栏多门课纵向堆叠),
            // 无冲突课一行一门全宽。结构上保证不丢课、不重复(用户 2026-09-02 报障修复)。
            val rows = remember(courses) {
                ConflictLayoutEngine.weekLaneRows(courses)
            }

            Column(verticalArrangement = Arrangement.spacedBy(sd(7f))) {
                rows.forEach { row ->
                    if (row.laneCount == 1) {
                        LessonRow(
                            course = row.courses[0], displayMode = displayMode, timeJson = timeJson,
                            onClick = { onCourseClick(row.courses[0]) }, isGrey = isGrey,
                            scale = scale, cornerRatio = cornerRatio
                        )
                    } else {
                        // 冲突行: 按 lane 并排,每列 weight 均分,同栏课程纵向堆叠
                        // (栏内课互不重叠——chainGroups 独立集保证,堆叠即正确时序)。
                        // v7.10.4: BoxWithConstraints 拿 lane 实宽 → 字体/间距按比例压缩
                        // (两栏模式下 lane 半宽,不缩则字全挤在一起——用户 2026-09-02)
                        // v7.10.10: 栏间画浅细竖分隔线(用户 2026-09-02)
                        val laneCount = row.laneCount
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val laneGap = sd(6f)
                            val laneW = (maxWidth - laneGap * (laneCount - 1)) / laneCount
                            val laneScale = weekLaneFontScale(laneW)
                            val hideSide = weekLaneHideSideLabel(laneW)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(laneGap)
                            ) {
                                repeat(laneCount) { li ->
                                    if (li > 0) {
                                        // 栏间浅细竖线: 0.5dp 宽, onSurface 0.3 透明度, 高度随行
                                        Box(
                                            modifier = Modifier
                                                .width(0.5.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    colors.onSurface.copy(alpha = SleepyTheme.Alpha.hairline)
                                                )
                                        )
                                    }
                                    val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                                    Box(modifier = Modifier.weight(1f)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(sd(5f))) {
                                            laneCourses.forEach { laneCourse ->
                                                LessonRow(
                                                    course = laneCourse, displayMode = displayMode,
                                                    timeJson = timeJson,
                                                    onClick = { onCourseClick(laneCourse) },
                                                    isGrey = isGrey, scale = scale, cornerRatio = cornerRatio,
                                                    laneScale = laneScale, hideSideLabel = hideSide
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// v7.10.4 冲突栏字号/间距压缩 — 用户 2026-09-02: 两栏模式下分栏字全挤到一起,
// 需按 lane 实宽调画面比例。
// =====================================================================================

/** lane 宽压缩基准: 单栏半宽量级(用户实测"比较好"的现状),低于它开始线性缩。 */
internal val WEEK_LANE_SCALE_BASE = 150f

/** 压缩下限 — 再窄也不无限缩,可读性由隐藏侧栏标签/副信息兜底。 */
internal const val WEEK_LANE_SCALE_FLOOR = 0.6f

/**
 * 冲突栏内 LessonRow 的缩放比(纯函数可测):
 *   laneW ≥ 150dp → 1.0(保现状);以下线性压缩;0.6 封底。
 */
internal fun weekLaneFontScale(laneW: Dp): Float =
    if (laneW >= WEEK_LANE_SCALE_BASE.dp) 1f
    else (laneW.value / WEEK_LANE_SCALE_BASE).coerceAtLeast(WEEK_LANE_SCALE_FLOOR)

/** 极窄 lane 判定: 侧栏节次/时间标签(42dp+8dp 间距)挤占正文 → 隐藏它。 */
internal fun weekLaneHideSideLabel(laneW: Dp): Boolean = laneW < 110.dp

@Composable
private fun LessonRow(
    course: CourseEntity,
    displayMode: String,
    timeJson: String,
    onClick: () -> Unit,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    laneScale: Float = 1f,
    hideSideLabel: Boolean = false
) {
    val colors = SleepyTheme.colors
    val palette = SleepyTheme.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    // 双层缩放: scale=全局周视图缩放(issue#8), laneScale=v7.10.4 冲突栏按实宽压缩
    val effScale = scale * laneScale
    val sd = { v: Float -> (v * effScale).dp }
    // 统一取色入口（决策 D3）— colorless 读取 AppPrefs course_colorless 独立开关
    val bg = CourseColorUtil.pickCourseColorCompose(
        course = course,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context)
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val effectiveBg = if (isGrey) bg.copy(alpha = SleepyTheme.Alpha.inactive) else bg
    val effectiveFg = if (isGrey) fg.copy(alpha = SleepyTheme.Alpha.inactive) else fg
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") androidx.compose.ui.text.style.TextDecoration.LineThrough else null

    // time 模式：「08:00-\n08:45」——时间段在连字符后折行，行距收紧读成一个整体
    val timeParts = if (displayMode == "time" && timeJson.isNotBlank()) {
        TimeTableUtils.courseTimeParts(course.startNode, course.step, timeJson, course.ownTime, course.startTime, course.endTime)
    } else null
    val nodeLabel = course.shortNodeString(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sd(12f * cornerRatio)))
            .background(effectiveBg)
            .noRippleClickable(onClick)
            .padding(sd(9f)),
        horizontalArrangement = Arrangement.spacedBy(sd(8f))
    ) {
        val sideStyle = SleepyTextStyle.smallMeta().copy(
            fontSize = (12 * effScale).sp,
            lineHeight = (16 * effScale).sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = textDecoration
        )
        // 极窄 lane: 侧栏标签(42dp+8dp)挤占正文 → 隐藏,节次信息由卡片纵向位置表达
        if (!hideSideLabel) {
            if (timeParts != null) {
                Text(
                    text = "${timeParts.first}-\n${timeParts.second}",
                    style = sideStyle,
                    color = effectiveFg,
                    modifier = Modifier.width(sd(42f))
                )
            } else {
                Text(
                    text = nodeLabel,
                    style = sideStyle,
                    color = effectiveFg,
                    modifier = Modifier.width(sd(42f)),
                    maxLines = 1
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (12 * effScale).sp,
                    lineHeight = (16 * effScale).sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Top,
                        trim = LineHeightStyle.Trim.FirstLineTop
                    ),
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (course.teacher.isNotBlank()) append(course.teacher)
                if (course.room.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(course.room)
                }
            }
            // 极窄 lane 下副信息(教师/教室)也让位给课名
            if (meta.isNotEmpty() && !hideSideLabel) {
                Text(
                    text = meta,
                    style = SleepyTextStyle.smallMeta().copy(
                        fontSize = (11 * effScale).sp,
                        lineHeight = (14 * effScale).sp,
                        textDecoration = textDecoration
                    ),
                    color = effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =====================================================================================
// 公共小组件
// =====================================================================================

@Composable
fun SectionHead(title: String, action: String? = null) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = SleepyTextStyle.sectionHead(),
            color = colors.onSurface
        )
        if (action != null) {
            Text(
                text = action,
                style = SleepyTextStyle.smallMeta().copy(
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            )
        }
    }
}


// sp 已被上面 textStyle 直接用 inline
