# WebView 环境与失败诊断层 — 设计文档

**日期**: 2026-09-26
**子项目**: 1 / 6
**分支**: `feat/jw-webview-diagnostics`
**状态**: 待用户审阅

---

## 1. 问题陈述

当前 Sleepy 已有 `JwDiagnosticSession`，记录请求、console、下载、JS 网络。但缺少用户反馈中 Edge 能打开而 Sleepy 打不开时最关键的证据：

| 缺失证据 | 后果 |
|---|---|
| WebView Provider 名称与版本 | 不知是否为厂商定制内核 |
| Chromium 版本 | 不知是否支持目标页面所需特性 |
| 实际生效的 UA | 不知是否命中桌面/移动分支 |
| 最终 URL | 不知是否被重定向到登录页/错误页 |
| 重定向链 | 不知 302 断在哪一跳 |
| 失败资源 | 不知哪个 CSS/JS/iframe 加载失败 |
| Storage / Service Worker 状态 | 不知 SPA 为何白屏 |
| 新窗口请求 | 不知 `target="_blank"` 是否丢页 |

**用户原话**：「Edge 浏览器能打开，但是 Via 打不开」「用它用了 h 浏览器，能打开它的教务链接，但是它用 via。这种安卓原生 WebView 反而无法渲染」

---

## 2. 目标

1. 记录 WebView Provider / Chromium 版本 / Android SDK / 设备型号
2. 记录实际生效的 UA（读 `settings.userAgentString`，非配置值）
3. 记录初始 URL → 最终 URL 的完整重定向链
4. 记录失败资源（`onReceivedError` 主文档 + 子资源）
5. 记录 Storage（localStorage / sessionStorage / IndexedDB / Service Worker）可用性与条目数
6. 记录 `window.open` / `target="_blank"` 请求
7. 记录 SSL 错误决策（放行/拒绝 + host）
8. 全部字段脱敏，不记录密码、完整 Cookie、Authorization、完整学号
9. 输出 `environment.txt` + `navigation.txt` + `failures.txt` 到现有排查包
10. 纯 JVM 可测，不依赖设备

---

## 3. 非目标

- 不改变 WebView 加载逻辑
- 不新增 WebChromeClient 回调（属子项目 2）
- 不新增 Profile 切换（属子项目 3）
- 不改 frame 采集与解析器
- 不改 interceptor
- 不自动上传诊断数据
- 不放宽 SSL / 混合内容策略

---

## 4. 架构

```
┌─────────────────────────────────────────────────────────┐
│ JwDiagnosticSession (现有, 线程安全 ring buffer)         │
│  requests / consoles / downloads / jsNetwork            │
└─────────────────────────────────────────────────────────┘
                          ▲
                          │ record*() 调用
┌─────────────────────────────────────────────────────────┐
│ JwWebViewEnvironment  [新增, 纯 JVM 数据类]              │
│  provider / chromium / sdk / model / ua / actualUa       │
│  initialUrl / finalUrl / redirectChain                  │
│  storageFlags / serviceWorkerState                      │
│  windowOpenRequests / sslDecisions                      │
│  failedResources                                        │
└─────────────────────────────────────────────────────────┘
                          ▲
                          │ 装配
┌─────────────────────────────────────────────────────────┐
│ JwWebViewEnvironmentProbe  [新增, Android 侧收集器]      │
│  WebViewCompat.getCurrentWebViewPackage()               │
│  WebViewFeature / settings.userAgentString              │
│  Build.VERSION.SDK_INT / Build.MANUFACTURER              │
└─────────────────────────────────────────────────────────┘
                          ▲
                          │ 导出
┌─────────────────────────────────────────────────────────┐
│ JwDiagnosticExporter  [新增, 纯 JVM 格式化]              │
│  exportEnvironment() / exportNavigation()                │
│  exportFailures()                                       │
└─────────────────────────────────────────────────────────┘
```

**分层原则**（沿用现有 `JwWebViewFrameCapture.kt` 纯 JVM 约定）：

- `JwWebViewEnvironment` — 纯数据类，无 `android.*` import，可 JUnit4 直接构造
- `JwDiagnosticExporter` — 纯格式化，字符串拼接
- Android 侧收集在现有 `JwWebViewLoginScreen.kt` 的 `WebView` factory 闭包内完成
- 线程安全：所有 `record*` 用 `ConcurrentLinkedDeque`

---

## 5. 数据模型

```kotlin
data class JwWebViewEnvironment(
    val providerPackage: String?,      // "com.android.webview" / "com.google.android.webview"
    val providerVersionName: String?,   // "120.0.6099.144"
    val chromiumMajor: Int?,            // 120, 无法解析则 null
    val androidSdkInt: Int,             // 33
    val androidRelease: String?,        // "13"
    val manufacturer: String?,          // "Xiaomi"
    val model: String?,                 // "M2101K6G"
    val configuredUserAgent: String?,   // 应用配置的 UA
    val actualUserAgent: String?,       // WebView 实际返回的 UA
    val initialUrl: String?,
    val finalUrl: String?,
    val redirectChain: List<String>,    // 有序: [初始, 302目标1, ...]
    val storageEnabled: Boolean,        // localStorage 可用
    val storageEntryCount: Int?,        // localStorage 条目数
    val sessionStorageEntryCount: Int?,
    val indexedDbAvailable: Boolean?,
    val serviceWorkerState: String?,    // "unsupported" / "registered" / "unknown"
    val windowOpenRequests: List<String>,
    val sslDecisions: List<String>,     // "proceed|host" 或 "cancel|host"
    val failedResources: List<String>,  // "ERROR_CODE|url|mime"
)
```

所有字段为可空或带默认值，保证旧版本 dump 不崩溃。

---

## 6. 采集点（Android 侧）

| 数据 | 采集位置 | 回调线程 |
|---|---|---|
| provider / chromium | `JwWebViewEnvironmentProbe.capture(webView)` factory 期 | 主线程 |
| sdk / model / manufacturer | `Build.VERSION` / `Build.MANUFACTURER` | 任意 |
| actual UA | `webView.settings.userAgentString` factory 期 | 主线程 |
| initial URL | `loadUrl(lastUrl)` 前 | 主线程 |
| final URL | `onPageFinished(url)` | 主线程 |
| redirect chain | `onPageStarted` 追加，去重 | 主线程 |
| failed resources | `onReceivedError` | 主线程（子项目 2 会扩展） |
| storage 状态 | `onPageFinished` 后 `evaluateJavascript` 探测 | 主线程 → 桥回调 |
| window.open | `WebChromeClient.onCreateWindow`（子项目 2） | 主线程 |
| SSL 决策 | `JwWebViewClientBuilder` 已有逻辑，加 record 调用 | 后台线程 |

**线程安全约束（现有架构不变量，必须保持）**：

> `shouldInterceptRequest` 在 Chromium 后台线程执行，**禁止**访问 `view.settings` / `view.url` / `view.cookieManager`。所有 `view.*` 读取必须在 factory 期一次性捕获并缓存。

SSL 决策已使用 `error.url`（`SslError` 字段）而非 `view.url`，符合此约束。诊断层只增加 `record`，不新增 `view.*` 读取。

---

## 7. 脱敏规则

```kotlin
// 敏感 header 名单 — 导出前替换
private val REDACT_HEADERS = setOf("cookie", "set-cookie", "authorization", "proxy-authorization")

// 规则
headersText() 输出时：敏感 header 值替换为 "***REDACTED***"
failedResources / redirectChain 中的 URL：
  - 移除 query string 中的 token/key/session/secret/jwt 等参数
  - 保留 path 与 host（排协议必需）
```

**不记录**：密码、完整 Cookie 值、Authorization 值、完整学号、未脱敏 HTML 原文。

现有 `JwDiagnosticSession.headersText()` 当前**不做脱敏**（源码注释「1B 不脱敏, 原样保留」），本子项目会在导出层加脱敏，保留原始记录不动。

---

## 8. 输出格式

追加到现有 `JwCaptureDump` 排查包：

```
environment.txt
  # Session: <id>
  # WebView Provider: com.android.webview 120.0.6099.144
  # Chromium major: 120
  # Android: SDK 33 (13) Xiaomi M2101K6G
  # Configured UA: Mozilla/5.0 (Windows NT 10.0; ...)
  # Actual UA: Mozilla/5.0 (Linux; Android 13; ...) Chrome/120...
  # Initial URL: https://jw.example.edu.cn/login
  # Final URL: https://jw.example.edu.cn/xkcb
  # Redirect chain: 3 hops
  #   0. https://jw.example.edu.cn/login
  #   1. https://sso.example.edu.cn/auth
  #   2. https://jw.example.edu.cn/xkcb
  # Storage: localStorage=18 entries, sessionStorage=3, indexedDB=yes, serviceWorker=unknown

navigation.txt
  # Window.open / target=_blank requests
  +120ms window.open https://jw.example.edu.cn/kbcx/new

failures.txt
  # Failed resources
  +340ms ERROR_UNKNOWN https://cdn.example.edu.cn/app.js (script)
  +512ms ERROR_CONNECT https://jw.example.edu.cn/frame.html (document)
  # SSL decisions
  +200ms proceed old-sso.example.edu.cn
  +900ms cancel expired-cert.example.edu.cn
```

---

## 9. 测试策略

**纯 JVM 单测**（`app/src/test/java/com/lingion/sleepy/ui/screen/imports/`）：

| 测试 | 断言 |
|---|---|
| `JwWebViewEnvironmentTest` | 数据类默认值全为 null/0/false，空构造不崩 |
| `JwDiagnosticExporterTest` | environment.txt 格式正确，字段顺序稳定 |
| `JwDiagnosticExporterTest.redaction` | Cookie/Authorization 被替换为 `***REDACTED***` |
| `JwDiagnosticExporterTest.urlSanitize` | query 中 token 被移除，path 保留 |
| `JwDiagnosticExporterTest.navigation` | window.open 列表正确导出 |
| `JwDiagnosticExporterTest.failures` | 失败资源 + SSL 决策合并导出正确 |
| `JwDiagnosticSessionContractTest` 扩展 | 新增 record 方法不破坏现有导出 |

**红线测试**：导出文本中不得出现 `Set-Cookie` 原值、`password`、`Authorization` 原值。

---

## 10. 分支与提交

**分支**：`feat/jw-webview-diagnostics`（从 `main` 拉）

**文件**：
- 新增 `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironment.kt`
- 新增 `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentTest.kt`
- 修改 `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt`（新增 record/export，不改现有行为）
- 修改 `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt`（factory 期采集 + 桥注入）
- 修改 `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCaptureDump.kt`（追加三个新文件导出）
- 新增 `docs/superpowers/specs/2026-09-26-jw-webview-diagnostics-design.md`（本文档）

**提交拆分**：
1. `feat(jw): add WebView environment data model and exporter` — 数据类 + 导出 + 单测
2. `feat(jw): capture WebView provider, UA, navigation and storage state` — 采集 + 接入
3. `test(jw): cover diagnostic exporter redaction and formatting` — 补充边界测试

**验证命令**：
```bash
./gradlew :app:testDebugUnitTest --tests "*JwWebViewEnvironment*"
./gradlew :app:testDebugUnitTest --tests "*JwDiagnostic*"
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

---

## 11. 成功标准

- [ ] 任意一次失败导入可生成完整 `environment.txt`
- [ ] 导出文本不含密码、完整 Cookie 值、Authorization 值
- [ ] 现有 `JwDiagnosticSession` 行为完全不变（现有测试全绿）
- [ ] 纯 JVM 单测覆盖格式化与脱敏
- [ ] 线程安全约束不变量保持（无新增 `view.*` 后台线程读取）
- [ ] `:app:testDebugUnitTest` 与 `:app:lintDebug` 全绿

---

## 12. 回滚

单文件新增为主，回滚成本极低：

```bash
git revert <commit>
```

或直接删除两个新增文件并还原三处修改。`JwDiagnosticSession` 新增方法为纯增量，不影响现有调用方。

---

## 13. 长期核对

| 周期 | 动作 |
|---|---|
| 每次导入失败 | 用户主动导出排查包，核对环境字段完整率 |
| 每周 | 统计 `UNKNOWN` 占比，>30% 触发补证据 |
| 每月 | 评估是否需新增字段（如厂商内核分叉信息） |
| 每季度 | 复核脱敏规则是否覆盖新增敏感字段 |

**指标**：
- 诊断完整率：≥90%（阶段 1）→ ≥99%（稳定）
- 敏感数据泄露：0
- `UNKNOWN` 占比：<30% → <10%
