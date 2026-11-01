package com.lingion.sleepy.console

import org.json.JSONObject

/**
 * 审计日志 — 进程内环形缓冲（默认 200 条），不写任何持久化文件。
 *
 * 红线（任务约束 + SPEC §5.4）：只记 时间+method+path+token 后 4 位+ok，
 * 请求体一律不进记录；完整 token、口令永远不落任何日志。
 */
class AuditLog(private val capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        const val DEFAULT_CAPACITY = 200
    }

    data class AuditEntry(
        val ts: Long,
        val method: String,
        val path: String,
        val code: Int,
        val ok: Boolean,
        val tokenSuffix: String? = null,
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
                .put("kind", "audit")
                .put("ts", ts)
                .put("method", method)
                .put("path", path)
                .put("code", code)
                .put("ok", ok)
            if (tokenSuffix != null) {
                json.put("tokenSuffix", tokenSuffix)
            }
            return json
        }
    }

    private val ring = ArrayDeque<AuditEntry>()

    @Synchronized
    fun record(method: String, path: String, token: String?, responseCode: Int, nowMs: Long) {
        val suffix = if (token != null && token.length >= 4) token.takeLast(4) else null
        val entry = AuditEntry(
            ts = nowMs,
            method = method.uppercase(),
            path = path,
            code = responseCode,
            ok = responseCode in 200..299,
            tokenSuffix = suffix,
        )
        if (ring.size >= capacity) ring.removeFirst()
        ring.addLast(entry)
    }

    /** 由旧到新。返回独立副本，调用方改动不影响缓冲本体。 */
    @Synchronized
    fun entries(): List<JSONObject> = ring.map { it.toJson() }

    @Synchronized
    fun clear() = ring.clear()
}
