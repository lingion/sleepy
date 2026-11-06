package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.ConsoleRequest
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.console.ui.UiNode
import com.lingion.sleepy.console.ui.UiRuntime
import com.lingion.sleepy.console.ui.diffFingerprints
import com.lingion.sleepy.console.ui.fingerprintJson
import com.lingion.sleepy.console.ui.fingerprintOf
import com.lingion.sleepy.console.ui.base64Encode
import com.lingion.sleepy.console.ui.dumpLines
import com.lingion.sleepy.console.ui.nodeToJson
import com.lingion.sleepy.console.ui.parseSelector
import com.lingion.sleepy.console.ui.selectNodes
import org.json.JSONArray
import org.json.JSONObject

// ui-ops 四端点（SPEC §3）— /v1/ui/tree /find /action /screenshot。
// 纯 JVM：只认 [UiRuntime] 接缝与 org.json，全部语义面由 UiSelectorUnitTest 锁死。
// 失败信封口径与 data-ops 一致：判据错 = 400，selector 无命中 = 404，
// 手势/截图运行时 false/null = 500（派发超时、无前台 Activity、捕获失败）。

object UiOpsHandlers {

    private val SCROLL_DIRS = setOf("down", "up", "left", "right")

    // ---- /v1/ui/tree ---------------------------------------------------------

    fun handleTree(rt: UiRuntime): String {
        val nodes = rt.snapshotNodes()
        val fp = fingerprintOf(rt.activityName(), nodes, rt.focusTag(), rt.visibleTextHash())
        val data = JSONObject()
            .put("activity", fp.activity ?: JSONObject.NULL)
            .put("nodes", JSONArray().apply { nodes.forEach { put(nodeToJson(it)) } })
            .put("dump", JSONArray(dumpLines(nodes)))
            .put("fingerprint", fingerprintJson(fp))
        return Envelope.ok(data.toString())
    }

    // ---- /v1/ui/find ---------------------------------------------------------

    fun handleFind(req: ConsoleRequest, rt: UiRuntime): String {
        val body = runCatching { JSONObject(req.body) }.getOrNull()
            ?: return Envelope.err(400, "body must be a JSON object")
        val selector = parseSelector(body)
            ?: return Envelope.err(400, "selector requires at least one of testTag/text/textContains/className/role/clickable")
        val hits = selectNodes(rt.snapshotNodes(), selector)
        val data = JSONObject()
            .put("hits", JSONArray().apply { hits.forEach { put(nodeToJson(it, withCenter = true)) } })
            .put("total", hits.size)
        return Envelope.ok(data.toString())
    }

    // ---- /v1/ui/action -------------------------------------------------------

    fun handleAction(req: ConsoleRequest, rt: UiRuntime): String {
        val body = runCatching { JSONObject(req.body) }.getOrNull()
            ?: return Envelope.err(400, "body must be a JSON object")
        val action = body.optString("action")
        val selector = if (body.has("selector") && !body.isNull("selector"))
            parseSelector(body.getJSONObject("selector")) else null
        val before = fingerprintOf(rt.activityName(), rt.snapshotNodes(), rt.focusTag(), rt.visibleTextHash())

        fun ok(matched: Int): String {
            val after = fingerprintOf(rt.activityName(), rt.snapshotNodes(), rt.focusTag(), rt.visibleTextHash())
            return Envelope.ok(JSONObject()
                .put("matched", matched)
                .put("diff", diffFingerprints(before, after))
                .toString())
        }

        // selector 命中集（带选择器的动作统一走这里；无命中 = 404，不猜坐标）
        fun target(): Pair<UiNode?, Int> {
            val hits = selector?.let { selectNodes(rt.snapshotNodes(), it) } ?: emptyList()
            return if (selector != null) Pair(hits.firstOrNull(), hits.size) else Pair(null, 0)
        }

        return when (action) {
            "tap", "longTap", "doubleTap" -> {
                val (node, matched) = if (selector != null) target() else Pair(null, 0)
                if (selector != null && node == null) return Envelope.err(404, "no node matches the selector")
                val x = if (node != null) node.bounds.centerX else if (body.has("x")) body.getInt("x") else -1
                val y = if (node != null) node.bounds.centerY else if (body.has("y")) body.getInt("y") else -1
                if (x < 0 || y < 0) return Envelope.err(400, "tap needs a matching selector or {x,y}")
                val hold = if (body.has("durationMs")) body.getLong("durationMs") else null
                val done = rt.tap(node, x, y, if (action == "longTap") hold else null, action == "doubleTap")
                if (!done) Envelope.err(500, "gesture dispatch failed or timed out") else ok(matched)
            }
            "swipe", "drag" -> {
                val (node, _) = if (selector != null) target() else Pair(null, 0)
                if (selector != null && node == null) return Envelope.err(404, "no node matches the selector")
                val sx = if (body.has("x")) body.getInt("x") else node?.bounds?.centerX ?: return Envelope.err(400, "swipe needs start {x,y} or a matching selector")
                val sy = if (body.has("y")) body.getInt("y") else node?.bounds?.centerY ?: return Envelope.err(400, "swipe needs start {x,y} or a matching selector")
                val dur = if (body.has("durationMs")) body.getLong("durationMs") else 120L
                // 显式端点 {toX,toY} 优先且必须成对；否则方向+位移推导终点
                val (ex, ey) = when {
                    body.has("toX") && body.has("toY") -> Pair(body.getInt("toX"), body.getInt("toY"))
                    body.has("toX") || body.has("toY") -> return Envelope.err(400, "explicit swipe end needs both {toX,toY}")
                    else -> {
                        val (dx, dy) = when (val d = body.optString("direction")) {
                            "up" -> Pair(0, -1)
                            "down" -> Pair(0, 1)
                            "left" -> Pair(-1, 0)
                            "right" -> Pair(1, 0)
                            else -> return Envelope.err(400, "swipe needs {toX,toY} or direction in down|up|left|right, got: ${d.ifBlank { "(missing)" }}")
                        }
                        val amount = if (body.has("amount")) body.getInt("amount") else 300
                        Pair(sx + dx * amount, sy + dy * amount)
                    }
                }
                if (!rt.swipe(sx, sy, ex, ey, dur)) return Envelope.err(500, "gesture dispatch failed or timed out")
                ok(if (node != null) 1 else 0)
            }
            "scroll" -> {
                val direction = body.optString("direction")
                if (direction !in SCROLL_DIRS) return Envelope.err(400, "scroll direction must be down|up|left|right, got: ${direction.ifBlank { "(missing)" }}")
                val (node, _) = if (selector != null) target() else Pair(null, 0)
                if (selector != null && node == null) return Envelope.err(404, "no node matches the selector")
                val amount = if (body.has("amount")) body.getInt("amount") else 400
                if (!rt.scroll(direction, amount, node)) return Envelope.err(500, "gesture dispatch failed or timed out")
                ok(if (node != null) 1 else 0)
            }
            "input" -> {
                val text = if (body.has("text")) body.getString("text") else ""
                if (text.isEmpty()) return Envelope.err(400, "input requires non-empty \"text\"")
                val (node, matched) = if (selector != null) target() else Pair(null, 0)
                if (selector != null && node == null) return Envelope.err(404, "no node matches the selector")
                if (!rt.inputText(text, node)) return Envelope.err(500, "text input failed: no target view")
                ok(matched)
            }
            "back", "home", "recents", "key" -> {
                val kc = when (action) {
                    "back" -> 4   // KEYCODE_BACK
                    "home" -> 3   // KEYCODE_HOME
                    "recents" -> 187 // KEYCODE_APP_SWITCH
                    else -> if (body.has("keyCode")) body.getInt("keyCode") else return Envelope.err(400, "action \"key\" requires numeric \"keyCode\"")
                }
                if (!rt.systemKey(action, kc)) return Envelope.err(500, "key dispatch failed or timed out")
                ok(0)
            }
            else -> Envelope.err(400, "unknown action: ${action.ifBlank { "(missing)" }}")
        }
    }

    // ---- /v1/ui/screenshot ---------------------------------------------------

    fun handleScreenshot(rt: UiRuntime): String {
        val png = rt.screenshotPng() ?: return Envelope.err(500, "screenshot unavailable (no window or capture failed)")
        return Envelope.ok(JSONObject()
            .put("format", "png")
            .put("bytes", png.size)
            .put("image", base64Encode(png))
            .toString())
    }

    // ---- 装配 -----------------------------------------------------------------

    fun registerAll(rt: () -> UiRuntime) {
        ConsoleBootstrap.addHandler("/v1/ui/tree", "GET", requiresToken = true) {
            handleTree(rt())
        }
        ConsoleBootstrap.addHandler("/v1/ui/find", "POST", requiresToken = true) { req ->
            handleFind(req, rt())
        }
        ConsoleBootstrap.addHandler("/v1/ui/action", "POST", requiresToken = true) { req ->
            handleAction(req, rt())
        }
        ConsoleBootstrap.addHandler("/v1/ui/screenshot", "GET", requiresToken = true) {
            handleScreenshot(rt())
        }
    }
}
