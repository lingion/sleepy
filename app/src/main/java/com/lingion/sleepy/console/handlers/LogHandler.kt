package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.util.AppLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * GET /v1/log — 日志双环读取（Task 8, SPEC §/v1/log）：
 *  - data.app = AppLog 内存环（业务里程碑双写面, 尾部 limit 条, 默认 200 全量）;
 *  - data.audit = 本 console 自身 AuditLog 请求环（method/path/code, 不含 token
 *    明文与请求体 — AuditLog.record 落盘前已脱敏）。
 *
 * query: ?limit=N（对两环各自取尾部 N）。
 */
object LogHandler {

    fun registerAll() {
        ConsoleBootstrap.addHandler("/v1/log", "GET", requiresToken = true) { req ->
            val limit = req.query["limit"]?.toIntOrNull()?.takeIf { it > 0 }
            val audit = ConsoleBootstrap.audit
            val auditArr = org.json.JSONArray()
            audit?.entries()?.let { list ->
                (if (limit != null) list.takeLast(limit) else list).forEach { auditArr.put(it) }
            }
            val body = JSONObject()
                .put("app", JSONArray(AppLog.json(AppLog.snapshot(limit))))
                .put("audit", auditArr)
                .toString()
            Envelope.ok(body)
        }
    }
}
