# BJTU 现状代码阅读 (Step 5)

> 分支 adapt/bjtu-jw (base=main a6e2cac) · 适配前快照, 全部路径相对 repo 根

## 数据流 (导入链路)

```
SchoolsScreen → JwImportActivity (school = JwSchoolInfo from schools.json)
  → JwWebViewLoginScreen(school)
      · JwWebView loads school.url; __sleepyBridge JS 桥 (WiseduBridge.onWiseduResult)
      · 点"捕获": type 分发:
          wisedu/neu/cqu/whut/chaoxing → 专属 *_FETCH_JS (evaluateFetchWithTimeout)
          eams5 → EAMS5_FETCH_JS / EAMS5_AHU_FETCH_JS (prefix 替换)
          zf_new / URL 含 /jwglxt/|/kbcx/|WebVPN /http/<hex>/ → ZF_NEW_FETCH_JS
          其余 → captureWithRetry (DFS frame 抓取页面 HTML)
      · fetch JS 回调 {ok:true, data, periods:[{node,start,end}]} → handleWiseduResult
          → onHtmlCaptured(data, school, periods)
  → JwImportViewModel.tryAllParsers(html, declaredType=school.type)
      → JwParserRegistry.selectBest: FACTORIES[type] 显式分发 (0 课回退通用裁决)
  → CourseEntity 落库
```

## 落点清单 (BJTU 适配要动的文件)

| 文件 | 现状 | BJTU 改动 |
|------|------|-----------|
| `app/src/main/assets/schools.json` | 181 条, idx8=北京化工大学, idx9=北京理工大学 | idx9 插入北京交通大学 (sortKey=B, sortKeyFull=beijingjiaotongdaxue, type=bjtu, aliases=[bjtu,北交大], url=https://aa.bjtu.edu.cn/) → 182 条 |
| `app/src/test/resources/jw/schools.json` | 与 assets 同步 (md5 相同) | cp 同步 |
| `JwProtocol.kt` | 28 个 TYPE_* 常量 + ALL_TYPES + displayName + category | 新增 TYPE_BJTU="bjtu" + 显示名"北京交通大学" + category="other" + ALL_TYPES 追加 |
| `JwParserRegistry.kt` | TYPE_PRIORITY (28) + FACTORIES (28) | TYPE_BJTU 优先级 (窄锚点, 拟 21, 与 seu/zju 档同带) + FACTORIES → JwBjtuParser |
| `JwImportViewModel.kt` | isRoutable 走 FACTORIES 自动 | 无改动 (自动路由) |
| `JwWebViewLoginScreen.kt` | 7 个 type 专属 FETCH_JS + 通用抓取兜底 | 新增 BJTU_FETCH_JS + `if (school.type == TYPE_BJTU)` 分支 |
| `SchoolsJsonConsistencyTest.kt` | declared 常量集 27 项 (静态列表) | 补 JwProtocol.TYPE_BJTU (否则 `every school type is a declared protocol constant` 红) |
| `LicenseScreen.kt` | perSchoolEntries 30 校卡 | 新增"北京交通大学 BJTU"卡 (24 条致谢) |
| `AboutLicenseAttributionTest.kt` | PER_SCHOOL_ATTRIBUTIONS ~60 tokens | 新增 24 条 BJTU Attribution tokens |
| 6 语 strings.xml about_license_body | 已含 60 校致谢 tokens | 6 语各追加 24 条 (tokens 与 LicenseScreen/测试 1:1) |
| `app/src/test/resources/jw/fixtures/` | 各协议 fixture | 新增 bjtu-stuschedule.html / bjtu-schedule.html (公开仓真实数据形状) |

## JwParser 契约 (BJTU parser 要实现的)

```kotlin
class JwBjtuParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse>   // JwCourse(name, room, teacher, day, startNode, endNode, startWeek, endWeek, type 0/1/2)
    override fun confidence(): Int                       // 锚点: /course_selection/ + 星期一 + 第N节
    override fun matchedFeatures(): List<String>
}
```

## 既有周次/单双周基建 (禁重复造轮)

- [JwCourse.type] 0=每周 1=单周 2=双周 — 表达能力与 BJTU 单双周后缀对齐
- JwUcasParser.splitWeekRuns: 有洞周次集合 → 多段 (每周/单/双), 逻辑可复用思路但 UCAS 输入是周次集合, BJTU 输入是文本文法, 各自实现
- JwParity.kt — 待读 (周次/单双周工具)
## 登录态检测基建

- FrameCaptureStatus.SESSION_EXPIRED (通用抓取路径)
- fetch JS 路径: JS 端检测 + err 回调, handleWiseduResult 显示 err

## 测试基建

- JwParserRegistry.selectBest(html, declaredType) — 测试可显式 declaredType="bjtu"
- fixture 目录 app/src/test/resources/jw/fixtures/ (bjtu fixtures 放这里)
- AboutLicenseAttributionTest ALL_RELEASED_LOCALES = values/values-zh-rCN/values-zh-rTW/values-en/values-ja/values-es
