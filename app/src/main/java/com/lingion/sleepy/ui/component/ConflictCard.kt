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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
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

// ============================ 视觉常量(Task 5 最终视觉,设计文档 §3 集中定义) ============================

/** STACK/FOLD 命中区视觉基准边(dp): 14 + 内延 12 = 26dp 见方。 */
private const val MARK_SQUARE_DP = 14f

/** 命中区在视觉区基础上的总内延(dp,单边) — 手指命中容差。 */
private const val MARK_HIT_PAD_DP = 12f

/** STACK 叠层错位 d(dp) — hidden 课作为幻影卡向右下错位量,也是 L 形露边宽度。 */
private const val STACK_OFFSET_DP = 4f

/** FOLD 折角直角边长 s(dp) — 右上切角/flap 的直角边。 */
private const val FOLD_SIZE_DP = 12f

/** FOLD flap 内角圆角(dp)。 */
private const val FOLD_FLAP_CORNER_DP = 2f

/** RAIL 轨宽(dp): N=2 单轨 / N≥3 加宽(容纳竖排课名)。 */
private const val MARK_RAIL_W_DP = 6f
private const val MARK_RAIL_W_MULTI_DP = 14f

/** RAIL N≥3 纵切段间缝(dp)。 */
private const val RAIL_SEG_GAP_DP = 1f

/** RAIL 竖排课名字号(sp): 8sp 起步,段高不够降到 6sp,再不够只显示首字。 */
private const val RAIL_NAME_SP = 8
private const val RAIL_NAME_MIN_SP = 6

/** N 徽标直径(dp)/字号(sp)。 */
private const val BADGE_SIZE_DP = 14f
private const val BADGE_FONT_SP = 8

/** 课程卡圆角(dp) — 与 SleepyTheme.shapes.medium(12dp)同源,幻影卡 L 边外侧圆角一致。 */
private const val CARD_CORNER_DP = 12f

/**
 * 标记命中区尺寸计算(纯 JVM 可测,单位与调用方约定一致):
 *
 * 命中区 = 标记视觉区 + MARK_HIT_PAD 总内延(约一指宽容差),**绝不铺满整卡**——
 * hidden 存在时若 overlay 可点区铺满顶层卡,「点主体=编辑最上层」(设计 §4)全域不可达。
 * 主体其余区域留给顶层卡自己的 onCourseClick。结果 coerce 到卡尺寸内(小卡裁剪内延)。
 *
 * 返回 (w, h),调用方按变体锚到顶层卡对应角/边。NONE 无标记 → (0, 0) 不可点。
 *
 * RAIL 收窄(Task 5,上轮 review 遗留): 命中区=竖轨视觉区+12dp 内延,不再独立铺满整卡——
 * N=2 单轨(默认参) → 宽 6+12=18dp × 顶层卡高(轨视觉区纵贯卡高,高度随卡);
 * N≥3 → 调用方传 railWidth=14 与段高,得 26dp × (段高+12dp 内延) 的段级命中区。
 */
fun markHitArea(
    variant: ConflictVariant,
    cardWidth: Float,
    cardHeight: Float,
    railWidth: Float = MARK_RAIL_W_DP,
    railSegmentHeight: Float = cardHeight
): Pair<Float, Float> = when (variant) {
    ConflictVariant.STACK, ConflictVariant.FOLD -> {
        // 右下/右上 14dp 视觉基准 + 12dp 总内延 → 26dp 见方
        val side = (MARK_SQUARE_DP + MARK_HIT_PAD_DP)
            .coerceAtMost(cardWidth).coerceAtMost(cardHeight)
        side to side
    }
    ConflictVariant.RAIL -> {
        // 轨视觉宽/轨段视觉高 + 12dp 总内延,coerce 进顶层卡
        (railWidth + MARK_HIT_PAD_DP).coerceAtMost(cardWidth) to
            (railSegmentHeight + MARK_HIT_PAD_DP).coerceAtMost(cardHeight)
    }
    ConflictVariant.NONE -> 0f to 0f
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
 *
 * 顶层判定兜底(评审 Important-2): 簇主课可能出界(startNode > maxNode)被调用方
 * 过滤,过滤后列表无 zRank 0——此时取列表首位当顶层(zRank 升序首位=界内最上层),
 * 保证任何情况下界内课有渲染有点击;输入为空(全出界)才返回空,调用方整簇跳过。
 */
fun overlayMarkOrder(laid: List<LaidOutCourse>): List<CourseDrawItem> {
    if (laid.isEmpty()) return emptyList()
    val top = laid.firstOrNull { it.zRank == 0 } ?: laid.first()
    val others = laid.filter { it !== top }.sortedByDescending { it.zRank }
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
 * 变体标记(Task 5 最终视觉,设计文档 §3):
 *   STACK = 主体右下错位 d=4dp 露 L 形边(hidden 课课色)
 *   FOLD  = 右上 s=12dp 切角+翻折 flap(hidden 课课色,内角 2dp 圆角)
 *   RAIL  = 右缘竖轨: N=2 单轨 6dp / N≥3 加宽 14dp 纵切 N-1 段,
 *           每段=对应 hidden 课课色+竖排课名(8sp→6sp→首字三级降级)
 *   N 徽标(N≥3) = 右上 14dp 圆标(surface 底+onSurface 文字),FOLD 时避开 flap。
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
                    // 命中区=视觉区+12dp 内延(markHitArea,评审 Important-1),绝不铺满整卡——
                    // 主体其余区域留给顶层卡自己的 onCourseClick(设计 §4 点主体=编辑最上层)。
                    // 视觉几何按变体走 Task 5 最终视觉;hiddenCount=0 或 NONE 时无标记到不了这。
                    val hiddenCourse = courseById[item.hiddenCourseId]?.course ?: return@forEach
                    if (item.variant == ConflictVariant.NONE) return@forEach
                    val topCourse = drawList.first().course
                    val topH = cardHOf(topCourse.id)
                    val courseColor = courseColorOf(hiddenCourse)
                    when (item.variant) {
                        ConflictVariant.STACK -> {
                            // STACK 叠层偏移(设计 §3): hidden 课作为幻影卡整体向右下错位 d=4dp,
                            // 露出部分=幻影卡减去顶层卡 bounds = 右下 L 形边(下边+右边,4dp 宽,
                            // 该 hidden 课课色,外侧圆角与卡片一致)。visual 与 hit 分离:
                            //   visual = Canvas 画「错位圆角矩形 - 卡矩形」的差集 L 形(Path.op DIFFERENCE)
                            //   hit    = 右下角 26dp 见方盲区(视觉边+12dp 内延,现有测试基线)
                            val hit = markHitArea(
                                ConflictVariant.STACK, colW.value, topH.value
                            ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(topH) }
                            // visual: 画布盖住「卡+错位」全区域,只落 L 形差集,不遮卡内容
                            Canvas(
                                modifier = Modifier
                                    .offset(y = cardYOf(topCourse.startNode))
                                    .size(
                                        width = colW + STACK_OFFSET_DP.dp,
                                        height = topH + STACK_OFFSET_DP.dp
                                    )
                            ) {
                                val d = STACK_OFFSET_DP.dp.toPx()
                                val r = CARD_CORNER_DP.dp.toPx()
                                val phantom = Path().apply {
                                    addRoundRect(
                                        RoundRect(d, d, size.width, size.height, CornerRadius(r, r))
                                    )
                                }
                                val cardBounds = Path().apply {
                                    addRect(Rect(0f, 0f, size.width - d, size.height - d))
                                }
                                val lEdge = Path().apply {
                                    op(phantom, cardBounds, PathOperation.Difference)
                                }
                                drawPath(lEdge, courseColor)
                            }
                            // hit: 右下角盲区,点击=该 hidden 课置顶
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = colW - hit.first,
                                        y = cardYOf(topCourse.startNode) + topH - hit.second
                                    )
                                    .width(hit.first)
                                    .height(hit.second)
                                    .noRippleClickable { onPickTop(item.hiddenCourseId) }
                            )
                        }
                        ConflictVariant.FOLD -> {
                            // FOLD 折角: 右上角 12dp 切角 + 翻折 flap(该 hidden 课课色),命中区同上
                            val hit = markHitArea(
                                ConflictVariant.FOLD, colW.value, topH.value
                            ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(topH) }
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = colW - hit.first,
                                        y = cardYOf(topCourse.startNode)
                                    )
                                    .width(hit.first)
                                    .height(hit.second)
                                    .noRippleClickable { onPickTop(item.hiddenCourseId) }
                            ) {
                                FoldCornerMark(
                                    flapColor = courseColor,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }
                        }
                        ConflictVariant.RAIL -> {
                            // RAIL 竖轨: N=2 单轨 6dp;N≥3 加宽 14dp 纵切 N-1 段,每段=对应
                            // hidden 课课色+竖排课名。每段命中区=段视觉区+12dp 内延(点击=该段课置顶)。
                            val hiddenCourses = drawList.filter { it.hidden }
                            val multi = hiddenCourses.size >= 2 // N=hidden+顶层≥3 → 多段轨
                            val railW = (if (multi) MARK_RAIL_W_MULTI_DP else MARK_RAIL_W_DP).dp
                            if (!multi) {
                                // ---- N=2 单轨 ----
                                val hit = markHitArea(
                                    ConflictVariant.RAIL, colW.value, topH.value
                                ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(topH) }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = colW - hit.first,
                                            y = cardYOf(topCourse.startNode)
                                        )
                                        .width(hit.first)
                                        .height(hit.second)
                                        .noRippleClickable { onPickTop(item.hiddenCourseId) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(y = 1.dp)
                                            .width(railW)
                                            .height(topH - 2.dp) // 视觉轨贴右缘纵贯,上下留卡内边距
                                            .clip(
                                                RoundedCornerShape(
                                                    topEnd = 3.dp, bottomEnd = 3.dp
                                                )
                                            )
                                            .background(courseColor)
                                    )
                                }
                            } else {
                                // ---- N≥3 多段轨: 段数=hidden 数(N-1),段高均分(扣缝) ----
                                val segCount = hiddenCourses.size
                                val railH = topH - 2.dp
                                val segH = ((railH - RAIL_SEG_GAP_DP.dp * (segCount - 1)) / segCount)
                                    .coerceAtLeast(0.dp)
                                hiddenCourses.forEach { hid ->
                                    val segColor = courseColorOf(hid.course)
                                    val segIdx = hiddenCourses.indexOf(hid)
                                    val segTopInRail = (segH + RAIL_SEG_GAP_DP.dp) * segIdx
                                    // 段视觉高+12dp 内延 → 段级命中区;高度再 coerce 到
                                    // 「段顶→卡底」剩余空间,末段命中区不越出卡外抢下一行 tap
                                    val hit = markHitArea(
                                        ConflictVariant.RAIL,
                                        colW.value,
                                        topH.value,
                                        railWidth = MARK_RAIL_W_MULTI_DP,
                                        railSegmentHeight = segH.value
                                    ).let { (w, h) ->
                                        w.dp.coerceAtMost(colW) to
                                            h.dp.coerceAtMost(topH - 1.dp - segTopInRail)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .offset(
                                                x = colW - hit.first,
                                                y = cardYOf(topCourse.startNode) + 1.dp +
                                                    segTopInRail
                                            )
                                            .width(hit.first)
                                            .height(hit.second)
                                            .noRippleClickable { onPickTop(hid.course.id) }
                                    ) {
                                        // 段视觉块锚命中区顶右(=段真实位置),圆角只在外侧(右)两角
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .width(railW)
                                                .height(segH)
                                                .clip(
                                                    RoundedCornerShape(
                                                        topEnd = 3.dp, bottomEnd = 3.dp
                                                    )
                                                )
                                                .background(segColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // 竖排课名: Text 逐字符换行,8sp→6sp→首字三级降级
                                            // (字符级贪心思路同 widget/WeekGridWidgetProvider 竖排)
                                            VerticalRailName(
                                                name = hid.course.courseName,
                                                segmentHeight = segH,
                                                color = CourseColorUtil.textColorOn(
                                                    segColor,
                                                    CourseColorUtil.isPaletteDark(palette),
                                                    colors.onSurface
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ConflictVariant.NONE -> Unit
                    }
                }
            }
        }

        // ---- N 徽标(N≥3 且 hidden 课存在): overlay 层右上,点击弹课名点选 ----
        // 位置避开 FOLD flap(设计文档 §3): flap 占右上 12dp 角 → 徽标沿 flap 左侧
        // (右缘内缩 14dp+2dp 间隙);非 FOLD 变体贴右上 2dp 内边距。
        if (showBadge) {
            val styleIsFold = style == "fold" ||
                (style == "stack" && drawList.size >= 3) // stack N≥3 合流 FOLD
            ConflictBadge(
                count = drawList.size,
                onClick = { showPicker = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = if (styleIsFold) -(FOLD_SIZE_DP.dp + 2.dp) else -2.dp,
                        y = 2.dp
                    )
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
 * FOLD 折角标记(overlay 层) — 设计文档 §3 变体 B 最终视觉:
 *
 * 顶层卡右上角切 s=12dp 直角三角(主体缺角),flap 三角用 hidden 课课色内折补进缺角,
 * flap 内角(直角点,靠近卡中心)2dp 圆角。flap 与主体间明度差=课色 vs 卡色,自然存在。
 *
 * 结构: Canvas 画布盖切角+flap 全区(s 见方,锚命中区右上):
 *   1) 切角补底: 三角(左上-右上-右下)取网格底色盖住主体右上角 → 视觉上主体被"切掉"
 *   2) flap 三角: 直角在右上(内角),2dp 圆角(quadratic);斜边=折痕
 */
@Composable
private fun FoldCornerMark(flapColor: Color, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    Canvas(modifier = modifier.size(FOLD_SIZE_DP.dp)) {
        val s = size.width // 正方形,直角边=s
        // 1) 切角补底(左上-右上-右下三角,网格底色)
        val cutPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(s, 0f)
            lineTo(s, s)
            close()
        }
        drawPath(cutPath, colors.surfaceContainerHigh)
        // 2) flap 三角(左上-右上圆角-右下),直角点=右上内角带 2dp 圆角
        val c = FOLD_FLAP_CORNER_DP.dp.toPx()
        val flapPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(c, 0f)
            quadraticTo(s, 0f, s, c) // 内角圆角
            lineTo(s, s)
            close()
        }
        drawPath(flapPath, flapColor)
    }
}

/**
 * RAIL 竖排课名(overlay 层轨段内) — 字符级竖排,三级降级防溢出
 * (字符级贪心思路与 widget/WeekGridWidgetProvider 竖排同源):
 *   段高装得下 8sp 全名 → 8sp;装不下 → 6sp;再装不下 → 只显示首字。
 * 逐字符换行 = Text("\n".join(chars)),TextAlign 居中 + auto maxLines 按段高自然裁。
 */
@Composable
private fun VerticalRailName(
    name: String,
    segmentHeight: Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (name.isBlank()) return
    // 三级降级(字符级贪心,竖排溢出修复同源思路):
    //   8sp 全名 → 6sp 全名 → 6sp 首字。估算行高≈字号×1.2,
    //   按"字符数×行高 ≤ 段高"挑档;全名两档都装不下 → 只显示首字(极端矮段缩首字)
    val charCount = name.length
    val fitsAt8 = charCount * RAIL_NAME_SP * 1.2f <= segmentHeight.value
    val fitsAt6 = charCount * RAIL_NAME_MIN_SP * 1.2f <= segmentHeight.value
    val fontSize = when {
        fitsAt8 -> RAIL_NAME_SP
        fitsAt6 -> RAIL_NAME_MIN_SP
        else -> RAIL_NAME_MIN_SP
    }
    val showFullName = fitsAt8 || fitsAt6
    val maxLines = if (showFullName) charCount else 1
    val text = if (showFullName) name.chunked(1).joinToString("\n") else name.take(1)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.2f).sp
        ),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        modifier = modifier
    )
}

/**
 * N 徽标(N≥3) — 右上角 14dp 小圆标,bg-elevated 类底色(surface token)+onSurface 文字,
 * 8sp 显示簇大小。视觉精修(Task 5): 由 Task 4 的 primary 实底改为中性浮起样式。
 */
@Composable
private fun ConflictBadge(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .size(BADGE_SIZE_DP.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = BADGE_FONT_SP.sp),
            color = colors.onSurface,
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
