package com.lingion.sleepy.console

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

/**
 * 真 HTTP 环锁定 readBody 的 nanohttpd 2.3.1 三分支语义(2026-10-31 M1 复盘:
 * 只查 map["content"] 键, 全部 POST 恒空 → 控制面写端点外部客户端 100% 400)。
 * 语义探针实测(JVM, 与设备同库同版本; HttpURLConnection 不发体时默认补
 * content-type: application/x-www-form-urlencoded, "不设头"实走 urlencoded 分支):
 *   POST + application/json (任意大小) → map["postData"] = 体字符串(内存, 不落盘)
 *   PUT  (任意 content-type)           → map["content"]  = 临时文件路径(依赖可写 tmpdir)
 *   POST + x-www-form-urlencoded       → 值并入 session.parameters, 体原文只在
 *                                        queryParameterString(decodeParms L839), map 空
 *   POST + 无 content-type(实际=urlencoded) → 同上 queryParameterString 原文
 */
class ReadBodyWireTest {

    private lateinit var server: DebugConsoleServer

    /** echo handler: 把读到的体原样回显, 零业务依赖。 */
    private fun registerEcho() {
        // Envelope 形返回: codeOf 按 ok 字段定码, 裸体会被默认 500
        ConsoleBootstrap.addHandler("/echo", "POST", requiresToken = true) { req ->
            Envelope.ok(JSONObject().put("body", req.body).toString())
        }
        ConsoleBootstrap.addHandler("/echo", "PUT", requiresToken = true) { req ->
            Envelope.ok(JSONObject().put("body", req.body).toString())
        }
    }

    @Before
    fun startServer() {
        CommandRouter.reset()
        // 真 socket 过 serve() 层 AuthGate 真值表(白名单外 = active×valid):
        // JVM 测试面手动装门, 设备面由 ConsoleBootstrap.start 自动装配, 同构
        DebugGate.activate(debugBuild = true, releaseUnlocked = false)
        TokenStore.grantNewToken()
        registerEcho()
        server = DebugConsoleServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.stop()
        CommandRouter.reset()
        TokenStore.revoke()
        DebugGate.reset { System.currentTimeMillis() }
    }

    private fun post(path: String, body: String, contentType: String?, method: String = "POST"): String {
        val conn = URL("http://127.0.0.1:${DebugConsoleServer.PORT}$path")
            .openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("X-Sleepy-Token", TokenStore.issue())
        if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) System.err.println("NON200 $code: $text")
        assertEquals("端点应答 200", 200, code)
        return text
    }

    @Test
    fun post_json_smallBody_arrivesViaPostDataKey() {
        val resp = post("/echo", """{"mode":"offset","dayOffset":2}""", "application/json")
        assertEquals("""{"mode":"offset","dayOffset":2}""", JSONObject(resp).getJSONObject("data").getString("body"))
    }

    @Test
    fun post_json_largeBody_arrivesIntact() {
        // >1KB(512B 读块 × N): 2.3.1 该分支仍走内存拼接, 必须逐字节到齐
        val big = "x".repeat(4096)
        val payload = "{\"pad\":\"$big\"}"
        val resp = post("/echo", payload, "application/json")
        assertEquals(payload, JSONObject(resp).getJSONObject("data").getString("body"))
    }

    @Test
    fun post_noContentType_rawBody_arrives() {
        val resp = post("/echo", """{"n":1}""", contentType = null)
        assertEquals("""{"n":1}""", JSONObject(resp).getJSONObject("data").getString("body"))
    }

    @Test
    fun put_json_bodyArrivesViaTempFile() {
        val resp = post("/echo", """{"a":1}""", "application/json", method = "PUT")
        assertEquals("""{"a":1}""", JSONObject(resp).getJSONObject("data").getString("body"))
    }

    @Test
    fun put_bodyTempFile_isDeletedAfterRead() {
        // 请求体不留痕迹(安全要求): PUT 临时文件读出后即删
        post("/echo", """{"secret":"tmpfile-must-die"}""", "application/json", method = "PUT")
        val leftovers = File(System.getProperty("java.io.tmpdir"))
            .listFiles { f -> f.name.startsWith("NanoHTTPD") } ?: emptyArray()
        assertTrue("临时文件必须读后即删: ${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    @Test
    fun post_urlencoded_rawBodyReachesViaQueryParameterString() {
        // 体原文经 decodeParms 存入 queryParameterString 后到达(L839); parameters
        // 兜底重建在空值键(k=&j=2)/同键多值场景必失真, 原文面才无损
        val resp = post("/echo", "k=v&j=2", "application/x-www-form-urlencoded")
        assertEquals("k=v&j=2", JSONObject(resp).getJSONObject("data").getString("body"))
    }

    @Test
    fun customTempDir_putReadsFromInjectedDir() {
        // 设备修复面锁定: tmpdir 注入可写目录后, content 临时文件分支可用
        server.stop()
        val tmp = File(System.getProperty("java.io.tmpdir"), "console-tmp-test-${System.nanoTime()}")
            .apply { mkdirs() }
        try {
            server = DebugConsoleServer(tmp)
            server.start()
            val resp = post("/echo", """{"mode":"reset"}""", "application/json", method = "PUT")
            assertNotNull(resp)
            assertEquals("""{"mode":"reset"}""", JSONObject(resp).getJSONObject("data").getString("body"))
            assertTrue("注入目录无残留", tmp.listFiles()?.isEmpty() != false)
        } finally {
            tmp.delete()
        }
    }
}
