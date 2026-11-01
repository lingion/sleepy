package com.lingion.sleepy.console

import com.lingion.sleepy.console.handlers.UnlockTapCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TokenStore.grantNewToken()（2026-10-30 debug 装配自动签发, SPEC §3
 * "debug 跳过口令与连点 arm, 不跳 token"）JVM 锁定：
 *  - 签发即得 128 hex token(256-bit, 与 attemptUnlock 同构), issue() 可回, verify() 通过;
 *  - 零口令面: 不经 attemptUnlock → 不消费 failCount/退避, 不要求 armed;
 *  - 不触 DebugGate.activate/clearArm(release 解锁语义与 debug 装配正交);
 *  - 覆盖旧 token（重签发 = 新值, 旧值 verify 失败）。
 */
class GrantNewTokenUnitTest {

    private var fakeNow = 1_000_000_000_000L
    private val now get() = { fakeNow }

    @Before
    fun base() {
        fakeNow = 1_000_000_000_000L
        UnlockTapCounter.reset()
        DebugGate.reset(now)
        TokenStore.reset(passphrase = "sleepy.is-my.app", forTestNow = now)
    }

    private fun arm() {
        var armed = false
        repeat(7) { i -> armed = UnlockTapCounter.registerTap(nowMs = fakeNow + i * 100L) }
        assertTrue("7 连点必须 armed", armed)
    }

    @Test
    fun grant_ignoresArmAndPassphrase_signsUsableToken() {
        // 未 armed、错口令从未提交 —— grant 仍直接成功
        assertFalse(DebugGate.isUnlockArmed)
        TokenStore.grantNewToken()
        val t = TokenStore.issue()
        assertNotNull("grant 后 issue 必须给值", t)
        // 与 attemptUnlock 签发面同构: 32B x2 = 128 hex (256-bit)
        assertEquals(128, t!!.length)
        assertTrue("hex 面值", Regex("^[0-9a-f]{128}$").matches(t))
        assertTrue(TokenStore.verify(t))
        // release 语义正交: 不经 attemptUnlock → 不 activate、不 arm 消费
        assertFalse("grant 不得替 release 解锁", DebugGate.isReleaseUnlocked)
    }

    @Test
    fun grant_doesNotClearFailureState() {
        // armed 后连错 3 发进入退避（BACKOFF_AFTER_FAILS=3）
        arm()
        repeat(3) {
            assertEquals(UnlockResult.WRONG_PASSPHRASE, TokenStore.attemptUnlock("wrong.pass"))
        }
        // grant 只签发 debug token —— 不消费失败计数、不清退避窗（红线 4 口令面零污染）
        TokenStore.grantNewToken()
        assertEquals(UnlockResult.BACKOFF, TokenStore.attemptUnlock("sleepy.is-my.app"))
    }

    @Test
    fun grant_overridesPreviousToken() {
        TokenStore.grantNewToken()
        val first = TokenStore.issue()!!
        TokenStore.grantNewToken()
        val second = TokenStore.issue()!!
        assertTrue(first != second)
        assertFalse("旧 token 即刻失效", TokenStore.verify(first))
        assertTrue(TokenStore.verify(second))
    }

    @Test
    fun reset_clearsGrant() {
        TokenStore.grantNewToken()
        TokenStore.reset(passphrase = "sleepy.is-my.app", forTestNow = now)
        assertNull(TokenStore.issue())
    }
}
