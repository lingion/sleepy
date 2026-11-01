package com.lingion.sleepy.console

/**
 * 鉴权判定纯函数（SPEC §5.2 / §6）— 从 DebugConsoleServer 抽出，供 JVM 单测直接锁真值表。
 *
 * 规则只有一条白名单：/v1/hello 与 /v1/enable 免 token；其余任何 path，
 * 只要 DebugGate 未激活 或 token 校验不通过，一律拒绝（401）。
 * release 隐藏模式下同样走本函数 — isActive 是 gate 的真相源，这里不问构建类型。
 */
object AuthGate {

    /** 免 token 端点白名单（仅这两个，SPEC §3）。 */
    val TOKEN_FREE_PATHS = setOf("/v1/hello", "/v1/enable")

    /**
     * 该请求是否应被鉴权拒绝。
     * path 精确匹配白名单（不做前缀放宽）；白名单外必须 gate 激活且 token 有效。
     */
    fun shouldReject(path: String, isActive: Boolean, tokenValid: Boolean): Boolean {
        if (path in TOKEN_FREE_PATHS) return false
        return !isActive || !tokenValid
    }
}
