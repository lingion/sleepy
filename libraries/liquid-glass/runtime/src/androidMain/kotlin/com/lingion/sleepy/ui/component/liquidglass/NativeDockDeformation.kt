package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Dock deformation keeps a single physical state across pointer modes.
 *
 * Direct selection coefficients fit the native three/four-item hold/tap traces;
 * four later taps and two short-travel taps were held out through 1.5 seconds.
 * They are an empirical approximation, not Apple constants. Direct commands have
 * bounded displacement energy, and actual lift descent adds a release force;
 * the drag mode retains [NativeGlassDragDeformation.Spec.Dock] verbatim.
 */
class NativeDockDeformation(initialPositionDp: Float = 0f) {
    private enum class Mode { Direct, Drag }

    private companion object {
        val Direct = NativeGlassDragDeformation.Spec(
            followerStiffness = 85.52391045,
            followerDampingRatio = .6543508812,
            accelerationFilterStiffness = 115.2254398,
            deformationStiffness = 241.6412428,
            deformationDampingRatio = .4429609780,
            positiveGain = .01303330833,
            negativeGain = .01543798388,
            positiveXAspect = 1.288556988,
            negativeXAspect = 1.288556988,
        )
        const val DirectCommandLimitDp = 236.0425928
        const val DirectReleaseForce = .3792058913
        const val DirectAbsoluteAspect = -.1432354008
        const val DirectLiftAspect = .03738114821
        const val DirectAbsoluteLiftAspect = -.09490683115
        const val DirectVelocityAspect = -.000608254099
        const val DirectSpeedAspect = -.006529362751
    }

    private var mode = Mode.Direct
    private val spec get() = if (mode == Mode.Direct) Direct else NativeGlassDragDeformation.Spec.Dock
    private var follower = initialPositionDp.toDouble()
    private var followerVelocity = 0.0
    private var filteredAcceleration = 0.0
    private var accelerationFilterVelocity = 0.0
    private var deformation = 0.0
    private var deformationVelocity = 0.0
    private var command = follower
    private var lastPhysicalTarget = follower
    private var currentLift = 0f
    private var currentLiftVelocity = 0.0
    private var directCommandDisplacement = 0.0
    private var verticalResidual = 0.0
    private var verticalResidualVelocity = 0.0

    private val gain get() = if (deformation >= 0.0) spec.positiveGain else spec.negativeGain
    val x get() = (gain * deformation).coerceIn(-.22, .22).toFloat()
    val velocity get() = (gain * deformationVelocity).toFloat()
    val isRunning: Boolean
        get() = abs(command - follower) > .001 || abs(followerVelocity) > .01 ||
            abs(filteredAcceleration) > .01 || abs(accelerationFilterVelocity) > .1 ||
            abs(gain * deformation) > .0001 || abs(gain * deformationVelocity) > .0005 ||
            abs(verticalResidual) > .0001 || abs(verticalResidualVelocity) > .0005

    fun verticalDeformation(horizontal: Float): Float =
        (rawVerticalDeformation(horizontal) + verticalResidual).toFloat()

    private fun rawVerticalDeformation(horizontal: Float): Double {
        if (mode == Mode.Direct) {
            // The native vertical response retains a small phase offset as X
            // crosses zero. A symmetric velocity gain keeps this readout
            // continuous through the signed horizontal-gain handoff.
            val speed = deformationVelocity * (Direct.positiveGain + Direct.negativeGain) * .5
            return (-horizontal * (Direct.positiveXAspect + DirectLiftAspect * currentLift) +
                abs(horizontal) * (DirectAbsoluteAspect + DirectAbsoluteLiftAspect * currentLift) +
                speed * DirectVelocityAspect + abs(speed) * DirectSpeedAspect)
        }
        val aspect = if (horizontal >= 0f) spec.positiveXAspect else spec.negativeXAspect
        return -horizontal * aspect
    }

    val verticalVelocity: Float
        get() = (rawVerticalVelocity() + verticalResidualVelocity).toFloat()

    private val releaseForce: Double
        get() = if (mode == Mode.Direct) {
            DirectReleaseForce * directCommandDisplacement * min(0.0, currentLiftVelocity)
        } else 0.0

    private fun rawVerticalVelocity(): Double {
        val horizontal = x.toDouble()
        val dx = if (abs(gain * deformation) >= .22) 0.0 else gain * deformationVelocity
        if (mode == Mode.Drag) {
            return -dx * if (horizontal >= 0.0) spec.positiveXAspect else spec.negativeXAspect
        }
        val sign = if (horizontal >= 0.0) 1.0 else -1.0
        val symmetricGain = (Direct.positiveGain + Direct.negativeGain) * .5
        val speed = deformationVelocity * symmetricGain
        val acceleration = filteredAcceleration + releaseForce -
            2.0 * Direct.deformationDampingRatio * sqrt(Direct.deformationStiffness) * deformationVelocity -
            Direct.deformationStiffness * deformation
        val speedDerivative = symmetricGain * acceleration
        return -dx * (Direct.positiveXAspect + DirectLiftAspect * currentLift) -
            horizontal * DirectLiftAspect * currentLiftVelocity +
            sign * dx * (DirectAbsoluteAspect + DirectAbsoluteLiftAspect * currentLift) +
            abs(horizontal) * DirectAbsoluteLiftAspect * currentLiftVelocity +
            speedDerivative * DirectVelocityAspect +
            (if (speed >= 0.0) 1.0 else -1.0) * speedDerivative * DirectSpeedAspect
    }

    /** A new direct press may interrupt a previous drop without erasing its velocity. */
    fun beginDirect() {
        directCommandDisplacement = 0.0
        changeMode(Mode.Direct)
    }

    private fun changeMode(next: Mode) {
        if (mode == next) return
        val oldVertical = rawVerticalDeformation(x) + verticalResidual
        val oldVerticalVelocity = rawVerticalVelocity() + verticalResidualVelocity
        val oldGain = gain
        mode = next
        // The readout gain changes between fitted models. Re-express the same
        // displacement/momentum instead of jumping or restarting the jelly.
        val ratio = oldGain / gain
        deformation *= ratio
        deformationVelocity *= ratio
        // Both physical modes retain their fitted vertical readout. Carry only
        // their difference as a critically damped residual so the rendered Y
        // and its velocity remain continuous across an interrupted gesture.
        verticalResidual = oldVertical - rawVerticalDeformation(x)
        verticalResidualVelocity = oldVerticalVelocity - rawVerticalVelocity()
    }

    fun advance(seconds: Double, targetPositionDp: Float, dragging: Boolean, lift: Float) {
        if (!seconds.isFinite() || seconds < 0.0 || !targetPositionDp.isFinite()) return
        val nextLift = lift.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        if (dragging) changeMode(Mode.Drag)
        currentLiftVelocity = if (seconds > 0.0) (nextLift - currentLift) / seconds else currentLiftVelocity
        currentLift = nextLift
        if (seconds > 1.0) {
            reset(targetPositionDp)
            return
        }
        val target = targetPositionDp.toDouble()
        val displacement = target - lastPhysicalTarget
        if (mode == Mode.Direct) {
            val boundedDisplacement = DirectCommandLimitDp * tanh(displacement / DirectCommandLimitDp)
            command += boundedDisplacement
            if (displacement != 0.0) directCommandDisplacement = boundedDisplacement
        } else command += displacement
        lastPhysicalTarget = target
        // Establish the input at the new frame origin without advancing physics.
        // isRunning must already expose this pulse when the loop's first dt is zero.
        if (seconds == 0.0) return

        val parameters = spec
        val followerDamping = 2.0 * parameters.followerDampingRatio * sqrt(parameters.followerStiffness)
        val accelerationFilterDamping = 2.0 * sqrt(parameters.accelerationFilterStiffness)
        val deformationDamping = 2.0 * parameters.deformationDampingRatio * sqrt(parameters.deformationStiffness)
        // This force follows measured lift descent; it has no elapsed-time gate
        // and never restarts the stored displacement or velocity on release.
        val currentReleaseForce = releaseForce
        val count = ceil(seconds * 240.0).toInt().coerceAtLeast(1)
        val step = seconds / count
        repeat(count) {
            val acceleration = parameters.followerStiffness * (command - follower) - followerDamping * followerVelocity
            followerVelocity += acceleration * step
            follower += followerVelocity * step
            val filterAcceleration = parameters.accelerationFilterStiffness * (acceleration - filteredAcceleration) -
                accelerationFilterDamping * accelerationFilterVelocity
            accelerationFilterVelocity += filterAcceleration * step
            filteredAcceleration += accelerationFilterVelocity * step
            val deformationAcceleration = filteredAcceleration + currentReleaseForce - deformationDamping * deformationVelocity -
                parameters.deformationStiffness * deformation
            deformationVelocity += deformationAcceleration * step
            deformation += deformationVelocity * step
        }
        val frequency = sqrt(parameters.deformationStiffness)
        val slope = verticalResidualVelocity + frequency * verticalResidual
        val decay = exp(-frequency * seconds)
        verticalResidual = (verticalResidual + slope * seconds) * decay
        verticalResidualVelocity = (verticalResidualVelocity - frequency * slope * seconds) * decay
        if (!isRunning) reset(targetPositionDp)
    }

    fun reset(positionDp: Float) {
        val position = if (positionDp.isFinite()) positionDp.toDouble() else 0.0
        command = position
        lastPhysicalTarget = position
        follower = position
        followerVelocity = 0.0
        filteredAcceleration = 0.0
        accelerationFilterVelocity = 0.0
        deformation = 0.0
        deformationVelocity = 0.0
        directCommandDisplacement = 0.0
        currentLiftVelocity = 0.0
        verticalResidual = 0.0
        verticalResidualVelocity = 0.0
    }
}
