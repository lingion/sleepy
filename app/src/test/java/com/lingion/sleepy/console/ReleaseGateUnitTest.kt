package com.lingion.sleepy.console

import com.lingion.sleepy.console.handlers.EnableFlow
import com.lingion.sleepy.console.handlers.UnlockTapCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 7 release 隐藏开关（brief Step 1）：
 *  - 连点计数纯函数：6 次不 armed、第 7 次 armed、窗口过期重新起算；
 *  - 未 armed 的 /v1/enable → NOT_ARMED；armed+对口令 → token + isReleaseUnlocked；
 *  - armed 超 60s 窗口 → enable 仍拒（过期即失效）；
 *  - 一键失活 → isActive=false 且 token 失效；
 *  - debug 构建 enable 短路 "already active"。
 * 时钟注入复位（DebugGate/TokenStore.reset(forTestNow)），全程虚拟时间。
 */
class ReleaseGateUnitTest {

    private var fakeNow = 1_000_000_000_000L
    private val now get() = { fakeNow }

    @Before
    fun base() {
        fakeNow = 1_000_000_000_000L
        DebugGate.reset(now)
        TokenStore.reset(passphrase = "sleepy.is-my.app", forTestNow = now)
    }

    // ===== 连点计数纯函数 =====

    @Test
    fun sixTaps_doNotArm_seventhArms() {
        var armed = false
        repeat(6) { i ->
            armed = UnlockTapCounter.registerTap(nowMs = fakeNow + i * 100L)
            assertFalse("第 ${i + 1} 次连点不应 armed", armed)
        }
        assertTrue("第 7 次连点必须进入等待解锁态", UnlockTapCounter.registerTap(nowMs = fakeNow + 600L))
    }

    @Test
    fun tapWindowExpiry_counterResets() {
        repeat(3) { UnlockTapCounter.registerTap(nowMs = fakeNow) }
        // 60s 窗口外再点 → 重新起算(这次算第 1 下), 再 5 下不 armed, 第 7 下 armed
        assertFalse(UnlockTapCounter.registerTap(nowMs = fakeNow + 61_000L))          // 第 1 下
        repeat(5) { assertFalse(UnlockTapCounter.registerTap(nowMs = fakeNow + 61_000L)) }  // 2..6
        assertTrue("窗口重启后累计第 7 下必须 armed", UnlockTapCounter.registerTap(nowMs = fakeNow + 61_000L))
    }

    @Test
    fun reset_clearsCounter() {
        repeat(6) { UnlockTapCounter.registerTap(nowMs = fakeNow) }
        UnlockTapCounter.reset()
        assertFalse("复位后第 1 次点击不应直接 armed", UnlockTapCounter.registerTap(nowMs = fakeNow))
    }

    // ===== enable 状态机 =====

    @Test
    fun notArmed_enable_rejected() {
        val resp = EnableFlow.attemptUnlock(debugBuild = false, armed = false, passphrase = "sleepy.is-my.app", nowMs = fakeNow)
        assertEquals(EnableFlow.Result.NOT_ARMED, resp)
    }

    @Test
    fun armed_correctPassphrase_tokenIssuedAndUnlocked() {
        DebugGate.armUnlock()
        val t = TokenStore.attemptUnlock("sleepy.is-my.app")
        assertEquals(UnlockResult.OK, t)
        assertTrue(DebugGate.isReleaseUnlocked)
        assertNotNull(TokenStore.issue())
    }

    @Test
    fun armedWindowExpiry_enable_rejected() {
        DebugGate.armUnlock()
        fakeNow += 61_000L // 60s 窗口过期
        assertFalse(DebugGate.isUnlockArmed)
        assertEquals(UnlockResult.NOT_ARMED, TokenStore.attemptUnlock("sleepy.is-my.app"))
    }

    @Test
    fun deactivate_tokenInvalidated() {
        DebugGate.armUnlock()
        TokenStore.attemptUnlock("sleepy.is-my.app")
        val token = TokenStore.issue()
        assertTrue(TokenStore.verify(token))
        TokenStore.revoke()
        DebugGate.invalidate()
        assertFalse(DebugGate.isActive)
        assertFalse(DebugGate.isReleaseUnlocked)
        assertFalse("失活后旧 token 必须失效", TokenStore.verify(token))
    }

    @Test
    fun debugBuild_enable_shortCircuitsAlreadyActive() {
        // debug 包: enable 面回 "already active", 不消费口令不签新 token
        val r = EnableFlow.attemptUnlock(debugBuild = true, armed = false, passphrase = "whatever", nowMs = fakeNow)
        assertEquals(EnableFlow.Result.ALREADY_ACTIVE, r)
    }

    @Test
    fun wrongPassphrase_backoffAfterThreeFails() {
        DebugGate.armUnlock()
        repeat(3) { TokenStore.attemptUnlock("wrong") }
        // 第 4 次尝试: 退避期内即便口令对也拒
        DebugGate.armUnlock()
        assertEquals(UnlockResult.BACKOFF, TokenStore.attemptUnlock("sleepy.is-my.app"))
    }

    @Test
    fun unlockArmed_exposesArmForUiWiring() {
        assertFalse(DebugGate.isUnlockArmed)
        DebugGate.armUnlock()
        assertTrue(DebugGate.isUnlockArmed)
        DebugGate.clearArm()
        assertFalse(DebugGate.isUnlockArmed)
        assertNull(DebugGate.unlockArmedAt)
    }
}
