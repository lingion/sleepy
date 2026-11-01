package com.lingion.sleepy.console

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 最终整枝评审 F1（Critical）防线 — /v1/hello 的 token 字段按构建态门控。
 *
 * 失败场景（评审裁决原文）：release 用户为 agent 解锁（7 连点+口令）后，
 * 设备上任意第三方 app POST 127.0.0.1:3737/v1/hello（免 token 白名单端点）
 * 即拿到活 token，随后可删真实课表/写 prefs/驱动 UI——AuthGate 的保护形同虚设。
 *
 * 契约（控制器已裁决）：debug 构建回显内存现值（128 hex，SPEC §3 debug 跳口令
 * 不跳 token，探活即可取）；release 态（无论是否解锁）恒 null——release 场景
 * agent 本就从 /v1/enable 成功响应一次性拿到 token，零功能损失。SPEC §3 从未
 * 授权 release 经 hello 取 token。
 */
class HelloTokenGateUnitTest {

    @Before
    fun setUp() {
        CommandRouter.reset()
        val sys = { System.currentTimeMillis() }
        DebugGate.reset(forTestNow = sys)
        TokenStore.reset(passphrase = "right-pass", forTestNow = sys)
    }

    @After
    fun tearDown() {
        val sys = { System.currentTimeMillis() }
        DebugGate.reset(forTestNow = sys)
        TokenStore.reset(passphrase = "", forTestNow = sys)
        CommandRouter.reset()
    }

    private fun helloData(): JSONObject {
        ConsoleBootstrap.registerDefaultHandlers()
        val o = JSONObject(runBlocking {
            CommandRouter.dispatch("/v1/hello", "POST", ConsoleRequest(body = ""))
        })
        assertTrue("hello 应答必须是 ok 信封", o.getBoolean("ok"))
        return o.getJSONObject("data")
    }

    @Test
    fun hello_releaseUnlocked_neverEchoesLiveToken() {
        // 走 release 真通路装配：arm → 口令对 → attemptUnlock 内部
        // activate(debugBuild=false, releaseUnlocked=true) 且签发活 token。
        DebugGate.armUnlock()
        assertEquals(UnlockResult.OK, TokenStore.attemptUnlock("right-pass"))
        val live = TokenStore.issue()
        assertNotNull("前置条件：内存必须有活 token，否则 null 断言锁不住泄漏面", live)

        val data = helloData()
        assertTrue("release 解锁态 hello 禁止回显 token（F1 红线）", data.isNull("token"))
        // 探活面不因修复缩水：门位三字段照常可观测
        assertFalse(data.getBoolean("debugBuild"))
        assertTrue(data.getBoolean("unlocked"))
        assertTrue(data.getBoolean("active"))
    }

    @Test
    fun hello_debugBuild_echoesLiveToken128Hex() {
        // debug 装配等价物（ConsoleBootstrap.bindAwaitingServer）：activate(true)+grantNewToken
        DebugGate.activate(debugBuild = true, releaseUnlocked = false)
        TokenStore.grantNewToken()

        val data = helloData()
        val token = data.getString("token")
        assertEquals("debug hello 必须回显活 token（设备面 wireResponses 同钉 128）", 128, token.length)
        assertTrue("回显值必须是当前活 token", TokenStore.verify(token))
    }
}
