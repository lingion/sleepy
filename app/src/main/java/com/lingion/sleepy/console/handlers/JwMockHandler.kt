package com.lingion.sleepy.console.handlers

import android.app.Application
import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.DebugGate
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.console.MockRuleStore
import com.lingion.sleepy.console.MockRuleStore.MockRule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * POST/DELETE/GET /v1/jw/mock — WebView 协议回放端点（Task 6）。
 *
 * 红线 2：release 解锁态（非 debug 构建）整端点 403 — [guard] 先于一切读写。
 * respond_with=file 只认 filesDir 内相对名（MockRuleStore.resolveBodyFile 守卫）。
 */
object JwMockHandler {

    fun registerAll(app: Application) {
        MockRuleStore.init(app)
        ConsoleBootstrap.addHandler("/v1/jw/mock", "GET", requiresToken = true) {
            handleGet(app)
        }
        ConsoleBootstrap.addHandler("/v1/jw/mock", "POST", requiresToken = true) { req ->
            handlePut(app, req.body)
        }
        ConsoleBootstrap.addHandler("/v1/jw/mock", "DELETE", requiresToken = true) { req ->
            handleDelete(req.body)
        }
    }

    /** 红线 2 守卫（SPEC §5.1）：mock 注入只允许 debug 构建。 */
    private fun guard(): String? =
        if (!DebugGate.debugBuild) Envelope.err(403, "jw mock requires debug build (SPEC red line 2)") else null

    fun handleGet(app: Application?): String {
        guard()?.let { return it }
        val arr = JSONArray()
        MockRuleStore.list().forEach { r ->
            arr.put(
                JSONObject()
                    .put("url_pattern", r.urlPattern)
                    .put("status", r.status)
                    .put("content_type", r.contentType)
                    .put("headers", JSONObject(r.headers))
                    .put("body_bytes", r.body.size)
            )
        }
        return Envelope.ok(JSONObject().put("rules", arr).toString())
    }

    fun handlePut(app: Application?, body: String): String {
        guard()?.let { return it }
        val o = try {
            JSONObject(body)
        } catch (_: Exception) {
            return Envelope.err(400, "body must be a JSON object")
        }
        val pattern = o.optString("url_pattern")
        if (pattern.isBlank()) return Envelope.err(400, "url_pattern required")
        try {
            Regex(pattern)
        } catch (_: Exception) {
            return Envelope.err(400, "url_pattern must be a valid regex")
        }
        val kind = o.optString("respond_with", "html")
        val bodyBytes: ByteArray = when (kind) {
            "html" -> o.optString("body").toByteArray()
            "b64" -> {
                val b = o.optString("body")
                try {
                    Base64.getDecoder().decode(b)
                } catch (_: Exception) {
                    return Envelope.err(400, "body must be valid base64")
                }
            }
            "file" -> {
                val filesDir = app?.filesDir
                    ?: return Envelope.err(400, "respond_with=file requires app context")
                val name = o.optString("body")
                val f = MockRuleStore.resolveBodyFile(filesDir, name)
                    ?: return Envelope.err(400, "file must be a relative path inside filesDir")
                if (!f.isFile) return Envelope.err(400, "file not found in filesDir: ${f.name}")
                f.readBytes()
            }
            else -> return Envelope.err(400, "respond_with must be file|b64|html")
        }
        val headers = o.optJSONObject("headers")?.let { h ->
            h.keys().asSequence().associateWith { h.getString(it) }
        } ?: emptyMap()
        MockRuleStore.put(
            MockRule(
                urlPattern = pattern,
                status = o.optInt("status", 200),
                contentType = o.optString("content_type", "text/html"),
                headers = headers,
                body = bodyBytes,
            )
        )
        return Envelope.ok(
            JSONObject()
                .put("url_pattern", pattern)
                .put("bytes", bodyBytes.size)
                .put("rules_total", MockRuleStore.list().size)
                .toString()
        )
    }

    fun handleDelete(body: String): String {
        guard()?.let { return it }
        val o = try {
            JSONObject(body)
        } catch (_: Exception) {
            return Envelope.err(400, "body must be a JSON object")
        }
        return when {
            o.has("url_pattern") -> {
                val removed = MockRuleStore.remove(o.getString("url_pattern"))
                if (removed) Envelope.ok(JSONObject().put("removed", o.getString("url_pattern")).toString())
                else Envelope.err(404, "no rule with url_pattern ${o.getString("url_pattern")}")
            }
            o.optBoolean("all", false) -> {
                MockRuleStore.clear()
                Envelope.ok("{\"cleared\":true}")
            }
            else -> Envelope.err(400, "requires {url_pattern} or {all:true}")
        }
    }
}
