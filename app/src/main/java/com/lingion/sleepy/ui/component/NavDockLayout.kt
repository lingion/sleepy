package com.lingion.sleepy.ui.component

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Dock 弹性布局结果 — FlClash `_dockLayout` 返回记录的等价物。
 * pitch=座位宽, lens=色块宽, inset=色块外缘到内容列起点的距离。
 */
data class DockLayout(val pitch: Float, val lens: Float, val inset: Float)

/** 色块矩形(相对内容列左上): FlClash `_lensRect` 等价。 */
data class LensRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * 悬浮 Dock 几何与拖动交互纯函数集 — 1:1 移植 FlClash
 * `lib/ui/navigation_dock.dart` (v0.9.1)。
 *
 * 全部无状态、单位无关(dp 或 px 均可, 调用方统一换算后传入)、可在 JVM 单测
 * (NavDockLayoutTest)直接红绿验证 — 玻璃分支的 pointerInput 手势层只负责
 * 把事件坐标喂进这里, 语义与 Flutter 侧同源。
 *
 * Dart→Kotlin 三处刻意对齐(勿"顺手简化"):
 *  1. `round()` .5 远离零 vs Kotlin half-up 的差只落在负数 .5, 在本文件两个
 *     调用点均被下游 clamp 吸收 — [roundHalfAway] 注释写明该前提;
 *  2. `overshoot.sign` Dart 对 0 给 0.0, Kotlin Float.sign 同(0→0)✓;
 *  3. clamp 嵌套顺序=先 ±1 后边界, 与 `_fling` 源码逐字符一致。
 */
object DockDragLogic {

    /**
     * FlClash `_dockLayout`: iOS tab bar 式布位 — 静止色块向座位两端各悬
     * barPadding, 且永不窄于 minLens。
     *
     * - count≤1: 单 tab 占满; pitch=span-2*pad, lens=span, inset=pad
     * - 开放分支(pitch+2*pad≥minLens): lens=pitch+2*pad, inset=pad
     * - 收窄分支: lens=min(minLens,span), pitch=(span-lens)/(count-1),
     *   inset=(lens-pitch)/2 — 相邻色块互相咬合, 座位中心仍等距
     */
    fun dockLayout(span: Float, count: Int, barPadding: Float, minLens: Float): DockLayout {
        if (count <= 1) {
            return DockLayout(pitch = span - barPadding * 2f, lens = span, inset = barPadding)
        }
        val pitch = (span - barPadding * 2f) / count
        if (pitch + barPadding * 2f >= minLens) {
            return DockLayout(pitch = pitch, lens = pitch + barPadding * 2f, inset = barPadding)
        }
        val lens = minOf(minLens, span)
        val closed = (span - lens) / (count - 1)
        return DockLayout(pitch = closed, lens = lens, inset = (lens - closed) / 2f)
    }

    /** FlClash `_rubberBand`: iOS 橡皮筋 — limit 为理论最大位移, 斜率 0.55 起步。 */
    fun rubberBand(overshoot: Float, limit: Float): Float {
        val pull = 1f - 1f / (abs(overshoot) * 0.55f / limit + 1f)
        return limit * pull * overshoot.sign
    }

    /**
     * FlClash `_positionAt`: 局部 dx → 连续座位坐标(0=首座位中心, 1=次座位中心…)。
     * 越出 [0,lastIndex] 的部分按 overdrag 橡皮筋压缩。
     * direction=1 为 LTR; RTL 由调用方先做 dx 镜像后再进来。
     */
    fun positionAt(
        dx: Float,
        layout: DockLayout,
        barPadding: Float,
        lastIndex: Int,
        overdrag: Float,
    ): Float {
        val position = (dx - barPadding - layout.inset) / layout.pitch - 0.5f
        if (position < 0f) return rubberBand(position, overdrag)
        if (position > lastIndex) return lastIndex + rubberBand(position - lastIndex, overdrag)
        return position
    }

    /** FlClash `_indexAt`: 连续座位坐标 → 最近座位序号(钳位 0..lastIndex)。 */
    fun indexAt(position: Float, lastIndex: Int): Int =
        roundHalfAway(position).coerceIn(0, lastIndex)

    /**
     * 胶囊(stadium)命中判定 — 条体矩形局部坐标 (x,y) 是否落在 percent-50
     * 胶囊内。问题②"命中区拦截"的唯一几何真源: 旧命中区是 Row/单元全矩形,
     * 胶囊四角的透明区把本该给页面内容的触点吞掉。
     *
     * 判定: r = min(w,h)/2; 中段 x∈[r, w−r] 只需 y∈[0,h]; 两端按到圆心
     * (r, h/2) / (w−r, h/2) 的欧氏距离 ≤ r。(w 或 h ≤0 → 恒 false, 防未测量框。)
     */
    fun capsuleHit(w: Float, h: Float, x: Float, y: Float): Boolean {
        if (w <= 0f || h <= 0f) return false
        if (x < 0f || x > w || y < 0f || y > h) return false
        val r = minOf(w, h) / 2f
        return if (x >= r && x <= w - r) true
        else {
            val cx = if (x < r) r else w - r
            val dx = x - cx
            val dy = y - h / 2f
            dx * dx + dy * dy <= r * r
        }
    }

    /**
     * Dart `_RenderHitSlop.hitTest`(391-406) 的命中域判定 + `_onSpine`(409-415)
     * 投影, 全部在 bar-outer 矩形(px, 含四周 barPadding)坐标内:
     *  ① 矩形内(含 stadium 弧外透明角 — Flutter Material 不裁 hit,
     *    Listener.opaque 1164-1169) → 原位置直通;
     *  ② 矩形外 ≤ slop(8dp, `_hitSlop`) 环内 → x clamp 到脊柱
     *    [r, w−r], y 取中线 h/2(Dart `_onSpine`: 弧外触点在 Flutter 里
     *    经 hitTestChildren 传子级的是投影点 → glow/lens 落脊柱) → 认领;
     *  ③ 环外 → null = 不认领、不消费, 事件下沉页面。
     * 审计 G5: 旧 `capsuleHit` 拒绝门(弧外即弃)与环外放大均缺失 = 偏离,
     * 两分支(玻璃/经典)统一走本式。返回投影后 bar-outer 坐标(bar 矩形左上
     * 原点, x/y px); 本文件保持纯 JVM(不引 compose 类型)故用 Pair。
     */
    fun hitTestSlop(
        w: Float, h: Float, x: Float, y: Float, slop: Float,
    ): Pair<Float, Float>? {
        if (w <= 0f || h <= 0f) return null
        if (x >= 0f && x <= w && y >= 0f && y <= h) return Pair(x, y)
        if (!(x >= -slop && x <= w + slop && y >= -slop && y <= h + slop)) return null
        val r = minOf(w, h) / 2f
        return Pair(x.coerceIn(r, w - r), h / 2f)
    }

    /**
     * FlClash 溢出标签缩放: 最宽 label 超出座位可用宽度时, 全条 label 按同一
     * 比例整体缩字号(FlClash 用 `fontSize = _labelSize * labelScale`, 对全部
     * tab 生效 — 不是逐 tab 各缩, 否则同一排字号会参差)。
     *   room       = pitch - 2*labelInset            (座位内可放文字的宽度)
     *   labelScale = if (widest <= room) 1 else max(room/widest, minRatio)
     * minRatio = _minLabelSize/_labelSize = 9/10 — 缩到 9sp 为止, 再宽就省略号。
     * widest=0(空 label/未测) 时返回 1, 避免除零。
     */
    fun labelScale(
        widestLabel: Float,
        pitch: Float,
        labelInset: Float,
        minRatio: Float,
    ): Float {
        val room = pitch - labelInset * 2f
        if (room <= 0f || widestLabel <= 0f) return 1f
        if (widestLabel <= room) return 1f
        return maxOf(room / widestLabel, minRatio)
    }

    /**
     * FlClash `_fling`: 拖拽中途松手的速度投影 — lead=100ms 内能滑过的座位数,
     * 落点=round(lensTarget+lead) 先钳 ±1(最多数过一格)再钳边界。
     * direction: LTR=1, RTL=-1(像素速度与座位序的反向关系)。
     */
    fun flingTarget(
        lensTarget: Float,
        velocityX: Float,
        pitch: Float,
        projectionMs: Float,
        pressedIndex: Int,
        lastIndex: Int,
        direction: Int,
    ): Int {
        val lead = velocityX / pitch * direction * projectionMs / 1000f
        return (roundHalfAway(lensTarget + lead))
            .coerceIn(pressedIndex - 1, pressedIndex + 1)
            .coerceIn(0, lastIndex)
    }

    /**
     * FlClash `_lensRect`: 座位连续位置 → 色块矩形(内容列坐标)。
     * lift=按压抬升 0..1 → 宽高各 +lensGrowth*2*lift;
     * stretch=果冻形变 → 宽×(1+s) 高×(1-s*jellyAspect); x 始终中心对齐,
     * y=(restHeight-height)/2 即上下对称溢出(与 FlClash 相同, 溢出进 shadowRoom)。
     */
    fun lensRect(
        position: Float,
        stretch: Float,
        pitch: Float,
        width: Float,
        height: Float,
        lift: Float,
        lensGrowth: Float,
        jellyAspect: Float,
    ): LensRect {
        val growth = lensGrowth * 2f * lift
        val lensWidth = (width + growth) * (1f + stretch)
        val lensHeight = (height + growth) * (1f - stretch * jellyAspect)
        return LensRect(
            x = (position + 0.5f) * pitch - lensWidth / 2f,
            y = (height - lensHeight) / 2f,
            width = lensWidth,
            height = lensHeight,
        )
    }

    /** Dart `double.round()` 是 0.5 远离零(官方: (3.5).round()==4, (-3.5).round()==-4),
     *  Kotlin roundToInt()=floor(x+0.5)(api.dart.dev/stable/dart-core/double/round.html)。
     *  唯一语义差=负数 .5(-0.5: Dart→-1, Kotlin→0), 在本文件两个调用点
     *  (indexAt/flingTarget)均被下游 clamp(0,…) 吸收 — NavDockLayoutTest 的
     *  `indexAt(-0.5f)==0` 与反向钳位用例锁住该前提。无钳位场景复用前需另写。 */
    private fun roundHalfAway(value: Float): Int = value.roundToInt()
}
