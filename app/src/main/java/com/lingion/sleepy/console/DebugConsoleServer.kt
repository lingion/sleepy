package com.lingion.sleepy.console

import com.lingion.sleepy.util.AppLog
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
class DebugConsoleServer(tempDir: File? = null) : NanoHTTPD(HOST, PORT) {

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 3737
        const val TOKEN_HEADER = "X-Sleepy-Token"
        private const val MIME_JSON = "application/json"
        private const val URLENCODED_MIME = "application/x-www-form-urlencoded"
        private const val TAG = "SleepyConsole"
    }

    init {
        // Android 经典坑(2026-10-31 M1 真机复盘): nanohttpd 2.3.1 默认
        // DefaultTempFileManager 把 POST body 写进 java.io.tmpdir — Android 上
        // /tmp 归 shell 771, app 进程不可写 → parseBody 落盘失败, 外部客户端
        // (curl/M1/Python)的 body 恒空。改指 app 私有 cache 子目录。
        // tempDir=null 保持 nanohttpd 默认行为(JVM 测试面 tmpdir 可写)。
        tempDir?.let { setTempFileManagerFactory(CacheTempFileManagerFactory(it)) }
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
     * 请求体读取。nanohttpd 2.3.1 实测语义(2026-10-31 M1 复盘 + 源码逐行核验,
     * 与 javadoc 相反; 行号指 fi/iki/elonen/NanoHTTPD.java 2.3.1):
     *  - POST 非 multipart 非 urlencoded → files["postData"] = 体字符串
     *    (内存分支 <1024B, 大体会经 tmp bucket; L1164–1186)
     *  - POST application/x-www-form-urlencoded → decodeParms 把值并进
     *    session.parameters(L1178–1179), files 为空; 原始体串只留存在
     *    session.queryParameterString(decodeParms L839 先存原串再分词,
     *    且分词只进 parameters 不动 qps)。
     *  - PUT → files["content"] = saveTmpFile 落盘路径(L1187–1189), 依赖可写
     *    tmpdir(构造注入已修)。PATCH 库无分支(L1164/L1187 只判 POST/PUT),
     *    体不可读; console 路由表无 PATCH, 不受影响。
     * 只查 content 会让全部 POST 恒空 — M1 真机复盘根因(读不出体, 写端点全 400)。
     */
    private fun readBody(session: IHTTPSession): String {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val contentPath = files["content"]
            if (!contentPath.isNullOrEmpty()) {
                // PUT: 临时文件读完即删(请求体不留痕迹)
                val f = File(contentPath)
                val text = if (f.exists()) f.readText() else ""
                runCatching { f.delete() }
                text
            } else {
                val postData = files["postData"]
                if (!postData.isNullOrEmpty()) {
                    postData
                } else if (session.method == NanoHTTPD.Method.POST &&
                    session.headers["content-type"]
                        ?.startsWith(URLENCODED_MIME, ignoreCase = true) == true
                ) {
                    // urlencoded 体已不可逆分词; qps 是唯一原始面。URI query 也写
                    // qps(decodeHeader L683), 但 urlencoded POST 的体 decodeParms
                    // 必然后执行(L1179)覆盖之 → 此处 qps 恒为体原文。空体 = ""。
                    session.queryParameterString ?: ""
                } else {
                    ""
                }
            }
        } catch (t: Throwable) {
            // 评审 R1(根因 3 教训): 降级语义保持空体, 但静默吞通道封死 — 异常
            // 类名+message 必进 AppLog(设备侧 /v1/log 可读回), 绝不记请求体本身。
            // 库内 saveTmpFile/getTmpBucket 把真故障包成 java.lang.Error(L1212/
            // L1105), 故记录根因链末端类名; AppLog.e 尾追 t.message + logcat 全栈。
            // JVM 安全: 单测 returnDefaultValues=true, android.util.Log 面返 0 不炸。
            val root = generateSequence(t) { it.cause }.last()
            AppLog.e(TAG, "readBody failed (root: ${root::class.java.name})", t)
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

/**
 * POST body 临时文件工厂 — 复刻 nanohttpd DefaultTempFileManager 语义,
 * 唯一差异 = 落盘目录可注入（app 私有 cache 子目录, 恒可写）。
 * readBody 经 parseBody 的 "content" 键拿到 getName() 绝对路径, 零改动兼容。
 */
internal class CacheTempFileManagerFactory(private val dir: File) :
    NanoHTTPD.TempFileManagerFactory {

    override fun create(): NanoHTTPD.TempFileManager = object : NanoHTTPD.TempFileManager {
        // 可空参数是契约要求: 库内 parseBody→saveTmpFile 固定传 null hint
        // (NanoHTTPD.java L1188/L1204), DefaultTempFileManager 是 Java 无空检;
        // Kotlin 覆写声明非空 String 会被 intrinsic 空检炸成 Error 吃掉整个体
        // (2026-10-31 customTempDir 探针复现: NPE on parameter prefix)。
        override fun createTempFile(prefix: String?): NanoHTTPD.TempFile {
            val suffix = prefix?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
            val file = File(dir, "nanoHTTPD-$suffix-${System.nanoTime()}.tmp")
            return object : NanoHTTPD.TempFile {
                override fun getName(): String = file.absolutePath
                override fun open(): java.io.OutputStream = java.io.BufferedOutputStream(
                    java.io.FileOutputStream(file)
                )
                override fun delete() {
                    runCatching { file.delete() }
                }
            }
        }

        override fun clear() {
            // 每请求 readBody 后即删自文件; 这里兜底清残留(PowerfulResponse 等
            // 未消费路径), 只删本工厂命名前缀, 不碰目录其他内容。
            dir.listFiles()?.filter { it.name.startsWith("nanoHTTPD-") }
                ?.forEach { runCatching { it.delete() } }
        }
    }
}
