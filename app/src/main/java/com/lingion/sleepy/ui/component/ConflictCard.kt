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
import androidx.compose.runtime.remember
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
 * 视觉本体(缩小的底卡/竖轨/折角缺角)挂在各自 Card 上,Mark 只承载 tap 入口。
 */
sealed class CourseDrawItem {
    data class Card(val laid: LaidOutCourse) : CourseDrawItem()
    data class Mark(val hiddenCourseId: Long, val variant: ConflictVariant) : CourseDrawItem()
}

// ============================ 视觉常量(视觉修订 v3: 等比缩小叠卡,用户 2026-09-01 定版) ============================

/** STACK/FOLD 命中区视觉基准边(dp): 16 + 内延 20 = 36dp 见方。 */
private const val MARK_SQUARE_DP = 16f

/** 命中区在视觉区基础上的总内延(dp,单边) — 手指命中容差。 */
private const val MARK_HIT_PAD_DP = 20f

/**
 * STACK 叠卡收缩量 d(dp) — 用户定版效果: 底卡与顶卡**同比例**向右下各缩 d,
 * 双卡对角错开叠放;整体仍占满原一节课的格位(顶卡右上角 = 格位右上角,
 * 底卡左下角 = 格位左下角,右/下各露 d 露出带)。
 */
private const val STACK_OFFSET_DP = 8f

/** FOLD 折痕直角边长 f(dp) — 右上缺角/翻折 flap 尺寸。 */
private const val FOLD_SIZE_DP = 16f

/** FOLD flap 内折角圆角(dp) — 翻进来的角保留原圆角意象。 */
private const val FOLD_FLAP_CORNER_DP = 6f

/** RAIL 顶卡右侧收窄量(dp) — 用户定版: 顶卡永远是窄卡,底卡保持全宽全高。 */
private const val RAIL_INSET_DP = 14f

/** RAIL 竖排课名字号(sp): 8sp 起步,段高不够降到 6sp,再不够只显示首字。 */
private const val RAIL_NAME_SP = 8
private const val RAIL_NAME_MIN_SP = 6

/** RAIL N≥3 轨内纵切段间缝(dp)。 */
private const val RAIL_SEG_GAP_DP = 1f

/** N 徽标直径(dp)/字号(sp)。 */
private const val BADGE_SIZE_DP = 14f
private const val BADGE_FONT_SP = 8

/** 课程卡圆角(dp) — 与 SleepyTheme.shapes.medium(12dp)同源。 */
private const val CARD_CORNER_DP = 12f

/** 冲突卡描边宽(dp) — 用户 2026-09-01: 重叠课程必须有边框;每张真卡各**一层**
 *  描边(顶卡一层+底卡一层=用户说的"上面一层边框下面一层边框"),除此之外不加任何层。 */
private const val CARD_BORDER_DP = 1.5f

/**
 * 冲突卡描边色 — 由课色自派生: 亮色压暗/暗色提亮,与自身填充、网格底、相邻课色都有对比。
 */
internal fun conflictBorderColor(base: Color): Color =
    if (base.luminance() > 0.5f) lerp(base, Color.Black, 0.35f)
    else lerp(base, Color.White, 0.45f)

/** flap 色 = 顶层课色压暗(翻面朝里的物理意象),与缺角处露出的底卡色形成明度差。 */
private fun foldFlapColor(topColor: Color): Color = lerp(topColor, Color.Black, 0.28f)

/**
 * 顶卡「折角剪裁形」— 圆角矩形挖掉右上角三角(折痕从顶边 (w-f,0) 到右边 (w,f)),
 * 缺角处露出底卡(含它自己的圆角)。纯 Shape,尺寸在 createOutline 按密度换算。
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
 * 命中区 = 标记视觉区 + MARK_HIT_PAD 总内延(约两指宽容差),**绝不铺满整卡**——
 * hidden 存在时若 overlay 可点区铺满顶卡,「点主体=编辑最上层」(设计 §4)全域不可达。
 *
 * 返回 (w, h),调用方按变体锚到对应角/边。NONE 无标记 → (0, 0) 不可点。
 * RAIL: 命中区=右缘露出带视觉宽(RAIL_INSET)+内延;段级命中区传 railSegmentHeight。
 */
fun markHitArea(
    variant: ConflictVariant,
    cardWidth: Float,
    cardHeight: Float,
    railWidth: Float = RAIL_INSET_DP,
    railSegmentHeight: Float = cardHeight
): Pair<Float, Float> = when (variant) {
    ConflictVariant.STACK, ConflictVariant.FOLD -> {
        // 右下/右上 16dp 视觉基准 + 20dp 总内延 → 36dp 见方
        val side = (MARK_SQUARE_DP + MARK_HIT_PAD_DP)
            .coerceAtMost(cardWidth).coerceAtMost(cardHeight)
        side to side
    }
    ConflictVariant.RAIL -> {
        // 右缘露出带视觉宽/段视觉高 + 20dp 总内延,coerce 进格位
        (railWidth + MARK_HIT_PAD_DP).coerceAtMost(cardWidth) to
            (railSegmentHeight + MARK_HIT_PAD_DP).coerceAtMost(cardHeight)
    }
    ConflictVariant.NONE -> 0f to 0f
}

/**
 * 簇内绘制序计算(纯 JVM 可测) — 评审 Critical 的核心修复:
 *
 * hidden 课的定义 = 被更高层课完全覆盖。故绘制序 = 非顶层课卡(zRank 降序,先画被盖住的)
 * → 顶卡(zRank 0 最后画) → 全部 hidden 课的 Mark 命中区(按 zRank 升序,叠在一切卡之上)。
 * Mark 区域自己接 clickable = hidden 课的 tap 入口(点 = 把该 hidden 课换到顶层)。
 *
 * 顶层判定兜底(评审 Important-2): 簇主课可能出界(startNode > maxNode)被调用方
 * 过滤,过滤后列表无 zRank 0——此时取列表首位当顶层,保证任何情况下界内课有渲染有点击;
 * 输入为空(全出界)才返回空,调用方整簇跳过。
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
 * ConflictClusterCard — 整簇一张,内部自绘各课(渲染层,视觉修订 v3)。
 *
 * 核心结构(用户 2026-09-01 定版): 两张卡**都以同样比例稍微缩小一点点**、对角错开叠放,
 * 整体仍占满原格位 —
 *   STACK: 底卡锚格位左上、向右下各缩 d;顶卡锚格位右下、同尺寸。右/下各露 d 带 = 底卡。
 *          等价意象: 两张原尺寸卡对角错开叠完,再把组合外接框缩回一节课格位大小。
 *   RAIL:  顶卡右缘收窄 railInset(高不变),底卡保持完整原尺寸 —
 *          「窄窄的顶卡叠在正常宽度的底卡上」;换置顶后宽窄随课互换(谁在顶谁窄)。
 *   FOLD:  顶卡原尺寸、右上角沿折痕内折(f 见方,flap=顶卡色压暗);缺角露底卡角(含圆角)。
 *          FOLD 仅在 hidden 课与上层课**同起点**时出现(引擎闸门),错位起点回落 STACK。
 *
 * 边框(用户 2026-09-01): 冲突卡内放开「纯色块禁描边」——每张真卡**各一层** 1.5dp
 * 描边(顶卡一层+底卡一层),色由各自课色自派生;除此之外不再叠任何边框层。
 *
 * 点击语义(设计 §4,不变):
 *   点顶卡 → onCourseClick(顶层课)——与原单卡行为一致,不多一步
 *   点露出带/标记 → onPickTop(该课 id)——把该课提到顶层
 *   N 徽标(N≥3) → AlertDialog 列簇内全部课课名点选 → onPickTop(id)
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
    // hidden 计算与渲染同一裁剪空间(startNode ∈ 1..maxNode + step 截界)。
    val laid = ConflictLayoutEngine.layoutCluster(cluster, style, topOverrideId, maxNode)

    // 绘制集: 与原单卡循环同一过滤(startNode ∈ [1, maxNode])——出界课原循环本就跳过。
    val drawList = laid.filter { it.course.startNode in 1..maxNode }
    if (drawList.isEmpty()) return
    val hiddenCount = drawList.count { it.hidden }

    // N 徽标可见性: N≥3 且存在 hidden 课
    val showBadge = drawList.size >= 3 && hiddenCount > 0
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // 绘制序(评审 Critical 修复): 非顶卡(zRank 降序) → 顶卡 → hidden 课 Mark 命中区(overlay 层)。
    val drawOrder = overlayMarkOrder(drawList)

    // 顶层判定(与 overlayMarkOrder 兜底同源)
    val topLaid = drawList.firstOrNull { it.zRank == 0 } ?: drawList.first()
    val topCourse = topLaid.course
    val hiddenItems = drawList.filter { it.hidden }

    // 簇级形态 = 首 hidden 课的 variant(hiddenCount=0 → NONE)
    val form = hiddenItems.firstOrNull()?.variant ?: ConflictVariant.NONE

    // 课色(hidden 缩小底卡/flap 取色,含 isGrey 灰显,与卡渲染取同一色)
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

    // 簇格位(= 主课区间): 所有变体的「原尺寸」参照。顶卡/底卡的缩小错位都在这框内。
    val cellY = cardYOf(topCourse.startNode)
    val cellH = cardHOf(topCourse.id)

    Box(
        modifier = modifier
            .width(colW)
            .height(clusterH)
            .offset(y = clusterYOffset)
    ) {
        // 按绘制序消费: 先非顶卡(露出区域点击=点非顶层课) → 顶卡(点击=onCourseClick) → Mark
        drawOrder.forEach { item ->
            when (item) {
                is CourseDrawItem.Card -> {
                    val course = item.laid.course
                    if (item.laid.zRank == 0) {
                        // ---- 顶卡: 按变体形态缩窄/剪角,点击=onCourseClick(与原单卡一致) ----
                        when (form) {
                            ConflictVariant.STACK -> ConflictCourseCard(
                                course = course,
                                onClick = { onCourseClick(course) },
                                // 锚格位右下: 右/下贴齐,左/上各缩 d → 底卡右/下露 d 带
                                modifier = Modifier
                                    .offset(
                                        x = STACK_OFFSET_DP.dp,
                                        y = cellY + STACK_OFFSET_DP.dp
                                    )
                                    .width(colW - STACK_OFFSET_DP.dp)
                                    .height(cellH - STACK_OFFSET_DP.dp),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                            ConflictVariant.FOLD -> {
                                val foldShape = remember { FoldCutShape(FOLD_SIZE_DP.dp, CARD_CORNER_DP.dp) }
                                ConflictCourseCard(
                                    course = course,
                                    onClick = { onCourseClick(course) },
                                    modifier = Modifier
                                        .offset(y = cellY)
                                        .width(colW)
                                        .height(cellH),
                                    isGrey = isGrey,
                                    shape = foldShape
                                )
                            }
                            ConflictVariant.RAIL -> ConflictCourseCard(
                                course = course,
                                onClick = { onCourseClick(course) },
                                // 顶卡永远窄卡: 右缘收窄 railInset(高不变)
                                modifier = Modifier
                                    .offset(y = cellY)
                                    .width(colW - RAIL_INSET_DP.dp)
                                    .height(cellH),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                            ConflictVariant.NONE -> ConflictCourseCard(
                                course = course,
                                onClick = { onCourseClick(course) },
                                modifier = Modifier
                                    .offset(y = cellY)
                                    .width(colW)
                                    .height(cellH),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                        }
                    } else {
                        // ---- 非顶卡(可见可见课或 hidden 底卡): 完整真卡垫底。
                        // RAIL/STACK 的 hidden 底卡 = 原尺寸真卡锚格位左上(被顶卡盖住的部分
                        // 自然不可见),露出带 = 底卡区域;可见非顶课(梯形部分重叠)保持
                        // 自己 y/h 原样铺。露出区域 tap = 换到顶层。
                        val isHiddenBottomCard = item.laid.hidden
                        val y = if (isHiddenBottomCard) cellY else cardYOf(course.startNode)
                        val h = if (isHiddenBottomCard) cellH else cardHOf(course.id)
                        Box(
                            modifier = Modifier
                                .offset(y = y)
                                .width(colW)
                                .height(h)
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
                    // ---- Mark = hidden 课的命中区(视觉本体已在缩小底卡/竖轨/折角层) ----
                    // 命中区=视觉带+20dp 内延(markHitArea),绝不铺满整卡——
                    // 顶卡主体留给 onCourseClick(设计 §4 点主体=编辑最上层)。
                    val hiddenCourse = courseById[item.hiddenCourseId]?.course ?: return@forEach
                    if (item.variant == ConflictVariant.NONE) return@forEach
                    when (item.variant) {
                        ConflictVariant.STACK -> {
                            // hit: 右下 36dp 见方盲区(= 露出带+内延),点击=该 hidden 课置顶
                            val hit = markHitArea(
                                ConflictVariant.STACK, colW.value, cellH.value
                            ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(cellH) }
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = colW - hit.first,
                                        y = cellY + cellH - hit.second
                                    )
                                    .width(hit.first)
                                    .height(hit.second)
                                    .noRippleClickable { onPickTop(item.hiddenCourseId) }
                            )
                        }
                        ConflictVariant.FOLD -> {
                            // flap 视觉: 顶卡右上角沿折痕内折——flap=顶卡色压暗(翻面),
                            // 圆角折进来;缺角处露出底卡(含它自己的圆角)。
                            // 锚点补偿卡自身 2dp 外边距,与折角剪裁形对齐。
                            Canvas(
                                modifier = Modifier
                                    .offset(
                                        x = colW - FOLD_SIZE_DP.dp - 2.dp,
                                        y = cellY + 2.dp
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
                                ConflictVariant.FOLD, colW.value, cellH.value
                            ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(cellH) }
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = colW - hit.first,
                                        y = cellY
                                    )
                                    .width(hit.first)
                                    .height(hit.second)
                                    .noRippleClickable { onPickTop(item.hiddenCourseId) }
                            )
                        }
                        ConflictVariant.RAIL -> {
                            // hit: 右缘露出带(RAIL_INSET 宽)+20dp 内延;N≥3 hidden>1 时
                            // 按段命中(段高与竖排课名段一一对齐),点击=该段课置顶。
                            val multi = hiddenItems.size >= 2
                            if (!multi) {
                                val hit = markHitArea(
                                    ConflictVariant.RAIL, colW.value, cellH.value
                                ).let { (w, h) ->
                                    w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(cellH)
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = colW - hit.first,
                                            y = cellY
                                        )
                                        .width(hit.first)
                                        .height(hit.second)
                                        .noRippleClickable { onPickTop(item.hiddenCourseId) }
                                )
                            } else {
                                // N≥3: 段数=hidden 数,段高均分(扣缝)纵贯格位高
                                val segCount = hiddenItems.size
                                val segH = ((cellH - RAIL_SEG_GAP_DP.dp * (segCount - 1)) / segCount)
                                    .coerceAtLeast(0.dp)
                                val segIdx = hiddenItems.indexOfFirst {
                                    it.course.id == item.hiddenCourseId
                                }
                                val segTop = (segH + RAIL_SEG_GAP_DP.dp) * segIdx
                                // 段视觉高+20dp 内延;高度 coerce 到「段顶→格位底」,末段不越界
                                val hit = markHitArea(
                                    ConflictVariant.RAIL,
                                    colW.value,
                                    cellH.value,
                                    railSegmentHeight = segH.value
                                ).let { (w, h) ->
                                    w.dp.coerceAtMost(colW) to
                                        h.dp.coerceAtMost(cellH - segTop)
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = colW - hit.first,
                                            y = cellY + segTop
                                        )
                                        .width(hit.first)
                                        .height(hit.second)
                                        .noRippleClickable { onPickTop(item.hiddenCourseId) }
                                )
                            }
                        }
                        ConflictVariant.NONE -> Unit
                    }
                }
            }
        }

        // ---- RAIL N≥3 竖排课名: 画在露出带上(overlay 层,每 hidden 课一段) ----
        // 底卡全宽、顶卡窄卡 — 名字竖排在顶卡右缘让出的露带里,段色=该 hidden 课课色。
        if (form == ConflictVariant.RAIL && hiddenItems.size >= 2) {
            val segCount = hiddenItems.size
            val segH = ((cellH - RAIL_SEG_GAP_DP.dp * (segCount - 1)) / segCount).coerceAtLeast(0.dp)
            hiddenItems.forEachIndexed { idx, hid ->
                val segColor = courseColorOf(hid.course)
                Box(
                    modifier = Modifier
                        .offset(
                            x = colW - RAIL_INSET_DP.dp,
                            y = cellY + (segH + RAIL_SEG_GAP_DP.dp) * idx
                        )
                        .width(RAIL_INSET_DP.dp)
                        .height(segH)
                        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
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

        // ---- N 徽标(N≥3 且 hidden 课存在): overlay 层右上,点击弹课名点选 ----
        // 位置避开 FOLD flap: flap 占右上 16dp 角 → 徽标沿 flap 左侧内缩;非 FOLD 贴右上 2dp。
        if (showBadge) {
            val styleIsFold = form == ConflictVariant.FOLD
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

/**
 * 簇内单课真卡 — 复用原 CourseOverlayCard 的取色/灰显/文案逻辑(渲染结构对齐,
 * 保持视觉一致;若后续收敛可让 CourseOverlayCard 改为转发到这里)。
 * 视觉修订 v3: shape 可注入(FOLD 折角剪裁形);自派生 1.5dp 描边(每张真卡各一层,
 * 用户 2026-09-01 要求重叠课程有边框)。
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
 * RAIL 竖排课名(overlay 露带段内) — 字符级竖排,三级降级防溢出
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
