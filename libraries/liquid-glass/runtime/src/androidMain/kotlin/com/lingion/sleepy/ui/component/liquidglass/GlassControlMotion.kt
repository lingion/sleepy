package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

data class GlassControlMotionSpec(
    val rise: SpringDescription,
    val fall: SpringDescription,
    val release: Release = Release.NearDestination,
    val activationRelease: Release = release,
) {
    enum class Release { NearDestination, FirstPeak, OnRelease }

    companion object {
        val Dock = GlassControlMotionSpec(FlClashSprings.LiftSpring, FlClashSprings.LiftSpring)

        // Fit to the iOS 26.5 native presentation trace: a short, slightly
        // overshooting rise followed by about 250 ms of contraction. A released
        // lens contracts while travelling instead of waiting for its destination.
        val Native = GlassControlMotionSpec(
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 841.0, ratio = .56),
            FlClashSprings.LiftSpring,
            release = Release.FirstPeak,
        )

        // Native segmented pickers rise monotonically, unlike switches.
        val NativeSegmented = GlassControlMotionSpec(
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 635.0, ratio = 1.0),
            FlClashSprings.LiftSpring,
            release = Release.OnRelease,
            activationRelease = Release.NearDestination,
        )

        // Clean SwiftUI Slider held/release trace, 2026-11-12. Separate fits:
        // rise k=713, zeta=.547; fall k=216, zeta=.779 (mass normalized to one).
        val NativeSlider = GlassControlMotionSpec(
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 713.0, ratio = .547),
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 216.0, ratio = .779),
            release = Release.OnRelease,
        )
    }
}

/** Velocity-continuous lift, follow and braking in logical travel units. */
class GlassControlMotion(
    initialPosition: Float,
    private val spec: GlassControlMotionSpec = GlassControlMotionSpec.Dock,
    travelDistanceDp: Float = 0f,
) {
    val position = SpringState(initialPosition)
    val elevation = SpringState(0f)
    val jelly = SpringState(0f, rest = FlClashDockPhysics.JELLY_REST)
    private var pressed = false
    private var dragging = false
    private var activation = 0
    private var riseHasPeaked = false
    private var releasePhase = spec.release
    private var dropping by mutableStateOf(false)
    private var deformationInteraction = false
    private var distancePerPositionDp = travelDistanceDp.takeIf { it.isFinite() && it != 0f } ?: 0f
    // The locked native deformation profile was driven by business travel.
    // A separate visual endpoint target must not add forcing to that profile.
    private var deformationTargetPosition = initialPosition
    private val dragDeformation = NativeGlassDragDeformation(
        when (spec) {
            GlassControlMotionSpec.Dock -> NativeGlassDragDeformation.Spec.Dock
            GlassControlMotionSpec.NativeSlider -> NativeGlassDragDeformation.Spec.Slider
            else -> NativeGlassDragDeformation.Spec.Segment
        },
        initialPosition * distancePerPositionDp,
    )

    val lift: Float get() = elevation.value.coerceIn(0f, 1f)
    val geometryLift: Float get() = elevation.value.coerceIn(-.14f, 1.14f)
    // Native deformation rebounds across touch-up while elevation contracts;
    // multiplying by lift would erase the measured release-side rebound.
    val stretch: Float get() = jelly.value
    val stretchY: Float get() = dragDeformation.verticalDeformation(jelly.value)
    val isRunning: Boolean get() = dropping || position.isRunning || elevation.isRunning || jelly.isRunning

    /** dp per logical position unit: full travel for 0..1, seat pitch for indices. */
    fun updateMotionDistance(distanceDp: Float) {
        val measured = distanceDp.takeIf { it.isFinite() && it != 0f } ?: 0f
        if (measured == distancePerPositionDp) return
        distancePerPositionDp = measured
        dragDeformation.reset(deformationTargetPosition * measured)
        jelly.jumpTo(0f)
    }

    /** [target] drives business deformation; [visualTarget] only drives lens follow. */
    fun update(target: Float, pressed: Boolean, dragging: Boolean, activation: Int = 0, visualTarget: Float = target) {
        val activated = activation > this.activation
        val interactiveInput = pressed || dragging || activated
        val silentExternalChange = target != deformationTargetPosition && !interactiveInput && !this.pressed && !this.dragging
        if (silentExternalChange) {
            deformationInteraction = false
            dragDeformation.reset(target * distancePerPositionDp)
            jelly.jumpTo(0f)
        } else if (interactiveInput && !deformationInteraction) {
            // Normally the existing visual position is also the interaction
            // baseline. An explicitly separated endpoint target can sit beyond
            // business travel, including after a scale-zero held frame.
            val baseline = if (position.target != deformationTargetPosition) deformationTargetPosition else position.value
            dragDeformation.reset(baseline * distancePerPositionDp)
            deformationInteraction = true
        }
        deformationTargetPosition = target
        this.activation = activation
        if (pressed && !this.pressed || activated) {
            releasePhase = if (activated) spec.activationRelease else spec.release
            if (elevation.target != 1f) riseHasPeaked = false
            elevation.springTo(1f, spec.rise)
            dropping = !pressed
        } else if (!pressed && this.pressed) {
            dropping = true
        }
        if (releasePhase == GlassControlMotionSpec.Release.OnRelease && !pressed && (this.pressed || activated)) {
            // A down/up pair may coalesce before its first visual frame. Keep
            // a partial pulse in that case instead of cancelling it at zero.
            val coalescedTap = activated && elevation.value == 0f && elevation.velocity == 0f
            dropping = coalescedTap
            if (!coalescedTap) elevation.springTo(0f, spec.fall)
        }
        if (position.target != visualTarget || this.dragging != dragging && position.isRunning) {
            position.springTo(visualTarget, if (dragging) FlClashSprings.FollowSpring else FlClashSprings.TravelSpring)
        }
        this.pressed = pressed
        this.dragging = dragging
    }

    fun tick(dt: Double) {
        position.tickDelta(dt)
        elevation.tickDelta(dt)
        if (distancePerPositionDp != 0f && deformationInteraction) {
            dragDeformation.advance(dt, deformationTargetPosition * distancePerPositionDp)
            // Preserve the existing observable drawing state without another
            // spring filtering an already integrated deformation trajectory.
            jelly.jumpTo(dragDeformation.x)
            jelly.velocity = dragDeformation.velocity
            jelly.isRunning = dragDeformation.isRunning
            if (!dragDeformation.isRunning && !pressed && !dragging) deformationInteraction = false
        } else if (!deformationInteraction) {
            dragDeformation.reset(deformationTargetPosition * distancePerPositionDp)
            jelly.jumpTo(0f)
        }
        if (elevation.target == 1f && lift >= FlClashDockPhysics.LIFT_SNAP && elevation.velocity <= 0f) {
            riseHasPeaked = true
        }
        val canLower = when (releasePhase) {
            GlassControlMotionSpec.Release.FirstPeak -> riseHasPeaked
            GlassControlMotionSpec.Release.OnRelease -> lift >= .35f
            GlassControlMotionSpec.Release.NearDestination ->
                lift >= FlClashDockPhysics.LIFT_SNAP &&
                    abs(position.target - position.value) <= FlClashDockPhysics.LIFT_RELEASE
        }
        if (dropping && !pressed && canLower) {
            dropping = false
            elevation.springTo(0f, spec.fall)
        }
    }

    fun finish() {
        position.jumpTo(position.target)
        elevation.jumpTo(if (pressed) 1f else 0f)
        jelly.jumpTo(0f)
        dragDeformation.reset(deformationTargetPosition * distancePerPositionDp)
        deformationInteraction = false
        dropping = false
        riseHasPeaked = pressed
    }

    /** Cancellation lowers the existing lens without synthesizing a tap pulse. */
    fun cancel(target: Float) {
        update(target, pressed = false, dragging = false, activation = activation)
        dropping = false
        elevation.springTo(0f, spec.fall)
    }
}

/**
 * Input-owned driver for glass controls. Receipt and hardware
 * sample clocks remain separate, and every delivered transition survives even
 * when Compose has not recomposed between DOWN and UP.
 */
class TimedGlassControlMotion(
    initialPosition: Float,
    spec: GlassControlMotionSpec,
    travelDistanceDp: Float = 0f,
    initiallyEnabled: Boolean = true,
) {
    val motion = GlassControlMotion(initialPosition, spec, travelDistanceDp)
    private sealed interface Input {
        data class Begin(val epoch: Long, val target: Float, val pressed: Boolean, val dragging: Boolean) : Input
        data class Move(val epoch: Long, val target: Float, val dragging: Boolean, val visualTarget: Float = target) : Input
        data class Release(val epoch: Long, val target: Float, val activate: Boolean) : Input
        data class Cancel(val epoch: Long, val target: Float) : Input
        data class Selection(val target: Float, val activate: Boolean, val enabled: Boolean) : Input
    }

    private val clock = DockInputClock<Input> { it is Input.Begin }
    private var nextEpoch = 0L
    private var requestedEpoch: Long? = null
    private var activeEpoch: Long? = null
    private var held = false
    private var activation = 0
    private var inputEnabled = initiallyEnabled
    private var appliedEnabled = initiallyEnabled
    var requestedPosition by mutableFloatStateOf(initialPosition)
        private set
    var requestRevision by mutableIntStateOf(0)
        private set
    val appliedPressEpoch: Long? get() = activeEpoch

    fun isCurrent(epoch: Long): Boolean = inputEnabled && requestedEpoch == epoch

    fun begin(pressed: Boolean, dragging: Boolean, receivedNanos: Long, sampleUptimeMillis: Long): Long? {
        if (!inputEnabled) return null
        val epoch = ++nextEpoch
        requestedEpoch = epoch
        clock.enqueue(Input.Begin(epoch, requestedPosition, pressed, dragging), receivedNanos, sampleUptimeMillis)
        return epoch
    }

    fun move(epoch: Long, target: Float, dragging: Boolean, receivedNanos: Long, sampleUptimeMillis: Long) {
        if (isCurrent(epoch)) clock.enqueue(Input.Move(epoch, target, dragging), receivedNanos, sampleUptimeMillis)
    }

    /**
     * A Slider publishes [target] during its drag; matching parent echoes retain
     * this epoch and its independent [visualTarget]. Only business changes alter
     * the requested value/revision, while every visual move still reaches input.
     */
    fun moveContinuously(epoch: Long, target: Float, receivedNanos: Long, sampleUptimeMillis: Long, visualTarget: Float = target): Boolean {
        if (!isCurrent(epoch)) return false
        if (requestedPosition != target) {
            requestedPosition = target
            requestRevision++
        }
        clock.enqueue(Input.Move(epoch, target, dragging = true, visualTarget = visualTarget), receivedNanos, sampleUptimeMillis)
        return true
    }

    fun release(epoch: Long, target: Float, activate: Boolean, receivedNanos: Long, sampleUptimeMillis: Long): Boolean {
        if (!isCurrent(epoch)) return false
        requestedEpoch = null
        requestedPosition = target
        requestRevision++
        clock.enqueue(Input.Release(epoch, target, activate), receivedNanos, sampleUptimeMillis)
        return true
    }

    fun cancel(epoch: Long, receivedNanos: Long, sampleUptimeMillis: Long? = null) {
        if (requestedEpoch != epoch) return
        requestedEpoch = null
        clock.enqueue(Input.Cancel(epoch, requestedPosition), receivedNanos, sampleUptimeMillis)
    }

    /** A controlled range update can invalidate the pointer on either handle. */
    fun cancelPointerInput(receivedNanos: Long) {
        requestedEpoch?.let { cancel(it, receivedNanos) }
    }

    /** Semantic and keyboard activation supersedes any pending pointer gesture. */
    fun select(target: Float, receivedNanos: Long): Boolean {
        if (!inputEnabled) return false
        val changed = target != requestedPosition
        requestedEpoch = null
        requestedPosition = target
        requestRevision++
        clock.enqueue(Input.Selection(target, activate = changed, enabled = true), receivedNanos)
        return changed
    }

    /** A matching controlled-value echo must not cancel a newer pointer epoch. */
    fun synchronize(target: Float, enabled: Boolean, receivedNanos: Long, observedRevision: Int = requestRevision) {
        if (observedRevision != requestRevision) return
        if (target == requestedPosition && enabled == inputEnabled) return
        requestedEpoch = null
        requestedPosition = target
        inputEnabled = enabled
        clock.enqueue(Input.Selection(target, activate = false, enabled = enabled), receivedNanos)
    }

    fun needsFrame(): Boolean = clock.hasPending || motion.isRunning

    fun advanceFrame(frame: ActiveFrame, scaleFactor: Float) {
        clock.advance(frame,
            integrate = { dt ->
                if (scaleFactor <= 0f || !appliedEnabled) motion.finish()
                else motion.tick(dt / scaleFactor)
            },
            apply = { input ->
                when (input) {
                    is Input.Begin -> {
                        activeEpoch = input.epoch
                        held = input.pressed
                        motion.update(input.target, held, input.dragging, activation)
                    }
                    is Input.Move -> if (activeEpoch == input.epoch) {
                        motion.update(input.target, held, input.dragging, activation, visualTarget = input.visualTarget)
                    }
                    is Input.Release -> if (activeEpoch == input.epoch) {
                        activeEpoch = null
                        held = false
                        if (input.activate) activation++
                        motion.update(input.target, pressed = false, dragging = false, activation = activation)
                    }
                    is Input.Cancel -> if (activeEpoch == input.epoch) {
                        activeEpoch = null
                        val ownedLens = held
                        held = false
                        // An unselected seat never lifted the lens. Cancelling
                        // that press must not truncate an earlier valid pulse.
                        if (ownedLens) motion.cancel(input.target)
                        else motion.update(input.target, pressed = false, dragging = false, activation = activation)
                    }
                    is Input.Selection -> {
                        activeEpoch = null
                        held = false
                        appliedEnabled = input.enabled
                        motion.cancel(input.target)
                        if (input.activate) {
                            activation++
                            motion.update(input.target, pressed = false, dragging = false, activation = activation)
                        }
                    }
                }
                // Process each complete transaction at scale zero, including
                // held drags that wake an otherwise idle frame loop.
                if (scaleFactor <= 0f || !appliedEnabled) motion.finish()
            },
        )
    }
}

@Composable
fun rememberTimedGlassControlMotion(
    target: Float,
    enabled: Boolean,
    spec: GlassControlMotionSpec,
    travelDistanceDp: Float = 0f,
): TimedGlassControlMotion {
    val driver = remember(spec) { TimedGlassControlMotion(target, spec, travelDistanceDp, enabled) }
    val inputTimeNanos by rememberUpdatedState(LocalDockInputTimeNanos.current)
    // Reading the revision schedules confirmation even when a parent rejects a
    // callback without changing its controlled value. An older composition is
    // not allowed to reject a more recent request.
    val observedRevision = driver.requestRevision
    SideEffect {
        driver.motion.updateMotionDistance(travelDistanceDp)
        driver.synchronize(target, enabled, inputTimeNanos(), observedRevision)
    }
    LaunchedEffect(driver) {
        val durationScale = coroutineContext[MotionDurationScale]
        runTimestampedActiveFrameLoop(driver::needsFrame) { frame ->
            driver.advanceFrame(frame, durationScale?.scaleFactor ?: 1f)
        }
    }
    return driver
}

@Composable
fun rememberGlassControlMotion(
    target: Float,
    pressed: Boolean,
    dragging: Boolean = false,
    activation: Int = 0,
    enabled: Boolean = true,
    spec: GlassControlMotionSpec = GlassControlMotionSpec.Dock,
    travelDistanceDp: Float = 0f,
): GlassControlMotion {
    val motion = remember(spec) { GlassControlMotion(target, spec, travelDistanceDp) }
    LaunchedEffect(motion, travelDistanceDp) { motion.updateMotionDistance(travelDistanceDp) }
    LaunchedEffect(motion, target, pressed, dragging, activation, enabled) {
        motion.update(target, pressed && enabled, dragging && enabled, activation)
        if (!enabled) motion.finish()
    }
    LaunchedEffect(motion) {
        val durationScale = coroutineContext[MotionDurationScale]
        runActiveFrameLoop({ motion.isRunning }) { dt ->
            val scale = durationScale?.scaleFactor ?: 1f
            if (scale <= 0f) motion.finish() else motion.tick(dt / scale)
        }
    }
    return motion
}

/** Deform the measured lens, keeping its sampling texture at the scene's original scale. */
fun glassLensWidth(width: Float, stretch: Float): Float = width * (1f + stretch)

fun glassLensHeight(height: Float, stretch: Float, minimum: Float = 0f): Float =
    max(minimum, height * (1f - stretch * FlClashDockPhysics.JELLY_ASPECT))
