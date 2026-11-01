package com.lingion.sleepy.ui.component.liquidglass

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.shapes.Capsule
import com.lingion.sleepy.ui.component.PillNavItemSpec
import material.Hct
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    val dark = isSystemInDarkTheme()

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

    // 外部 selectedIndex 改变时, 驱动镜头滑向目标
    LaunchedEffect(clampedSelected) {
        if (pressedIndex == null && lensSpring.target.roundToInt() != clampedSelected) {
            val nowSec = SystemClock.uptimeMillis() / 1000.0
            lensSpring.springTo(clampedSelected.toFloat(), FlClashSprings.TravelSpring, nowSec)
        }
    }

    // ── 连续物理仿真帧循环 (withFrameNanos) ──
    var isRunning by remember { mutableStateOf(false) }
    val isAnySpringActive = lensSpring.isRunning || liftSpring.isRunning ||
        swellSpring.isRunning || stretchSpring.isRunning ||
        jellySpring.isRunning || glowSpring.isRunning ||
        glowSpreadSpring.isRunning || pointerId != null

    LaunchedEffect(isAnySpringActive) {
        if (!isAnySpringActive) return@LaunchedEffect
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val nowSec = frameTimeNanos / 1_000_000_000.0
                lensSpring.tick(nowSec)
                liftSpring.tick(nowSec)
                swellSpring.tick(nowSec)
                stretchSpring.tick(nowSec)
                jellySpring.tick(nowSec)
                glowSpring.tick(nowSec)
                glowSpreadSpring.tick(nowSec)

                // 果冻弹性 (wobble)
                val wobbleTarget = FlClashDockPhysics.wobbleTarget(lensSpring.velocity, lensSpring.acceleration)
                if (wobbleTarget != jellySpring.target) {
                    jellySpring.springTo(wobbleTarget, FlClashSprings.JellySpring, nowSec)
                }

                // 稳妥落锁收镜 (dropLift)
                if (dropping && pointerId == null && liftSpring.value >= FlClashDockPhysics.LIFT_SNAP) {
                    if (abs(lensSpring.target - lensSpring.value) <= FlClashDockPhysics.LIFT_RELEASE) {
                        dropping = false
                        liftSpring.springTo(0f, FlClashSprings.LiftSpring, nowSec)
                    }
                }
            }
        }
    }

    // ── 尺寸与排版计算 ──
    val barHeightDp = FlClashDockPhysics.BAR_HEIGHT.dp
    val barPaddingDp = FlClashDockPhysics.BAR_PADDING.dp
    val maxExtentDp = FlClashDockPhysics.MAX_ITEM_EXTENT.dp
    val totalWidthDp = if (count >= FlClashDockPhysics.FULL_WIDTH_COUNT) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(maxExtentDp * count + barPaddingDp * 4)
    }

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val isLtr = layoutDirection == LayoutDirection.Ltr

    fun positionAt(x: Float, layout: FlClashDockPhysics.DockLayout): Float {
        val dx = if (isLtr) x else (barWidthPx - x)
        val pos = (dx - FlClashDockPhysics.BAR_PADDING - layout.inset) / layout.pitch - 0.5f
        return when {
            pos < 0f -> FlClashDockPhysics.rubberBand(pos, FlClashDockPhysics.OVERDRAG)
            pos > lastIndex -> lastIndex + FlClashDockPhysics.rubberBand(pos - lastIndex, FlClashDockPhysics.OVERDRAG)
            else -> pos
        }
    }

    fun indexAt(position: Float): Int = position.roundToInt().coerceIn(0, lastIndex)

    val velocityTracker = remember { VelocityTracker() }

    // 触控径向光晕模型
    val touchGlow = remember(glowSpring.value, glowSpreadSpring.value, glowAt) {
        val glowVal = glowSpring.value
        if (glowVal <= 0.001f) {
            LiquidGlassGlow.None
        } else {
            LiquidGlassGlow(
                center = glowAt,
                reach = (FlClashDockPhysics.GLOW_REACH * (1f + FlClashDockPhysics.GLOW_GROWTH * glowSpreadSpring.value)).toDouble(),
                strength = (FlClashDockPhysics.GLOW_PEAK * glowVal * glowVal).toDouble(),
                wash = FlClashDockPhysics.GLOW_WASH.toDouble(),
            )
        }
    }

    // 按压缩放比例 (PressTransform)
    val swellVal = swellSpring.value
    val stretchVal = stretchSpring.value
    val liftVal = liftSpring.value
    val lensVal = lensSpring.value
    val jellyVal = jellySpring.value

    val pressGrowth = FlClashDockPhysics.pressGrowthOf(barWidthPx / (density.density.takeIf { it > 0 } ?: 1f))
    val swellScale = 1f + swellVal * pressGrowth
    val scaleX = swellScale * (1f + abs(stretchVal) / max(1f, barWidthPx) * FlClashDockPhysics.PULL_STRETCH)
    val scaleY = swellScale

    // 阴影强度 (Dark: 3x, Light: 1x)
    val shadowStrength = if (dark) 3.0f else 1.0f

    Box(
        modifier = modifier
            .then(totalWidthDp)
            .height(barHeightDp)
            .onGloballyPositioned { coords ->
                barWidthPx = coords.size.width.toFloat()
                barHeightPx = coords.size.height.toFloat()
            }
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
                this.translationX = stretchVal
            }
            .shadow(
                elevation = (8 * shadowStrength).dp,
                shape = Capsule(),
                ambientColor = colorScheme.scrim.copy(alpha = 0.08f * shadowStrength),
                spotColor = colorScheme.scrim.copy(alpha = 0.04f * shadowStrength),
            )
            .then(
                if (backdrop != null) {
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
                    Modifier.background(
                        colorScheme.surfaceContainer.copy(alpha = if (dark) 0.72f else 0.88f),
                        shape = Capsule(),
                    )
                }
            )
            .pointerInput(count, isLtr) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val nowSec = SystemClock.uptimeMillis() / 1000.0

                        when (event.type) {
                            PointerEventType.Press -> {
                                pointerId = change.id.value
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                swellSpring.springTo(1f, FlClashSprings.PressSpring, nowSec)
                                glowAt = change.position
                                glowSpreadSpring.jumpTo(0f)
                                glowSpring.springTo(1f, FlClashSprings.GlowInSpring, nowSec)

                                val layout = FlClashDockPhysics.dockLayout(
                                    size.width.toFloat() / density.density - FlClashDockPhysics.BAR_PADDING * 2f,
                                    count,
                                )
                                val pos = positionAt(change.position.x, layout)
                                val idx = indexAt(pos)

                                pressedIndex = idx
                                pressX = change.position.x
                                dragging = false
                                dropping = false

                                liftSpring.springTo(1f, FlClashSprings.LiftSpring, nowSec)
                                lensSpring.springTo(idx.toFloat(), FlClashSprings.TravelSpring, nowSec)
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
                                        val overshoot = change.position.x - change.position.x.coerceIn(0f, size.width.toFloat())
                                        val pullLimit = size.height * FlClashDockPhysics.PULL_LIMIT
                                        stretchSpring.springTo(
                                            FlClashDockPhysics.rubberBand(overshoot, pullLimit),
                                            FlClashSprings.TrackSpring,
                                            nowSec,
                                        )

                                        val layout = FlClashDockPhysics.dockLayout(
                                            size.width.toFloat() / density.density - FlClashDockPhysics.BAR_PADDING * 2f,
                                            count,
                                        )
                                        val pos = positionAt(change.position.x, layout)
                                        lensSpring.springTo(pos, FlClashSprings.FollowSpring, nowSec)

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
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val layout = FlClashDockPhysics.dockLayout(
                                        size.width.toFloat() / density.density - FlClashDockPhysics.BAR_PADDING * 2f,
                                        count,
                                    )

                                    var targetIndex = pressedIndex ?: clampedSelected
                                    if (dragging) {
                                        val direction = if (isLtr) 1f else -1f
                                        val lead = (velocityX / layout.pitch) * direction * FlClashDockPhysics.FLING_PROJECTION_SEC.toFloat()
                                        val projected = (lensSpring.target + lead).roundToInt().coerceIn(targetIndex - 1, targetIndex + 1).coerceIn(0, lastIndex)
                                        if (projected != targetIndex) {
                                            LiquidGlassHaptics.selectionClick(view)
                                            targetIndex = projected
                                        }
                                    }

                                    pressedIndex = null
                                    dragging = false
                                    dropping = true

                                    lensSpring.springTo(targetIndex.toFloat(), FlClashSprings.TravelSpring, nowSec)
                                    stretchSpring.springTo(0f, FlClashSprings.PressSpring, nowSec)
                                    glowSpring.springTo(0f, FlClashSprings.GlowOutSpring, nowSec)
                                    glowSpreadSpring.springTo(1f, FlClashSprings.GlowOutSpring, nowSec)
                                    swellSpring.springTo(0f, FlClashSprings.PressSpring, nowSec)

                                    if (targetIndex != selectedIndex) {
                                        onSelect(targetIndex)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            .padding(barPaddingDp),
    ) {
        val spanDp = (barWidthPx / density.density) - FlClashDockPhysics.BAR_PADDING * 2f
        val layout = FlClashDockPhysics.dockLayout(max(1f, spanDp), count)

        val lensRectDp = FlClashDockPhysics.lensRect(
            position = lensVal,
            stretch = jellyVal,
            pitch = layout.pitch,
            width = layout.lens,
            height = FlClashDockPhysics.BAR_HEIGHT - FlClashDockPhysics.BAR_PADDING * 2f,
            lift = liftVal,
        )

        val lensLeftPx = with(density) { (layout.inset + lensRectDp.left).dp.toPx() }
        val lensTopPx = with(density) { lensRectDp.top.dp.toPx() }
        val lensWidthPx = with(density) { lensRectDp.width.dp.toPx() }
        val lensHeightPx = with(density) { lensRectDp.height.dp.toPx() }
        val lensBoundsPx = Rect(lensLeftPx, lensTopPx, lensLeftPx + lensWidthPx, lensTopPx + lensHeightPx)

        // ── 层1: 沉底底盘 (Resting Platter) ──
        // HCT secondary 调色 (Dark: tone=4.0, Light: tone=90.0)
        val platterTone = if (dark) 4.0 else 90.0
        val platterHct = Hct.fromInt(colorScheme.secondary.toArgb()).apply { tone = platterTone }
        val platterColor = Color(platterHct.toInt()).copy(alpha = 0.85f * (1f - liftVal.coerceIn(0f, 1f)))

        Box(
            modifier = Modifier
                .offset { IntOffset(lensLeftPx.roundToInt(), lensTopPx.roundToInt()) }
                .size(with(density) { lensWidthPx.toDp() }, with(density) { lensHeightPx.toDp() })
                .clip(Capsule())
                .background(platterColor),
        )

        // ── 层2: 抬起的高光清亮凸透镜 (Lifted Glass Lens) ──
        if (liftVal > 0.001f) {
            val lensStyle = LiquidGlassStyle.lens(colorScheme, dark).scale(liftVal.toDouble())
            Box(
                modifier = Modifier
                    .offset { IntOffset(lensLeftPx.roundToInt(), lensTopPx.roundToInt()) }
                    .size(with(density) { lensWidthPx.toDp() }, with(density) { lensHeightPx.toDp() })
                    .shadow(
                        elevation = (5 * liftVal).dp,
                        shape = Capsule(),
                        ambientColor = colorScheme.scrim.copy(alpha = 0.055f * shadowStrength * liftVal),
                        spotColor = colorScheme.scrim.copy(alpha = 0.055f * shadowStrength * liftVal),
                    )
                    .then(
                        if (backdrop != null) {
                            Modifier.drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { Capsule() },
                                effects = {
                                    liquidGlass(
                                        context = context,
                                        style = lensStyle,
                                    )
                                },
                            )
                        } else {
                            Modifier.background(
                                colorScheme.surface.copy(alpha = 0.35f * liftVal),
                                shape = Capsule(),
                            )
                        }
                    ),
            )
        }

        // ── 层3: 两趟式子项渲染 (Outside 外围与 Inside 镜内放大上色) ──
        val lensPath = remember(lensBoundsPx) {
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = lensBoundsPx,
                        cornerRadius = CornerRadius(lensHeightPx / 2f, lensHeightPx / 2f),
                    ),
                )
            }
        }

        // 通道 A: 镜外区域 (Outside - onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipPath(lensPath, clipOp = ClipOp.Difference) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            DestinationsRow(
                items = items,
                layout = layout,
                tint = colorScheme.onSurfaceVariant,
                magnification = 1f,
                lensCenter = lensBoundsPx.center,
                onSelect = onSelect,
            )
        }

        // 通道 B: 镜内区域 (Inside - primary 放大 1.16x)
        if (liftVal > 0.001f) {
            val magnifyScale = 1f + FlClashDockPhysics.LENS_MAGNIFY * liftVal
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipPath(lensPath) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                DestinationsRow(
                    items = items,
                    layout = layout,
                    tint = colorScheme.primary,
                    magnification = magnifyScale,
                    lensCenter = lensBoundsPx.center,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun DestinationsRow(
    items: List<PillNavItemSpec>,
    layout: FlClashDockPhysics.DockLayout,
    tint: Color,
    magnification: Float,
    lensCenter: Offset,
    onSelect: (Int) -> Unit,
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
                            scaleX = magnification
                            scaleY = magnification
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
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
