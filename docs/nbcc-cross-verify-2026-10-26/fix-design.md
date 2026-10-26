# NBCC 教务适配 — Step 6 修复设计(最终)

- 日期: 2026-10-26 · 工作区: ~/sleepy-worktrees/nbcc(branch adapt/nbcc-webvpn)
- 输入: scope.md / pc-entry-evidence.md / protocol-matrix.md / current-code-state.md / findings.json(268 裁决) / 上游证据 /tmp/nbcc-research/raw/files/(POSITIVE = Wangxiaolu0716/class-schedule)
- 定位: 本文 = Step 7 实现与 Step 8 测试的唯一蓝本。所有代码位点均已在本 worktree 逐行核验。

## 0. 设计铁律(不可被后续步骤推翻的裁决)

### 0.1 根因与钉钉路线永久作废
根因 = 我方 WebView 默认移动 UA(`JwWebViewLoginScreen.kt:140` desktopUa=false)→ 新版门户按 UA 分流 `/m/portal` → 移动教务磁贴指向钉钉专用 SPA(`jwglapp.nbcc.cn/app-web/dingding-login`)→ 换票失败落手输页(独立账号体系)→ 门户凭据必「密码错误」。人类真实路径全程无钉钉:门户 → PC 教务 → 教务账号直登。**本文档及其全部后续实现中不得出现任何钉钉模拟/authCode 注入/SPA 容器分支**(用户原话裁决,scope.md;memory 档案 project_nbcc_webvpn_ua_entry)。错误入口只允许以反例 fixture 形式存在(§5.2-d),用于锁死「不再复现」。

### 0.2 协议定性 = classic_eams(非 shuwei_json)
双源锁死:NBCC 学生本人应用(POSITIVE 裁决仓)与本校 `JwClassicEamsParser` 锁同一 TaskActivity 指纹 + `courseTableForStd!courseTable.action` 参数体形态。schools.json 现配 `shuwei_json` 系 2026-10-16 目录战役按 WakeUp `xatu_shuwei` 厂商名猜配的错标(他仓旁证见 protocol-matrix 补记:树维真形态 = SHA1 登录体 + loginExt.action,指纹完全不同源)。本轮修正 `type`,不依赖 Q1/Q2 裁决。

### 0.3 双盐铁律
NBCC 有两个互不相干的盐:**URL hex 编解码盐 `wrdvpnisthebest!`**(AES-128-CFB,key=iv,segment128;我方 dump 实解 + 调研 agent + 控制器复现三源锁定)与**门户密码加密盐 `wrdvpnisawesome!`**(新门户登录页 JS 原文两次出现,提交前加密 password 字段)。二者用途永不互换;盐是按校部署常量,禁跨校写死(NEU 异盐先例)。**本设计选形态 B(WebView 同源链),两盐均零落地**——同源相对 fetch 不需要 URL 编解码,人工登录不需要密码注入。两盐仅以注释留档于 §4 常量表末行;若未来启用形态 A(原生登录链),hex 盐接 URL 编解码、密码盐接表单提交,接线位仅此一处定义。

### 0.4 双分支强制令(Q1/Q2)
新门户桌面 UA 是否有 PC 教务磁贴(Q1)与 `jwgl.webvpn.nbcc.cn` vhost 会话内可达性(Q2)在无凭据条件下判别力已穷尽(pc-entry-evidence §3.1 对照实验:匿名 hex 探测对任意内网域一律 302,零区分度)。**裁决闸 = 学生自愿回传实测**。本设计必须同时给出两个可上线的 schools.json 条目分支(§1),禁止单分支上线,禁止等待裁决阻塞其余设计。

### 0.5 形态优先级
B(WebView 门户登录 + 同源 fetch,本校学生实测成功)> A(原生 wengine 链,要素已全公开但含滑块/_csrf/锁定策略,留档不实现)> C(密码传递器,先例存在、对本校非必需,反而扩大凭据面,本轮不做)> D(教务域直登,crt.sh 无直域 + 公共 DNS 零解析,排除)。

### 0.6 SOP v1.14 Step 6 三件套
共享常量单一真源(§4)+ 宽松 regex(每条 pattern 容忍单双引号/空白变体,照抄 Dart 实测形态)+ 跨语言 invariant(Kotlin pattern ≡ JS regex literal 逐字符,契约测试锁死)+ 登录态检测(§3 判据链,302→/login 必报 SESSION_EXPIRED,绝不落 0 课伪成功)。

---

## 1. schools.json NBCC 条目 — 两分支各一份(Step 7 照抄)

现状(`app/src/main/assets/schools.json:27476-27485`,数组下标 2548):

```json
{ "sortKey": "N", "name": "宁波城市职业技术学院",
  "url": "https://webvpn.nbcc.edu.cn/login", "type": "shuwei_json",
  "status": "unverified", "aliases": [],
  "sortKeyFull": "ningbochengshizhiyejishuxueyuan",
  "enableFetch": false, "catalogId": "moe:4133012645" }
```

### 1.1 分支 Q1 — 新门户桌面 UA 取 PC 教务磁贴(人真实路径的直接复刻)

```json
{ "sortKey": "N", "name": "宁波城市职业技术学院",
  "url": "https://webvpn.nbcc.edu.cn/login",
  "type": "classic_eams", "status": "unverified",
  "aliases": [],
  "sortKeyFull": "ningbochengshizhiyejishuxueyuan",
  "enableFetch": true, "catalogId": "moe:4133012645" }
```

配套代码行为(非 JSON 字段):NBCC 的 WebView **初始桌面 UA**(`initialDesktopUa(school)` 纯函数,§3.5),用户仍可手动切回手机 UA(开关保留)。判型链对 Q1 无需改——用户从门户点进 PC 教务后,`wv.url` 落在 `…webvpn.nbcc.edu.cn/https/<hex>/eams/courseTableForStd…`,尾部明文 `coursetableforstd` 子串仍可见(CaptureBar 分支判据 §3.3 用尾部指纹,不依赖 hex 解码)。

### 1.2 分支 Q2 — 移动 UA 经旧门户时代链路直达 PC EAMS vhost(学生应用实测形态)

```json
{ "sortKey": "N", "name": "宁波城市职业技术学院",
  "url": "https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action",
  "type": "classic_eams", "status": "unverified",
  "aliases": [],
  "sortKeyFull": "ningbochengshizhiyejishuxueyuan",
  "enableFetch": true, "catalogId": "moe:4133012645" }
```

配套代码行为:默认移动 UA 即可(学生应用实证 `school_config.dart:11-14`「强制移动端 UA 反而直达 PC EAMS」);WebView 打开该 URL:若 wengine 会话有效 → 直接落正方 EAMS 课表入口页;若无会话 → 302 到门户登录页,用户**在同一 WebView 内**完成门户登录,回跳后再点「导入此页」。`JwImportViewModel.detectProtocolFromUrlImpl` 的 webvpn 内层 when 需补 `coursetableforstd` 锚点(§3.6,否则该 URL 命中 `.webvpn.` 前缀分支后判 null)。

### 1.3 两分支共同点与不变量

- `type=classic_eams` 双分支必改(协议=TaskActivity 指纹,双源锁死,与入口分支无关);shuwei_json 错配修正不依赖 Q1/Q2 裁决。
- `status=unverified`:两分支的入口可达性都要等学生实测,先保持 unverified。unverified + `JwParserRegistry.supports("classic_eams")` → `canImport=true`(`JwSchoolInfo.kt:32`),用户选择后走「未验证试用」确认弹窗(`JwImportActivity.kt:902` catalogNotice mayTry 分支),导入成功自动弹**离线报告**(`JwImportReport`,2026-10-18 回路)——这正是 Q1/Q2 裁决闸的现成采集通道。
- `enableFetch=true`:作为 NBCC 新 fetch 分支的校级总开关(48 个既有 classic_eams 校全部 enableFetch=false/缺失 → 零影响,§6.2)。
- sortKey/sortKeyFull/aliases/catalogId 不动。`previously supported school count stays 350` 计数闸(Schools179CrossValidationTest:186)只数 isSupported,status 保持 unverified → 不触发。

### 1.4 分支切换判据与实测采集步骤

无凭据已做完的(Q1/Q2 无凭据判别力已穷尽,pc-entry-evidence §3.1):
- 旧门户整站 302 → 新门户根(推翻旧行 200 快照)→ Q2 的可达性只能在新门户会话内验。
- hex 探测对照实验:jwgl vhost 与编造域同形 302→/login → 网关鉴权前置,匿名探测零判别力。
- 公开 SAN(crt.sh)无教务直域 → 教务必走 VPN 通道(双源)。

必须等学生自愿回传(裁决闸,二选一即定分支):
| 采集项 | Q1 判据 | Q2 判据 |
|---|---|---|
| 新门户桌面 UA 登录后首页磁贴列表截图/DOM | 存在「教务管理/PC 教务」类磁贴且 href 含 `courseTableForStd` 或解码前 hex 段可点开 | — |
| 移动 UA WebView 直达 `jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action` | — | 页面渲染出正方 EAMS 登录框或课表入口页(URL 不变/尾部明文命中) |
| 登录后落地 URL(netlog 或地址栏) | 落在 `/https/<hex>/eams/…` 或直域,且含 TaskActivity/入口表单 | 同左 |

判定规则:Q1 通 → 上线 Q1 条目(桌面 UA 初值);Q1 不通且 Q2 通 → 上线 Q2 条目;两通 → 取 Q1(与用户权威描述的人类路径同构);两不通 → 保持条目 type=classic_eams + url=门户 /login + enableFetch=true + status=unverified 如实挂 pending(协议改判不回退,入口如实未证——对齐 SOP v1.14-⑨「完成定义=每条结论有出处」)。Step 7 实现两分支共用的 fetch 链,分支只是 JSON 条目初值差异,代码零分支切换成本。

---

## 2. 现状缺口 → 本设计映射(全部位点已逐行核验)

| 缺口(current-code-state §9) | 本设计处置 | 落点 |
|---|---|---|
| G1 pick() 无 classic_eams 通路 | 不动 JwFetchProtocol;NBCC 走 CaptureBar 新串行分支(与 EAMS5/BJTU 等同层),分支判据 type+enableFetch+URL 尾部指纹 | §3.3 |
| G2 `/https/<hex>/` 正则零支持 | **本轮不放宽** WEBVPN_HTTP_HEX(`JwFetchProtocol.kt:38`,现 pattern 只匹配 `/http/<4-8位hex>/`,不匹配 `/https/<长hex>/`)——放宽会把 GCU/HEBUEE/柳州城市 3 条既有 `/https/` zf_new 校从通用抓取改道 ZF_NEW fetch,超出影响半径;NBCC 链不需要它(同源相对 fetch + 尾部明文指纹,§3.4)。记为已知空洞,仅形态 A 复活时再修 | §6.3 |
| G3 ANCHORS 无 SPA 指纹 | 不修——修复方向是根本不进 SPA(根因=入口,非锚点);反例 fixture (d) 锁「错入口必 WRONG_PAGE 清晰报错」 | §5.2 |
| G4 默认移动 UA | Q1 分支初值桌面化,纯函数可测,白名单式(仅 NBCC Q1 条目生效) | §3.5 |
| G5 移动 SPA 账号体系差异 | 根因修复后不再触达;设计中无任何钉钉/SPA 分支 | §0.1 |
| G6 无凭据传递通路 | 形态 B 不需要(用户凭据只在 WebView 内人肉输入,JS 从不读 password 字段);密码传递器(形态 C)本轮明确不做 | §7 第 4 条 |

---

## 3. 导入链数据流(形态 B,Step 7 实现蓝本)

### 3.1 ASCII 数据流图

```
学校选中 JwImportActivity(Stage.SelectSchool)
  │ canImport=false? → 提示; unverified → catalogNotice「试用?」确认
  ▼
JwWebViewLoginScreen  WebView 初始化
  │ initialUrl = schools.json url            [Q1: 门户/login | Q2: PC EAMS 直链]
  │ desktopUa 初值 = initialDesktopUa(school) [Q1: true | Q2: false | 其余校: false]
  │ mixedContentMode = ALWAYS_ALLOW(现役 :690,不动)
  ▼
用户在 WebView 内人工登录门户(app 零接触凭据)
  ▼
落地判定(用户停在教务页 → 点「导入此页」CaptureBar.onCapture)
  │ 串行 if-分支新增:
  │   school.type==classic_eams ∧ school.enableFetch ∧
  │   (wv.url 或 school.url 含 "coursetableforstd"(大小写不敏感,明文尾部指纹))
  │     → evaluateFetchWithTimeout(wv, CLASSIC_EAMS_FETCH_JS)
  │   同型但 URL 指纹未命中(用户还停在门户首页) → 走下方 hint 分支,
  │   err=「请先在门户进入 教务管理→我的课表 页面后再点导入」(不自动猜磁贴 URL)
  ▼
CLASSIC_EAMS_FETCH_JS(页面内, 同源)
  ① 定位课表表单上下文: 顶层 document;未命中则遍历同源 iframe
     (try/catch contentDocument, 判据: iframe src 或文档含 courseTableForStd/courseTableForm)
  ② 参数提取(宽松 regex, §4):
     ids        ← addInput(form,"ids","NNN") 首选, name="ids" 隐藏域兜底
     semester.id← semesterCalendar({…,value:"NNN"}) 首选, name="semester.id" 兜底
     project.id ← name="project.id" 隐藏域, 缺省 "1"
     (ids 取不到 → 不猜, 直接 err; 防串表单: 只在含课表表单的 frame 内取 —
      Dart 实测教训: 整段拼接取第一个 addInput 会抓到成绩查询表单的学号)
  ③ 候选 ≤3 组参数体(与 Dart buildCourseTableParamsCandidates 同序):
     c1 = ignoreHead=1&setting.kind=std&startWeek=&project.id=P&semester.id=S&ids=I
     c2 = 去 project.id 段(semesterCalendar 源)
     c3 = semester.id 换隐藏域源(仍 ≤3, 去重)
  ④ 依序 POST, endpoint = 相对当前文档目录的
     "courseTableForStd!courseTable.action"
     (Q1 在 /https/<hex>/eams/… 页面下相对解析 → 前缀自动保留, 无需 hex 盐;
      Q2 在 jwgl vhost /eams/ 下同理; credentials:'same-origin')
  ⑤ 判据链(每一候选):
     r.url 含 "/login"|"sign_in"          → SESSION_EXPIRED err(登录态失效,请重登)
     响应文本含 "new TaskActivity("        → 成功, 立即停止重试
     三候选耗尽均无 TaskActivity          → err=「教务页未返回课表数据,请在课表页停留后重试」
  ▼
__sleepyBridge.onWiseduResult({ok:true, data:响应HTML, periods:[]})
  │ (复用现役回调通道, handleWiseduResult :159 不改签名)
  ▼
onHtmlCaptured(data, school, periods, "")   JwImportActivity :777
  │ effectiveType = school.type = "classic_eams"(非空,不跑兜底 detect)
  ▼
JwImportViewModel.parseHtml → JwParserRegistry.FACTORIES[classic_eams]
  → JwClassicEamsParser: unitCount 抠页面(11/12/13/14 禁写死) →
    TaskActivity 块状态机切参 → 周次位图 → JwCourse 列表
  ▼
预览/确认页(现役) — periods 空 → 用户手填开学日期(既有通路;NBCC 教务页无开学日期,
学生应用默认 2026-09-07 仅作 app 内引导, 不写死进协议)
  ▼
保存 → 未验证校成功 → JwImportReport 离线报告弹窗(= §1.4 采集闸的回传载体)
```

### 3.2 落点一:schools.json(`app/src/main/assets/schools.json:27476-27485`)
改 §1 选定分支的字段;新增 `nbcc entry pinned to pc-entry evidence` 契约测试(§5.3)。最小面:一个条目 + 一个测试。

### 3.3 落点二:CaptureBar 新串行分支(`ui/screen/imports/JwWebViewLoginScreen.kt`,插入点 :535 BJTU 分支后、:540 EAMS5 分支前)
```kotlin
if (school.type == JwProtocol.TYPE_CLASSIC_EAMS && school.enableFetch) {
    evaluateFetchWithTimeout(wv, CLASSIC_EAMS_FETCH_JS)
    return@CaptureBar
}
```
三重判据缺一不可:既有 48 校 enableFetch 恒 false 落通用 outerHTML 抓取(路径零变化);NBCC 若未登录停在门户,JS 内 host/表单守卫给精确动线 hint(复刻 EAMS5 对 one.hfut.edu.cn 门户拦截模式 :1757)。

### 3.4 落点三:新 JS 常量 `CLASSIC_EAMS_FETCH_JS`(`JwWebViewLoginScreen.kt` 底部常量区,:1754 EAMS5_FETCH_JS 旁)
结构复刻 EAMS5_FETCH_JS 骨架(IIFE + try/catch + `__sleepyBridge.onWiseduResult`);与 Dart 参考实现的三条刻意对齐:①候选上限 3(不无限重试);②「只在课表表单所在 frame 取参」(跨 frame 串表单反例);③ids 缺失不猜。相对 endpoint ⇒ 双盐在本形态全不落地(§0.3)。

### 3.5 落点四:桌面 UA 初值(`JwWebViewLoginScreen.kt:140`)
```kotlin
var desktopUa by remember { mutableStateOf(initialDesktopUa(school)) }
```
`initialDesktopUa` 新纯函数,放 `data/jw/ClassicEamsWebVpn.kt`(§4):
`school.type==classic_eams && school.enableFetch && hostOf(school.url)=="webvpn.nbcc.edu.cn"` → true,其余 false。白名单式判据,UCAS #18 的手动切换体验不变;未来第二所「门户分流」校进入时只扩该函数的校名单,零结构改动。单测锁:NBCC-Q1=true / NBCC-Q2=false / GUET=false / 无 URL 校=false。

### 3.6 落点五:webvpn 判型补锚(`data/jw/JwImportViewModel.kt:359-380` 内层 when)
在 `u.contains("jw.nuit.edu.cn")` 之前补:
```kotlin
u.contains("coursetableforstd") || u.contains("/eams/courseTableForStd") -> JwProtocol.TYPE_CLASSIC_EAMS
```
(边界正则形态,对齐 ①a 外层锚点 :402;只在内层 webvpn 重写分支生效——其余校 URL 不含该明文尾,零影响。)同时修 Q2 条目的 `canImport` 判型路径与 T9 诊断分类。

---

## 4. 共享常量清单与跨语言 invariant(单一真源 = 新文件 `data/jw/ClassicEamsWebVpn.kt`,纯 JVM 可测)

| 常量 | 值(pattern 原文) | 用途 | JS 对应体 |
|---|---|---|---|
| `CLASSIC_EAMS_ACTION_PATH` | `courseTableForStd!courseTable.action` | fetch endpoint + 判型 | JS 字面量同串 |
| `CLASSIC_EAMS_PAGE_ANCHOR` | `courseTableForStd`(小写比较) | URL/表单 frame 指纹 | JS `indexOf` 同串 |
| `CLASSIC_EAMS_TASK_ACTIVITY_NEEDLE` | `new TaskActivity(` | 成功判据 | JS `indexOf` 同串 |
| `CLASSIC_EAMS_IDS_RE` | `addInput\(\s*form\s*,\s*["']ids["']\s*,\s*["'](\d+)["']` | 学号首选 | JS 同 pattern |
| `CLASSIC_EAMS_IDS_INPUT_RE` | `<[^>]*\bname\s*=\s*["']ids["'][^>]*>.*?\bvalue\s*=\s*["'](\d+)["']` | 学号隐藏域兜底 | JS 同 pattern |
| `CLASSIC_EAMS_SEMESTER_CAL_RE` | `semesterCalendar\(\{[^}]*value\s*:\s*["'](\d+)["']` | 学期首选(Dart 实测隐藏域常为空) | JS 同 pattern |
| `CLASSIC_EAMS_SEMESTER_INPUT_RE` | `<[^>]*\bname\s*=\s*["']semester\.id["'][^>]*>.*?\bvalue\s*=\s*["'](\d+)["']` | 学期兜底 | JS 同 pattern |
| `CLASSIC_EAMS_PROJECT_RE` | `<[^>]*\bname\s*=\s*["']project\.id["'][^>]*>.*?\bvalue\s*=\s*["'](\d+)["']` | 项目 | JS 同 pattern |
| `CLASSIC_EAMS_PARAM_TEMPLATE` | `ignoreHead=1&setting.kind=std&startWeek=&project.id=%s&semester.id=%s&ids=%s`(去 project 形态 `ignoreHead=1&setting.kind=std&startWeek=&semester.id=%s&ids=%s`) | 候选体 | JS 拼接同前缀串 |
| `CLASSIC_EAMS_DEFAULT_PROJECT_ID` | `"1"` | project 缺省(Dart `defaultProjectId`) | JS 同值 |
| `CLASSIC_EAMS_LOGIN_URL_RE` | `/login(\?|$)|/users/sign_in` | 会话失效判据 | JS `test` 同 pattern |
| `NBCC_URL_HEX_SALT` / `NBCC_PORTAL_PWD_SALT` | **不在本设计引用面**——双盐仅注释留档:hex 编解码盐 `wrdvpnisthebest!`、门户密码盐 `wrdvpnisawesome!`;二者用途永不互换,常量按校,禁跨校写死。形态 A 复活时 hex 盐接 URL 编解码、密码盐接表单提交,WebView 同源链(形态 B)零使用 | 铁律 §0.3 | (无 JS 对应体,不造伪 invariant — SOP v1.9-② 口径) |

invariant 实现位置:`app/src/test/java/com/lingion/sleepy/data/jw/NbccClassicEamsWebViewContractTest.kt`,方法论照抄 `HfutPortalEams5WebViewContractTest.kt`(读源文件文本 → 定位 `private const val CLASSIC_EAMS_FETCH_JS = """` 提取 JS 体 → 对上表每个 pattern/字面量断言 JS 内**逐字符出现**,Regex pattern 按 JS regex literal 语法转义差异逐条声明容差:唯一允许差异是 JS 字面量外层 `/…/flags` 与 Kotlin `"""…"""`,转义体内逐字符相等)。Kotlin 侧 pattern 从 `ClassicEamsWebVpn.kt` 源文本marker 提取(不依赖反射)。登录态/成功判据的 needle 串(C3 表前三行)同样锁。unitCount regex **不在 JS 出现**(JS 不解码,解码唯一落点 parser)→ 不锁,与 v1.9-② 同口径。

---

## 5. fixture 设计输入(Step 7)

目录:`app/src/test/resources/jw/fixtures/classic_eams/`(现役 NWUPL/LIXIN 同目录)。每文件首行注释:fixture 编号 + 来源(仓 URL + 路径 + commit sha 或「合成」标注),防 SOP v1.2-② 注释误命中——头注不得含 TaskActivity 等判据词,判据词出现前必脱敏。

### 5.1 成功样本(N 形态各一条)
- `nbcc-course-table-action.html` — POST 响应页:含 `var unitCount = 12;` + 至少 3 个 `new TaskActivity(` 块 + 一个 `join(` 教师表达式块(锁 hpu/actTeachers 反查形态)+ 一个 `index =3*unitCount+2;` 形态 + 一个纯数字 index 形态;周次位图含单双周。合成依据:`/tmp/nbcc-research/raw/files/Wangxiaolu0716_class-schedule_eams_course_table_parser.dart` 头注真实样例(脱敏改写,零真实姓名/学号)。
- `nbcc-entry-page.html` — courseTableForStd.action 入口页:`semesterCalendar({…,value:"279"})` + `bg.form.addInput(form,"ids","2024…")` + 空壳 `<table id="manualArrangeCourseTable">` + **干扰成绩表单**(第二个 addInput ids 不同值,排在课表表单之前)→ 锁「按 frame/表单作用域取参」反串行为:提取器必须取课表表单的 ids。
- (可选,若学生回传)真实脱敏采集包样本一条,标 `collector-<session>` 头注。

### 5.2 反例样本(必全列)
| fixture | 形态 | 断言 |
|---|---|---|
| `nbcc-empty-shell.html` | 只有 `#manualArrangeCourseTable` 空壳 + 无 TaskActivity(直接 GET 入口页误当数据页) | parser 0 课 confidence=0;capture 层不选空壳为成功 |
| `nbcc-shuwei-mixed.json` | shuwei 引擎 JSON 混入(验证错配修正) | JwClassicEamsParser confidence=0;JwShuweiParser 命中;adjudicate 不串型 |
| `nbcc-login-page.html` | wengine 门户登录页片段(`nbcc_webvpn_login.html` 脱敏,不含盐值明文以外敏感内容) | 判据链: finalUrl/正文命中登录指纹 → SESSION_EXPIRED,绝不落 0 课伪成功;`encrypt(data[i].value,"wrdvpnisawesome!",…)` 原文保留在 fixture(公开前端常量,非凭据)以锁双盐留档真实性 |
| `nbcc-mobile-spa-shell.html` | Ionic SPA `<app-root>` 壳(根因错误入口,scope.md §2) | ANCHORS 0 命中 → WRONG_PAGE;锁死「钉钉时代错误入口不复现」 |
| `webvpn-urls/` 对抗组扩充 | `nbcc-https-hex.txt` = `/https/77726476…/eams/courseTableForStd.action` | `detectProtocolFromUrl` = classic_eams(§3.6 回归);现有 pku/bnuz/cf 三件不动 |

### 5.3 测试矩阵(Step 7 落地清单)
1. `JwClassicEamsParserTest` 追加:nbcc 成功样本逐断言(day/node/周次/教师 join 反查/unitCount=12);5.2 前三条反例。
2. `ClassicEamsWebVpnTest`:ids/semester/project 提取 N 形态(引号单双/空格/隐藏域兜底)+ 干扰表单反例 + 候选构造 ≤3 去重 + 模板串逐字符断言 + `initialDesktopUa` 四校表。
3. `NbccClassicEamsWebViewContractTest`(§4 invariant)。
4. `JwImportViewModelDetectTest`/既有判型套件追加 NBCC 两条 URL 形态(Q1 重写态 / Q2 直链态)→ classic_eams;webvpn-urls 对抗组扩充件。
5. `Schools179CrossValidationTest` 追加 `nbcc entry pinned`:catalogId=moe:4133012645、type=classic_eams、enableFetch=true、url ∈ {Q1, Q2} 白名单双值 OR 断言(避免文档裁决前测试锁死单分支)、计数闸 350 不动。
6. 全量回归:`./gradlew :app:testDebugUnitTest` + lint 0 新增(v1.9-④ 基线豁免口径)。

---

## 6. 风险与回滚(逐位点爆炸半径)

### 6.1 逐位点半径表

| 位点 | 改动性质 | 爆炸半径 | 回滚方式 |
|---|---|---|---|
| schools.json NBCC 条目 | 一行条目字段值 | 仅 NBCC(catalogId 唯一定位) | revert 条目即回到 shuwei_json 现状(已知坏态,无损失) |
| CaptureBar 新分支(:535/:540 间) | 串行 if 链**新增一支**,判据含 `enableFetch` | 仅 `type==classic_eams ∧ enableFetch==true` 的条目——当前全库 = 0 条 + NBCC 1 条 = 1 条 | 删分支即回到通用抓取路径;NBCC 本来就在通用路径上失败,无回归面 |
| `CLASSIC_EAMS_FETCH_JS` 新常量 | 纯新增,无既有引用 | 零(仅被上一行分支调用) | 随分支同删 |
| `initialDesktopUa` + :140 初值改写 | 初值表达式由 `false` 改纯函数;函数对除 NBCC-Q1 外全部返回 false | 其余全部学校 desktopUa 初值恒 false = 现状;NBCC-Q1 未裁决前 enableFetch 若保持 false 则连 NBCC 也不变 | 初值改回 `mutableStateOf(false)` 一行 |
| detectProtocolFromUrlImpl 内层补锚(§3.6) | 内层 webvpn when **新增 case**,置于 nuit 锚之前 | 仅 URL 含 `coursetableforstd` 明文尾者命中;既有 webvpn 校(nuit/kust/jsxsd/xkaction…)URL 不含 → 原分支顺序与结果不变 | 删 case;webvpn-urls 对抗组 pku/bnuz/cf 三件回归锁 |
| `ClassicEamsWebVpn.kt` 新文件 | 纯新增常量 + 纯函数;JwClassicEamsParser **不在本轮重构**(其内联 regex 与新文件逐字符同值,由 invariant 测试双锁,漂移即红) | 零既有引用 | 删文件 |
| fixtures + 测试追加 | 纯新增;`JwClassicEamsParserTest` 只追加不修改既有断言 | 零(4 先例校 fixture 与断言不动) | revert 测试文件 |

**总面:新增 2 文件(JS 常量随宿主文件、ClassicEamsWebVpn.kt)+ 改动 4 处(条目、分支、UA 初值、判型补锚)+ 测试若干。零既有行为修改,零 schema 变更,零依赖变更。**

### 6.2 48 所既有 classic_eams 校不受影响的完整论证(五重)

1. **分支闸门**:新 CaptureBar 分支判据 `type==classic_eams ∧ enableFetch`——schools.json 实测 48 条 classic_eams 条目 `enableFetch` 全部为 false 或缺省(false),只有 NBCC 被本设计置 true。既有校「导入此页」路径逐字节不变(通用 outerHTML → parser)。
2. **协议形态自洽**:48 校全是直链教务页(URL 即数据页,GET 即带 TaskActivity 脚本),通用抓取对它们本来就正确——它们**没有** NBCC 的「门户会话 + POST 才出数据」问题,天然不需要新分支。
3. **判型补锚只在内层**:§3.6 case 加在 `.webvpn.`/`/http/<hex>/` 重写分支内;48 校 URL 无 webvpn 前缀,根本不进该 when。外层 ①a 锚(:402)一字不动。
4. **parser 零改动**:JwClassicEamsParser 及其置信度阶梯(95/90/70/55)、adjudicate 阈值(≥80)、FourSchool 先例回归(NWUPL/上财/湖南师大/南航)全部不动;新共享常量文件是**镜像**而非重构,不产生 parser 行为差。
5. **计数闸与状态位**:NBCC 保持 unverified → supported 计数 350 不变(Schools179CrossValidationTest:186);既有条目 url/type/status 无一改动。

### 6.3 已知空洞与接受理由(如实记账,不掩盖)

- **G2 `/https/<hex>/` 正则空洞本轮保留**:影响面 = 若未来有校**只**能以 `/https/` 形态进通用 fetch 分派(JwFetchProtocol.pick)才暴露;NBCC 链不经过 pick(§2-G2)。放宽正则的代价(GCU/HEBUEE/柳州城市 3 条 zf_new 校被改道)大于收益,拒绝。留档:pc-entry-evidence §6 已存 hex 编解码方法,形态 A 复活时一并修。
- **Q1 桌面 UA 后门户仍无 PC 教务磁贴**(可能形态):分支判据不命中 → 走 hint 分支,用户可手动切移动 UA(开关保留)或按 §1.4 判定规则改配 Q2 条目。失败形态是「清晰提示 + 可重试」,不是错抓。
- **页面结构改版**(正方 EAMS 升级 SPA):候选耗尽 err 分支给明确文案;parser 0 课 confidence=0 由预览层拦住,不落脏数据。此风险与 48 校共担,非 NBCC 新增。
- **wengine 对 WebView 的 URL 重写导致相对 endpoint 解析异常**:候选 POST 失败落 err 分支;`credentials:'same-origin'` 语义在同文档上下文恒成立。若实测发现 rewrite 破坏相对解析(无凭据不可预证),备选形态 = 从当前 `location.pathname` 截 `/eams/` 前缀拼接——Step 8 真实回传驱动,不在本轮写死(避免无证据复杂化)。
- **误报风险(串表单)**:ids 提取限定在课表表单作用域(§3.1-②),fixture `nbcc-entry-page.html` 的干扰成绩表单(§5.1)把该行为锁成测试;Dart 参考实现同教训,双源。

### 6.4 回滚单元划分(Step 9 commit 纪律预告,归 commit-sop)

建议 3 个可独立 revert 的 commit:A)条目 + 判型补锚 + webvpn-urls 对抗件(协议改判自洽单元);B)ClassicEamsWebVpn.kt + CLASSIC_EAMS_FETCH_JS + CaptureBar 分支 + UA 初值 + 单测/invariant(抓取链单元);C)fixtures + parser 测试追加(验证单元,可与 B 同 commit 若耦合紧密)。任一单元 revert 不破坏其余单元编译;A 单独 revert 回到 shuwei 错配态但 B/C 测试仍绿(分支判据引用 TYPE_CLASSIC_EAMS 常量,不引用条目)。

---

## 7. 用户原话 vs 设计行为(逐条 diff,核验拿原话不拿二手文档)

| # | 用户原话(scope.md 锚点) | 本设计如何兑现 | 兑现位点 |
|---|---|---|---|
| 1 | 「查一下我们的 cookie 传递是不是有问题 / 密码错误是不是传的问题」 | 结论:桥接与 cookie 通路无恙(采集包实况:门户登录后 SPA 能打开,失败在 SPA 容器账号体系);真正「传递」缺陷 = 默认移动 UA 把用户送进错误入口。设计上以「入口初值修复 + WebView 会话内人工登录 + 同源 fetch 自动带 cookie」回应,不引入任何自建 cookie 注入 | §0.1 §3.5 §1.2(Q2 同 WebView 登录回跳) |
| 2 | 「调研所有 webvpn 的解决方案」 | 268 裁决全量(POSITIVE 1/INDIRECT 108/NEGATIVE 1/NULL 158,wengine 族 109)→ 四形态 matrix A/B/C/D;本设计 = 形态 B 完整蓝本,A 留档不实现,D 排除有据 | §0.5 §2-G2 §6.3 protocol-matrix 全文 |
| 3 | 「结合调研内容适配 NBCC」 | 调研锁死的三件套直接进设计:参数体模板与 ≤3 候选(Dart buildCourseTableParamsCandidates 同序)、成功判据 `new TaskActivity(`、semesterCalendar 优先隐藏域兜底——全部落 §4 常量表并由 invariant 测试锁跨语言一致 | §3.1-③⑤ §4 §5.3-3 |
| 4 | 「考虑新增一个密码传递器」 | 如实回应:形态 C 先例(ZJXU-CampusHub)存在但对本校非必需——B 已实测免接触凭据可行且 C 反而扩大凭据面(密码进 JS 可读 DOM);本轮明确不做,理由入档防止战役重提 | §0.3 §2-G6 §6 无相关位点 |
| 5 | 「参考 HFUT 当时的经验」 | 三处直接移植:EAMS5_FETCH_JS 骨架(IIFE/守卫/onWiseduResult 复用不改签名)、门户拦截 hint 模式(one.hfut 门户拒绝分支)、HfutPortalEams5WebViewContractTest 的 marker 提取 invariant 方法论、会话失效判据(isEams5LoginRedirect 同型 /login 检查)。同时划清不可照搬边界:EAMS5 是 supwisdom 新式,NBCC 是经典正方,协议层不复用 type | §3.3 §3.4 §4 invariant §6.2-4 |
| 6 | 「没有人用钉钉好吗 / 他明明在网页上也可以用教务账号登录」 | 设计全文零钉钉/authCode/SPA 分支(永久作废,进 §0.1 铁律);人类路径「门户 → 教务管理 → 我的课表 → 教务账号直登」逐段同构映射:Q1=桌面 UA 门户磁贴、hint 文案=学生应用同款动线提示(「请在页面中进入: 课程管理 → 我的课表」)、登录全程 WebView 内人肉输入零接触 | §1.1 §3.1 §3.3 hint §5.2-(d) 反例锁 |

### 设计阻塞开放问题(全部已在文档内给出处置,无一条阻塞 Step 7 开工)

1. Q1/Q2 入口裁决——需学生自愿回传(§1.4 表);Step 7 两分支共用链已做零分支切换成本处理,§5.3-5 测试用 OR 断言不锁死。
2. wengine 相对 endpoint 在 WebView 重写下的解析行为——无凭据不可预证,失败落 err 分支 + §6.3 备选形态留待 Step 8 实测驱动。
3. NBCC 教务页学期 ID 实际取值(279 为学生应用样例)——运行时提取,非设计常量化,fixture 标注合成来源。

---

## 8. 增补设计:课节详情钟点获取(taskTable,2026-10-27 报修驱动)

### 8.1 报修与根因(用户原话锚点)

用户报:「有的课点进去之后是有节次时间的,但是我们没抓到」,凭证 = 大学生心理健康(2班)详情弹层截图(教室 奉化号楼奉6-01阶梯教室 / 节数 3-4节 / 时间 10:15-11:40 / 教师 李焱波 / 起止周 6 8 10 12 14 16 18)。

根因(终局调研 27 仓实锤,致谢已落 664152d7): 经典 EAMS `courseTableForStd!courseTable.action` 响应的 `new TaskActivity(...)` 只编码节次编号(index = D*unitCount+P),**响应体内全校无 HH:MM 钟点**;钟点存在于逐课时的服务端渲染详情页 `taskTable.action?lesson.id=<id>`(openurp 源码实锤: `taskTable()` 装配 `timeSetting` → `TimeSetting → List<CourseUnit> → CourseUnit{indexno, beginAt, endAt}`)。我方形态 B 只抓课表主响应,periods 恒为空表,节次时间列自然缺失——不是解析 bug,是数据源面窄。

### 8.2 终局调研四判定(公开侧,零凭据)

- Q1: GitHub 全检索**无任何公开仓解析过 taskTable.action 响应**——全部命中都是课表页出链形态 `onclick="return bg.Go(this,null)"`(beangle/bui 源码证明 bg.Go = 普通同源 GET 返回 HTML;kissTJU 只拼链接从不解析响应 = 关键负样本)。
- Q2: 弹层时间来自服务端渲染的 taskTable HTML,不是前端 JS 数组(TaskActivity 构造函数字段 = teacher/room/validWeeksBitmap,无钟点映射;asliujinhe/NeuEams、WeCuit fork 双源互证)。
- Q3: 公开侧拿到过钟点的学生工具全部硬编码本地 time_slots 表(ECNU ecnuics.py、CDUTCM),唯一例外 TJU 从 courseTable 行头读官方钟点(tjuse/tju-python)——硬编码违背「校方权威数据」原则,行头钟点在 NBCC 部署被省略(hua-yi-yi fixture 与 duzhaokun123 旁证: 同一 timeSetting 不同模板打印风格),故 NBCC 只能走详情页。
- Q4: 无公开 NBCC 移动弹层样本;钉钉路线永久作废,不展开。

### 8.3 设计形态(复用既有 periods 通路,签名零改动)

抓取链扩展在 `CLASSIC_EAMS_FETCH_JS` 内,主响应解析成功后:

1. 从已渲染课表 DOM 收集课时详情链接的 `lesson.id` 参数(bg.Go(this,null) 的 href,同源 `taskTable.action`)。
2. 逐课时同源 GET(串行,`credentials:'same-origin'`,上限 ~15 次,请求间限速)——每个详情响应提取「节数 + 时间」对(如 3-4节 + 10:15-11:40 → 第3节下界 10:15、第4节上界 11:40)。
3. 跨课时合并推断全节次表: 每节取观测到的最晚 start 与最早 end;覆盖满 unitCount 全部节次才产出,否则如实残缺(见 8.4 降级)。
4. 以既有 `__sleepyBridge.onWiseduResult({ok,data,periods})` 通道回传 `periods = [[node,"HH:MM","HH:MM"],...]`,消费链零改动: JwWebViewLoginScreen.kt:175-189 解析 → :243 onHtmlCaptured → JwImportActivity.kt:823 periodMap 预填 → :851 JwImportDraftPeriod 持久化(位点已实测核验,CQU:189/YETHAN:200-238 为先例)。

### 8.4 三条硬条件(实现闸,缺一不准合)

1. **探针采集闸**: 在拿到一份真实 NBCC taskTable.action 响应样本(大概率需学生自愿回传)之前,**禁写提取 regex、禁造 fixture**。本节只锁链路形态与消费面,正则字面量等实物到位后按 SOP Step 7 补。
2. **强制降级路径**: 任一详情请求失败/无权限(openurp 未选课返回「没有权限」形态)/超时预算耗尽 → 该课时放弃、保留节次编号、periods 不完整时预览层照旧走「请补充节次时间」fallback(现有 UI 承接),**绝不硬编码默认钟点表**伪装校方数据。
3. **跨语言 invariant(SOP 铁律3)**: 节数/时间提取 pattern 的 Kotlin 常量与 JS 字面量逐字符相等,测试锁死;lesson.id 收集 pattern 同理。

### 8.5 爆炸半径

改动全部在 CLASSIC_EAMS_FETCH_JS(仅 NBCC 条目引用)与 ClassicEamsWebVpn.kt 常量区;48 所既有 classic_eams 校走各自条目 enableFetch=false 或原 JS,零波及。回滚 = revert 单 commit。风险账追加: 逐课时 GET 对校方服务器的请求放大(~15 次/导入)已用串行+限速+上限约束,属只读 GET,与人在页面逐条点开弹层等价。

### 8.6 状态

设计定稿,实现**阻塞在 8.4-1 采集闸**——待真实 NBCC taskTable 响应样本(向已回传学生请求自愿采集: 浏览器登录教务 → 点开任意一课详情 → 另开该链接 → 保存页面源码回传,零凭据外泄面)。样本到位后 Step 7 出 regex+fixture+invariant,Step 8 全量验证。


