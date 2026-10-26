# UCAS SEP SSO 修复 — 现状代码阅读 + 修复内容

## 修复前状态 (main 08a1f26)

- JwWebViewLoginScreen.kt: UCAS 无专属 fetch JS (走通用 frame 抓取), WebViewClient 无
  shouldInterceptRequest — SEP 域请求带 XRW 原样发出 → 401 JSON 页。
- JwUcasParser + UcasDetailFetch (#18 v1.2 落地): personSchedule 网格 HTML + xkcts:8443
  详情页原生直抓, 与本次 SSO 层修复正交, 未触碰。

## 修复内容 (fix/issue-18-sep-xrw-login @ 0f3d77c)

- 新增 SepXrwStripInterceptor.kt: shouldProxy 门 (仅 sep.ucas.ac.cn GET; POST /slogin
  实测豁免放行) + intercept 入口 (失败回 null = 原生行为) + execute (手动逐跳 300..399,
  每跳 Set-Cookie → CookieManager.setCookie 同步, 出域 leaveHostRedirect 合成 302,
  SKIP_REQUEST_HEADERS 含 x-requested-with/accept-encoding)。
- JwWebViewLoginScreen.kt: WebViewClient 挂 shouldInterceptRequest 委托
  SepXrwStripInterceptor.intercept; SSL 白名单 (SslBypassRegistry) 原样保留。
- 测试: SepXrwStripInterceptorTest 16 用例 (纯函数) + SepXrwStripInterceptorContractTest
  6 用例 (源码扫描: 接线/剥头/逐跳/出域/POST 放行/SSL 白名单存续)。
- 全量单测绿; lint 35 errors = pre-existing 基线 (main 同数), 0 新增。

## 补跑跨仓验证轮新增 (本 commit)

- 致谢 +2: wirsbf/TraintimePda-UCAS、tbjuechen/sep-api → LicenseScreen.kt school-ucas
  块 + 6 locale strings.xml + AboutLicenseAttributionTest。
- 归档: issues/0018-adapt/ (comments.json.new 7 评论 + 18-relogin-error.png 截图)。
