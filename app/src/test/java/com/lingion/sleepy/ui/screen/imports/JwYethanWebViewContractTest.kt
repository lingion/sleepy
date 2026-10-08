package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lingion.sleepy.testutil.readProjectSource

/**
 * SWJTU YETHAN 课表抓取契约锁。
 *
 * 演进史（两轮实测驱动）：
 *
 * ① 2026-09-16 用户复测 — 微信扫码登录卡壳：
 * 用户动线: 选西南交通大学 → WebView 打开 yhxt.swjtu.edu.cn → 教学平台登录页
 * 提供「账号密码」与「微信扫码」双通道。用户走微信扫码, 停在二维码页点导入
 * → 旧实现只报「未取到登录凭据，请先登录逐专平台后再点导入」, 用户不知要
 * 切回密码通道还是等扫码完成 → 体感「卡在微信界面, 只抓了登录页面」。
 * 契约: ytoken 缺失时按页面标记区分三种状态给出精确指引。
 *
 * ② 2026-10-08 诊断包实锤 — **token 存储位置假设是错的**（本次修正）:
 * 采集包 (sleepy-jw-dump-20261008-135253, appVersion 1.0.60) 证据：
 *   - `5-storage/storage.json` = {"sessionStorage":{},"localStorage":{}} —— 全空
 *   - netlog 唯一一条 `> ytoken: undefined` 即 Sleepy 注入的 fetch
 *     → 证明走到了取 token 那步且取到空串, 随后误判"未登录"提前 return,
 *       课表请求**从未发出**（courseCount=0 / status=UNKNOWN）
 *   - 同刻逐专平台自身请求 (+3616ms / +40359ms) 却带**有效 JWT**
 *     (sub=2024116815) 且无任何 JS 干预 → token 来源只能是 **Cookie**
 * 结论: JWT 只存 Cookie, localStorage 从来就没有过（旧注释与旧契约均为
 * 未验证的假设）。故取法改为 cookie → localStorage 兜底, 且**取不到不再
 * 提前 return**, 靠 credentials:'include' 自动携带 Cookie + 接口码判登录态
 * （对 HttpOnly 场景同样成立）。
 *
 * 本次契约 (YETHAN_FETCH_JS 必须):
 *  - hostname 闸 yhxt.swjtu.edu.cn 不变
 *  - token 取法: document.cookie 优先, localStorage 兜底
 *  - token 为空**不得阻断请求**; ytoken 头有值才发
 *  - 登录页文案标记区分仍保留（①②③ 三态）
 *  - 端点沿用 student-course-schedule + common-config
 */
class JwYethanWebViewContractTest {

    private val source: String = sequenceOf(
        System.getProperty("sleepy.test.root")?.let { java.io.File(it, "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt") },
        java.io.File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")
    ).filterNotNull().firstOrNull { it.isFile }
        ?.readText()
        ?: error("Unable to load JwWebViewLoginScreen.kt source")

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

    /**
     * 2026-10-08 修正核心：token 必须优先从 Cookie 读。
     * 旧契约断言 "must read ytoken from localStorage" —— 正是这条把错误假设固化了。
     */
    @Test
    fun yethanFetchJs_reads_ytoken_from_cookie_first() {
        val js = fetchJs
        assertTrue("must gate on yhxt.swjtu.edu.cn", js.contains("yhxt.swjtu.edu.cn"))
        assertTrue(
            "must read ytoken from document.cookie (2026-10-08 诊断包实锤: token 只在 Cookie)",
            Regex("""document\.cookie[\s\S]{0,200}ytoken""").containsMatchIn(js)
        )
        assertTrue(
            "localStorage 仅作兜底（保留以兼容平台未来改存储方式）",
            js.contains("localStorage.getItem('ytoken')")
        )
    }

    /**
     * 关键回归门：token 取不到时**不得**提前 return。
     * 旧实现在这里 return，导致课表请求从未发出 —— 本次事故的直接成因。
     */
    @Test
    fun yethanFetchJs_does_not_abort_when_token_missing() {
        val js = fetchJs
        assertTrue(
            "must still target the schedule endpoint (token 缺失不得阻断请求)",
            js.contains("/yethan/common/course-schedule/student-course-schedule")
        )
        assertTrue(
            "ytoken 头必须条件化发送 (有值才发)，不能无条件塞空串",
            Regex("""if\s*\(\s*token\s*\)\s*headers\['ytoken'\]\s*=\s*token""").containsMatchIn(js)
        )
        assertTrue(
            "捕获链路必须保留 credentials:'include' 以自动携带 Cookie",
            js.contains("credentials:'include'")
        )
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

    /**
     * 失效码分流：401/A0230/A0422 是明确过期，应给"刷新重登"；
     * 无 code（接口返回登录页 HTML 等）才落到登录页文案提示。
     */
    @Test
    fun yethanFetchJs_routes_expired_codes_separately_from_login_hint() {
        val js = fetchJs
        assertTrue("must branch on 401", js.contains("'401'"))
        assertTrue("must branch on A0230", js.contains("A0230"))
        assertTrue("must branch on A0422", js.contains("A0422"))
        assertTrue(
            "过期码应给『刷新重登』指引",
            js.contains("登录态已过期") || js.contains("刷新重登")
        )
    }

    /**
     * 2026-10-08 补充：common-config 接口侧疑似已不返回 termLessonStr
     * （用户实测选课界面本就不显示节次时间），periods 为空时必须有
     * SWJTU 标准作息兜底，且兜底数据为 13 节（2025-05-21 官方新表）。
     */
    @Test
    fun yethanFetchJs_hasSwjtuFallbackPeriods_with13Nodes() {
        assertTrue(
            "必须定义 YETHAN_FALLBACK_PERIODS 兜底作息（接口拿不到时导入免手填）",
            Regex("""val\s+YETHAN_FALLBACK_PERIODS""").containsMatchIn(source)
        )
        val fallbackStart = source.indexOf("val YETHAN_FALLBACK_PERIODS")
        val fallbackEnd = source.indexOf(")", fallbackStart)
        val fallbackBlock = source.substring(fallbackStart, fallbackEnd)
        val timePairs = Regex(""""(\d{2}:\d{2})"\s*to\s*"(\d{2}:\d{2})"""").findAll(fallbackBlock).toList()
        assertEquals("SWJTU 标准作息应为 13 节（2025-05 官方新表）", 13, timePairs.size)
        val first = timePairs.first().destructured
        assertEquals("第 1 节应为 08:00", "08:00", first.component1())
        val last = timePairs.last().destructured
        assertEquals("第 13 节应结束于 21:55", "21:55", last.component2())
        val docStart = source.lastIndexOf("/**", fallbackStart)
        val doc = source.substring(docStart, fallbackStart)
        assertTrue("文档必须引用官方通知 URL（news.swjtu.edu.cn/info/1020/80965.htm）",
            doc.contains("news.swjtu.edu.cn/info/1020/80965.htm"))
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
