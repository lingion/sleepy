package com.lingion.sleepy.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictCluster
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.ConflictVariant
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.LaidOutCourse

/**
 * 引擎封装 — UI 渲染层的唯一入口（纯 JVM 可测）。
 *
 * findClusters(仅 size≥2 的簇)后逐簇 layoutCluster 展开,展平返回全部簇内课。
 * 簇间 day 升序,簇内 zRank 升序;zRank 按簇独立从 0 起(展平后跨簇不连续,消费方按簇消费)。
 * topOverrideId 只影响命中其 id 的那个簇,其余簇回落主课判定序。
 * 单课/无冲突课不在此输出——调用方(ConflictCardHost)从原循环剔除簇内课后,余课走原 CourseOverlayCard 路径。
 */
fun layoutFor(
    courses: List<CourseEntity>,
    style: String,
    topOverrideId: Long? = null
): List<LaidOutCourse> =
    ConflictLayoutEngine.findClusters(courses).flatMap { cluster ->
        ConflictLayoutEngine.layoutCluster(cluster, style, topOverrideId)
    }

/**
 * 簇内绘制项 — Card(课卡)或 Mark(hidden 课的变体标记)。
 * Mark 携带 hidden 课 id: overlay 标记区域自己接 clickable → onPickTop(该 id)。
 */
sealed class CourseDrawItem {
    data class Card(val laid: LaidOutCourse) : CourseDrawItem()
    data class Mark(val hiddenCourseId: Long, val variant: ConflictVariant) : CourseDrawItem()
}

/**
 * 簇内绘制序计算(纯 JVM 可测) — 评审 Critical 的核心修复:
 *
 * hidden 课的定义 = 被更高层课完全覆盖。若变体标记画在 hidden 课自己的层,
 * 更高层课其后绘制、背景完整盖住标记 → 标记永不可见。故标记必须挂在
 * **顶层卡之后的 overlay 层**: 绘制序 = 非顶层课卡(zRank 降序,先画被盖住的)
 * → 顶层卡(zRank 0 最后画,保证完整真卡在最上) → 全部 hidden 课的变体标记
 * (按 zRank 升序,叠在一切卡之上)。标记区域自己接 clickable = hidden 课的
 * tap 入口(点标记 = 把该 hidden 课换到顶层)。
 *
 * N=2 完全重叠场景由此获得唯一视觉存在(overlay 标记)与唯一 tap 入口。
 */
fun overlayMarkOrder(laid: List<LaidOutCourse>): List<CourseDrawItem> {
    val top = laid.firstOrNull { it.zRank == 0 } ?: return emptyList()
    val others = laid.filter { it.zRank != 0 }.sortedByDescending { it.zRank }
    val marks = laid.filter { it.hidden }.sortedBy { it.zRank }
        .map { CourseDrawItem.Mark(it.course.id, it.variant) }
    return others.map { CourseDrawItem.Card(it) } +
        listOf(CourseDrawItem.Card(top)) + marks
}

/**
 * ConflictClusterCard — 整簇一张,内部自绘各课(Task 4 渲染层)。
 *
 * 结构:
 *   外层 Box(锚定到簇基点,由调用方 offset 定位) → 内部每课按自己的 startNode/step
 *   映射出 y/h,用 Modifier.offset 叠放。按 zRank 升序画,zRank 0(顶层)最后画 →
 *   后画的接住未遮挡 tap,天然实现「露出区域点击=点非顶层课」(Compose 无 pointer
 *   穿透,露出区域 tap 落在该课自己的 Box 上)。
 *
 * 点击语义:
 *   点顶层课卡 → onCourseClick(顶层课)——与原单卡行为一致,不多一步
 *   点非顶层课露出区域 → onPickTop(该课 id)——把该课提到顶层
 *   N 徽标(N≥3 且存在 hidden 课)→ AlertDialog 列簇内全部课课名点选 → onPickTop(id)
 *
 * 变体标记(Task 4 占位级,几何精修/竖排课名归 Task 5):
 *   hidden 课可见部分按 variant 画纯色块示意(STACK=右下露边 / FOLD=右上三角 / RAIL=右侧竖条),
 *   颜色取该 hidden 课课色。
 */
@Composable
fun ConflictClusterCard(
    cluster: ConflictCluster,
    style: String,
    topOverrideId: Long?,
    onPickTop: (Long?) -> Unit,
    onCourseClick: (CourseEntity) -> Unit,
    colW: Dp,
    rowH: Dp,
    maxNode: Int,
    timeW: Dp,
    gapW: Dp,
    gapH: Dp,
    isGrey: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = SleepyTheme.palette
    val colors = SleepyTheme.colors
    val context = LocalContext.current

    // 布局现算(引擎零缓存承诺)——override 变化即重排
    val laid = ConflictLayoutEngine.layoutCluster(cluster, style, topOverrideId)

    // 绘制集: 与原单卡循环同一过滤(startNode ∈ [1, maxNode])——链式跨界的出界课
    // (startNode > maxNode,如 11-13 节课链到 13-14 节)原循环本就跳过,此处同样剔除,
    // 避免 steps clamp 到 0 产生负高度。出界课只覆盖出界节次,不影响界内课的 hidden 判定。
    val drawList = laid.filter { it.course.startNode in 1..maxNode }
    if (drawList.isEmpty()) return
    val hiddenCount = drawList.count { it.hidden }

    // N 徽标可见性: N≥3 且存在 hidden 课(N=2 时 hidden 课的可见性/tap 入口由 overlay 标记承载)
    val showBadge = drawList.size >= 3 && hiddenCount > 0
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // 绘制序(评审 Critical 修复): 非顶层卡(zRank 降序) → 顶层卡 → hidden 课变体标记(overlay 层)。
    // hidden 课被顶层完全覆盖,标记画在它自己层会被顶层卡背景盖住 → 必须挂 overlay。
    val drawOrder = overlayMarkOrder(drawList)

    // 课色缓存(hidden 标记取色用,含 isGrey 灰显,与卡渲染取同一色)
    fun courseColorOf(course: CourseEntity): Color {
        val bg = CourseColorUtil.pickCourseColorCompose(
            course = course,
            isDark = CourseColorUtil.isPaletteDark(palette),
            neutralColor = colors.surfaceVariant,
            colorless = AppPrefs.isCourseColorless(context)
        )
        return if (isGrey) bg.copy(alpha = SleepyTheme.Alpha.inactive) else bg
    }
    val courseById = drawList.associateBy { it.course.id }

    // 簇几何: 整簇基点 = 主课判定序首位课(调用方以它定位,override 不改变该锚点——
    // 交换置顶时簇不跳动);簇顶 minStart ≤ 基点恒成立。y/h 公式与 CardsGridView 原循环体一致。
    // maxEnd 用 clamp 后的 steps(与卡片实际渲染高度同源,Minor ③)。
    val baseNode = cluster.courses.first().startNode
    val minStart = drawList.minOf { it.course.startNode }
    val clampedSteps = drawList.associate {
        it.course.id to it.course.step.coerceAtLeast(1).coerceAtMost(maxNode - it.course.startNode + 1)
    }
    val maxEnd = drawList.maxOf { it.course.startNode + (clampedSteps[it.course.id] ?: 1) } - 1
    val clusterH = rowH * (maxEnd - minStart + 1) - gapH
    val clusterYOffset = rowH * (minStart - baseNode)

    fun cardYOf(startNode: Int) = rowH * (startNode - minStart)
    fun cardHOf(courseId: Long) = rowH * (clampedSteps[courseId] ?: 1) - gapH

    Box(
        modifier = modifier
            .width(colW)
            .height(clusterH)
            .offset(y = clusterYOffset)
    ) {
        // 按绘制序消费: 先非顶层卡(露出区域点击=点非顶层课) → 顶层卡(点击=onCourseClick)
        drawOrder.forEach { item ->
            when (item) {
                is CourseDrawItem.Card -> {
                    val course = item.laid.course
                    if (item.laid.zRank == 0) {
                        // ---- 顶层课: 完整真卡,点击=onCourseClick(与原单卡行为一致,不多一步) ----
                        ConflictCourseCard(
                            course = course,
                            onClick = { onCourseClick(course) },
                            modifier = Modifier
                                .offset(y = cardYOf(course.startNode))
                                .width(colW)
                                .height(cardHOf(course.id)),
                            isGrey = isGrey
                        )
                    } else {
                        // ---- 非顶层课: 完整真卡垫底,露出区域自然可点(tap 落在此卡 = 换到顶层) ----
                        Box(
                            modifier = Modifier
                                .offset(y = cardYOf(course.startNode))
                                .width(colW)
                                .height(cardHOf(course.id))
                                .noRippleClickable { onPickTop(course.id) }
                        ) {
                            ConflictCourseCard(
                                course = course,
                                onClick = { onPickTop(course.id) },
                                modifier = Modifier.fillMaxSize(),
                                isGrey = isGrey
                            )
                        }
                    }
                }
                is CourseDrawItem.Mark -> {
                    // ---- overlay 变体标记(hidden 课唯一视觉存在 + 唯一 tap 入口) ----
                    // 锚定在顶层卡的对应区域(几何精修归 Task 5),自己接 clickable → onPickTop(hidden 课)
                    val hiddenCourse = courseById[item.hiddenCourseId]?.course ?: return@forEach
                    val topCourse = drawList.first().course
                    HiddenVariantMark(
                        variant = item.variant,
                        courseColor = courseColorOf(hiddenCourse),
                        modifier = Modifier
                            .offset(y = cardYOf(topCourse.startNode))
                            .width(colW)
                            .height(cardHOf(topCourse.id))
                            .noRippleClickable { onPickTop(item.hiddenCourseId) }
                    )
                }
            }
        }

        // ---- N 徽标(N≥3 且 hidden 课存在): overlay 层锚定簇右上,点击弹课名点选 ----
        if (showBadge) {
            ConflictBadge(
                count = drawList.size,
                onClick = { showPicker = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            )
        }
    }

    if (showPicker) {
        ConflictCoursePickerDialog(
            courses = drawList.map { it.course },
            onDismiss = { showPicker = false },
            onPick = { id ->
                showPicker = false
                onPickTop(id)
            }
        )
    }
}

/**
 * 簇内单课真卡 — 复用原 CourseOverlayCard 的取色/灰显/文案逻辑(渲染结构对齐,
 * 保持视觉一致;若后续收敛可让 CourseOverlayCard 改为转发到这里)。
 */
@Composable
private fun ConflictCourseCard(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGrey: Boolean = false
) {
    val palette = SleepyTheme.palette
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val bg = CourseColorUtil.pickCourseColorCompose(
        course = course,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context)
    )
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val effectiveBg = if (isGrey) bg.copy(alpha = SleepyTheme.Alpha.inactive) else bg
    val effectiveFg = if (isGrey) fg.copy(alpha = SleepyTheme.Alpha.inactive) else fg
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") TextDecoration.LineThrough else null
    val subInfo = AppPrefs.getGridSubInfo(context)
    val subText = when (subInfo) {
        "room" -> course.room
        "teacher" -> course.teacher
        else -> ""
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(SleepyTheme.shapes.medium)
            .background(effectiveBg)
            .noRippleClickable(onClick)
            .padding(4.dp)
    ) {
        if (subText.isBlank()) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            textDecoration = textDecoration
                        ),
                        color = effectiveFg,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subText,
                    style = SleepyTextStyle.micro().copy(textDecoration = textDecoration),
                    color = effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * hidden 课变体标记(overlay 层) — Task 4 占位级(纯色块示意),几何精修归 Task 5。
 *   STACK = 右下露边色块 | FOLD = 右上角三角色块 | RAIL = 右侧竖条色块
 * 调用方已把 modifier 锚定到顶层卡区域并接 clickable(= hidden 课 tap 入口);
 * 内部铺满该区域后自建 Box 获得 BoxScope 做角部对齐。
 */
@Composable
private fun HiddenVariantMark(variant: ConflictVariant, courseColor: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        when (variant) {
            ConflictVariant.STACK -> {
                // 右下露边色块
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(14.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp))
                        .background(courseColor)
                )
            }
            ConflictVariant.FOLD -> {
                // 右上角三角色块(直角三角形: 右上-右下-左上)
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                ) {
                    val path = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, 0f)
                        close()
                    }
                    drawPath(path, courseColor)
                }
            }
            ConflictVariant.RAIL -> {
                // 右侧竖条色块
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .size(width = 6.dp, height = 40.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(courseColor)
                )
            }
            ConflictVariant.NONE -> Unit
        }
    }
}

/**
 * N 徽标 — 简单圆形数字(Task 4 占位级,视觉精修归 Task 5)。
 */
@Composable
private fun ConflictBadge(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.primary)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.onPrimary,
            maxLines = 1
        )
    }
}

/**
 * N 徽标弹窗 — AlertDialog 列簇内全部课课名点选,点选 → onPickTop(id) 关弹窗。
 * 风格对齐 ImportSheet/EditTableScreen 现有 AlertDialog(titleContentColor/textContentColor + TextButton)。
 */
@Composable
private fun ConflictCoursePickerDialog(
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val colors = SleepyTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.import_conflicts)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                courses.forEach { course ->
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.small)
                            .noRippleClickable { onPick(course.id) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        dismissButton = {}
    )
}
