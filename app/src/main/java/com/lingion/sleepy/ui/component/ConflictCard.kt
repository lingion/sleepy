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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * 视觉本体(缩小底卡/竖轨/折角缺角/虚线轮廓)挂在各 Card 或 overlay 绘制,Mark 只承载 tap 入口。
 */
sealed class CourseDrawItem {
    data class Card(val laid: LaidOutCourse) : CourseDrawItem()
    data class Mark(val hiddenCourseId: Long, val variant: ConflictVariant) : CourseDrawItem()
}

// ============================ 视觉常量(视觉修订 v4: 等大双卡左上/右下叠放,用户 2026-09-01 定版) ============================

/** STACK/FOLD 命中区视觉基准边(dp): 16 + 内延 20 = 36dp 见方。 */
private const val MARK_SQUARE_DP = 16f

/** 命中区在视觉区基础上的总内延(dp,单边) — 手指命中容差。 */
private const val MARK_HIT_PAD_DP = 20f

/** STACK 叠卡收缩量 d(dp) — v6 起由设置滑杆传入(AppPrefs.getConflictTopInset),此值为几何测试基线。 */
internal const val STACK_OFFSET_DP = 8f

/** FOLD 折痕直角边长 f(dp) — 右上缺角/翻折 flap 尺寸。 */
private const val FOLD_SIZE_DP = 16f

/** FOLD flap 内折角圆角(dp) — 翻进来的角保留原圆角意象。 */
private const val FOLD_FLAP_CORNER_DP = 6f

/** RAIL 顶卡右侧收窄量(dp) — v6 起由设置滑杆传入,与 STACK_OFFSET 共用同一设置值;此值为测试基线。 */
internal const val RAIL_INSET_DP = 10f

/** N 徽标直径(dp)/字号(sp)。 */
private const val BADGE_SIZE_DP = 14f
private const val BADGE_FONT_SP = 8

/** 课程卡圆角(dp) — 与 SleepyTheme.shapes.medium(12dp)同源。 */
private const val CARD_CORNER_DP = 12f

/** 冲突卡描边宽(dp) — 用户 2026-09-01: 卡本身窄,边框要细。每张真卡各一层细描边。 */
private const val CARD_BORDER_DP = 1f

/** FOLD 虚线轮廓: 段长/段间隙(dp) — 底课被顶课全遮时标出它的真实占位。 */
private const val DASH_LEN_DP = 4f
private const val DASH_GAP_DP = 3f

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
 * v7.8.4 修订: RAIL 不再有侧边竖轨, 命中区复用 STACK 风格 —— 自身区间右下 36dp 见方。
 */
fun markHitArea(
    variant: ConflictVariant,
    cardWidth: Float,
    cardHeight: Float,
    railWidth: Float = RAIL_INSET_DP,
    railSegmentHeight: Float = cardHeight
): Pair<Float, Float> = when (variant) {
    ConflictVariant.STACK, ConflictVariant.FOLD, ConflictVariant.RAIL -> {
        // 右下/右上 16dp 视觉基准 + 20dp 总内延 → 36dp 见方
        val side = (MARK_SQUARE_DP + MARK_HIT_PAD_DP)
            .coerceAtMost(cardWidth).coerceAtMost(cardHeight)
        side to side
    }
    ConflictVariant.NONE -> 0f to 0f
}

/**
 * 簇级形态(纯 JVM 可测,v5) — hidden 课 variant 优先;交换置顶后可能无 hidden 课
 * (长课置顶、原顶课露出尾独占节次 → hidden 集空),形态不能塌缩成 NONE——
 * 几何跟用户设置走: rail 恒 RAIL(窄卡形态与 hidden 无关);
 * fold 样式回落看 foldEligible(首个非顶课与紧邻上层同起点,与引擎闸门同规);
 * 其余回落 STACK。
 * N≥3 stack→FOLD 合流仍由引擎经 hidden variant 给出。
 */
internal fun clusterForm(
    style: String,
    firstHiddenVariant: ConflictVariant?,
    foldEligible: Boolean = false
): ConflictVariant = when {
    firstHiddenVariant != null -> firstHiddenVariant
    style == "rail" -> ConflictVariant.RAIL
    style == "fold" && foldEligible -> ConflictVariant.FOLD
    else -> ConflictVariant.STACK
}

/** 簇内单卡/命中区放置矩形(dp),渲染与单测共用同一份真值。 */
internal data class ConflictRect(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

/**
 * v7.6 图层语义(纯 JVM 可测) — N 徽标的「N」按**图层数**,不按裸课数。
 * 用户 2026-09-02: 「分组之后这两节就绑定在一个图层了」——多课链组整组是一层,
 * 单课是一层。{1-3,4-6} 组 + 1-6 重叠者 = 2 图层(裸课数 3),徽标不该按 3 出。
 * 防御: 组切分与课数对不上(输入源漂移)→ 回落裸课数,不显示错数。
 */
internal fun conflictBadgeLayerCount(groupSizes: List<Int>, rawCount: Int): Int =
    if (groupSizes.sum() == rawCount) groupSizes.size else rawCount

/** N 徽标可见性 = 图层 N≥3 且存在 hidden 课(N≥3 的逃生门语义,单位是图层)。 */
internal fun conflictShowBadge(layerCount: Int, hiddenCount: Int): Boolean =
    layerCount >= 3 && hiddenCount > 0

/**
 * 簇内单卡放置矩形(纯 JVM 可测,v5)。
 *
 * 锚点规则(用户 2026-09-01 定版): 一切锚定都是**相对该课自身区间**的方位,不是簇格位的——
 * a=1-2 的「右下」落在 1-2 节范围内,不落到 2-3 节去。
 *   STACK: 顶卡 = 自身尺寸缩 d,锚自身区间左上;非顶卡 = 同样缩 d,锚自身区间右下。
 *          每张卡尺寸只跟课走 ⇒ 无论怎么切换(多次往返)大小恒定,切换只换层级。
 *   RAIL:  顶卡 = 右缘收窄 topInset 的窄卡;非顶卡全宽。都按自身真实节位/节数铺。
 *   FOLD/NONE: 全尺寸,自身节位(FOLD 的缺角由 Shape 叠加,不改变矩形)。
 *
 * v6: topInset = 顶卡收窄量(dp),STACK 的偏移 d 与 RAIL 的右缘让宽共用同一设置值
 * (AppPrefs.getConflictTopInset 滑杆,4..20dp);默认取 STACK_OFFSET_DP(测试基线)。
 */
internal fun conflictCardRect(
    startNode: Int,
    ownRows: Int,
    isTop: Boolean,
    form: ConflictVariant,
    colW: Dp,
    rowH: Dp,
    gapH: Dp,
    minStart: Int,
    topInset: Dp = AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp
): ConflictRect {
    val ownH = rowH * ownRows.coerceAtLeast(1) - gapH
    val y = rowH * (startNode - minStart)
    return when {
        isTop && form == ConflictVariant.STACK ->
            ConflictRect(0.dp, y, colW - topInset, ownH - topInset)
        isTop && form == ConflictVariant.RAIL ->
            ConflictRect(0.dp, y, colW - topInset, ownH)
        !isTop && form == ConflictVariant.STACK -> {
            // 锚自身区间右下: 右缘贴格位右边,下缘贴自己区间的底——hidden 与否同待遇,
            // 部分重叠课(长课被短课压顶)也缩 d 锚右下,露出的边就是它自己的真实长度
            val h = ownH - topInset
            ConflictRect(topInset, y + ownH - h, colW - topInset, h)
        }
        else -> ConflictRect(0.dp, y, colW, ownH)
    }
}

/**
 * hidden 课命中区矩形(纯 JVM 可测,v5) — 视觉区 + MARK_HIT_PAD 内延,**锚自身区间**
 * (v4 锚簇格位是错的: 短课的命中区会伸进它不占的节次)。
 * FOLD 命中区锚顶卡右上(跟顶课走,与 flap 同锚),仍在 Composable 内绘制。
 */
internal fun conflictMarkRect(
    startNode: Int,
    ownRows: Int,
    form: ConflictVariant,
    colW: Dp,
    rowH: Dp,
    gapH: Dp,
    minStart: Int,
    clusterH: Dp,
    topInset: Dp = AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp
): ConflictRect {
    val ownH = rowH * ownRows.coerceAtLeast(1) - gapH
    val y = rowH * (startNode - minStart)
    return when (form) {
        ConflictVariant.STACK, ConflictVariant.RAIL -> {
            // v7.8.4 修订: RAIL 命中区复用 STACK 风格 —— 自身区间右下 36dp 见方。
            // (side 超课高时压回课高,不越过自己区间顶部)
            val side = (MARK_SQUARE_DP.dp + MARK_HIT_PAD_DP.dp)
                .coerceAtMost(colW).coerceAtMost(ownH)
            ConflictRect(colW - side, y + ownH - side, side, side)
        }
        else -> ConflictRect(0.dp, 0.dp, 0.dp, 0.dp)
    }
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
 *
 * v7.8 链组态: hidden 课为空 → Mark 列表也空,绘制序退化为非顶层卡(zRank 降序)+ 顶层卡。
 * 沉底链组成员以 Card 形态自然露出,与 v7.4 之前由 groupRep 引导的 chainFront 卡合并为同一绘制序。
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
 * ConflictClusterCard — 整簇一张,内部自绘各课(渲染层,视觉修订 v4)。
 *
 * 各变体几何(用户 2026-09-01 定版):
 *   STACK: 顶卡/底卡**大小完全相同**(各为自身原尺寸缩 d)——顶卡锚簇格位**左上**,
 *          底卡锚簇格位**右下**;右/下各露 d 带 = 底卡露出边,点击切换。
 *          等长课(1-3 叠 1-3): 两卡同大,顶左上底右下。
 *          一长一短: 尺寸固定不随切换变——短顶长底: 短卡左上正常显示,长卡右下露边+
 *          下方长出的一段全可见;长顶短底: 长卡左上,短卡右下只露自己长度(两节)的边。
 *   RAIL:  顶卡窄卡(右缘收窄 railInset,高=自身节数),底卡**全宽、按自己真实节位铺**——
 *          短课露出的边就是短课自己的长度;换置顶后宽窄/长短随课互换。
 *          N≥3: 名字段纵贯簇格右缘均分(竖排课名特性保留)。
 *   FOLD:  顶卡全尺寸、右上角沿折痕内折(f 见方,flap=顶卡色压暗);缺角露底卡角。
 *          顶课比 hidden 底课长 → 底课被全遮,按它**真实占位**画虚线轮廓(同长不画);
 *          FOLD 仅同起点出现(引擎闸门),错位起点回落 STACK。
 *
 * 边框: 每张真卡各一层 1dp 细描边(用户: 边框要细),色由各自课色自派生。
 *
 * 点击语义(设计 §4,v7.8 改写):
 *   点顶卡/置顶链组成员 → onCourseClick(该课)
 *   点露出带/沉底课程卡(沉底层整组) → onPickTop(沉底层代表 id)
 *   沉底层代表 = 该层内的最前课程 id(zRank 升序首位);引擎收到 id 后把整层提到顶层。
 *   N 徽标(图层 N≥3) → AlertDialog 列簇内全部课课名点选 → onPickTop(id)
 *
 * v7.8 图层语义: 引擎已按 chainGroups 分图层, 每图层要么整组置顶要么整组垫底。
 * 本组件不需再做组代表判定——拿到任意沉底层成员 id, 引擎的 topOverrideId 语义会
 * 把整层提到顶层(见 layoutCluster 的 orderedLayers)。
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

    // N 徽标可见性: 图层 N≥3 且存在 hidden 课(v7.6: N 按图层数——链组整组是一层)
    val chainGroups = remember(cluster) {
        ConflictLayoutEngine.chainGroups(drawList.map { it.course })
    }
    val layerCount = conflictBadgeLayerCount(
        groupSizes = chainGroups.map { it.size },
        rawCount = drawList.size
    )
    val showBadge = conflictShowBadge(layerCount, hiddenCount)
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // 绘制序(评审 Critical 修复): 非顶卡(zRank 降序) → 顶卡 → hidden 课 Mark 命中区(overlay 层)。
    val drawOrder = overlayMarkOrder(drawList)

    // 顶层判定(与 overlayMarkOrder 兜底同源): zRank 0 即可, v7.8 单课顶层与多课链组顶层都属同一判定。
    val topLaid = drawList.firstOrNull { it.zRank == 0 } ?: drawList.first()
    val topCourse = topLaid.course
    val hiddenItems = drawList.filter { it.hidden }
    // 右缘名字段/分段命中的分段集合 = 全部非顶课(v5): 切换后 hidden 集会缩,
    // 但竖轨字段要纵贯全簇不缺段——非顶可见课的段与它的露出带也该有名字。
    val railOthers = drawList.filter { it.zRank != 0 }

    // 簇级形态(v5/v7.8): fold 回落资格 —— 由 chainGroups 第一图层尺寸判定。
    //   - 单课顶层(N=1,经典完全重叠): 同起点 N=2 顶层 size=1 → 不进入 fold 路径, 维持原 style
    //   - 单课冲突顶层 size>=2: 顶层单课 + 沉底一门课 → foldEligible=true (兼容经典双课等长场景)
    //   - 链组顶层 size>=2: 顶层是 1-3/4-6 这类多课组合 → foldEligible=true (B 方案一起折角)
    //   - 不再用 drawList[0]/drawList[1].startNode 是否相同判断 —— 链组 1-3/4-6 起点不同但应一起折角
    val topLayer = chainGroups.firstOrNull() ?: emptyList()
    val foldEligible = drawList.isNotEmpty() && topLayer.size >= 2
    val form = clusterForm(style, hiddenItems.firstOrNull()?.variant, foldEligible)

    // 课色(描边/虚线/flap 取色,含 isGrey 灰显,与卡渲染取同一色)
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

    // 簇几何: 整簇基点 = 主课判定序首位课(调用方以它定位,override 不改变该锚点)。
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

    // v6: 顶卡收窄量 = 用户设置(A 偏移 d / C 右缘让宽共用),滑杆 4..20dp
    val topInset = AppPrefs.getConflictTopInset(context).dp

    // v7.8 图层语义: 引擎 layerOfId 即每课对应图层; 该层的「整层代表 id」=
    // 该层内 zRank 最小的成员(引擎 zOrdered 已按图层拼接序排序, 同层内按 startNode 升序)。
    // 沉底层任何成员点击 = onPickTop(沉底层代表 id), 引擎收到 id 后整层上移。
    val layerOfId: Map<Long, Int> = remember(chainGroups) {
        chainGroups.flatMapIndexed { gi, g -> g.map { it.id to gi } }.toMap()
    }
    val layerRepId: Map<Int, Long> = remember(chainGroups) {
        chainGroups.mapIndexed { gi, g -> gi to g.first().id }.toMap()
    }
    fun layerRepOf(courseId: Long): Long? =
        layerOfId[courseId]?.let { gi -> layerRepId[gi] }

    // v7.8: 拼条态(chainStripActive)仅用于 FOLD flap 绘制,不影响 rect 几何。
    // 链组内每张成员卡严格按所在图层走 conflictCardRect 的标准顶/底分支:
    //  - 顶层图层(STACK→缩小左上 / RAIL→收窄 / FOLD→全尺寸折角)
    //  - 底层图层(STACK→缩小右下 / RAIL→全宽胶囊 / FOLD→全尺寸无折角)
    // A/B/C 三方案无差别, 不存在「链组走全尺寸」的特殊分支。
    val chainStripActive = topLaid.chainFront

    /** 单卡放置矩形(纯函数 conflictCardRect 的 Composable 包装,几何真值唯一来源)。
     *  isFront = 该课程是否属于顶层图层, 直接驱动 conflictCardRect 的 isTop 分支。
     *  链组态不影响 rect: 顶层链组成员按顶层标准收窄, 底层链组成员按底层标准收窄。 */
    fun rectOf(course: CourseEntity, isFront: Boolean): ConflictRect = conflictCardRect(
        startNode = course.startNode,
        ownRows = clampedSteps[course.id] ?: 1,
        isTop = isFront,
        form = form,
        colW = colW, rowH = rowH, gapH = gapH, minStart = minStart,
        topInset = topInset
    )

    // 簇格位高 = minStart..maxEnd 全区间(STACK 的右下锚定参照——不能用顶课区间:
    // 短课置顶时,底部长课仍要按自己的尺寸锚在簇位右下)。
    val cellH = clusterH

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
                    // v7.8: 顶层链组成员(chainFront=true) → 顶卡形态(STACK 缩小左上 /
                    // RAIL 收窄 / FOLD 折角), 点击 = onCourseClick(course)。
                    // 沉底链组成员(chainFront=false 且 hidden=false) → 底卡形态,
                    // 点击 = onPickTop(layerRepOf)。
                    // 单课层顶层(zRank=0, chainFront=false) → 顶卡形态;单课层垫底 →
                    // 底卡形态。
                    val isFrontCard = item.laid.chainFront || item.laid.zRank == 0
                    // v7.4(F3): fold 拼条态下链组每个可见成员都折角(用户定版「A/C 都折角」)
                    // B 方案: 顶层(链组成员或单课顶层)折角, 底层不折角 —— 由图层归属判定。
                    val cardIsFolded = form == ConflictVariant.FOLD &&
                        (item.laid.chainFront || item.laid.zRank == 0)
                    val memberShape = if (cardIsFolded && item.laid.hidden.not()) {
                        remember { FoldCutShape(FOLD_SIZE_DP.dp, CARD_CORNER_DP.dp) }
                    } else cardShape

                    if (isFrontCard) {
                        // ---- 顶卡 / 顶层链组成员: 形态只改宽窄(STACK 同缩/RAIL 收窄;
                        // 链式拼条态不缩), 矩形=自身区间几何。点击 = onCourseClick(course)
                        val topRect = rectOf(course, isFront = true)
                        ConflictCourseCard(
                            course = course,
                            onClick = { onCourseClick(course) },
                            modifier = Modifier
                                .offset(x = topRect.x, y = topRect.y)
                                .width(topRect.width)
                                .height(topRect.height),
                            isGrey = isGrey,
                            shape = memberShape
                        )
                    } else {
                        // ---- 沉底卡: 同一矩形函数(hidden 与否同待遇)——尺寸只跟课走,
                        // 切换只换层级, 多次往返几何不变。STACK 右下锚自身区间, RAIL 全宽。
                        // 点击(v7.8) = 该课所在层整层上移(层代表 id 作为 override)。
                        val r = rectOf(course, isFront = false)
                        Box(
                            modifier = Modifier
                                .offset(x = r.x, y = r.y)
                                .width(r.width)
                                .height(r.height)
                                .noRippleClickable {
                                    onPickTop(layerRepOf(course.id) ?: course.id)
                                }
                        ) {
                            ConflictCourseCard(
                                course = course,
                                onClick = { onPickTop(layerRepOf(course.id) ?: course.id) },
                                modifier = Modifier.fillMaxSize(),
                                isGrey = isGrey,
                                shape = cardShape
                            )
                        }
                    }
                }
                is CourseDrawItem.Mark -> {
                    // ---- Mark = hidden 课的命中区(视觉本体已在底卡/竖轨/折角层) ----
                    // v5: 命中区锚**自身区间**(v4 锚簇格位是错的——短课命中区伸进它不占的节次)。
                    // v7.8: 点击 = onPickTop(layerRepOf) 让整层上移。
                    val hiddenCourse = courseById[item.hiddenCourseId]?.course ?: return@forEach
                    if (item.variant == ConflictVariant.NONE) return@forEach
                    when (item.variant) {
                        ConflictVariant.STACK -> {
                            // hit: 自身区间右下 36dp 见方,点击=该课置顶
                            val hit = conflictMarkRect(
                                startNode = hiddenCourse.startNode,
                                ownRows = clampedSteps[hiddenCourse.id] ?: 1,
                                form = ConflictVariant.STACK,
                                colW = colW, rowH = rowH, gapH = gapH,
                                minStart = minStart, clusterH = cellH,
                                topInset = topInset
                            )
                            Box(
                                modifier = Modifier
                                    .offset(x = hit.x, y = hit.y)
                                    .width(hit.width)
                                    .height(hit.height)
                                    .noRippleClickable {
                                        onPickTop(layerRepOf(item.hiddenCourseId) ?: item.hiddenCourseId)
                                    }
                            )
                        }
                        ConflictVariant.FOLD -> {
                            // hit: 顶卡右上 36dp 见方盲区(跟顶课走,与 flap 同锚),点击=该 hidden 课置顶
                            // (flap 视觉已提升到簇级——切换后 hidden 集空它也必须在)
                            val hit = markHitArea(
                                ConflictVariant.FOLD, colW.value, cellH.value
                            ).let { (w, h) -> w.dp.coerceAtMost(colW) to h.dp.coerceAtMost(cellH) }
                            Box(
                                modifier = Modifier
                                    .offset(x = colW - hit.first, y = cardYOf(topCourse.startNode))
                                    .width(hit.first)
                                    .height(hit.second)
                                    .noRippleClickable {
                                        onPickTop(layerRepOf(item.hiddenCourseId) ?: item.hiddenCourseId)
                                    }
                            )
                        }
                        ConflictVariant.RAIL -> {
                            // v7.8.4 修订: RAIL 不再有侧边竖轨结构, 命中区复用 STACK 风格 —— 自身区间右下 36dp 见方。
                            // 链组态下 hidden 课为空, 不会走到这里; 经典完全重叠 RAIL hit 走 STACK 同一矩形函数。
                            val hit = conflictMarkRect(
                                startNode = hiddenCourse.startNode,
                                ownRows = clampedSteps[hiddenCourse.id] ?: 1,
                                form = ConflictVariant.STACK,
                                colW = colW, rowH = rowH, gapH = gapH,
                                minStart = minStart, clusterH = cellH,
                                topInset = topInset
                            )
                            Box(
                                modifier = Modifier
                                    .offset(x = hit.x, y = hit.y)
                                    .width(hit.width)
                                    .height(hit.height)
                                    .noRippleClickable {
                                        onPickTop(layerRepOf(item.hiddenCourseId) ?: item.hiddenCourseId)
                                    }
                            )
                        }
                        ConflictVariant.NONE -> Unit
                    }
                }
            }
        }

        // ---- FOLD flap 视觉(v6 簇级 / v7.4 逐成员): 右上角沿折痕内折——flap=该卡课色压暗
        // (翻面),圆角折进来;缺角处露出底卡(含它自己的圆角)。挂在簇级而非 hidden Mark:
        // 切换置顶后 hidden 集空,「折角的切换也得是折角」——形态跟设置走,flap 必须常在。
        // v7.4(F3): fold 拼条态下链组每个可见成员都各自带 flap(「A/C 都折角」),
        // flap 色取各自课色;非拼条态维持单 flap(顶卡)。
        // 锚点补偿卡自身 2dp 外边距,与折角剪裁形对齐。
        if (form == ConflictVariant.FOLD) {
            val flapHosts: List<CourseEntity> = if (chainStripActive) {
                // 拼条每个可见成员一行 flap(去重后按节位)
                drawList.filter { it.chainFront && !it.hidden || it.zRank == 0 }.map { it.course }
            } else listOf(topCourse)
            flapHosts.forEach { host ->
                Canvas(
                    modifier = Modifier
                        .offset(
                            x = colW - FOLD_SIZE_DP.dp - 2.dp,
                            y = cardYOf(host.startNode) + 2.dp
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
                    drawPath(flap, foldFlapColor(courseColorOf(host)))
                }
            }
        }

        // ---- FOLD 虚线轮廓: 顶课比 hidden 底课长 → 底课被全遮,按它真实占位画虚线(同长不画) ----
        // 用户 2026-09-01: 长顶短底时给底课画虚线;等长靠 flap 缺角示意即可。
        if (form == ConflictVariant.FOLD) {
            hiddenItems.forEach { hid ->
                val hidH = cardHOf(hid.course.id)
                if (hidH < cardHOf(topCourse.id)) {
                    DashOutline(
                        color = conflictBorderColor(courseColorOf(hid.course)),
                        modifier = Modifier
                            .offset(
                                x = 2.dp,
                                y = cardYOf(hid.course.startNode) + 2.dp
                            )
                            .size(width = colW - 4.dp, height = hidH - 4.dp)
                    )
                }
            }
        }

        // ---- RAIL 名字段: 用户 C 方案原话只描述胶囊大小, 不要求侧边竖轨/竖排汉字 ----
        // v7.8.4 修订: 删除 RAIL 右缘彩色带 + 竖排汉字。
        // 顶胶囊宽度收窄(RAIL_INSET), 底胶囊全宽 —— 课名都在胶囊上横排, 没有额外的右缘带。
        // 命中区复用 STACK 右下 36dp 见方语义(见下方 Mark 分支, 已统一)。
        // railOthers 字段仅作为 Mark 命中区命中集合使用, 此处不再渲染。

        // ---- N 徽标(图层 N≥3 且 hidden 课存在): overlay 层右上,点击弹课名点选 ----
        if (showBadge) {
            val styleIsFold = form == ConflictVariant.FOLD
            ConflictBadge(
                count = layerCount,
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
 * FOLD 虚线轮廓 — 被 top 课全遮的更短底课的真实占位提示(圆角虚线框,不接点击)。
 */
@Composable
private fun DashOutline(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = CARD_CORNER_DP.dp.toPx()
        val path = Path().apply {
            addRoundRect(
                RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))
            )
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = CARD_BORDER_DP.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(DASH_LEN_DP.dp.toPx(), DASH_GAP_DP.dp.toPx())
                )
            )
        )
    }
}

/**
 * 簇内单课真卡 — 复用原 CourseOverlayCard 的取色/灰显/文案逻辑(渲染结构对齐,
 * 保持视觉一致;若后续收敛可让 CourseOverlayCard 改为转发到这里)。
 * 视觉修订 v4: shape 可注入(FOLD 折角剪裁形);自派生 1dp 细描边(每张真卡各一层)。
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
 * v7.8.4 修订: VerticalRailName 已删除。
 * RAIL 不再有侧边竖轨 + 竖排汉字, 所有课名都画在胶囊内部横排(由 ConflictCourseCard 负责)。
 * 函数彻底移除, 防止未来误用。
 */

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