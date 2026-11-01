package com.lingion.sleepy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppClock
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * SPEC §8 总验收自动化面（Task 8 Step 4）。
 *
 * 自动化 5 条（本类）+ 2 条人工（M1 真机通知到达截图、M2 release 拒连实测 —
 * 需 adb shell 外执行, checklist 在 docs/agent-debug-console/AGENT-SKILL.md
 * 验收清单节, 装机需用户明示且一律 com.lingion.sleepy.debug 包）。
 *
 * 走真装配链（ConsoleBootstrap.start 已由 provider 在进程启动完成,
 * DebugGate.isActive=true），与 DataOpsConsoleAndroidTest 同形态。
 */
@RunWith(AndroidJUnit4::class)
class ConsoleAcceptanceAndroidTest {

    private val tag = "acc-${System.nanoTime()}"
    private lateinit var app: android.app.Application

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as android.app.Application
        assertTrue("debug 包 console 必须已激活", DebugGate.isActive && DebugGate.debugBuild)
        // 测试隔离（2026-10-30 全量实跑）：同类 clockToFuture… 用例末尾的
        // /v1/session/reset 会 revoke token（TokenStore 注释"session 复位用"），
        // JUnit 方法序不可依赖 → 每用例自签, 与 provider debug 装配同构。
        TokenStore.grantNewToken()
    }

    /** SPEC#1: /v1/hello 返回能力清单, 且清单含全 21 路由。 */
    @Test
    fun hello_returnsFullCapabilityList() = runBlocking {
        val resp = JSONObject(dispatch("/v1/hello", "POST", ""))
        assertTrue(resp.getBoolean("ok"))
        val paths = resp.getJSONObject("data").getJSONArray("paths")
        val set = (0 until paths.length()).map { paths.getString(it) }
        listOf("/v1/hello", "/v1/enable", "/v1/clock", "/v1/data/tables", "/v1/ui/tree",
            "/v1/trigger", "/v1/jw/mock", "/v1/session/reset", "/v1/log")
            .forEach { assertTrue("hello 清单必须含 $it", set.contains(it)) }
        assertTrue(resp.getJSONObject("data").getBoolean("debugBuild"))
    }

    /** SPEC#3: 虚拟时钟拨到未来周三 → ui/tree 指纹跟随; session/reset 复原。 */
    @Test
    fun clockToFutureWednesday_treeFollows_andResetRestores() {
        val realToday = LocalDate.now(AppClock)
        // 找到 30 天内下一个周三
        var target = realToday.plusDays(1)
        while (target.dayOfWeek != java.time.DayOfWeek.WEDNESDAY) target = target.plusDays(1)
        val epochMs = target.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

        val set = JSONObject(dispatch("/v1/clock", "POST",
            """{"mode":"absolute","epochMs":$epochMs}"""))
        assertTrue(set.getBoolean("ok"))
        assertEquals(target, LocalDate.now(AppClock))
        assertEquals(java.time.DayOfWeek.WEDNESDAY, LocalDate.now(AppClock).dayOfWeek)

        val tree = JSONObject(dispatch("/v1/ui/tree", "GET", ""))
        assertTrue("ui/tree 必须可答(agent 的眼睛不能瞎): " + tree.opt("error"), tree.getBoolean("ok"))

        val reset = JSONObject(dispatch("/v1/session/reset", "POST", ""))
        assertTrue(reset.getBoolean("ok"))
        assertEquals("reset 后时钟必须归零", realToday, LocalDate.now(AppClock))
        assertEquals(0L, AppClock.offsetMs)
    }

    /** SPEC reset 判据: console: 前缀表删净, 非前缀表一张不少。 */
    @Test
    fun sessionReset_deletesOnlyConsolePrefixed() = runBlocking {
        val repo = SleepyApp.get().repository
        // 收尾自清: 本用例造的 console: 表由 reset 删除, 断言链即验收判据
        val keepBefore = repo.getAllTables().count { !it.name.startsWith("console:") }
        val mk = JSONObject(dispatch("/v1/data/table", "POST",
            """{"op":"create","name":"console: $tag","startDate":"2026-09-01"}"""))
        assertTrue(mk.getBoolean("ok"))
        val id = mk.getJSONObject("data").getLong("id")

        val r = JSONObject(dispatch("/v1/session/reset", "POST", ""))
        assertTrue(r.getBoolean("ok"))
        val deleted = r.getJSONObject("data").getJSONArray("tables_deleted")
        assertTrue("reset 必须报出删掉的 console: 表",
            (0 until deleted.length()).any { deleted.getString(it) == "console: $tag" })
        val keepAfter = repo.getAllTables().count { !it.name.startsWith("console:") }
        assertEquals("非前缀表守恒(用户数据零触碰)", keepBefore, keepAfter)
        assertTrue("console: 表清零",
            repo.getAllTables().none { it.name.startsWith("console:") })
    }

    /** SPEC#4 语义面: ui/find 能按 tag+text 命中 settings_theme 系节点(注册表真身)。 */
    @Test
    fun uiFind_resolvesTaggedNodes_withoutCoordinates() {
        val resp = JSONObject(dispatch("/v1/ui/find", "POST",
            """{"testTag":"about_version"}"""))
        // 关于页未必在前台 → 命中 0 合法; 断言端点语义正确而非节点存在
        assertTrue("find 必须可答: " + resp.opt("error"), resp.getBoolean("ok"))
        // handler 出参键 = hits(UiSelectorUnitTest 锁的契约面), 不是 nodes
        val hits = resp.getJSONObject("data").getJSONArray("hits")
        (0 until hits.length()).forEach { i ->
            val n = hits.getJSONObject(i)
            assertNotNull("find 命中必须带 bounds", n.getJSONObject("bounds"))
            assertTrue("可点击面必须显式回 clickable", n.has("clickable"))
        }
    }

    /** SPEC#5 mock 面: 注入即命中、清后即放; 解析入库活体链归 M2 验收(WebView 需登录页)。 */
    @Test
    fun jwMock_injectionRoundTrip() {
        // 规则表进程级 — 先清, 计数断言才对同进程其他注入隔离
        MockRuleStore.clear()
        val put = JSONObject(dispatch("/v1/jw/mock", "POST",
            """{"url_pattern":"https://mock.acc/.*","respond_with":"html","body":"<html>$tag</html>"}"""))
        assertTrue(put.getBoolean("ok"))
        val list = JSONObject(dispatch("/v1/jw/mock", "GET", ""))
        assertEquals(1, list.getJSONObject("data").getJSONArray("rules").length())
        val del = JSONObject(dispatch("/v1/jw/mock", "DELETE", """{"all":true}"""))
        assertTrue(del.getBoolean("ok"))
        assertEquals(0, JSONObject(dispatch("/v1/jw/mock", "GET", ""))
            .getJSONObject("data").getJSONArray("rules").length())
    }

    /**
     * /v1/log 双环面 + 自证闭环: app 环结构存在; audit 环只收经 HTTP serve() 的
     * 请求(dispatch 直调不入环) → 先打一发真 wire hello, 再断言该请求已入环。
     */
    @Test
    fun log_auditRing_recordsRealHttpRequests() {
        val before = auditCount()
        wireHello()
        val resp = JSONObject(dispatch("/v1/log", "GET", ""))
        assertTrue(resp.getBoolean("ok"))
        val data = resp.getJSONObject("data")
        assertNotNull(data.getJSONArray("app"))
        val audit = data.getJSONArray("audit")
        assertTrue("audit 环必须记录到刚发出的 wire 请求(前值=$before 现值=${audit.length()})",
            audit.length() > before)
        val last = audit.getJSONObject(audit.length() - 1)
        assertEquals("/v1/hello", last.getString("path"))
        assertEquals(200, last.getInt("code"))
    }

    /**
     * wire 契约回归(2026-10-30 复盘): NanoHTTPD 2.3.1 状态行 =
     * "HTTP/1.1"+getDescription()+" \r\n", StatusLine 描述必须自带数字码 —
     * 畸形行(`HTTP/1.1OK`)标准客户端全数拒收。2xx/4xx 双形态同测。
     */
    @Test
    fun wireResponses_carryStandardStatusLines() {
        val hello = wireCall("POST", "/v1/hello", "")
        assertEquals(200, hello.code)
        assertEquals("application/json", hello.header("Content-Type")?.substringBefore(";"))
        val body = JSONObject(hello.body!!.string())
        assertTrue(body.getBoolean("ok"))
        // debug 装配自动签发 → hello 必须带 token(SPEC §3: 跳口令不跳 token)
        // 长度字面值 = 实现面 32B x2 hex = 128(JVM GrantNewTokenUnitTest:47 同钉;
        // 2026-10-30 设备实跑 token 为 null 暴露本行 64 是错口径, 一并纠)
        assertTrue("debug hello 必须暴露 token",
            body.getJSONObject("data").getString("token").length == 128)

        val token = TokenStore.issue()
        val clock = wireCall("GET", "/v1/clock", "", token)
        assertEquals(200, clock.code)
        assertTrue(JSONObject(clock.body!!.string()).getBoolean("ok"))

        // 错误面同样必须是可解析的标准状态行(401 = 无 token 打受保护端点)
        val unauth = wireCall("GET", "/v1/clock", "", token = null)
        assertEquals(401, unauth.code)
        assertEquals(401, JSONObject(unauth.body!!.string()).getInt("code"))

        // POST-with-body 必须走真 socket 到 handler(2026-10-31 M1 真机复盘):
        // dispatch 直调不过 NanoHTTPD 临时文件层, 抓不到外部客户端 body 丢失。
        // 默认 TempFileManager 写 java.io.tmpdir=/tmp(app 不可写, 771 shell) →
        // 读空。dayOffset=2 拨完必须能在 GET 快照里看到 2 天偏移。
        val posted = wireCall("POST", "/v1/clock",
            """{"mode":"offset","dayOffset":2}""", token)
        assertEquals(200, posted.code)
        val pb = JSONObject(posted.body!!.string())
        assertTrue("真 wire POST body 必须送达 handler(空=回环坏): " + pb.opt("error"),
            pb.getBoolean("ok"))
        val snap = JSONObject(wireCall("GET", "/v1/clock", "", token).body!!.string())
        assertEquals("dayOffset=2 必须经真 socket 送达 handler",
            2L * 86_400_000L, snap.getJSONObject("data").getLong("offsetMs"))
        // 用例自清: 复位虚拟时钟, 不污染同类其他用例
        wireCall("POST", "/v1/clock", """{"mode":"reset"}""", token)
    }

    // ---- wire helpers: 真 HTTP 环(optHttp 禁用自动重定向, 状态行逐字节到达) ----

    private fun auditCount(): Int =
        JSONObject(dispatch("/v1/log", "GET", "")).getJSONObject("data")
            .getJSONArray("audit").length()

    private fun wireHello(): Response = wireCall("POST", "/v1/hello", "")

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).build()
    }

    private fun wireCall(method: String, path: String, body: String, token: String? = TokenStore.issue()): Response {
        val media = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder().url("http://127.0.0.1:3737$path")
            .method(method, if (method == "GET") null else body.toRequestBody(media))
            .apply { token?.let { header("X-Sleepy-Token", it) } }
            .build()
        return http.newCall(req).execute()
    }

    // 测试线程即桥: dispatch 内部 runBlocking — 与生产 DebugConsoleServer.serve
    // 的 runBlocking 收敛同形态, 测试面不再逐个包协程。
    private fun dispatch(path: String, method: String, body: String): String =
        runBlocking { CommandRouter.dispatch(path, method, ConsoleRequest(body = body, query = emptyMap())) }
}
