package com.lingion.sleepy.ui.screen.imports

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onHtmlCaptured: (html: String, school: JwSchoolInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: JwImportViewModel = viewModel()
) {
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
                enabled = !loading,
                onCapture = {
                    val wv = webViewRef
                    if (wv == null) {
                        scope.launch { snackbar.showSnackbar("WebView 未就绪") }
                        return@CaptureBar
                    }
                    // wakeup 风格：loadUrl javascript: 把 JS 注入到当前页面，JS 通过 local_obj 回调 HTML
                    val js = """
                        javascript:(function() {
                            var ifrs = document.getElementsByTagName('iframe');
                            var iframeContent = '';
                            for (var i = 0; i < ifrs.length; i++) {
                                try {
                                    iframeContent = iframeContent + ifrs[i].contentDocument.body.parentElement.outerHTML;
                                } catch (e) {}
                            }
                            var frs = document.getElementsByTagName('frame');
                            var frameContent = '';
                            for (var i = 0; i < frs.length; i++) {
                                try {
                                    frameContent = frameContent + frs[i].contentDocument.body.parentElement.outerHTML;
                                } catch (e) {}
                            }
                            var html = document.getElementsByTagName('html')[0].outerHTML + iframeContent + frameContent;
                            window.local_obj.showSource(html);
                        })();
                    """.trimIndent()
                    wv.loadUrl(js)
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
                onProgressChange = { p ->
                    progress = p
                    loading = p < 100
                },
                onWebViewCreated = { wv -> webViewRef = wv },
                onHtmlCaptured = { html -> onHtmlCaptured(html, school) }
            )

            if (loading) {
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
    onHtmlCaptured: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView,
                        handler: android.webkit.SslErrorHandler,
                        error: android.net.http.SslError
                    ) {
                        handler.proceed()
                    }
                }
                // 关键：addJavascriptInterface 暴露给 JS（wakeup 6 年方案）
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun showSource(html: String) {
                            onHtmlCaptured(html)
                        }
                    },
                    "local_obj"
                )
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
                text = "完成登录后",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Text(
                text = "导航到「个人课表」页，再点右侧按钮",
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
            Text("导入此页", color = colors.onPrimary)
        }
    }
}

