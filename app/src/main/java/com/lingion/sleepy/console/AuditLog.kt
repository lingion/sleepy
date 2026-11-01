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

    private val ring = ArrayDeque<JSONObject>()

    @Synchronized
    fun record(method: String, path: String, token: String?, responseCode: Int, nowMs: Long) {
        val e = JSONObject()
            .put("kind", "audit")
            .put("ts", nowMs)
            .put("method", method.uppercase())
            .put("path", path)
            .put("code", responseCode)
            .put("ok", responseCode in 200..299)
        if (token != null && token.length >= 4) {
            e.put("tokenSuffix", token.takeLast(4))
        }
        if (ring.size >= capacity) ring.removeFirst()
        ring.addLast(e)
    }

    /** 由旧到新。返回深拷贝，调用方改动不影响缓冲本体。 */
    @Synchronized
    fun entries(): List<JSONObject> = ring.map { JSONObject(it.toString()) }

    @Synchronized
    fun clear() = ring.clear()
}
