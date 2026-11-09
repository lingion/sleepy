package com.lingion.sleepy.ui.component.liquidglass

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.shapes.Capsule
import com.lingion.sleepy.ui.component.NavIconDriver
import com.lingion.sleepy.ui.component.NavIconDriverEffects
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.component.NavPartIcon
import com.lingion.sleepy.ui.component.rememberNavIconDrivers
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import material.Hct
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 100% 逐字对齐 FLClash (FLASH) 的液态玻璃悬浮导航底栏。
 *
 * 覆盖四轴物理与视觉规范:
 * 1. 真实折射光学: 凸透镜外弯 (-4.3dp), 6.5dp 真实色散, 发丝高光, 径向触控光芒
 * 2. 动力学弹簧: Travel (400ms, b=0.15), Follow (k=1024), Jelly 速度与刹车形变, 橡皮筋超拖
 * 3. 极速触控: 即时膨胀, 平滑拖拽跟手, 100ms 甩手预测, 稳妥落锁后收镜
 * 4. 触觉微振: 跨越 tab 边界与松手确认触发系统级微震动 (CLOCK_TICK / SEGMENT_TICK)
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
    // 主题实时订阅: LocalDarkTheme 用 compositionLocalOf 提供(见 Theme.kt),
    // 组合期直读即注册依赖; derivedStateOf 把 dark 收敛成单一快照状态,
    // 供着色器 uniform / 底盘色 / 阴影强度等价消费,
    // 会话内切深浅色时立即刷新, 不再定格在初始化值直到重启。
    val isAppDark = LocalDarkTheme.current
    val dark by remember(isAppDark, colorScheme) {
        derivedStateOf { isAppDark || colorScheme.surface.luminance() < 0.5f }
    }

    val count = items.size
    val lastIndex = max(0, count - 1)
    val clampedSelected = selectedIndex.coerceIn(0, lastIndex)

    // ── 动力学弹簧状态机 ──
    val lensSpring = remember { SpringState(clampedSelected.toFloat()) }
    val liftSpring = remember { SpringState(0f) }
    val swellSpring = remember { SpringState(0f) }
    val stretchSpring = remember { SpringState(0f) }
    val jellySpring = remember { SpringState(0f, rest = FlClashDockPhysics.JELLY_REST) }
    val glowSpring = remember { SpringState(0f) }
    val glowSpreadSpring = remember { SpringState(0f) }

    var glowAt by remember { mutableStateOf(Offset.Zero) }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var pressX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf(false) }
    var pointerId by remember { mutableStateOf<Long?>(null) }

    // 图标专属动效驱动表(NavIconMotion.kt): bar 层一份, 透镜内外双遍 DestinationsRow
    // 共享同一实例 → 两圈相位逐帧一致, 透镜像素不撕裂。pressed = 手指按住 bar 且
    // 透镜正落在本格(lensSpring.value 为连续座位坐标, 半格内算覆盖)。
    val iconDrivers = rememberNavIconDrivers(items, clampedSelected)
    NavIconDriverEffects(iconDrivers, clampedSelected) { i ->
        pressedIndex != null && abs(lensSpring.value - i) < 0.5f
    }

    // 外部 selectedIndex 改变时, 驱动镜头滑向目标
    LaunchedEffect(clampedSelected) {
        if (pressedIndex == null && lensSpring.target.roundToInt() != clampedSelected) {
            lensSpring.springTo(clampedSelected.toFloat(), FlClashSprings.TravelSpring)
        }
    }

    LaunchedEffect(Unit) {
        runActiveFrameLoop(
            needsFrame = {
                dragging || dropping || lensSpring.isRunning || liftSpring.isRunning ||
                    swellSpring.isRunning || stretchSpring.isRunning || jellySpring.isRunning ||
                    glowSpring.isRunning || glowSpreadSpring.isRunning
            },
        ) { dt ->
            lensSpring.tickDelta(dt)
            liftSpring.tickDelta(dt)
            swellSpring.tickDelta(dt)
            stretchSpring.tickDelta(dt)
            jellySpring.tickDelta(dt)
            glowSpring.tickDelta(dt)
            glowSpreadSpring.tickDelta(dt)

            // 果冻弹性 (wobble):
            // 1. 手指拖拽中 (dragging): 受拖拽速度连续平滑驱动纯拉伸，彻底杜绝阻尼高频振颤引起的假性刹车相互抵消
            // 2. 释放飞行/回弹阶段 (lensSpring.isRunning): 包含精准刹车拍扁 (braking squash) 与果冻回弹
            val wobbleTarget = if (dragging) {
                val vel = abs(lensSpring.velocity)
                FlClashDockPhysics.JELLY_STRETCH * FlClashDockPhysics.saturate(vel / FlClashDockPhysics.JELLY_SPEED)
            } else if (lensSpring.isRunning) {
                FlClashDockPhysics.wobbleTarget(lensSpring.velocity, lensSpring.acceleration)
            } else {
                0f
            }
            if (abs(wobbleTarget - jellySpring.target) > 1e-3f) {
                jellySpring.springTo(wobbleTarget, FlClashSprings.JellySpring)
            }

            // 稳妥落锁收镜 (dropLift): 当手指已松开且镜片升满时, 落回沉底底盘
            if (dropping && pointerId == null) {
                val nearTarget = abs(lensSpring.target - lensSpring.value) <= FlClashDockPhysics.LIFT_RELEASE
                if (liftSpring.value >= FlClashDockPhysics.LIFT_SNAP && nearTarget) {
                    dropping = false
                    liftSpring.springTo(0f, FlClashSprings.LiftSpring)
                } else if (!liftSpring.isRunning && liftSpring.value > 0f && nearTarget) {
                    // 容错兜底: 若升镜已达峰停止且对准目标, 立即触发回弹落底
                    dropping = false
                    liftSpring.springTo(0f, FlClashSprings.LiftSpring)
                }
            }
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
    var dockCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isLtr = layoutDirection == LayoutDirection.Ltr

    val layout = remember(barWidthPx, density.density, count) {
        val spanDp = (barWidthPx / density.density) - FlClashDockPhysics.BAR_PADDING * 2f
        FlClashDockPhysics.dockLayout(max(1f, spanDp), count)
    }

    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentLayout by rememberUpdatedState(layout)
    val currentDensity by rememberUpdatedState(density.density)
    val currentIsLtr by rememberUpdatedState(isLtr)
    val currentLastIndex by rememberUpdatedState(lastIndex)

    fun positionAt(xPx: Float): Float {
        val xDp = xPx / currentDensity
        val barWidthDp = barWidthPx / currentDensity
        val dxDp = if (currentIsLtr) xDp else (barWidthDp - xDp)
        val pos = (dxDp - FlClashDockPhysics.BAR_PADDING - currentLayout.inset) / currentLayout.pitch - 0.5f
        return when {
            pos < 0f -> FlClashDockPhysics.rubberBand(pos, FlClashDockPhysics.OVERDRAG)
            pos > currentLastIndex -> currentLastIndex + FlClashDockPhysics.rubberBand(pos - currentLastIndex, FlClashDockPhysics.OVERDRAG)
            else -> pos
        }
    }

    fun indexAt(position: Float): Int = position.roundToInt().coerceIn(0, currentLastIndex)

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

    // 沉底底盘调色 (Dark: surfaceContainerHighest, Light: surfaceContainer / 优雅柔和不刺眼)
    val restingPlatterColor = remember(colorScheme, dark) {
        if (dark) {
            colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
        } else {
            colorScheme.surfaceContainer.copy(alpha = 0.92f)
        }
    }

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
            .then(totalWidthModifier)
            .height(barHeightDp + shadowRoomDp * 2)
            .onGloballyPositioned { coords ->
                barWidthPx = coords.size.width.toFloat()
                barHeightPx = coords.size.height.toFloat()
                dockCoordinates = coords
            }
            .pointerInput(count, isLtr) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        when (event.type) {
                            PointerEventType.Press -> {
                                pointerId = change.id.value
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                swellSpring.springTo(1f, FlClashSprings.PressSpring)
                                glowAt = change.position
                                glowSpreadSpring.jumpTo(0f)
                                glowSpring.springTo(1f, FlClashSprings.GlowInSpring)

                                val pos = positionAt(change.position.x)
                                val idx = indexAt(pos)

                                pressedIndex = idx
                                pressX = change.position.x
                                dragging = false
                                dropping = false

                                liftSpring.springTo(1f, FlClashSprings.LiftSpring)
                                lensSpring.springTo(idx.toFloat(), FlClashSprings.TravelSpring)
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                if (pointerId == change.id.value) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    glowAt = change.position

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
                                        val targetStretch = FlClashDockPhysics.rubberBand(overshootPx, pullLimit)
                                        // 顺着手的拉力整体牵引整个 Dock
                                        stretchSpring.springTo(targetStretch, FlClashSprings.TrackSpring)

                                        val pos = positionAt(change.position.x)
                                        // 1:1 FLClash 跟手追踪 (FollowSpring, mass=1, k=1024, ratio=0.9):
                                        // 保留微秒级连续速度与加速度微积分，驱动果冻弹簧根据拖拽快慢自适应剧烈拉长/扁平形变
                                        lensSpring.springTo(pos, FlClashSprings.FollowSpring)

                                        val idx = indexAt(pos)
                                        if (idx != pressedIndex) {
                                            LiquidGlassHaptics.selectionClick(view)
                                            pressedIndex = idx
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            PointerEventType.Release -> {
                                if (pointerId == change.id.value) {
                                    pointerId = null
                                    val velocityXDp = velocityTracker.calculateVelocity().x / currentDensity

                                    var targetIndex = pressedIndex ?: currentSelected
                                    if (dragging) {
                                        val direction = if (currentIsLtr) 1f else -1f
                                        val lead = (velocityXDp / currentLayout.pitch) * direction * FlClashDockPhysics.FLING_PROJECTION_SEC.toFloat()
                                        val projected = (lensSpring.target + lead).roundToInt().coerceIn(targetIndex - 1, targetIndex + 1).coerceIn(0, currentLastIndex)
                                        if (projected != targetIndex) {
                                            LiquidGlassHaptics.selectionClick(view)
                                            targetIndex = projected
                                        }
                                    }

                                    pressedIndex = null
                                    dragging = false
                                    dropping = true

                                    lensSpring.springTo(targetIndex.toFloat(), FlClashSprings.TravelSpring)
                                    stretchSpring.springTo(0f, FlClashSprings.PressSpring)
                                    glowSpring.springTo(0f, FlClashSprings.GlowOutSpring)
                                    glowSpreadSpring.springTo(1f, FlClashSprings.GlowOutSpring)
                                    swellSpring.springTo(0f, FlClashSprings.PressSpring)

                                    if (liftSpring.value >= FlClashDockPhysics.LIFT_SNAP &&
                                        abs(targetIndex.toFloat() - lensSpring.value) <= FlClashDockPhysics.LIFT_RELEASE) {
                                        dropping = false
                                        liftSpring.springTo(0f, FlClashSprings.LiftSpring)
                                    }

                                    if (targetIndex != currentSelected) {
                                        currentOnSelect(targetIndex)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── 全 Dock 弹簧物理形变层 (包裹所有 4 个子层，随 stretchSpring 整体弹性位移与拉伸) ──
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
                    translationX = stretchVal
                },
            contentAlignment = Alignment.Center,
        ) {
            // ── 层1: Dock 胶囊底座 (62dp) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp)
                    .shadow(
                        elevation = (12 * shadowStrength).dp,
                        shape = Capsule(),
                        clip = false,
                        ambientColor = colorScheme.scrim.copy(alpha = (if (dark) 0.25f else 0.16f) * shadowStrength),
                        spotColor = colorScheme.scrim.copy(alpha = (if (dark) 0.35f else 0.22f) * shadowStrength),
                    )
                    .background(
                        color = colorScheme.surfaceContainer.copy(alpha = if (dark) 0.72f else 0.88f),
                        shape = Capsule(),
                    )
                    .border(
                        width = 1.dp,
                        color = if (dark) Color.White.copy(alpha = 0.12f) else Color(0x1F000000),
                        shape = Capsule(),
                    )
                    .then(
                        if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { Capsule() },
                                effects = {
                                    liquidGlass(
                                        context = context,
                                        style = LiquidGlassStyle.pill(colorScheme, dark),
                                        glow = touchGlow,
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
                                val lensRect = FlClashDockPhysics.lensRect(
                                    position = lensSpring.value,
                                    stretch = jellySpring.value,
                                    pitch = layout.pitch,
                                    width = layout.lens,
                                    height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                    lift = lift,
                                )
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
                        val lensRect = FlClashDockPhysics.lensRect(
                            position = lensSpring.value,
                            stretch = jellySpring.value,
                            pitch = layout.pitch,
                            width = layout.lens,
                            height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                            lift = lift,
                        )
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
                        tint = colorScheme.onSurfaceVariant,
                        iconDrivers = iconDrivers,
                    )
                }
            }

            // ── 层3: 浮空凸透镜 (70dp 独立上层图层，运行 1:1 liquid_glass.agsl lens 折射着色器) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp + shadowRoomDp * 2)
                    .drawBehind {
                        // 1. FLClash 立体下沉投影: offset (0, 8.dp * lift), blur 8.dp
                        val lift = liftSpring.value
                        if (lift > 0.001f) {
                            val lensRect = FlClashDockPhysics.lensRect(
                                position = lensSpring.value,
                                stretch = jellySpring.value,
                                pitch = layout.pitch,
                                width = layout.lens,
                                height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                lift = lift,
                            )
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
                            val lensRect = FlClashDockPhysics.lensRect(
                                position = lensSpring.value,
                                stretch = jellySpring.value,
                                pitch = layout.pitch,
                                width = layout.lens,
                                height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                lift = lift,
                            )
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
                            val lensRect = FlClashDockPhysics.lensRect(
                                position = lensSpring.value,
                                stretch = jellySpring.value,
                                pitch = layout.pitch,
                                width = layout.lens,
                                height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                lift = lift,
                            )
                            val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                            val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                            val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                            val topPx = dockContentTopPx + lensRect.top * density.density

                            val style = LiquidGlassStyle.lens(colorScheme, dark).scale(lift.toDouble())
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
                        // 1. 内部绘制层：向 uTexture 提供完整的未裁切背景（Backdrop 与完整 Dock 胶囊条），使透镜向外折射能采到真实边缘
                        val lift = liftSpring.value
                        if (lift > 0.001f) {
                            // 1. 实时背景图层 (Backdrop)
                            val coords = dockCoordinates
                            if (backdrop != null && coords != null) {
                                with(backdrop) {
                                    drawBackdrop(density, coords, null)
                                }
                            }
                            // 2. 底层完整 Dock 胶囊背景底盘
                            val dockTopPx = shadowRoomDp.toPx()
                            val dockHeightPx = barHeightDp.toPx()
                            drawRoundRect(
                                color = colorScheme.surfaceContainer.copy(alpha = if (dark) 0.72f else 0.88f),
                                topLeft = Offset(0f, dockTopPx),
                                size = Size(size.width, dockHeightPx),
                                cornerRadius = CornerRadius(dockHeightPx / 2f, dockHeightPx / 2f),
                            )
                            // Dock 胶囊顶底微光边缘 (对齐 LiquidGlassStyle.pill rim: 0.35, 为 AGSL 透镜折射提供高反差边缘发生真实棱镜色散)
                            drawRoundRect(
                                color = Color.White.copy(alpha = if (dark) 0.28f else 0.40f),
                                topLeft = Offset(0f, dockTopPx),
                                size = Size(size.width, dockHeightPx),
                                cornerRadius = CornerRadius(dockHeightPx / 2f, dockHeightPx / 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                            )
                            // 3. 沉底槽位色块 (随起镜淡出)
                            if (lift < 1f) {
                                val lensRect = FlClashDockPhysics.lensRect(
                                    position = lensSpring.value,
                                    stretch = jellySpring.value,
                                    pitch = layout.pitch,
                                    width = layout.lens,
                                    height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                    lift = lift,
                                )
                                val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                                val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                                val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                                val topPx = dockContentTopPx + lensRect.top * density.density
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
                            // 4. 老设备 (API < 33) 1:1 FLClash 悬浮凸透镜微晶拟物质感
                            if (lensShader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                val lensRect = FlClashDockPhysics.lensRect(
                                    position = lensSpring.value,
                                    stretch = jellySpring.value,
                                    pitch = layout.pitch,
                                    width = layout.lens,
                                    height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                    lift = lift,
                                )
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

            // ── 层4: 镜内文字与图标 (Inside Destinations - primary, 1:1 FLClash lensContent 边缘非线性光学放大拉伸) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp + shadowRoomDp * 2)
                    .drawWithContent {
                        val lift = liftSpring.value
                        val lensRect = FlClashDockPhysics.lensRect(
                            position = lensSpring.value,
                            stretch = jellySpring.value,
                            pitch = layout.pitch,
                            width = layout.lens,
                            height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                            lift = lift,
                        )
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
                                val lensRect = FlClashDockPhysics.lensRect(
                                    position = lensSpring.value,
                                    stretch = jellySpring.value,
                                    pitch = layout.pitch,
                                    width = layout.lens,
                                    height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
                                    lift = lift,
                                )
                                val dockContentTopPx = (shadowRoomDp + FlClashDockPhysics.BAR_PADDING.dp).toPx()
                                val dockContentLeftPx = FlClashDockPhysics.BAR_PADDING.dp.toPx()
                                val leftPx = dockContentLeftPx + (layout.inset + lensRect.left) * density.density
                                val topPx = dockContentTopPx + lensRect.top * density.density

                                val style = LiquidGlassStyle.lensContent(colorScheme, dark).scale(lift.toDouble())
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
                            .padding(barPaddingDp),
                    ) {
                        DestinationsRow(
                            items = items,
                            layout = layout,
                            tint = colorScheme.primary,
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
    iconDrivers: List<NavIconDriver>,
    itemTransform: (index: Int) -> DestinationTransform = { DestinationTransform.Identity },
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = layout.inset.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                            contentDescription = item.label,
                        )
                        if (item.badge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .background(Color.Red, CircleShape),
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
