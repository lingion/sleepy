package com.lingion.sleepy.console

import java.security.MessageDigest
import java.security.SecureRandom

/** `/v1/enable` 解锁尝试的四种结果（SPEC §3）。 */
enum class UnlockResult {
    OK,
    WRONG_PASSPHRASE,
    BACKOFF,
    NOT_ARMED,
}

/**
 * 内存 token 仓 + 口令校验 + 防刷退避。
 *
 * 红线（SPEC §5.4）：token 与口令只存在于本 object 的进程内存里——
 * 不写 SharedPreferences、不写文件、不入任何日志。进程死即归零。
 *
 * 防刷：连续错满 3 次后进入指数退避（8s 起每多错一次翻倍，封顶 60s）；
 * 退避窗口内即使口令正确也返回 [UnlockResult.BACKOFF]。
 *
 * 时钟走可注入 [forTestNow]，单测用假时钟锁退避与 24h 过期。
 */
object TokenStore {

    /** token 有效期：24 小时（SPEC §3）。 */
    const val TOKEN_TTL_MS = 24L * 3_600_000L

    /** 错满该次数后开始退避。 */
    const val BACKOFF_AFTER_FAILS = 3

    /** 退避基数：第 3 次错后窗口 8s，之后每错一次翻倍，封顶 60s。 */
    const val BACKOFF_BASE_MS = 8_000L
    const val BACKOFF_MAX_MS = 60_000L

    @Volatile
    private var token: String? = null

    @Volatile
    private var issuedAt: Long = 0L

    @Volatile
    private var failCount: Int = 0

    @Volatile
    private var lastFailAt: Long = 0L

    /** 口令编译期注入（BuildConfig.CONSOLE_ENABLE_PASSPHRASE），单测用 reset 换假口令。 */
    @Volatile
    private var passphrase: String = ""

    private var now: () -> Long = System::currentTimeMillis

    private val random = SecureRandom()

    /**
     * 尝试用口令解锁。只在 UI 侧已连点版本号进入“等待解锁”态（60s 窗口）时接受。
     * 成功：签发 24h token，清等待态，并把 DebugGate 置为 release 解锁激活。
     */
    @Synchronized
    fun attemptUnlock(passphrase: String): UnlockResult {
        if (!DebugGate.isUnlockArmed) return UnlockResult.NOT_ARMED
        if (inBackoff()) return UnlockResult.BACKOFF
        // fail-closed：口令未配置（如 release 构建误留空串）时任何提交都算错，绝不签发
        if (this.passphrase.isEmpty()) {
            lastFailAt = now()
            failCount++
            return UnlockResult.WRONG_PASSPHRASE
        }
        if (!constantTimeEquals(passphrase.toByteArray(Charsets.UTF_8),
                this.passphrase.toByteArray(Charsets.UTF_8))) {
            lastFailAt = now()
            failCount++
            return UnlockResult.WRONG_PASSPHRASE
        }
        failCount = 0
        val sb = StringBuilder(64)
        val bytes = ByteArray(32)
        repeat(2) {
            random.nextBytes(bytes)
            for (b in bytes) sb.append("%02x".format(b))
        }
        token = sb.toString()
        issuedAt = now()
        DebugGate.clearArm()
        DebugGate.activate(debugBuild = DebugGate.debugBuild, releaseUnlocked = true)
        return UnlockResult.OK
    }

    /** 当前有效 token；无 token 或已过期返回 null（过期时顺手清掉）。 */
    @Synchronized
    fun issue(): String? {
        val t = token ?: return null
        if (now() - issuedAt >= TOKEN_TTL_MS) {
            token = null
            return null
        }
        return t
    }

    /** token 校验：非空、与当前有效 token 恒时比较、且未过 24h。 */
    @Synchronized
    fun verify(token: String?): Boolean {
        val current = issue() ?: return false
        if (token == null) return false
        return constantTimeEquals(token.toByteArray(), current.toByteArray())
    }

    /** 立即吊销 token（invalidate / session 复位用）。 */
    @Synchronized
    fun revoke() {
        token = null
        failCount = 0
    }

    private fun inBackoff(): Boolean {
        if (failCount < BACKOFF_AFTER_FAILS) return false
        return now() - lastFailAt < backoffWindowMs()
    }

    private fun backoffWindowMs(): Long {
        var window = BACKOFF_BASE_MS
        repeat(failCount - BACKOFF_AFTER_FAILS) {
            window = minOf(window * 2, BACKOFF_MAX_MS)
        }
        return window
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)

    /** 仅测试/装配复位：注入口令与假时钟，清空 token 与退避计数。 */
    fun reset(passphrase: String = "", forTestNow: () -> Long = System::currentTimeMillis) {
        synchronized(this) {
            this.passphrase = passphrase
            this.now = forTestNow
            token = null
            issuedAt = 0L
            failCount = 0
            lastFailAt = 0L
        }
    }
}
