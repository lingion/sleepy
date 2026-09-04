package com.lingion.sleepy.data.jw

/**
 * 单/双周 (parity) 共享工具 — JwZju/JwUstc/JwSeu/JwNeu/JwScu 五 parser 同型端点
 * 修正的单一实现 (审计 2026-09-04: 三处复制且都只抬 start 不动 end, 端点相等
 * 时产出倒挂区间 (7,6) 类, 下游 coerce 会把课挤到错误周)。
 *
 * Sleepy 语义: type 0=每周 1=单周(奇数周) 2=双周(偶数周)。
 */
object JwParity {

    /**
     * 单/双周起点端点修正: 单周起点须奇数 / 双周起点须偶数。
     * 修正后超出 endWeek (端点相等场景如 "6-6(单)" → 7,6) 时把 end 一并抬到
     * start — 端点倒挂会把课挤到错误周, 让它落回首个合法周。
     *
     * @param startWeek 原始起点周
     * @param endWeek   原始终点周
     * @param parity    0=每周(不修正) 1=单周 2=双周
     */
    fun adjustedRange(startWeek: Int, endWeek: Int, parity: Int): Pair<Int, Int> {
        if (parity != 1 && parity != 2) return startWeek to endWeek
        val start = when (parity) {
            1 -> if (startWeek % 2 == 0) startWeek + 1 else startWeek
            else -> if (startWeek % 2 != 0) startWeek + 1 else startWeek
        }
        return start to maxOf(endWeek, start)
    }
}
