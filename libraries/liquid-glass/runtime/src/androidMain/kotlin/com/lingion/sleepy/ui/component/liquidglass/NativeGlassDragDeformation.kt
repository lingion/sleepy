package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Acceleration-driven approximation to the recorded iOS 26.5 drag matrix.
 *
 * All positions are dp and time is seconds. A unit-mass follower tracks the
 * input, and its acceleration drives a second unit-mass spring:
 *
 *     p'' = kf (input - p) - cf p'
 *     a'' = ki (p'' - a) - ci a'
 *     q'' = a - cd q' - kd q
 *     scaleX = 1 + gain q
 *
 * Constant-speed motion tends to zero deformation; starting and braking carry
 * signed energy through both springs. The held lens can therefore reach its
 * first peak after the finger stops, then rebound without an up-event timer.
 * The acceleration follower is critically damped. Segment/Dock parameters were
 * fitted to both repetitions of their direction/speed matrix; Slider uses its
 * own forward training and reverse holdouts. Parameters are empirical, not read
 * from a private native implementation. This is an approximation, not an exact replica.
 */
class NativeGlassDragDeformation(
    private val spec: Spec = Spec.Segment,
    initialPositionDp: Float = 0f,
) {
    data class Spec(
        val followerStiffness: Double,
        val followerDampingRatio: Double,
        val accelerationFilterStiffness: Double,
        val deformationStiffness: Double,
        val deformationDampingRatio: Double,
        val positiveGain: Double,
        val negativeGain: Double,
        val positiveXAspect: Double,
        val negativeXAspect: Double = 1.0,
    ) {
        companion object {
            val Segment = Spec(102.64835, .671483, 151.41661, 260.47090, .323936,
                .01127168, .01202510, 1.17)
            val Dock = Spec(136.97630, .615304, 117.35327, 234.98117, .463322,
                .01151552, .01313549, 1.41)

            // Slider65: forward slow/fast train, complete reverse gestures held out.
            // Independent empirical fit, not native constants. At the existing 240Hz
            // integration limit, held-out fast carrier width/height RMSE is .461/.300pt
            // given native lift, versus 3.191/2.075pt for isotropic geometry. See the
            // native-glass/slider-endpoint-65 fixture provenance. Keep this stage order:
            // exchanging springs is equivalent continuously, but not numerically.
            val Slider = Spec(1752.03562712, 3.0, 458.09850777, 207.20738710, .3278919171,
                .008486517928, .009023585816, 1.0054241240, 1.0009013734)
        }
    }

    private val followerDamping = 2.0 * spec.followerDampingRatio * sqrt(spec.followerStiffness)
    private val deformationDamping = 2.0 * spec.deformationDampingRatio * sqrt(spec.deformationStiffness)
    private val accelerationFilterDamping = 2.0 * sqrt(spec.accelerationFilterStiffness)
    private var follower = initialPositionDp.toDouble()
    private var followerVelocity = 0.0
    private var deformation = 0.0
    private var deformationVelocity = 0.0
    private var filteredAcceleration = 0.0
    private var accelerationFilterVelocity = 0.0
    private var input = follower

    /** Signed changes from unit scale. Positive x means wider / shorter. */
    private val gain: Double get() = if (deformation >= 0.0) spec.positiveGain else spec.negativeGain
    val x: Float get() = (gain * deformation).coerceIn(-.22, .22).toFloat()
    val y: Float get() = verticalDeformation(x)
    val velocity: Float get() = (gain * deformationVelocity).toFloat()
    val isRunning: Boolean
        get() = abs(input - follower) > .001 || abs(followerVelocity) > .01 ||
            abs(filteredAcceleration) > .01 || abs(accelerationFilterVelocity) > .1 ||
            abs(gain * deformation) > .0001 || abs(gain * deformationVelocity) > .0005

    fun verticalDeformation(horizontal: Float): Float =
        (-horizontal * if (horizontal >= 0f) spec.positiveXAspect else spec.negativeXAspect).toFloat()

    fun advance(seconds: Double, targetPositionDp: Float) {
        if (!seconds.isFinite() || seconds < 0.0 || !targetPositionDp.isFinite()) return
        input = targetPositionDp.toDouble()
        // A fresh frame establishes its time origin without advancing physics.
        // Retain the new target so that callers cannot mistake that zero-time
        // frame for a settled deformation and discard the selection pulse.
        if (seconds == 0.0) return
        // Bounded substeps keep 60/90/120Hz and dropped frames on the same ODE.
        // An app resumed after a long pause has no visible intermediate frames.
        if (seconds > 1.0) {
            reset(targetPositionDp)
            return
        }
        val count = ceil(seconds * 240.0).toInt().coerceAtLeast(1)
        val step = seconds / count
        repeat(count) {
            val acceleration = spec.followerStiffness * (input - follower) - followerDamping * followerVelocity
            followerVelocity += acceleration * step
            follower += followerVelocity * step
            val filterAcceleration = spec.accelerationFilterStiffness * (acceleration - filteredAcceleration) -
                accelerationFilterDamping * accelerationFilterVelocity
            accelerationFilterVelocity += filterAcceleration * step
            filteredAcceleration += accelerationFilterVelocity * step
            val deformationAcceleration = filteredAcceleration - deformationDamping * deformationVelocity -
                spec.deformationStiffness * deformation
            deformationVelocity += deformationAcceleration * step
            deformation += deformationVelocity * step
        }
        if (!isRunning) reset(targetPositionDp)
    }

    fun reset(positionDp: Float) {
        input = if (positionDp.isFinite()) positionDp.toDouble() else 0.0
        follower = input
        followerVelocity = 0.0
        deformation = 0.0
        deformationVelocity = 0.0
        filteredAcceleration = 0.0
        accelerationFilterVelocity = 0.0
    }
}
