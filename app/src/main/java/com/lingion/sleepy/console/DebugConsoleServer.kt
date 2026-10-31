package com.lingion.sleepy.console

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/**
 * NanoHTTPD 子类 — 绑 127.0.0.1:3737（SPEC §3 传输定稿：回环 TCP，adb forward 驱动）。
 *
 * serve() 单一入口做三件事：
 *  1. 鉴权闸：除 /v1/hello、/v1/enable 外，DebugGate 未激活或 token 校验失败 = 401（无例外，SPEC §5.2）
 *  2. 分发：异步 router 在 NanoHTTPD worker 线程经 runBlocking 收敛为同步（控制面并发即单 agent，可接受）
 *  3. 审计：每请求记一条 [AuditLog]（method+path+token 后 4 位+ok，绝不记 body）
 *
 * 出口统一 [Envelope]，HTTP 状态码与 envelope 的 code 字段对齐。
 */
class DebugConsoleServer : NanoHTTPD(HOST, PORT) {

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 3737
        const val TOKEN_HEADER = "X-Sleepy-Token"
        private const val MIME_JSON = "application/json"
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri ?: "/"
        val method = session.method?.name ?: "GET"
        val token = session.headers.entries
            .firstOrNull { it.key.equals(TOKEN_HEADER, ignoreCase = true) }
            ?.value

        val (status, body) = try {
            handleRequest(method, path, token, session)
        } catch (t: Throwable) {
            // 服务器层兜底：任何未收敛异常也不能裸抛（NanoHTTPD 裸抛会断连接）
            500 to Envelope.err(500, "server failure: ${t::class.simpleName}")
        }
        ConsoleAudit.log.record(method, path, token, status, System.currentTimeMillis())
        return newFixedLengthResponse(StatusLine(status), MIME_JSON, body)
    }

    private fun handleRequest(method: String, path: String, token: String?, session: IHTTPSession): Pair<Int, String> {
        // Task 2 收编：401 判定抽入纯函数 AuthGate（SPEC §6 release 鉴权拒绝路径单测锁真值表）。
        // 真值表不区分构建（SPEC §5.2"无例外"字面成立）；debug 包的可用性由
        // ConsoleBootstrap.bind 时自动签发 token + /v1/hello 暴露保证（SPEC §3
        // "debug 包跳过此步"= 跳口令与连点 arm, 不跳 token 本身）。
        if (AuthGate.shouldReject(path, DebugGate.isActive, TokenStore.verify(token))) {
            return 401 to Envelope.err(401, "missing or invalid token")
        }
        val req = ConsoleRequest(
            body = readBody(session),
            query = session.parms ?: emptyMap(),
        )
        val body = runBlocking { CommandRouter.dispatch(path, method, req) }
        return codeOf(body) to body
    }

    /**
     * NanoHTTPD 2.3.1 把非表单 POST 体写进临时文件、路径放 parseBody 结果的 "content" 键；
     * 控制面只收原始 JSON 体，读出后即删临时文件（请求体不留痕迹）。
     */
    private fun readBody(session: IHTTPSession): String {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val contentPath = files["content"] ?: return ""
            val f = File(contentPath)
            val text = if (f.exists()) f.readText() else ""
            runCatching { f.delete() }
            text
        } catch (_: Throwable) {
            ""
        }
    }

    /** envelope 的 code 决定 HTTP 状态；ok=true 缺省 200。 */
    private fun codeOf(body: String): Int = try {
        val o = JSONObject(body)
        if (o.optBoolean("ok")) 200 else o.optInt("code", 500)
    } catch (_: Exception) {
        500
    }

    /**
     * 精确 HTTP 状态行。不直接用 NanoHTTPD.Status 枚举命名常量——2.3.1 里
     * UNAUTHORIZED=403、FORBIDDEN=401（历史命名错位），直接引用会发错状态码；
     * envelope 的 code 才是真源，这里按数值构造。
     *
     * 契约（2026-10-30 真机/模拟双失败复盘, 本地 NanoHTTPD 2.3.1 源码字节码核验）:
     * NanoHTTPD 组装状态行 = "HTTP/1.1" + getDescription() + " \r\n" — 描述必须
     * 自带数字码（与内置 Status.OK.getDescription()="200 OK" 同构），只回 "OK"
     * 会发出 `HTTP/1.1OK` 式畸形状态行, 标准 HTTP 客户端全数无法解析。
     */
    private class StatusLine(private val code: Int) : Response.IStatus {
        override fun getRequestStatus(): Int = code
        override fun getDescription(): String = when (code) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
            401 -> "401 Unauthorized"
            403 -> "403 Forbidden"
            404 -> "404 Not Found"
            429 -> "429 Too Many Requests"
            else -> "$code SleepyConsole"
        }
    }

    override fun stop() {
        runCatching { super.stop() }
        // 审计环 ConsoleAudit 刻意不清：失活/重绑后 /v1/log 仍能回看本轮证据
    }
}
