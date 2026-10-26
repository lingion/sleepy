# fix — UCAS 入口 URL 改指 SEP 登录门户 (2026-09-09)

## 修改
- `app/src/main/assets/schools.json` (line 1450)
  - 旧: `"url": "https://xkgo.ucas.ac.cn:3000/course/personSchedule"`
  - 新: `"url": "https://sep.ucas.ac.cn/"`
- `app/src/test/java/com/lingion/sleepy/data/jw/Schools179CrossValidationTest.kt`
  - 新增契约测试 `UCAS entry URL must point at SEP auth gateway (not xkgo schedule)`,锁死回归。

## 选定 SEP / 的理由
1. **不指向 xkgo**:xkgo 任意 URL 未登录均返 200 Error 页(`登录失败!`),实测覆盖 `/`、`/login`、原 URL 三条路径全部不可用。
2. **XRW-safe**:SEP 根路径不被 filter 保护,带/不带 XRW 同为 200(probes.md 实测 3)。WebView 强制注入的 XRW 头不会触发 401 JSON。
3. **登录页面可用**:body 标题 "SEP 教育业务接入平台",登录表单可见,用户可在此直接登录。
4. **不动 SEP XRW 链路**:SEP 根登录后,服务器 302 默认 loginFrom=/appStore → /appStore 被 XRW 拦截(因 5)→ 401 JSON。这部分由独立分支 `fix/issue-18-sep-xrw-login` 修,本分支不重复实现。

## 与 fix/issue-18-sep-xrw-login 的关系(评估结论)

**结论:本分支不依赖该分支,但两者互补。**

| 场景 | 仅本分支 | 仅 sep-xrw 分支 | 两者合并 |
|------|----------|------------------|----------|
| 当前 main 行为 | (xkgo Error 页 → click → 401 JSON) | (xkgo Error 页 → click → SEP 登录页可走 → 但入口仍 Error) | (SEP 登录页 → login → /appStore XRW 剥离 → 跳 xkgo) |

**逐项说明**:
- 本分支独立价值:用户进 App 看到的首屏从"登录失败"变成"SEP 登录页"——首屏可见性修复。
- sep-xrw 分支独立价值:用户点"请重新登录"链接不再被 401 拦截——点击可达性修复。
- 两者合并价值:完整 SEP SSO 链路 + 落 xkgo 抓课表。

## 不做的事
- 不动 JwWebViewLoginScreen.kt 的 `JwWebView(url = school.url)` 入口逻辑——保持现有"学校 url 即首屏 url"的设计,只是把 UCAS 这条的 url 改对。
- 不改 TYPE_UCAS 协议类型或 JwUcasParser / UcasDetailFetch——已有适配(v1.0.52 闭环,issue #18 reporter 确认)。
- 不动 5.5 致谢:本分支调研触达的 URL 集为 0 新仓库(xkgo/sep 都不是 GitHub 第三方项目),LicenseScreen 既有的 UCAS 致谢(`ldiex/UCAS_Course_Schedule_Convertor` 等 4 仓)维持原状。
- 不 merge `fix/issue-18-sep-xrw-login`(用户明示批准才动)。
- 不 push / 不打 tag / 不关 issue(用户明示批准才动)。