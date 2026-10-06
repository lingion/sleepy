package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.PeriodHeaderFormatter

// 三行式表头字号(与 PeriodHeaderCellContent 内部一致):
// M3 labelSmall=11sp / labelMedium=12sp 下限, 乘 scale 联动。
// 仅作为宽度预算基线 (threeLineWidthDp) 与 legacy 两行渲染;
// 三行绘制字号由卡片尺寸自适应 (PeriodHeaderAdaptiveFont, 用户 2026-09-27 令)。
internal fun headerTimeStyle(scale: Float) = SleepyTextStyle.micro().copy(
    fontSize = (11f * scale).sp,
    lineHeight = (13f * scale).sp,
)

internal fun headerLabelStyle(scale: Float) = SleepyTextStyle.smallMeta().copy(
    fontWeight = FontWeight.SemiBold,
    fontSize = (12f * scale).sp,
    lineHeight = (14f * scale).sp,
)

/** 自适应字号 → Compose TextStyle: 数值单位是 sp (font.px 与 density 无关的字号语义)。 */
internal fun adaptiveHeaderTimeStyle(scale: Float, font: PeriodHeaderAdaptiveFont) =
    SleepyTextStyle.micro().copy(
        fontSize = (font.timeSize * scale).sp,
        lineHeight = (font.timeSize * 1.25f * scale).sp,
    )

internal fun adaptiveHeaderLabelStyle(scale: Float, font: PeriodHeaderAdaptiveFont) =
    SleepyTextStyle.smallMeta().copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = (font.labelSize * scale).sp,
        lineHeight = (font.labelSize * 1.25f * scale).sp,
    )

/**
 * 三行表头卡片内边距 — 预览/周视图/小组件唯一的边距事实来源 (用户 2026-09-28 令:
 * 预览是唯一视觉基准, 禁止任何一链自定边距)。单位 dp, 全部乘 scale。
 */
internal const val PERIOD_HEADER_CARD_PAD_DP = 3f

/** 三行式表头列所需宽度(dp): 在用户滑杆移动到极限时仍能完整显示三行文字。
 *  联合墨迹包络 = timeMax + 2×标签宽 (端点异锚各悬出半标签), 下限 = 3×开始时间宽轨道。
 *  与 PeriodHeaderCellContent 共享同一公式, 真实网格列宽与设置页预览卡共用。
 *
 * 2026-09-30 修截断: 列宽按自适应字号 (16sp) 投影, 不再用基准字号 (12sp) 测 —
 * 基准 12sp 测的墨迹宽比 16sp 实际渲染窄 33%, 列宽不够 → 文字被 .clip 裁掉。 */
internal fun threeLineWidthDp(
    slots: List<TimeSlot>,
    headerStyle: String,
    scale: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    hangingUnits: Float = 0f,
    showX: Boolean = false,
    targetLabelSp: Float = 16f,
): androidx.compose.ui.unit.Dp {
    val timeStyle = headerTimeStyle(scale)
    val labelStyle = headerLabelStyle(scale)
    // 投影到目标字号: 基准 12sp 测的宽度 × (目标字号 / 基准字号)
    val projection = targetLabelSp / PeriodHeaderAdaptiveFont.BASE_LABEL_SP
    var maxGroup = 0f
    for (slot in slots) {
        val label = if (showX && slot.nodeStart == slot.nodeEnd) {
            PeriodHeaderFormatter.fullLabel(slot.nodeStart, headerStyle)
        } else {
            PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, headerStyle)
        }
        val startWidth = measurer.measure(slot.displayStart, timeStyle).size.width.toFloat()
        val endWidth = measurer.measure(slot.displayEnd, timeStyle).size.width.toFloat()
        val labelWidth = measurer.measure(label, labelStyle).size.width.toFloat()
        val metrics = PeriodHeaderMetrics(
            startWidth = startWidth,
            endWidth = endWidth,
            labelWidth = labelWidth,
            showX = showX && slot.nodeStart == slot.nodeEnd,
        )
        maxGroup = maxOf(maxGroup, metrics.inkWidth(hangingUnits) * projection)
    }
    // + 卡片内边距 ×2 (PERIOD_HEADER_CARD_PAD_DP, 预览/网格/widget 同一常量), 乘 scale
    return with(density) { maxGroup.toDp() } + (2f * PERIOD_HEADER_CARD_PAD_DP * scale).dp
}

/**
 * 老式表头列所需宽度(dp): 用户 2026-10-03 第二轮定稿 — dash 轴几何。
 * 列内所有行的 dash (时间串 "08:00-09:35" 中间 "-") 落在同一 X, 标签同轴居中;
 * 列宽 = 轴左最大延伸 + 轴右最大延伸 + pad×2 ("最宽的那一行, 余量也流出来")。
 * 与 PeriodHeaderCellContent legacy 分支共享同一字号, 测量与渲染同源。
 */
internal fun legacyLineWidthDp(
    slots: List<TimeSlot>,
    headerStyle: String,
    scale: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    showX: Boolean = false,
): androidx.compose.ui.unit.Dp {
    val timeStyle = headerTimeStyle(scale)
    val labelStyle = headerLabelStyle(scale)
    val rows = slots.filter { !it.isPlaceholder }.map { slot ->
        val label = if (showX && slot.nodeStart == slot.nodeEnd) {
            PeriodHeaderFormatter.fullLabel(slot.nodeStart, headerStyle)
        } else {
            PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, headerStyle)
        }
        LegacyDashRowInk(
            labelPx = measurer.measure(label, labelStyle).size.width.toFloat(),
            timeLeftPx = measurer.measure(slot.displayStart, timeStyle).size.width.toFloat(),
            dashPx = measurer.measure("-", timeStyle).size.width.toFloat(),
            timeRightPx = measurer.measure(slot.displayEnd, timeStyle).size.width.toFloat(),
        )
    }
    val geometry = legacyDashColumnPx(rows, padPx = 0f)
    // + 卡片内边距 ×2 (PERIOD_HEADER_CARD_PAD_DP, 预览/网格/widget 同一常量), 乘 scale
    return with(density) { geometry.columnWidthPx.toDp() } + (2f * PERIOD_HEADER_CARD_PAD_DP * scale).dp
}

/**
 * 旧式表头时间列宽(dp)。hideTime=false 走 dash 轴几何(legacyLineWidthDp 同源);
 * hideTime=true 时时间行不再渲染, 列宽收口到最长节次标签实测宽 + 卡片边距,
 * 下限 36dp 防「第X节」等长标签被挤压。网格与设置页预览共用。
 */
internal fun legacyTimeWidthDp(
    slots: List<TimeSlot>,
    headerStyle: String,
    scale: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    showX: Boolean = false,
    hideTime: Boolean = false,
): androidx.compose.ui.unit.Dp {
    if (!hideTime) return legacyLineWidthDp(slots, headerStyle, scale, measurer, density, showX)
    val labelStyle = headerLabelStyle(scale)
    var maxLabel = 0f
    for (slot in slots) {
        val label = if (showX && slot.nodeStart == slot.nodeEnd) {
            PeriodHeaderFormatter.fullLabel(slot.nodeStart, headerStyle)
        } else {
            PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, headerStyle)
        }
        maxLabel = maxOf(maxLabel, measurer.measure(label, labelStyle).size.width.toFloat())
    }
    val content = with(density) { maxLabel.toDp() } + (2f * PERIOD_HEADER_CARD_PAD_DP * scale).dp
    return content.coerceAtLeast((36f * scale).dp)
}

/** 老式表头单行墨迹: 标签整宽 + 时间串三段 (start / dash / end, px 实测, 含行高)。 */
internal fun legacyRowInk(
    slot: TimeSlot,
    label: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    scale: Float,
): LegacyDashRowInk {
    val timeStyle = headerTimeStyle(scale)
    val labelStyle = headerLabelStyle(scale)
    val labelM = measurer.measure(label, labelStyle)
    val startM = measurer.measure(slot.displayStart, timeStyle)
    val dashM = measurer.measure("-", timeStyle)
    val endM = measurer.measure(slot.displayEnd, timeStyle)
    return LegacyDashRowInk(
        labelPx = labelM.size.width.toFloat(),
        timeLeftPx = startM.size.width.toFloat(),
        dashPx = dashM.size.width.toFloat(),
        timeRightPx = endM.size.width.toFloat(),
        // 行高取两行 union 用的实测值: label=标签行高, time=start 行高
        // (dash/end 与 start 同字号同行, 行高一致; 单值代表整行)
        labelHeightPx = labelM.size.height.toFloat(),
        timeHeightPx = startM.size.height.toFloat(),
    )
}

/** Shared by the real grid header and the settings preview. */
@Composable
fun PeriodHeaderCellContent(
    slot: TimeSlot,
    layout: String,
    style: String,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    hangingUnitsOverride: Float? = null,
    showXOverride: Boolean? = null,
    sharedFont: PeriodHeaderAdaptiveFont? = null,
    sharedPlacement: PeriodHeaderPlacement? = null,
    sharedLegacyAxis: androidx.compose.ui.unit.Dp? = null,
    hideTimeOverride: Boolean? = null,
) {
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val showX = showXOverride ?: AppPrefs.isPeriodHeaderShowX(context)
    val hideTime = hideTimeOverride ?: AppPrefs.isPeriodHeaderHideTime(context)
    val label = if (showX && slot.nodeStart == slot.nodeEnd) {
        PeriodHeaderFormatter.fullLabel(slot.nodeStart, style)
    } else {
        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, style)
    }
    val timeStyle = headerTimeStyle(scale)
    val labelStyle = headerLabelStyle(scale)

    // 内边距由卡片层统一负责 (SingleTimeHeadCell 3dp / 预览补 3dp),
    // 这里不再叠加 — 双重 3dp 曾把墨迹矩形挤出卡片贴边 (margin 归零, 2026-09-27);
    // legacy 侧 2026-10-04 对齐三行式同口径: legacyLineWidthDp 返回值已含
    // 2×pad, 卡片层 padding 就是唯一一层, 双层扣 6dp 即 legacy 墨迹溢出的根因。
    val contentModifier = modifier.fillMaxSize()
    BoxWithConstraints(modifier = contentModifier) {
        if (layout == "three_line") {
            // 三行式悬挂 (hanging 模型):
            //   1. 第一/三行时间共享同一左缘基线。
            //   2. 第二行端点异锚: -1 用标签右缘贴时间块左缘, +1 用标签左缘贴时间块右缘;
            //      u=0 时标签中心与时间块中心重合。
            //   3. 卡片宽覆盖全轨道和联合文字包络, 任何位置都不裁剪文字。
            //   4. 所有宽度由 TextMeasurer 实测, 不固定值。
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            // 基线测量用于宽度预算, 实际绘制字号由卡片尺寸自适应。
            val baseStartW = measurer.measure(slot.displayStart, timeStyle).size.width.toFloat()
            val baseEndW = measurer.measure(slot.displayEnd, timeStyle).size.width.toFloat()
            val baseLabelW = measurer.measure(label, labelStyle).size.width.toFloat()
            val hangingUnits = (hangingUnitsOverride ?: AppPrefs.getPeriodHeaderHanging(context))
                .coerceIn(-1f, 1f)
            val baseMetrics = PeriodHeaderMetrics(baseStartW, baseEndW, baseLabelW, showX && slot.nodeStart == slot.nodeEnd)
            // 用户 2026-09-29: 整列统一字号 — sharedFont 由调用方按全列最紧约束算一次;
            // 未传(预览)时逐卡自适应保持原行为。
            // 单位契约: compute 输入 sp. Dp.toPx()/density = sp (px→sp 正确);
            // dp 字面量 (52f 等) 数值≈sp, 禁再除 density (2026-09-30 修单位 bug).
            val adaptiveFont0 = sharedFont ?: PeriodHeaderAdaptiveFont.compute(
                cardWidthSp = with(density) { maxWidth.toPx() } / density.density,
                cardHeightSp = with(density) { maxHeight.toPx() } / density.density,
                inkWidthSp = baseMetrics.inkWidth(hangingUnits) / density.density,
                timeMaxWidthSp = baseMetrics.timeMax / density.density,
                labelWidthSp = baseLabelW / density.density,
            )
            var adaptiveFont = adaptiveFont0
            var timeStyleAdaptive = adaptiveHeaderTimeStyle(scale, adaptiveFont)
            var labelStyleAdaptive = adaptiveHeaderLabelStyle(scale, adaptiveFont)
            var startWidth = measurer.measure(slot.displayStart, timeStyleAdaptive).size.width.toFloat()
            var endWidth = measurer.measure(slot.displayEnd, timeStyleAdaptive).size.width.toFloat()
            var labelWidth = measurer.measure(label, labelStyleAdaptive).size.width.toFloat()
            var startH = measurer.measure(slot.displayStart, timeStyleAdaptive).size.height.toFloat()
            var labelH = measurer.measure(label, labelStyleAdaptive).size.height.toFloat()
            var endH = measurer.measure(slot.displayEnd, timeStyleAdaptive).size.height.toFloat()
            // 不重叠硬约束 (用户令 2026-10-03, 最低下限): 三行必须严格分隔,
            // 每行行盒高 ≥ 字体实际占高。卡片装不下三行行盒时字号等比缩小,
            // 宁小勿叠。实测行盒 px 对比可用高 px — 与 fontScale/density 无关,
            // 兜住上游字号 (columnFont 共享值按整列最矮行算, 本卡可能更矮)。
            val availHPx = with(density) { maxHeight.toPx() }
            val requiredHPx = startH + labelH + endH
            if (requiredHPx > availHPx && requiredHPx > 0f && availHPx > 0f) {
                val guard = availHPx / requiredHPx
                adaptiveFont = PeriodHeaderAdaptiveFont(
                    timeSize = (adaptiveFont.timeSize * guard).coerceAtLeast(0.1f),
                    labelSize = (adaptiveFont.labelSize * guard).coerceAtLeast(0.1f),
                )
                timeStyleAdaptive = adaptiveHeaderTimeStyle(scale, adaptiveFont)
                labelStyleAdaptive = adaptiveHeaderLabelStyle(scale, adaptiveFont)
                startWidth = measurer.measure(slot.displayStart, timeStyleAdaptive).size.width.toFloat()
                endWidth = measurer.measure(slot.displayEnd, timeStyleAdaptive).size.width.toFloat()
                labelWidth = measurer.measure(label, labelStyleAdaptive).size.width.toFloat()
                startH = measurer.measure(slot.displayStart, timeStyleAdaptive).size.height.toFloat()
                labelH = measurer.measure(label, labelStyleAdaptive).size.height.toFloat()
                endH = measurer.measure(slot.displayEnd, timeStyleAdaptive).size.height.toFloat()
            }
            val metrics = PeriodHeaderMetrics(startWidth, endWidth, labelWidth, showX && slot.nodeStart == slot.nodeEnd)
            // 用户令 2026-09-30: 整列统一排布 — sharedPlacement 由调用方按全列
            // 最宽行解一次, 本行各元素平移到基准行对应元素的 middle point;
            // 未传 (预览/单卡) 时退回逐行排布保持原行为。
            val placement = sharedPlacement ?: metrics.solvePlacement(hangingUnits)
            // 真实墨迹并集左/右缘 (px, 相对元素矩形左缘).
            // 左缘: 时间块左缘 (timeBaseLeft) 与标签左缘 (labelLeft) 的最小值;
            // 右缘: max(timeBaseLeft+时间块宽, labelLeft+标签宽).
            // inner 盒宽 = 并集宽, 内层偏移转相对并集左缘, 外层 Box Alignment.Center
            // 自然 X+Y 双居中 (用户 2026-10-04 实测: 原版用 placement.contentWidth 当
            // 盒宽, 标签悬挂方向不同使并集左缘常 < 0, 视觉上整块贴卡左而非居中).
            val timeMaxW = maxOf(startWidth, endWidth)
            val inkLeftPx = min(placement.timeBaseLeft, placement.labelLeft)
            val inkRightPx = max(
                placement.timeBaseLeft + timeMaxW,
                placement.labelLeft + labelWidth
            )
            val inkUnionWidthPx = (inkRightPx - inkLeftPx).coerceAtLeast(0f)
            val inkUnionWidth = with(density) { inkUnionWidthPx.toDp() }
            // 三行行盒总高可能 < 卡高 (缩字守卫触发时) — 盒高 = 行盒总高,
            // 外层 Center 使上下间隙均分, 水平同理左右均分.
            val contentHeight = with(density) { (startH + labelH + endH).toDp() }
            val timeBaseLeft = with(density) { (placement.timeBaseLeft - inkLeftPx).toDp() }
            val labelLeft = with(density) { (placement.labelLeft - inkLeftPx).toDp() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(inkUnionWidth)
                        .height(contentHeight),
                ) {
                    Text(
                        text = slot.displayStart,
                        style = timeStyleAdaptive,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = timeBaseLeft),
                    )
                    Text(
                        text = label,
                        style = labelStyleAdaptive,
                        color = colors.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = labelLeft),
                    )
                    Text(
                        text = slot.displayEnd,
                        style = timeStyleAdaptive,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = timeBaseLeft),
                    )
                }
            }
        } else {
            // Legacy 两行 (用户 2026-10-03 定稿 + 2026-10-03 续 union 垂直):
            //   ① dash 轴对齐 + 完整时间串容纳 (上轮已修: 列宽按 start+dash+end 算);
            //   ② 两行墨迹是一个整体矩形 — 卡片对这个整体做水平 + 垂直居中,
            //      不是每行各自居中 (上轮 Arrangement.Center 居中粗放, 高不够
            //      时两行互相挤叠的根因);
            //   ③ 垂直永不重叠: 每行高度 = 该行字体行高;
            //   ④ 高度不够 → 两行同比例缩小, 缩字优先于重叠 (minScale 钳底)。
            //   sharedLegacyAxis 由网格侧按全列最紧约束解一次; 垂直解每卡就地做 —
            //   字号基线/行高/卡高三条输入全列同源, 解出的 fontScale 天然一致。
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val rowInk = legacyRowInk(slot, label, measurer, scale)
            val axisX = sharedLegacyAxis ?: with(density) {
                maxOf(rowInk.labelPx / 2f, rowInk.timeLeftPx).toDp()
            }
            val baseFont = PeriodHeaderAdaptiveFont(
                timeSize = PeriodHeaderAdaptiveFont.BASE_TIME_SP,
                labelSize = PeriodHeaderAdaptiveFont.BASE_LABEL_SP,
            )
            if (hideTime) {
                // 隐藏时间行 (用户 2026-10-05): 只剩标签一行 — 不参与 dash 轴/双行
                // union 垂直解, 卡片内水平+垂直整体居中; 卡高装不下时同口径缩字。
                val cardHPx = with(density) { maxHeight.toPx() }
                val labelHPx = rowInk.labelHeightPx
                val fontScale = if (labelHPx > 0f && cardHPx in 1f..<labelHPx)
                    (cardHPx / labelHPx).coerceAtLeast(0.6f) else 1f
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = adaptiveHeaderLabelStyle(scale * fontScale, baseFont),
                        color = colors.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
            val vertical = legacyColumnVerticalFit(
                rows = listOf(rowInk),
                cardContentHeightPx = with(density) { maxHeight.toPx() },
                // 用户原话 2026-10-04 (行间距不对): label 与 time 之间恒留 2dp×scale
                // 语义间距, 缩字不压行距 — gap 优先被吞, 吞完才缩字。
                rowGapPx = with(density) { (2f * scale).dp.toPx() },
            )
            // 字号按 fontScale 联动 — legacy 基准字号 (label 12sp/time 11sp, 与
            // headerTimeStyle/headerLabelStyle 同源), 缩字时整体乘 fontScale:
            // 行高与字号同比缩, 垂直解的 labelH/timeH 与真实渲染行高一致。
            val effectiveScale = scale * vertical.fontScale
            val labelStyleScaled = adaptiveHeaderLabelStyle(effectiveScale, baseFont)
            val timeStyleScaled = adaptiveHeaderTimeStyle(effectiveScale, baseFont)
            val labelLeftDp = with(density) { (axisX.toPx() - rowInk.labelPx / 2f).toDp() }
            // dash 字形中心坐轴 (与模型同口径): 串左缘 = 轴 − timeLeftPx − dashPx/2
            // (2026-10-04 设备实锤漏 dashPx/2, dash 全列偏右半字宽)。
            val timeLeftDp = with(density) {
                (axisX.toPx() - rowInk.timeLeftPx - rowInk.dashPx / 2f).toDp()
            }
            val labelTopDp = with(density) { vertical.labelTopPx.toDp() }
            val timeTopDp = with(density) { vertical.timeTopPx.toDp() }
            // 两行都按 vertical 解绝对定位 (offset), 外层必须是 Box —
            // 用 Column 会先按文字盒流式堆叠 (label 盒高 39px) 再叠 offset,
            // time 行被凭空推下一个 label 高 (2026-10-04 设备实锤: 行距多 39px)。
            Box(modifier = Modifier.fillMaxSize()) {
                // 第一行: 标签, 以 dash 轴水平居中 + union 包络垂直顶定位
                Text(
                    text = label,
                    style = labelStyleScaled,
                    color = colors.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.offset(x = labelLeftDp, y = labelTopDp),
                )
                // 第二行: 时间串, dash 在 axisX 上; 用一个 Box 让 start/dash/end 共享同一基线,
                // 整体左缘 = timeLeftDp, 各子段按实测宽顺序排布 (不重叠, 不截断).
                Box(modifier = Modifier.offset(x = timeLeftDp, y = timeTopDp)) {
                    val startW = with(density) { rowInk.timeLeftPx.toDp() }
                    val dashW = with(density) { rowInk.dashPx.toDp() }
                    Text(
                        text = slot.displayStart,
                        style = timeStyleScaled,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.align(Alignment.CenterStart).width(startW),
                    )
                    Text(
                        text = "-",
                        style = timeStyleScaled,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = startW).width(dashW),
                    )
                    Text(
                        text = slot.displayEnd,
                        style = timeStyleScaled,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = startW + dashW),
                    )
                }
            }
            }
        }
    }
}
