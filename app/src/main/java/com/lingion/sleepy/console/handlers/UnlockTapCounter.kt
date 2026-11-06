package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.console.DebugGate

/**
 * 连点计数纯函数（Task 7, SPEC §release 隐藏开关）：
 * 同一 60s 滑动窗口内连续 7 次 → 进入等待解锁态（[DebugGate.armUnlock]）。
 *
 * 无状态对象 + 纯参数：计数与时间戳由调用方(UI)持有并传入, 便于 JVM 直测
 * （brief Step 1 "连点计数 helper 纯函数化"）。返回 true = 本次点击触发 armed。
 */
object UnlockTapCounter {

    const val REQUIRED_TAPS = 7
    const val TAP_WINDOW_MS = 60_000L

    private var taps = 0
    private var firstTapAt = 0L

    /** 记一次点击；true = 恰好本次达到 armed 条件（已调 armUnlock）。 */
    fun registerTap(nowMs: Long): Boolean {
        if (taps == 0 || nowMs - firstTapAt > TAP_WINDOW_MS) {
            taps = 1
            firstTapAt = nowMs
        } else {
            taps++
        }
        if (taps >= REQUIRED_TAPS) {
            taps = 0
            firstTapAt = 0L
            DebugGate.armUnlock()
            return true
        }
        return false
    }

    /** 复位（解锁成功/失活/测试隔离用）。 */
    fun reset() {
        taps = 0
        firstTapAt = 0L
    }
}
