package com.lingion.sleepy.ui.component.liquidglass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.ui.component.NavIconDriver
import com.lingion.sleepy.ui.component.NavIconDriverEffects
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.component.NavPartIcon
import com.lingion.sleepy.ui.component.rememberNavIconDrivers
import com.lingion.sleepy.ui.component.selectionColors
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import material.Hct
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/** Actual painted capsule bounds; the lens carrier itself spans the complete Dock. */
internal val DockLensBoundsInRootPx = SemanticsPropertyKey<Rect>("DockLensBoundsInRootPx")
internal val DockMaterialDark = SemanticsPropertyKey<Boolean>("DockMaterialDark")
internal val DockBackdropLuma = SemanticsPropertyKey<Float>("DockBackdropLuma")
internal val DockOrdinaryTint = SemanticsPropertyKey<Color>("DockOrdinaryTint")
/** Experimental until retained-source notifications and native optical calibration are complete. */
internal val LocalDockBackdropAppearanceEnabled = androidx.compose.runtime.compositionLocalOf { false }
internal val LocalDockLumaObserver = androidx.compose.runtime.compositionLocalOf<((BackdropLumaSample) -> Unit)?> { null }

/**
 * Production four-tab sizing uses the SwiftUI TabView parent reference, whose
 * 375pt holder has a 333pt platter and an 88.25pt resting lens (iOS 26.5,
 * swiftui-dock-parent-61). The independent UITabBar's 94pt lens is a different
 * intrinsic layout. Other counts retain that existing policy until measured.
 *
 * Only the four-tab minimum is calibrated here. Available content span still
 * determines pitch, and a wider intrinsic seat can grow the lens. Values at
 * other widths are Android's responsive policy, not measured UIKit geometry.
 */
internal fun tabViewDockLayout(span: Float, count: Int): FlClashDockPhysics.DockLayout {
    if (count != 4) return FlClashDockPhysics.dockLayout(span, count)
    val measuredFourTabLensWidth = 88.25f
    val padding = FlClashDockPhysics.BAR_PADDING
    val naturalPitch = (span - padding * 2f) / count
    val lens = max(measuredFourTabLensWidth, naturalPitch + padding * 2f).coerceAtMost(span)
    val pitch = (span - lens) / (count - 1)
    return FlClashDockPhysics.DockLayout(pitch = pitch, lens = lens, inset = (lens - pitch) / 2f)
}

private class DockInputState(var selected: Int)

/**
 * 悬浮导航栏。镜片起落与拖动形变按 iOS 26.5 原生控件的记录校准，
 * 选中色复用 Material 模式。光学着色器及低版本回退仍是近似绘制。
 */
@Composable
fun LiquidGlassNavigationBar(
    items: List<PillNavItemSpec>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    if (items.isEmpty()) return

    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val colorScheme = MaterialTheme.colorScheme
    val selectionColors = colorScheme.selectionColors()
    val platterShape = remember { DockPlatterShape() }
    // 主题实时订阅: LocalDarkTheme 用 compositionLocalOf 提供(见 Theme.kt),
    // 组合期直读即注册依赖; derivedStateOf 把 dark 收敛成单一快照状态,
    // 供着色器 uniform / 底盘色 / 阴影强度等价消费,
    // 会话内切深浅色时立即刷新, 不再定格在初始化值直到重启。
    val isAppDark = LocalDarkTheme.current
    val dark by remember(isAppDark, colorScheme) {
        derivedStateOf { isAppDark || colorScheme.surface.luminance() < 0.5f }
    }
    // Native 26.5 retains different appearances for the same gray on the rise
    // and return paths. These midpoint thresholds and five source taps are an
    // explicit engineering candidate; they are not measured UIKit constants.
    val backdropAppearanceEnabled = LocalDockBackdropAppearanceEnabled.current
    val appearance = remember(backdrop, dark, colorScheme, backdropAppearanceEnabled) {
        BackdropAppearanceHysteresis(dark, 223.5f / 255f, 153.5f / 255f)
    }
    var sampledLuma by remember(backdrop, dark, colorScheme, backdropAppearanceEnabled) { mutableStateOf<Float?>(null) }
    var materialDark by remember(backdrop, dark, colorScheme, backdropAppearanceEnabled) { mutableStateOf(dark) }
    val ordinaryTint = if (sampledLuma == null) selectionColors.unselectedContent
        else if (materialDark) Color.White else Color.Black
    val lumaObserver by rememberUpdatedState(LocalDockLumaObserver.current)
    // Retain only the actual sibling backing. The raised lens must include
    // its haze, continuous corners, border and fading selection, not a second
    // approximation of the platter. Glyphs and the lens are separate siblings.
    val platterBackdrop = rememberLayerBackdrop()
    val lensBackdrop = if (backdrop != null) rememberCombinedBackdrop(backdrop, platterBackdrop) else platterBackdrop

    val count = items.size
    val lastIndex = max(0, count - 1)
    val clampedSelected = selectedIndex.coerceIn(0, lastIndex)

    // ── 动力学弹簧状态机 ──
    val motion = remember { GlassDockMotion(clampedSelected.toFloat()) }
    val inputTimeNanos by rememberUpdatedState(LocalDockInputTimeNanos.current)
    val lensSpring = motion.position
    val liftSpring = motion.lift
    val swellSpring = motion.swell
    val stretchSpring = motion.pull
    val jellySpring = motion.jelly
    val glowSpring = motion.glow
    val glowSpreadSpring = motion.glowSpread

    val glowAt = motion.glowAt
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var pressX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var pointerId by remember { mutableStateOf<Long?>(null) }
    var pointerEpoch by remember { mutableLongStateOf(0L) }
    var selectionRequest by remember { mutableIntStateOf(0) }

    // 图标专属动效驱动表(NavIconMotion.kt): bar 层一份, 透镜内外双遍 DestinationsRow
    // 共享同一实例 → 两圈相位逐帧一致, 透镜像素不撕裂。pressed = 手指按住 bar 且
    // 透镜正落在本格(lensSpring.value 为连续座位坐标, 半格内算覆盖)。
    val iconDrivers = rememberNavIconDrivers(items, clampedSelected)
    NavIconDriverEffects(iconDrivers, clampedSelected) { i ->
        pressedIndex != null && abs(lensSpring.value - i) < 0.5f
    }

    // 外部 selectedIndex 改变时, 驱动镜头滑向目标
    LaunchedEffect(clampedSelected, pressedIndex, selectionRequest) {
        if (pressedIndex == null && motion.requestedPosition.roundToInt() != clampedSelected) {
            motion.select(clampedSelected.toFloat(), inputTimeNanos())
        }
    }

    LaunchedEffect(motion) {
        val durationScale = coroutineContext[MotionDurationScale]
        runTimestampedActiveFrameLoop(
            needsFrame = { motion.needsFrame(durationScale?.scaleFactor ?: 1f) },
        ) { frame ->
            motion.advanceFrame(frame, durationScale?.scaleFactor ?: 1f)
        }
    }

    // ── 尺寸与排版计算 ──
    val barHeightDp = FlClashDockPhysics.BAR_HEIGHT.dp
    val barPaddingDp = FlClashDockPhysics.BAR_PADDING.dp
    val maxExtentDp = FlClashDockPhysics.MAX_ITEM_EXTENT.dp
    val edgeMarginDp = FlClashDockPhysics.EDGE_MARGIN.dp
    val totalWidthModifier = Modifier
        .padding(horizontal = edgeMarginDp)
        .widthIn(max = maxExtentDp * count + barPaddingDp * 4)
        .fillMaxWidth()

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    var inputCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lensCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isLtr = layoutDirection == LayoutDirection.Ltr

    val layout = remember(barWidthPx, density.density, count) {
        val spanDp = (barWidthPx / density.density) - FlClashDockPhysics.BAR_PADDING * 2f
        tabViewDockLayout(max(1f, spanDp), count)
    }

    val inputState = remember { DockInputState(clampedSelected) }
    SideEffect { inputState.selected = clampedSelected }
    SideEffect { motion.distancePerPosition = if (isLtr) layout.pitch else -layout.pitch }
    val currentSelected by rememberUpdatedState(clampedSelected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentLayout by rememberUpdatedState(layout)
    val currentDensity by rememberUpdatedState(density.density)
    val currentIsLtr by rememberUpdatedState(isLtr)
    val currentLastIndex by rememberUpdatedState(lastIndex)

    // Rows place logical indices according to layout direction. Canvas geometry
    // uses physical left-to-right coordinates, so mirror it exactly once here.
    fun paintedLensRect(lift: Float): Rect = FlClashDockPhysics.lensRect(
        position = if (currentIsLtr) lensSpring.value else currentLastIndex - lensSpring.value,
        stretch = jellySpring.value,
        stretchY = motion.stretchY,
        pitch = currentLayout.pitch,
        width = currentLayout.lens,
        height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
        lift = lift,
    )

    fun clearPointerState() {
        pointerId = null
        pressedIndex = null
        dragging = false
    }

    fun cancelInteraction(receivedNanos: Long = inputTimeNanos(), sampleUptimeMillis: Long? = null) {
        clearPointerState()
        motion.cancel(currentSelected.toFloat(), receivedNanos, sampleUptimeMillis)
    }

    fun commitDestination(index: Int) {
        if (index != inputState.selected) {
            inputState.selected = index
            selectionRequest++
            currentOnSelect(index)
        }
    }

    fun activateDestination(index: Int) {
        // A semantic/key action supersedes an unfinished pointer preview, while
        // committing through the same controlled-selection guard as touch UP.
        clearPointerState()
        motion.select(index.toFloat(), inputTimeNanos())
        commitDestination(index)
    }

    fun positionAt(xPx: Float): Float {
        val xDp = xPx / currentDensity
        val barWidthDp = barWidthPx / currentDensity
        val dxDp = if (currentIsLtr) xDp else (barWidthDp - xDp)
        val pos = (dxDp - FlClashDockPhysics.BAR_PADDING - currentLayout.inset) / currentLayout.pitch - 0.5f
        return nativeDockDragPosition(pos, currentLastIndex + 1, currentLayout.pitch)
    }

    fun indexAt(position: Float): Int = position.roundToInt().coerceIn(0, currentLastIndex)

    fun physicalFingerXDp(position: Offset): Float? {
        if (count != 4) return null
        val coordinates = inputCoordinates ?: return null
        if (!coordinates.isAttached) return null
        // This owner is outside the moving parent layer. Window coordinates
        // retain physical +X in RTL and include static caller transforms.
        return (coordinates.localToWindow(position).x / currentDensity).takeIf { it.isFinite() }
    }

    val velocityTracker = remember { VelocityTracker() }

    // 触控径向光晕模型
    val touchGlow by remember(glowAt) {
        derivedStateOf {
            val glowVal = glowSpring.value
            if (glowVal <= 0.001f) {
                LiquidGlassGlow.None
            } else {
                val spreadVal = glowSpreadSpring.value
                LiquidGlassGlow(
                    center = glowAt,
                    reach = (FlClashDockPhysics.GLOW_REACH * (1f + FlClashDockPhysics.GLOW_GROWTH * spreadVal)).toDouble(),
                    strength = (FlClashDockPhysics.GLOW_PEAK * glowVal * glowVal).toDouble(),
                    wash = FlClashDockPhysics.GLOW_WASH.toDouble(),
                )
            }
        }
    }

    // 阴影强度 (Dark: 3x, Light: 1x)
    val shadowStrength = if (dark) 3.0f else 1.0f

    // Match the Material navigation selection before it lifts into a lens.
    val restingPlatterColor = selectionColors.selectedContainer

    val lensBaseWidthDp = layout.lens.dp
    val lensBaseHeightDp = (FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f).dp
    val lensPath = remember { Path() }
    val shadowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }
    val lensShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LiquidGlassShader.shaderSource(context))
            } catch (_: Throwable) {
                null
            }
        } else null
    }
    val contentShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LiquidGlassShader.shaderSource(context))
            } catch (_: Throwable) {
                null
            }
        } else null
    }
    fun computeInsideTransform(index: Int): DestinationTransform {
        val lift = liftSpring.value
        val centerMag = 1f + FlClashDockPhysics.LENS_MAGNIFY * lift
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && contentShader != null) {
            return DestinationTransform(scaleX = centerMag, scaleY = centerMag)
        }

        // 老设备无硬件着色器时: 中心标准 1.16x 纯光学放大，边缘过渡区辅以自然的非线性微曲面拉伸 (edgeStretch)，
        // 保持图标清晰不走形，同时与 FLClash 凸透镜物理质感对齐
        val currentPos = lensSpring.value
        val delta = index.toFloat() - currentPos
        val absDelta = abs(delta)
        val halfLensTabs = if (currentLayout.pitch > 0.001f) (currentLayout.lens / currentLayout.pitch) * 0.5f else 0.55f
        val edgeFactor = if (absDelta <= halfLensTabs) {
            ((absDelta - 0.25f * halfLensTabs) / (0.75f * halfLensTabs)).coerceIn(0f, 1f)
        } else 0f
        val edgeStretch = edgeFactor * edgeFactor * 0.05f * lift

        return DestinationTransform(
            scaleX = centerMag + edgeStretch,
            scaleY = centerMag,
        )
    }

    val shadowRoomDp = 12.dp

    Box(
        modifier = modifier
            // A caller's fillMaxWidth/fixed width must not force the capsule past
            // its native intrinsic extent. Keep the available width as a maximum.
            .wrapContentWidth()
            .then(totalWidthModifier)
            .height(barHeightDp + shadowRoomDp * 2)
            .onGloballyPositioned { coords ->
                barWidthPx = coords.size.width.toFloat()
                barHeightPx = coords.size.height.toFloat()
                inputCoordinates = coords
            }
            .glassGestureOwnership()
            .pointerInput(count, isLtr, density.density) {
                try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        // Same monotonic clock as withFrameNanos; event sample
                        // uptime can predate actual delivery and is kept separately.
                        val receivedNanos = inputTimeNanos()

                        when (event.type) {
                            PointerEventType.Press -> {
                                pointerId = change.id.value
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val pos = positionAt(change.position.x)
                                val idx = indexAt(pos)

                                pressedIndex = idx
                                pressX = change.position.x
                                dragging = false
                                pointerEpoch = motion.press(idx.toFloat(), receivedNanos, change.uptimeMillis,
                                    change.position, physicalFingerXDp(change.position))
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                if (pointerId == change.id.value) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                                    val dx = change.position.x - pressX
                                    if (!dragging && abs(dx) > 4f) {
                                        dragging = true
                                    }

                                    if (dragging) {
                                        val xDp = change.position.x / currentDensity
                                        val barWidthDp = barWidthPx / currentDensity
                                        val dxDp = if (currentIsLtr) xDp else (barWidthDp - xDp)
                                        val rawPos = (dxDp - FlClashDockPhysics.BAR_PADDING - currentLayout.inset) / currentLayout.pitch - 0.5f

                                        // 当滑块拖到最左端或最右端超过边界时，计算拉出边界的物理像素距离
                                        val overshootPx = when {
                                            rawPos < 0f -> rawPos * currentLayout.pitch * currentDensity
                                            rawPos > currentLastIndex -> (rawPos - currentLastIndex) * currentLayout.pitch * currentDensity
                                            else -> 0f
                                        }
                                        val pullLimit = barHeightPx * FlClashDockPhysics.PULL_LIMIT
                                        val physicalOvershootPx = overshootPx * if (currentIsLtr) 1f else -1f
                                        val targetStretch = FlClashDockPhysics.rubberBand(physicalOvershootPx, pullLimit)
                                        val pos = positionAt(change.position.x)
                                        // The Dock follows the measured native center trajectory;
                                        // other controls retain their own tracking springs.
                                        motion.drag(pointerEpoch, pos, targetStretch, receivedNanos, change.uptimeMillis,
                                            change.position, physicalFingerXDp(change.position))

                                        val idx = indexAt(pos)
                                        if (idx != pressedIndex) {
                                            LiquidGlassHaptics.selectionClick(view)
                                            pressedIndex = idx
                                        }
                                    } else {
                                        motion.trackGlow(pointerEpoch, change.position, receivedNanos, change.uptimeMillis,
                                            physicalFingerXDp(change.position))
                                    }
                                    change.consume()
                                }
                            }
                            PointerEventType.Release -> {
                                if (pointerId == change.id.value) {
                                    // Compose represents pointer cancellation as an already
                                    // consumed release. It must never commit a destination.
                                    if (change.isConsumed) {
                                        cancelInteraction(receivedNanos, change.uptimeMillis)
                                        continue
                                    }
                                    pointerId = null
                                    val velocityXDp = velocityTracker.calculateVelocity().x / currentDensity

                                    var targetIndex = pressedIndex ?: inputState.selected
                                    if (dragging) {
                                        val direction = if (currentIsLtr) 1f else -1f
                                        val lead = (velocityXDp / currentLayout.pitch) * direction * FlClashDockPhysics.FLING_PROJECTION_SEC.toFloat()
                                        val projected = (motion.requestedPosition + lead).roundToInt().coerceIn(targetIndex - 1, targetIndex + 1).coerceIn(0, currentLastIndex)
                                        if (projected != targetIndex) {
                                            LiquidGlassHaptics.selectionClick(view)
                                            targetIndex = projected
                                        }
                                    }

                                    pressedIndex = null
                                    dragging = false
                                    motion.release(pointerEpoch, targetIndex.toFloat(), receivedNanos, change.uptimeMillis)

                                    commitDestination(targetIndex)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                } finally {
                    cancelInteraction()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Whole moving carrier, including shadow room; these bounds are not
        // the visible plume/contour. Every painted sibling shares this layer.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeightDp + shadowRoomDp * 2)
                .graphicsLayer {
                    val pressGrowth = FlClashDockPhysics.pressGrowthOf(size.width / density.density)
                    val swellScale = 1f + swellSpring.value * pressGrowth
                    val stretchVal = stretchSpring.value
                    scaleX = swellScale * (1f + abs(stretchVal) / max(1f, size.width) * FlClashDockPhysics.PULL_STRETCH)
                    scaleY = swellScale
                    if (count == 4) {
                        val coordinates = inputCoordinates
                        val physicalX = motion.parentTranslation.value * density.density
                        val localDelta = if (coordinates != null && coordinates.isAttached && physicalX.isFinite()) {
                            val origin = coordinates.localToWindow(Offset.Zero)
                            coordinates.windowToLocal(origin + Offset(physicalX, 0f)) - coordinates.windowToLocal(origin)
                        } else Offset.Zero
                        // Translation is a window-space vector. Undo caller
                        // transforms once; the press scale above is unchanged.
                        translationX = localDelta.x
                        translationY = localDelta.y
                    } else {
                        translationX = stretchVal
                        translationY = 0f
                    }
                }
                .testTag("dock-parent"),
            contentAlignment = Alignment.Center,
        ) {
            // ── 层1: Dock 胶囊底座 (62dp) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp)
                    .testTag("dock-material")
                    .semantics {
                        this[DockMaterialDark] = materialDark
                        this[DockOrdinaryTint] = ordinaryTint
                        sampledLuma?.let { this[DockBackdropLuma] = it }
                    }
                    .then(if (backdropAppearanceEnabled && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Modifier.sampleBackdropLuma(
                            backdrop = backdrop,
                            transparentBackground = colorScheme.background.copy(alpha = 1f),
                            sampleBoundsPx = { size -> Rect(Offset.Zero, size) },
                            onSample = { sample ->
                                sampledLuma = sample.meanLuma
                                materialDark = appearance.accept(sample.meanLuma)
                                lumaObserver?.invoke(sample)
                            },
                        )
                    } else Modifier)
                    .layerBackdrop(platterBackdrop)
                    .shadow(
                        elevation = (12 * shadowStrength).dp,
                        shape = platterShape,
                        clip = false,
                        ambientColor = colorScheme.scrim.copy(alpha = (if (dark) 0.25f else 0.16f) * shadowStrength),
                        spotColor = colorScheme.scrim.copy(alpha = (if (dark) 0.35f else 0.22f) * shadowStrength),
                    )
                    .background(
                        color = colorScheme.surfaceContainer.copy(alpha = if (dark) 0.72f else 0.88f),
                        shape = platterShape,
                    )
                    .border(
                        width = 1.dp,
                        color = if (dark) Color.White.copy(alpha = 0.12f) else Color(0x1F000000),
                        shape = platterShape,
                    )
                    .then(
                        if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { platterShape },
                                effects = {
                                    val opticalProfile = platterShape.opticalProfile(size, layoutDirection, this)
                                    liquidGlass(
                                        context = context,
                                        style = LiquidGlassStyle.pill(colorScheme, materialDark),
                                        glow = touchGlow,
                                        outline = opticalProfile?.path,
                                        cachedOutlineProfile = opticalProfile,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(barPaddingDp),
            ) {
                // 沉底底盘: 当 lift < 1 时沉在底部的静止色块
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val lift = liftSpring.value
                            if (lift < 1f) {
                                val lensRect = paintedLensRect(lift)
                                val leftPx = (layout.inset + lensRect.left) * density.density
                                val topPx = lensRect.top * density.density
                                val widthPx = lensRect.width * density.density
                                val heightPx = lensRect.height * density.density
                                val radiusPx = heightPx / 2f
                                drawRoundRect(
                                    color = restingPlatterColor.copy(alpha = restingPlatterColor.alpha * (1f - lift.coerceIn(0f, 1f))),
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                )
                            }
                        },
                )
            }

            // ── 层2: 外部文字与图标 (Outside Destinations - onSurfaceVariant) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp + shadowRoomDp * 2)
                    .drawWithContent {
                        val lift = liftSpring.value
                        val lensRect = paintedLensRect(lift)
                        val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                        val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                        val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                        val topPx = dockContentTopPx + lensRect.top * density.density
                        val widthPx = lensRect.width * density.density
                        val heightPx = lensRect.height * density.density
                        val radiusPx = heightPx / 2f

                        lensPath.rewind()
                        lensPath.addRoundRect(
                            RoundRect(
                                rect = Rect(leftPx, topPx, leftPx + widthPx, topPx + heightPx),
                                cornerRadius = CornerRadius(radiusPx, radiusPx),
                            ),
                        )
                        clipPath(lensPath, clipOp = ClipOp.Difference) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeightDp)
                        .padding(barPaddingDp),
                ) {
                    DestinationsRow(
                        items = items,
                        layout = layout,
                        tint = ordinaryTint,
                        badgeTint = selectionColors.accent,
                        iconDrivers = iconDrivers,
                        selectedIndex = clampedSelected,
                        onActivate = ::activateDestination,
                    )
                }
            }

            // ── 层3: 浮空凸透镜 (70dp 独立上层图层，运行 1:1 liquid_glass.agsl lens 折射着色器) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp + shadowRoomDp * 2)
                    .onGloballyPositioned { lensCoordinates = it }
                    .testTag("dock-lens")
                    .semantics {
                        // This carrier must stay bar-sized for uncut optical
                        // sampling. Export the painted rect instead of claiming
                        // its semantics-node bounds describe the capsule.
                        val coordinates = lensCoordinates
                        // Observe the parent's transform springs here as well;
                        // localToRoot includes their current graphics layer.
                        val parentTransformIsFinite = swellSpring.value.isFinite() && stretchSpring.value.isFinite() &&
                            motion.parentTranslation.value.isFinite()
                        if (coordinates != null && coordinates.isAttached && parentTransformIsFinite) {
                            val rect = paintedLensRect(liftSpring.value)
                            val left = (FlClashDockPhysics.BAR_PADDING + layout.inset + rect.left) * density.density
                            val top = (shadowRoomDp.value + FlClashDockPhysics.BAR_PADDING + rect.top) * density.density
                            val first = coordinates.localToRoot(Offset(left, top))
                            val last = coordinates.localToRoot(Offset(left + rect.width * density.density,
                                top + rect.height * density.density))
                            this[DockLensBoundsInRootPx] = Rect(minOf(first.x, last.x), minOf(first.y, last.y),
                                maxOf(first.x, last.x), maxOf(first.y, last.y))
                        }
                    }
                    .drawBehind {
                        // 1. FLClash 立体下沉投影: offset (0, 8.dp * lift), blur 8.dp
                        val lift = liftSpring.value
                        if (lift > 0.001f) {
                            val lensRect = paintedLensRect(lift)
                            val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                            val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                            val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                            val topPx = dockContentTopPx + lensRect.top * density.density
                            val widthPx = lensRect.width * density.density
                            val heightPx = lensRect.height * density.density
                            val radiusPx = heightPx / 2f

                            val shadowAlpha = (if (dark) 0.35f else 0.18f) * lift.coerceIn(0f, 1f)
                            if (shadowAlpha > 0.001f) {
                                shadowPaint.color = android.graphics.Color.argb((shadowAlpha * 255).toInt(), 0, 0, 0)
                                shadowPaint.maskFilter = android.graphics.BlurMaskFilter(
                                    8f * density.density,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                                )
                                val shadowOffsetY = 8f * density.density * lift
                                drawIntoCanvas { canvas ->
                                    val rectF = android.graphics.RectF(
                                        leftPx,
                                        topPx + shadowOffsetY,
                                        leftPx + widthPx,
                                        topPx + heightPx + shadowOffsetY,
                                    )
                                    canvas.nativeCanvas.drawRoundRect(rectF, radiusPx, radiusPx, shadowPaint)
                                }
                            }
                        }
                    }
                    .drawWithContent {
                        // 3. 外部裁切：将着色器渲染后的光学生效画面精确裁切至透镜胶囊内
                        val lift = liftSpring.value
                        if (lift > 0.001f) {
                            val lensRect = paintedLensRect(lift)
                            val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                            val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                            val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                            val topPx = dockContentTopPx + lensRect.top * density.density
                            val widthPx = lensRect.width * density.density
                            val heightPx = lensRect.height * density.density
                            val radiusPx = heightPx / 2f

                            lensPath.rewind()
                            lensPath.addRoundRect(
                                RoundRect(
                                    rect = Rect(leftPx, topPx, leftPx + widthPx, topPx + heightPx),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                ),
                            )
                            clipPath(lensPath) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                    .graphicsLayer {
                        // 2. 着色器滤镜处理层：接收未裁切的底图作为 uTexture 输入，执行真实光学曲率弯折与色散
                        val lift = liftSpring.value
                        if (lensShader != null && lift > 0.001f && size.width > 0f && size.height > 0f) {
                            val lensRect = paintedLensRect(lift)
                            val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                            val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                            val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                            val topPx = dockContentTopPx + lensRect.top * density.density

                            val style = LiquidGlassStyle.lens(colorScheme, materialDark).scale(lift.toDouble())
                            val sizeDp = Size(lensRect.width, lensRect.height)
                            val radiusDp = lensRect.height / 2f
                            val corners = LiquidGlassCornerRadii(radiusDp, radiusDp, radiusDp, radiusDp)
                            val originPx = Offset(leftPx, topPx)
                            val atlasSizePx = Size(size.width, size.height)
                            val sampling = LiquidGlassShader.sampling(
                                originPx = originPx,
                                texelsPerDp = density.density,
                                clampPx = Rect(0f, 0f, size.width, size.height),
                            )

                            val uniforms = LiquidGlassShader.build(
                                size = sizeDp,
                                corners = corners,
                                style = style,
                                originPx = originPx,
                                density = density.density,
                                stretch = 1f,
                                atlasSizePx = atlasSizePx,
                                sampling = sampling,
                                glow = touchGlow,
                                circularCorners = true,
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                uniforms.forEach { (name, values) ->
                                    lensShader.setFloatUniform(name, values)
                                }
                                this.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                                    lensShader,
                                    "uTexture",
                                ).asComposeRenderEffect()
                            }
                        } else {
                            this.renderEffect = null
                        }
                    }
                    .drawBehind {
                        // Sample the completed backing before the foreground
                        // and lens are drawn. Both source and consumer live
                        // under the same parent swell/stretch transform.
                        val lift = liftSpring.value
                        if (lift > 0.001f) {
                            val coords = lensCoordinates
                            if (coords != null) {
                                with(lensBackdrop) {
                                    drawBackdrop(density, coords, null)
                                }
                            }
                            // 4. 老设备 (API < 33) 1:1 FLClash 悬浮凸透镜微晶拟物质感
                            if (lensShader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                val lensRect = paintedLensRect(lift)
                                val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                                val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                                val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                                val topPx = dockContentTopPx + lensRect.top * density.density
                                val widthPx = lensRect.width * density.density
                                val heightPx = lensRect.height * density.density
                                val radiusPx = heightPx / 2f

                                // A. 悬浮微晶质感底膜 (FLClash tint: transparent, ultra-light frosted sheen)
                                val fillAlpha = (if (dark) 0.12f else 0.22f) * lift
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = fillAlpha),
                                            Color.White.copy(alpha = fillAlpha * 0.35f),
                                        ),
                                        startY = topPx,
                                        endY = topPx + heightPx,
                                    ),
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                )

                                // B. 1:1 FLClash 镜面反光边缘 (_drawFrost: rim stroke + hairline)
                                val rim = if (dark) 0.30f else 0.60f
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = rim * lift),
                                            Color.White.copy(alpha = rim * 0.15f * lift),
                                            Color.White.copy(alpha = rim * 0.50f * lift),
                                        ),
                                        start = Offset(leftPx, topPx),
                                        end = Offset(leftPx + widthPx, topPx + heightPx),
                                    ),
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.1.dp.toPx()),
                                )
                                // 发丝微暗边 (_hairlineWidth = 0.67dp)
                                val hairlineAlpha = (if (dark) 0.15f else 0.08f) * lift
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = hairlineAlpha),
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.67.dp.toPx()),
                                )
                            }
                        }
                    },
            )

            // ── 层4: 镜内文字与图标 (Material 选中文字色，保留镜片的光学放大拉伸) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp + shadowRoomDp * 2)
                    .drawWithContent {
                        val lift = liftSpring.value
                        val lensRect = paintedLensRect(lift)
                        val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                        val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                        val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                        val topPx = dockContentTopPx + lensRect.top * density.density
                        val widthPx = lensRect.width * density.density
                        val heightPx = lensRect.height * density.density
                        val radiusPx = heightPx / 2f

                        lensPath.rewind()
                        lensPath.addRoundRect(
                            RoundRect(
                                rect = Rect(leftPx, topPx, leftPx + widthPx, topPx + heightPx),
                                cornerRadius = CornerRadius(radiusPx, radiusPx),
                            ),
                        )
                        clipPath(lensPath) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeightDp + shadowRoomDp * 2)
                        .graphicsLayer {
                            val lift = liftSpring.value
                            if (contentShader != null && lift > 0.001f && size.width > 0f && size.height > 0f) {
                                val lensRect = paintedLensRect(lift)
                                val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                                val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                                val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                                val topPx = dockContentTopPx + lensRect.top * density.density

                                val style = LiquidGlassStyle.lensContent(colorScheme, materialDark).scale(lift.toDouble())
                                val sizeDp = Size(lensRect.width, lensRect.height)
                                val radiusDp = lensRect.height / 2f
                                val corners = LiquidGlassCornerRadii(radiusDp, radiusDp, radiusDp, radiusDp)
                                val originPx = Offset(leftPx, topPx)
                                val atlasSizePx = Size(size.width, size.height)
                                val sampling = LiquidGlassShader.sampling(
                                    originPx = originPx,
                                    texelsPerDp = density.density,
                                    clampPx = Rect(0f, 0f, size.width, size.height),
                                )

                                val uniforms = LiquidGlassShader.build(
                                    size = sizeDp,
                                    corners = corners,
                                    style = style,
                                    originPx = originPx,
                                    density = density.density,
                                    stretch = 1f,
                                    atlasSizePx = atlasSizePx,
                                    sampling = sampling,
                                    glow = LiquidGlassGlow.None,
                                    circularCorners = true,
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    uniforms.forEach { (name, values) ->
                                        contentShader.setFloatUniform(name, values)
                                    }
                                    this.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                                        contentShader,
                                        "uTexture",
                                    ).asComposeRenderEffect()
                                }
                            } else {
                                this.renderEffect = null
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeightDp)
                            .padding(barPaddingDp)
                            // This row is a second optical painting of the same
                            // tabs. Only the outside row owns user-facing semantics.
                            .clearAndSetSemantics {},
                    ) {
                        DestinationsRow(
                            items = items,
                            layout = layout,
                            tint = selectionColors.selectedContent,
                            badgeTint = selectionColors.accent,
                            iconDrivers = iconDrivers,
                            itemTransform = { computeInsideTransform(it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 针对每个导航项的几何变换 (1:1 FLClash _lensMagnify = 0.16 中心放大)。
 */
private data class DestinationTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
) {
    companion object {
        val Identity = DestinationTransform()
    }
}

@Composable
private fun DestinationsRow(
    items: List<PillNavItemSpec>,
    layout: FlClashDockPhysics.DockLayout,
    tint: Color,
    badgeTint: Color,
    iconDrivers: List<NavIconDriver>,
    selectedIndex: Int = -1,
    onActivate: ((Int) -> Unit)? = null,
    itemTransform: (index: Int) -> DestinationTransform = { DestinationTransform.Identity },
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = layout.inset.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            var heldKey by remember { mutableStateOf<Key?>(null) }
            val accessibleTab = if (onActivate == null) Modifier else Modifier
                .semantics(mergeDescendants = true) {
                    role = Role.Tab
                    selected = index == selectedIndex
                    onClick { onActivate(index); true }
                }
                .onFocusChanged { if (!it.isFocused) heldKey = null }
                .onKeyEvent { event ->
                    val activationKey = event.key == Key.Enter || event.key == Key.NumPadEnter ||
                        event.key == Key.Spacebar || event.key == Key.DirectionCenter
                    if (!activationKey) false else {
                        when (event.type) {
                            KeyEventType.KeyDown -> heldKey = event.key
                            KeyEventType.KeyUp -> {
                                val activate = heldKey == event.key
                                heldKey = null
                                if (activate) onActivate(index)
                            }
                        }
                        true
                    }
                }
                .focusable()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(accessibleTab),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val transform = itemTransform(index)
                            scaleX = transform.scaleX
                            scaleY = transform.scaleY
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.size(24.dp)) {
                        NavPartIcon(
                            driver = iconDrivers.getOrNull(index),
                            fallback = item.icon,
                            tint = tint,
                            iconSize = 24.dp,
                            // Text supplies the single label for the tab.
                            contentDescription = null,
                        )
                        if (item.badge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .background(badgeTint, CircleShape),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(FlClashDockPhysics.LABEL_GAP.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = FlClashDockPhysics.LABEL_SIZE.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
