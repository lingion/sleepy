# Step 5 现状代码阅读 — NBCC WebVPN 适配前基线

- 日期: 2026-10-26 · 工作区: `~/sleepy-worktrees/nbcc`(branch `adapt/nbcc-webvpn`,HEAD `b7d7de47`,含 main 全部)
- 所有位置均已人工在 nbcc worktree 逐行核验(考古 agent 报告的路径前缀有误——实际在 `data/jw/` 与 `ui/screen/imports/` 下,非 `jw/`;内容结论一致)。
- 并行 worktree 事故与收编: 本会话曾误开 `nbcc-adapt`(branch `feature/nbcc-webvpn-adapt`),两 scope.md 经 diff 证实字节相同后已 `worktree remove --force` + 删分支,唯一工作区 = `nbcc`。

## 1. 学校条目现状(`app/src/main/assets/schools.json:27476-27485`)

```json
{ "name": "宁波城市职业技术学院",
  "url": "https://webvpn.nbcc.edu.cn/login",
  "type": "shuwei_json", "status": "unverified",
  "enableFetch": false, "catalogId": "moe:4133012645" }
```

- type 为 2026-10-16 目录战役按 WakeUp 资产 `xatu_shuwei` 猜配,从未实测。
- 同形态先例: GUET、HFPEC 两条 shuwei_json 条目 URL 已带 `/https/77726476.../` 前缀,同样 unverified——**NBCC 不是孤例,是「shuwei_json × 深信服 WebVPN」整族的共同空洞**。

## 2. 协议层(`data/jw/JwProtocol.kt`)

- `TYPE_SHUWEI = "shuwei_json"`(:61),显示名「数维教务」,在 `WAKEUP_COMPAT_TYPES`(:313-317)。

## 3. 解析层(仅解析,无登录/无 fetch)

- `JwParserRegistry` → `JwShuweiParser`(`JwWakeUpCompatParsers.kt`)→ `WakeUpCompat.parseShuwei`。
- 解析只在拿到课表 HTML/JSON 之后生效;NBCC 场景根本走不到这一步。

## 4. fetch 分发缺口(`data/jw/JwFetchProtocol.kt`)

- `pick()`(:14-35)分支: WISEDU / ZF_NEW / QZ 族——**无 SHUWEI 分支 → NBCC 恒返回 null → 落入通用 outerHTML 抓取**。
- `WEBVPN_HTTP_HEX = Regex("/http/[0-9a-f]{4,8}/")`(:38):**对 NBCC 的 `/https/<hex>/` 永不匹配**(`/http/` 要求第 6 字符是 `/`,`https` 的 `s` 挡住)。全库 Kotlin 字面量 `/https/` 命中数 = 0(已 grep 复核)。
- `stripWebvpnPrefix()`(:55-61)只处理 `/http/<hex>` 与 `/webvpn/<host>` 两形态——`/https/<hex>` 原样返回,不去前缀。
- `cookie?method`(wengine-vpn cookie 桥)在 Kotlin/JS 源码 0 命中——app 从不直接调它(WebVPN 对 WebView 透明,浏览器自身会带;但这也意味着我们无法主动注入内网域 cookie)。

## 5. WebView 登录屏(`ui/screen/imports/JwWebViewLoginScreen.kt`)

- CaptureBar 按 type 串行 if-分支(约 :455 起): NUIT/KUST/YETHAN/CQU/CF_NEW/WHUT/CHAOXING/BOYA_PP/QZ_APP/BJTU/EAMS5…——**无 SHUWEI / EAMS-mobile 分支**,NBCC 点「导入此页」走通用 DOM 抓取。
- `currentUrl = school.url`(:132 区)→ 初始导航即 `webvpn.nbcc.edu.cn/login`(VPN 门户登录页,非教务页)。

## 6. 框架锚点(`ui/screen/imports/JwWebViewFrameCapture.kt`)

- `ANCHORS`(:125-127)= Table1/blacktab/kbtable/kbgrid_table_0/kblist_table/kbList/kbxx/timetable/_dgdata/pageRpt/mytable/reportArea/xkinfo/sample-table-1——**全部传统服务端渲染表格 id,无一能命中 Ionic/Angular SPA**(采集包实况: top frame 只有 `<app-root>` 与错误文案,0 锚点 → WRONG_PAGE「当前页面未检测到课表容器」,与 dump summary 完全一致)。

## 7. Cookie 管理(修正考古 agent 结论)

- agent 报「main 源码无任何 CookieManager 使用」= **错误**。实测 5 文件使用: JwRequestInterceptor(Set-Cookie 回写同步)、JwImportActivity(主线程取 cookie)、JwWebViewLoginScreen(下载带 cookie)、JwWebViewClientBuilder、dump 导出。
- 但确无显式 `setAcceptCookie/setAcceptThirdPartyCookies/flush()`——依赖 WebView 默认开启 cookie。对 WebVPN 场景够用(ticket 由 302 链自动落),**瓶颈不在 app 侧 cookie 管理,在校园 SPA 的容器依赖**。

## 8. HFUT 先例(可移植资产)

- `TYPE_EAMS5` 四段 fetch链: JwWebViewLoginScreen EAMS5 分支 + `data/jw/Eams5PathPrefix.kt`(纯 JVM 前缀推断 `/eams5-student` vs `/student`;`EAMS5_STUDENT_ID_REGEX` 六形态宽松匹配;`isEams5LoginRedirect` 会话失效检测)。
- **注意**: EAMS5 = supwisdom 新版 PC 形态(HFUT/ahu/cumtb);NBCC 移动端是 Ionic SPA + 钉钉 authCode,仅同源概念不同协议,不能直接复用 EAMS5 type。
- 测试组: `HfutPortalEams5WebViewContractTest` / `HfutIssue25UrlEntryTest` / `HfutIssue46LayoutNodeMappingTest` / `HfutEams5DatumChainContractTest`;对抗 fixture 组 `jw_fixtures/adversarial/webvpn-urls/`(pku/bnuz/cf)——**webvpn-urls fixture 目录存在但只收 `/http/<hex>/` 形态,无 `/https/` 样本**,Step 7 要补。

## 9. 缺口汇总(Step 6 修复设计的输入)

| # | 缺口 | 证据位置 |
|---|------|---------|
| G1 | pick() 无 shuwei/EAMS-mobile 分支 → 通用抓取 | JwFetchProtocol.kt:14-35 |
| G2 | `/https/<hex>/` 正则零支持(hex 前缀识别与剥离) | JwFetchProtocol.kt:38,55-61 |
| G3 | ANCHORS 无 SPA/Ionic 容器指纹 | JwWebViewFrameCapture.kt:125-127 |
| G4 | 钉钉容器换票死结: SPA 在 WebView 内 notInDingTalk,换票 POST 不发出 → 兜底登录页又依赖验证码(采集包 console 实锤) | dump console.txt +70358ms |
| G5 | 若学生账号体系不同源(VPN 门户票 ≠ EAMS 内部账号),手输必「密码错误」 | dump netlog 零 nbcc POST + 门户 API 全带票成功 |
| G6 | 无密码传递通路(JS↔原生 bridge 只有采集用 `__sleepyNetworkBridge`,无凭据注入面) | JwDiagnosticSession.kt:171 |

G4/G5 属校园侧设计约束,修复路径选择(钉钉模拟 / authCode 注入 / PC 端直连 / 密码传递器)待 Step 2-4 调研矩阵收敛后定。
