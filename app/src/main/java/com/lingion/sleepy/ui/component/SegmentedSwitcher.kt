package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassDepth
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassOverlay
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassOverlaySamplingHost
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassStyle
import com.lingion.sleepy.ui.component.liquidglass.FlClashDockPhysics
import com.lingion.sleepy.ui.component.liquidglass.glassControl
import com.lingion.sleepy.ui.component.liquidglass.liquidGlass
import com.lingion.sleepy.ui.component.liquidglass.controlFill
import kotlinx.coroutines.delay
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
/**
 * 选项定义，支持可选徽标（如「实验」胶囊标签）
 */
data class SegmentOption<T>(
    val value: T,
    val label: String,
    val badge: String? = null
)

@JvmName("SegmentedSwitcherPairs")
@Composable
fun <T> SegmentedSwitcher(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    // ui-ops 语义定位前缀：非空时每个可点击段注册 dbgTag("<prefix>#<index>")，
    // find/action 可按段号或 label 文本命中；默认 null 不打标，行为与旧版一致。
    dbgTagPrefix: String? = null,
) {
    SegmentedSwitcher(
        options = options.map { SegmentOption(it.first, it.second) },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        containerColor = containerColor,
        dbgTagPrefix = dbgTagPrefix,
    )
}

@JvmName("SegmentedSwitcherWithOptions")
@Composable
fun <T> SegmentedSwitcher(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    dbgTagPrefix: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val count = options.size
    if (count == 0) return

    // thumb 位移: Animatable 值 = 逻辑段坐标(0..count-1), spring 驱动
    // 高硬度+无阻尼: 用户反馈 MediumLow 拖沓不跟手, 提到 High 段切换 ≈150ms 收束
    val thumbX = remember { Animatable(selectedIndex.toFloat()) }
    // 动画与手势共用连续位置，文字着色不再取整跳段。
    var thumbPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // 按压瞬间即浮起 (2026-11-06 用户: "手按压瞬间像Toggle和dock滑块一样浮起有透射效果"):
    // pressing 在 awaitFirstDown 立刻置位, 不等拖动 slop; tapLift 给点按/落位补一段短升镜
    // (GlassSwitch tapGeneration 同款语义, 200ms)。
    var pressing by remember { mutableStateOf(false) }
    var tapGeneration by remember { mutableIntStateOf(0) }
    var tapLift by remember { mutableStateOf(false) }
    var gestureEnd by remember { mutableIntStateOf(0) }
    // 拖动中滑块浮起成镜片(用户 2026-11-04: 像 Toggle/Dock 那样浮着拖):
    // 高度 42dp 轨道下允许放大 1.155 仍不出屏; 松手弹簧回落, 投影随之消散。
    val thumbLift by animateFloatAsState(if (dragging || pressing || tapLift) 1f else 0f, tween(150), label = "segment-thumb-lift")
    LaunchedEffect(tapGeneration) {
        if (tapGeneration > 0) {
            tapLift = true
            delay(200)
        }
        tapLift = false
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(selectedIndex, dragging, gestureEnd, count) {
        if (!dragging) {
            thumbX.snapTo(thumbPosition)
            thumbX.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = SleepyThumbSpring,
                block = { thumbPosition = value },
            )
        }
    }

    // 每段文字的字符矩形缓存(非 state: 字色由 thumbPosition 逐帧驱动, 缓存只是普通字典,
    // 避免 onTextLayout 每次重组都重新分配列表 — 上一版每次着色都 charRects() 全量重算 = "卡卡的")
    val charRectsCache = remember { mutableMapOf<Int, List<Rect>>() }

    // 轨道像素宽(padding 后的可用宽) — thumb 像素位置与字符覆盖率共同的真值
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableIntStateOf(0) }

    // 按压透射镜片 (2026-11-06 用户: 像 Toggle/Dock 那样浮起有透射):
    // 只采样宿主 scene 记录层 — 与药丸静置 tinted 面板 (glassControl) 同一条无环通路。
    // 严禁在本组件注册任何本地背板: 轨道 Box 是滑块的祖先, 祖先注册的背板被后代采样
    // = prepareTreeImpl 无限递归 (2026-11-05 实机 SIGSEGV 根因)。
    // 按压透射镜片 (2026-11-06 用户: 像 Toggle/Dock 那样浮起有透射):
    // 只采样宿主 scene 前置层 (backdropAt) — 轨道底色与文字先于 thumb 节点绘制,
    // 深度 ≥1 的前置层已含滑块正下方的轨道/文字像素, 镜片折射"里面有东西"。
    // 严禁在本组件注册本地背板给后代采样: 轨道 Box 是滑块的祖先, 祖先注册的
    // 背板被后代采样 = prepareTreeImpl 无限递归 (2026-11-05 实机 SIGSEGV 根因;
    // 2026-11-08 Robolectric 全量跑同点复炸, exit 132, 205 个测试类没跑到)。
    val glassHost = LocalGlassHost.current
    val glassActive = LocalUiStyle.current == UiStyle.LiquidGlass && glassHost != null
    val samplingHost = if (LocalGlassOverlay.current) LocalGlassOverlaySamplingHost.current ?: glassHost else glassHost
    val consumerDepth = LocalGlassDepth.current
    // 深度夹到 ≥1: 前置层必须包含同 Layout 里先画的轨道底色与文字 — 镜片采到它们
    // 才折射得出"里面有东西"的玻璃 (2026-11-08 用户: 不得是里面没内容的透明色块)。
    val lensBackdrop: Backdrop? = if (glassActive) samplingHost?.backdropAt(maxOf(1f, consumerDepth)) else null
    val context = LocalContext.current
    val baseLensStyle = LiquidGlassStyle.lens(colors, LocalDarkTheme.current).copy(
        refraction = -8.5,
        bevel = 9.0,
        brightness = if (LocalDarkTheme.current) 1.12 else 1.08,
        rim = if (LocalDarkTheme.current) 0.48 else 0.72,
        rimWidth = 1.2,
        endShade = 0.06,
        dispersion = 1.8,
        hairline = 0.28,
        contrast = 0.0,
    )
    val lensStyle = baseLensStyle.scale(thumbLift.toDouble())

    Box(
        modifier = modifier
            .height(42.dp)
            .glassControl(shape = RoundedCornerShape(14.dp), grouped = true, enabled = false)
            // 不再外层 clip: 浮起滑块需要越出轨道边缘(同 GlassSwitch requiredSize 溢出模式)。
            // 静止安全: 轨道底色自带 14dp 圆角, 滑块 12dp 圆角四角弧经几何核对全在底色
            // 圆角内侧(对角余量 ~3px), 文字/点击段均界内 — clip 移除不改变静止像素。
            .background(controlFill(containerColor ?: colors.surfaceContainer), RoundedCornerShape(14.dp))
            .onGloballyPositioned { coords ->
                // coords 是含 padding 的外区; 轨道宽 = 外区宽 - 左右 4dp padding
                trackWidthPx = (coords.size.width - with(density) { 8.dp.toPx() }).toInt().coerceAtLeast(0)
            }
            .padding(4.dp)
            .selectableGroup()
            .pointerInput(options, selectedIndex, isRtl, trackWidthPx, glassActive) {
                // Classic 模式完全走原有 selectable 点击路径，不安装自定义拖动接管器。
                if (!glassActive) return@pointerInput
                if (count < 2 || trackWidthPx <= 0) return@pointerInput
                val segmentWidth = trackWidthPx.toFloat() / count
                val direction = if (isRtl) -1f else 1f
                try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 按下瞬间即升镜 (2026-11-06 用户: 手按压瞬间浮起透射, 不等 slop,
                    // 落点是否在高亮块上也升 — 点其它段后滑块弹簧过去的全程戴镜片, 同 Toggle)。
                    pressing = true
                    val logicalX = if (isRtl) size.width - down.position.x else down.position.x
                    val start = thumbPosition
                    try {
                        // 只有从高亮块开始的横拖接管手势；普通段仍由 selectable 点击。
                        if (logicalX in start * segmentWidth..(start + 1f) * segmentWidth) {
                            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                                dragging = true
                                thumbPosition = (start + (change.position.x - down.position.x) * direction / segmentWidth)
                                    .coerceIn(0f, (count - 1).toFloat())
                            }
                            if (drag != null) {
                                val released = horizontalDrag(drag.id) { change ->
                                    thumbPosition = (thumbPosition + change.positionChange().x * direction / segmentWidth)
                                        .coerceIn(0f, (count - 1).toFloat())
                                    change.consume()
                                }
                                if (released) {
                                    val target = thumbPosition.roundToInt().coerceIn(0, count - 1)
                                    if (target != currentSelectedIndex) currentOnSelect(options[target].value)
                                }
                            }
                        } else {
                            // 非高亮落点 (点按其它段): 不消费事件 (selectable 负责点击), 但要等抬手,
                            // 让 pressing 跟住整根手指 — 滑块弹簧移向新段的全程都戴着升镜镜片。
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.none { it.pressed }) break
                            } while (true)
                        }
                    } finally {
                        // 即使调用方拒绝更新，仍从当前手势位置吸附回受控 selected。
                        gestureEnd++
                    }
                    pressing = false
                    dragging = false
                    // 抬手后升镜再保持 200ms (GlassSwitch tapGeneration 同款):
                    // 纯点按的下-上太短, 不加这段就看不见浮起透射。
                    tapGeneration++
                }
                } finally {
                    // 手势协程被取消 (pointerInput 重启 / 组合离开) 时不留悬空按压态,
                    // 否则镜片永远浮着 (GlassSwitch detectHorizontalDragGestures 同款护栏)。
                    pressing = false
                    dragging = false
                }
            }
    ) {
        val segW = if (count == 0) 0f else trackWidthPx.toFloat() / count
        val thumbStart = thumbPosition * segW
        val thumbEnd = thumbStart + segW

        Layout(
            content = {
                // 层0: thumb 色块 — 位置每帧由弹簧动画驱动
                // graphicsLayer{translationX} 承担亚像素余数: place 层取整丢掉的高刷帧间位移
                // 由 draw 层浮点补回, 120Hz 下连续非线性曲线, 不再有"一格一格"的整数跳变
                Box(
                    Modifier
                        .testTag("segment-thumb")
                        .fillMaxHeight()
                        .graphicsLayer {
                            val segWpx = trackWidthPx.toFloat() / count
                            val exact = thumbPosition * segWpx
                            translationX = (exact - exact.toInt()) * if (isRtl) -1f else 1f
                            // 浮起: 拖动中滑块放大 1.155 + 8dp 托影, 缩放围绕中心, 像从轨道里浮出来
                            // (对齐 Dock/GlassSwitch 按压升镜; 1.155*34dp=39.3dp 仍在 42dp 组件高度内,
                            //  轨道外层已不 clip, 越出轨道边界的投影可完整绘制)
                            val l = thumbLift
                            scaleX = 1f + 0.2f * l
                            scaleY = 1f + 0.2f * l
                            shape = RoundedCornerShape(12.dp)
                            clip = false
                            shadowElevation = if (l > 0f) 8.dp.toPx() * l else 0f
                        }
                        // 升镜三态 = GlassSwitch 完全同构 (2026-11-06 用户: "摁起来像 toggle 一样
                        // 变成一大块透明的玻璃, 有透射效果, 就像 Dock 的那一大块悬浮玻璃"):
                        // 静置实心 secondaryContainer; 按压瞬间固体淡到 0.5 保底 + 透射镜片盖顶
                        // → 读感是浮起一大块透明玻璃。
                        // 旧版 "镜片叠加而非替换、底色不淡" 的读感根因: tinted 面板 0.82 alpha
                        // 实心材质在 lift 时原样保留 → 按下去看到的还是实心药丸 = "没有变化"。
                        // 白雾教训 (2026-11-05 开关淡到 0 消失) 已由 0.5 保底堵住。
                        // 淡出只在玻璃风格生效: Classic 无镜片可盖, 淡半透明 = 直接变糊。
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            colors.secondaryContainer.copy(
                                alpha = if (glassActive) 1f - 0.5f * thumbLift else 1f
                            )
                        )
                        .then(if (glassActive && thumbLift > 0f && lensBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = lensBackdrop,
                            shape = { RoundedCornerShape(12.dp) },
                            effects = { liquidGlass(context = context, style = lensStyle) },
                        ) else Modifier)
                )
                // 层1..n: 点击段 — 透明, 只负责点击与无障碍
                options.forEachIndexed { index, option ->
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .selectable(
                                selected = index == selectedIndex,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (index != selectedIndex) onSelect(option.value) },
                            )
                            .then(
                                dbgTagPrefix?.let {
                                    Modifier.dbgTag("$it#$index", text = option.label, role = "button")
                                } ?: Modifier
                            )
                    )
                }
                // 层n+1..2n: 内容层(文字+可选徽标) — 每个字符按覆盖率 lerp 上色, 与 thumb 同帧
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = option.label,
                            onTextLayout = { result ->
                                // 字形只在文本/字重变化时重排, 缓存结果避免每帧重算字符矩形
                                charRectsCache[index] = result.charRects()
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = segmentLabelColor(
                                index = index,
                                charRects = charRectsCache[index],
                                thumbStartPx = thumbStart,
                                thumbEndPx = thumbEnd,
                                segmentWidthPx = segW,
                                selected = isSelected,
                                selectedColor = colors.onSecondaryContainer,
                                unselectedColor = colors.onSurfaceVariant
                            )
                        )
                        if (option.badge != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = option.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) colors.primary.copy(alpha = 0.14f)
                                        else colors.onSurfaceVariant.copy(alpha = 0.08f)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            },
            measurePolicy = { measurables, constraints ->
                val segmentWidth = if (count == 0) 0 else constraints.maxWidth / count
                val heightPx = constraints.maxHeight

                val thumbPlaceable = measurables[0].measure(
                    Constraints(
                        minWidth = segmentWidth,
                        maxWidth = segmentWidth,
                        minHeight = heightPx,
                        maxHeight = heightPx,
                    )
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
                    val lensRect = FlClashDockPhysics.lensRect(
                        position = thumbPosition,
                        stretch = 0f,
                        pitch = layoutSegW / density.density,
                        width = (layoutSegW / density.density),
                        height = 34f,
                        lift = thumbLift,
                    )
                    thumbPlaceable.placeRelative(
                        x = (thumbPosition * layoutSegW - (lensRect.width * density.density - thumbPlaceable.width) / 2f).toInt(),
                        y = ((heightPx - lensRect.height * density.density) / 2f).toInt(),
                    )

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
