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
import androidx.compose.material3.Snackbar
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

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
    onBack: () -> Unit,
    viewModel: JwImportViewModel = viewModel()
) {
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var progress by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val webviewNotReadyMsg = stringResource(R.string.jw_webview_not_ready)
    val fetchingMsg = stringResource(R.string.jw_fetching)
    val fetchFailedNoResponseMsg = stringResource(R.string.jw_fetch_failed_no_response)
    val fetchFormatErrorMsg = stringResource(R.string.jw_fetch_format_error)
    val fetchFailedFmt = stringResource(R.string.jw_fetch_failed)
    val pageNotLoadedMsg = stringResource(R.string.jw_page_not_loaded)

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
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = colors.surfaceContainer)
            }
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
                        wv.evaluateJavascript(WISEDU_FETCH_JS, null)
                        return@CaptureBar
                    }
                    // 用 evaluateJavascript（API 19+）同步回调拿 HTML，不依赖 JS 桥跨域问题
                    val js = """
                        (function() {
                            try {
                                var ifrs = document.getElementsByTagName('iframe');
                                var iframeContent = '';
                                for (var i = 0; i < ifrs.length; i++) {
                                    try { iframeContent += ifrs[i].contentDocument.documentElement.outerHTML; } catch(e) {}
                                }
                                var frs = document.getElementsByTagName('frame');
                                var frameContent = '';
                                for (var i = 0; i < frs.length; i++) {
                                    try { frameContent += frs[i].contentDocument.documentElement.outerHTML; } catch(e) {}
                                }
                                var html = (document.documentElement && document.documentElement.outerHTML) || '';
                                JSON.stringify({ok:true, url:location.href, len:html.length+iframeContent.length+frameContent.length, html:html+iframeContent+frameContent});
                            } catch(err) {
                                JSON.stringify({ok:false, err:String(err)});
                            }
                        })();
                    """.trimIndent()
                    wv.evaluateJavascript(js, ValueCallback<String> { raw ->
                        Log.d("JwWebView", "evaluateJavascript returned len=${raw?.length ?: 0}")
                        if (raw.isNullOrEmpty() || raw == "null") {
                            scope.launch { snackbar.showSnackbar(fetchFailedNoResponseMsg) }
                            return@ValueCallback
                        }
                        // raw 是 JSON 字符串（含转义），先解一层
                        val unwrapped = try {
                            org.json.JSONObject(raw).optString("html", "")
                        } catch (e: Exception) {
                            Log.e("JwWebView", "parse capture JSON failed", e)
                            scope.launch { snackbar.showSnackbar(fetchFormatErrorMsg) }
                            return@ValueCallback
                        }
                        val ok = try {
                            org.json.JSONObject(raw).optBoolean("ok", false)
                        } catch (e: Exception) { false }
                        if (!ok || unwrapped.isBlank()) {
                            val err = try { org.json.JSONObject(raw).optString("err", "") } catch (e: Exception) { "" }
                            scope.launch { snackbar.showSnackbar(fetchFailedFmt.format(err.ifBlank { pageNotLoadedMsg })) }
                            return@ValueCallback
                        }
                        Log.d("JwWebView", "captured html len=${unwrapped.length}")
                        onHtmlCaptured(unwrapped, school, emptyList())
                    })
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
                        handler.proceed()
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
            shape = RoundedCornerShape(24.dp),
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
 * 必须在 jwgl.hrbeu.edu.cn 域执行（同域 fetch 自动带 _WEU cookie）。
 */
private const val WISEDU_FETCH_JS = """
(function(){
  try {
    if (location.hostname.indexOf('jwgl.hrbeu.edu.cn') < 0) {
      window.__sleepyBridge.onWiseduResult(JSON.stringify({ok:false, err:'请先登录并进入教务系统(jwgl.hrbeu.edu.cn)再点导入'}));
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
      var xnxq = '';
      try { xnxq = d.datas.dqxnxq.rows[0].DM; } catch(e) {}
      if (!xnxq) throw new Error('无法获取当前学期');
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

