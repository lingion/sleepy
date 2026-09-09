# JOU (江苏海洋大学) 适配 — 现状代码阅读 (SOP Step 5)

基线: worktree `sleepy-worktrees/jou-adapt`, branch `adapt/jou-jw` @ main `376336f`。

## 数据流 (老版正方 type=zf 在 sleepy 中的既有通路)

```
SchoolSelectScreen → JwImportActivity/JwWebViewLoginScreen
  loadUrl(school.url)                      ← schools.json url 即 WebView 起点 URL
  用户手动完成 CAS/教务登录, 停留在课表页
  点「导入此页」→ CaptureBar.onCapture:
    qz_app / BJTU / EAMS5 专用分支 (不适用)
    isZfNew 判定 (JOU 不命中: 无 /jwglxt/ /kbcx/ 路径) → 落默认 DFS
    captureWithRetry → CAPTURE_FRAMES_JS (DFS 遍历 frame, 跨域记 blocked)
    → FrameTraversalTree.selectBestFrame:
        锚点 Table1/blacktab 命中 → OK (html 交 parser)
        无锚点 → 登录指纹 (score>=2) SESSION_EXPIRED > 跨域 > WRONG_PAGE
    → JwImportViewModel.parseHtml(html, "zf") → JwOldZfParser (Table1, type=0)
```

## 关键既有件 (全部已存在, JOU 零新增 parser)

- `schools.json`: 183 校, type=zf 12 所。JOU 缺失 → 本次新增 1 条 (assets 与
  app/src/test/resources/jw/schools.json 两份必须同步)。
- `JwProtocol`: TYPE_ZF="zf" 已声明; URL/HTML 嗅探已含 `xskbcx.aspx → TYPE_ZF`
  (JwWebViewLoginScreen 328/380/458 行); schools.json 一致性测试已锁 type 白名单。
- `JwOldZfParser` (issue #5/T1 移植 WakeupSchedule_BUPT ZhengFangParser): Table1 →
  blacktab → 含"星期一"首表兜底; 23 项属性词; 合并行头; 单双周; zf_1 变体。
  12 所在用 zf 校同走此 parser — JOU 直接复用, 不写新 parser。
- `FrameTraversalTree.ANCHORS` 已含 Table1/blacktab; `LOGIN_FINGERPRINTS` score>=2
  (`__viewstate`+password 同族对 / checkcode.aspx / 请重新登录 等) — JOU 登录页
  (default2.aspx) 命中 zf 同族对, 已有检测覆盖。
- `SslBypassRegistry`: 按注册域豁免 — cas.jou.edu.cn 与 zf.jou.edu.cn 同注册域
  (jou.edu.cn), CAS 跳转链的 SSL 白名单天然覆盖, 无需改。

## 缺口 (本次适配的改动点)

- G1: schools.json 无江苏海洋大学条目 (assets + test resources 两份同步)。
- G2: 老正方"登录态失效 = HTTP 200 + `<script>window.parent.location.href=
  'logout.aspx'</script>` + frameset"形态 (JOU 未登录抓包实锤)。采集时机恰在
  parent 未完成跳转时, 可达 frame 全无锚点、登录指纹 < 2 分 → 误报 WRONG_PAGE
  (「当前页面未检测到课表容器」) 而非 SESSION_EXPIRED (「请重新登录」)。该 JS
  跳转惯用法是老正方通用 logout 跳转形态, 应作硬指纹 (单条即判, 不走 2 分门槛)。
  JwParseDiagnostics 的页面级登录嗅探同样不含它, 同步补。
- G3: JOU Table1 形态无本地 fixture — 老正方 parser 单测 23 用例已绿, 缺一版按
  JOU 实测协议拼装的形态锁 (synthetic, 标注来源与理由)。

## schools.json 条目设计 (D1)

```json
{"sortKey": "J", "name": "江苏海洋大学",
 "url": "https://zf.jou.edu.cn/login_cas.aspx", "type": "zf", "aliases": [],
 "sortKeyFull": "jiangsuhaiyangdaxue"}
```

url 选择 login_cas.aspx 而非裸域: 直开 → 302 → cas.jou.edu.cn/lyuapServer/login?
service= (登录页含验证码) → 用户登录 → ticket 回跳 zf 域建立教务会话。WebView
CookieManager 全局共享, CAS 域与 zf 域 cookie 同时存活。走 default2.aspx 裸登录
表单会绕开 CAS, 校内多域 SSO (选课 zfxk.jou.edu.cn 等子系统) 断裂 — login_cas.aspx
是教务域的 SSO 正门。与闭源参考酱海带 (login_cas.aspx 入口) 印证一致。
