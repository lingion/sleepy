package com.lingion.sleepy.console

import android.app.Application
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.lingion.sleepy.SleepyApp
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * jw-mock 规则表（Task 6, SPEC §jw-mock 红线 2）：
 * urlPattern(正则) → 构造响应（status/headers/body），WebView
 * shouldInterceptRequest 头位回放 — 全协议族任何 URL 可被离线回放。
 *
 * 分层（与 data-ops 同构）：
 *  - [matchRuleIn]/[resolveBodyFile] 纯函数, JVM 直测（JwMockRuleUnitTest）;
 *  - [intercept] 薄桥: 命中 → WebResourceResponse, 空表/未命中恒 null —
 *    未注入 mock 时教务真实通路零影响（Review Focus 5 锁）;
 *  - 持久化 filesDir/console_jw_mocks.json, 进程重启规则仍在（跨会话回放）。
 *
 * 红线 2 落点：注册在 [MockRuleStore.registerEndpoint] 内部按 DebugGate 判定 —
 * release 解锁态（非 debug 构建）POST/DELETE 一律 403, GET 列表同样 403;
 * 规则表本体在 release 恒空(解锁态无法注入), intercept 恒 null。
 */
object MockRuleStore {

    data class MockRule(
        val urlPattern: String,
        val status: Int,
        val contentType: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is MockRule && urlPattern == other.urlPattern &&
                status == other.status && contentType == other.contentType &&
                headers == other.headers && body.contentEquals(other.body)

        override fun hashCode(): Int =
            urlPattern.hashCode() * 31 + status + contentType.hashCode()
    }

    @Volatile
    private var rules: List<MockRule> = emptyList()

    @Volatile
    private var file: File? = null

    // ===== 纯函数面（JVM 直测） =====

    /** 注册序即优先级, 首个正则命中者胜; 非法 pattern 跳过不炸链。 */
    fun matchRuleIn(rules: List<MockRule>, url: String): MockRule? {
        for (r in rules) {
            val hit = try {
                Regex(r.urlPattern).containsMatchIn(url)
            } catch (_: Exception) {
                continue
            }
            if (hit) return r
        }
        return null
    }

    /**
     * respond_with=file 的路径守卫：只接受 filesDir 内相对路径 —
     * 拒绝空串/绝对路径/任何 `..` 段, 且 canonical 后必须仍在 filesDir 下。
     */
    fun resolveBodyFile(filesDir: File, name: String): File? {
        if (name.isBlank() || File(name).isAbsolute) return null
        if (name.split('/').contains("..") || name.split(File.separatorChar).contains("..")) return null
        val target = File(filesDir, name)
        return try {
            if (target.canonicalPath == filesDir.canonicalPath ||
                target.canonicalPath.startsWith(filesDir.canonicalPath + File.separator)
            ) target else null
        } catch (_: Exception) {
            null
        }
    }

    // ===== 装载/变更面（主线程或 handler 线程, volatile 整体替换） =====

    fun init(app: Application) {
        val f = File(app.filesDir, "console_jw_mocks.json")
        file = f
        rules = load(f)
    }

    /** 当前规则快照（GET /v1/jw/mock）。 */
    fun list(): List<MockRule> = rules

    fun put(rule: MockRule) = mutate(rules + rule)

    fun remove(urlPattern: String): Boolean =
        rules.any { it.urlPattern == urlPattern }.also { if (it) mutate(rules.filterNot { r -> r.urlPattern == urlPattern }) }

    fun clear() = mutate(emptyList())

    private fun mutate(next: List<MockRule>) {
        rules = next
        file?.let { save(it, next) }
    }

    // ===== WebView 拦截点（background-thread 安全：纯读 volatile + 构造响应） =====

    /**
     * JwWebViewClientImpl 链头调用：空表/未命中 = null（真实通路零影响）;
     * 命中 = 回放响应。headers 经 ResponseHeaderFactory 兼容注入（跨版本
     * setResponseHeaders 对部分键静默丢弃, Set-Cookie 需走工厂路径 —
     * 这里先给标准 map, 教务登录关键 Cookie 回放由后续验收校准）。
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? =
        request.url?.toString()?.let { intercept(it) }

    /** String 重载 — androidTest/JVM 无 Uri 构造时直用。 */
    fun intercept(url: String): WebResourceResponse? {
        val snapshot = rules
        if (snapshot.isEmpty()) return null
        val r = matchRuleIn(snapshot, url) ?: return null
        return WebResourceResponse(
            r.contentType.substringBefore('/'),
            r.contentType.substringAfter('/', "utf-8"),
            r.status,
            if (r.status in 200..299) "OK" else "MOCKED",
            r.headers.toMutableMap<String, String>().also { it["Content-Type"] = r.contentType },
            ByteArrayInputStream(r.body),
        )
    }

    // ===== 持久化 =====

    private fun load(f: File): List<MockRule> {
        if (!f.isFile) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MockRule(
                    urlPattern = o.getString("url_pattern"),
                    status = o.optInt("status", 200),
                    contentType = o.optString("content_type", "text/html"),
                    headers = o.optJSONObject("headers")?.let { h ->
                        h.keys().asSequence().associateWith { h.getString(it) }
                    } ?: emptyMap(),
                    body = Base64.getDecoder().decode(o.getString("body_b64")),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(f: File, rs: List<MockRule>) = runCatching {
        val arr = JSONArray()
        rs.forEach { r ->
            arr.put(
                JSONObject()
                    .put("url_pattern", r.urlPattern)
                    .put("status", r.status)
                    .put("content_type", r.contentType)
                    .put("headers", JSONObject(r.headers))
                    .put("body_b64", Base64.getEncoder().encodeToString(r.body))
            )
        }
        f.writeText(arr.toString())
    }
}
