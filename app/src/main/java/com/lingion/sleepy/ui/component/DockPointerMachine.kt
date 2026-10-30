package com.lingion.sleepy.ui.component

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 弹簧目标规格(值镜像 [DockSprings.Spec], 机器自持避免装配依赖)。 */
data class DockSpringSpec(val stiffness: Float, val dampingRatio: Float)

/**
 * FlClash `FloatingNavigationBar` 指针状态机 (navigation_dock.dart 1003-1127)
 * 的纯逻辑移植 — 输入带时间戳事件, 输出目标值 + 弹簧规格 + 语义命令。
 * Compose 侧 (PillNavigationBar 玻璃分支) 只负责把每条命令转成一次
 * Animatable.animateTo/snapTo + 触觉 + onSelect; 全部判定在本机, JVM 可测。
 *
 * 坐标制(前提, NavDockPointerMachineTest 锁): 事件 x 用**内容列坐标**。
 * FlClash Listener 挂在 Padding(_barPadding) 外层, `_positionAt` 里减
 * _barPadding; Sleepy 的 handler 挂在 padding 后的内容 Row(内层), 事件
 * 原点即内容列左上 → 本机器 positionAt 固定 pad=0 传入。两种接法等价,
 * 但混用会整体偏 4dp — 改 handler 层级必须同步改这里。
 *
 * 单位: 机器内部统一 **dp**; 事件/速度 px 入参经 density 换算。
 * down 的 `glow.springTo(1)` 瞬态按 glow=1 计(_shine), 与 Dart 首帧一致
 * (1015 弹簧起步, _glow.value 下一 tick 才 >0 — 视觉差 <1 帧, 记为已声明
 * 近似); 其余弹簧语义(springTo 可重入=Animatable.animateTo 抢占)一致。
 */
class DockPointerMachine(
    val count: Int,
    /** D4 fix: lambda reads current selected index live (external tab switches visible).
     *  var — UI 层每次选中变化经 bindSelected 重灌闭包, 对齐 Dart 活读
     *  `widget.selectedIndex`(1115/1121/1123)。闭包冻结旧值 → release 的
     *  cancel 回座/`index != selectedIndex()` 判定失真 = 松手落错座/漏发 Select。 */
    var selectedIndex: () -> Int,
    val spanDp: Float,
    val barPaddingDp: Float,
    val minLensDp: Float,
    val barHeightDp: Float,
    /** = NavDockSpec.touchSlop(dp) * density — 与 Flutter kTouchSlop*ppd 同域。 */
    val touchSlopPx: Float,
    val density: Float,
) {
    /** 内容列弹性几何(dp) — FlClash `_layout` 同源(dockLayout(span=count 列宽))。 */
    val layout = DockDragLogic.dockLayout(spanDp, count, barPaddingDp, minLensDp)
    val lastIndex: Int = (count - 1).coerceAtLeast(0)

    /** Dart `_pointer != null` — _settleSwell(977-982) 仅无指针时动 swell。 */
    val pointerActive: Boolean get() = pointer != null

    /** = Dart `_lens.target` (line 166): fling 投影读它, 非当前值。
     *  springTo 即换 target(_simulation 从当前 value 重新起跑), 本机器不跑
     *  弹簧数值 — 只记 target, value 由渲染层 Animatable 持有。 */
    var lensTarget = selectedIndex().toFloat().coerceIn(0f, lastIndex.toFloat())
        private set

    var pressedIndex: Int? = null
        private set
    var dragging = false
        private set
    private var pressX = 0f
    private var pointer: Int? = null
    private val tracker = DockVelocityTracker()

    /** `_shine`(866-874)输出: 触点 dp + 强度 + 半径(spread 只随弹簧动)。 */
    var glowCenterXDp = 0f
        private set
    var glowCenterYDp = 0f
        private set
    var glowStrength = 0f
        private set
    var glowReachDp = DockSpringConsts.GLOW_REACH
        private set
    val glowWash = DockSpringConsts.GLOW_WASH
    private var glowValue = 0f
    private var glowSpreadValue = 0f

    /** `_dropLift` 挂起态 (907-916): lift 未升满 → 落回推迟到 tick 复核。 */
    private var dropping = false

    /** line 1003-1017 `_handlePointerDown` + 1049-1060 `_press`。 */
    fun onDown(pointerId: Int, xPx: Float, yPx: Float, uptimeMs: Long): List<DockCommand> {
        if (pointer != null) return emptyList() // 1004: 首指后其它指针忽略
        pointer = pointerId
        tracker.reset()
        tracker.addPosition(uptimeMs, xPx)
        // 1013-1015: _glowAt=pos, spread.jumpTo(0), glow.springTo(1) → _shine
        glowCenterXDp = xPx / density
        glowCenterYDp = yPx / density
        glowSpreadValue = 0f
        glowValue = 1f
        shine()
        val position = positionAt(xPx)
        val index = DockDragLogic.indexAt(position, lastIndex)
        pressedIndex = index
        lensTarget = index.toFloat()
        dragging = false
        pressX = xPx
        dropping = false
        return listOf(
            DockCommand.HoverHide, // 1010-1011 hoverShow→0 + hoverAt=null
            DockCommand.SwellTo(1f, DockSpringConsts.SPRING_PRESS), // 1012
            DockCommand.GlowSpread(0f), // 1014 jumpTo
            DockCommand.GlowTo(1f, DockSpringConsts.SPRING_GLOW_IN), // 1015
            DockCommand.Shine(glowStrength, glowReachDp),
            DockCommand.LiftTo(1f, DockSpringConsts.SPRING_LIFT), // 1058
            DockCommand.LensTo(lensTarget, DockSpringConsts.SPRING_TRAVEL), // 1059
        )
    }

    /** line 1019-1026 `_handlePointerMove` → `_shine`(866) + `_slide`(1062-1086)。 */
    fun onMove(pointerId: Int, xPx: Float, yPx: Float, uptimeMs: Long): List<DockCommand> {
        if (pointerId != pointer || pressedIndex == null) return emptyList()
        tracker.addPosition(uptimeMs, xPx)
        glowCenterXDp = xPx / density
        glowCenterYDp = yPx / density
        shine()
        val out = mutableListOf<DockCommand>()
        if (!dragging && abs(xPx - pressX) < touchSlopPx) { // 1067 kTouchSlop
            out += DockCommand.Shine(glowStrength, glowReachDp)
            return out
        }
        if (!dragging) dragging = true
        // 1072-1078: size = 外层 Material 盒(span+2pad × barH) → 内坐标钳位
        // [−pad, span+pad]; limit = shortestSide × _pullLimit(手机恒取 barH)。
        val dxDp = xPx / density
        val overshoot = dxDp - dxDp.coerceIn(-barPaddingDp, spanDp + barPaddingDp)
        val barBoxW = spanDp + barPaddingDp * 2f
        val limit = minOf(barBoxW, barHeightDp) * DockSpringConsts.PULL_LIMIT
        out += DockCommand.Shine(glowStrength, glowReachDp)
        out += DockCommand.StretchTo(
            DockDragLogic.rubberBand(overshoot, limit),
            DockSpringConsts.SPRING_TRACK,
        )
        val position = positionAt(xPx) // 1079
        out += DockCommand.LensTo(position, DockSpringConsts.SPRING_FOLLOW) // 1080
        lensTarget = position // Dart _lens.target 即手指连续位(166 getter)
        val index = DockDragLogic.indexAt(position, lastIndex) // 1081-1085
        if (index != pressedIndex) {
            pressedIndex = index
            out += DockCommand.SelectionHaptic
        }
        return out
    }

    /** line 1028-1047 `_handlePointerEnd`(up 路径: 带 `_fling`)。 */
    fun onUp(pointerId: Int, xPx: Float, yPx: Float, uptimeMs: Long): List<DockCommand> {
        if (pointerId != pointer) return emptyList()
        pointer = null
        // Dart 不把 up 喂进 tracker(1033-1036): getVelocity() 只读 down/move 样本
        val out = mutableListOf<DockCommand>()
        val pressed = pressedIndex
        if (dragging && pressed != null) { // 1091 `_fling` 前置
            val index = DockDragLogic.flingTarget(
                lensTarget = lensTarget,                 // 1103: _lens.target
                velocityX = tracker.velocity(),
                pitch = layout.pitch * density,
                projectionMs = DockSpringConsts.FLING_PROJECTION_MS,
                pressedIndex = pressed,
                lastIndex = lastIndex,
                direction = 1,
            )
            if (index != pressed) { // 1107-1110
                pressedIndex = index
                out += DockCommand.SelectionHaptic
            }
        }
        out += release(commit = true) // 1038 _release(commit: up)
        // 1039-1041: glow 淡出+扩散, stretch 归 0(press 弹簧)
        glowValue = 0f // 目标态(弹簧值由渲染层跑) — shine 报终值
        shine()
        out += DockCommand.GlowTo(0f, DockSpringConsts.SPRING_GLOW_OUT)
        out += DockCommand.GlowSpread(1f, DockSpringConsts.SPRING_GLOW_OUT)
        out += DockCommand.StretchTo(0f, DockSpringConsts.SPRING_PRESS)
        out += DockCommand.SwellTo(0f, DockSpringConsts.SPRING_PRESS) // 1046 触屏无 cursor
        return out
    }

    /** line 1028-1047 cancel 路径: 无 fling, `_release(commit=false)`。 */
    fun onCancel(pointerId: Int): List<DockCommand> {
        if (pointerId != pointer) return emptyList()
        pointer = null
        val out = release(commit = false).toMutableList()
        glowValue = 0f
        shine()
        out += DockCommand.GlowTo(0f, DockSpringConsts.SPRING_GLOW_OUT)
        out += DockCommand.GlowSpread(1f, DockSpringConsts.SPRING_GLOW_OUT)
        out += DockCommand.StretchTo(0f, DockSpringConsts.SPRING_PRESS)
        out += DockCommand.SwellTo(0f, DockSpringConsts.SPRING_PRESS)
        return out
    }

    /**
     * line 1113-1126 `_release`: lens 归 commit?新座:旧座; **onSelected 即时**
     * (1123-1125, 与 lift 落回解耦 —— 只有 lift 的 drop 受 `_dropLift` 门控)。
     */
    private fun release(commit: Boolean): List<DockCommand> {
        val index = pressedIndex ?: return emptyList()
        pressedIndex = null
        dragging = false
        dropping = true
        val target = if (commit) index.toFloat() else selectedIndex().toFloat()
        lensTarget = target
        val out = mutableListOf<DockCommand>()
        out += DockCommand.LensTo(target, DockSpringConsts.SPRING_TRAVEL)
        out += DockCommand.DropGate
        if (commit && index != selectedIndex()) out += DockCommand.Select(index) // 1123
        return out
    }

    /**
     * line 907-916 `_dropLift` 复核(渲染层在 lift/lens 弹簧活动期每 tick 调):
     * dropping && 无指针 && lift≥_liftSnap && |lensTarget−lensValue|≤_liftRelease
     * → lift→0(_liftSpring)。UIKit 语义: 色块到站前托盘不落(Select 已即时发)。
     */
    fun onSettleTick(liftValue: Float, lensValue: Float): List<DockCommand> {
        if (!dropping || pointer != null || liftValue < DockSpringConsts.LIFT_SNAP) return emptyList()
        if (abs(lensTarget - lensValue) > DockSpringConsts.LIFT_RELEASE) return emptyList()
        dropping = false
        return listOf(DockCommand.LiftTo(0f, DockSpringConsts.SPRING_LIFT))
    }

    /** line 853-862 `_wobble`: lens 弹簧速度/加速度(**座位/s、座位/s²** —
     *  Dart `_lens.velocity/_acceleration` 即 position 制, jellySpeed=3 座/s)
     *  → 果冻目标。 */
    fun wobbleTarget(lensVelocitySeatsPerS: Float, lensAccelerationSeatsPerS2: Float): Float {
        return DockGlassLogic.jellyTarget(
            velocity = lensVelocitySeatsPerS,
            acceleration = lensAccelerationSeatsPerS2,
            jellySpeed = DockSpringConsts.JELLY_SPEED,
            jellyStretch = DockSpringConsts.JELLY_STRETCH,
            jellyBrake = DockSpringConsts.JELLY_BRAKE,
            jellySquash = DockSpringConsts.JELLY_SQUASH,
        )
    }

    /** px → 连续座位坐标(内容列原点, pad 已含在 handler 层级 → 传 0)。 */
    fun positionAt(xPx: Float): Float =
        DockDragLogic.positionAt(xPx / density, layout, 0f, lastIndex, DockSpringConsts.OVERDRAG)

    /**
     * 渲染层弹簧 tick 用 (Dart _glow/_glowSpread 监听器每 tick 重跑 _shine):
     * 给定 glow/spread 弹簧现值 → (strength, reachDp); 触点中心取机器态。
     */
    fun shineAt(glow: Float, spread: Float): DockCommand.Shine {
        glowValue = glow
        glowSpreadValue = spread
        shine()
        return DockCommand.Shine(glowStrength, glowReachDp)
    }

    /** up 时刻指针速度查询(px/s, 供 UI 层需要时复核 fling 输入)。 */
    fun velocityXPxPerS(): Float = tracker.velocity()

    /** line 866-874 `_shine`(glow 弹簧起步即 1 → 强度=peak 的 down 首帧语义)。 */
    private fun shine() {
        glowStrength = DockGlassLogic.glowStrength(glowValue, DockSpringConsts.GLOW_PEAK)
        glowReachDp = DockGlassLogic.glowReach(
            DockSpringConsts.GLOW_REACH, glowSpreadValue, DockSpringConsts.GLOW_GROWTH,
        )
    }
}

/** 状态机命令 — 渲染层逐条映射到 Animatable/触觉/回调; 顺序即发射顺序。 */
sealed interface DockCommand {
    data class LensTo(val position: Float, val spring: DockSpringSpec) : DockCommand
    data class LiftTo(val value: Float, val spring: DockSpringSpec) : DockCommand
    data class SwellTo(val value: Float, val spring: DockSpringSpec) : DockCommand
    data class StretchTo(val value: Float, val spring: DockSpringSpec) : DockCommand
    data class GlowTo(val value: Float, val spring: DockSpringSpec) : DockCommand
    /** 无 spring = jumpTo(0)(Dart 1014); 带 spring = springTo(Dart 1040)。 */
    data class GlowSpread(val value: Float, val spring: DockSpringSpec? = null) : DockCommand
    /** 立即刷新 uGlow(strength=peak·g², reach·wash)。 */
    data class Shine(val strength: Float, val reachDp: Float) : DockCommand
    data object SelectionHaptic : DockCommand
    data object HoverHide : DockCommand
    /** 落回门控已置起: 渲染层开始每 tick 调 onSettleTick。 */
    data object DropGate : DockCommand
    data class Select(val index: Int) : DockCommand
}

/** navigation_dock.dart 头常量镜像(逻辑 dp 域; 弹簧值转译自 DockSprings)。 */
object DockSpringConsts {
    val GLOW_REACH = NavDockSpec.glowReach
    val GLOW_GROWTH = NavDockSpec.glowGrowth
    val GLOW_PEAK = NavDockSpec.glowPeak
    val GLOW_WASH = NavDockSpec.glowWash
    val PULL_LIMIT = NavDockSpec.pullLimit
    val OVERDRAG = NavDockSpec.overdrag
    val FLING_PROJECTION_MS = NavDockSpec.flingProjectionMs
    val LIFT_SNAP = NavDockSpec.liftSnap
    val LIFT_RELEASE = NavDockSpec.liftRelease
    val JELLY_SPEED = NavDockSpec.jellySpeed
    val JELLY_STRETCH = NavDockSpec.jellyStretch
    val JELLY_BRAKE = NavDockSpec.jellyBrake
    val JELLY_SQUASH = NavDockSpec.jellySquash

    val SPRING_TRACK = DockSpringSpec(DockSprings.track.stiffness, DockSprings.track.dampingRatio)
    val SPRING_PRESS = DockSpringSpec(DockSprings.press.stiffness, DockSprings.press.dampingRatio)
    val SPRING_FOLLOW = DockSpringSpec(DockSprings.follow.stiffness, DockSprings.follow.dampingRatio)
    val SPRING_LIFT = DockSpringSpec(DockSprings.lift.stiffness, DockSprings.lift.dampingRatio)
    val SPRING_TRAVEL = DockSpringSpec(DockSprings.travel.stiffness, DockSprings.travel.dampingRatio)
    val SPRING_JELLY = DockSpringSpec(DockSprings.jelly.stiffness, DockSprings.jelly.dampingRatio)
    val SPRING_GLOW_IN = DockSpringSpec(DockSprings.glowIn.stiffness, DockSprings.glowIn.dampingRatio)
    val SPRING_GLOW_OUT = DockSpringSpec(DockSprings.glowOut.stiffness, DockSprings.glowOut.dampingRatio)
}

/**
 * Flutter `VelocityTracker`(velocity_tracker.dart) 的纵向简化移植:
 * 保留最近 20 样本, 最小二乘拟合 position~time 斜率 = px/s。Flutter 对
 * ≥5 样本同样走线性回归(_LinearFitEstimator), 短样本(2-4)用一阶差分 —
 * 差异只影响投影的小数部分, 落点被 round + pressed±1 钳位吸收
 * (NavDockPointerMachineTest 的 fling 用例锁行为等价)。
 */
class DockVelocityTracker(private val maxSamples: Int = 20) {
    private val times = ArrayDeque<Long>()
    private val xs = ArrayDeque<Float>()

    fun addPosition(uptimeMs: Long, xPx: Float) {
        times.addLast(uptimeMs)
        xs.addLast(xPx)
        while (times.size > maxSamples) { times.removeFirst(); xs.removeFirst() }
    }

    /** px/s; 样本 <2 或零跨度 → 0。 */
    fun velocity(): Float {
        val n = times.size
        if (n < 2) return 0f
        val t0 = times.first()
        var sumT = 0.0; var sumP = 0.0; var sumTP = 0.0; var sumT2 = 0.0
        for (i in 0 until n) {
            val t = (times[i] - t0) / 1000.0
            val p = xs[i].toDouble()
            sumT += t; sumP += p; sumTP += t * p; sumT2 += t * t
        }
        val denom = sumT2 - sumT * sumT / n
        if (denom == 0.0) return 0f
        return ((sumTP - sumT * sumP / n) / denom).toFloat()
    }

    fun reset() { times.clear(); xs.clear() }
}
