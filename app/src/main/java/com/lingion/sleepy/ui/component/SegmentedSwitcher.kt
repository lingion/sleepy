package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlin.math.roundToInt

/**
 * Segmented Switcher — 仿 switchable.html .switcher
 *
 *  ┌──────────────────────────────────────────┐
 *  │ ┌────────────────────┐  ┌──────────────┐ │
 *  │ │ 7days full (active)│  │ cards        │ │
 *  │ └────────────────────┘  └──────────────┘ │
 *  └──────────────────────────────────────────┘
 *
 * 容器: surface-container (M3), 圆角 14dp
 * 选中: secondary-container 色块（无描边，色块填充风格）
 *
 * 动画 (v2, iOS 原生级): 单个 thumb 色块由物理弹簧(Animatable+spring)在轨道上从旧段滑到新段;
 * 每帧读 thumb 实时像素位置, 每个字符按「自身矩形 ∩ thumb 矩形」的横向覆盖率 lerp 上色 —
 * 色块边缘扫过哪个字, 那个字才跟着变色, 逐像素擦除感, 与 thumb 严格同帧。
 * thumb 位置在布局阶段读取(只重排不重组); 字色在组合阶段读取(逐帧重组着色, 文字量极小)。
 *
 * containerColor: 默认 surfaceContainer(课表页直接铺在页面 background 上);
 * 嵌进 surfaceContainer 卡片时同色会隐形, 调用方传 surfaceContainerHighest 降一级保持对比。
 * 宽度: 组件不自撑满, 由调用方 modifier 决定(全宽传 fillMaxWidth; 嵌标题行由外层精确测宽)。
 */
@Composable
fun <T> SegmentedSwitcher(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null
) {
    val colors = SleepyTheme.colors
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val count = options.size

    // thumb 位移: Animatable 值 = 逻辑段坐标(0..count-1), spring 驱动
    // 高硬度+无阻尼: 用户反馈 MediumLow 拖沓不跟手, 提到 High 段切换 ≈150ms 收束
    val thumbX = remember { Animatable(selectedIndex.toFloat()) }
    // 逐帧快照: animateTo 期间把当前值同步到 state, 供文字层逐帧重组着色
    var thumbXState by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        thumbX.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            block = { thumbXState = value.roundToInt() }
        )
    }

    // 每段文字的字符矩形缓存(非 state: 字色由 thumbXState 逐帧驱动, 缓存只是普通字典,
    // 避免 onTextLayout 每次重组都重新分配列表 — 上一版每次着色都 charRects() 全量重算 = "卡卡的")
    val charRectsCache = remember { mutableMapOf<Int, List<Rect>>() }

    // 轨道像素宽(padding 后的可用宽) — thumb 像素位置与字符覆盖率共同的真值
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor ?: colors.surfaceContainer)
            .onGloballyPositioned { coords ->
                // coords 是含 padding 的外区; 轨道宽 = 外区宽 - 左右 4dp padding
                trackWidthPx = (coords.size.width - with(density) { 8.dp.toPx() }).toInt().coerceAtLeast(0)
            }
            .padding(4.dp)
    ) {
        val segW = if (count == 0) 0f else trackWidthPx.toFloat() / count
        val thumbStart = thumbXState * segW
        val thumbEnd = thumbStart + segW

        Layout(
            content = {
                // 层0: thumb 色块 — 位置每帧由弹簧动画驱动
                // graphicsLayer{translationX} 承担亚像素余数: place 层取整丢掉的高刷帧间位移
                // 由 draw 层浮点补回, 120Hz 下连续非线性曲线, 不再有"一格一格"的整数跳变
                Box(
                    Modifier
                        .fillMaxHeight()
                        .graphicsLayer {
                            val segWpx = trackWidthPx.toFloat() / count
                            val exact = thumbX.value * segWpx
                            translationX = exact - exact.toInt()
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.secondaryContainer)
                )
                // 层1..n: 点击段 — 透明, 只负责点击与无障碍
                options.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(options[index].first) }
                            .semantics { role = Role.Button }
                    )
                }
                // 层n+1..2n: 文字层 — 每个字符按覆盖率 lerp 上色, 与 thumb 同帧
                options.forEachIndexed { index, (_, label) ->
                    Text(
                        text = label,
                        onTextLayout = { result ->
                            // 字形只在文本/字重变化时重排, 缓存结果避免每帧重算字符矩形
                            charRectsCache[index] = result.charRects()
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = segmentLabelColor(
                            index = index,
                            charRects = charRectsCache[index],
                            thumbStartPx = thumbStart,
                            thumbEndPx = thumbEnd,
                            segmentWidthPx = segW,
                            selected = index == selectedIndex,
                            selectedColor = colors.onSecondaryContainer,
                            unselectedColor = colors.onSurfaceVariant
                        )
                    )
                }
            },
            measurePolicy = { measurables, constraints ->
                val segmentWidth = if (count == 0) 0 else constraints.maxWidth / count
                val heightPx = constraints.maxHeight

                val thumbPlaceable = measurables[0].measure(
                    Constraints(minWidth = segmentWidth, maxWidth = segmentWidth, minHeight = heightPx, maxHeight = heightPx)
                )
                val clickPlaceables = measurables.drop(1).take(count).map { m ->
                    m.measure(Constraints(minWidth = segmentWidth, maxWidth = segmentWidth, minHeight = heightPx, maxHeight = heightPx))
                }
                // 文字按自然尺寸测(min=0): 强制撑满段宽/段高会让 placeable 恒等于段尺寸,
                // 居中偏移 (段-物)/2 恒为 0, 文字就钉在左上角 — 必须自然测, place 时再居中
                val labelPlaceables = measurables.drop(1 + count).map { m ->
                    m.measure(Constraints(maxWidth = segmentWidth, maxHeight = heightPx))
                }

                layout(constraints.maxWidth, heightPx) {
                    // thumb 布局位置用弹簧浮点真值(布局阶段读 Animatable, 每帧自动重排):
                    // round 到 int 会把 120Hz 的连续插值压扁成离散跳格(用户实机看到一格一格) —
                    // placeRelative 用 float 精度保留逐帧亚像素位移, 高刷屏才是顺滑非线性曲线
                    val layoutSegW = constraints.maxWidth.toFloat() / count
                    // place 层是整数像素, 取整不可避免 — 亚像素平滑改由外层 graphicsLayer 承担:
                    // thumb Box 自带 Modifier.graphicsLayer{ translationX = 弹簧余数 }, 见 thumb Box。
                    thumbPlaceable.placeRelative(x = (thumbX.value * layoutSegW).toInt(), y = 0)
                    clickPlaceables.forEachIndexed { i, p -> p.placeRelative(x = i * segmentWidth, y = 0) }
                    // 文字水平: placeable 自身已在段宽约束下测出, 需在段内水平居中; 垂直: 段内居中
                    labelPlaceables.forEachIndexed { i, p ->
                        p.placeRelative(
                            x = i * segmentWidth + (segmentWidth - p.width) / 2,
                            y = (heightPx - p.height) / 2
                        )
                    }
                }
            }
        )
    }
}

/**
 * 逐字符覆盖率上色: thumb 像素区间 [thumbStart, thumbEnd] 与每个字符矩形求横向交集,
 * 覆盖率 0..1 → lerp(未选中色, 选中色, coverage)。
 * 布局未就绪/字符矩形无效时退化为整词翻转(选中色或未选中色)。
 */
private fun segmentLabelColor(
    index: Int,
    charRects: List<Rect>?,
    thumbStartPx: Float,
    thumbEndPx: Float,
    segmentWidthPx: Float,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color
): Color {
    val rects = charRects
    if (segmentWidthPx <= 0f || rects.isNullOrEmpty()) return if (selected) selectedColor else unselectedColor

    // 文字在段内水平居中后的真实左缘 = 段起点 + (段宽 - 文字宽)/2, 覆盖率坐标必须用它
    val textWidth = (rects.maxOf { it.right } - rects.minOf { it.left })
    val segStart = index * segmentWidthPx + (segmentWidthPx - textWidth) / 2f
    var sum = 0f
    var total = 0f
    for (r in rects) {
        val w = r.width
        if (w <= 0f) continue
        val l = r.left + segStart
        val e = r.right + segStart
        // 无交集的字符直接算 0, 不进交集计算(性能: 短词逐字成本可忽略)
        val overlap = (thumbEndPx.coerceAtMost(e) - thumbStartPx.coerceAtLeast(l)).coerceIn(0f, w)
        sum += overlap
        total += w
    }
    if (total <= 0f) return if (selected) selectedColor else unselectedColor
    return lerp(start = unselectedColor, stop = selectedColor, fraction = (sum / total).coerceIn(0f, 1f))
}

/** 字符级 bounding box 列表(无效矩形剔除; emoji/组合字符异常兜底) */
private fun TextLayoutResult.charRects(): List<Rect> =
    (0 until layoutInput.text.length).mapNotNull { i ->
        try {
            val r = getBoundingBox(i)
            if (r.width > 0f && r.height > 0f) r else null
        } catch (_: Exception) {
            null
        }
    }
