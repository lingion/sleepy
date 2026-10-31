package com.lingion.sleepy.console

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 1 bridge 单元测试 — TokenStore / Envelope / AuditLog / DebugGate / CommandRouter。
 *
 * 锁的是纯逻辑面（SPEC §6：鉴权拒绝路径必有红绿用例）。NanoHTTPD 真 socket 行为
 * 属设备侧验证（Task 8 instrumented + agent 实测），unit JVM 层只锁分发/鉴权/审计逻辑。
 */
class BridgeUnitTest {

    private var fakeNow = 1_000_000L

    private val now: () -> Long = { fakeNow }

    @Before
    fun setUp() {
        fakeNow = 1_000_000L
        DebugGate.reset(forTestNow = now)
        TokenStore.reset(passphrase = "right-pass", forTestNow = now)
        CommandRouter.reset()
    }

    @After
    fun tearDown() {
        val sys = { System.currentTimeMillis() }
        DebugGate.reset(forTestNow = sys)
        TokenStore.reset(passphrase = "", forTestNow = sys)
        CommandRouter.reset()
    }

    // ---- TokenStore: 口令签发 ----

    @Test
    fun unlock_with_correct_passphrase_while_armed_issues_token() {
        DebugGate.armUnlock()
        val r = TokenStore.attemptUnlock("right-pass")
        assertEquals(UnlockResult.OK, r)
        val token = TokenStore.issue()
        assertNotNull(token)
        assertTrue("签发的 token 必须当场可验", TokenStore.verify(token))
    }

    @Test
    fun unlock_with_wrong_passphrase_returns_WRONG_PASSPHRASE_and_issues_nothing() {
        DebugGate.armUnlock()
        val r = TokenStore.attemptUnlock("wrong-pass")
        assertEquals(UnlockResult.WRONG_PASSPHRASE, r)
        assertNull(TokenStore.issue())
    }

    @Test
    fun unlock_returns_NOT_ARMED_when_armedAt_null() {
        val r = TokenStore.attemptUnlock("right-pass")
        assertEquals(UnlockResult.NOT_ARMED, r)
        assertNull(TokenStore.issue())
    }

    @Test
    fun unlock_returns_NOT_ARMED_when_arm_expired_past_60s() {
        DebugGate.armUnlock()
        fakeNow += 61_000L
        val r = TokenStore.attemptUnlock("right-pass")
        assertEquals(UnlockResult.NOT_ARMED, r)
        assertFalse(DebugGate.isUnlockArmed)
        assertNull(TokenStore.issue())
    }

    @Test
    fun unlock_still_accepted_at_exactly_60s_boundary() {
        DebugGate.armUnlock()
        fakeNow += 60_000L
        assertTrue("60s 整点仍在等待解锁窗口内(过期判定用 >)", DebugGate.isUnlockArmed)
        assertEquals(UnlockResult.OK, TokenStore.attemptUnlock("right-pass"))
    }

    // ---- TokenStore: 错 3 次指数退避 ----

    @Test
    fun three_wrong_attempts_then_even_correct_passphrase_is_BACKOFF() {
        DebugGate.armUnlock()
        repeat(3) { assertEquals(UnlockResult.WRONG_PASSPHRASE, TokenStore.attemptUnlock("bad")) }
        val r = TokenStore.attemptUnlock("right-pass")
        assertEquals("退避窗口内口令对也拒发", UnlockResult.BACKOFF, r)
        assertNull(TokenStore.issue())
    }

    @Test
    fun backoff_window_is_8s_after_three_fails() {
        DebugGate.armUnlock()
        repeat(3) { TokenStore.attemptUnlock("bad") }
        fakeNow += 7_999L
        assertEquals("窗口未到仍拒", UnlockResult.BACKOFF, TokenStore.attemptUnlock("right-pass"))
        fakeNow += 1L // 距第 3 次失败整 8s
        assertEquals("窗口已过恢复校验", UnlockResult.OK, TokenStore.attemptUnlock("right-pass"))
    }

    @Test
    fun backoff_window_doubles_after_fourth_fail() {
        DebugGate.armUnlock()
        repeat(3) { TokenStore.attemptUnlock("bad") }
        fakeNow += 9_000L // 8s 窗口已过 → 第 4 次尝试放行校验，错 → failCount=4，窗口 16s
        assertEquals(UnlockResult.WRONG_PASSPHRASE, TokenStore.attemptUnlock("bad"))
        fakeNow += 15_000L
        assertEquals(UnlockResult.BACKOFF, TokenStore.attemptUnlock("right-pass"))
        fakeNow += 1_000L
        assertEquals(UnlockResult.OK, TokenStore.attemptUnlock("right-pass"))
    }

    // ---- TokenStore: token 24h 过期 ----

    @Test
    fun verify_rejects_expired_token_after_24h() {
        DebugGate.armUnlock()
        assertEquals(UnlockResult.OK, TokenStore.attemptUnlock("right-pass"))
        val token = TokenStore.issue()!!
        assertTrue(TokenStore.verify(token))
        fakeNow += 24L * 3_600_000L + 1L
        assertFalse("token 必须 24h 硬过期", TokenStore.verify(token))
        assertNull("过期后 issue() 不再暴露 token", TokenStore.issue())
    }

    @Test
    fun verify_rejects_null_wrong_and_tampered_tokens() {
        DebugGate.armUnlock()
        TokenStore.attemptUnlock("right-pass")
        val token = TokenStore.issue()!!
        assertFalse(TokenStore.verify(null))
        assertFalse(TokenStore.verify(""))
        val tampered = if (token.first() == 'a') "b" + token.drop(1) else "a" + token.drop(1)
        assertFalse(TokenStore.verify(tampered))
    }

    // ---- Envelope: 统一响应形状 ----

    @Test
    fun err_401_envelope_has_exact_shape() {
        val o = JSONObject(Envelope.err(401, "missing token"))
        assertFalse(o.getBoolean("ok"))
        assertEquals("missing token", o.getString("error"))
        assertEquals(401, o.getInt("code"))
    }

    @Test
    fun ok_envelope_wraps_json_data_verbatim() {
        val body = Envelope.ok("""{"paths":["/v1/hello"],"active":true}""")
        val o = JSONObject(body)
        assertTrue(o.getBoolean("ok"))
        val data = o.getJSONObject("data")
        assertEquals("/v1/hello", data.getJSONArray("paths").getString(0))
        assertTrue(data.getBoolean("active"))
    }

    @Test
    fun ok_envelope_without_data_emits_empty_object() {
        val o = JSONObject(Envelope.ok(null))
        assertTrue(o.getBoolean("ok"))
        assertEquals("{}", o.getJSONObject("data").toString())
    }

    // ---- AuditLog: 只记端点与 token 后 4 位，绝不记 body ----

    @Test
    fun audit_record_contains_no_request_body_and_only_token_suffix() {
        val audit = AuditLog(capacity = 200)
        audit.record(
            method = "POST",
            path = "/v1/data/course",
            token = "ABCDEFGH9999",
            responseCode = 200,
            nowMs = 1_700_000_000_000L,
        )
        val o = audit.entries().single()
        assertEquals("POST", o.getString("method"))
        assertEquals("/v1/data/course", o.getString("path"))
        assertEquals("9999", o.getString("tokenSuffix"))
        assertTrue("成功响应 ok=true", o.getBoolean("ok"))
        val json = o.toString()
        assertFalse("全 token 禁入审计", json.contains("ABCDEFGH"))
        assertFalse("请求体禁入审计", json.contains("body"))
    }

    @Test
    fun audit_record_with_null_token_and_error_code() {
        val audit = AuditLog(capacity = 4)
        audit.record("POST", "/v1/enable", null, 401, 1L)
        val o = audit.entries().single()
        assertFalse("无 token 时不得出现 tokenSuffix", o.has("tokenSuffix"))
        assertFalse(o.has("token"))
        assertFalse(o.has("body"))
        assertFalse(o.getBoolean("ok"))
        assertEquals(401, o.getInt("code"))
    }

    @Test
    fun audit_ring_buffer_drops_oldest_beyond_capacity() {
        val audit = AuditLog(capacity = 200)
        repeat(250) { i -> audit.record("GET", "/p/$i", null, 200, i.toLong()) }
        val es = audit.entries()
        assertEquals(200, es.size)
        assertEquals("/p/50", es.first().getString("path"))
        assertEquals("/p/249", es.last().getString("path"))
    }

    // ---- DebugGate: 进程态 + 失效倒计时 ----

    @Test
    fun gate_activate_deactivate_flips_isActive() {
        assertFalse(DebugGate.isActive)
        DebugGate.activate(debugBuild = true, releaseUnlocked = false)
        assertTrue(DebugGate.isActive)
        assertTrue(DebugGate.debugBuild)
        assertFalse(DebugGate.isReleaseUnlocked)
        DebugGate.deactivate()
        assertFalse(DebugGate.isActive)
    }

    @Test
    fun gate_arm_expiry_countdown_at_60s_plus_one() {
        DebugGate.armUnlock()
        assertTrue(DebugGate.isUnlockArmed)
        assertNotNull(DebugGate.unlockArmedAt)
        fakeNow += 60_001L
        assertFalse(DebugGate.isUnlockArmed)
    }

    @Test
    fun gate_invalidate_clears_everything_immediately() {
        DebugGate.activate(debugBuild = false, releaseUnlocked = true)
        DebugGate.armUnlock()
        DebugGate.invalidate()
        assertFalse(DebugGate.isActive)
        assertFalse(DebugGate.isReleaseUnlocked)
        assertNull(DebugGate.unlockArmedAt)
    }

    // ---- CommandRouter: 注册分发扩展点（后续 7 个任务全靠它挂端点）----

    @Test
    fun router_dispatches_registered_route_to_handler() = runBlocking {
        CommandRouter.register("/v1/test", "POST", requiresToken = false) { req ->
            Envelope.ok("\"echo=${req.body}\"")
        }
        val body = CommandRouter.dispatch("/v1/test", "POST", ConsoleRequest(body = "ping"))
        val o = JSONObject(body)
        assertTrue(o.getBoolean("ok"))
        assertEquals("echo=ping", o.getString("data"))
    }

    @Test
    fun router_path_and_method_must_both_match_or_404() = runBlocking {
        CommandRouter.register("/v1/only", "GET", requiresToken = true) { Envelope.ok(null) }
        assertEquals(404, JSONObject(CommandRouter.dispatch("/v1/only", "POST", ConsoleRequest(body = ""))).getInt("code"))
        assertEquals(404, JSONObject(CommandRouter.dispatch("/v1/nope", "GET", ConsoleRequest(body = ""))).getInt("code"))
        assertTrue(CommandRouter.requiresTokenFor("/v1/only", "GET"))
    }

    @Test
    fun router_handler_crash_maps_to_500_not_throw() = runBlocking {
        CommandRouter.register("/v1/boom", "POST", requiresToken = false) { throw IllegalStateException("x") }
        val o = JSONObject(CommandRouter.dispatch("/v1/boom", "POST", ConsoleRequest(body = "")))
        assertFalse(o.getBoolean("ok"))
        assertEquals(500, o.getInt("code"))
    }

    @Test
    fun router_lists_registered_paths_for_hello_capabilities() {
        CommandRouter.register("/v1/a", "GET", requiresToken = true) { Envelope.ok(null) }
        CommandRouter.register("/v1/b", "POST", requiresToken = true) { Envelope.ok(null) }
        val paths = CommandRouter.registeredPaths()
        assertTrue(paths.contains("/v1/a") && paths.contains("/v1/b"))
    }

    // ---- 默认 handler（bootstr 注册通道, hello/enable 免 token）----

    @Test
    fun default_hello_reports_capabilities_and_gate_state() = runBlocking {
        ConsoleBootstrap.registerDefaultHandlers()
        val o = JSONObject(CommandRouter.dispatch("/v1/hello", "POST", ConsoleRequest(body = "")))
        assertTrue(o.getBoolean("ok"))
        val data = o.getJSONObject("data")
        val paths = data.getJSONArray("paths")
        val listed = (0 until paths.length()).map { paths.getString(it) }
        assertTrue("能力清单必须含已注册端点", listed.containsAll(listOf("/v1/hello", "/v1/enable")))
        assertFalse(data.getBoolean("debugBuild")) // 单测 JVM 里 gate 未 activate
        assertFalse(data.getBoolean("unlocked"))
        assertFalse(data.getBoolean("active"))
    }

    @Test
    fun default_enable_returns_unlock_result_and_token_only_on_ok() = runBlocking {
        ConsoleBootstrap.registerDefaultHandlers()
        // 未 armed → NOT_ARMED，data 不含 token
        val o1 = JSONObject(CommandRouter.dispatch(
            "/v1/enable", "POST", ConsoleRequest(body = """{"passphrase":"right-pass"}"""),
        ))
        assertEquals("NOT_ARMED", o1.getJSONObject("data").getString("result"))
        assertFalse(o1.getJSONObject("data").has("token"))
        // armed + 口令对 → OK，data 带 token（供后续请求头使用, 禁入日志）
        DebugGate.armUnlock()
        val o2 = JSONObject(CommandRouter.dispatch(
            "/v1/enable", "POST", ConsoleRequest(body = """{"passphrase":"right-pass"}"""),
        ))
        assertEquals("OK", o2.getJSONObject("data").getString("result"))
        val token = o2.getJSONObject("data").getString("token")
        assertTrue(TokenStore.verify(token))
    }

    @Test
    fun default_enable_bad_json_yet_still_reports_NOT_ARMED_or_WRONG() = runBlocking {
        ConsoleBootstrap.registerDefaultHandlers()
        val o = JSONObject(CommandRouter.dispatch("/v1/enable", "POST", ConsoleRequest(body = "not-json")))
        assertFalse(o.getBoolean("ok"))
        assertEquals(400, o.getInt("code"))
    }
}
