package com.lingion.sleepy.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
 * 簇内绘制项 — Card(课卡)或 Mark(hidden 课的命中区)。
 * Mark 携带 hidden 课 id: 命中区自己接 clickable → onPickTop(该 id)。
 * 视觉本体(hidden 课的外层胶囊/竖轨/折角缺角)不挂在 Mark 上——统一结构下
 * 胶囊画在最底层、竖轨属于胶囊、折角 flap 紧跟顶层卡,Mark 只承载 tap 入口。
 */
sealed class CourseDrawItem {
    data class Card(val laid: LaidOutCourse) : CourseDrawItem()
    data class Mark(val hiddenCourseId: Long, val variant: ConflictVariant) : CourseDrawItem()
}

// ============================ 视觉常量(视觉修订 v2: 统一胶囊结构,用户实测反馈定版) ============================

/** STACK/FOLD 命中区视觉基准边(dp): 16 + 内延 20 = 36dp 见方。 */
private const val MARK_SQUARE_DP = 16f

/** 命中区在视觉区基础上的总内延(dp,单边) — 手指命中容差(用户反馈: 三种面积都过小,放大)。 */
private const val MARK_HIT_PAD_DP = 20f

/** STACK 叠层错位 d(dp) — 顶层课收窄量 = 右/下露出带宽度(用户反馈: 错位加大 4→8)。 */
private const val STACK_OFFSET_DP = 8f

/** FOLD 折痕直角边长 f(dp) — 右上缺角/翻折 flap 尺寸(用户反馈: 折角加大 12→16)。 */
private const val FOLD_SIZE_DP = 16f

/** FOLD flap 内折角圆角(dp) — 翻进来的角保留了原圆角矩形的圆角意象。 */
private const val FOLD_FLAP_CORNER_DP = 6f

/** RAIL 轨宽(dp): N=2 单轨 / N≥3 加宽(容纳竖排课名)。用户反馈: 侧边面积加大 6→8 / 14→16。 */
private const val MARK_RAIL_W_DP = 8f
private const val MARK_RAIL_W_MULTI_DP = 16f

/** RAIL N≥3 纵切段间缝(dp)。 */
private const val RAIL_SEG_GAP_DP = 1f

/** RAIL 竖排课名字号(sp): 8sp 起步,段高不够降到 6sp,再不够只显示首字。 */
private const val RAIL_NAME_SP = 8
private const val RAIL_NAME_MIN_SP = 6

/** N 徽标直径(dp)/字号(sp)。 */
private const val BADGE_SIZE_DP = 14f
private const val BADGE_FONT_SP = 8

/** 课程卡圆角(dp) — 与 SleepyTheme.shapes.medium(12dp)同源,胶囊与内套课同圆角幅度。 */
private const val CARD_CORNER_DP = 12f

/** 冲突簇外层胶囊描边宽(dp) — 用户明确要求(2026-09-01): 重叠课程必须有边框,
 *  本簇内放开「UI 纯色块禁描边」规则(仅限冲突簇,其余 UI 不变)。 */
private const val CAPSULE_BORDER_DP = 1.5f

/** 簇内单课卡描边宽(dp) — 同上,课卡边界在重叠色块间保持可辨。 */
private const val CARD_BORDER_DP = 1f

/**
 * 冲突簇描边色 — 由课色自派生: 亮色压暗/暗色提亮,保证与自身填充、网格底、
 * 相邻课色三个方向都有对比(课色任意,固定中性色不可靠)。
 */
internal fun conflictBorderColor(base: Color): Color =
    if (base.luminance() > 0.5f) lerp(base, Color.Black, 0.35f)
    else lerp(base, Color.White, 0.45f)

/** flap 色 = 顶层课色压暗(翻面朝里的物理意象),与缺角处露出的胶囊色形成明度差。 */
private fun foldFlapColor(topColor: Color): Color = lerp(topColor, Color.Black, 0.28f)

/**
 * 顶层课卡「折角剪裁形」— 圆角矩形挖掉右上角三角(折痕从顶边 (w-f,0) 到右边 (w,f)),
 * 缺角处露出外层胶囊(hidden 课色 + 它自己的圆角)。纯 Shape,尺寸在 createOutline 按密度换算。
 */
private class FoldCutShape(
    private val fold: Dp,
    private val corner: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val f = with(density) { fold.toPx() }
        val r = with(density) { corner.toPx() }
        val outline = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
        }
        val cut = Path().apply {
            moveTo(size.width - f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, f)
            close()
        }
        outline.op(outline, cut, PathOperation.Difference)
        return Outline.Generic(outline)
    }
}

/**
 * 标记命中区尺寸计算(纯 JVM 可测,单位与调用方约定一致):
 *
 * 命中区 = 标记视觉区 + MARK_HIT_PAD 总内延(约两指宽容差,视觉修订 v2 放大),**绝不铺满整卡**——
 * hidden 存在时若 overlay 可点区铺满顶层卡,「点主体=编辑最上层」(设计 §4)全域不可达。
 * 主体其余区域留给顶层卡自己的 onCourseClick。结果 coerce 到卡尺寸内(小卡裁剪内延)。
 *
 * 返回 (w, h),调用方按变体锚到顶层卡对应角/边。NONE 无标记 → (0, 0) 不可点。
 *
 * RAIL: 命中区=竖轨视觉区+20dp 内延——
 * N=2 单轨(默认参) → 宽 8+20=28dp × 顶层卡高(轨视觉区纵贯卡高,高度随卡);
 * N≥3 → 调用方传 railWidth=16 与段高,得 36dp × (段高+20dp 内延) 的段级命中区。
 */
fun markHitArea(
    variant: ConflictVariant,
    cardWidth: Float,
    cardHeight: Float,
    railWidth: Float = MARK_RAIL_W_DP,
    railSegmentHeight: Float = cardHeight
): Pair<Float, Float> = when (variant) {
    ConflictVariant.STACK, ConflictVariant.FOLD -> {
        // 右下/右上 16dp 视觉基准 + 20dp 总内延 → 36dp 见方
        val side = (MARK_SQUARE_DP + MARK_HIT_PAD_DP)
            .coerceAtMost(cardWidth).coerceAtMost(cardHeight)
        side to side
    }
    ConflictVariant.RAIL -> {
        // 轨视觉宽/轨段视觉高 + 20dp 总内延,coerce 进顶层卡
        (railWidth + MARK_HIT_PAD_DP).coerceAtMost(cardWidth) to
            (railSegmentHeight + MARK_HIT_PAD_DP).coerceAtMost(cardHeight)
    }
    ConflictVariant.NONE -> 0f to 0f
}

/**
 * 簇内绘制序计算(纯 JVM 可测) — 评审 Critical 的核心修复:
 *
 * hidden 课的定义 = 被更高层课完全覆盖。故绘制序 = 非顶层课卡(zRank 降序,先画被盖住的)
 * → 顶层卡(zRank 0 最后画,保证完整真卡在最上) → 全部 hidden 课的 Mark 命中区
 * (按 zRank 升序,叠在一切卡之上)。Mark 区域自己接 clickable = hidden 课的
 * tap 入口(点标记 = 把该 hidden 课换到顶层)。
 *
 * N=2 完全重叠场景由此获得唯一视觉存在(外层胶囊)与唯一 tap 入口(Mark 命中区)。
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
 * ConflictClusterCard — 整簇一张,内部自绘各课(渲染层,视觉修订 v2)。
 *
 * 统一结构(用户定版): 整个冲突格 = 一张**完整圆角矩形胶囊**(= 被 hidden 课的本体,
 * 课色填充+描边),里面**套一张高一样、宽小一些、圆角幅度一样的顶层课卡**;
 * 露出的胶囊带 = 底下那节课的可见部分,也是换置顶的 tap 区(Mark)。
 *
 * 变体差异只在那条露出带的位置与形态:
 *   STACK = 顶层课向锚点收窄 d=8dp(右+下露出 L 形带,经典叠纸错位)
 *   FOLD  = 顶层课右上角沿折痕内折(f=16dp): 缺角露胶囊,flap=顶层课色压暗翻进卡内
 *   RAIL  = 右缘竖轨带: N=2 单轨 8dp(纯色带)/ N≥3 加宽 16dp 纵切 N-1 段,
 *           每段=对应 hidden 课课色+竖排课名(8sp→6sp→首字三级降级)
 *   N 徽标(N≥3) = 右上 14dp 圆标(surface 底+onSurface 文字),FOLD 时避开 flap。
 *
 * 点击语义(设计 §4,不变):
 *   点顶层课卡 → onCourseClick(顶层课)——与原单卡行为一致,不多一步
 *   点露出带/标记 → onPickTop(该课 id)——把该课提到顶层
 *   N 徽标 → AlertDialog 列簇内全部课课名点选 → onPickTop(id)
 *
 * 边框(用户 2026-09-01 明确要求): 冲突簇内放开「纯色块禁描边」——胶囊 1.5dp、
 * 簇内每张课卡 1dp,色均由各自课色自派生(亮压暗/暗提亮)。
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
    val cardShape = SleepyTheme.shapes.medium

    // 布局现算(引擎零缓存承诺)——override 变化即重排。maxNode 传入引擎:
    // hidden 计算与下方渲染同一裁剪空间(startNode ∈ 1..maxNode + step 截界),
    // 否则界外尾部独占节次会让课漏拿标记 → UI 裁剪后零视觉零 tap(不可达课)。
    val laid = ConflictLayoutEngine.layoutCluster(cluster, style, topOverrideId, maxNode)

    // 绘制集: 与原单卡循环同一过滤(startNode ∈ [1, maxNode])——链式跨界的出界课
    // (startNode > maxNode,如 11-13 节课链到 13-14 节)原循环本就跳过,此处同样剔除,
    // 避免 steps clamp 到 0 产生负高度。出界课只覆盖出界节次,不影响界内课的 hidden 判定。
    val drawList = laid.filter { it.course.startNode in 1..maxNode }
    if (drawList.isEmpty()) return
    val hiddenCount = drawList.count { it.hidden }

    // N 徽标可见性: N≥3 且存在 hidden 课(N=2 时 hidden 课的可见性/tap 入口由露出带承载)
    val showBadge = drawList.size >= 3 && hiddenCount > 0
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // 绘制序(评审 Critical 修复): 非顶层卡(zRank 降序) → 顶层卡 → hidden 课 Mark 命中区(overlay 层)。
    val drawOrder = overlayMarkOrder(drawList)

    // 顶层判定(与 overlayMarkOrder 兜底同源)
    val topLaid = drawList.firstOrNull { it.zRank == 0 } ?: drawList.first()
    val topCourse = topLaid.course
    val topH = rowH * (topCourse.step.coerceAtLeast(1)
        .coerceAtMost(maxNode - topCourse.startNode + 1)) - gapH

    // 簇级变体形态 = hidden 课的 variant(hiddenCount=0 → NONE,整簇退化为普通叠卡+边框)
    val hiddenItems = drawList.filter { it.hidden }
    val form = hiddenItems.firstOrNull()?.variant ?: ConflictVariant.NONE
    val hasHidden = hiddenItems.isNotEmpty()
    val railWdp = (if (hiddenItems.size >= 2) MARK_RAIL_W_MULTI_DP else MARK_RAIL_W_DP).dp

    // 课色缓存(hidden 胶囊/flap 取色用,含 isGrey 灰显,与卡渲染取同一色)
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
        // ---- 外层胶囊(统一结构): 完整圆角矩形 = hidden 课本体,课色填充+自派生描边 ----
        // 顶层课收窄后露出的带就是它;FOLD 缺角处露出的也是它(含它自己的圆角)。
        if (hasHidden) {
            val capsuleColor = courseColorOf(hiddenItems.first().course)
            Box(
                modifier = Modifier
                    .offset(y = cardYOf(topCourse.startNode))
                    .width(colW)
                    .height(topH)
                    .clip(cardShape)
                    .background(capsuleColor)
                    .border(
                        CAPSULE_BORDER_DP.dp,
                        conflictBorderColor(capsuleColor),
                        cardShape
                    )
            ) {
                if (form == ConflictVariant.RAIL && hiddenItems.size >= 2) {
                    // N≥3 分段竖轨贴右缘纵贯: 段=各 hidden 课色+竖排课名;
                    // 外层 clip(cardShape) 已把轨道右缘裁成胶囊圆角,段本身直角即可。
                    val segCount = hiddenItems.size
                    val segH = ((topH - RAIL_SEG_GAP_DP.dp * (segCount - 1)) / segCount)
                        .coerceAtLeast(0.dp)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(railWdp)
                            .height(topH),
                        verticalArrangement = Arrangement.spacedBy(RAIL_SEG_GAP_DP.dp)
                    ) {
                        hiddenItems.forEach { hid ->
                            val segColor = courseColorOf(hid.course)
                            Box(
                                modifier = Modifier
                                    .width(railWdp)
                                    .height(segH)
                                    .background(segColor),
                                contentAlignment = Alignment.Center
                            ) {
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
        }

        // 按绘制序消费: 先非顶层卡(露出区域点击=点非顶层课) → 顶层卡(点击=onCourseClick) → Mark
        drawOrder.forEach { item ->
            when (item) {
                is CourseDrawItem.Card -> {
                    val course = item.laid.course
                    if (item.laid.zRank == 0) {
                        // ---- 顶层课: 按变体收窄(统一结构: 高一样/宽小一些/同圆角),点击=onCourseClick ----
                        when (form) {
                            ConflictVariant.STACK -> ConflictCourseCard(
                                course = course,
                                onClick = { onCourseClick(course) },
                                modifier = Modifier
                                    .offset(y = cardYOf(course.startNode))
                                    .width(colW - STACK_OFFSET_DP.dp)
                                    .height(topH - STACK_OFFSET_DP.dp),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                            ConflictVariant.FOLD -> {
                                val foldShape = rememberFoldCutShape()
                                ConflictCourseCard(
                                    course = course,
                                    onClick = { onCourseClick(course) },
                                    modifier = Modifier
                                        .offset(y = cardYOf(course.startNode))
                                        .width(colW)
                                        .height(topH),
                                    isGrey = isGrey,
                                    shape = foldShape
                                )
                            }
                            ConflictVariant.RAIL -> ConflictCourseCard(
                                course = course,
                                onClick = { onCourseClick(course) },
                                modifier = Modifier
                                    .offset(y = cardYOf(course.startNode))
                                    .width(colW - railWdp)
                                    .height(topH),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                            ConflictVariant.NONE -> ConflictCourseCard(
                                course = course,
                                onClick = { onCourseClick(course) },
                                modifier = Modifier
                                    .offset(y = cardYOf(course.startNode))
                                    .width(colW)
                                    .height(topH),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                        }
                    } else {
                        // ---- 非顶层可见课: 完整真卡垫底,露出区域自然可点(tap 落在此卡 = 换到顶层) ----
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
                                isGrey = isGrey,
                                shape = cardShape
                            )
                        }
                    }
                }
                is CourseDrawItem.Mark -> {
                    // ---- Mark = hidden 课的命中区(视觉本体已在胶囊/折角层) ----
                    // 命中区=视觉带+20dp 内延(markHitArea),绝不铺满整卡——
                    // 主体其余区域留给顶层卡自己的 onCourseClick(设计 §4 点主体=编辑最上层)。
                    val hiddenCourse = courseById[item.hiddenCourseId]?.course ?: return@forEach
                    if (item.variant == ConflictVariant.NONE) return@forEach
                    when (item.variant) {
                        ConflictVariant.STACK -> {
                            // hit: 右下 36dp 见方盲区,点击=该 hidden 课置顶
                            val hit = markHitArea(
                                ConflictVariant.STACK, colW.value, topH.value
                            ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(topH) }
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
                            // flap 视觉: 顶层卡右上角沿折痕内折——flap=顶层课色压暗(翻面),
                            // 圆角折进来;缺角处露出胶囊(hidden 课色+它的圆角)。
                            // 锚点须补偿卡自身 2dp 外边距,与折角剪裁形对齐。
                            Canvas(
                                modifier = Modifier
                                    .offset(
                                        x = colW - FOLD_SIZE_DP.dp - 2.dp,
                                        y = cardYOf(topCourse.startNode) + 2.dp
                                    )
                                    .size(FOLD_SIZE_DP.dp)
                            ) {
                                val f = size.width
                                val c = FOLD_FLAP_CORNER_DP.dp.toPx()
                                val flap = Path().apply {
                                    moveTo(0f, 0f)              // 折痕上端(卡顶边)
                                    lineTo(f, f)                // 折痕下端(卡右边)
                                    lineTo(c, f)
                                    quadraticTo(0f, f, 0f, f - c) // 内折角保留圆角意象
                                    close()
                                }
                                drawPath(flap, foldFlapColor(courseColorOf(topCourse)))
                            }
                            // hit: 右上 36dp 见方盲区(缺角+flap+内延),点击=该 hidden 课置顶
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
                            )
                        }
                        ConflictVariant.RAIL -> {
                            // hit: 每段 hidden 课各一段命中区(轨视觉宽+20dp 内延),点击=该课置顶。
                            // N=2 单轨 → 一段纵贯整卡高;N≥3 → 段高与胶囊内的视觉段一一对齐。
                            val segIdx = hiddenItems.indexOfFirst { it.course.id == item.hiddenCourseId }
                            val multi = hiddenItems.size >= 2
                            val segH = if (multi) {
                                ((topH - RAIL_SEG_GAP_DP.dp * (hiddenItems.size - 1)) / hiddenItems.size)
                                    .coerceAtLeast(0.dp)
                            } else topH
                            val segTop = if (multi) (segH + RAIL_SEG_GAP_DP.dp) * segIdx else 0.dp
                            // 段视觉高+20dp 内延;高度再 coerce 到「段顶→卡底」,末段不越出卡外抢下一行 tap
                            val hit = markHitArea(
                                ConflictVariant.RAIL,
                                colW.value,
                                topH.value,
                                railWidth = railWdp.value,
                                railSegmentHeight = segH.value
                            ).let { (w, h) ->
                                w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(topH - segTop)
                            }
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = colW - hit.first,
                                        y = cardYOf(topCourse.startNode) + segTop
                                    )
                                    .width(hit.first)
                                    .height(hit.second)
                                    .noRippleClickable { onPickTop(item.hiddenCourseId) }
                            )
                        }
                        ConflictVariant.NONE -> Unit
                    }
                }
            }
        }

        // ---- N 徽标(N≥3 且 hidden 课存在): overlay 层右上,点击弹课名点选 ----
        // 位置避开 FOLD flap(设计文档 §3): flap 占右上 16dp 角 → 徽标沿 flap 左侧
        // (右缘内缩 16dp+4dp 间隙);非 FOLD 变体贴右上 2dp 内边距。
        if (showBadge) {
            val styleIsFold = style == "fold" ||
                (style == "stack" && drawList.size >= 3) // stack N≥3 合流 FOLD
            ConflictBadge(
                count = drawList.size,
                onClick = { showPicker = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = if (styleIsFold) -(FOLD_SIZE_DP.dp + 4.dp) else -2.dp,
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

/** FoldCutShape 常量参数,remember 避免每帧重建 Path。 */
@Composable
private fun rememberFoldCutShape(): FoldCutShape =
    androidx.compose.runtime.remember { FoldCutShape(FOLD_SIZE_DP.dp, CARD_CORNER_DP.dp) }

/**
 * 簇内单课真卡 — 复用原 CourseOverlayCard 的取色/灰显/文案逻辑(渲染结构对齐,
 * 保持视觉一致;若后续收敛可让 CourseOverlayCard 改为转发到这里)。
 * 视觉修订 v2: shape 可注入(FOLD 折角剪裁形),自派生 1dp 描边(用户要求重叠课有边框)。
 */
@Composable
private fun ConflictCourseCard(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGrey: Boolean = false,
    shape: Shape = SleepyTheme.shapes.medium
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
            .clip(shape)
            .background(effectiveBg)
            .border(CARD_BORDER_DP.dp, conflictBorderColor(effectiveBg), shape)
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
 * RAIL 竖排课名(胶囊轨段内) — 字符级竖排,三级降级防溢出
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
