package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 玻璃 Dock 运动状态 — FlClash `_FloatingNavigationBarState` 弹簧组
 * (navigation_dock.dart 772-798) 的 Compose 化。
 *
 * 弹簧映射: 每条 _Spring = 一个 Animatable; springTo = animateTo(自动抢占
 * 重定向 = Flutter `_Spring.springTo` 179-189: 新模拟从当前 value+velocity
 * 起跑)。`Animatable.velocity/target` = `_Spring.velocity/target`(168/166)。
 *
 * 绘制接线: 所有弹簧值由**绘制 lambda 直读**(graphicsLayer/onDraw 读
 * Animatable.value 只订阅重绘不重组 — 与经典分支 thumb 同一机制), 本类不
 * 缓存每帧几何; 仅结构位 [lifted](_GlassLens/_LensContent 存在性, Dart
 * `_noteLift` setState 899-904 + `lensed` 1225) 是可观察状态。
 *
 * 坐标制: pointerInput/hover 手势挂 padding 后的**内容 Row**(内层), 事件 x
 * 即内容列坐标 → [DockPointerMachine] 以 pad=0 换算(与 FlClash 外层 Listener
 * +内部减 _barPadding 等价); [lensRectOuterPx] 平移到外层盒(pane)坐标。
 * px = dp × density。
 *
 * 重建: [DockLayoutKey](span/数量/条高/密度)变化 = Flutter LayoutBuilder 每
 * 帧重读 context.size 的等价(旋转/分屏/字号); 选中变化走 [onSelectedChanged]
 * (didUpdateWidget 824-832)。
 */
class NavDockMotion internal constructor(
    val machine: DockPointerMachine,
    private val scope: CoroutineScope,
    private val onSelection: (Int) -> Unit,
    private val onHaptic: () -> Unit,
    selectedIndex: Int,
    private var restPosition: () -> Float,
    private var restIndex: () -> Int,
) {
    // ══ 弹簧(_Spring 组) ══
    val lens: Animatable<Float, AnimationVector1D> = Animatable(selectedIndex.toFloat())
    var lensTarget = selectedIndex.toFloat()
        private set
    val lift = Animatable(0f)
    var liftTarget = 0f
        private set
    val hover = Animatable(selectedIndex.toFloat())
    var hoverTarget = selectedIndex.toFloat()
        private set
    val hoverShow = Animatable(0f)
    var hoverShowTarget = 0f
        private set
    val swell = Animatable(0f)
    var swellTarget = 0f
        private set
    val stretch = Animatable(0f)
    var stretchTarget = 0f
        private set
    val jelly = Animatable(0f)
    var jellyTarget = 0f
        private set
    val glow = Animatable(0f)
    var glowTarget = 0f
        private set
    val glowSpread = Animatable(0f)
    var glowSpreadTarget = 0f
        private set

    /** = `_lifted`(899-904 setState): lift>0 → _GlassLens/_LensContent 存在。 */
    var lifted by mutableStateOf(false)
        private set

    /** line 797 `_hoverAt`(null=无悬停; 写即触发 _leanContent)。 */
    var hoverAt by mutableStateOf<Float?>(null)
        private set

    /** 项图标视差(_parallax[i], 788+876-897): hoverAt 变化 → 逐项 springTo。 */
    val parallax: List<Animatable<Float, AnimationVector1D>> =
        List(machine.count.coerceAtLeast(1)) { Animatable(0f) }

    // ══ 事件入口(pointerInput 循环直调) ══

    fun onDown(pointerId: Int, xPx: Float, yPx: Float, uptimeMs: Long) {
        dispatch(machine.onDown(pointerId, xPx, yPx, uptimeMs))
    }

    fun onMove(pointerId: Int, xPx: Float, yPx: Float, uptimeMs: Long) {
        dispatch(machine.onMove(pointerId, xPx, yPx, uptimeMs))
    }

    /** 投影速度 = 机器内部 VelocityTracker(down/move 样本; Dart 不把 up 入样 1033-1036)。 */
    fun onUp(pointerId: Int, xPx: Float, yPx: Float, uptimeMs: Long) {
        dispatch(machine.onUp(pointerId, xPx, yPx, uptimeMs))
    }

    fun onCancel(pointerId: Int) = dispatch(machine.onCancel(pointerId))

    /**
     * didUpdateWidget 对位(Dart `widget.selectedIndex` 全通路活读 943/951/1115/
     * 1121/1123): 每次选中变化重灌闭包; remember(key) 不重建也保持新鲜,
     * 否则 release/cancel/leanLens/rest 全部对比冻结的旧选中 → 松手落错座。
     */
    fun bindSelected(index: Int) {
        machine.selectedIndex = { index }
        restPosition = { index.toFloat() }
        restIndex = { index }
    }

    /** line 984-993 `_handleHover`(UI 层滤 PointerKind.Touch 后调)。 */
    fun onHover(xPx: Float) {
        val position = machine.positionAt(xPx)
        val target = DockGlassLogic.hoverLean(position, NavDockSpec.hoverMagnet, machine.lastIndex)
        hoverAt = position // 967
        scope.launch {
            if (hoverShowTarget == 0f && hoverShow.value == 0f) {
                hover.snapTo(target) // 968-972
                hoverTarget = target
            } else {
                hover.animateTo(target, springSpec(DockSprings.hover))
                hoverTarget = target
            }
            hoverShow.animateTo(1f, springSpec(DockSprings.fade))
            hoverShowTarget = 1f
        }
        leanLens()                    // 974
        settleSwell(DockSprings.settle) // 992
    }

    /** line 995-1001 `_handleExit`。 */
    fun onHoverExit() {
        hoverAt = null
        scope.launch {
            hoverShow.animateTo(0f, springSpec(DockSprings.fade))
            hoverShowTarget = 0f
        }
        leanLens()
        settleSwell(DockSprings.settle)
    }

    /** didUpdateWidget 824-832: 外部选中变化且无按压 → lens 归位(travel)。 */
    fun onSelectedChanged(newIndex: Int) {
        syncParallaxTargets()
        val rest = newIndex.toFloat()
        if (machine.pressedIndex == null && lensTarget != rest && lens.value != rest) {
            scope.launch {
                lens.animateTo(rest, springSpec(DockSprings.travel))
                lensTarget = rest
            }
        }
    }

    /** `_leanContent` 886-897: hoverAt 变化重排视差目标(_hoverSpring)。 */
    private fun syncParallaxTargets() {
        val h = hoverAt
        scope.launch {
            parallax.forEachIndexed { index, anim ->
                val offset = h?.minus(index) ?: 0f
                val target = if (kotlin.math.abs(offset) > 0.5f) 0f
                else offset * NavDockSpec.hoverMagnet * NavDockSpec.hoverParallax
                if (target != anim.targetValue) anim.animateTo(target, springSpec(DockSprings.hover))
            }
        }
    }

    init {
        syncParallaxTargets()
    }

    // ══ 每绘制帧几何(px; 读弹簧=订阅重绘) ══

    /**
     * `_lensRect(height=内层 constraints.maxHeight).shift(inset,0)` (1188-1195)
     * 平移到外层盒 = +padPx; [lensRectColumnPx] 列坐标供 clip/items 用。
     */
    fun lensRectColumnPx(pitchPx: Float, lensWPx: Float, innerHPx: Float, insetPx: Float, lensGrowthPx: Float): Rect {
        val r = DockDragLogic.lensRect(
            position = lens.value, stretch = jelly.value,
            pitch = pitchPx, width = lensWPx, height = innerHPx,
            lift = lift.value.coerceIn(0f, 1f), lensGrowth = lensGrowthPx,
            jellyAspect = NavDockSpec.jellyAspect,
        )
        return Rect(insetPx + r.x, r.y, insetPx + r.x + r.width, r.y + r.height)
    }

    /** superellipse 轮廓(AppShape.full.getOuterPath 等价, 与 uCurves 同参)。 */
    fun lensOutlineColumnPx(rectPx: Rect, density: Float): Path =
        LiquidGlassOutline.path(rectPx, minOf(rectPx.width, rectPx.height) / 2f)

    /** _RenderPressTransform._transform(718-730): scale 组 + 中心平移。 */
    fun pressTransform(pitchPx: Float, count: Int, insetPx: Float, padPx: Float, panelHPx: Float): PressTransform {
        val panelW = pitchPx * count + insetPx * 2f + padPx * 2f
        val panelLongest = maxOf(panelW, panelHPx)
        val s = DockGlassLogic.pressSwell(
            swell.value.coerceIn(0f, 1.5f), panelLongest,
            NavDockSpec.pressGrowth, NavDockSpec.maxPressGrowth,
        )
        val pullPx = stretch.value * machine.density
        val sx = DockGlassLogic.pullScale(s, pullPx, panelW, NavDockSpec.pullStretch)
        return PressTransform(scaleX = sx, scaleY = s, pullXPx = pullPx, panelWidthPx = panelW)
    }

    data class PressTransform(val scaleX: Float, val scaleY: Float, val pullXPx: Float, val panelWidthPx: Float) {
        /**
         * 把 bar 节点局部 px 点映射到 root px(= 屏幕位置): Compose graphicsLayer
         * 的变换 = 绕中心缩放 + 平移 pull(Flutter `_transform` 718-730 同式,
         * 但 Compose 的 pivot 已含 c(1−s) 项, 平移只剩 pull)。backdrop 采样
         * pad 必须用**变换后**的屏幕位置 —— atlas 是屏幕内容, 不随本节点变形。
         */
        fun screenRootPx(
            localX: Float,
            localY: Float,
            centerX: Float,
            centerY: Float,
            barRootX: Float,
            barRootY: Float,
        ): androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(
            barRootX + centerX + (localX - centerX) * scaleX + pullXPx,
            barRootY + centerY + (localY - centerY) * scaleY,
        )
    }

    // ══ 命令分发 + 监听器复刻(811-819) ══

    private fun dispatch(cmds: List<DockCommand>) {
        for (c in cmds) when (c) {
            is DockCommand.LensTo -> {
                // Dart `_lens.springTo` 在调用瞬间即置 target(180-182);
                // wobble/acceleration 反解依赖它, 必须同步写, 不能等协程起跑。
                lensTarget = c.position
                // 监听器接线=811-816: `_noteLift` 只挂 `_lift`(816), lens tick
                // 不翻 lifted —— 在 lens tick 里调它会把结构位翻转绑到行程
                // 弹簧节奏上, 每 tick 一次布尔写=每 tick 整行重组(颤抖放大器)。
                spring(lens, c.position, c.spring) {
                    wobble(c.spring); dropLift()
                }
            }
            is DockCommand.LiftTo -> spring(lift, c.value, c.spring) { dropLift(); noteLift() }
            is DockCommand.SwellTo -> spring(swell, c.value, c.spring)
            is DockCommand.StretchTo -> spring(stretch, c.value, c.spring)
            is DockCommand.GlowTo -> spring(glow, c.value, c.spring)
            is DockCommand.GlowSpread -> if (c.spring == null) scope.launch { glowSpread.snapTo(c.value) }
                else spring(glowSpread, c.value, c.spring)
            is DockCommand.Shine -> { /* 绘制帧从 glow/glowSpread.value 现算(= Dart 监听器), 命令仅测试契约 */ }
            DockCommand.HoverHide -> {
                scope.launch { hoverShow.animateTo(0f, springSpec(DockSprings.fade)) } // 1010
                hoverAt = null // 1011
            }
            DockCommand.SelectionHaptic -> onHaptic()          // 1083/1108 selectionClick
            DockCommand.DropGate -> dropLift()
            is DockCommand.Select -> onSelection(c.index)      // 1123 onSelected
        }
    }

    /** line 853-862 `_wobble`: lens.velocity/acceleration(座位制) → jelly。 */
    private fun wobble(spec: DockSpringSpec) {
        val v = lens.velocity
        val a = DockSprings.acceleration(
            DockSprings.Spec(spec.stiffness, spec.dampingRatio),
            target = lensTarget, value = lens.value, velocity = v,
        )
        val target = machine.wobbleTarget(v, a)
        if (target != jellyTarget) { // 859 同 target 去重
            scope.launch {
                jelly.animateTo(target, springSpec(DockSprings.jelly))
                jellyTarget = target
            }
        }
    }

    /** line 907-916 `_dropLift` 复核 → lift→0。 */
    private fun dropLift() {
        val cmds = machine.onSettleTick(lift.value, lens.value)
        for (c in cmds) if (c is DockCommand.LiftTo) spring(lift, c.value, c.spring) { noteLift() }
    }

    /**
     * line 899-904 `_noteLift` 逐字等价: `lift.value > 0`。lift 弹簧 ratio=1
     * 无过冲、animateTo 终值精确=target, 不会出现亚零残值 → lifted 每次
     * 按压/释放各翻转一次(结构位, Dart setState 同款)。勿改成带阈值的
     * 三态/epsilon: 那与 Dart 语义不同, 且会把双拷贝窗口拖长。
     */
    private fun noteLift() {
        val l = lift.value > 0f
        if (l != lifted) lifted = l
    }

    /** line 949-958 `_leanLens`。 */
    private fun leanLens() {
        if (machine.pressedIndex != null || DockDragLogic.indexAt(lensTarget, machine.lastIndex) != restIndex()) return
        val rest = restPosition()
        if (lensTarget != rest) {
            scope.launch {
                lens.animateTo(rest, springSpec(DockSprings.hover))
                lensTarget = rest
            }
        }
    }

    /** line 977-982 `_settleSwell`: 仅无指针。 */
    private fun settleSwell(spec: DockSprings.Spec) {
        val target = if (hoverAt == null) 0f else NavDockSpec.hoverSwell
        if (!machine.pointerActive && swellTarget != target) {
            scope.launch {
                swell.animateTo(target, springSpec(spec))
                swellTarget = target
            }
        }
    }

    private fun spring(anim: Animatable<Float, AnimationVector1D>, to: Float, spec: DockSpringSpec, onTick: () -> Unit = {}) {
        scope.launch {
            anim.animateTo(to, SpringSpec(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness)) {
                if (onTick !== EMPTY) onTick()
            }
            if (onTick !== EMPTY) onTick()
        }
    }

    private fun springSpec(spec: DockSprings.Spec) =
        SpringSpec<Float>(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness)

    companion object {
        private val EMPTY: () -> Unit = {}
    }
}

/** 布局键: 变化即重建运动态(旋转/分屏/字号)。 */
data class DockLayoutKey(
    val spanDp: Float,
    val count: Int,
    val barHeightDp: Float,
    val density: Float,
)

@Composable
fun rememberDockMotion(
    key: DockLayoutKey,
    selectedIndex: Int,
    onSelection: (Int) -> Unit,
    onHaptic: () -> Unit,
    restPosition: () -> Float,
    restIndex: () -> Int,
): NavDockMotion {
    val scope = rememberCoroutineScope()
    return remember(key) {
        NavDockMotion(
            machine = DockPointerMachine(
                count = key.count, selectedIndex = { selectedIndex },
                spanDp = key.spanDp, barPaddingDp = NavDockSpec.barPadding.value,
                minLensDp = NavDockSpec.minLens.value, barHeightDp = key.barHeightDp,
                touchSlopPx = NavDockSpec.touchSlop * key.density, density = key.density,
            ),
            scope = scope,
            selectedIndex = selectedIndex.coerceIn(0, (key.count - 1).coerceAtLeast(0)),
            onSelection = onSelection,
            onHaptic = onHaptic,
            restPosition = restPosition,
            restIndex = restIndex,
        )
    }
}
