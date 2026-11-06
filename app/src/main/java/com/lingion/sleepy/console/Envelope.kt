package com.lingion.sleepy.console

/**
 * 统一响应信封（SPEC §3）：`{"ok":true,"data":...}` / `{"ok":false,"error":"...","code":N}`。
 *
 * [ok] 的 data 参数是已编码好的 JSON 文本（handler 返回值原样内嵌，避免二次转义）；
 * null/空白 → `{}`。[err] 只拼纯字符串，绝不把 token/口令拼进 error。
 */
object Envelope {

    fun ok(data: String?): String {
        val payload = data?.takeIf { it.isNotBlank() } ?: "{}"
        return "{\"ok\":true,\"data\":$payload}"
    }

    fun err(code: Int, message: String): String {
        val safe = StringBuilder("\"")
        for (ch in message) {
            when {
                ch == '"' -> safe.append("\\\"")
                ch == '\\' -> safe.append("\\\\")
                ch == '\n' -> safe.append("\\n")
                ch == '\r' -> safe.append("\\r")
                ch == '\t' -> safe.append("\\t")
                ch.code < 0x20 -> safe.append("\\u%04x".format(ch.code))
                else -> safe.append(ch)
            }
        }
        safe.append('"')
        return "{\"ok\":false,\"error\":$safe,\"code\":$code}"
    }
}
