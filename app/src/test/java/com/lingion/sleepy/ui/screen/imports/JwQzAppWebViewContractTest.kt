package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强智移动教务 SPA (qz_app) WebView 抓取契约 — 河北资源环境职业技术学院 (issue 源)。
 *
 * 强智移动教务的课表只在移动 JSON API 里 (token header 鉴权), 页面 HTML 无课程数据,
 * 所以 dispatcher 必须为 TYPE_QZ_APP 选专用 JS 分支, 禁止落到通用 Wisedu dqxnxq/xskcb
 * 流程。JS 必须:
 *   - 先 GET serverconfig.json (相对) 失败 .catch 回退 /dist/serverconfig.json (免鉴权
 *     静态资源) 发现各校部署可不同的 ApiUrl 前缀 (禁硬编码 /njwhd),
 *   - 取 sessionStorage.Token 作 `token` 头,
 *   - POST {ApiUrl}/student/curriculum?week=&kbjcmsid=,
 *   - credentials:'include' 保会话,
 *   - code=='401' 识别登录过期 (传输层状态路由, 非协议字段解码),
 *   - 经 __sleepyBridge.onWiseduResult 以 {ok,data} 信封回传, Kotlin 路由到
 *     JwImportViewModel.parseHtml(..., "qz_app")。
 *
 * 跨语言 invariant: JS 只做 fetch + 传输层 status 路由; 协议字段解码唯一落点 =
 * JwQzAppParser (Kotlin)。JS 全文禁现 classTime/classWeek/classWeekDetails 字段名。
 */
class JwQzAppWebViewContractTest {

    private val source: String = sequenceOf(
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwWebViewLoginScreen.kt source")

    @Test
    fun qzAppWebView_dispatch_selects_a_dedicated_fetch_branch() {
        assertTrue(
            "Capturing qz_app must not fall through to the generic Wisedu dqxnxq/xskcb flow",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_QZ_APP""").containsMatchIn(source)
        )
        assertTrue(
            "There must be a dedicated qz_app fetch JS constant",
            source.contains("QZ_APP_FETCH_JS")
        )
    }

    @Test
    fun qzAppFetchJs_discovers_ApiUrl_via_serverconfig_and_posts_curriculum() {
        val start = source.indexOf("const val QZ_APP_FETCH_JS")
        val end = source.indexOf("const val WHUT_FETCH_JS")
        assertTrue("QZ_APP_FETCH_JS constant missing or after WHUT_FETCH_JS", start >= 0 && end > start)
        val js = source.substring(start, end)

        // 1. serverconfig.json 发现: 相对 + /dist/ 回退, 禁硬编码 API 前缀
        assertTrue("必须 GET 相对 serverconfig.json", js.contains("serverconfig.json"))
        assertTrue("必须有 /dist/serverconfig.json 回退", js.contains("/dist/serverconfig.json"))
        assertTrue("必须读 ApiUrl 字段", js.contains("ApiUrl"))
        assertFalse("禁止硬编码 /njwhd 前缀", js.contains("/njwhd"))

        // 2. token 头 = sessionStorage.Token
        assertTrue("必须从 sessionStorage 取 Token", js.contains("sessionStorage.getItem('Token')"))
        assertTrue("必须带 token 请求头", js.contains("'token': token") || js.contains("\"token\": token"))

        // 3. POST 课表端点 + 会话
        assertTrue("必须 POST /student/curriculum?week=&kbjcmsid=",
            js.contains("/student/curriculum?week=&kbjcmsid="))
        assertTrue("必须 method:'POST'", js.contains("method:'POST'"))
        assertTrue("必须 credentials:'include'", js.contains("credentials:'include'"))

        // 4. 401 过期识别 (传输层) + {ok,data} 信封回传
        assertTrue("必须识别 code=='401' 过期", js.contains("401"))
        assertTrue("必须经 __sleepyBridge.onWiseduResult 回传",
            js.contains("__sleepyBridge.onWiseduResult"))
        assertTrue("必须 {ok,data} 信封", js.contains("ok:true") && js.contains("data:"))

        // 5. 跨语言 invariant: JS 禁解码协议字段 (解码唯一落点 = JwQzAppParser)
        assertFalse("JS 禁现 classTime", js.contains("classTime"))
        assertFalse("JS 禁现 classWeek", js.contains("classWeek"))
        assertFalse("JS 禁现 classWeekDetails", js.contains("classWeekDetails"))
        assertFalse("JS 禁现 courseName", js.contains("courseName"))
    }
}
