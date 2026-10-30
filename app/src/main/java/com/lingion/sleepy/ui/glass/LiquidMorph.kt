package com.lingion.sleepy.ui.glass

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

enum class LiquidMorphDirection { Opening, Closing }

enum class LiquidMorphPhase {
    Idle,
    Preparing,
    Opening,
    Open,
    Closing,
    Released
}

data class LiquidMorphSessionToken(
    val routeKey: String,
    val generation: Long
)

enum class LiquidMorphResourceLifetime {
    /** Clip, RenderEffect and Offscreen layers that must not survive a stable Open frame. */
    Movement,

    /** Route/session resources that remain owned until close, cancellation or replacement. */
    Session
}

data class LiquidMorphControllerState(
    val token: LiquidMorphSessionToken? = null,
    val phase: LiquidMorphPhase = LiquidMorphPhase.Idle,
    val cleanupGeneration: Long = 0
) {
    val keepsTemporaryLayers: Boolean
        get() = phase == LiquidMorphPhase.Preparing ||
            phase == LiquidMorphPhase.Opening ||
            phase == LiquidMorphPhase.Closing

    val contentInteractive: Boolean get() = phase == LiquidMorphPhase.Open
}

/** One generation owns one set of cleanup actions; every terminal path releases it once. */
@Stable
class LiquidMorphController {
    var state by mutableStateOf(LiquidMorphControllerState())
        private set

    private var nextGeneration = 0L
    private data class CleanupRegistration(
        val lifetime: LiquidMorphResourceLifetime,
        val cleanup: () -> Unit
    )

    private val cleanupByGeneration =
        linkedMapOf<Long, LinkedHashMap<String, CleanupRegistration>>()
    private var latestReleasedGeneration = 0L

    fun prepare(routeKey: String): LiquidMorphSessionToken {
        state.token?.let(::releaseGeneration)
        val token = LiquidMorphSessionToken(routeKey, ++nextGeneration)
        state = LiquidMorphControllerState(
            token = token,
            phase = LiquidMorphPhase.Preparing,
            cleanupGeneration = state.cleanupGeneration
        )
        return token
    }

    fun registerCleanup(
        token: LiquidMorphSessionToken,
        resourceKey: String,
        lifetime: LiquidMorphResourceLifetime = LiquidMorphResourceLifetime.Session,
        cleanup: () -> Unit
    ): Boolean {
        if (!owns(token) || token.generation <= latestReleasedGeneration) return false
        val cleanups = cleanupByGeneration.getOrPut(token.generation) { linkedMapOf() }
        if (resourceKey in cleanups) return false
        cleanups[resourceKey] = CleanupRegistration(lifetime, cleanup)
        return true
    }

    fun startOpening(token: LiquidMorphSessionToken): Boolean =
        move(token, setOf(LiquidMorphPhase.Preparing, LiquidMorphPhase.Closing), LiquidMorphPhase.Opening)

    fun finishOpening(token: LiquidMorphSessionToken): Boolean {
        if (!move(token, setOf(LiquidMorphPhase.Opening), LiquidMorphPhase.Open)) return false
        releaseResources(token, LiquidMorphResourceLifetime.Movement)
        return true
    }

    /** Supports immediate-back from Preparing and Opening without inventing another state. */
    fun startClosing(token: LiquidMorphSessionToken): Boolean = move(
        token,
        setOf(LiquidMorphPhase.Preparing, LiquidMorphPhase.Opening, LiquidMorphPhase.Open),
        LiquidMorphPhase.Closing
    )

    fun finishClosing(token: LiquidMorphSessionToken): Boolean {
        if (!move(token, setOf(LiquidMorphPhase.Closing), LiquidMorphPhase.Released)) return false
        releaseGeneration(token)
        return true
    }

    fun cancel(token: LiquidMorphSessionToken): Boolean {
        if (!owns(token) || state.phase == LiquidMorphPhase.Released) return false
        state = state.copy(phase = LiquidMorphPhase.Released)
        releaseGeneration(token)
        return true
    }

    /** Rotation/process-host recreation ends the old layer generation before preparing a new one. */
    fun replaceForConfiguration(token: LiquidMorphSessionToken): LiquidMorphSessionToken? {
        if (!owns(token)) return null
        val routeKey = token.routeKey
        cancel(token)
        return prepare(routeKey)
    }

    fun reset(): Boolean {
        if (state.phase != LiquidMorphPhase.Released) return false
        state = LiquidMorphControllerState(cleanupGeneration = state.cleanupGeneration)
        return true
    }

    private fun move(
        token: LiquidMorphSessionToken,
        allowed: Set<LiquidMorphPhase>,
        next: LiquidMorphPhase
    ): Boolean {
        if (!owns(token) || state.phase !in allowed) return false
        state = state.copy(phase = next)
        return true
    }

    private fun owns(token: LiquidMorphSessionToken): Boolean = state.token == token

    private fun releaseGeneration(token: LiquidMorphSessionToken) {
        if (token.generation <= latestReleasedGeneration) return
        latestReleasedGeneration = token.generation
        releaseResources(token, lifetime = null)
        if (state.token == token) {
            state = state.copy(cleanupGeneration = state.cleanupGeneration + 1)
        }
    }

    private fun releaseResources(
        token: LiquidMorphSessionToken,
        lifetime: LiquidMorphResourceLifetime?
    ) {
        val cleanups = cleanupByGeneration[token.generation] ?: return
        val iterator = cleanups.iterator()
        while (iterator.hasNext()) {
            val (_, registration) = iterator.next()
            if (lifetime == null || registration.lifetime == lifetime) {
                iterator.remove()
                // One faulty release hook must not strand the remaining clip/layer resources.
                runCatching(registration.cleanup)
            }
        }
        if (cleanups.isEmpty()) cleanupByGeneration.remove(token.generation)
    }
}

/** Keeps accepted legacy phase enums in lockstep while routes migrate one renderer at a time. */
class LiquidMorphControllerBridge(
    val controller: LiquidMorphController = LiquidMorphController(),
    private val routeKey: () -> String
) {
    private var token: LiquidMorphSessionToken? = null

    fun synchronize(phase: LiquidMorphPhase) {
        when (phase) {
            LiquidMorphPhase.Idle -> {
                token?.let { active ->
                    if (controller.state.phase != LiquidMorphPhase.Released) controller.cancel(active)
                }
                if (controller.state.phase == LiquidMorphPhase.Released) controller.reset()
                token = null
            }

            LiquidMorphPhase.Preparing -> token = controller.prepare(routeKey())
            LiquidMorphPhase.Opening -> {
                val active = token ?: controller.prepare(routeKey()).also { token = it }
                controller.startOpening(active)
            }

            LiquidMorphPhase.Open -> {
                val active = token ?: return
                if (controller.state.phase == LiquidMorphPhase.Preparing) controller.startOpening(active)
                controller.finishOpening(active)
            }

            LiquidMorphPhase.Closing -> token?.let(controller::startClosing)
            LiquidMorphPhase.Released -> token?.let { active ->
                if (!controller.finishClosing(active)) controller.cancel(active)
            }
        }
    }
}

data class LiquidMorphInput(
    val source: Rect,
    val target: Rect,
    val rawProgress: Float,
    val direction: LiquidMorphDirection,
    /** Source-to-target tangent of the accepted route at this progress, when available. */
    val trajectoryTangentAngleRadians: Float? = null,
    val backdropScale: Float = 1f,
    val backdropBlurPx: Float = 0f,
    val useCachedBackdrop: Boolean = false
)

data class LiquidProgressKinematics(
    val progress: Float,
    val velocity: Float,
    val acceleration: Float
)

data class LiquidMotionSample(
    val trajectory: LiquidProgressKinematics,
    val shape: LiquidProgressKinematics
)

data class LiquidContentHandoffFrame(
    val sourceAlpha: Float,
    val destinationSurfaceAlpha: Float,
    val destinationContentAlpha: Float,
    val sourceBlurPx: Float = 0f,
    val destinationBlurPx: Float = 0f,
    val destinationMounted: Boolean = true,
    val destinationInteractive: Boolean = false
)

/** Preloaded forms crossfade on the raw clock, independently of the fast geometry easing.
 * The same progress on return preserves alpha when an opening is interrupted, which is also what
 * keeps a mid-flight reversal continuous. The liquid window sits earlier than the native one. */
internal fun liquidPreloadedContentAlpha(
    rawProgress: Float, earlyContentHandoff: Boolean = false, expansionProgress: Float = rawProgress
): Float {
    // Liquid content changes with the space available in the shell, rather than elapsed time.
    // The very small source keeps its own text; the form is readable by 62% expansion.
    val start = if (earlyContentHandoff) 0.24f else 0.35f
    val end = if (earlyContentHandoff) 0.62f else 0.70f
    val progress = if (earlyContentHandoff) expansionProgress else rawProgress
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

data class LiquidBackdropDepthFrame(
    val scale: Float = 1f,
    val blurPx: Float = 0f,
    val useCachedScene: Boolean = false
)

data class LiquidLayerLifecycleFrame(
    val keepMorphClip: Boolean,
    val keepOffscreenLayer: Boolean,
    val prewarmRequired: Boolean
)

/** Motion-only deformation; content layout remains at its true measured size. */
data class LiquidDeformationFrame(
    val tangentAngleRadians: Float = 0f,
    val tangentStretch: Float = 0f,
    val crossAxisSqueeze: Float = 0f,
    val tailLag: Float = 0f,
    val rebound: Float = 0f
) {
    companion object {
        val None = LiquidDeformationFrame()
    }
}

data class LiquidMorphFrame(
    val rect: Rect,
    val cornerRadiusPx: Float,
    val trajectoryProgress: Float,
    val shapeProgress: Float,
    val motion: LiquidMotionSample,
    val content: LiquidContentHandoffFrame,
    val backdropDepth: LiquidBackdropDepthFrame,
    val layerLifecycle: LiquidLayerLifecycleFrame,
    val sourceScale: Float = 1f,
    val deformation: LiquidDeformationFrame = LiquidDeformationFrame.None
)

interface LiquidMorphSpec {
    val routeKey: String
    fun frame(input: LiquidMorphInput): LiquidMorphFrame
}

fun interface LiquidMotionSpec {
    fun sample(input: LiquidMorphInput): LiquidMotionSample
}

fun interface LiquidTrajectorySpec {
    fun rect(input: LiquidMorphInput, motion: LiquidMotionSample): Rect
}

fun interface LiquidShapeSpec {
    fun cornerRadiusPx(
        input: LiquidMorphInput,
        motion: LiquidMotionSample,
        rect: Rect
    ): Float
}

fun interface LiquidContentHandoffSpec {
    fun sample(input: LiquidMorphInput, motion: LiquidMotionSample): LiquidContentHandoffFrame
}

fun interface LiquidBackdropDepthSpec {
    fun sample(input: LiquidMorphInput, motion: LiquidMotionSample): LiquidBackdropDepthFrame
}

fun interface LiquidLayerLifecycleSpec {
    fun sample(input: LiquidMorphInput, motion: LiquidMotionSample): LiquidLayerLifecycleFrame
}

fun interface LiquidDeformationSpec {
    fun sample(input: LiquidMorphInput, motion: LiquidMotionSample): LiquidDeformationFrame
}

val NoLiquidDeformationSpec = LiquidDeformationSpec { _, _ -> LiquidDeformationFrame.None }

data class SegmentedLiquidMorphSpec(
    override val routeKey: String,
    val motion: LiquidMotionSpec,
    val trajectory: LiquidTrajectorySpec,
    val shape: LiquidShapeSpec,
    val contentHandoff: LiquidContentHandoffSpec,
    val backdropDepth: LiquidBackdropDepthSpec,
    val layerLifecycle: LiquidLayerLifecycleSpec,
    val deformation: LiquidDeformationSpec = NoLiquidDeformationSpec
) : LiquidMorphSpec {
    override fun frame(input: LiquidMorphInput): LiquidMorphFrame {
        val motionFrame = motion.sample(input)
        val rect = trajectory.rect(input, motionFrame)
        return LiquidMorphFrame(
            rect = rect,
            cornerRadiusPx = shape.cornerRadiusPx(input, motionFrame, rect),
            trajectoryProgress = motionFrame.trajectory.progress,
            shapeProgress = motionFrame.shape.progress,
            motion = motionFrame,
            content = contentHandoff.sample(input, motionFrame),
            backdropDepth = backdropDepth.sample(input, motionFrame),
            layerLifecycle = layerLifecycle.sample(input, motionFrame),
            deformation = deformation.sample(input, motionFrame)
        )
    }
}

val LinearLiquidMotionSpec = LiquidMotionSpec { input ->
    val progress = input.rawProgress.coerceIn(0f, 1f)
    val kinematics = LiquidProgressKinematics(progress, velocity = 1f, acceleration = 0f)
    LiquidMotionSample(trajectory = kinematics, shape = kinematics)
}
