// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.glassBackdropProducer
import com.lingion.sleepy.ui.glass.rememberGlassCombinedBackdrop
import com.lingion.sleepy.ui.glass.rememberGlassLayerBackdrop
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface
import com.lingion.sleepy.ui.glass.ui.*
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal enum class LiquidSliderPhase {
    Idle,
    ThumbPressed,
    PreviewDragging,
    TrackAnimating
}

internal object LiquidSliderMath {
    fun smoothVelocity(previous: Float, target: Float, response: Float = 0.14f): Float {
        val safeResponse = response.coerceIn(0f, 1f)
        val stableTarget = if (abs(target) < 0.08f) 0f else target
        return previous + (stableTarget - previous) * safeResponse
    }

    fun valueFromPosition(
        positionX: Float,
        width: Float,
        thumbInset: Float,
        rangeStart: Float,
        rangeEnd: Float,
        isLtr: Boolean
    ): Float {
        val usableWidth = (width - thumbInset * 2f).coerceAtLeast(1f)
        val ltrFraction = ((positionX - thumbInset) / usableWidth).coerceIn(0f, 1f)
        val fraction = if (isLtr) ltrFraction else 1f - ltrFraction
        return rangeStart + (rangeEnd - rangeStart) * fraction
    }

    fun positionForValue(
        value: Float,
        width: Float,
        thumbInset: Float,
        rangeStart: Float,
        rangeEnd: Float,
        isLtr: Boolean
    ): Float {
        val fraction = ((value - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)
        val ltrFraction = if (isLtr) fraction else 1f - fraction
        return thumbInset + (width - thumbInset * 2f).coerceAtLeast(1f) * ltrFraction
    }

    fun valueFromPositionWithSnap(
        positionX: Float,
        width: Float,
        thumbInset: Float,
        rangeStart: Float,
        rangeEnd: Float,
        isLtr: Boolean,
        snapValue: Float?,
        snapRadius: Float
    ): Float {
        val rawValue = valueFromPosition(
            positionX = positionX,
            width = width,
            thumbInset = thumbInset,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isLtr = isLtr
        )
        val snap = snapValue?.coerceIn(rangeStart, rangeEnd) ?: return rawValue
        val snapPosition = positionForValue(
            value = snap,
            width = width,
            thumbInset = thumbInset,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isLtr = isLtr
        )
        return if (abs(positionX - snapPosition) <= snapRadius) snap else rawValue
    }
}

internal class LiquidSliderCommitGate {
    private var generation = 0

    fun next(): Int = ++generation

    fun isCurrent(token: Int): Boolean = token == generation
}

private class FramePreviewDispatcher(private val scope: CoroutineScope) {
    private var pendingValue = 0f
    private var dispatchJob: Job? = null

    fun offer(value: Float, dispatch: (Float) -> Unit) {
        pendingValue = value
        if (dispatchJob?.isActive == true) return
        dispatchJob = scope.launch {
            awaitFrame()
            dispatch(pendingValue)
        }
    }

    fun flush(value: Float, dispatch: (Float) -> Unit) {
        dispatchJob?.cancel()
        dispatchJob = null
        pendingValue = value
        dispatch(value)
    }

    fun cancel() {
        dispatchJob?.cancel()
        dispatchJob = null
    }
}

private class LiquidSliderMotionState(
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val visibilityThreshold: Float,
    private val scope: CoroutineScope,
    private val pressedScale: Float
) {
    private val valueSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val velocitySpec = spring<Float>(0.5f, 300f, 0.05f)
    private val pressSpec = spring<Float>(1f, 1000f, 0.001f)
    private val scaleXSpec = spring<Float>(0.6f, 250f, 0.001f)
    private val scaleYSpec = spring<Float>(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue.coerceIn(valueRange), visibilityThreshold)
    var visualValue by mutableFloatStateOf(initialValue.coerceIn(valueRange))
        private set
    private val velocityAnimation = Animatable(0f, 0.05f)
    var velocity by mutableFloatStateOf(0f)
        private set
    val pressProgress = Animatable(0f, 0.001f)
    val scaleX = Animatable(1f, 0.001f)
    val scaleY = Animatable(1f, 0.001f)

    var phase by mutableStateOf(LiquidSliderPhase.Idle)
        private set
    private var motionJob: Job? = null
    private var velocityJob: Job? = null
    private val gate = LiquidSliderCommitGate()

    val progress: Float
        get() = ((visualValue - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    fun beginThumb(): Int {
        motionJob?.cancel()
        velocityJob?.cancel()
        velocity = 0f
        val token = gate.next()
        phase = LiquidSliderPhase.ThumbPressed
        press()
        return token
    }

    fun activatePreview(token: Int): Boolean {
        if (!gate.isCurrent(token) || phase == LiquidSliderPhase.Idle) return false
        phase = LiquidSliderPhase.PreviewDragging
        return true
    }

    fun isCurrent(token: Int): Boolean = gate.isCurrent(token)

    fun dragTo(value: Float, normalizedVelocity: Float) {
        visualValue = value.coerceIn(valueRange)
        velocityJob?.cancel()
        val targetVelocity = normalizedVelocity.coerceIn(-4f, 4f)
        velocity = LiquidSliderMath.smoothVelocity(velocity, targetVelocity)
    }

    fun finishThumb(token: Int) {
        if (!gate.isCurrent(token)) return
        phase = LiquidSliderPhase.Idle
        release()
    }

    fun cancelThumb(token: Int, persistedValue: Float) {
        if (!gate.isCurrent(token)) return
        phase = LiquidSliderPhase.Idle
        release()
        syncExternal(persistedValue, animate = false)
    }

    fun animateTrack(
        target: Float,
        onPreview: (Float) -> Unit,
        onCommit: (Float) -> Unit
    ) {
        motionJob?.cancel()
        val token = gate.next()
        phase = LiquidSliderPhase.TrackAnimating
        press()
        motionJob = scope.launch {
            try {
                val coercedTarget = target.coerceIn(valueRange)
                valueAnimation.snapTo(visualValue)
                valueAnimation.animateTo(coercedTarget, valueSpec) {
                    visualValue = value
                    onPreview(value)
                }
                if (gate.isCurrent(token)) {
                    onPreview(coercedTarget)
                    onCommit(coercedTarget)
                }
            } finally {
                if (gate.isCurrent(token)) {
                    phase = LiquidSliderPhase.Idle
                    release()
                }
            }
        }
    }

    fun syncExternal(value: Float, animate: Boolean = true) {
        if (phase != LiquidSliderPhase.Idle) return
        val target = value.coerceIn(valueRange)
        if (abs(visualValue - target) <= visibilityThreshold) return
        motionJob?.cancel()
        if (!animate) {
            visualValue = target
            motionJob = scope.launch { valueAnimation.snapTo(target) }
        } else {
            motionJob = scope.launch {
                valueAnimation.snapTo(visualValue)
                valueAnimation.animateTo(target, valueSpec) { visualValue = value }
            }
        }
    }

    fun dispose() {
        gate.next()
        motionJob?.cancel()
        velocityJob?.cancel()
    }

    private fun press() {
        scope.launch { pressProgress.animateTo(1f, pressSpec) }
        scope.launch { scaleX.animateTo(pressedScale, scaleXSpec) }
        scope.launch { scaleY.animateTo(pressedScale, scaleYSpec) }
    }

    private fun release() {
        scope.launch { pressProgress.animateTo(0f, pressSpec) }
        scope.launch { scaleX.animateTo(1f, scaleXSpec) }
        scope.launch { scaleY.animateTo(1f, scaleYSpec) }
        velocityJob?.cancel()
        velocityJob = scope.launch {
            velocityAnimation.snapTo(velocity)
            velocityAnimation.animateTo(0f, velocitySpec) { this@LiquidSliderMotionState.velocity = value }
        }
    }
}

@Composable
fun LiquidSlider(
    value: () -> Float,
    onPreviewValueChange: (Float) -> Unit,
    onPreviewModeChange: (Boolean) -> Unit,
    onCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    snapValue: Float? = null,
    modifier: Modifier = Modifier,
    compactThumb: Boolean = false
) {
    if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSliderRenderer.current?.invoke(
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSliderRequest(
                value, valueRange, onPreviewValueChange, onPreviewModeChange, onCommit, modifier, snapValue
            )) == true) return
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val trackBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.ChromeCombined,
        providerId = "liquid-slider-track"
    )
    val scope = rememberCoroutineScope()
    val currentPreview by rememberUpdatedState(onPreviewValueChange)
    val currentPreviewMode by rememberUpdatedState(onPreviewModeChange)
    val currentCommit by rememberUpdatedState(onCommit)
    val currentValue by rememberUpdatedState(value)
    val pressedScale = if (compactThumb) 1.2f else 1.5f
    val motion = remember(valueRange, visibilityThreshold, pressedScale) {
        LiquidSliderMotionState(currentValue(), valueRange, visibilityThreshold, scope, pressedScale)
    }
    val previewDispatcher = remember(scope) { FramePreviewDispatcher(scope) }
    val material = remember {
        GlassMaterialSpec(
            role = GlassMaterialRole.Control,
            blur = 8.dp,
            lensHeight = 10.dp,
            lensAmount = 14.dp,
            surfaceAlpha = 1f,
            borderAlpha = 0f,
            highlightAlpha = 0.45f,
            shadowAlpha = 0.05f,
            innerShadowAlpha = 1f,
            chromaticAberration = false,
            depthEffect = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidSliderThumb",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Control,
        sceneKey = "liquid-slider-thumb"
    )

    DisposableEffect(motion, previewDispatcher) {
        onDispose {
            previewDispatcher.cancel()
            motion.dispose()
        }
    }

    LaunchedEffect(motion) {
        snapshotFlow { currentValue().coerceIn(valueRange) }
            .collectLatest { externalValue -> motion.syncExternal(externalValue) }
    }

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val thumbInsetPx = with(density) { (if (compactThumb) 16.dp else 10.dp).toPx() }
        val thumbHitRadiusPx = with(density) { 18.dp.toPx() }
        val dragThresholdPx = with(density) { 4.dp.toPx() }
        val snapHitRadiusPx = with(density) { 12.dp.toPx() }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(motion, trackWidth, isLtr, snapValue) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val thumbCenter = LiquidSliderMath.positionForValue(
                            motion.visualValue,
                            trackWidth,
                            thumbInsetPx,
                            valueRange.start,
                            valueRange.endInclusive,
                            isLtr
                        )
                        val isThumbPress = abs(down.position.x - thumbCenter) <= thumbHitRadiusPx
                        if (!isThumbPress) {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                val target = LiquidSliderMath.valueFromPositionWithSnap(
                                    down.position.x,
                                    trackWidth,
                                    thumbInsetPx,
                                    valueRange.start,
                                    valueRange.endInclusive,
                                    isLtr,
                                    snapValue,
                                    snapHitRadiusPx
                                )
                                motion.animateTrack(
                                    target = target,
                                    onPreview = { previewDispatcher.offer(it, currentPreview) },
                                    onCommit = {
                                        previewDispatcher.flush(it, currentPreview)
                                        currentCommit(it)
                                    }
                                )
                            }
                            return@awaitEachGesture
                        }

                        down.consume()
                        val token = motion.beginThumb()
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }
                        val downPosition = down.position
                        var changed = false
                        var completedNormally = false
                        val holdJob = scope.launch {
                            delay(80L)
                            if (motion.activatePreview(token)) currentPreviewMode(true)
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    completedNormally = change != null
                                    break
                                }
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (!changed && (change.position - downPosition).getDistance() >= dragThresholdPx) {
                                    changed = true
                                    // A real drag is already an unambiguous preview gesture. Enter preview
                                    // immediately instead of waiting for the stationary 80ms hold gate, so a
                                    // quick swipe hides sibling controls just like a deliberate long press.
                                    holdJob.cancel()
                                    if (motion.activatePreview(token)) currentPreviewMode(true)
                                }
                                if (changed) {
                                    val nextValue = LiquidSliderMath.valueFromPositionWithSnap(
                                        change.position.x,
                                        trackWidth,
                                        thumbInsetPx,
                                        valueRange.start,
                                        valueRange.endInclusive,
                                        isLtr,
                                        snapValue,
                                        snapHitRadiusPx
                                    )
                                    val pointerVelocity = velocityTracker.calculateVelocity().x
                                    val normalizedVelocity = pointerVelocity /
                                        trackWidth.coerceAtLeast(1f) * 10f
                                    motion.dragTo(nextValue, normalizedVelocity)
                                    previewDispatcher.offer(nextValue, currentPreview)
                                }
                            }
                        } catch (_: CancellationException) {
                            throw CancellationException()
                        } finally {
                            holdJob.cancel()
                            val previewWasActive = motion.phase == LiquidSliderPhase.PreviewDragging
                            if (completedNormally) {
                                val finalValue = motion.visualValue.coerceIn(valueRange)
                                if (changed) {
                                    previewDispatcher.flush(finalValue, currentPreview)
                                    currentCommit(finalValue)
                                }
                                motion.finishThumb(token)
                            } else {
                                previewDispatcher.cancel()
                                motion.cancelThumb(token, value())
                            }
                            if (previewWasActive) currentPreviewMode(false)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.glassBackdropProducer(trackBackdrop)) {
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(trackColor)
                        .height(6.dp)
                        .fillMaxWidth()
                )
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(accentColor)
                        .height(6.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val width = (constraints.maxWidth * motion.progress).fastRoundToInt()
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        }
                )
            }

            Box(
                Modifier
                    .graphicsLayer {
                        val thumbCenter = LiquidSliderMath.positionForValue(
                            value = motion.visualValue,
                            width = trackWidth,
                            thumbInset = thumbInsetPx,
                            rangeStart = valueRange.start,
                            rangeEnd = valueRange.endInclusive,
                            isLtr = isLtr
                        )
                        translationX = thumbCenter - size.width / 2f
                    }
                    .sleepDownGlassSurface(
                        backdrop = rememberGlassCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = motion.pressProgress.value
                                scale(lerp(1f / pressedScale, 1f, progress), lerp(0f, 1f, progress)) {
                                    drawBackdrop()
                                }
                            }
                        ),
                        descriptor = descriptor,
                        material = material,
                        fallbackColor = Color.White,
                        fallbackOpacity = 1f,
                        shape = { Capsule() },
                        effectFrame = GlassEffectFrame(blur = null),
                        effectsOverride = {
                            val progress = motion.pressProgress.value
                            vibrancy()
                            blur(8.dp.toPx() * (1f - progress))
                            lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = false)
                        },
                        highlightOverride = {
                            val progress = motion.pressProgress.value
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / pressedScale,
                                blurRadius = Highlight.Ambient.blurRadius / pressedScale,
                                alpha = progress * 0.45f
                            )
                        },
                        shadowOverride = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                        innerShadowOverride = {
                            val progress = motion.pressProgress.value
                            InnerShadow(radius = 4.dp * progress, alpha = progress)
                        },
                        additionalLayerBlock = {
                            scaleX = motion.scaleX.value
                            scaleY = motion.scaleY.value
                            val velocity = motion.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 1f - motion.pressProgress.value))
                        }
                    )
                    // Keep the 36dp gesture track; only the visible thumb becomes smaller.
                    .size(if (compactThumb) 34.dp else 40.dp, if (compactThumb) 22.dp else 24.dp)
            )
        }
    }
}
