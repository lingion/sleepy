package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
) {
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val showX = showXOverride ?: AppPrefs.isPeriodHeaderShowX(context)
    val label = if (showX && slot.nodeStart == slot.nodeEnd) {
        PeriodHeaderFormatter.fullLabel(slot.nodeStart, style)
    } else {
        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, style)
    }
    val timeStyle = headerTimeStyle(scale)
    val labelStyle = headerLabelStyle(scale)

    // 三行模式: 内边距由卡片层统一负责 (SingleTimeHeadCell 3dp / 预览补 3dp),
    // 这里不再叠加 — 双重 3dp 曾把墨迹矩形挤出卡片贴边 (margin 归零, 2026-09-27)。
    // 旧版模式维持原根部 3dp 行为不变。
    val contentModifier = if (layout == "three_line") modifier.fillMaxSize()
    else modifier.fillMaxSize().padding((3f * scale).dp)
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
            val contentWidthPx = placement.contentWidth
            val contentWidth = with(density) { contentWidthPx.toDp() }
            val contentHeight = with(density) { (startH + labelH + endH).toDp() }
            val timeBaseLeft = with(density) { placement.timeBaseLeft.toDp() }
            val labelLeft = with(density) { placement.labelLeft.toDp() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(contentWidth).height(contentHeight)) {
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = labelStyle,
                    color = colors.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = slot.timeString,
                    style = timeStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}
