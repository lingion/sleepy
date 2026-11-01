package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.console.DebugGate
import com.lingion.sleepy.console.TokenStore
import com.lingion.sleepy.console.UnlockResult

/**
 * /v1/enable 决策纯函数（Task 7）：把"debug 短路 / 未 armed / 退避 /
 * 口令错 / 成功"五分支收成一个可 JVM 直测的状态机, handler 只做
 * envelope 序列化与 start() 副作用。
 */
object EnableFlow {

    enum class Result { ALREADY_ACTIVE, OK, WRONG_PASSPHRASE, BACKOFF, NOT_ARMED }

    /**
     * @param debugBuild debug 构建 → 恒 ALREADY_ACTIVE（SPEC §3: debug 包跳过此步,
     *   口令不进任何分支, 也不消费失败计数）
     */
    fun attemptUnlock(debugBuild: Boolean, armed: Boolean, passphrase: String, nowMs: Long): Result =
        if (debugBuild) {
            Result.ALREADY_ACTIVE
        } else if (!armed) {
            Result.NOT_ARMED
        } else {
            when (TokenStore.attemptUnlock(passphrase)) {
                UnlockResult.OK -> Result.OK
                UnlockResult.WRONG_PASSPHRASE -> Result.WRONG_PASSPHRASE
                UnlockResult.BACKOFF -> Result.BACKOFF
                UnlockResult.NOT_ARMED -> Result.NOT_ARMED
            }
        }

    fun attempt(reqPassphrase: String): Result =
        attemptUnlock(
            debugBuild = DebugGate.debugBuild,
            armed = DebugGate.isUnlockArmed,
            passphrase = reqPassphrase,
            nowMs = System.currentTimeMillis(),
        )
}
