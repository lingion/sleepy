package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 物理弹簧定义, 100% 逐字对齐 Flutter 的 [SpringDescription]。
 *
 * 坐标与时间单位均为标准物理量 (距离: dp, 时间: 秒)。
 */
data class SpringDescription(
    val mass: Double,
    val stiffness: Double,
    val damping: Double,
) {
    companion object {
        /**
         * Flutter `SpringDescription.withDampingRatio`:
         * damping = ratio * 2.0 * sqrt(mass * stiffness)
         */
        fun withDampingRatio(
            mass: Double,
            stiffness: Double,
            ratio: Double = 1.0,
        ): SpringDescription {
            require(mass > 0.0) { "Mass must be positive" }
            require(stiffness > 0.0) { "Stiffness must be positive" }
            require(ratio >= 0.0) { "Damping ratio must be non-negative" }
            return SpringDescription(
                mass = mass,
                stiffness = stiffness,
                damping = ratio * 2.0 * sqrt(mass * stiffness),
            )
        }

        /**
         * Flutter `SpringDescription.withDurationAndBounce`:
         * durationInSeconds = durationMs / 1000.0
         * stiffness = (4 * pi^2 * mass) / durationInSeconds^2
         * dampingRatio = if (bounce > 0) 1.0 - bounce else 1.0 / (bounce + 1.0)
         * damping = dampingRatio * 2.0 * sqrt(mass * stiffness)
         */
        fun withDurationAndBounce(
            durationMs: Long = 500L,
            bounce: Double = 0.0,
        ): SpringDescription {
            require(durationMs > 0) { "Duration must be positive" }
            val durationSec = durationMs / 1000.0
            val mass = 1.0
            val stiffness = (4.0 * PI * PI * mass) / (durationSec * durationSec)
            val dampingRatio = if (bounce > 0.0) (1.0 - bounce) else (1.0 / (bounce + 1.0))
            val damping = dampingRatio * 2.0 * sqrt(mass * stiffness)
            return SpringDescription(mass = mass, stiffness = stiffness, damping = damping)
        }
    }
}

/** 模拟收敛判定公差。 */
data class SpringTolerance(
    val distance: Double = 1e-3,
    val velocity: Double = 1e-3,
) {
    companion object {
        val Default = SpringTolerance()
    }
}

private sealed interface SpringSolution {
    fun x(time: Double): Double
    fun dx(time: Double): Double
}

/** 临界阻尼解 (zeta == 1) */
private class CriticalSolution(
    r: Double,
    c1: Double,
    c2: Double,
) : SpringSolution {
    private val rVal = r
    private val c1Val = c1
    private val c2Val = c2

    override fun x(time: Double): Double = (c1Val + c2Val * time) * exp(rVal * time)

    override fun dx(time: Double): Double {
        val power = exp(rVal * time)
        return c2Val * power + (c1Val + c2Val * time) * rVal * power
    }
}

/** 欠阻尼解 (zeta < 1, 存在振荡) */
private class UnderdampedSolution(
    w: Double,
    r: Double,
    c1: Double,
    c2: Double,
) : SpringSolution {
    private val wVal = w
    private val rVal = r
    private val c1Val = c1
    private val c2Val = c2

    override fun x(time: Double): Double {
        return exp(rVal * time) * (c1Val * cos(wVal * time) + c2Val * sin(wVal * time))
    }

    override fun dx(time: Double): Double {
        val power = exp(rVal * time)
        val cosWt = cos(wVal * time)
        val sinWt = sin(wVal * time)
        return power * ((rVal * c1Val + wVal * c2Val) * cosWt + (rVal * c2Val - wVal * c1Val) * sinWt)
    }
}

/** 过阻尼解 (zeta > 1, 无振荡) */
private class OverdampedSolution(
    r1: Double,
    r2: Double,
    c1: Double,
    c2: Double,
) : SpringSolution {
    private val r1Val = r1
    private val r2Val = r2
    private val c1Val = c1
    private val c2Val = c2

    override fun x(time: Double): Double = c1Val * exp(r1Val * time) + c2Val * exp(r2Val * time)

    override fun dx(time: Double): Double =
        c1Val * r1Val * exp(r1Val * time) + c2Val * r2Val * exp(r2Val * time)
}

/**
 * 阻尼二阶常微分方程解析解器, 100% 对齐 Flutter [SpringSimulation]。
 *
 * m * x''(t) + c * x'(t) + k * (x(t) - end) = 0
 */
class SpringSimulation(
    val spring: SpringDescription,
    val start: Double,
    val end: Double,
    val initialVelocity: Double,
    val tolerance: SpringTolerance = SpringTolerance.Default,
) {
    private val solution: SpringSolution

    init {
        val m = spring.mass
        val k = spring.stiffness
        val c = spring.damping
        val cmk = c * c - 4.0 * m * k

        val x0 = start - end
        val v0 = initialVelocity

        solution = when {
            abs(cmk) < 1e-5 -> {
                val r = -c / (2.0 * m)
                val c1 = x0
                val c2 = v0 - r * c1
                CriticalSolution(r, c1, c2)
            }
            cmk < 0.0 -> {
                val w = sqrt(4.0 * m * k - c * c) / (2.0 * m)
                val r = -c / (2.0 * m)
                val c1 = x0
                val c2 = (v0 - r * c1) / w
                UnderdampedSolution(w, r, c1, c2)
            }
            else -> {
                val d = sqrt(cmk) / (2.0 * m)
                val r = -c / (2.0 * m)
                val r1 = r + d
                val r2 = r - d
                val c2 = (v0 - r1 * x0) / (r2 - r1)
                val c1 = x0 - c2
                OverdampedSolution(r1, r2, c1, c2)
            }
        }
    }

    /** 给定时间 t (秒) 下的位置 (绝对坐标)。 */
    fun x(time: Double): Double = end + solution.x(time)

    /** 给定时间 t (秒) 下的速度 (dp/s)。 */
    fun dx(time: Double): Double = solution.dx(time)

    /** 模拟是否已完全静止于目标位置。 */
    fun isDone(time: Double): Boolean {
        return abs(solution.x(time)) < tolerance.distance && abs(solution.dx(time)) < tolerance.velocity
    }
}

/**
 * FlClash `_Spring` 的 Kotlin 状态封装, 支持每帧动态重定目标而无初动静止顿挫。
 *
 * [value], [target], [velocity], [isRunning] 均由 Compose 状态支持,
 * 确保图形变换层能在 Draw 阶段实时观察并驱动 120 FPS 平滑重绘。
 */
class SpringState(
    initialValue: Float,
    val rest: Double = 1e-3,
) {
    var value: Float by mutableFloatStateOf(initialValue)
        internal set
    var target: Float by mutableFloatStateOf(initialValue)
        internal set
    var velocity: Float by mutableFloatStateOf(0f)
        internal set
    var isRunning: Boolean by mutableStateOf(false)
        internal set

    private var simulation: SpringSimulation? = null
    private var spring: SpringDescription? = null
    private var startSec: Double = 0.0
    private var nowSec: Double = 0.0

    val acceleration: Float
        get() {
            val sp = spring ?: return 0f
            val sim = simulation ?: return 0f
            return ((sp.stiffness * (target - value) - sp.damping * velocity) / sp.mass).toFloat()
        }

    fun springTo(newTarget: Float, newSpring: SpringDescription) {
        if (target == newTarget && spring == newSpring && isRunning) return
        val currentVel = velocity.toDouble()
        val tol = SpringTolerance(
            distance = rest,
            velocity = rest * sqrt(newSpring.stiffness / newSpring.mass),
        )
        simulation = SpringSimulation(
            spring = newSpring,
            start = value.toDouble(),
            end = newTarget.toDouble(),
            initialVelocity = currentVel,
            tolerance = tol,
        )
        spring = newSpring
        target = newTarget
        if (isRunning) {
            // 关键对齐 FLClash: 动画运行中重设目标时，仅将物理基准起点设为上一帧 nowSec
            // 绝不在触摸事件中用 System.nanoTime() 冲刷时间轴，避免 time 被持续清零卡顿
            startSec = nowSec
            return
        }
        nowSec = 0.0
        startSec = 0.0
        isRunning = true
    }

    fun jumpTo(newValue: Float) {
        simulation = null
        value = newValue
        target = newValue
        velocity = 0f
        isRunning = false
    }

    /** 每帧推进物理模拟。dt 为该帧与上一帧的实际物理时间增量 (秒)。 */
    fun tickDelta(dt: Double): Boolean {
        if (!isRunning) return false
        val sim = simulation ?: run {
            isRunning = false
            return false
        }
        nowSec += dt
        val time = max(0.0, nowSec - startSec)
        if (sim.isDone(time)) {
            val changed = value != target || velocity != 0f
            value = target
            velocity = 0f
            simulation = null
            isRunning = false
            return changed
        }
        val prev = value
        value = sim.x(time).toFloat()
        velocity = sim.dx(time).toFloat()
        return prev != value
    }

    /** 兼容单参数连续调用接口 */
    fun tick(currentSec: Double): Boolean {
        return tickDelta(0.016)
    }
}

/**
 * FlClash 导航栏全部弹簧预设 (逐字对齐 `navigation_dock.dart:59-100`)。
 */
object FlClashSprings {
    /** 120ms 弹性追踪过拖 */
    val TrackSpring = SpringDescription.withDurationAndBounce(
        durationMs = 120L,
    )

    /** 按压缩放弹簧 mass=1, k=311, zeta=0.593 */
    val PressSpring = SpringDescription.withDampingRatio(
        mass = 1.0,
        stiffness = 311.0,
        ratio = 0.593,
    )

    /** 跟手移动弹簧 mass=1, k=1024, zeta=0.9 */
    val FollowSpring = SpringDescription.withDampingRatio(
        mass = 1.0,
        stiffness = 1024.0,
        ratio = 0.9,
    )

    /** 镜片升起/落下弹簧 250ms */
    val LiftSpring = SpringDescription.withDurationAndBounce(
        durationMs = 250L,
    )

    /** 镜片换项弹簧 400ms, bounce=0.15 */
    val TravelSpring = SpringDescription.withDurationAndBounce(
        durationMs = 400L,
        bounce = 0.15,
    )

    /** 果冻果冻弹性 mass=1, k=100, zeta=0.4 */
    val JellySpring = SpringDescription.withDampingRatio(
        mass = 1.0,
        stiffness = 100.0,
        ratio = 0.4,
    )

    /** 回弹稳定弹簧 500ms, bounce=0.32 */
    val SettleSpring = SpringDescription.withDurationAndBounce(
        durationMs = 500L,
        bounce = 0.32,
    )

    /** 悬浮感应弹簧 260ms, bounce=0.18 */
    val HoverSpring = SpringDescription.withDurationAndBounce(
        durationMs = 260L,
        bounce = 0.18,
    )

    /** 渐隐淡入弹簧 200ms */
    val FadeSpring = SpringDescription.withDurationAndBounce(
        durationMs = 200L,
    )

    /** 触控光芒渐入弹簧 100ms */
    val GlowInSpring = SpringDescription.withDurationAndBounce(
        durationMs = 100L,
    )

    /** 触控光芒散去弹簧 500ms */
    val GlowOutSpring = SpringDescription.withDurationAndBounce(
        durationMs = 500L,
    )
}

/**
 * FlClash 导航栏动力学与几何公式常量。
 */
object FlClashDockPhysics {
    const val BAR_HEIGHT = 62f
    const val BAR_PADDING = 4f
    const val EDGE_MARGIN = 21f
    const val SHADOW_ROOM = 8f
    const val HIT_SLOP = 8f
    const val MAX_ITEM_EXTENT = 86f
    // iOS 26.5 natural fitting layout keeps a 94pt lens as destinations overlap.
    const val MIN_LENS_WIDTH = 94f
    const val FULL_WIDTH_COUNT = 5
    const val ICON_SIZE = 28f
    const val LABEL_GAP = 2f
    const val LABEL_INSET = 2f
    const val LABEL_SIZE = 10f
    const val LABEL_LEADING = 14.5f
    const val MIN_LABEL_SIZE = 9f
    const val PRESS_GROWTH = 1f / 8f
    const val MAX_PRESS_GROWTH = 16f
    const val LENS_GROWTH = 8f
    const val LENS_MAGNIFY = 0.16f
    const val LIFT_SNAP = 0.975f
    const val LIFT_RELEASE = 0.04f
    const val JELLY_SPEED = 2.4f
    const val JELLY_STRETCH = 0.24f
    const val JELLY_BRAKE = 25f
    const val JELLY_SQUASH = 0.16f
    const val JELLY_ASPECT = 1.35f
    const val JELLY_REST = 3e-3
    const val GLOW_REACH = 28f
    const val GLOW_GROWTH = 3f
    const val GLOW_PEAK = 0.06f
    const val GLOW_WASH = 0.7f
    const val OVERDRAG = 0.35f
    const val PULL_LIMIT = 7f / 32f
    const val PULL_STRETCH = 0.5f
    const val FLING_PROJECTION_SEC = 0.1 // 100ms

    /** 边缘橡皮筋阻尼拉伸公式 `_rubberBand` (navigation_dock.dart:102-105) */
    fun rubberBand(overshoot: Float, limit: Float): Float {
        val pull = 1f - 1f / (abs(overshoot) * 0.55f / limit + 1f)
        return limit * pull * sign(overshoot)
    }

    /** S 型平滑饱和函数 `_saturate` (navigation_dock.dart:107) */
    fun saturate(value: Float): Float {
        return (1.0 - 2.0 / (exp(2.0 * value) + 1.0)).toFloat()
    }

    /** 镜片几何包围盒 `_lensRect` (navigation_dock.dart:110-127) */
    fun lensRect(
        position: Float,
        stretch: Float,
        pitch: Float,
        width: Float,
        height: Float,
        lift: Float,
        stretchY: Float = -stretch * JELLY_ASPECT,
    ): Rect {
        val growth = LENS_GROWTH * 2f * lift
        val lensWidth = (width + growth) * (1f + stretch)
        val lensHeight = (height + growth) * (1f + stretchY)
        return Rect(
            left = (position + 0.5f) * pitch - lensWidth / 2f,
            top = (height - lensHeight) / 2f,
            right = (position + 0.5f) * pitch + lensWidth / 2f,
            bottom = (height + lensHeight) / 2f,
        )
    }

    /** 底栏排版布局计算 `_dockLayout` (navigation_dock.dart:131-145) */
    data class DockLayout(val pitch: Float, val lens: Float, val inset: Float)

    fun dockLayout(span: Float, count: Int): DockLayout {
        if (count <= 1) {
            return DockLayout(pitch = span - BAR_PADDING * 2f, lens = span, inset = BAR_PADDING)
        }
        val pitch = (span - BAR_PADDING * 2f) / count
        if (pitch + BAR_PADDING * 2f >= MIN_LENS_WIDTH) {
            return DockLayout(pitch = pitch, lens = pitch + BAR_PADDING * 2f, inset = BAR_PADDING)
        }
        val lens = min(MIN_LENS_WIDTH, span)
        val closed = (span - lens) / (count - 1)
        return DockLayout(pitch = closed, lens = lens, inset = (lens - closed) / 2f)
    }

    /** 果冻弹性目标计算 `_wobble` (navigation_dock.dart:974-983) */
    fun wobbleTarget(velocity: Float, acceleration: Float): Float {
        val braking = max(0f, -acceleration * sign(velocity))
        return JELLY_STRETCH * saturate(abs(velocity) / JELLY_SPEED) -
            JELLY_SQUASH * saturate(braking / JELLY_BRAKE)
    }

    /** 按压缩放比例计算 `growthOf` (navigation_dock.dart:454-455) */
    fun pressGrowthOf(side: Float, maxGrowth: Float = MAX_PRESS_GROWTH): Float =
        min(PRESS_GROWTH * 2f, maxGrowth / side)
}
