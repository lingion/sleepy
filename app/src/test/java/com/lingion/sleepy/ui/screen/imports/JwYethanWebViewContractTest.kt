package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test
import com.lingion.sleepy.testutil.readProjectSource

/**
 * SWJTU YETHAN 微信扫码登录卡壳 (2026-09-16 用户复测报告) 契约锁。
 *
 * 用户动线: 选西南交通大学 → WebView 打开 yhxt.swjtu.edu.cn → 教学平台登录页
 * 提供「账号密码」与「微信扫码」双通道。用户走微信扫码, 停在二维码页点导入
 * → 旧实现只报「未取到登录凭据，请先登录逐专平台后再点导入」, 用户不知要
 * 切回密码通道还是等扫码完成 → 体感「卡在微信界面, 只抓了登录页面」。
 *
 * 契约 (YETHAN_FETCH_JS 必须):
 *  - ytoken 缺失时按页面标记区分三种状态给出精确指引:
 *      ① 微信扫码页 (「使用微信扫一扫登录」/「微信登录」) → 指引扫码完成或切换账号密码
 *      ② 账号密码页 (type="password" 表单) → 指引输入学号密码完成登录
 *      ③ 其余 → 保留通用「请先登录逐专平台」文案
 *  - 登录成功后 localStorage ytoken + T (login-cas.js LOGIN_EXPIRED 双键) 都在
 *    同一 yhxt.swjtu.edu.cn 源上, hostname 闸不变。
 *  - config 抓取沿用 /yethan/common-config (2026-09-15 采集包 yethan_common-config.json 实锤)。
 */
class JwYethanWebViewContractTest {

    private val source: String = readProjectSource(
        "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"
    )

    private val fetchJs: String
        get() {
            val start = source.indexOf("const val YETHAN_FETCH_JS")
            val end = source.indexOf("const val CQU_FETCH_JS")
            assertTrue("YETHAN_FETCH_JS constant missing or after CQU_FETCH_JS", start >= 0 && end > start)
            return source.substring(start, end)
        }

    @Test
    fun yethanWebView_dispatch_selects_a_dedicated_fetch_branch() {
        assertTrue(
            "Capturing YETHAN must use the dedicated fetch JS, not HTML frame capture",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_YETHAN""").containsMatchIn(source)
        )
    }

    @Test
    fun yethanFetchJs_refuses_off_host_and_reads_ytoken() {
        val js = fetchJs
        assertTrue("must gate on yhxt.swjtu.edu.cn", js.contains("yhxt.swjtu.edu.cn"))
        assertTrue("must read ytoken from localStorage", js.contains("localStorage.getItem('ytoken')"))
    }

    @Test
    fun yethanFetchJs_distinguishes_wechat_qr_page_from_password_page() {
        val js = fetchJs
        assertTrue(
            "ytoken 缺失时必须识别微信扫码页 (使用微信扫一扫登录 / 微信登录 标记)",
            js.contains("使用微信扫一扫登录") || js.contains("微信登录")
        )
        assertTrue(
            "ytoken 缺失时必须识别账号密码页 (password 输入框标记)",
            js.contains("type=\"password\"") || js.contains("type='password'") || js.contains("password")
        )
        assertTrue(
            "微信扫码页必须给出『扫码完成或切换账号密码』精确指引",
            js.contains("微信扫码") && js.contains("账号密码")
        )
        assertTrue(
            "账号密码页必须给出『输入学号密码完成登录』精确指引",
            js.contains("学号密码") || js.contains("账号密码登录")
        )
    }

    @Test
    fun yethanFetchJs_still_targets_schedule_and_common_config() {
        val js = fetchJs
        assertTrue(
            "must GET /yethan/common/course-schedule/student-course-schedule",
            js.contains("/yethan/common/course-schedule/student-course-schedule")
        )
        assertTrue(
            "must GET /yethan/common-config (2026-09-15 采集包实锤端点)",
            js.contains("/yethan/common-config")
        )
        assertTrue(
            "must carry ytoken header + same-origin credentials",
            js.contains("ytoken") && js.contains("credentials:'include'")
        )
    }

    @Test
    fun yethanConfig_is_consumed_into_periods_and_termStartDate() {
        // 死载荷治理: YETHAN fetch 抓的 common-config 必须被 handleWiseduResult 消费 —
        // termLessonStr 拆 HH:MM-HH:MM 槽填 periods, termStartDate 填 startDate。
        val handleStart = source.indexOf("val handleWiseduResult")
        assertTrue("handleWiseduResult missing", handleStart >= 0)
        val handler = source.substring(handleStart, source.indexOf("private fun", handleStart).let { if (it < 0) source.length else it })
        assertTrue(
            "must parse yethanConfig.data.termLessonStr into periods",
            handler.contains("termLessonStr")
        )
        assertTrue(
            "must fall back termStartDate from yethanConfig.data.termStartDate",
            handler.contains("termStartDate") && Regex("""effectiveStartDate""").containsMatchIn(handler)
        )
        assertTrue(
            "periods filling must be gated to TYPE_YETHAN",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_YETHAN""").containsMatchIn(handler)
        )
    }
}
