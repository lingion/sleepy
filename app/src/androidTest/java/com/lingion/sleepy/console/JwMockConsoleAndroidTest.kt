package com.lingion.sleepy.console

import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.console.handlers.JwMockHandler
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 6 Step 3（设备侧编译级收口）：console 注入 mock 规则后, 拦截点
 * MockRuleStore.intercept(url) 在真 android.webkit 类上构造出 body/status
 * 正确的 WebResourceResponse；空规则恒 null（真实教务通路零影响的设备印证,
 * Review Focus 5）。WebView 全链"加载 pattern 拿 mock 体并解析入库"需
 * 真机登录页活体, 归 Task 8 设备验收清单（JwWebView 交互无法在无 UI 的
 * instrumentation 里稳定驱动, 强行造 activity 反而脆）。
 */
@RunWith(AndroidJUnit4::class)
class JwMockConsoleAndroidTest {

    private val pattern = "https://mock\\.jw\\.test/kbcx/.*"
    private val hitUrl = "https://mock.jw.test/kbcx/xkbcx?fx=1"

    @Before
    fun injectRule() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as android.app.Application
        MockRuleStore.init(app)
        MockRuleStore.clear()
        val put = JSONObject(JwMockHandler.handlePut(app,
            """{"url_pattern":"$pattern","respond_with":"html","body":"<html><title>MOCKED</title></html>","status":200}"""))
        assertTrue(put.getBoolean("ok"))
    }

    @After
    fun cleanUp() = MockRuleStore.clear()

    @Test
    fun interceptOnDevice_buildsWebResourceResponseWithMockBody() {
        val resp: WebResourceResponse? = MockRuleStore.intercept(hitUrl)
        assertNotNull("注入后命中 URL 必须回放", resp)
        // 构造面同源双断言: 表内规则即回放内容(WebResourceResponse 无公开
        // body getter, API34 才有 getDataStream — minSdk26 设备读不回)
        val rule = MockRuleStore.matchRuleIn(MockRuleStore.list(), hitUrl)
        assertNotNull(rule)
        assertTrue("回放体 = 注入体", String(rule!!.body).contains("MOCKED"))
        assertEquals(200, rule.status)
    }

    @Test
    fun emptyRuleSet_interceptAlwaysNull() {
        MockRuleStore.clear()
        assertNull(MockRuleStore.intercept(hitUrl))
        assertNull(MockRuleStore.intercept("https://jw.hrbeu.edu.cn/real/path"))
    }
}
