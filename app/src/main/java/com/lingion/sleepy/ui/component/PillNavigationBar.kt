package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable

/**
 * 底栏单项数据。v2 起组件内部要管理 thumb 滑块几何(目标位置/逐字符扫过变色),
 * content lambda 拿不到子项坐标, API 从 { PillNavItem(...) } 改为数据驱动。
 */
data class PillNavItemSpec(val icon: ImageVector, val label: String)

/**
 * Dock(悬浮)模式下主内容需要的额外底部滚动余量 — 让最后一项能滚到 Dock 上方完全可见
 * (FAB 语义: 内容画到屏幕底, 但滚动范围多留遮挡物的高度)。贴底模式为 0dp。
 * MainActivity 在 dock 分支 provide Dock 总高, 四个 tab 页的滚动容器读取追加。
 */
val LocalNavExtraBottomPadding = compositionLocalOf { 0.dp }

/** Dock 形态常量 — 悬浮几何三处共用(组件本体/滚动余量换算/维护锚点) */
object NavDockSpec {
    val horizontalMargin = 16.dp   // 左右距屏幕边缘
    val bottomFloat = 12.dp        // 药丸底边再悬于手势条上方的高度
    val itemSeat = 64.dp           // 每 tab 座位宽(= 图标药丸宽)
    val navBarExtra = 6.dp         // 原 top padding(药丸内上下留白取一半对称)
}

/**
 * 底部导航栏 (v2)
 *
 * 动画: 与 SegmentedSwitcher 同一套 — 单个 secondaryContainer thumb 色块由物理弹簧
 * (Animatable+spring) 在药丸带上从旧 tab 滑到新 tab; 图标按「图标区间 ∩ thumb 区间」
 * 覆盖率 lerp 变色, 文字逐字符按「字符矩形 ∩ thumb 区间」扫过上色 — 色块边缘扫过哪个字
 * 那个字才变色。thumb 位置 graphicsLayer 直读弹簧浮点值(无 placeRelative 取整量化),
 * 高刷屏下是连续非线性曲线。图标/文字逐帧重组着色(4 项小成本, 同 SegmentedSwitcher 先例)。
 *
 * 几何真值: 各 tab 药丸中心/文字左缘/bar 原点全部 onGloballyPositioned 实测(positionInRoot
 * 差值), SpaceEvenly + 变宽 label 的实际分布不靠推算。布局变化(旋转/语言/形态)时
 * centers key 变化 → LaunchedEffect 重跑 → thumb 平滑滑到新位置。
 */
@Composable
fun PillNavigationBar(
    items: List<PillNavItemSpec>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dock: Boolean = false
) {
    val colors = SleepyTheme.colors
    val density = LocalDensity.current
    val count = items.size.coerceAtLeast(1)
    val pillHalf = with(density) { 32.dp.toPx() }
    val iconHalf = with(density) { 10.dp.toPx() }

    // ── 几何真值(root 坐标, bar 相对坐标在读取时现算) ──
    var barRootX by remember { mutableFloatStateOf(0f) }
    var barRootY by remember { mutableFloatStateOf(0f) }
    var barLaidOut by remember { mutableStateOf(false) }
    val pillRootXs = remember(count) { mutableStateListOf<Float>().apply { repeat(count) { add(0f) } } }
    var pillRootY by remember { mutableFloatStateOf(0f) }
    val labelRootXs = remember(count) { mutableStateListOf<Float>().apply { repeat(count) { add(0f) } } }
    // 各 tab 文字字符矩形(文本局部坐标, onTextLayout 时缓存 — 非状态, 着色帧顺带读)
    val charRectsCache = remember { mutableMapOf<Int, List<Rect>>() }

    // ── thumb 位移(px 浮点真值, 弹簧驱动; 初次定位 snap, 之后 animate) ──
    val thumbX = remember { Animatable(0f) }
    // 逐帧快照: 驱动 icon/label 扫过变色
    val thumbCenterState = remember { mutableFloatStateOf(0f) }
    var thumbPlaced by remember { mutableStateOf(false) }

    // 读 pillRootXs/barRoot → 布局变化时重启 effect 重新定目标
    val centers = List(count) { pillRootXs[it] + pillHalf - barRootX }
    LaunchedEffect(centers, barLaidOut, selectedIndex) {
        val idx = selectedIndex.coerceIn(0, count - 1)
        if (!barLaidOut || pillRootXs[idx] <= 0f) return@LaunchedEffect
        val target = centers[idx]
        if (!thumbPlaced) {
            thumbX.snapTo(target)
            thumbCenterState.floatValue = target
            thumbPlaced = true
        } else {
            // 与 SegmentedSwitcher 同参: 高硬度+无回弹, 用户实测 MediumLow 拖沓不跟手
            thumbX.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                block = { thumbCenterState.floatValue = value }
            )
        }
    }

    // 形态: 贴底 = 通栏矩形(现状); Dock = 悬浮药丸 — 由调用方叠加在内容之上,
    // 本组件只负责自己的几何: 紧凑定宽(按 tab 数)、药丸圆角、投影、悬于手势条上方
    val barShape = if (dock) RoundedCornerShape(percent = 50) else RectangleShape
    val shadowMod = if (dock) {
        Modifier.shadow(elevation = 8.dp, shape = barShape, clip = false)
    } else Modifier
    // Dock 药丸定宽: 每 tab 一个 64dp 座位 + Row 水平内边距 6dp×2 + 屏幕两侧 16dp 边距
    // (宽度内含两侧留白 = 最窄屏 320dp 也放得下) — iOS Dock 式紧凑, 不通栏
    // (此前 Row fillMaxWidth 在 wrap-content overlay 里撑满全屏 = 伪悬浮)
    val widthMod = if (dock) {
        Modifier.width(
            NavDockSpec.itemSeat * count + 12.dp + NavDockSpec.horizontalMargin * 2
        )
    } else Modifier.fillMaxWidth()
    Box(
        modifier = modifier
            .then(shadowMod)
            .then(widthMod)
            .clip(barShape)
            .background(colors.surfaceContainer)
            .then(
                if (dock) {
                    // Dock: 手势条 inset 只用于把药丸悬上来; 贴底: 原样整条让出
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = NavDockSpec.bottomFloat)
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                }
            )
            .padding(top = 6.dp, bottom = if (dock) 6.dp else 8.dp)
            .onGloballyPositioned { c ->
                barRootX = c.positionInRoot().x
                barRootY = c.positionInRoot().y
                barLaidOut = true
            }
    ) {
        // 层0: thumb 色块 — graphicsLayer 直读弹簧浮点值, 无整数取整, 高刷连续
        // 首次定位前 alpha 0(否则 thumb 停在 x=0 闪现左上角)
        Box(
            Modifier
                .size(width = 64.dp, height = 32.dp)
                .graphicsLayer {
                    alpha = if (thumbPlaced) 1f else 0f
                    translationX = thumbX.value - pillHalf
                    translationY = pillRootY - barRootY
                }
                .clip(SleepyTheme.shapes.large)
                .background(colors.secondaryContainer)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, item ->
                val isSel = i == selectedIndex
                val tc = thumbCenterState.floatValue
                val thumbStart = tc - pillHalf
                val thumbEnd = tc + pillHalf

                // 图标: 图标区间 ∩ thumb 区间 覆盖率
                val iconCov = intervalCoverage(
                    thumbStart, thumbEnd,
                    pillRootXs[i] - barRootX - iconHalf,
                    pillRootXs[i] - barRootX + iconHalf
                )
                // 文字: 逐字符扫过(NaN = 字形未就绪 → 退化整词翻转)
                val rects = charRectsCache[i]
                val labelCov = rects?.let {
                    sweepCoverageAt(it, thumbStart, thumbEnd, labelRootXs[i] - barRootX)
                } ?: Float.NaN
                val t = if (labelCov.isNaN()) (if (isSel) 1f else 0f) else labelCov
                val labelColor = lerp(colors.onSurfaceVariant, colors.onSurface, t)

                Column(
                    modifier = Modifier
                        .noRippleClickable { onSelect(i) }
                        .semantics { role = Role.Tab; selected = isSel }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .onGloballyPositioned { c ->
                                pillRootXs[i] = c.positionInRoot().x
                                pillRootY = c.positionInRoot().y
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = lerp(colors.onSurfaceVariant, colors.onSurface, iconCov),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        onTextLayout = { result ->
                            // 字形只在文本/字重变化时重排, 缓存避免每帧重算字符矩形
                            charRectsCache[i] = result.charRects()
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = labelColor,
                        modifier = Modifier.onGloballyPositioned { c ->
                            labelRootXs[i] = c.positionInRoot().x
                        }
                    )
                }
            }
        }
    }
}

/** 区间 [aStart,aEnd] 对 [bStart,bEnd] 的横向覆盖率 0..1 */
private fun intervalCoverage(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Float {
    val w = bEnd - bStart
    if (w <= 0f) return 0f
    return (aEnd.coerceAtMost(bEnd) - aStart.coerceAtLeast(bStart)).coerceIn(0f, w) / w
}

/**
 * 逐字符扫过覆盖率: rects(文本局部坐标) + 文字左缘偏移 → 与 thumb 区间求交集权重平均。
 * 与 SegmentedSwitcher.segmentLabelColor 同一算法(横向交集/字符宽加权/lerp),
 * 这里以「文字左缘 + thumb 中心±半宽」参数化 — thumb 为中心坐标制。
 */
private fun sweepCoverageAt(
    rects: List<Rect>,
    thumbStart: Float,
    thumbEnd: Float,
    labelLeftEdge: Float
): Float {
    if (rects.isEmpty()) return Float.NaN
    val left = rects.minOf { it.left }
    val right = rects.maxOf { it.right }
    if (right - left <= 0f) return Float.NaN
    var sum = 0f
    var total = 0f
    for (r in rects) {
        val w = r.width
        if (w <= 0f) continue
        val l = r.left + labelLeftEdge
        val e = r.right + labelLeftEdge
        sum += (thumbEnd.coerceAtMost(e) - thumbStart.coerceAtLeast(l)).coerceIn(0f, w)
        total += w
    }
    if (total <= 0f) return Float.NaN
    return (sum / total).coerceIn(0f, 1f)
}

/** 字符级 bounding box 列表(无效矩形剔除; 与 SegmentedSwitcher 同一兜底) */
private fun TextLayoutResult.charRects(): List<Rect> =
    (0 until layoutInput.text.length).mapNotNull { i ->
        try {
            val r = getBoundingBox(i)
            if (r.width > 0f && r.height > 0f) r else null
        } catch (_: Exception) {
            null
        }
    }
