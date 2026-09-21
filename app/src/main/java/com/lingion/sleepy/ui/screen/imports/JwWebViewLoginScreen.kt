package com.lingion.sleepy.ui.screen.imports

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.EAMS5_PREFIX_PLACEHOLDER
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.jw.eams5PathPrefixFor
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

/** fetch JS 注入超时: 教务宕机时 20s 无桥回调即报超时, 禁无限 pending。 */
private const val FETCH_TIMEOUT_MS = 20_000L

/** Chrome 121 / Windows 10 桌面 UA — 无 Android; Safari 等 iPhone 词汇, 触发门户桌面版布局 */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/121.0.0.0 Safari/537.36"

/**
 * 桌面模式 viewport 覆盖 JS (issue #18 PCUA 不生效修复)。
 *
 * 根因 (2026-09-13 实锤): SEP 门户的移动/桌面布局 = Bootstrap 2 响应式 CSS 纯宽度驱动 —
 * `@media (max-width: 979px)` 侧栏收起 / `@media (min-width: 980px)` 侧栏展开, 布局宽度
 * 来自页面 `<meta name="viewport" content="width=device-width">` (= 手机屏宽 ~411dp <
 * 980px, 永远命中移动分支)。UA 字符串对布局零影响 (双 UA curl 同为 16027 字节同构
 * HTML), isMobile JS 判定也是触屏 (`ontouchstart`/maxTouchPoints) 非 UA — 换桌面 UA
 * 不改变任何一条布局分支。
 *
 * 修复: 桌面模式注入本 JS 把 layout viewport 钉到 1024px → `@media (min-width: 980px)`
 * 命中 → 桌面布局 (侧栏展开, "选课系统"入口可见)。注入时机 = onPageFinished (SEP 每次导航
 * — 登录页/portal/xkgo 302 链每跳都触发)。仅 SEP 域注入: xkgo 课表页手机宽度已可用
 * (报告人横屏导入成功实证), 不动。
 */
internal const val DESKTOP_VIEWPORT_JS =
    "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
        "if(m){m.setAttribute('content','width=1024');}" +
        "else{m=document.createElement('meta');m.setAttribute('name','viewport');" +
        "m.setAttribute('content','width=1024');document.head.appendChild(m);}" +
        "setTimeout(function(){window.scrollTo(0,0);},0);})()"

/**
 * 教务 WebView 登录页
 *
 * 实现细节（参考 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) WebViewLoginFragment.kt）：
 *   - 用 `loadUrl("javascript:...")` 触发 JS（不是 evaluateJavascript）—— wakeup 用了 6 年的稳定方案
 *   - `addJavascriptInterface(InJavaScriptLocalObj, "local_obj")` 把回调暴露给 JS
 *   - JS 把 HTML 通过 `window.local_obj.showSource(html)` 回调回 Kotlin
 *   - 抓的是 `document.documentElement.outerHTML`（innerHTML 不够，frame/iframe 内容也合并）
 *
 * 流程：WebView 加载学校 URL → 用户输账号密码 + 验证码 → 导航到课表页 → 点"导入此页" → JS 抓 HTML → 回调 → 落库
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwWebViewLoginScreen(
    school: JwSchoolInfo,
    onHtmlCaptured: (html: String, school: JwSchoolInfo, periods: List<Triple<Int, String, String>>, termStartDate: String) -> Unit,
    onCaptureError: (result: FrameCaptureResult, hint: String) -> Unit,
    onBack: () -> Unit,
    onWebViewReady: ((WebView) -> Unit)? = null,
    viewModel: JwImportViewModel = viewModel()
) {
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var progress by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // #18: 桌面 UA 开关 — true 时重建 WebView 用 Chrome 桌面 UA
    var desktopUa by remember { mutableStateOf(false) }
    var uaSwitchReload by remember { mutableStateOf(0) }
    val logToken = remember { java.util.concurrent.atomic.AtomicLong(0) }
    val webviewNotReadyMsg = stringResource(R.string.jw_webview_not_ready)
    val fetchingMsg = stringResource(R.string.jw_fetching)
    val fetchFailedNoResponseMsg = stringResource(R.string.jw_fetch_failed_no_response)
    val fetchFormatErrorMsg = stringResource(R.string.jw_fetch_format_error)
    val fetchFailedFmt = stringResource(R.string.jw_fetch_failed)
    val pageNotLoadedMsg = stringResource(R.string.jw_page_not_loaded)
    val sepPortalHintMsg = stringResource(R.string.jw_err_ucas_sep_portal)
    val fetchTimeoutMsg = stringResource(R.string.jw_fetch_timeout)
    val fetchNoCoursesMsg = stringResource(R.string.jw_fetch_no_courses)

    // wisedu (金智) 协议：WebView 内 fetch 课表 JSON 的回调结果处理
    // 桥回调已切到主线程；result 形如 {ok:true,data:"<xskcb.do JSON>"} 或 {ok:false,err:"..."}
    val handleWiseduResult: (String) -> Unit = { json ->
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optBoolean("ok", false)) {
                val data = obj.optString("data", "")
                if (data.isBlank()) {
                    onCaptureError(
                        FrameCaptureResult(null, "", emptyList(),
                            status = FrameCaptureStatus.UNKNOWN,
                            diagnosticHint = fetchFailedNoResponseMsg),
                        fetchFailedNoResponseMsg,
                    )
                } else {
                    // 解析 periods 数组（节次时间）
                    val periods = mutableListOf<Triple<Int, String, String>>()
                    val periodsArr = obj.optJSONArray("periods")
                    if (periodsArr != null) {
                        for (i in 0 until periodsArr.length()) {
                            val p = periodsArr.getJSONObject(i)
                            periods += Triple(
                                p.optInt("node", i + 1),
                                p.optString("start", ""),
                                p.optString("end", "")
                            )
                        }
                    }
                    Log.d("JwWebView", "wisedu fetched JSON len=${data.length} periods=${periods.size}")
                    if (periods.isEmpty() && school.type == JwProtocol.TYPE_CQU) {
                        // CQU fetch 走通但 time-pattern 没给节次: 落库前明确提示,
                        // 用户可在确认页手填节次时间, 而非报误导性的「空学期」
                        scope.launch { snackbar.showSnackbar(fetchNoCoursesMsg) }
                    }
                    val termStartDate = obj.optString("startDate", "")
                    // YETHAN: config 抓了但旧实现无人消费 (死载荷)。采集包
                    // yethan_common-config.json 实锤 data.termLessonStr =
                    // "08:00-08:45,08:50-09:35,…" (courseSysDayLesson 个 HH:MM-HH:MM 槽),
                    // data.termStartDate = 开学日 → 填 periods/startDate, 用户免手填节次。
                    var termStart = termStartDate
                    if (periods.isEmpty() && school.type == JwProtocol.TYPE_YETHAN) {
                        try {
                            val cfg = obj.optJSONObject("yethanConfig")?.optJSONObject("data")
                            val lessonStr = cfg?.optString("termLessonStr").orEmpty()
                            if (lessonStr.isNotBlank()) {
                                val slots = lessonStr.split(',').mapNotNull { seg ->
                                    val p = seg.trim().split('-')
                                    if (p.size == 2) p[0].trim() to p[1].trim() else null
                                }
                                periods.addAll(slots.mapIndexed { i, (s, e) ->
                                    Triple(i + 1, s, e)
                                })
                            }
                            val cfgStart = cfg?.optString("termStartDate").orEmpty()
                            if (termStart.isBlank() && cfgStart.isNotBlank()) termStart = cfgStart
                        } catch (e: Exception) {
                            Log.w("JwWebView", "yethanConfig parse failed", e)
                        }
                    }
                    val effectiveStartDate = termStart.ifBlank { termStartDate }
                    onHtmlCaptured(data, school, periods, effectiveStartDate)
                }
            } else {
                val err = obj.optString("err", "")
                val msg = fetchFailedFmt.format(err.ifBlank { pageNotLoadedMsg })
                onCaptureError(
                    FrameCaptureResult(null, "", emptyList(),
                        status = FrameCaptureStatus.UNKNOWN,
                        diagnosticHint = msg),
                    msg,
                )
            }
        } catch (e: Exception) {
            Log.e("JwWebView", "parse wisedu result failed", e)
            onCaptureError(
                FrameCaptureResult(null, "", emptyList(),
                    status = FrameCaptureStatus.UNKNOWN,
                    diagnosticHint = fetchFormatErrorMsg),
                fetchFormatErrorMsg,
            )
        }
    }

    // fetch JS 注入超时闸: evaluateJavascript 无内建超时, 教务宕机/挂起时
    // 桥回调永远不来, 用户只见「正在抓取」无限 pending。20s 无回调即报超时。
    fun evaluateFetchWithTimeout(wv: WebView, js: String) {
        var answered = false
        val beginToken = logToken.incrementAndGet()
        wv.evaluateJavascript(js) {
            answered = true
            Log.d("JwWebView", "fetch js done token=$beginToken")
        }
        wv.postDelayed({
            if (!answered) {
                Log.w("JwWebView", "fetch js timeout token=$beginToken")
                onCaptureError(
                    FrameCaptureResult(null, "", emptyList(),
                        status = FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY,
                        diagnosticHint = fetchTimeoutMsg),
                    fetchTimeoutMsg,
                )
            }
        }, FETCH_TIMEOUT_MS)
    }

    BackHandler {
        webViewRef?.let { wv ->
            if (wv.canGoBack()) wv.goBack() else onBack()
        } ?: onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(school.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = JwProtocol.displayName(school.type),
                            style = MaterialTheme.typography.bodySmall,
                            color = SleepyTheme.colors.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // #18: 部分门户 (UCAS SEP 等) 手机 UA 下不显示"个人课表"入口,
                    // 桌面 UA 可见。切换 = 销毁重建 WebView (UA 只在创建期生效),
                    // 同步保留 cookie (CookieManager 全局共享) 与当前 URL
                    IconButton(
                        onClick = {
                            desktopUa = !desktopUa
                            uaSwitchReload++   // 触发 JwWebView 重建 (UA 创建期生效)
                        },
                        enabled = webViewRef != null
                    ) {
                        // 图标随状态切换: 当前手机 UA → 显示 Computer (点了变桌面);
                        // 当前桌面 UA → 显示 PhoneAndroid (点了回手机)
                        Icon(
                            if (desktopUa) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                            contentDescription = stringResource(
                                if (desktopUa) R.string.jw_toggle_mobile_ua
                                else R.string.jw_toggle_desktop_ua
                            )
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        enabled = webViewRef != null
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.jw_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground,
                    actionIconContentColor = colors.onBackground
                )
            )
        },
        snackbarHost = {
            // 默认 Snackbar 配色 — 与其余 4 处 SnackbarHost 一致, 不再单独覆写容器色
            SnackbarHost(snackbar)
        },
        bottomBar = {
            CaptureBar(
                enabled = webViewRef != null,
                onCapture = {
                    val wv = webViewRef
                    if (wv == null) {
                        Log.w("JwWebView", "capture tapped but webViewRef is null")
                        onCaptureError(
                            FrameCaptureResult(null, "", emptyList(),
                                status = FrameCaptureStatus.UNKNOWN,
                                diagnosticHint = webviewNotReadyMsg),
                            webviewNotReadyMsg,
                        )
                        return@CaptureBar
                    }
                    val url = wv.url ?: ""
                    Log.d("JwWebView", "capture tapped, current url=$url")
                    scope.launch { snackbar.showSnackbar(fetchingMsg) }
                    // wisedu (金智 jwapp)：课表数据在 JSON API 不在页面 HTML，改用 fetch 拿 JSON（结果走 JS 桥回调）
                    if (school.type == JwProtocol.TYPE_WISEDU) {
                        evaluateFetchWithTimeout(wv, WISEDU_FETCH_JS)
                        return@CaptureBar
                    }
                    // 东北大学新版金智教务：课表只在 homeapp 的 mobile JSON API 中，
                    // 页面 HTML 不含 arrangedList，必须主动取当前学期、校区和课表详情。
                    if (school.type == JwProtocol.TYPE_NEU) {
                        evaluateFetchWithTimeout(wv, NEU_FETCH_JS)
                        return@CaptureBar
                    }
                    if (school.type == JwProtocol.TYPE_NUIT) {
                        evaluateFetchWithTimeout(wv, NUIT_FETCH_JS)
                        return@CaptureBar
                    }
                    if (school.type == JwProtocol.TYPE_KUST) {
                        evaluateFetchWithTimeout(wv, KUST_FETCH_JS)
                        return@CaptureBar
                    }
                    // SWJTU YETHAN 逐专平台：CAS 登录后从 localStorage 取 ytoken，
                    // 同源 GET 课表 JSON；不发送采集包中的真实 token。
                    if (school.type == JwProtocol.TYPE_YETHAN) {
                        evaluateFetchWithTimeout(wv, YETHAN_FETCH_JS)
                        return@CaptureBar
                    }
                    // CQU（重庆大学门户）：同走 JS 桥 fetch 四个 REST API，Bearer token 取自 localStorage
                    if (school.type == JwProtocol.TYPE_CQU) {
                        evaluateFetchWithTimeout(wv, CQU_FETCH_JS)
                        return@CaptureBar
                    }
                    // 新青果 NTSS (江西中医药大学等, /new/student/xsgrkb): 课表数据只在
                    // FullCalendar 的 getCalendarWeekDatas JSON 接口里, 页面 HTML 无课程数据。
                    // WebView 内逐周并行 POST 1..22 周合并 (每行自带全学期周次串,
                    // JwCfNewParser 按唯一键去重), businessHours 节次时间与
                    // getDatesOfWeek 开学日随 payload 回传。
                    if (school.type == JwProtocol.TYPE_CF_NEW) {
                        evaluateFetchWithTimeout(wv, CF_NEW_FETCH_JS)
                        return@CaptureBar
                    }

                    // WHUT（武汉理工）：金智 jwapp 变体 — kcbcxby 微应用三段 fetch
                    // (currentUser 学号+学期 → cxjcs 开学日期/总周数 → jcjcx 节次映射 → cxxskcb 课表)
                    if (school.type == JwProtocol.TYPE_WHUT) {
                        evaluateFetchWithTimeout(wv, WHUT_FETCH_JS)
                        return@CaptureBar
                    }
                    // 超星综合教务 (Powered by ChaoXing): getMenuList 取学期 →
                    // queryKbForGrdb 个人课表 (无参, session 态) → getZclistByXnxq 节次时间
                    if (school.type == JwProtocol.TYPE_CHAOXING) {
                        evaluateFetchWithTimeout(wv, CHAOXING_FETCH_JS)
                        return@CaptureBar
                    }
                    // 博雅研究生平台 (/pp/ 前端, 首校燕山大学研究生): term 列表取学期+开学日 →
                    // setting/current 取节次时间 → 逐周 byStudent 全学期排课行
                    if (school.type == JwProtocol.TYPE_BOYA_PP) {
                        evaluateFetchWithTimeout(wv, BOYA_PP_FETCH_JS)
                        return@CaptureBar
                    }
                    // 强智移动教务 SPA: 课表只在移动 JSON API 里 (token header 鉴权), 页面 HTML
                    // 无课程数据。先 GET /dist/serverconfig.json (免鉴权) 发现 ApiUrl (前缀
                    // 各校部署可不同, 禁硬编码), 再带 sessionStorage.Token POST 课表。
                    if (school.type == JwProtocol.TYPE_QZ_APP) {
                        evaluateFetchWithTimeout(wv, QZ_APP_FETCH_JS)
                        return@CaptureBar
                    }
                    // 北京交通大学 AA 平台 (issue #19): 课表 HTML 在 /course_selection/ 双端点
                    // (无学期/周次参数, 服务端按会话返回)。WebView 内同源 fetch 两段 HTML
                    // 拼组合源 (<!--sleepy-bjtu-doc:label--> 标记), JwBjtuParser 切段解析,
                    // 节次时间从行首格 [HH:MM-HH:MM] 抽出随 periods 回传。
                    if (school.type == JwProtocol.TYPE_BJTU) {
                        evaluateFetchWithTimeout(wv, BJTU_FETCH_JS)
                        return@CaptureBar
                    }
                    // 合工大 EAMS5: 四段 fetch (course-table → info/<sid> → get-data → POST schedule-table/datum)
                    // 用户已在 WebView 走完 CAS 登录并落到教务域。supwisdom 新版部署
                    // 前缀分两形态：合工大 /eams5-student、安大/矿大北京 /student —
                    // 按学校 URL 推断后替换模板占位符。
                    if (school.type == JwProtocol.TYPE_EAMS5) {
                        val prefix = eams5PathPrefixFor(school.url)
                        // 安徽大学自建'新教务'走金智 EAMS 新版 GET 路径, 复用 EAMS5 type
                        // 但 fetch JS 单独走 EAMS5_AHU_FETCH_JS (2026-09-06 cross-verified)
                        val js = if (school.url.contains("ahu.edu.cn", ignoreCase = true) ||
                            (wv.url ?: "").contains("ahu.edu.cn", ignoreCase = true)
                        ) {
                            EAMS5_AHU_FETCH_JS.replace(EAMS5_PREFIX_PLACEHOLDER, prefix)
                        } else {
                            EAMS5_FETCH_JS.replace(EAMS5_PREFIX_PLACEHOLDER, prefix)
                        }
                        evaluateFetchWithTimeout(wv, js)
                        return@CaptureBar
                    }
                    // T5: 新版正方 — WebView 内 fetch kbList JSON
                    // 路径指纹: school.type 显式 zf_new, 或 URL 含 /jwglxt/、/kbcx/ (广东医科等新版), 或 WebVPN /http/<hex>/ 重写形态
                    val currentUrl = wv.url ?: ""
                    val isIeas = school.type == JwProtocol.TYPE_QZ_IEAS ||
                        currentUrl.contains("/ieas2.1/", ignoreCase = true)
                    val isZfNew = !isIeas && (school.type == JwProtocol.TYPE_ZF_NEW ||
                        currentUrl.contains("/jwglxt/", ignoreCase = true) ||
                        currentUrl.contains("/kbcx/", ignoreCase = true) ||
                        Regex("/http/[0-9a-f]{4,8}/").containsMatchIn(currentUrl))
                    if (isZfNew) {
                        wv.evaluateJavascript(ZF_NEW_FETCH_JS, null)
                        return@CaptureBar
                    }
                    // T7: DFS frame 抓取 + ready 重试, 决策在 JVM 层(可测可日志)
                    captureWithRetry(wv, 0) { r ->
                        Log.d("JwWebView", "captured frame=${r.selectedFramePath} anchors=${r.matchedAnchors} status=${r.status}")
                        // #18: UCAS 入口是 SEP 门户, 学生登录后常停在 SEP 应用列表页点导入
                        // (还没跳到 xkgo 课表页) → hint 换成精确动线指引, Activity 对
                        // WRONG_PAGE 且 hint 非空时优先展示 hint
                        val hint = if (r.status == FrameCaptureStatus.WRONG_PAGE &&
                            school.type == JwProtocol.TYPE_UCAS &&
                            (wv.url ?: "").contains("sep.ucas.ac.cn")
                        ) {
                            sepPortalHintMsg
                        } else r.diagnosticHint
                        when (r.status) {
                            FrameCaptureStatus.OK, FrameCaptureStatus.EMPTY_SEMESTER ->
                                onHtmlCaptured(r.html, school, emptyList(), "")   // 0 课交给 Activity 按空学期文案报
                            FrameCaptureStatus.SESSION_EXPIRED,
                            FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED,
                            FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY,
                            FrameCaptureStatus.IFRAME_NAV_PENDING,
                            FrameCaptureStatus.WRONG_PAGE,
                            FrameCaptureStatus.UNKNOWN ->
                                onCaptureError(r, hint)    // 不走 onHtmlCaptured, 避免伪"0 课"
                        }
                    }
                }
            )
        },
        containerColor = colors.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            JwWebView(
                url = school.url.ifBlank { "https://www.baidu.com" },
                school = school,
                desktopUa = desktopUa,
                recreateKey = uaSwitchReload,
                onProgressChange = { p -> progress = p },
                onWebViewCreated = { wv -> webViewRef = wv },
                onHtmlCaptured = { html -> onHtmlCaptured(html, school, emptyList(), "") },
                onWiseduResult = handleWiseduResult,
                onWebViewReady = onWebViewReady
            )

            if (progress in 1..99) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = colors.primary,
                        trackColor = colors.surfaceContainer
                    )
                }
            }
        }
    }
}

/** 下载实体抓取 — 共享有界池(daemon 线程), 避免每条下载泄漏一个核心线程。 */
private val DOWNLOAD_FETCH_EXECUTOR =
    java.util.concurrent.Executors.newFixedThreadPool(
        2,
        { r ->
            java.lang.Thread(r, "sleepy-jw-download").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        },
    )

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun JwWebView(
    url: String,
    school: JwSchoolInfo,
    desktopUa: Boolean,
    recreateKey: Int,
    onProgressChange: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onHtmlCaptured: (String) -> Unit,
    onWiseduResult: (String) -> Unit = {},
    onWebViewReady: ((WebView) -> Unit)? = null
) {
    // key 含 recreateKey: UA 切换时销毁重建 WebView (userAgentString 仅创建期可靠,
    // 部分页面在 onPageStarted 后改 UA 不回读); CookieManager 全局共享, 登录态不丢
    var lastUrl by remember { mutableStateOf(url) }
    key(recreateKey) {
        AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val schoolHost = lastUrl.toUri().host.orEmpty()
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // wisedu (金智) 协议：注册 JS 桥，async fetch 课表 JSON 完成后回调
                addJavascriptInterface(WiseduBridge(onWiseduResult), "__sleepyBridge")
                // 诊断桥常驻；页面内 recorder 只观察网络，不替换请求或响应。
                addJavascriptInterface(DiagnosticNetworkBridge(), "__sleepyNetworkBridge")
                settings.apply {
                    javaScriptEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    if (desktopUa) {
                        // Chrome 桌面 UA — SEP 布局对 UA 零差异, 真正的桌面布局由
                        // onPageFinished 后的 viewport 覆盖 JS (DESKTOP_VIEWPORT_JS) 触发
                        userAgentString = DESKTOP_USER_AGENT
                        useWideViewPort = true
                    }
                }
                // 正常 WebView 配置
                settings.databaseEnabled = true
                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                    // 实体抓取 — 桌面 collector 4-downloads/ 对标: 下载文件字节落诊断包
                    // (导出 xls/ics 课表文件本身是协议证据)。后台线程 GET, 带 Cookie。
                    // 单次记录: 元数据 + 字节合并到一条 (避免 manifest 重复/body 索引错位)。
                    // 共享有界池 (daemon 线程), 不用 newSingleThreadExecutor — 那会每条下载泄漏一个核心线程。
                    DOWNLOAD_FETCH_EXECUTOR.execute {
                        val body = runCatching {
                            val conn = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 10_000
                            conn.readTimeout = 15_000
                            conn.instanceFollowRedirects = true
                            android.webkit.CookieManager.getInstance().getCookie(downloadUrl)?.let {
                                conn.setRequestProperty("Cookie", it)
                            }
                            if (userAgent?.isNotBlank() == true) conn.setRequestProperty("User-Agent", userAgent)
                            conn.connect()
                            if (conn.responseCode !in 200..299) null
                            else conn.inputStream.use { ins -> ins.readNBytes(2 * 1024 * 1024) }
                        }.getOrNull()
                        JwDiagnosticSession.recordDownload(
                            downloadUrl, userAgent, contentDisposition, mimeType, contentLength, body
                        )
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChange(newProgress)
                    }
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                        Log.d("JwWebView", "console[${msg?.messageLevel()}]: ${msg?.message()}")
                        JwDiagnosticSession.recordConsole(msg)
                        return true
                    }

                }
                JwDiagnosticSession.resetSession()
        evaluateJavascript(DIAGNOSTIC_NETWORK_INSTALL_JS, null)
        webViewClient = JwWebViewClientBuilder.build(
                    webView = this,
                    school = school,
                    desktopMode = desktopUa,
                ) { finished ->
                    Log.d("JwWebView", "onPageFinished url=$finished")
                    lastUrl = finished ?: url
                    // Install after every navigation: page scripts may replace fetch/XHR globals.
                    evaluateJavascript(DIAGNOSTIC_NETWORK_INSTALL_JS, null)
                }
                loadUrl(lastUrl)
                onWebViewCreated(this)
                onWebViewReady?.invoke(this)
            }
        }
    )
        }
}

@Composable
private fun CaptureBar(enabled: Boolean, onCapture: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.jw_after_login),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.jw_nav_hint),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
        }
        Button(
            onClick = onCapture,
            enabled = enabled,
            shape = SleepyTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(stringResource(R.string.jw_import_page), color = colors.onPrimary)
        }
    }
}

/**
 * wisedu (金智 jwapp) 协议：在 WebView 内 fetch 课表 JSON + 抓节次时间。
 *
 * 流程：
 *  1) GET 我的课表(wdkb)微应用入口，初始化 app 会话
 *  2) POST dqxnxq.do 拿当前学年学期 DM
 *  3) POST xskcb.do 拿课表（XNXQDM=当前学期）
 *  4) 抓页面 DOM 中节次时间（"08:00~08:45" 格式），前提是"是否显示节次时间"已勾选
 *  5) 通过 __sleepyBridge.onWiseduResult({ok, data, periods}) 回调
 *
 * 路径指纹判定（/jwapp/ 在路径中即可，不锁定单一 hostname — T5/T11 拆雷）。
 */
private const val WISEDU_FETCH_JS = """
(function(){
  try {
    var pathOk = location.pathname.indexOf('/jwapp/') >= 0;
    if (!pathOk) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录并进入教务系统后再点导入'}));
      return;
    }
    // 0. 先 GET 我的课表(wdkb)微应用入口，初始化 app 会话；否则 module API 返回 403
    fetch('/jwapp/sys/wdkb/*default/index.do', {credentials:'include'})
    .then(function(){
      return fetch('/jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do', {
        method:'POST',
        headers:{'X-Requested-With':'XMLHttpRequest'},
        credentials:'include'
      });
    })
    .then(function(r){ return r.json(); })
    .then(function(d){
      var rows = [];
      try { rows = d.datas.dqxnxq.rows || []; } catch(e) {}

      // 教务页面允许用户切换学期，但 dqxnxq 的 rows[0] 不一定是页面当前选项。
      // 先从当前页面的 select/option 读取用户实际选中的 XNXQDM，避免静默回退到旧学期。
      var xnxq = '';
      var selects = document.querySelectorAll('select');
      for (var i = 0; i < selects.length && !xnxq; i++) {
        var selected = selects[i].options && selects[i].options[selects[i].selectedIndex];
        var candidates = selected ? [selected.value, selected.textContent || ''] : [];
        for (var j = 0; j < candidates.length; j++) {
          var match = candidates[j].match(/20[0-9]{2}-20[0-9]{2}-[12]/);
          if (match && rows.some(function(row) { return String(row.DM || '') === match[0]; })) {
            xnxq = match[0];
            break;
          }
        }
      }
      // 某些 Wisedu 页面不是原生 select，而是自定义控件；这时匹配已选/激活节点文本。
      if (!xnxq) {
        var active = document.querySelectorAll('.selected,.active,[aria-selected="true"]');
        for (var k = 0; k < active.length && !xnxq; k++) {
          var activeText = active[k].value || active[k].textContent || '';
          var activeMatch = activeText.match(/20[0-9]{2}-20[0-9]{2}-[12]/);
          if (activeMatch && rows.some(function(row) { return String(row.DM || '') === activeMatch[0]; })) {
            xnxq = activeMatch[0];
          }
        }
      }
      // HEU 当前课表页使用 data-elem="XNXQMC" 展示当前学期，不是 select 或 active 节点。
      // 例如："2026-2027学年1学期"；将展示文本映射到接口中的 DM："2026-2027-1"。
      if (!xnxq) {
        var termNode = document.querySelector('[data-elem="XNXQMC"]');
        var termText = termNode ? (termNode.textContent || '') : '';
        var termMatch = termText.match(/(20[0-9]{2})-(20[0-9]{2})\s*学年\s*([12])\s*学期/);
        if (termMatch) {
          var termDm = termMatch[1] + '-' + termMatch[2] + '-' + termMatch[3];
          // HEU 的 dqxnxq 接口可能只返回旧的当前学期，而页面已切到下一学期。
          // 页面显示的学期才是用户选择，不能再要求它必须出现在这份旧列表中。
          xnxq = termDm;
        }
      }
      // 若页面没有学期控件，才使用接口标记的当前学期；禁止无条件取 rows[0]。
      if (!xnxq) {
        var current = rows.find(function(row) {
          return row.DM && (row.SFDQ === '1' || row.SFDQ === 1 || row.CURRENT === '1' || row.current === true);
        });
        xnxq = current ? String(current.DM) : '';
      }
      if (!xnxq) throw new Error('无法识别当前选中的学期，请先在教务页面选择学期后再点导入');
      return fetch('/jwapp/sys/wdkb/modules/xskcb/xskcb.do', {
        method:'POST',
        headers:{'Content-Type':'application/x-www-form-urlencoded','X-Requested-With':'XMLHttpRequest'},
        body:'XNXQDM='+encodeURIComponent(xnxq),
        credentials:'include'
      }).then(function(r){ return r.text().then(function(txt){
        return {xnxq:xnxq, txt:txt};
      });});
    })
    .then(function(o){
      // 抓节次时间：从页面 DOM 找"是否显示节次时间"开启后的节次文本
      // 格式：节次列每个 cell 含 "1:08:00~08:45" 或 "1\\n08:00~08:45"
      var periods = [];
      try {
        var nodes = document.querySelectorAll('[class*="jc"],[class*="jcdm"],[class*="jcbz"],[id*="node"],[id*="jc"]');
        var seen = {};
        for (var i = 0; i < nodes.length; i++) {
          var txt = (nodes[i].innerText || nodes[i].textContent || '').trim();
          // 匹配 "1:08:00~08:45" 或 "1 08:00~08:45"
          var m = txt.match(/^([0-9]{1,2})[:\\s]+([0-2]?[0-9]:[0-5][0-9])[~～-]([0-2]?[0-9]:[0-5][0-9])$/);
          if (m && !seen[m[1]]) {
            seen[m[1]] = true;
            periods.push({node:parseInt(m[1],10), start:m[2], end:m[3]});
          }
        }
        // 如果没抓到（DOM 选择器不对），从整个 body innerText 用 regex 全局抓
        if (periods.length === 0) {
          var allText = document.body.innerText || '';
          // 跨多行匹配节次文本 "1\n08:00~08:45"
          var re = /([0-9]{1,2})[:\s]\s*([0-2]?[0-9]:[0-5][0-9])[~～-]([0-2]?[0-9]:[0-5][0-9])/g;
          var mm;
          while ((mm = re.exec(allText)) !== null) {
            var n = parseInt(mm[1], 10);
            if (n >= 1 && n <= 20 && !seen[n]) {
              seen[n] = true;
              periods.push({node:n, start:mm[2], end:mm[3]});
            }
          }
        }
        // 按 node 排序
        periods.sort(function(a,b){ return a.node - b.node; });
      } catch(e) { periods = []; }
      window.__sleepyBridge.onWiseduResult(JSON.stringify({
        ok:true,
        data:o.txt,
        xnxq:o.xnxq,
        periods:periods
      }));
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

/**
 * 东北大学 (jwxt.neu.edu.cn) 金智新版教务的课表 JSON 抓取。
 *
 * 课表页面没有可供 HTML parser 使用的课程数据。登录态下依次取得当前学期、可用校区，
 * 再向 getMyScheduleDetail.do 提交表单；返回的 datas.arrangedList 由 JwNeuParser 解析。
 */
private const val NUIT_FETCH_JS = """
(function(){
  fetch('/jwapp/sys/homeapp/api/home/currentUser.do',{credentials:'include'})
    .then(function(r){return r.json();})
    .then(function(u){
      var term=u&&u.datas&&u.datas.welcomeInfo&&u.datas.welcomeInfo.xnxqdm;
      if(!term) throw new Error('无法识别当前学期');
      return fetch('/jwapp/sys/homeapp/api/home/student/courses.do?termCode='+encodeURIComponent(term),{credentials:'include'});
    })
    .then(function(r){return r.text();})
    .then(function(data){window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true,data:data}));})
    .catch(function(e){window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false,err:String(e)}));});
})();
"""

private const val KUST_FETCH_JS = """
(function(){
  fetch('/api/uppcard/kbsz/queryAllTerm',{credentials:'include'})
    .then(function(r){return r.json();})
    .then(function(terms){
      var list=terms&&terms.data||[]; var term=list[0]&&list[0].XNXQ;
      if(!term) throw new Error('无法识别昆明理工学期');
      return fetch('/api/uppcard/kbsz/queryAWeekSchedule?XNXQ='+encodeURIComponent(term),{credentials:'include'});
    })
    .then(function(r){return r.text();})
    .then(function(data){window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true,data:data}));})
    .catch(function(e){window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false,err:String(e)}));});
})();
"""

/**
 * 东北大学 (jwxt.neu.edu.cn) 金智新版教务的课表 JSON 抓取。
 *
 * 课表页面没有可供 HTML parser 使用的课程数据。登录态下依次取得当前学期、可用校区，
 * 再向 getMyScheduleDetail.do 提交表单；返回的 datas.arrangedList 由 JwNeuParser 解析。
 *
 * 兼容北京航空航天大学新本研教务 (byxt.buaa.edu.cn, 2026-09-19 9 仓 cross-verified)：
 *   协议层同源 (金智 jwapp homeapp family), 但 BUAA 在 21/21 仓均 campusCode=""
 *   直接 POST 不取校区端点 (该端点 byxt 可能不暴露或返回非预期)。按 host 分流,
 *   NEU 走完整三步 (currentUser → campus → schedule), byxt 走两步
 *   (currentUser → schedule, campusCode 留空)。
 */
const val NEU_FETCH_JS = """
(function(){
  function finish(payload) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify(payload));
  }

  function fetchJson(url, options) {
    var request = Object.assign({credentials:'include'}, options || {});
    return fetch(url, request).then(function(response) {
      return response.text().then(function(body) {
        if (!response.ok) throw new Error('请求失败 HTTP ' + response.status + ': ' + url);
        try {
          return JSON.parse(body);
        } catch (e) {
          throw new Error('接口返回不是 JSON: ' + url);
        }
      });
    });
  }

  // 兼容 NEU + byxt.buaa.edu.cn 同协议族 (金智 jwapp homeapp)
  var NEU_HOSTS = ['jwxt.neu.edu.cn', 'byxt.buaa.edu.cn'];

  try {
    var hostname = (location.hostname || '').toLowerCase();
    var isSupportedHost = NEU_HOSTS.indexOf(hostname) >= 0;
    if (!isSupportedHost) {
      finish({ok:false, err:'请先完成登录并进入教务系统 (东北大学 / 北京航空航天大学新本研) 后再点导入'});
      return;
    }

    // BUAA byxt.buaa.edu.cn: campusCode='' 直接 POST, 不取 getMyScheduledCampus
    //   9 仓实锤 (fontlos/buaa-api + BUAASubnet/UBAA + CoolwindHF/buaa2wakeup +
    //   cantBeFoundGroup/OpenBUAA + el-ev/BUAA-ics-gen + Krignd/KAgenda +
    //   Yiki21/iclass_buaa_tui + Lidozs55/BUAAer + Alyssumira/BUAA-Schedule),
    //   该端点在 byxt 未公开/不返回有效 campus 列表。
    var isBuaaByxt = hostname === 'byxt.buaa.edu.cn';

    fetchJson('/jwapp/sys/homeapp/api/home/currentUser.do')
    .then(function(userData) {
      var termCode = userData && userData.datas && userData.datas.welcomeInfo &&
        userData.datas.welcomeInfo.xnxqdm;
      if (!termCode) throw new Error('当前用户信息中没有学期代码，请重新登录后重试');

      if (isBuaaByxt) {
        var bodyBuaa = 'termCode=' + encodeURIComponent(String(termCode)) +
          '&campusCode=&type=term';
        return fetchJson('/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do', {
          method:'POST',
          headers:{
            'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8',
            'X-Requested-With':'XMLHttpRequest'
          },
          body:bodyBuaa
        });
      }

      return fetchJson(
        '/jwapp/sys/homeapp/api/home/student/getMyScheduledCampus.do?termCode=' +
        encodeURIComponent(String(termCode))
      ).then(function(campusData) {
        var campuses = campusData && campusData.datas;
        var campusCode = Array.isArray(campuses) && campuses[0] && campuses[0].id;
        if (campusCode === undefined || campusCode === null || campusCode === '') {
          throw new Error('当前学期没有可用校区信息');
        }

        var body = 'termCode=' + encodeURIComponent(String(termCode)) +
          '&campusCode=' + encodeURIComponent(String(campusCode)) + '&type=term';
        return fetchJson('/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do', {
          method:'POST',
          headers:{
            'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8',
            'X-Requested-With':'XMLHttpRequest'
          },
          body:body
        });
      });
    })
    .then(function(scheduleData) {
      var arranged = scheduleData && scheduleData.datas && scheduleData.datas.arrangedList;
      if (!Array.isArray(arranged)) throw new Error('课表响应中没有 arrangedList');
      finish({ok:true, data:JSON.stringify(scheduleData)});
    })
    .catch(function(error) {
      finish({ok:false, err:String(error && error.message ? error.message : error)});
    });
  } catch (error) {
    finish({ok:false, err:String(error && error.message ? error.message : error)});
  }
})();
"""

/**
 * CQU (重庆大学门户 my.cqu.edu.cn) 协议：在 WebView 内 fetch 课表 JSON + 抓节次时间。
 *
 * 前提：用户已在 WebView 里登录统一身份认证（2026-06 起含动态验证码双因素，人工输入即可）
 * 并落到 my.cqu.edu.cn 域内（登录后任意页面均可，token 存在该域的 localStorage）。
 *
 * 流程（复用 __sleepyBridge.onWiseduResult 同一回调通道，payload 同为 {ok, data, periods}）：
 *  1) localStorage['cqu_edu_ACCESS_TOKEN'] 取 Bearer token；取不到报"请先登录"
 *  2) GET /api/resourceapi/session/info-detail → curSessionId（当前学期）
 *  3) POST /api/timetable/class/timetable/student/my-table-detail?sessionId=… body=[学号]
 *     学号从页面 .trigger-user-name 文本 "姓名 [2025xxxx]" 提取
 *  4) GET /api/workspace/time-pattern/session-time-pattern → 节次时间（periodOrder/startTime/endTime）
 *  5) 通过 __sleepyBridge.onWiseduResult({ok, data, periods}) 回调
 *
 * 接口形状外部佐证：时光课程表 cqu.js（茵符草）、321CQU/pymycqu course/tools.py。
 */
private const val YETHAN_FETCH_JS = """
(function(){
  try {
    if (location.hostname !== 'yhxt.swjtu.edu.cn') {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录西南交通大学逐专平台后再点导入'}));
      return;
    }
    var token = '';
    try { token = localStorage.getItem('ytoken') || ''; } catch(e) {}
    if (!token) {
      // 按页面标记区分卡点 (2026-09-16 用户复测: 微信扫码页点导入, 旧文案不指路):
      //   ① 微信扫码页: 「使用微信扫一扫登录」/「微信登录」入口标记
      //   ② 账号密码页: password 输入框
      //   ③ 其余: 通用文案
      var pageHint = '';
      try {
        var lower = (document.body ? document.body.innerText : '') || '';
        if (lower.indexOf('使用微信扫一扫登录') >= 0 || lower.indexOf('微信登录') >= 0) {
          pageHint = '当前停在微信扫码登录页：请用微信扫码并确认，或点「微信登录」旁的切换按钮改用账号密码登录，登录完成后再点导入';
        } else if (document.querySelector('input[type="password"]')) {
          pageHint = '当前停在账号密码登录页：请输入学号密码和验证码完成登录，登录完成后再点导入';
        }
      } catch(e2) {}
      if (!pageHint) pageHint = '未取到登录凭据，请先登录逐专平台后再点导入';
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:pageHint}));
      return;
    }
    var headers = {Accept:'application/json', 'ytoken':token};
    var get = function(path) {
      return fetch(path, {method:'GET', credentials:'include', headers:headers}).then(function(r) {
        return r.text().then(function(txt) {
          if (!r.ok) throw new Error('HTTP ' + r.status);
          return txt;
        });
      });
    };
    Promise.all([
      get('/yethan/common/course-schedule/student-course-schedule'),
      get('/yethan/common-config')
    ]).then(function(values){
      var schedule = JSON.parse(values[0]);
      var config = JSON.parse(values[1]);
      if (!schedule || (schedule.code !== '00000' && schedule.code !== 0)) {
        var code = schedule && schedule.code ? String(schedule.code) : 'unknown';
        throw new Error('课表接口返回 ' + code + '（登录态可能已过期，请刷新重登）');
      }
      window.__sleepyBridge.onWiseduResult(JSON.stringify({
        ok:true,
        data:values[0],
        yethanConfig:config
      }));
    }).catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

/**
 * CQU（重庆大学门户）fetch 脚本：Bearer token 取自 localStorage。
 */
private const val CQU_FETCH_JS = """
(function(){
  try {
    if (location.hostname.indexOf('cqu.edu.cn') < 0) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录并进入重庆大学门户后再点导入'}));
      return;
    }
    var token = '';
    try { token = (localStorage.getItem('cqu_edu_ACCESS_TOKEN') || '').replaceAll('\"', ''); } catch(e) {}
    if (!token) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'未取到登录凭据，请先登录 my.cqu.edu.cn 再点导入'}));
      return;
    }
    var studentId = '';
    try {
      var el = document.querySelector('.trigger-user-name');
      var m = el ? (el.innerText || '').match(/\\[(.*?)\\]/) : null;
      studentId = m ? m[1] : '';
    } catch(e) {}
    if (!studentId) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'页面上未找到学号，请确认已登录门户首页'}));
      return;
    }
    var auth = {credentials:'include', headers:{'Content-Type':'application/json', 'Authorization':'Bearer ' + token}};
    fetch('/api/resourceapi/session/info-detail', auth)
    .then(function(r){
      if (!r.ok) throw new Error('获取学期信息失败 HTTP ' + r.status);
      return r.json();
    })
    .then(function(session){
      var termId = session.curSessionId;
      if (!termId) throw new Error('学期信息里没有 curSessionId');
      var body = Object.assign({}, auth, {method:'POST', body: JSON.stringify([studentId])});
      return fetch('/api/timetable/class/timetable/student/my-table-detail?sessionId=' + encodeURIComponent(termId), body);
    })
    .then(function(r){
      if (!r.ok) throw new Error('获取课表失败 HTTP ' + r.status + '（登录态可能过期，请刷新重登）');
      return r.text();
    })
    .then(function(txt){
      // 节次时间：time-pattern 接口拿不到就置空（解析端允许无 periods）
      var periods = [];
      return fetch('/api/workspace/time-pattern/session-time-pattern', auth)
      .then(function(r){ return r.ok ? r.json() : null; })
      .then(function(tp){
        try {
          var vos = (tp && tp.data && tp.data.classPeriodVOS) || [];
          for (var i = 0; i < vos.length; i++) {
            var v = vos[i];
            periods.push({
              node: v.periodOrder || (i + 1),
              start: v.startTime || '',
              end: v.endTime || ''
            });
          }
          periods.sort(function(a,b){ return a.node - b.node; });
        } catch(e) { periods = []; }
        window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true, data:txt, periods:periods}));
      });
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

/**
 * WHUT (武汉理工 jwxt.whut.edu.cn) — 金智 jwapp 变体 (kcbcxby 微应用)。
 *
 * 前提：用户已在 WebView 走统一身份认证 (zhlgd.whut.edu.cn CAS) 登录 jwxt。
 *
 * 流程（复用 __sleepyBridge.onWiseduResult 同一回调通道）：
 *  1) currentUser.do → datas.userId (学号) + datas.welcomeInfo.xnxqdm (当前学期 DM)
 *  2) cxxljc.do (XN+XQ) → rows[0].XQKSRQ (第一周周一), ZZC (总周数) — 供前端展示
 *  3) jcjcx.do → rows[{DM,MC}] 节次映射表 (MC 含 "节" 的行按顺序 = 物理节次 1..13);
 *     拉不到时用 fallback 表 (1..5→1..5, 8..12→6..10, 14..16→11..13, 6/7/13 缺位)
 *  4) cxxskcb.do (XH=学号&XNXQDM=学期) → datas.cxxskcb.rows[] 课表
 *     rows 里 KSJC/JSJC (大节 DM) 就地替换为物理节次再回传
 *  5) 解析由 JwWhutParser 完成 (兼容 datas.xskcb 路径)
 *
 * 上游协议形态: shiguang_warehouse (MIT) whut_01.js + iwut (AGPL, 仅引形态)。
 */
private const val CHAOXING_FETCH_JS = """
(function(){
  try {
    // 管理前缀按入口 URL 推断: 部分部署挂 /admin (吉林工商), 部分无前缀
    var m = location.pathname.match(/^\/(\w+)\//);
    var base = location.pathname.indexOf('/admin/') === 0 ? '/admin' : '';
    var get = function(url){
      return fetch(url, {credentials:'include', headers:{'X-Requested-With':'XMLHttpRequest'}})
        .then(function(r){ return r.json(); });
    };
    // 1) 当前学期 + 校区: getMenuList 响应含 jsxq.dataXnxq / xqid (session 态)
    var ctx = get(base + '/api/getMenuList').then(function(j){
      var jsxq = (j && (j.jsxq || (j.data && j.data.jsxq))) || {};
      return { xnxq: jsxq.dataXnxq || '', xqid: jsxq.xqid || '' };
    }).catch(function(){ return { xnxq: '', xqid: '' }; });
    ctx.then(function(c){
      if (!c.xnxq) {
        // getMenuList 缺 jsxq 时回退: 让服务端自己用会话学期 (queryKbForGrdb 无参)
      }
      // 2) 个人课表 (无参 — 服务端按会话学期返回本人数据)
      return get(base + '/pkgl/xskb/queryKbForGrdb?sf_request_type=ajax')
      .then(function(kb){
        var rows = kb && kb.data;
        if (!rows || !rows.length) throw new Error('课表为空: 请先在教务里打开"我的课表"页再导入');
        // 3) 节次时间 (尽力而为, 失败不阻断 — periods 仅用于展示)
        return get(base + '/api/getZclistByXnxq?xnxq=' + encodeURIComponent(c.xnxq) +
                  '&xqid=' + encodeURIComponent(c.xqid) + '&role=&userId=&sf_request_type=ajax')
        .then(function(zc){
          var periods = [];
          try {
            var arr = zc.data.jcsjszList || [];
            for (var i = 0; i < arr.length; i++) {
              periods.push({node: parseInt(arr[i].jc, 10), kssj: arr[i].kssj, jssj: arr[i].jssj});
            }
          } catch(e) {}
          var payload = JSON.stringify({
            xnxq: c.xnxq,
            dqzc: (zc && zc.data && zc.data.dqzc) || null,
            rows: rows,
            periods: periods
          });
          window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true, data: payload, periods: periods}));
        });
      });
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(e) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
  }
})()"""

/**
 * 强智移动教务 SPA (type=qz_app) 课表 JSON 抓取。
 *
 * 课表只在 {ApiUrl}/student/curriculum 移动 JSON API 中 (token header 鉴权), 页面
 * HTML 无课程数据。链路: 相对路径 serverconfig.json (各校部署根可不同, 先试当前
 * 目录再试 /dist/serverconfig.json) 免鉴权发现 ApiUrl → sessionStorage.Token 作
 * `token` 请求头 POST 课表 → 透传 JSON。未登录 (无 Token) 或会话过期 (code:401)
 * 直接报错, 不落伪"空学期"。JS 只做 fetch 与传输层状态路由, 协议字段解码全部在
 * JwQzAppParser (跨语言 invariant)。
 */
const val QZ_APP_FETCH_JS = """
(function(){
  try {
    var post = function(obj){
      window.__sleepyBridge.onWiseduResult(JSON.stringify(obj));
    };
    var token = '';
    try { token = sessionStorage.getItem('Token') || ''; } catch(e) {}
    if (!token) {
      post({ok:false, err:'未取到登录令牌: 请先登录移动教务后再点导入'});
      return;
    }
    var getCfg = function(url){
      return fetch(url, {credentials:'include'})
        .then(function(r){
          if (!r.ok) { throw new Error('serverconfig.json HTTP ' + r.status); }
          return r.json();
        });
    };
    getCfg('serverconfig.json')
      .catch(function(){
        return getCfg('/dist/serverconfig.json');
      })
      .then(function(cfg){
        var apiUrl = String(cfg.ApiUrl || '').replace(/\/+$/,'');
        if (!apiUrl) { throw new Error('serverconfig.json 缺 ApiUrl'); }
        return apiUrl;
      })
      .then(function(apiUrl){
        var call = function(path){
          return fetch(apiUrl + path, {
            method:'POST',
            credentials:'include',
            headers: { 'token': token }
          }).then(function(r){ return r.text(); });
        };
        // 课表端点一次只回一周 (week= 空 = 当前教学周, data 单元素);
        // 只抓当前周会丢掉仅在后续周出现的课 — 先取 teachingWeek 周数列表,
        // 再并行逐周拉取合并 (与燕大 boya_pp 逐周方案同构)
        return call('/teachingWeek').then(function(twText){
          var weekNums = [];
          try {
            var tw = JSON.parse(twText);
            if (tw && tw.code != '401' && tw.data && tw.data.length) {
              for (var i = 0; i < tw.data.length; i++) {
                var n = parseInt(tw.data[i].week, 10);
                if (n >= 1 && n <= 30) weekNums.push(n);
              }
            }
          } catch(e) {}
          if (!weekNums.length) weekNums = [1];
          var expired = false;
          var reqs = weekNums.map(function(w){
            return call('/student/curriculum?week=' + w + '&kbjcmsid=')
              .then(function(text){
                try { if (JSON.parse(text).code == '401') expired = true; } catch(e) {}
                return text;
              })
              .catch(function(){ return null; });
          });
          return Promise.all(reqs).then(function(texts){
            if (expired) {
              post({ok:false, err:'登录已过期: 请重新登录后再点导入'});
              return;
            }
            var ok = texts.filter(function(t){ return t !== null; });
            if (!ok.length) {
              post({ok:false, err:'课表接口无响应: 请确认已登录并进入课表页'});
              return;
            }
            post({ok:true, data: JSON.stringify({weeks: ok.map(function(t){
              try { return JSON.parse(t); } catch(e) { return null; }
            }).filter(function(j){ return j !== null; })})});
          });
        });
      })
      .catch(function(e){
        post({ok:false, err:String(e && e.message || e)});
      });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})()"""

private const val BOYA_PP_FETCH_JS = """
(function(){
  try {
    // 入口是燕大 CAS (cer.ysu.edu.cn), 登录后回调落到 yjsxt.ysu.edu.cn —
    // 只在平台域放行, CAS 页上点导入给出明确提示而非 404 请求
    if (location.hostname.indexOf('yjsxt.ysu.edu.cn') < 0 && location.pathname.indexOf('/pp/') < 0) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先完成统一身份认证登录并进入研究生平台后再点导入'}));
      return;
    }
    var hdrs = {'Protocol-Type': location.protocol.replace(/:/g, '')};
    try {
      var m = document.cookie.match(/(?:^|;\s*)token=([^;]+)/);
      var tk = m ? decodeURIComponent(m[1]) : '';
      if (!tk) {
        var raw = sessionStorage.getItem('ROOT:APPSTORE');
        if (raw) { var j = JSON.parse(raw); if (j && j.token) tk = j.token; }
      }
      if (tk) hdrs['token'] = tk;
    } catch(e) {}
    var get = function(url){
      return fetch(url, {credentials:'include', headers:hdrs}).then(function(r){
        if (r.status === 401) throw new Error('登录态已失效，请重新登录研究生平台后再点导入');
        if (!r.ok) throw new Error('接口请求失败 HTTP ' + r.status);
        return r.json();
      }).then(function(j){
        if (!j || (j.code !== 200 && j.code !== '200')) {
          throw new Error((j && j.message) ? j.message : '接口返回异常');
        }
        return j.data;
      });
    };
    get('/api/login/currentUser')
    .then(function(u){ return (u && u.termName) ? String(u.termName) : ''; })
    .catch(function(){ return ''; })
    .then(function(termName){
      return get('/api/microForm/term').then(function(terms){
        var list = terms || [];
        var cur = null;
        for (var i = 0; i < list.length; i++) {
          if (termName && list[i].termName === termName) { cur = list[i]; break; }
          if (!termName && String(list[i].currentTerm) === '是') { cur = list[i]; }
        }
        if (!cur || !cur.termName) throw new Error('未找到当前学期，请确认已进入本学期课表页');
        return cur;
      });
    })
    .then(function(term){
      var T = term.termName;
      var periodsP = get('/api/schedule/class/setting/current?yearTerm=' + encodeURIComponent(T))
      .then(function(cfg){
        var periods = [];
        try {
          var lc = (cfg && cfg.lessonConfig) || [];
          for (var i = 0; i < lc.length; i++) {
            var t = lc[i].lessonTime || [];
            periods.push({
              node: lc[i].lessonNumber || (i + 1),
              start: String(t[0] || '').slice(11, 16),
              end: String(t[1] || '').slice(11, 16)
            });
          }
          periods.sort(function(a, b){ return a.node - b.node; });
        } catch(e) { periods = []; }
        return periods;
      }).catch(function(){ return []; });
      // 总周数: weekEnd 优先 ("19"), 兜底按起止日推算, 再兜底 30
      var maxWeek = parseInt(term.weekEnd, 10);
      if (!(maxWeek >= 1 && maxWeek <= 30)) {
        try {
          var ms = new Date(term.termEndTime) - new Date(term.termBeginTime);
          maxWeek = Math.ceil(ms / (7 * 24 * 3600 * 1000));
        } catch(e) { maxWeek = 0; }
        if (!(maxWeek >= 1 && maxWeek <= 30)) maxWeek = 30;
      }
      var weekReqs = [];
      for (var w = 1; w <= maxWeek; w++) {
        weekReqs.push(
          get('/api/schedule/table/byStudent?page=0&size=500&whichWeek=' + w +
              '&yearTerm=' + encodeURIComponent(T))
          .then(function(rows){ return rows || []; })
          .catch(function(e){
            // 单周偶发失败不致命, 静默跳过; 但 401 登录失效必须中止 —
            // 否则会以部分数据伪装成完整课表
            if (e && String(e.message || '').indexOf('登录态已失效') >= 0) throw e;
            return [];
          })
        );
      }
      return Promise.all([Promise.all(weekReqs), periodsP]).then(function(rs){
        var rows = [];
        for (var k = 0; k < rs[0].length; k++) rows = rows.concat(rs[0][k]);
        if (!rows.length) throw new Error('课表为空：请先在研究生平台"我的课表"页确认本学期已有课程');
        window.__sleepyBridge.onWiseduResult(JSON.stringify({
          ok: true,
          data: JSON.stringify({term: T, rows: rows}),
          periods: rs[1],
          startDate: String(term.termBeginTime || '').slice(0, 10)
        }));
      });
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e && e.message || e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

private const val WHUT_FETCH_JS = """
(function(){
  try {
    if (location.hostname.indexOf('whut.edu.cn') < 0) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录并进入武汉理工教务后再点导入'}));
      return;
    }
    var post = function(url, body){
      return fetch(url, {
        method:'POST',
        headers:{'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'},
        body: body || '',
        credentials:'include'
      }).then(function(r){ return r.json(); });
    };
    // 0) 切到本科生角色 (EMAP homeapp 角色机制; 值为 WHUT 全站固定的本科生 appRole GUID,
    //    与 iwut 掌上吾理 bachelor-import 一致)。失败不阻断 — 有些账号可能只有单角色。
    fetch('/jwapp/sys/homeapp/api/home/changeAppRole.do?appRole=ef212c48c8f84be79acbd9d81b090f51',
      {method:'POST', credentials:'include', headers:{'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'}})
    .catch(function(){})
    .then(function(){
    // 1) 当前学期 (学号在 wdkbby 通道不需要 — 服务端按会话返回本人数据)
    return fetch('/jwapp/sys/homeapp/api/home/currentUser.do', {credentials:'include'})
    .then(function(r){ return r.json(); })
    .then(function(u){
      var d = u.datas || {};
      var xh = d.userId || '';
      var xnxq = (d.welcomeInfo && d.welcomeInfo.xnxqdm) || '';
      if (!xnxq) throw new Error('未取到当前学期, 请确认已登录本科教务');
      // 2) 节次 DM→物理节映射: jcjcx.do 按名称含"节"计数 (中课1/晚课等不带"节"的跳过)。
      //    2026-09 采集包实锤: DM 表 = 1..5,8..12,14..16, 与 fallback 一致。
      var fallbackMap = {"1":1,"2":2,"3":3,"4":4,"5":5,"8":6,"9":7,"10":8,"11":9,"12":10,"14":11,"15":12,"16":13};
      var sectionMap = {};
      var mapReady = post('/jwapp/sys/wdkbby/modules/dzkz/jcjcx.do', '').then(function(j){
        try {
          var rows = (j.datas && j.datas.jcjcx && j.datas.jcjcx.rows) || [];
          var n = 0;
          for (var i = 0; i < rows.length; i++) {
            var mc = String(rows[i].MC || '');
            if (mc.indexOf('节') >= 0) { n += 1; sectionMap[String(rows[i].DM)] = n; }
          }
          if (n < 10) sectionMap = fallbackMap;
        } catch(e) { sectionMap = fallbackMap; }
      }).catch(function(){ sectionMap = fallbackMap; });
      var applyMap = function(rows){
        for (var i = 0; i < rows.length; i++) {
          var r = rows[i];
          if (sectionMap[r.KSJC] != null) r.KSJC = String(sectionMap[r.KSJC]);
          if (sectionMap[r.JSJC] != null) r.JSJC = String(sectionMap[r.JSJC]);
        }
      };
      return mapReady.then(function(){
        // 3) 主通道: wdkbby/学生课程表 cxxszhxqkb.do (2026-09 用户采集包实锤 —
        //    学生"我的课表"页面走这条: POST XNXQDM → datas.cxxszhxqkb.rows[],
        //    字段 KCM/SKXQ/KSJC/JSJC/SKZC 与解析内核同构, KSJC 为 DM 值)。
        //    旧 kcbcxby/cxxskcb.do 是"教室课表"(教师端)微应用, 学生账号常 403/空。
        return post('/jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do', 'XNXQDM=' + encodeURIComponent(xnxq))
        .then(function(k){
          var rows = (k.datas && k.datas.cxxszhxqkb && k.datas.cxxszhxqkb.rows) || [];
          if (rows.length > 0) {
            applyMap(rows);
            window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true, data:JSON.stringify(k), periods:[]}));
            return null;
          }
          // 4) 兜底: 老 kcbcxby/cxxskcb.do 通道 (部分老版本部署仍用它)
          return post('/jwapp/sys/kcbcxby/modules/xskcb/cxxskcb.do', 'XH=' + encodeURIComponent(xh) + '&XNXQDM=' + encodeURIComponent(xnxq))
          .then(function(k2){
            var rows2 = (k2.datas && k2.datas.cxxskcb && k2.datas.cxxskcb.rows) || [];
            if (rows2.length === 0) throw new Error('课表为空: 请确认已在教务"我的课表"页可见本学期课程');
            applyMap(rows2);
            window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true, data:JSON.stringify(k2), periods:[]}));
          });
        });
      });
    });
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

/**
 * 合工大 EAMS5 (jxglstu.hfut.edu.cn) 协议：在 WebView 内 fetch 三段拿课表 JSON。
 * 前提：用户已在 WebView 里走完 CAS 登录
 *   1) https://cas.hfut.edu.cn/cas/login (POST username + password + execution + _eventId + lt)
 *   2) 落到 jxglstu.hfut.edu.cn 域 (Cookie 自动带入同源 /eams5-student/ 路径)
 *
 * 流程（复用 __sleepyBridge.onWiseduResult 同一回调通道，payload 同为 {ok, data}）：
 *   1) GET /eams5-student/for-std/course-table           → HTML 含 studentId (script 标签里)
 *      - 已登录 → 302 跟到 /for-std/course-table/info/<studentId>, 页面 HTML <script>
 *        段里有 `var studentId = '2024210001';` 或对象字面量 `studentId:'2024210001',`
 *      - 未登录 → 302 跟到 /eams5-student/login, 报"登录态已失效"
 *      - 顺带解析 semesterId (script 段 `semesterId: 234`), 供第 3 段用
 *   2) GET /eams5-student/for-std/course-table/info/<studentId>
 *      → HTML <script> 段含 `bizTypeId: 23` (参考仓 parseBizTypeId 同形正则)
 *   3) GET /eams5-student/for-std/course-table/get-data?bizTypeId=<biz>&dataId=<studentId>[&semesterId=<sem>]
 *      → JSON 顶层 lessonIds:[<int>...] (可能包在 result 下, 两种形态都取)
 *   4) POST /eams5-student/ws/schedule-table/datum
 *      body: {"lessonIds":[<int>...], "studentId":<学号,纯数字转 int>, "weekIndex":""}
 *      resp: schedule-table/datum JSON 全文
 *   5) 通过 __sleepyBridge.onWiseduResult({ok, data}) 回调
 *
 * v2 四段链说明（2026-09-11, 合肥工业大学用户反馈 datum HTTP 500）：
 *   - v1 简化直接 POST datum + 空 lessonIds — 参考仓 JxglstuService.kt:53 注释明言
 *     "需要提交前面获取到的数据才可以, 否则返回500错误"。HFUT 服务端对空 lessonIds
 *     直接 500, v1 从未真正可用; 本版补齐 info → get-data 两段拿真实 lessonIds。
 *   - 兜底: 第 2/3 段任一失败 (bizTypeId 解析不到 / get-data 非 200 / JSON 异常)
 *     → lessonIds 退回空数组, 行为等同 v1, 保护共用本 JS 的其它 EAMS5 校
 *     (中国矿业大学北京 cumtb.edu.cn) 不因新链路异常而整体失败。
 *   - studentId 纯数字时转 Number 发送 (参考仓以 int 提交), 字母数字学号保持字符串。
 *
 * 外部佐证：Chiu-xaH/HFUT-Schedule JxglstuService.kt + JxglstuRepository.kt
 * (parseStudentId / parseBizTypeId / getDatum), BoynChan/HfutOpenApi CourseCrawler.java。
 * 2026-09 用户反馈原正则只匹配 quoted-digit 形态, 学号嵌入 supwisdom 对象字面量
 * (studentId: '...') 时漏, 本版放宽正则 + 加 r.url 检测登录失效。
 */
private const val EAMS5_FETCH_JS = """
(function(){
  try {
    if (location.hostname === 'one.hfut.edu.cn') {
      // issue #25: 门户 (统一信息门户) 不是教务 — 且 'one.hfut.edu.cn'.indexOf('hfut.edu.cn')
      // >= 0 会误过下方弱包含守卫。显式拦截并引导用户去 jxglstu 教务入口。
      // issue #46 (2026-09): 门户上线了课表预览卡 (/api/operation/course-timetable/search/<n>/<date>,
      // Bearer token, 只有本周+下周), 全学期课表仍只有 jxglstu 的 datum 有 — 拦截策略不变, 文案改准确。
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false,
        err:'检测到您在合工大统一信息门户 (one.hfut.edu.cn)。门户里只有本周/下周的课表预览, 完整学期课表请到"我的学校"选择合肥工业大学, 进入 jxglstu 教务系统后再点导入'}));
      return;
    }
    if (location.hostname.indexOf('hfut.edu.cn') < 0 && location.hostname.indexOf('jxglstu') < 0
        && location.hostname.indexOf('ahu.edu.cn') < 0 && location.hostname.indexOf('cumtb.edu.cn') < 0) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录并进入合工大教务后再点导入'}));
      return;
    }
    var PREFIX = '__EAMS5_PREFIX__';
    // 1) GET course-table 拿 studentId (Cookie 已带)。
    //    fetch 默认跟随重定向: 已登录 → /for-std/course-table 重定向到
    //    /for-std/course-table/info/<studentId>, 页面 HTML <script> 段里有
    //    `var studentId = '2024210001';` 或对象字面量 `studentId:'2024210001',`。
    //    未登录 → 重定向到 /eams5-student/login, 页面无 studentId。
    fetch(PREFIX + '/for-std/course-table', {credentials:'include'})
    .then(function(r){
      if (!r.ok) throw new Error('course-table 取 studentId 失败 HTTP ' + r.status + '（请确认已在教务主页登录）');
      return r.text().then(function(html){ return {html: html, finalUrl: r.url || ''}; });
    })
    .then(function(ctx){
      var html = ctx.html || '';
      // 检测会话失效: 最终 URL 含 /login (302 落到登录页)
      if (ctx.finalUrl.indexOf('/login') >= 0 || ctx.finalUrl.indexOf('login?') >= 0) {
        window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'登录态已失效,请在教务主页重新登录后再试'}));
        return null;
      }
      // 多形态匹配 — 2026-09 用户反馈 + 跨仓验证 (Chiu-xaH/HFUT-Schedule,
      // BoynChan/HfutOpenApi) 综合, 上游 script 段 studentId 写法不统一:
      //   A. var studentId = '2024210001';
      //   B. var studentId="2024210001";
      //   C. studentId:'2024210001',        ← supwisdom 对象字面量
      //   D. studentId: "2024210001",        ← supwisdom 对象字面量双引号
      //   E. studentId=2024210001;           ← 极少数裸数字
      //   F. studentId=2024210001&...        ← 在查询串里
      // 用一条宽松正则覆盖 A–F: 键名后 [=:] 可有引号, 值允许 \w (数字/字母/下划线)。
      // 命中后取第一个纯数字/字母数字串; 不依赖引号, 不依赖分号终止。
      // 与 JVM 端 EAMS5_STUDENT_ID_REGEX (data/jw/Eams5PathPrefix.kt) 同形 —
      // 单测锁契约, JS 端保持字符串字面量 (WebView JS context 无 JVM 调用通道)。
      var m = html.match(/studentId\s*[=:]\s*['"]?([A-Za-z0-9]+)['"]?/);
      var sid = m ? m[1] : null;
      if (!sid) {
        // 参考仓 dev 分支实测口径: course-table 302 Location =
        // /eams5-student/for-std/course-table/info/<studentId>, 其直接截断该路径取学号。
        // fetch 已自动跟随重定向, r.url (ctx.finalUrl) 即 Location 的等价物。
        m = /for-std\/course-table\/info\/(\d+)/.exec(ctx.finalUrl);
        if (m) sid = m[1];
      }
      if (!sid) return null;
      // semesterId 若本页 script 段带就顺手拿 (get-data 查询参数), 拿不到留空由 info 页兜底
      var sem = html.match(/semesterId\s*[=:]\s*['"]?(\d+)/);
      return {studentId: sid, semesterId: sem ? sem[1] : ''};
    })
    .then(function(ctx){
      if (!ctx) {
        window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'未取到 studentId,请在 course-table 页面停留后再试'}));
        return null;
      }
      var sid = ctx.studentId;
      var sem = ctx.semesterId;
      // 2) info 页拿 bizTypeId (参考仓 parseBizTypeId: /bizTypeId\s*:\s*(\d+)/ 同形)
      return fetch(PREFIX + '/for-std/course-table/info/' + sid, {credentials:'include'})
      .then(function(r){ return r.ok ? r.text() : ''; })
      .catch(function(){ return ''; })
      .then(function(infoHtml){
        var biz = '';
        if (infoHtml) {
          var bm = infoHtml.match(/bizTypeId\s*[=:]\s*['"]?(\d+)/);
          if (bm) biz = bm[1];
          if (!sem) {
            var sm = infoHtml.match(/semesterId\s*[=:]\s*['"]?(\d+)/);
            if (sm) sem = sm[1];
          }
        }
        // 3) get-data 拿 lessonIds[]; 任一环失败 → ids 留空数组 = v1 旧行为兜底
        var q = '/for-std/course-table/get-data?bizTypeId=' + biz + '&dataId=' + sid;
        if (sem) q += '&semesterId=' + sem;
        var chain = biz
          ? fetch(PREFIX + q, {credentials:'include'})
              .then(function(r){ return r.ok ? r.text() : ''; })
              .catch(function(){ return ''; })
          : Promise.resolve('');
        return chain.then(function(txt){
          var ids = [];
          var layoutId = null;
          try {
            var j = JSON.parse(txt);
            var got = j && (j.lessonIds || (j.result && j.result.lessonIds));
            if (got && got.length) ids = got;
            // issue #46: get-data 顶层带 timeTableLayoutId (采集包实锤 122),
            // 供第 3.5 段拉服务端权威节次表
            if (j && typeof j.timeTableLayoutId === 'number') layoutId = j.timeTableLayoutId;
          } catch (e) { /* get-data 异常 → 空数组兜底 */ }
          // 3.5) POST timetable-layout 拿 courseUnitList (各校区节次表, 宣城 12 节 ≠ 合肥 10 节)。
          //      参照 HFUTer/HFNewParserViewModel + 小爱系 provider + kirsh1/ustc-timetable 共识:
          //      节次不能客户端硬编码, 以 {timeTableLayoutId} 换取 courseUnitList。
          //      失败可降级: layoutId 空/接口 500 → units 留空, parser 走旧 heuristic 兜底。
          var layoutChain = layoutId
            ? fetch(PREFIX + '/ws/schedule-table/timetable-layout', {
                method:'POST',
                credentials:'include',
                headers:{'Content-Type':'application/json'},
                body: JSON.stringify({timeTableLayoutId: layoutId})
              }).then(function(r){ return r.ok ? r.json() : null; })
                .catch(function(){ return null; })
            : Promise.resolve(null);
          return layoutChain.then(function(layout){
            var units = (layout && layout.result && layout.result.courseUnitList) || [];
            // 4) POST schedule-table/datum — lessonIds 为真课时数组; 纯数字学号按 int 提交
            return fetch(PREFIX + '/ws/schedule-table/datum', {
              method:'POST',
              credentials:'include',
              headers:{'Content-Type':'application/json'},
              body: JSON.stringify({
                lessonIds: ids,
                studentId: /^\d+$/.test(sid) ? Number(sid) : sid,
                weekIndex: ''
              })
            }).then(function(r){
              if (!r.ok) throw new Error('POST schedule-table/datum 失败 HTTP ' + r.status + ' (lessonIds:' + ids.length + ')');
              return r.text().then(function(datumTxt){
                if (!units.length) return datumTxt;                 // 无布局 → 原样 (parser heuristic)
                var periodUnits = units.map(function(u){            // 布局节次升序, 与 datum 原文合并
                  return {indexNo:u.indexNo, startTime:u.startTime, endTime:u.endTime};
                }).sort(function(a,b){ return a.indexNo - b.indexNo; });
                var merged;
                try {
                  var dj = JSON.parse(datumTxt);
                  dj.courseUnitList = periodUnits;                  // parser 精确查表锚点
                  merged = JSON.stringify(dj);
                } catch (e2) { merged = datumTxt; }
                return JSON.stringify({__layout: periodUnits, __datum: merged});
              });
            });
          });
        });
      });
    })
    .then(function(txt){
      if (!txt) return;
      // issue #46: 有布局时把 courseUnitList 转成 periods[] 供确认页作息表预填
      // (HHmm → 'HH:MM', 与 session-time-pattern 形态一致); 无布局 = 原行为 periods:[]
      var periods = [];
      var dataTxt = txt;
      try {
        var o = JSON.parse(txt);
        if (o && o.__layout && o.__datum) {
          dataTxt = o.__datum;
          for (var i = 0; i < o.__layout.length; i++) {
            var u = o.__layout[i];
            periods.push({
              node: u.indexNo,
              start: Math.floor(u.startTime/100) + ':' + (u.startTime%100 < 10 ? '0' : '') + (u.startTime%100),
              end: Math.floor(u.endTime/100) + ':' + (u.endTime%100 < 10 ? '0' : '') + (u.endTime%100)
            });
          }
        }
      } catch (e) { /* 非 merged 形态 (CUMTB/AHU/无布局) → 原样 */ }
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true, data:dataTxt, periods:periods}));
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

/**
 * 安大 (AHU) 自建'新教务' (jw.ahu.edu.cn, 金智 EAMS 新版部署形态)。
 *
 * 与合工大 EAMS5_FETCH_JS 的差异 (2026-09-06 cross-verified, 5 仓共识:
 * MoeclubM/AHU-AIO + qiqqqqq517/shangkeschschedule + abydym/Ahu_Plus +
 * Landon-3314/AHU-TimeTable + Zeraora-807/Anhui-Univ-DSH-Tool):
 *   - 不走 POST /ws/schedule-table/datum (安大返 HTTP 500, 不存在该 endpoint)
 *   - 走 GET /for-std/course-table/semester/<semesterId>/print-data
 *     (activities[] 含 weekday/weekIndexes/startUnit/endUnit/courseName/
 *      teacherNames/campus/building/room, 是真正的课表数据)
 *   - 不再依赖 get-data 的 lessons[]: 那是 metadata-only (courseCode/examMode),
 *     不含 weekday/节次, 不能直接渲染到课表
 *   - studentId (JW-internal Long, 非学号) 从 /grade/sheet 302 Location 提取
 *     (MoeclubM + abydym + Zeraora 共识 regex:
 *      /\/(?:semester-index|info)\/(\d+)(?:[/?#]|$)/)
 *   - dataId: 客户端尝试走 print-data 不需要 dataId; 若 print-data 失败, 退到
 *     get-data 走 dataId=<studentId> (MoeclubM 模式)
 *
 * 流程 (三段, 复用 __sleepyBridge.onWiseduResult 同一回调通道):
 *   1) GET /student/for-std/course-table → HTML 含 allSemesters + 已登录判定
 *   2) 从 HTML 提取 allSemesters, 取首个 semesterId
 *      并行: GET /student/for-std/grade/sheet (302 → 提取 studentId)
 *   3) GET /student/for-std/course-table/semester/<semesterId>/print-data?semesterId=<id>&hasExperiment=false
 *   4) __sleepyBridge.onWiseduResult({ok, data, periods}) 回调
 *
 * 外部佐证 (5 仓):
 *   - MoeclubM/AHU-AIO (lib/jw/api/jw_api.dart, GPL-3.0)
 *   - qiqqqqq517/shangkeschschedule (Apache-2.0, ahu.js)
 *   - abydym/Ahu_Plus (GPL-3.0, Kotlin 同栈 — 关键同栈证据)
 *   - Landon-3314/AHU-TimeTable (Flutter, print-data + get-data 双轨实现)
 *   - Zeraora-807/Anhui-Univ-DSH-Tool (TypeScript DeepSeek Harness)
 */
private const val EAMS5_AHU_FETCH_JS = """
(function(){
  try {
    if (location.hostname.indexOf('ahu.edu.cn') < 0) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录并进入安徽大学教务后再点导入'}));
      return;
    }
    var PREFIX = '__EAMS5_PREFIX__';
    // 1) GET course-table HTML, 提取 allSemesters (学期 JSON 数组) + 检测登录态
    fetch(PREFIX + '/for-std/course-table', {credentials:'include', redirect:'follow'})
    .then(function(r){
      // 会话失效判定 (Zeraora client.ts:608-617 + abydym SessionAuthenticator.kt + Landon-3314 isLoginExpired 共识):
      //   1) 302-399 redirect + Location 落到 CAS (/cas/login, one.ahu.edu.cn/cas)
      //   2) 200 OK + HTML 含 'name="lt"' AND 'name="execution"' 双字段 (CAS 登录页)
      //   3) URL 含 casloginform / 统一身份认证 / 请重新登录 (兜底)
      // 5 仓 cross-verified 2026-09-06 (a999c385/aa707ac2033872402 agents)
      var status = r.status;
      var locHeader = r.headers.get('location') || '';
      var isCasRedirect = status >= 300 && status < 400
        && (locHeader.indexOf('one.ahu.edu.cn/cas') >= 0
            || /\/cas\/login/.test(locHeader)
            || /\/cas\b/.test(locHeader));
      // r.text() 单次消费 — 先一次性读再判
      return r.text().then(function(body){
        // 同时读 r.url, fetch redirect:'follow' 后 finalUrl 是最终 URL (CAS 登录页)
        var finalUrl = r.url || '';
        var hasLtField = /name=["']lt["']/i.test(body);
        var hasExecField = /name=["']execution["']/i.test(body);
        var hasCasLoginUi = /统一身份认证|请重新登录|session timeout|会话已过期|name=["']casloginform["']/i.test(body);
        var loginExpired = finalUrl.indexOf('/login') >= 0
                        || finalUrl.indexOf('cas/login') >= 0
                        || finalUrl.indexOf('one.ahu.edu.cn/cas') >= 0
                        || finalUrl.indexOf('casloginform') >= 0
                        || isCasRedirect
                        || (hasLtField && hasExecField)
                        || hasCasLoginUi;
        return {html: body, finalUrl: finalUrl, status: status, loginExpired: loginExpired};
      });
    })
    .then(function(ctx){
      if (ctx.loginExpired) {
        window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false,
          err:'登录态已失效,请在教务主页重新登录后再试'}));
        return null;
      }
      var html = ctx.html || '';
      // 学期列表提取 — 5 仓共识 (a999c385 agent):
      //   AHU 是 Thymeleaf/J2EE 模板渲染, 学期列表嵌入 HTML 内
      //   `<select id="allSemesters"><option value="112">2024-2025-2</option>...`
      //   qiqqqqq517 ahu.js:37 / abydym CourseRepository.kt:119-167 / Zeraora client.ts:480-489
      //   共识 regex: <select[^>]*id=["']allSemesters["'][^>]*>([\s\S]*?)</select>
      //   + 内部 <option value="(\d+)"[^>]*>([^<]+)</option> 循环
      // 按 id 倒序取首 (服务器返回 newest-first, abydym + Zeraora 一致)
      var semesterId = null;
      var selectMatch = html.match(/<select[^>]*\bid=["']allSemesters["'][^>]*>([\s\S]*?)<\/select>/i);
      if (selectMatch) {
        var inner = selectMatch[1];
        var optionRegex = /<option[^>]*\bvalue=["'](\d+)["'][^>]*>([^<]*)<\/option>/gi;
        var m;
        var opts = [];
        while ((m = optionRegex.exec(inner)) !== null) {
          opts.push({id: parseInt(m[1], 10), name: m[2].trim()});
        }
        // 取 id 最大 (newest first; abydym .sortedByDescending { it.id }.first())
        if (opts.length) {
          opts.sort(function(a, b) { return b.id - a.id; });
          semesterId = String(opts[0].id);
        }
      }
      // 优先级 2 fallback: Vue/Nuxt 嵌入的 JSON 形态 (防御性)
      // 形如 var allSemesters = [...] 或 semesters = [...]
      if (!semesterId) {
        var jsonMatch = html.match(/(?:allSemesters|semesters)\s*[=:]\s*(\[[\s\S]*?\])\s*[;,]?/);
        if (jsonMatch) {
          try {
            var arr = JSON.parse(jsonMatch[1]);
            if (arr && arr.length) {
              // 优先 isCurrentSemester 标志
              var cur = null;
              for (var i = 0; i < arr.length; i++) {
                if (arr[i].isCurrentSemester === true || arr[i].isCurrent === true ||
                    arr[i].current === true || arr[i].status === 'CURRENT') { cur = arr[i]; break; }
              }
              var semObj = cur || arr[0];
              semesterId = String(semObj.id || semObj.semesterId || semObj.value || semObj.code);
            }
          } catch(e) {}
        }
      }
      // 优先级 3 fallback: <option selected value="X">
      if (!semesterId) {
        var sel = html.match(/<option[^>]*\bselected\b[^>]*\bvalue=["']?(\d+)["']?/i);
        if (sel) semesterId = sel[1];
      }
      // v1 兜底: abydym hardcoded DEFAULT_SEMESTER_ID = 112 (2024-2025-2 已知)
      // 不强行猜, 直接报"未取到学期列表"
      if (!semesterId) {
        window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false,
          err:'未取到学期列表 (allSemesters), 请在课表页面停留几秒后再点导入'}));
        return null;
      }
      return semesterId;
    })
    .then(function(semesterId){
      if (!semesterId) return null;
      // 2) GET print-data (含 weekday/weekIndexes/startUnit/endUnit 的真实课表数据)
      // followRedirects: 'follow' (服务端按 session 绑定学生, 不需要 studentId — 5 仓共识)
      var url = PREFIX + '/for-std/course-table/semester/' + encodeURIComponent(semesterId)
              + '/print-data?semesterId=' + encodeURIComponent(semesterId) + '&hasExperiment=false';
      return fetch(url, {credentials:'include', redirect:'follow'}).then(function(r){
        if (!r.ok) throw new Error('print-data 失败 HTTP ' + r.status);
        return r.text();
      });
    })
    .then(function(txt){
      if (!txt) return;
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:true, data:txt, periods:[]}));
    })
    .catch(function(e){
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(e)}));
    });
  } catch(err) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:String(err)}));
  }
})();
"""

/**
 * 北京交通大学教学支撑平台 (AA, aa.bjtu.edu.cn) — WebView 同源 fetch 双端点组合源。
 *
 * 协议证据 (SOP 跨仓验证 2026-09-09, POSITIVE 12 仓): HFDLYS/BJTUselfService (MIT) +
 * wan300/bjtu_mis_Android (MIT) 双仓独立证实:
 *   GET /course_selection/courseselect/stuschedule/        本学期课表 (HTML 表格)
 *   GET /course_selection/courseselecttask/schedule/       选课任务课表 (全学期)
 * 两端点无学期/周次参数 — 服务端按会话决定; 登录失效时 302 到 /client/login/。
 *
 * 流程 (复用 __sleepyBridge.onWiseduResult 同一回调通道, payload 同为 {ok, data, periods}):
 *   1) host 校验 bjtu.edu.cn (schools.json url = https://aa.bjtu.edu.cn/, CAS 登录后
 *      落回 aa origin — 同源 fetch 直通; 落在 cas/mis 域时报"请先到 aa.bjtu.edu.cn")
 *   2) 并行 fetch 两端点 (credentials include)
 *   3) 双双命中登录页 (/client/login/ 且无 星期一 表头) → 报"登录已失效"
 *   4) 双双 <200 字符 → 报"课表页为空"
 *   5) 拼组合源 '<!--sleepy-bjtu-doc:stuschedule-->' + a + '<!--sleepy-bjtu-doc:schedule-->' + b
 *   6) 节次时间: DOMParser 解析行首格 `第N节 ... [HH:MM-HH:MM]` (页面自带, 3 仓实锚)
 *   7) __sleepyBridge.onWiseduResult({ok, data, periods})
 *
 * 跨语言 invariant (SOP 铁律 3): LABEL_RE / TIME_RE 的 JS 字符串字面量与
 * JwBjtuParser.PERIOD_LABEL_RE_SRC / PERIOD_TIME_RE_SRC 逐字符相等 —
 * JS 字符串字面量里的 \\s 在字符串求值后即 Kotlin 常量值里的 \s;
 * JwBjtuParserTest 按源码字面文本断言锁死。
 */
private const val BJTU_FETCH_JS = """
(function(){
  function finish(payload) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify(payload));
  }
  function err(msg) {
    finish({ok:false, err:msg});
  }
  try {
    if (location.hostname.indexOf('bjtu.edu.cn') < 0) {
      err('请先在北京交通大学教务页面 (aa.bjtu.edu.cn) 登录后再点导入');
      return;
    }
    var LABEL_RE = new RegExp('第\\s*(\\d+)\\s*节');
    var TIME_RE = new RegExp('(\\d{1,2}:\\d{2})\\s*[-–—~至]\\s*(\\d{1,2}:\\d{2})');
    function get(path) {
      return fetch(path, {credentials:'include'}).then(function(r){
        return r.text().then(function(body){ return {status: r.status, body: body}; });
      });
    }
    function isLoginExpired(ctx) {
      return (ctx.body || '').indexOf('/client/login/') >= 0 &&
             (ctx.body || '').indexOf('星期一') < 0;
    }
    function periodsFrom(html) {
      var periods = [];
      try {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var rows = doc.querySelectorAll('tr');
        var seen = {};
        for (var i = 0; i < rows.length; i++) {
          var cell = rows[i].querySelector('th,td');
          if (!cell) continue;
          var text = cell.textContent || '';
          var lm = text.match(LABEL_RE);
          if (!lm) continue;
          var node = parseInt(lm[1], 10);
          if (seen[node]) continue;
          seen[node] = true;
          var tm = text.match(TIME_RE);
          periods.push({node: node, start: tm ? tm[1] : '', end: tm ? tm[2] : ''});
        }
        periods.sort(function(a,b){ return a.node - b.node; });
      } catch(e) { periods = []; }
      return periods;
    }
    Promise.all([
      get('/course_selection/courseselect/stuschedule/'),
      get('/course_selection/courseselecttask/schedule/')
    ])
    .then(function(rs){
      var a = rs[0], b = rs[1];
      if (isLoginExpired(a) && isLoginExpired(b)) {
        err('登录已失效，请重新登录后再点导入');
        return;
      }
      if ((a.body || '').length < 200 && (b.body || '').length < 200) {
        err('课表页返回内容为空，请先在教务里打开课表页后再点导入');
        return;
      }
      var combined = '<!--sleepy-bjtu-doc:stuschedule-->' + (a.body || '') +
                     '<!--sleepy-bjtu-doc:schedule-->' + (b.body || '');
      var periods = periodsFrom(a.body || '');
      if (periods.length === 0) periods = periodsFrom(b.body || '');
      finish({ok:true, data:combined, periods:periods});
    })
    .catch(function(e){
      err('抓取失败: ' + String(e && e.message ? e.message : e) + '（登录会话可能已失效，请重新登录）');
    });
  } catch(e) {
    err(String(e && e.message ? e.message : e));
  }
})();
"""

/**
 * wisedu fetch 结果 JS 桥。@JavascriptInterface 回调跑在 WebView JS 线程，
 * post 到主线程后再回调 Compose，避免线程问题。
 */
private class WiseduBridge(private val onResult: (String) -> Unit) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onWiseduResult(json: String) {
        main.post { onResult(json) }
    }
}

/** 页面内网络 recorder 的批量结果桥。记录本身留在 JS，避免每个请求跨 bridge。 */
internal class DiagnosticNetworkBridge {
    @android.webkit.JavascriptInterface
    fun onNetworkRecord(json: String) {
        // The recorder is intentionally pull-based; this callback is a compatibility hook
        // for pages that choose to flush incrementally. The authoritative export uses pull.
        JwDiagnosticSession.recordJsNetwork(json)
    }
}

/** 导出诊断资源重取结果的 JS 桥；每次导出创建一次，避免跨页面串包。 */
internal class DiagnosticReplayBridge(private val onResult: (String) -> Unit) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onReplayResult(json: String) {
        main.post { onResult(json) }
    }
}

/**
 * SSL 豁免注册表: 仅对学校 URL 的注册域 (含其子域) 放行自签/私有 CA 证书 —
 * 部分高校教务确用私有 CA。除此之外的 SSL 错误一律 cancel (中间人防护)。
 */
internal object SslBypassRegistry {
    /** 取注册域: 无公共后缀库, 用启发式 — 取末两段 (xx.edu.cn 形态取末三段)。 */
    fun registrableDomain(host: String): String {
        val h = host.lowercase().trim().trimEnd('.')
        if (h.isEmpty()) return ""
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val secondLevel = parts[parts.size - 2]
        // 多段公共后缀 (edu.cn / edu.hk / ac.uk 等): 公共后缀 + 域名 = 末三段
        val tld = parts.last()
        if ((tld == "cn" || tld == "hk" || tld == "uk" || tld == "tw" || tld == "jp") &&
            secondLevel in setOf("edu", "ac", "gov", "org")
        ) {
            return parts.takeLast(3).joinToString(".")
        }
        return parts.takeLast(2).joinToString(".")
    }

    /** host 是否与 schoolHost 同注册域 (或为其子域)。 */
    fun isAllowed(host: String, schoolHost: String): Boolean {
        if (host.isBlank() || schoolHost.isBlank()) return false
        return registrableDomain(host) == registrableDomain(schoolHost)
    }
}

/**
 * T7 抓取 JS — DFS 递归遍历 frame+iframe, 跨域记 blocked, 输出 FrameSnapshotList JSON。
 *
 * 与 FrameSnapshotList.fromJson 的字段契约(改任一侧必须同步):
 *   {ok: true, url: location.href, depth: N, frames: [
 *     {name, src, depth, path:[], html, blocked}]}
 * blocked 非空 = 跨域或读取失败; html null = 同上。两者互斥。
 */
private const val CAPTURE_FRAMES_JS_TEMPLATE = """
(function(maxDepth){
  function snap(win, name, src, depth, path){
    var html = null, blocked = '';
    try { html = win.document.documentElement.outerHTML; }
    catch(e) {
      try { blocked = win.location.hostname || ''; } catch(_) { blocked = ''; }
      if (!blocked) { blocked = String(src || 'unknown'); }
    }
    return {name:(name===undefined||name==='')?null:name,
            src:(src===undefined||src==='')?null:src,
            depth:depth, path:path.slice(), html:html, blocked:blocked};
  }
  function walk(win, depth, path, out){
    if (depth > maxDepth) return;
    var d; try { d = win.document; } catch(e) { return; }
    if (depth === 0) { out.push(snap(win, '(top)', win.location.href, 0, [])); }
    var tags = ['frame','iframe'];
    for (var t = 0; t < tags.length; t++) {
      var els; try { els = d.getElementsByTagName(tags[t]); } catch(e) { els = []; }
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        var nm = el.name || el.id || (tags[t] + '_' + i);
        var p = path.concat([nm]);
        var cw = null;
        try { cw = el.contentWindow; } catch(e) { cw = null; }
        if (!cw) {
          out.push({name:nm, src:el.src||null, depth:depth+1, path:p, html:null,
                    blocked: el.src ? String(el.src) : 'no-contentWindow'});
          continue;
        }
        out.push(snap(cw, nm, el.src, depth+1, p));
        walk(cw, depth+1, p, out);
      }
    }
  }
  var out = [];
  walk(window, 0, [], out);
  return JSON.stringify({ok:true, url:location.href, depth:maxDepth, frames:out});
})
"""

/**
 * 排查包专用 — DOM 可点元素清单 (issue #45 现场报告反循)。
 * 失败弹窗触发点 evaluateJavascript(本 JS) — 报告学生当时看的页面。
 * 输出 JSON: {url, items:[{tag, id, name, type, text, onclick, href, disabled, hidden}], counts:{byTag:{}}}
 * 不跨 frame (与 CAPTURE_FRAMES_JS 配套, 后者在 frames/ 落 outerHTML)
 * 不动登录密码字段 — 1B 完全不脱敏,字段原文保留供排查 EID/UID 异常。
 */
const val DOM_INVENTORY_JS = """
(function(){
  function safe(t){return t==null?'':String(t);}
  function cls(el){return el && el.className ? String(el.className) : '';}
  function attrs(el){
    var o = {};
    try {
      var attrs = el.attributes || [];
      for (var i=0;i<attrs.length;i++){
        var a = attrs[i];
        if (a && a.specified !== false) o[a.name] = safe(a.value).slice(0,200);
      }
    } catch(e){}
    return o;
  }
  var CLICKABLE = ['a','button','input','select','textarea','label'];
  var items = [];
  var byTag = {};
  for (var i=0;i<CLICKABLE.length;i++){
    var tag = CLICKABLE[i];
    var nodes;
    try { nodes = document.getElementsByTagName(tag); } catch(e){ nodes = []; }
    for (var j=0;j<nodes.length;j++){
      var el = nodes[j];
      var rect = null;
      try { rect = el.getBoundingClientRect(); } catch(e){}
      if (rect && (rect.width===0 && rect.height===0)) continue;  // 跳过零尺寸隐藏控件
      var text = '';
      try { text = (el.innerText || el.textContent || el.value || '').replace(/\\s+/g,' ').trim().slice(0,120); } catch(e){}
      items.push({
        tag: tag,
        id: safe(el.id),
        name: safe(el.name),
        type: safe(el.type),
        text: text,
        cls: cls(el),
        href: el.getAttribute ? safe(el.getAttribute('href')) : '',
        onclick: el.getAttribute ? safe(el.getAttribute('onclick')) : '',
        disabled: !!el.disabled,
        hidden: !!el.hidden || safe(el.style && el.style.display)==='none',
        rect: rect ? {x:Math.round(rect.x),y:Math.round(rect.y),w:Math.round(rect.width),h:Math.round(rect.height)} : null,
        attrs: attrs(el)
      });
    }
    byTag[tag] = nodes.length;
  }
  return JSON.stringify({url:location.href, total:items.length, byTag:byTag, items:items});
})
"""

/**
 * 排查包专用 — Web Storage 全量 (2026-09-18 用户: 排查包信息量对齐桌面 collector 5-storage/)。
 * sessionStorage + localStorage 全键值, 1B 不脱敏 — 学号/token 明文恰是排查材料。
 * 不跨 frame (storage 按 origin 隔离, 当前文档 origin 即可)。
 */
const val STORAGE_JS = """
(function(){
  var out = {sessionStorage:{}, localStorage:{}, url:location.href};
  ['sessionStorage','localStorage'].forEach(function(sn){
    try {
      var st = window[sn]; var m = {};
      if (st) { for (var i=0;i<st.length;i++){ var k=st.key(i); try{ m[k]=String(st.getItem(k)); }catch(e){} } }
      out[sn] = m;
    } catch(e) {}
  });
  return JSON.stringify(out);
})
"""

/**
 * 排查包专用 — 页面链接/表单动作全集 (对标桌面 collector jsLinks/jsSelects)。
 * a[href]/iframe[src]/form[action] 绝对化 → 适配者看导航面; select 枚举 → 学期码全集。
 */
const val LINKS_JS = """
(function(){
  function abs(h){ try { return new URL(h, location.href).href; } catch(e){ return h; } }
  var seen={}, links=[];
  var els=document.querySelectorAll('a[href],iframe[src],form[action]');
  for (var i=0;i<els.length;i++){
    var h=els[i].getAttribute('href')||els[i].getAttribute('src')||els[i].getAttribute('action')||'';
    if ((h.indexOf('http')===0||h.charAt(0)==='/') && !seen[h]) { seen[h]=1; links.push(abs(h)); }
  }
  var selects=[];
  var ss=document.querySelectorAll('select');
  for (var s=0;s<ss.length;s++){
    var opts=[];
    var os=ss[s].querySelectorAll('option');
    for (var o=0;o<os.length;o++){
      opts.push({v: os[o].getAttribute('value')||'', t: (os[o].textContent||'').trim().slice(0,60)});
    }
    if (opts.length) selects.push({sel:s, name:ss[s].getAttribute('name')||'', id:ss[s].id||'', opts:opts});
  }
  return JSON.stringify({url:location.href, links:links, selects:selects});
})
"""

/**
 * Passive runtime network recorder. It preserves native fetch/XHR behavior and records only
 * what the page itself can observe. Bodies are intentionally retained unredacted for diagnosis.
 */
internal const val DIAGNOSTIC_NETWORK_INSTALL_JS = """
(function(){
  if (window.__sleepyDiagNetworkInstalled) return;
  window.__sleepyDiagNetworkInstalled = true;
  var rows = window.__sleepyDiagNetworkRecords = [];
  var MAX_ROWS = 500, MAX_BODY = 512 * 1024;
  function trim(v){ v = v == null ? '' : String(v); return v.length > MAX_BODY ? v.slice(0, MAX_BODY) + '\\n[truncated]' : v; }
  function headers(h){ var o={}; try { h && h.forEach(function(v,k){o[k]=v;}); } catch(e) {} return o; }
  function push(x){ x.ts = Date.now(); rows.push(x); if(rows.length > MAX_ROWS) rows.shift(); }
  function instrument(win){
    try {
      if (!win || win.__sleepyDiagNetworkFramed) return;
      win.__sleepyDiagNetworkFramed = true;
      var nf = win.fetch;
      if (nf) win.fetch = function(input, init){
        var reqUrl = typeof input === 'string' ? input : (input && input.url) || '';
        var method = (init && init.method) || (input && input.method) || 'GET';
        var reqBody = init && init.body != null ? String(init.body) : '';
        var reqHeaders = {};
        try { if (input && input.headers) reqHeaders = headers(input.headers); } catch(e) {}
        try { if (init && init.headers) { var ih = headers(init.headers); for (var hk in ih) reqHeaders[hk]=ih[hk]; } } catch(e) {}
        return nf.apply(this, arguments).then(function(r){
          var x={source:'fetch',url:r.url || reqUrl,finalUrl:r.url || reqUrl,method:String(method).toUpperCase(),requestBody:trim(reqBody),requestHeaders:reqHeaders,status:r.status,ok:r.ok,responseHeaders:headers(r.headers),frame:win===window?'top':'frame'};
          try { x.redirected=!!r.redirected; } catch(e) {}
          return r.clone().text().then(function(b){ x.responseBody=trim(b); push(x); return r; },function(e){x.error=String(e);push(x);return r;});
        }, function(e){ push({source:'fetch',url:reqUrl,method:String(method).toUpperCase(),requestBody:trim(reqBody),status:0,error:String(e),frame:win===window?'top':'frame'}); throw e; });
      };
      var NX = win.XMLHttpRequest;
      if (NX) {
        var open = NX.prototype.open, send = NX.prototype.send, setHeader = NX.prototype.setRequestHeader;
        NX.prototype.open = function(method,url){ this.__sleepyDiag={method:String(method||'GET').toUpperCase(),url:String(url||''),headers:{}}; return open.apply(this,arguments); };
        NX.prototype.setRequestHeader = function(k,v){ if(this.__sleepyDiag) this.__sleepyDiag.headers[k]=String(v); return setHeader.apply(this,arguments); };
        NX.prototype.send = function(body){ var xhr=this, meta=this.__sleepyDiag || {method:'GET',url:''}; meta.requestBody=trim(body == null ? '' : body); function done(){ var x={source:'xhr',url:meta.url,finalUrl:meta.url,method:meta.method,requestBody:meta.requestBody,status:0,requestHeaders:meta.headers,frame:win===window?'top':'frame'}; try{x.status=xhr.status;x.responseHeaders=xhr.getAllResponseHeaders();x.responseBody=trim(typeof xhr.responseText==='string'?xhr.responseText:'');}catch(e){x.error=String(e);} push(x); } this.addEventListener('loadend',done,{once:true}); return send.apply(this,arguments); };
      }
    } catch(e) {}
  }
  instrument(window);
  // 同源 iframe 立即 instrument (XJU PageFrame / HEBZYHJ qz 等 frameset 教务)
  try { for (var i=0;i<window.frames.length;i++) instrument(window.frames[i]); } catch(e) {}
  // 迟加载的子帧 — setInterval 兜底, 适配 history API 重置的 frame
  setInterval(function(){
    try { for (var j=0;j<window.frames.length;j++) instrument(window.frames[j]); } catch(e) {}
  }, 1500);
})();
"""

internal const val DIAGNOSTIC_NETWORK_SNAPSHOT_JS = """
(function(){ try { return JSON.stringify({live:window.__sleepyDiagNetworkRecords || []}); } catch(e) { return '{\"live\":[]}'; } })()
"""

internal const val DIAGNOSTIC_NETWORK_EXPORT_JS = """
(function(){
  var live = window.__sleepyDiagNetworkRecords || [];
  var same = function(u){ try{return new URL(u,location.href).origin===location.origin;}catch(e){return false;} };
  var replaceParam = function(text,name,value){
    var re = new RegExp('([?&]' + name + '=|(^|&)'+name+'=)([^&]*)','i');
    return re.test(text) ? text.replace(re,function(_,p){return p+encodeURIComponent(value);}) : text;
  };
  var weekOf = function(r){
    var s=(r.url||'')+'\n'+(r.requestBody||'');
    var m=s.match(/(?:^|[?&\s])((?:week|zc|weekIndex|zhouci|xq))=([^&\s]*)/i);
    return m ? {name:m[1],value:m[2]} : null;
  };
  var call = function(url, method, body, headers){
    var opts={method:method,credentials:'include',cache:'no-store'};
    if(method==='POST'){opts.body=body||''; opts.headers=headers||{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'};}
    return fetch(url,opts).then(function(r){return r.text().then(function(b){return {url:r.url||url,method:method,status:r.status,body:b,headers:(function(){var o={};try{r.headers.forEach(function(v,k){o[k]=v;});}catch(e){}return o;})()};});}).catch(function(e){return {url:url,method:method,status:0,error:String(e)};});
  };
  var replay = [];
  var jobs=[];
  for(var i=0;i<live.length && jobs.length<40;i++){
    var r=live[i]; if(!r||!same(r.url)||!r.url||r.source==='resource') continue;
    if(String(r.method).toUpperCase()==='POST') jobs.push(call(r.url,'POST',r.requestBody||'',r.requestHeaders||{}));
  }
  Promise.all(jobs).then(function(rs){ replay=rs; flushWeeks(replay); },function(){ flushWeeks(replay); });

  var weekState = [];
  function flushWeeks(replay){
    var seen={};
    for(var i=0;i<live.length && Object.keys(seen).length<10;i++){
      var r=live[i], w=weekOf(r); if(!r||!w||!same(r.url)) continue;
      var key=r.method+' '+r.url+' '+w.name; if(seen[key]) continue; seen[key]=1;
      weekState.push({key:key,r:r,name:w.name});
    }
    // 周重放必须串行(对齐桌面 collector): 校务服务器对 10 API × 25 周并发 = 风控/封禁风险。
    // 链式 reduce 逐个请求, 单项失败不影响后续。
    var tasks=[];
    weekState.forEach(function(ws){
      for(var n=1;n<=25;n++){
        var r=ws.r, method=String(r.method).toUpperCase();
        var u=r.url, b=r.requestBody||'';
        if(method==='POST') b=replaceParam(b,ws.name,String(n)); else u=replaceParam(u,ws.name,String(n));
        (function(week, weekName, weekMethod, weekUrl, weekBody){
          tasks.push(function(){
            return call(weekUrl,weekMethod,weekBody,ws.r.requestHeaders||{}).then(function(res){
              res.week=week; res.name=weekName; res.url=res.url || weekUrl; return res;
            }).catch(function(e){return {week:week,name:weekName,method:weekMethod,url:weekUrl,status:0,error:String(e)};});
          });
        })(n, ws.name, method, u, b);
      }
    });
    tasks.reduce(function(p,fn){
      return p.then(function(rs){ return fn().then(function(r){ rs.push(r); return rs; }); });
    }, Promise.resolve([])).then(function(rs){
      try { __sleepyDiagBridge.onReplayResult(JSON.stringify({live:live,replay:replay,weeks:rs})); } catch(e) {}
    }, function(){
      try { __sleepyDiagBridge.onReplayResult(JSON.stringify({live:live,replay:replay,weeks:[]})); } catch(e) {}
    });
  }
})()
"""

/**
 * 排查包专用 — 重取页面已经加载过的同源文本资源。
 * 只重放 GET, 使用浏览器缓存和当前 Cookie; 不重放 POST/表单写操作。
 * 每项保留 url/status/mime/body 或 error, 失败项不能被伪造为成功响应。
 * evaluateJavascript 不等待 Promise, 所以最终 JSON 经一次性 JS bridge 回传。
 */
const val RESOURCE_REPLAY_JS = """
(function(){
  var entries=[];
  var seen={};
  var resources=[];
  try { resources=performance.getEntriesByType('resource') || []; } catch(e) {}
  resources.push({name:location.href, initiatorType:'document'});
  function sameOrigin(u){ try { return new URL(u,location.href).origin===location.origin; } catch(e){ return false; } }
  function abs(u){ try { return new URL(u,location.href).href; } catch(e){ return String(u||''); } }
  function one(u){
    u=abs(u);
    if (!u || seen[u] || !sameOrigin(u)) return Promise.resolve();
    seen[u]=1;
    var controller = window.AbortController ? new AbortController() : null;
    var timer = controller ? setTimeout(function(){controller.abort();},5000) : null;
    var opts={method:'GET',credentials:'include',cache:'force-cache'};
    if (controller) opts.signal=controller.signal;
    return fetch(u,opts)
      .then(function(r){
        return r.text().then(function(body){
          entries.push({url:u,status:r.status,mime:r.headers.get('content-type')||'',body:body});
        });
      })
      .catch(function(e){ entries.push({url:u,status:0,mime:'',error:String(e)}); })
      .then(function(){ if (timer) clearTimeout(timer); });
  }
  var jobs=[];
  for (var i=0;i<resources.length;i++) {
    var u=resources[i] && resources[i].name;
    if (u) jobs.push(one(u));
  }
  function done(){
    try { __sleepyDiagBridge.onReplayResult(JSON.stringify(entries)); } catch(e) {}
  }
  var all = Promise.all(jobs);
  var totalTimer = setTimeout(function(){ done(); },15000);
  all.then(function(){ clearTimeout(totalTimer); done(); },function(){ clearTimeout(totalTimer); done(); });
})()
"""

/** 单次抓取: evaluateJavascript → FrameSnapshot.fromJson → selectBestFrame。回调已在主线程。 */
private fun captureOnce(wv: WebView, onResult: (FrameCaptureResult) -> Unit) {
    wv.evaluateJavascript(
        CAPTURE_FRAMES_JS_TEMPLATE + "(8);",
        ValueCallback<String> { raw ->
            if (raw.isNullOrEmpty() || raw == "null") {
                onResult(FrameCaptureResult(null, "", emptyList(),
                    status = FrameCaptureStatus.WRONG_PAGE,
                    diagnosticHint = "WebView 未返回响应"))
                return@ValueCallback
            }
            try {
                // evaluateJavascript 回传的是 JSON 字符串字面量(带引号+转义), 先解一层
                val unquoted = if (raw.startsWith("\""))
                    org.json.JSONTokener(raw).nextValue().toString()
                else raw
                onResult(FrameTraversalTree.selectBestFrame(FrameSnapshotList.fromJson(unquoted)))
            } catch (e: Exception) {
                Log.e("JwWebView", "parse frame snapshot failed", e)
                onResult(FrameCaptureResult(null, "", emptyList(),
                    status = FrameCaptureStatus.UNKNOWN,
                    diagnosticHint = "解析抓取结果失败: ${e.message}"))
            }
        })
}

/** 带重试的抓取: 空壳 iframe / 延迟渲染容器按 1500ms 间隔重试, 至多 3 次。 */
private fun captureWithRetry(wv: WebView, retryCount: Int, onResult: (FrameCaptureResult) -> Unit) {
    captureOnce(wv) { first ->
        // ① 空壳 iframe: 先于 Readiness 判定(about:blank 壳不属于"容器空")
        val shell = first.html.isBlank() || RenderReadinessChecker.checkBlankShell(first.html)
        // ② readiness 只看选中 frame 的 html
        val readiness = when {
            retryCount >= 3 -> RenderReadinessChecker.Readiness.GIVE_UP
            shell -> RenderReadinessChecker.Readiness.DELAY   // 空壳 → 等导航
            else -> RenderReadinessChecker.check(first.html, retryCount)
        }
        when (readiness) {
            RenderReadinessChecker.Readiness.READY -> onResult(first)
            RenderReadinessChecker.Readiness.DELAY -> {
                Log.d("JwWebView", "frame not ready, retry #${retryCount + 1} after 1500ms")
                wv.postDelayed({ captureWithRetry(wv, retryCount + 1, onResult) }, 1500L)
            }
            RenderReadinessChecker.Readiness.GIVE_UP -> onResult(first.copy(
                status = when {
                    first.status == FrameCaptureStatus.SESSION_EXPIRED -> FrameCaptureStatus.SESSION_EXPIRED
                    first.html.isBlank() || RenderReadinessChecker.checkBlankShell(first.html)
                        -> FrameCaptureStatus.IFRAME_NAV_PENDING
                    else -> FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY
                },
                retryCount = retryCount,
                diagnosticHint = when {
                    first.status == FrameCaptureStatus.SESSION_EXPIRED -> first.diagnosticHint
                    first.html.isBlank() -> "课表框架尚未开始加载(重试 $retryCount 次), 请等页面完全显示课表后再点导入"
                    else -> "课表容器存在但内容未填充(重试 $retryCount 次), 请等待页面完全加载后再点导入"
                }
            ))
        }
    }
}
