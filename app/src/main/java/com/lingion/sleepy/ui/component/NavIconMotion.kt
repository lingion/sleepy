package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dock tab 图标的专属动效键 — 按语义命名, 不按 ordinal 匹配(增删 tab 不串位)。
 */
enum class NavIconMotionKey {
    /** 课表: 选中瞬间竖向压扁回弹(翻页感)。 */
    PageFlip,

    /** 今日: 选中瞬间向上跃起 + 过冲回落。 */
    Hop,

    /** 管理: 选中转 90 度; 按住/拖拽期间持续缓转。 */
    GearSpin,

    /** 我的: 选中果冻式 squash-stretch 弹跳。 */
    Jelly,

    /** 不参与动效(默认, 行为与无动画完全一致)。 */
    None,
}

/** 图标动效变换结果(纯数据, 供脱离 GraphicsLayerScope 断言)。 */
internal data class IconMotionTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
)

/**
 * 图标动作唯一真源: key × (pulse 相位∈[0,1], spin 累计弧度, 图标高 px) → 变换。
 * [NavIconDriver.applyTo] 只是把它写进 GraphicsLayerScope, 动作几何全在这里 ——
 * 纯函数, 可脱离帧时钟/graphicsLayer/协程逐值断言("每个图标各自动作"的契约锁这)。
 *
 * pulse 语义随剧本: PageFlip 剧本 1→0(峰值即起始), Hop/Jelly 剧本 0→1→0
 * (Jelly 用 sin 包络把 0→1→0 转成一压一伸)。静止态 pulse=0 / spin 不动 → 恒等。
 */
internal fun transformFor(
    key: NavIconMotionKey,
    pulse: Float,
    spinRad: Float,
    heightPx: Float,
): IconMotionTransform = when (key) {
    // 翻页压扁: 竖向压、横向补(面积守恒感), p=1 最扁 scaleY=0.78。
    NavIconMotionKey.PageFlip -> IconMotionTransform(
        scaleX = 1f + 0.08f * pulse,
        scaleY = 1f - 0.22f * pulse,
    )

    // 向上跃起: 位移随图标高缩放(28dp 基准), p=1 最高 -9/28·h。
    NavIconMotionKey.Hop -> IconMotionTransform(
        translationY = -9f * (heightPx / 28f) * pulse,
    )

    // 齿轮转: 累计弧度→角度, 不取模(保留圈数, 松开再按方向感连续)。
    NavIconMotionKey.GearSpin -> IconMotionTransform(
        rotationZ = spinRad * (180f / PI.toFloat()),
    )

    // 果冻弹: sin 包络 p∈[0,1] → 0→1→0 单峰, 横向鼓、纵向瘪再回正。
    NavIconMotionKey.Jelly -> {
        val s = sin(pulse * PI.toFloat())
        IconMotionTransform(
            scaleX = 1f + 0.16f * s,
            scaleY = 1f - 0.14f * s,
        )
    }

    NavIconMotionKey.None -> IconMotionTransform()
}

/**
 * 单项图标动效驱动(每 [PillNavItemSpec] 一个实例)。
 *
 * 状态提升到 bar 层的原因:
 * - 玻璃 dock 的图标在透镜内外圈各画一遍(DockGlassBar ItemsSeatColumn 双遍,
 *   Dart 1936-1966 等价)。两遍必须共享同一 driver, 否则内/外圈动画相位分叉,
 *   透镜边缘处图标几何错位。
 * - pop 返回子页时 Main entry 全新组合(2026-09-21 thumb 闪动事故同源场景):
 *   [rememberNavIconDrivers] 重建时按当前选中项初始化 [wasSelected] →
 *   不误触发脉冲, 图标从首帧起钉死原位。
 *
 * 变换经 [graphicsLayer] lambda 直读 Animatable.value: 逐帧只重绘图标层,
 * 不重组座位列 — 与 NavDockMotion 类头"绘制帧直读弹簧值"既有约定一致。
 */
class NavIconDriver internal constructor(internal val key: NavIconMotionKey) {
    /** 上一次生效时本项是否选中 — 边沿检测用(bar 层 effect 写)。 */
    internal var wasSelected by mutableStateOf(false)
        private set

    // 一次性脉冲相位: 语义由各 key 定义(PageFlip 1→0; Hop/Jelly 0→1→0)。
    private val pulse = Animatable(0f)

    // GearSpin 专用: 累计弧度(跨 360 不跳变)。
    private val spin = Animatable(0f)

    internal fun markSelected(selected: Boolean) {
        wasSelected = selected
    }

    /** 部件渲染层逐帧直读用(draw lambda 里读 state → 仅本节点重绘)。 */
    internal fun pulsePhase(): Float = pulse.value

    internal fun spinRadians(): Float = spin.value

    internal suspend fun activate() {
        when (key) {
            NavIconMotionKey.PageFlip,
            NavIconMotionKey.Hop,
            NavIconMotionKey.Jelly -> {
                pulse.snapTo(1f)
                pulse.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }

            NavIconMotionKey.GearSpin -> {
                spin.animateTo(
                    spin.value + PI.toFloat() / 2f,
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }

            NavIconMotionKey.None -> Unit
        }
    }

    /** pressed 期间持续缓转(齿轮); 调用方取消即停, 角度停在当前值。 */
    internal suspend fun runPressLoop() {
        while (true) {
            spin.animateTo(
                spin.value + PI.toFloat() / 6f,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessVeryLow,
                ),
            )
        }
    }

    internal fun applyTo(layer: GraphicsLayerScope) {
        // 动作几何单真源 = transformFor(纯函数, 契约测试直接锁它);
        // 本处只负责把它灌进 GraphicsLayerScope。四属性全显式赋值 →
        // 非该 key 的分量恒回默认(0/1), 不留上一 key 的残值。
        val t = transformFor(key, pulse.value, spin.value, layer.size.height)
        layer.scaleX = t.scaleX
        layer.scaleY = t.scaleY
        layer.translationY = t.translationY
        layer.rotationZ = t.rotationZ
    }
}

/**
 * bar 层共享驱动表。key=items:
 * - 同一形态内跨重组(含拖拽动画期间)保持连续;
 * - 经典↔玻璃形态切换/冷启动/pop 重建 = 新实例, 按 [selectedIndex] 初始化
 *   wasSelected → 首帧不放脉冲。
 */
@Composable
fun rememberNavIconDrivers(
    items: List<PillNavItemSpec>,
    selectedIndex: Int,
): List<NavIconDriver> = remember(items) {
    items.mapIndexed { i, spec ->
        NavIconDriver(spec.motionKey).also { it.markSelected(i == selectedIndex) }
    }
}

/**
 * 选中边沿判定唯一真源: 仅"本次选中 且 上次未选中"放一次脉冲。
 * 抽出为纯函数供契约测试锁定(顺序/边沿本身, 非实现细节)。
 */
internal fun shouldActivate(selectedNow: Boolean, wasSelected: Boolean): Boolean =
    selectedNow && !wasSelected

/**
 * 驱动调度。每个 bar(DockGlassBar / DockNavigationBar)只调一次 —
 * 玻璃双遍绘制共享 drivers 列表但 effects 只挂一份, 防脉冲双触发。
 *
 * @param press 连续按压信号 getter(仅 GearSpin 消费)。契约: lambda 必须
 *   读取 remember 出的**状态对象**(motion.lifted / cellPressed 列表),
 *   禁止捕获重组期快照的原始值 — effect 不随重组重启, 读旧对象才拿得到活值。
 *   玻璃 = motion.lifted(透镜在场即按住); 经典 dock = 本项手势 down/up。
 */
@Composable
fun NavIconDriverEffects(
    drivers: List<NavIconDriver>,
    selectedIndex: Int,
    press: (Int) -> Boolean,
) {
    // 激活动画跑在 bar 组合作用域, 不跑在 LaunchedEffect 子作用域:
    // 快速连切 tab 时 effect 重启会杀掉在飞的旧动画 → 旧图标冻结在
    // 形变中间帧(pulse=部分值)。外部 scope 只随组合销毁取消,
    // 同 driver 重复激活由 animateTo 内部抢占 + activate() 首行 snapTo 归零。
    val actScope = rememberCoroutineScope()
    LaunchedEffect(selectedIndex, drivers) {
        drivers.forEachIndexed { i, d ->
            val sel = i == selectedIndex
            if (shouldActivate(selectedNow = sel, wasSelected = d.wasSelected)) {
                actScope.launch { d.activate() }
            }
            d.markSelected(sel)
        }
    }
    LaunchedEffect(drivers) {
        drivers.forEachIndexed { i, d ->
            if (d.key != NavIconMotionKey.GearSpin) return@forEachIndexed
            launch {
                snapshotFlow { press(i) }.collectLatest { pressed ->
                    // 松开 → collectLatest 取消 runPressLoop, 角度停在当前值。
                    if (pressed) d.runPressLoop()
                }
            }
        }
    }
}

/** 图标层应用动效变换(纯读共享 driver, 无副作用; 双遍两圈用同一实例)。 */
fun Modifier.navIconMotion(driver: NavIconDriver?): Modifier =
    if (driver == null || driver.key == NavIconMotionKey.None) this
    else graphicsLayer { driver.applyTo(this) }

/**
 * 官方形态(NavigationBar/NavigationRail)接线表 — dock 两形态之外唯一的动效入口。
 *
 * 报障根因(2026-11-08): 默认贴底走官方 NavigationBar, 图标经 NavigationTabIcon
 * 绘制, 从未接驱动 → 四个图标一个都不动。本表把"驱动表 + 按压跟踪"一次装配,
 * 贴底/平板 rail 共用同一实例(单一接线真源)。
 */
class OfficialNavIconMotion internal constructor(
    internal val drivers: List<NavIconDriver>,
    private val pressed: Array<MutableState<Boolean>>,
) {
    /** [index] = Tab.ordinal。越界 → null(与无动画逐位一致)。 */
    fun driverFor(index: Int): NavIconDriver? = drivers.getOrNull(index)

    /** 连续按压跟踪(仅 GearSpin 消费); 图标侧经 observePressTracker 观察。 */
    fun pressTrackerFor(index: Int): MutableState<Boolean>? = pressed.getOrNull(index)
}

/**
 * [motionKeys] 按 Tab.ordinal 对齐; [selectedIndex] = 当前选中项下标,
 * 变化即选中边沿(与 dock 同一调度真源 NavIconDriverEffects)。
 * 首组合按当前选中初始化, 防冷启动/pop 重建误放脉冲(同 dock 约定)。
 */
@Composable
fun rememberOfficialNavIconMotion(
    motionKeys: List<NavIconMotionKey>,
    selectedIndex: Int,
): OfficialNavIconMotion {
    // 与 dock 版 rememberNavIconDrivers 同约定: remember 创建时同步初始化
    // wasSelected, 防冷启动/pop 重建误放脉冲(异步 effect 会晚于调度器)。
    val drivers = remember(motionKeys) {
        motionKeys.mapIndexed { i, k -> NavIconDriver(k).also { it.markSelected(i == selectedIndex) } }
    }
    val pressed = remember(motionKeys) { Array(motionKeys.size) { mutableStateOf(false) } }
    NavIconDriverEffects(drivers, selectedIndex) { i -> pressed.getOrNull(i)?.value == true }
    return remember(drivers, pressed) { OfficialNavIconMotion(drivers, pressed) }
}

/**
 * 纯观察按压跟踪 — 不消费任何事件(下游 clickable/capsule 环语义不变),
 * down 置 true / 全部指针抬起或取消置 false。经典分支专用: 既有
 * noRippleClickable 内部自建 InteractionSource、capsuleAwareClick 手写手势
 * 环, 均无法外部订阅按压; 注入回调要改命中判定通路, 违背"只动图标"边界。
 *
 * 已知近似: 观察器挂在整格矩形, 胶囊弧外透明角的死区触摸也会算按下
 * (齿轮多转一瞬, 抬起即停) — 换取零侵入, 可接受。
 */
fun Modifier.observePressTracker(pressed: MutableState<Boolean>): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            try {
                awaitFirstDown(requireUnconsumed = false)
                pressed.value = true
                while (awaitPointerEvent().changes.any { it.pressed }) {
                    // 只观察, 不消费
                }
            } finally {
                pressed.value = false
            }
        }
    }
