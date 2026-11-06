package com.lingion.sleepy.console

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Task 2 顺带收编 Task 1 遗留两条 Minor：
 *  #1 CommandRouter 运行期注册后 dispatch 立即可见（并发可见性回归锁）；
 *  #2 AuthGate.shouldReject 纯函数锁 SPEC §6 release 鉴权拒绝路径
 *     （hello/enable 白名单 + 其余端点 isActive×tokenValid 三真值组合）。
 */
class ConsoleGateRouterUnitTest {

    @Before
    fun setUp() = CommandRouter.reset()

    @After
    fun tearDown() = CommandRouter.reset()

    // ---- Minor #2: AuthGate 真值表 ----

    @Test
    fun hello_and_enable_never_rejected_regardless_of_gate_and_token() {
        for (active in listOf(true, false)) {
            for (valid in listOf(true, false)) {
                assertFalse("/v1/hello 免 token 白名单", AuthGate.shouldReject("/v1/hello", active, valid))
                assertFalse("/v1/enable 免 token 白名单", AuthGate.shouldReject("/v1/enable", active, valid))
            }
        }
    }

    @Test
    fun other_paths_reject_whenever_gate_inactive_or_token_invalid() {
        val paths = listOf("/v1/data/tables", "/v1/ui/tree", "/v1/log")
        for (p in paths) {
            // 三真值组合 = 只要有一个不成立即拒
            assertTrue("active=false,valid=true → 拒", AuthGate.shouldReject(p, false, true))
            assertTrue("active=true,valid=false → 拒", AuthGate.shouldReject(p, true, false))
            assertTrue("active=false,valid=false → 拒", AuthGate.shouldReject(p, false, false))
            assertFalse("active=true,valid=true → 放行", AuthGate.shouldReject(p, true, true))
        }
    }

    @Test
    fun blank_and_malformed_paths_are_rejected_when_unauthenticated() {
        assertTrue(AuthGate.shouldReject("", false, false))
        assertTrue(AuthGate.shouldReject("/", false, false))
    }

    // ---- Minor #1: 运行期注册对 dispatch 立即可见 ----

    @Test
    fun route_registered_at_runtime_is_visible_to_dispatch_immediately() = runBlocking {
        CommandRouter.register("/v1/a", "GET", requiresToken = true) { Envelope.ok("\"a\"") }
        // 先让 dispatch 跑一轮（若读路径有缓存快照，这轮会把它建起来）
        assertTrue(JSONObject(CommandRouter.dispatch("/v1/a", "GET", ConsoleRequest())).getBoolean("ok"))
        // 运行期补注册的新端点必须下一跳立即可见
        CommandRouter.register("/v1/late", "POST", requiresToken = false) { Envelope.ok("\"late\"") }
        val o = JSONObject(CommandRouter.dispatch("/v1/late", "POST", ConsoleRequest()))
        assertTrue("运行期注册后 dispatch 必须立即命中", o.getBoolean("ok"))
        assertEquals("late", o.getString("data"))
    }

    @Test
    fun concurrent_register_and_dispatch_never_drops_or_duplicates_routes() {
        val n = 40
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        var registerError: Throwable? = null
        var dispatchError: Throwable? = null
        pool.execute {
            try {
                start.await()
                for (i in 0 until n) {
                    CommandRouter.register("/v1/c$i", "GET", requiresToken = false) { Envelope.ok(null) }
                }
            } catch (t: Throwable) {
                registerError = t
            } finally {
                done.countDown()
            }
        }
        pool.execute {
            try {
                start.await()
                var hits = 0
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (System.nanoTime() < deadline) {
                    for (i in 0 until n) {
                        val body = runBlocking {
                            CommandRouter.dispatch("/v1/c$i", "GET", ConsoleRequest())
                        }
                        if (JSONObject(body).optBoolean("ok")) hits++
                    }
                    if (hits >= n) break
                }
                assertTrue("并发注册期间 dispatch 不得丢路由（最终 $hits/$n 命中）", hits >= n)
            } catch (t: Throwable) {
                dispatchError = t
            } finally {
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("并发测试超时", done.await(15, TimeUnit.SECONDS))
        pool.shutdownNow()
        registerError?.let { throw it }
        dispatchError?.let { throw it }
    }
}
