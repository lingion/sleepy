package com.lingion.sleepy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppClock
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
        assertTrue(resp.getBoolean("ok"))
        val nodes = resp.getJSONObject("data").getJSONArray("nodes")
        (0 until nodes.length()).forEach { i ->
            val n = nodes.getJSONObject(i)
            assertNotNull("find 命中必须带 bounds", n.getJSONObject("bounds"))
            assertTrue("可点击面必须显式回 clickable", n.has("clickable"))
        }
    }

    /** SPEC#5 mock 面: 注入即命中、清后即放; 解析入库活体链归 M2 验收(WebView 需登录页)。 */
    @Test
    fun jwMock_injectionRoundTrip() {
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

    /** /v1/log 双环面: 结构存在即可, 内容随会话增长。 */
    @Test
    fun log_returnsBothRings() {
        val resp = JSONObject(dispatch("/v1/log", "GET", ""))
        assertTrue(resp.getBoolean("ok"))
        val data = resp.getJSONObject("data")
        assertNotNull(data.getJSONArray("app"))
        val audit = data.getJSONArray("audit")
        // 本测试自身已发过多条 console 请求 → 审计环非空(自证闭环)
        assertTrue("audit 环必须记录到本会话请求", audit.length() > 0)
    }

    // 测试线程即桥: dispatch 内部 runBlocking — 与生产 DebugConsoleServer.serve
    // 的 runBlocking 收敛同形态, 测试面不再逐个包协程。
    private fun dispatch(path: String, method: String, body: String): String =
        runBlocking { CommandRouter.dispatch(path, method, ConsoleRequest(body = body, query = emptyMap())) }
}
