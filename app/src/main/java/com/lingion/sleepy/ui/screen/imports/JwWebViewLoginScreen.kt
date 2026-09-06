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
    onHtmlCaptured: (html: String, school: JwSchoolInfo, periods: List<Triple<Int, String, String>>) -> Unit,
    onCaptureError: (status: FrameCaptureStatus, hint: String) -> Unit,
    onBack: () -> Unit,
    viewModel: JwImportViewModel = viewModel()
) {
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var progress by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val logToken = remember { java.util.concurrent.atomic.AtomicLong(0) }
    val webviewNotReadyMsg = stringResource(R.string.jw_webview_not_ready)
    val fetchingMsg = stringResource(R.string.jw_fetching)
    val fetchFailedNoResponseMsg = stringResource(R.string.jw_fetch_failed_no_response)
    val fetchFormatErrorMsg = stringResource(R.string.jw_fetch_format_error)
    val fetchFailedFmt = stringResource(R.string.jw_fetch_failed)
    val pageNotLoadedMsg = stringResource(R.string.jw_page_not_loaded)
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
                    scope.launch { snackbar.showSnackbar(fetchFailedNoResponseMsg) }
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
                    onHtmlCaptured(data, school, periods)
                }
            } else {
                val err = obj.optString("err", "")
                scope.launch { snackbar.showSnackbar(fetchFailedFmt.format(err.ifBlank { pageNotLoadedMsg })) }
            }
        } catch (e: Exception) {
            Log.e("JwWebView", "parse wisedu result failed", e)
            scope.launch { snackbar.showSnackbar(fetchFormatErrorMsg) }
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
                scope.launch { snackbar.showSnackbar(fetchTimeoutMsg) }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
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
                        scope.launch { snackbar.showSnackbar(webviewNotReadyMsg) }
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
                    // CQU（重庆大学门户）：同走 JS 桥 fetch 四个 REST API，Bearer token 取自 localStorage
                    if (school.type == JwProtocol.TYPE_CQU) {
                        evaluateFetchWithTimeout(wv, CQU_FETCH_JS)
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
                    // 合工大 EAMS5: 三段 fetch (for-std/course-table → for-std/lessons → POST schedule-table/datum)
                    // 用户已在 WebView 走完 CAS 登录并落到教务域。supwisdom 新版部署
                    // 前缀分两形态：合工大 /eams5-student、安大/矿大北京 /student —
                    // 按学校 URL 推断后替换模板占位符。
                    if (school.type == JwProtocol.TYPE_EAMS5) {
                        val prefix = eams5PathPrefixFor(school.url)
                        evaluateFetchWithTimeout(wv, EAMS5_FETCH_JS.replace(EAMS5_PREFIX_PLACEHOLDER, prefix))
                        return@CaptureBar
                    }
                    // T5: 新版正方 — WebView 内 fetch kbList JSON
                    // 路径指纹: school.type 显式 zf_new, 或 URL 含 /jwglxt/、/kbcx/ (广东医科等新版), 或 WebVPN /http/<hex>/ 重写形态
                    val currentUrl = wv.url ?: ""
                    val isZfNew = school.type == JwProtocol.TYPE_ZF_NEW ||
                        currentUrl.contains("/jwglxt/", ignoreCase = true) ||
                        currentUrl.contains("/kbcx/", ignoreCase = true) ||
                        Regex("/http/[0-9a-f]{4,8}/").containsMatchIn(currentUrl)
                    if (isZfNew) {
                        wv.evaluateJavascript(ZF_NEW_FETCH_JS, null)
                        return@CaptureBar
                    }
                    // T7: DFS frame 抓取 + ready 重试, 决策在 JVM 层(可测可日志)
                    captureWithRetry(wv, 0) { r ->
                        Log.d("JwWebView", "captured frame=${r.selectedFramePath} anchors=${r.matchedAnchors} status=${r.status}")
                        when (r.status) {
                            FrameCaptureStatus.OK, FrameCaptureStatus.EMPTY_SEMESTER ->
                                onHtmlCaptured(r.html, school, emptyList())   // 0 课交给 Activity 按空学期文案报
                            FrameCaptureStatus.SESSION_EXPIRED,
                            FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED,
                            FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY,
                            FrameCaptureStatus.IFRAME_NAV_PENDING,
                            FrameCaptureStatus.WRONG_PAGE,
                            FrameCaptureStatus.UNKNOWN ->
                                onCaptureError(r.status, r.diagnosticHint)    // 不走 onHtmlCaptured, 避免伪"0 课"
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
                onProgressChange = { p -> progress = p },
                onWebViewCreated = { wv -> webViewRef = wv },
                onHtmlCaptured = { html -> onHtmlCaptured(html, school, emptyList()) },
                onWiseduResult = handleWiseduResult
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun JwWebView(
    url: String,
    onProgressChange: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onHtmlCaptured: (String) -> Unit,
    onWiseduResult: (String) -> Unit = {}
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val schoolHost = url.toUri().host.orEmpty()
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // wisedu (金智) 协议：注册 JS 桥，async fetch 课表 JSON 完成后回调
                addJavascriptInterface(WiseduBridge(onWiseduResult), "__sleepyBridge")
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
                }
                // 正常 WebView 配置
                settings.databaseEnabled = true
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChange(newProgress)
                    }
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                        Log.d("JwWebView", "console[${msg?.messageLevel()}]: ${msg?.message()}")
                        return true
                    }
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView,
                        handler: android.webkit.SslErrorHandler,
                        error: android.net.http.SslError
                    ) {
                        // 中间人防护: 不再无条件 proceed (曾放行任意自签证书劫持课表账号),
                        // 改为按主域名白名单豁免 — 部分高校教务确用自签/私有 CA, 仅对
                        // 学校 URL 的注册域放行, 其余一律 cancel。
                        val host = view.url?.toUri()?.host.orEmpty()
                        val allowed = SslBypassRegistry.isAllowed(host, schoolHost)
                        if (allowed) handler.proceed() else handler.cancel()
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d("JwWebView", "onPageFinished url=$url")
                    }
                }
                loadUrl(url)
                onWebViewCreated(this)
            }
        }
    )
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
 *   2) POST /eams5-student/ws/schedule-table/datum
 *      body: {"lessonIds":[], "studentId":<学号>, "weekIndex":""}
 *      resp: schedule-table/datum JSON 全文
 *   3) 通过 __sleepyBridge.onWiseduResult({ok, data}) 回调
 *
 * 简化说明（v1 不完美但可用）：
 *   - 上游协议第 2 段要先调 /for-std/course-table/get-data?bizTypeId=23 拿 lessonIds[];
 *     v1 简化: 直接 POST datum, lessonIds 数组置空, 上游通常会用空数组返全量
 *   - 后续 v2: 增加 get-data 段拿 lessonIds, 与上游 Chiu-xaH/HFUT-Schedule 对齐
 *
 * 外部佐证：Chiu-xaH/HFUT-Schedule JxglstuService.kt + JxglstuRepository.kt
 * (parseStudentId / parseBizTypeId), BoynChan/HfutOpenApi CourseCrawler.java。
 * 2026-09 用户反馈原正则只匹配 quoted-digit 形态, 学号嵌入 supwisdom 对象字面量
 * (studentId: '...') 时漏, 本版放宽正则 + 加 r.url 检测登录失效。
 */
private const val EAMS5_FETCH_JS = """
(function(){
  try {
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
      if (!m) return null;
      return m[1];
    })
    .then(function(studentId){
      if (!studentId) {
        window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'未取到 studentId,请在 course-table 页面停留后再试'}));
        return null;
      }
      // 2) POST schedule-table/datum (lessonIds 空数组; v1 简化,上游多返全量)
      return fetch(PREFIX + '/ws/schedule-table/datum', {
        method:'POST',
        credentials:'include',
        headers:{'Content-Type':'application/json'},
        body: JSON.stringify({lessonIds:[], studentId:studentId, weekIndex:''})
      }).then(function(r){
        if (!r.ok) throw new Error('POST schedule-table/datum 失败 HTTP ' + r.status);
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

/**
 * SSL 豁免注册表: 仅对学校 URL 的注册域 (含其子域) 放行自签/私有 CA 证书 —
 * 部分高校教务确用私有 CA。除此之外的 SSL 错误一律 cancel (中间人防护)。
 */
private object SslBypassRegistry {
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
