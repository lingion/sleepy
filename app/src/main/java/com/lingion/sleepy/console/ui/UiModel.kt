package com.lingion.sleepy.console.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Task 3 ui-ops 纯逻辑模型 — 零 Android 依赖，JVM 直测面。
 *
 * 定位链事实（SPEC §3 ui/find / ui/action）：
 *  - [UiNode] 一个可定位节点的快照：语义 tag / 文本 / 类名 / role / 可点击 / 可达 + 屏幕绝对 bounds；
 *  - [UiSelector] 六个判据全部 AND；空判据（或只带 index）不构成选择器；
 *  - [selectNodes] 保持遍历顺序返回命中（order 即序位），index 非空时取第 index 个
 *    （越界/负数 = 空命中，宁空勿猜——盲操作错节点比不操作更危险）；
 *  - [UiFingerprint]/[diffFingerprints] 动作前后 UI 指纹对比（SPEC §3：tag 集 + 焦点 + 文本哈希）。
 */

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = left + (right - left) / 2
    val centerY: Int get() = top + (bottom - top) / 2
    fun toJson(): JSONObject = JSONObject().put("l", left).put("t", top).put("r", right).put("b", bottom)
}

data class UiNode(
    val tag: String?,
    val text: String?,
    val className: String?,
    val role: String?,
    val clickable: Boolean,
    val reachable: Boolean,
    val bounds: Bounds,
    val order: Int = -1,
)

data class UiSelector(
    val testTag: String? = null,
    val text: String? = null,
    val textContains: String? = null,
    val className: String? = null,
    val role: String? = null,
    /** 可点击性判据：非 null 时要求节点可点击状态相等（true=只要可点，false=只要不可点）。 */
    val clickable: Boolean? = null,
    val index: Int? = null,
) {
    /** 至少一个内容判据才算选择器（index 只是取法，不是判据）。 */
    val hasCriteria: Boolean
        get() = listOf<Any?>(testTag, text, textContains, className, role, clickable).any {
            it != null && it != ""
        }

    fun matches(n: UiNode): Boolean {
        if (testTag != null && n.tag != testTag) return false
        if (text != null && n.text != text) return false
        if (textContains != null && (n.text?.contains(textContains, ignoreCase = true) != true)) return false
        if (className != null && !classNameMatches(n.className, className)) return false
        if (role != null && !role.equals(n.role, ignoreCase = true)) return false
        if (clickable != null && n.clickable != clickable) return false
        return true
    }
}

/** className 判据：等值或末段相等（"androidx.appcompat.widget.AppCompatButton" 可用 "Button" 命中）。 */
private fun classNameMatches(nodeClass: String?, want: String): Boolean {
    if (nodeClass == null) return false
    if (nodeClass == want) return true
    return nodeClass.substringAfterLast('.').equals(want, ignoreCase = true)
}

/** 遍历顺序过滤；index 判据在命中集上二次取位。 */
fun selectNodes(nodes: List<UiNode>, selector: UiSelector): List<UiNode> {
    val hits = nodes.filter { selector.matches(it) }.mapIndexed { i, n -> n.copy(order = i) }
    val idx = selector.index ?: return hits
    if (idx < 0 || idx >= hits.size) return emptyList()
    return listOf(hits[idx])
}

fun parseSelector(o: JSONObject): UiSelector? {
    val s = UiSelector(
        testTag = o.optString("testTag").takeIf { it.isNotEmpty() },
        text = o.optString("text").takeIf { it.isNotEmpty() },
        textContains = o.optString("textContains").takeIf { it.isNotEmpty() },
        className = o.optString("className").takeIf { it.isNotEmpty() },
        role = o.optString("role").takeIf { it.isNotEmpty() },
        clickable = if (o.has("clickable") && !o.isNull("clickable")) o.optBoolean("clickable") else null,
        index = if (o.has("index") && !o.isNull("index")) o.optInt("index") else null,
    )
    return if (s.hasCriteria) s else null
}

data class UiFingerprint(
    val activity: String?,
    val tags: List<String>,
    val focus: String?,
    val textHash: String,
)

fun fingerprintOf(
    activity: String?,
    nodes: List<UiNode>,
    focus: String?,
    textHash: String,
): UiFingerprint = UiFingerprint(
    activity = activity,
    tags = nodes.mapNotNull { it.tag }.distinct().sorted(),
    focus = focus,
    textHash = textHash,
)

/** find/tree 的节点序列化形态（center 只在 find 用得到时附加）。 */
fun nodeToJson(n: UiNode, withCenter: Boolean = false): JSONObject {
    val o = JSONObject()
        .put("tag", n.tag ?: JSONObject.NULL)
        .put("text", n.text ?: JSONObject.NULL)
        .put("className", n.className ?: JSONObject.NULL)
        .put("role", n.role ?: JSONObject.NULL)
        .put("clickable", n.clickable)
        .put("reachable", n.reachable)
        .put("bounds", n.bounds.toJson())
    if (withCenter) {
        o.put("center", JSONObject().put("x", n.bounds.centerX).put("y", n.bounds.centerY))
    }
    return o
}

/**
 * 动作前后指纹 diff（SPEC §3 ui/action "返回动作后 UI 指纹 diff"）：
 * tag 增删集、焦点迁移、文本哈希变化、Activity 迁移。
 */
fun diffFingerprints(before: UiFingerprint, after: UiFingerprint): JSONObject {
    val appeared = after.tags.toSet() - before.tags.toSet()
    val disappeared = before.tags.toSet() - after.tags.toSet()
    return JSONObject()
        .put("appeared", JSONArray(appeared.sorted()))
        .put("disappeared", JSONArray(disappeared.sorted()))
        .put("focus", JSONObject().put("before", before.focus ?: JSONObject.NULL).put("after", after.focus ?: JSONObject.NULL))
        .put("textChanged", before.textHash != after.textHash)
        .put("activityChanged", before.activity != after.activity)
}

/** 指纹的 JSON 形态（tree 端点 data.fingerprint）。 */
fun fingerprintJson(fp: UiFingerprint): JSONObject = JSONObject()
    .put("activity", fp.activity ?: JSONObject.NULL)
    .put("tags", JSONArray(fp.tags))
    .put("focus", fp.focus ?: JSONObject.NULL)
    .put("textHash", fp.textHash)

/**
 * 纯 Kotlin Base64（RFC 4648 标准表 + padding）：screenshot 的 data.image 用。
 * 不用 android.util.Base64（JVM 单测里是 not-mocked 桩）也不用
 * java.util.Base64（minSdk 26，API 26 下 desugar 覆盖不保证）——自实现零风险且可测。
 */
private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

fun base64Encode(bytes: ByteArray): String {
    val sb = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i + 3 <= bytes.size) {
        val n = (bytes[i].toInt() and 0xFF shl 16) or
            (bytes[i + 1].toInt() and 0xFF shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        sb.append(B64[n shr 18 and 63]).append(B64[n shr 12 and 63])
            .append(B64[n shr 6 and 63]).append(B64[n and 63])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val n = bytes[i].toInt() and 0xFF
            sb.append(B64[n shr 2]).append(B64[n shl 4 and 63]).append("==")
        }
        2 -> {
            val n = (bytes[i].toInt() and 0xFF shl 8) or (bytes[i + 1].toInt() and 0xFF)
            sb.append(B64[n shr 10]).append(B64[n shr 4 and 63]).append(B64[n shl 2 and 63]).append('=')
        }
    }
    return sb.toString()
}

/** tree 的文字形态 dump（每行一节点，人读友好；机读走 nodes 数组）。 */
fun dumpLines(nodes: List<UiNode>): List<String> = nodes.map { n ->
    buildString {
        append(n.className ?: "?")
        n.tag?.let { append(" tag=").append(it) }
        n.text?.let { append(" text=\"").append(it.replace("\n", " ")).append('"') }
        n.role?.let { append(" role=").append(it) }
        if (n.clickable) append(" [clickable]")
        if (!n.reachable) append(" [unreachable]")
        append(" (").append(n.bounds.left).append(',').append(n.bounds.top)
        append('×').append(n.bounds.right - n.bounds.left).append('x').append(n.bounds.bottom - n.bounds.top).append(')')
    }
}

/** 文本哈希：全部可见文本按序连接后取稳定 hash（指纹的"内容变了没"维度）。 */
fun textHashOf(texts: List<String>): String {
    var h = 1129781269251L // kotlin "abc".hashCode 级别无所谓，取 FNV 风格稳定值
    for (t in texts) {
        for (ch in t) {
            h = (h xor ch.code.toLong()) * 1099511628211L
        }
        h = (h xor 0x1L) * 1099511628211L
    }
    return h.toULong().toString(16)
}
