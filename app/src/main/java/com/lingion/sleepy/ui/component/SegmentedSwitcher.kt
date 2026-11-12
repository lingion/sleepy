package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Animatable
import com.lingion.sleepy.ui.component.liquidglass.glassGestureOwnership
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.component.liquidglass.rememberGlassControlMotion
import com.lingion.sleepy.ui.component.liquidglass.GlassControlMotionSpec
import com.lingion.sleepy.ui.component.liquidglass.glassLensWidth
import com.lingion.sleepy.ui.component.liquidglass.glassLensHeight
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassDepth
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassOverlay
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassOverlaySamplingHost
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassStyle
import com.lingion.sleepy.ui.component.liquidglass.liquidGlass
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassHaptics
import androidx.compose.ui.zIndex
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
    val selectionColors = colors.selectionColors()
    val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val count = options.size
    if (count == 0) return

    val glassHost = LocalGlassHost.current
    val glassActive = LocalUiStyle.current == UiStyle.LiquidGlass && glassHost != null
    val thumbX = remember { Animatable(selectedIndex.toFloat()) }
    var classicPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var gesturePosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var pressing by remember { mutableStateOf(false) }
    var tapGeneration by remember { mutableIntStateOf(0) }
    val motion = rememberGlassControlMotion(
        if (dragging) gesturePosition else selectedIndex.toFloat(), pressing || dragging,
        dragging, tapGeneration, glassActive,
        spec = GlassControlMotionSpec.NativeSegmented,
    )
    val thumbPosition = if (glassActive) motion.position.value else classicPosition
    val thumbLift = if (glassActive) motion.lift else 0f
    val geometryLift = if (glassActive) motion.geometryLift else 0f
    val thumbStretch = if (glassActive) motion.stretch * .5f else 0f
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(selectedIndex, glassActive) {
        if (!glassActive) {
            thumbX.animateTo(selectedIndex.toFloat(), SleepyThumbSpring) { classicPosition = value }
        }
    }

    // 每段文字的字符矩形缓存(非 state: 字色由 thumbPosition 逐帧驱动, 缓存只是普通字典,
    // 避免 onTextLayout 每次重组都重新分配列表 — 上一版每次着色都 charRects() 全量重算 = "卡卡的")
    val charRectsCache = remember { mutableMapOf<Int, List<Rect>>() }

    // 轨道像素宽(padding 后的可用宽) — thumb 像素位置与字符覆盖率共同的真值
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableIntStateOf(0) }

    val samplingHost = if (LocalGlassOverlay.current) LocalGlassOverlaySamplingHost.current ?: glassHost else glassHost
    val scene = if (LocalGlassOverlay.current) samplingHost?.overlayBackdrop else samplingHost?.backdropAt(LocalGlassDepth.current)
    // Only the ordinary sibling backing is recorded. It never contains this lens.
    val trackBackdrop = rememberLayerBackdrop()
    // Native UISegmentLabel keeps its 1× transform while the lens locally refracts
    // its edges. Record unscaled glyphs, keeping one visible/accessibility tree.
    val labelBackdrop = rememberLayerBackdrop()
    val lensBackdrop = if (scene != null) rememberCombinedBackdrop(scene, trackBackdrop) else trackBackdrop
    val context = LocalContext.current
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    val lensStyle = LiquidGlassStyle.controlLens(colors, dark).scale(thumbLift.toDouble())
    val contentStyle = LiquidGlassStyle.controlLensContent(colors, dark).scale(thumbLift.toDouble())
    val trackShape = if (glassActive) RoundedCornerShape(50) else RoundedCornerShape(14.dp)
    val restingShape = if (glassActive) RoundedCornerShape(50) else RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(42.dp)
            .zIndex(if (glassActive && thumbLift > 0f) 1f else 0f)
            .onGloballyPositioned { coords ->
                // coords 是含 padding 的外区; 轨道宽 = 外区宽 - 左右 4dp padding
                trackWidthPx = (coords.size.width - with(density) { 8.dp.toPx() }).toInt().coerceAtLeast(0)
            }
            .selectableGroup()
            .glassGestureOwnership(count > 1)
            .pointerInput(options, isRtl, trackWidthPx, glassActive) {
                // Classic 模式完全走原有 selectable 点击路径，不安装自定义拖动接管器。
                if (!glassActive) return@pointerInput
                if (count < 2 || trackWidthPx <= 0) return@pointerInput
                val segmentWidth = trackWidthPx.toFloat() / count
                val direction = if (isRtl) -1f else 1f
                try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    // 按下瞬间即升镜 (2026-11-06 用户: 手按压瞬间浮起透射, 不等 slop,
                    // 落点是否在高亮块上也升 — 点其它段后滑块弹簧过去的全程戴镜片, 同 Toggle)。
                    pressing = true
                    // Input follows the current selection, even while its lens is travelling.
                    val start = currentSelectedIndex.toFloat()
                    gesturePosition = start
                    var lastHapticIndex = gesturePosition.roundToInt()
                    fun tickIfChanged() {
                        val index = gesturePosition.roundToInt()
                        if (index != lastHapticIndex) {
                            LiquidGlassHaptics.selectionClick(view)
                            lastHapticIndex = index
                        }
                    }
                    LiquidGlassHaptics.selectionClick(view)
                    val logicalX = (if (isRtl) size.width - down.position.x else down.position.x) - 4.dp.toPx()
                    try {
                        val onThumb = logicalX in start * segmentWidth..(start + 1f) * segmentWidth
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                if (pointer.isConsumed || (!onThumb && moved)) break
                                pointer.consume()
                                val target = if (dragging) gesturePosition.roundToInt()
                                    else (logicalX / segmentWidth).toInt()
                                val index = target.coerceIn(0, count - 1)
                                if (index != currentSelectedIndex) currentOnSelect(options[index].value)
                                break
                            }
                            val dx = pointer.position.x - down.position.x
                            if ((pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (onThumb && kotlin.math.abs(dx) > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                gesturePosition = (start + dx * direction / segmentWidth)
                                    .coerceIn(0f, (count - 1).toFloat())
                                tickIfChanged()
                            }
                            pointer.consume()
                        }
                    } finally {
                        pressing = false
                        dragging = false
                    }
                    // Native segmented lenses can contract before reaching held size.
                    tapGeneration++
                }
                } finally {
                    // pointerInput 重启或离开组合时也必须收回镜片。
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
                Box(
                    Modifier.testTag("segment-thumb")
                        .graphicsLayer {
                            shape = RoundedCornerShape(50)
                            clip = false
                        }
                        .then(if (glassActive && thumbLift > 0f) Modifier.drawBackdrop(
                            backdrop = lensBackdrop,
                            shape = { RoundedCornerShape(50) },
                            effects = { liquidGlass(context = context, style = lensStyle) },
                            highlight = null,
                            shadow = { LiquidGlassStyle.controlLensShadow(dark, thumbLift) },
                        ) else Modifier)
                )
                Layout(content = {
                    // Track source contains only the ordinary platter, never glyphs or lenses.
                    Layout(
                        modifier = Modifier.layerBackdrop(trackBackdrop)
                            .background(containerColor ?: if (glassActive) colors.onSurface.copy(alpha = .045f) else colors.surfaceContainer, trackShape)
                            .padding(4.dp),
                        content = {
                            Box(Modifier.background(selectionColors.selectedContainer
                                .copy(alpha = if (glassActive) 1f - thumbLift else 1f), restingShape))
                        },
                    ) { children, constraints ->
                        val pitch = constraints.maxWidth.toFloat() / count
                        val pill = children.single().measure(Constraints.fixed(pitch.toInt(), constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val x = thumbPosition * pitch
                            pill.placeRelativeWithLayer(x.toInt(), 0) {
                                translationX = (x - x.toInt()) * if (isRtl) -1f else 1f
                            }
                        }
                    }
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
                    Layout(
                        modifier = Modifier.drawWithContent {
                            if (glassActive && thumbLift > 0f) {
                                val padding = 4.dp.toPx()
                                val pitch = (size.width - 2 * padding) / count
                                val width = glassLensWidth(pitch + 24.dp.toPx() * geometryLift, thumbStretch).roundToInt().toFloat()
                                val height = glassLensHeight(size.height - 2 * padding + 16.dp.toPx() * geometryLift, thumbStretch,
                                    minimum = size.height - 2 * padding + 14.dp.toPx() * geometryLift).roundToInt().toFloat()
                                val logical = padding + thumbPosition * pitch + (pitch - width) / 2
                                val left = if (isRtl) size.width - logical - width else logical
                                val top = (size.height - height) / 2
                                val outline = Path().apply {
                                    addRoundRect(RoundRect(Rect(left, top, left + width, top + height), CornerRadius(height / 2)))
                                }
                                // This clip affects only the visible replay. The sibling source
                                // records full glyphs on its own canvas for the content lens.
                                clipPath(outline, ClipOp.Difference) { this@drawWithContent.drawContent() }
                            } else drawContent()
                        }.layerBackdrop(labelBackdrop).padding(4.dp),
                        content = {
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
                                        selectedColor = selectionColors.selectedContent,
                                        unselectedColor = selectionColors.unselectedContent
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
                                        color = if (isSelected) selectionColors.accent else selectionColors.unselectedContent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) selectionColors.accent.copy(alpha = 0.14f)
                                                else selectionColors.unselectedContent.copy(alpha = 0.08f)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        },
                    ) { labels, constraints ->
                        val pitch = constraints.maxWidth / count
                        val height = constraints.maxHeight
                        val placeables = labels.map { it.measure(Constraints(maxWidth = pitch, maxHeight = height)) }
                        layout(constraints.maxWidth, height) {
                            placeables.forEachIndexed { index, label ->
                                label.placeRelative(index * pitch + (pitch - label.width) / 2, (height - label.height) / 2)
                            }
                        }
                    }
                }) { children, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val padding = with(density) { 4.dp.roundToPx() }
                    val pitch = (width - padding * 2) / count
                    val track = children[0].measure(Constraints.fixed(width, height))
                    val clicks = children.drop(1).take(count).map { it.measure(Constraints.fixed(pitch, height - padding * 2)) }
                    val labels = children.last().measure(Constraints.fixed(width, height))
                    layout(width, height) {
                        track.placeRelative(0, 0)
                        clicks.forEachIndexed { index, click -> click.placeRelative(padding + index * pitch, padding) }
                        labels.placeRelative(0, 0)
                    }
                }
                Box(Modifier.then(if (glassActive && thumbLift > 0f) Modifier.drawBackdrop(
                    backdrop = labelBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = { liquidGlass(context = context, style = contentStyle) },
                    highlight = null,
                    shadow = null,
                ) else Modifier))
            },
            measurePolicy = { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val backing = measurables[1].measure(Constraints.fixed(width, height))
                val padding = with(density) { 4.dp.toPx() }
                val segmentWidth = (width - 2 * padding) / count
                val lift = geometryLift
                val lensWidth = glassLensWidth(segmentWidth + with(density) { 24.dp.toPx() } * lift, thumbStretch).roundToInt()
                val lensHeight = glassLensHeight(height - 2 * padding + with(density) { 16.dp.toPx() } * lift, thumbStretch,
                    minimum = height - 2 * padding + with(density) { 14.dp.toPx() } * lift).roundToInt()
                val lens = measurables[0].measure(Constraints.fixed(lensWidth, lensHeight))
                val contentLens = measurables[2].measure(Constraints.fixed(lensWidth, lensHeight))
                layout(width, height) {
                    backing.placeRelative(0, 0)
                    val exactX = padding + thumbPosition * segmentWidth + (segmentWidth - lensWidth) / 2f
                    lens.placeRelativeWithLayer(exactX.toInt(), (height - lensHeight) / 2) {
                        translationX = (exactX - exactX.toInt()) * if (isRtl) -1f else 1f
                    }
                    contentLens.placeRelativeWithLayer(exactX.toInt(), (height - lensHeight) / 2) {
                        translationX = (exactX - exactX.toInt()) * if (isRtl) -1f else 1f
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
