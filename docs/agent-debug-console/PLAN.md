# Sleepy Agent Debug Console 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) — or, when the partner chose inline execution or no subagent tool exists, its "Fallback: no subagents" section — to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现只在调试模式激活时存在的本地 HTTP 控制面，让任何能执行 adb 的 agent 以 JSON 命令全量驱动 Sleepy：数据读写、语义/坐标手势、虚拟时钟、事件触发、教务协议 mock。

**Architecture:** 六模块按 SPEC 能力地图顺序 bridge → data-ops → ui-ops → virtual-clock → event-trig → jw-mock。控制台服务器与全部端点处理器放 `app/src/main/java/com/lingion/sleepy/console/`，运行期由 `DebugGate` 单点判活（debug 构建恒开；release 仅隐藏解锁成功后开）——这是 SPEC 红线 1 的落点：release 未解锁时端口完全不绑，既有 debug 源集组件（DebugImportReceiver 等）照旧物理不进 release APK。UI 语义定位不依赖 AccessibilityService，走自维护的 testTag 注册表 + View.performClick + MotionEvent 派发。

**Tech Stack:** Kotlin、NanoHTTPD 2.3.1（唯一新增依赖，SPEC §7 已载用户预授权豁免）、kotlinx-serialization-json、Room、JUnit4。

**Spec:** `docs/agent-debug-console/SPEC.md`（commit 7ba9e5c4 + 传输定稿 amend）

## Global Constraints

- commit 作者固定 `lingion <lingion@hrbeu.edu.cn>`；尾注禁任何 Co-Authored-By；关联只写 `ref #N`。
- 每任务 commit 前跑 `./gradlew :app:testDebugUnitTest` 全量 0 失败（真跑，必要时 `--rerun-tasks`）。
- 除 `org.nanohttpd:nanohttpd:2.3.1` 外禁新增任何 Gradle 依赖。
- 禁 AccessibilityService；token/口令禁明文持久化、禁进日志。
- 服务器只绑 `127.0.0.1:3737`；`adb forward tcp:3737 tcp:3737`。
- 口令 `sleepy.is-my.app`（编译期常量，`console.enablePassphrase` buildConfigField 可覆盖）；token 24h。
- 响应统一 `{"ok":true,"data":...}` / `{"ok":false,"error":"..."}`；除 `hello`/`enable` 外无有效 token = 401。
- main 业务代码改动仅限：Clock 收敛、dbgTag 包装、mock 拦截注入点、版本连点——超出即 Ask first。

## Review Focus

1. **token/口令泄漏面**：审计日志与响应体必须只记端点名不记头/体原文（T1 测试锁）。
2. **release 降级完整性**：解锁后 `/v1/data/catalog` 写与 `/v1/jw/mock` 必须 403，且 UI 红条必须显示（T7 测试锁）。
3. **时钟收敛漏网**：残留裸 `LocalDate.now()` 会让部分 UI 不跟随虚拟时间（T4 契约扫描锁）。
4. **手势派发线程**：MotionEvent 必须主线程派发且对 ComposeView 生效（T3 instrumented 锁）。
5. **mock 泄漏真实网络**：命中 pattern 的请求绝不允许放行到真实教务站（T6 锁 intercept 返回非 null 分支）。

---

### Task 1: bridge — 服务器、鉴权、分发、审计

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/console/DebugGate.kt`（进程态：`isActive`、`isReleaseUnlocked`、`unlockArmedAt`、失效倒计时）
- Create: `app/src/main/java/com/lingion/sleepy/console/TokenStore.kt`（内存 token，24h，口令校验 + 错 3 次指数退避）
- Create: `app/src/main/java/com/lingion/sleepy/console/Envelope.kt`（`ok(data)` / `err(code,msg)` JSON 字符串工具）
- Create: `app/src/main/java/com/lingion/sleepy/console/CommandRouter.kt`（path → handler 表，handler 接口 `suspend fun handle(req: ConsoleRequest): String`）
- Create: `app/src/main/java/com/lingion/sleepy/console/DebugConsoleServer.kt`（NanoHTTPD 子类，绑 127.0.0.1:3737，异步转 sync 调 router，统一 401/404/500 出口）
- Create: `app/src/main/java/com/lingion/sleepy/console/AuditLog.kt`（环形缓冲 200 条：时间+method+path+token 后 4 位+ok，不记 body）
- Create: `app/src/main/java/com/lingion/sleepy/console/ConsoleBootstrap.kt`（`fun onCreate(app: Application)`：`if (BuildConfig.DEBUG) start()` 由 debug 源集 StartupProvider 调；release 仅解锁后调）
- Create: `app/src/debug/java/com/lingion/sleepy/debug/DebugConsoleProvider.kt` + 修改 `app/src/debug/AndroidManifest.xml`（ContentProvider 自动触发 bootstrap）
- Modify: `app/build.gradle.kts`（debugImplementation + implementation `org.nanohttpd:nanohttpd:2.3.1`；`console.enablePassphrase` buildConfigField 默认 `"sleepy.is-my.app"`）
- Test: `app/src/test/java/com/lingion/sleepy/console/BridgeUnitTest.kt`

**Interfaces:**
- Produces: `DebugGate.isActive: Boolean`、`TokenStore.issue(): String?`、`TokenStore.verify(token: String?): Boolean`、`CommandRouter.register(path: String, method: String, requiresToken: Boolean, handler: suspend (ConsoleRequest) -> String)`、`ConsoleRequest(body: String, query: Map<String,String>, params: Map<String,String>)`。后续任务全部只经 `CommandRouter.register` 挂端点。
- `hello` 返回能力清单 = 已注册 path 列表 + `{debugBuild, unlocked, active}`。

- [ ] Step 1: 写失败测试——TokenStore：口令对签发/错累计 3 次后即使对也退避拒发；verify 过期 token 为 false。Envelope：`err(401,...)` 串形状。AuditLog：记录不含请求体子串。
- [ ] Step 2: `./gradlew :app:testDebugUnitTest --tests "*console*" -v` 确认编译失败（类不存在）。
- [ ] Step 3: 实现五个类 + router + server + bootstrap + provider + 依赖行。server 的 serve() 里：`/v1/hello`、`/v1/enable` 免 token，其余 `DebugGate.isActive` 且 token verify 失败 → 401 envelope。
- [ ] Step 4: 重跑 focused 测试绿；再跑 `./gradlew :app:assembleDebug` 确认 provider 合并进 manifest。
- [ ] Step 5: 全量 `testDebugUnitTest` 0 失败后 commit `feat(console): bridge — 鉴权分发与 HTTP 服务器`。

### Task 2: data-ops — tables / course / timesetting / prefs / catalog

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/DataOpsHandlers.kt`
- Create: `app/src/main/java/com/lingion/sleepy/console/CatalogOverrideStore.kt`（运行时 schools.json 行覆盖层：内存 map + 持久化到 `filesDir/console_catalog_overrides.json`；读取链在 `JwImportViewModel` 加载 assets 后套用）
- Modify: `app/src/main/java/com/lingion/sleepy/data/jw/JwImportViewModel.kt:74` 附近（一行套用覆盖层）
- Test: `app/src/test/java/com/lingion/sleepy/console/DataOpsHandlerUnitTest.kt`；`app/src/androidTest/.../DataOpsConsoleAndroidTest.kt`

**Interfaces:**
- Consumes: Task1 `CommandRouter.register`、`SleepyApp.get().repository / .importDraftRepository`、`AppPrefs`（SharedPreferences 文件 `sleepy_prefs` 全量导出/写入）。
- Produces: 端点 `GET /v1/data/tables`（表+课程+作息聚合 JSON）、`POST /v1/data/table`（`{op:"create"|"update"|"delete"|"setDefault", ...TimeTableEntity 字段}`）、`POST /v1/data/course`（`{tableId, courses:[CourseEntity]}` 或 `{text|b64}` 走 `ScheduleParser` 现通路）、`POST /v1/data/timesetting`（PeriodTable 增改删，复用 `repository.savePeriodTable`）、`GET/POST /v1/data/prefs`、`POST /v1/data/catalog`（`{op:"get"|"set", name, status?, type?, url?}`；release 解锁态 `set` → 403 envelope）。

- [ ] Step 1: JVM 失败测试：catalog 覆盖层 set/get/持久化重载；prefs 导出 map 形状；`table` op 参数校验缺字段返回 err 不抛异常。
- [ ] Step 2: 跑红（缺实现）。
- [ ] Step 3: 实现 handlers + 覆盖层 + JwImportViewModel 一行接入；`catalog set` 的 release 降级判 `DebugGate.isReleaseUnlocked`。
- [ ] Step 4: androidTest 真 Room：注入课程→`/v1/data/tables` 读出含该课；设默认→repository.getDefaultTable 变。
- [ ] Step 5: 全量测试绿，commit `feat(console): data-ops — 课表/课程/作息/偏好/目录运行时读写`。

### Task 3: ui-ops — 语义定位、手势、截图、UI 树

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/console/ui/DbgTag.kt`（`Modifier.dbgTag(tag: String)` = `testTag(tag)` + `onPlaced` 注册到 `TagRegistry`：tag → View 引用 + bounds）
- Create: `app/src/main/java/com/lingion/sleepy/console/ui/TagRegistry.kt`（弱引用表 + `fun find(selector): List<FoundNode>`）
- Create: `app/src/main/java/com/lingion/sleepy/console/ui/Gestures.kt`（`tap/longTap/doubleTap/swipe/drag/scroll/input` 主线程派发；优先 `view.performClick()`，坐标兜底用 `dispatchTouchEvent(MotionEvent.obtain(...))`；back/home/recents 走 `activity` 的 onKeyDown / `Intent` 语义）
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/UiOpsHandlers.kt`（tree / find / action / screenshot）
- Modify: 全库交互控件补 `dbgTag`（与各屏文件同批改，每屏一个 commit 内完成）
- Test: `app/src/test/java/com/lingion/sleepy/console/UiSelectorUnitTest.kt`；`app/src/androidTest/.../UiOpsConsoleAndroidTest.kt`

**Interfaces:**
- Consumes: Task1 router；前台 Activity 引用经 `ConsoleBootstrap` 注册的 `ActivityTracker`（`app/src/main/java/com/lingion/sleepy/console/ActivityTracker.kt`，Application lifecycle callbacks）。
- Produces: `GET /v1/ui/tree`（当前 Activity 名 + 注册表全部 tag/bounds/可点击 + View 树文字 dump）、`POST /v1/ui/find`（selector → bounds/clickable/reachable）、`POST /v1/ui/action`（selector 或 x,y；返回执行前后 UI 指纹 diff：tag 集 + 焦点 + 文本哈希）、`GET /v1/ui/screenshot`（PixelCopy 失败回退 drawingCache，base64 PNG 长边 ≤1568）。

- [ ] Step 1: JVM 失败测试：selector 匹配优先级（testTag > text > textContains > className > role > index）、多命中取 index、UI 指纹 diff 结构。
- [ ] Step 2: 跑红。
- [ ] Step 3: 实现四个 UI 类 + ActivityTracker + handlers；screenshot 降采样。
- [ ] Step 4: 给设置屏、导入屏、课表屏主交互控件补 `dbgTag`（约 30-50 处，逐屏编译过即提）。
- [ ] Step 5: androidTest：Compose 规则下 `dbgTag("settings_theme")` 节点 performClick 后 UI 状态变化可断言；无坐标参与。
- [ ] Step 6: 全量绿，commit `feat(console): ui-ops — 语义手势与 UI 树/截图`。

### Task 4: virtual-clock — AppClock 收敛 + /v1/clock

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/util/AppClock.kt`（`object AppClock : java.time.Clock()`，`Instant.now()` 等价物 = 真实 now + offsetMs；`setOffset(ms)` / `setAbsolute(epochMs)` / `reset()`，纯内存）
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/ClockHandler.kt`
- Modify: 33 文件 58 处调用点：`LocalDate.now()` → `LocalDate.now(AppClock)`、`LocalTime.now()` → `LocalTime.now(AppClock)`、`System.currentTimeMillis()`（业务判定处）→ `AppClock.millis()`——widget 刷新/闹钟计算/课程状态判定全在内
- Test: `app/src/test/java/com/lingion/sleepy/util/AppClockTest.kt`；`ClockContractScanTest.kt`（契约：扫描 `app/src/main/java` 全部 `.kt`，裸 `LocalDate.now()`/`LocalTime.now()` 无参调用必须 0 处，AppClock.kt 自身除外）

**Interfaces:**
- Consumes: Task1 router。
- Produces: `AppClock.millis(): Long`、`AppClock.offsetMs: Long`；端点 `POST /v1/clock` `{mode:"offset"|"absolute"|"reset", epochMs?|dayOffset?}`、`GET /v1/clock` → `{virtualNowEpochMs, offsetMs, realNowEpochMs}`。

- [ ] Step 1: 写失败测试：setAbsolute 后 `AppClock.date()` 返回目标日；offset 后 millis 差正确；契约扫描测试先跑证明现状 58 处全红。
- [ ] Step 2: 跑红。
- [ ] Step 3: 实现 AppClock + ClockHandler。
- [ ] Step 4: 分 4-6 批（每批 6-10 文件）改调用点，每批跑受影响 focused 测试；契约扫描逐批从红到最终全绿。
- [ ] Step 5: 全量绿，commit `feat(clock): AppClock 收敛 58 处时间源 + /v1/clock 端点`。

### Task 5: event-trig — /v1/trigger 系统化 DebugImportReceiver 六能力

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/TriggerHandler.kt`
- Create: `app/src/debug/java/com/lingion/sleepy/debug/DebugTriggers.kt`（从 DebugImportReceiver 抽六能力为 `object`：`restoreAndCleanup / deleteNonDefaultTables / clearFluidNotification / testBeforeClass / scheduleBeforeClassNow / addSoonestCourse`；receiver 改为薄壳转调）
- Test: `app/src/androidTest/.../TriggerEndpointAndroidTest.kt`

**Interfaces:**
- Consumes: Task1 router、Task4 `AppClock`（"课前 N 分钟"判定用虚拟时间）。
- Produces: `POST /v1/trigger` `{type:"before_class_alarm"|"fluid_cloud"|"live_card"|"notification"|"sync"|"import_helper", ...payload}`——payload 直接透传原 receiver extras 语义（courseName/room/startTime/notifyEpoch/classEpoch/minutes）。注意：能力体在 debug 源集，release 解锁态调用 → 403（`DebugGate.isReleaseUnlocked` 判定），与红线 1 一致。

- [ ] Step 1: androidTest 失败例：注入 `addSoonestCourse(minutes=2)` 后 DB 出现虚拟时间 +2 分钟的课。
- [ ] Step 2: 抽六能力到 DebugTriggers，receiver 转调；TriggerHandler 挂端点（token 后按 type 分派，debug 才注册 handler）。
- [ ] Step 3: 测试绿（真机通知到达以 Success Criteria 2 的人工验收为准，此处断言 service 启动与 DB 态）。
- [ ] Step 4: 全量绿，commit `feat(console): event-trig — 复用 DebugImportReceiver 六能力`。

### Task 6: jw-mock — WebView 协议回放

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/console/MockRuleStore.kt`（规则：urlPattern(正则) → {status, headers, bodyBytes}；持久化 `filesDir/console_jw_mocks.json`；`intercept(url): WebResourceResponse?`）
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/JwMockHandler.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt`（WebViewClient `shouldInterceptRequest` 头部插一行 `MockRuleStore.intercept(url)?.also { return it }`）
- Test: `app/src/test/java/com/lingion/sleepy/console/JwMockRuleUnitTest.kt`

**Interfaces:**
- Produces: `POST /v1/jw/mock` `{school?, url_pattern, respond_with:"file"|"b64"|"html", body, content_type?, status?}`（file 从 `filesDir` 内读，禁 `../` 穿越）；`DELETE /v1/jw/mock`；规则注入点函数 `MockRuleStore.intercept(requestUrl: String): WebResourceResponse?`。release 解锁态整端点 403（SPEC 红线 2）。

- [ ] Step 1: 失败测试：pattern 命中返回构造 response（body/status/headers 对）；未命中返回 null；`respond_with=file` 拒绝 filesDir 外路径；空规则 intercept 恒 null（Review Focus 5 锁）。
- [ ] Step 2: 跑红 → 实现 → 跑绿。
- [ ] Step 3: 接线 JwWebViewLoginScreen 一行拦截点；androidTest：注入 mock HTML 后 `JwWebView` 加载该 pattern 拿到 mock 体并走通解析入库（用现有 fixture HTML 一份）。
- [ ] Step 4: 全量绿，commit `feat(console): jw-mock — WebView 全协议族 url 拦截回放`。

### Task 7: release 隐藏开关 — 连点解锁、红条、降级面

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/AboutScreen.kt:285`（版本文本 `Modifier.dbgTag("about_version").clickable` 连点计数：7 次内连续 → `DebugGate.armUnlock()`，60s 窗口）
- Create: `app/src/main/java/com/lingion/sleepy/console/DebugBanner.kt`（`@Composable DebugModeBanner()`：`isReleaseUnlocked` 时顶栏红条 "DEBUG UNLOCKED" + 一键失活按钮；App.kt 根 Scaffold 接入一行）
- Modify: `ConsoleBootstrap.kt`（release：仅 `armUnlock` 后等待 `/v1/enable`；成功后 `start()`；失活 = 停服清 token）
- Test: `app/src/test/java/com/lingion/sleepy/console/ReleaseGateUnitTest.kt`

**Interfaces:**
- Consumes: Task1 `TokenStore`（enable 走它：口令对且 `DebugGate.unlockArmed` 才签发）、Task3 `dbgTag`。
- Produces: `POST /v1/enable` `{passphrase}` → `{token, expiresAt}`；未 armed/口令错/退避中 → err。debug 构建 `/v1/enable` 返回 "already active"。release 解锁态 `DebugGate.isReleaseUnlocked=true` 驱动 DataOps catalog 写 403（T2 已留判）与 Trigger（T5）/JwMock（T6）403。

- [ ] Step 1: 失败测试：未 armed enable → err；armed+对口令 → token；armed 过期 60s → err；失活后 isActive=false 且 token 失效；连点计数 6 次不触发 armed、7 次触发（helper 纯函数化便于 JVM 测）。
- [ ] Step 2: 跑红 → 实现（计数逻辑放 `app/src/main/java/com/lingion/sleepy/console/UnlockTapCounter.kt` 纯函数）→ 绿。
- [ ] Step 3: AboutScreen 接入连点 + 红条 composable；androidTest 眼验红条出现/消失截图。
- [ ] Step 4: 全量绿，commit `feat(console): release 隐藏解锁与降级红线落地`。

### Task 8: session/reset、/v1/log、agent 速查文档与总验收

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/SessionHandler.kt`（清测试表/草稿/时钟偏移/mock/覆盖层/prefs 恢复默认之外全留；判据：仅删 `name.startsWith("console:")` 前缀表 + ImportDraft 全清 + 各 reset）
- Create: `app/src/main/java/com/lingion/sleepy/console/handlers/LogHandler.kt`（读自身 AuditLog 环形缓冲 + `AppLog` 尾部内存环）
- Modify: `app/src/main/java/com/lingion/sleepy/util/AppLog.kt`（若不存在则建：Application 级 `Log` 写入时同步进 200 条内存环）
- Create: `docs/agent-debug-console/AGENT-SKILL.md`（端点速查表，由 CommandRouter 注册表在 androidTest 里导出比对，防文档漂移）
- Create: `app/src/androidTest/.../ConsoleAcceptanceAndroidTest.kt`（SPEC §8 七条逐条：curl 由 adb shell 外执行的两条标记 manual，其余自动化）
- Test: 上述

- [ ] Step 1: 失败测试：reset 后虚拟偏移=0、mock 规则空、`console:` 前缀表清零、非前缀表不动。
- [ ] Step 2: 实现两 handler + 内存日志环。
- [ ] Step 3: AGENT-SKILL.md 与注册表一致性测试（每个注册 path 必须在文档出现，反向亦然）。
- [ ] Step 4: 验收测试类跑通自动化 5 条；剩余 2 条（真机通知、release 拒连实测）写入文档 manual checklist，装 debug 包 + release 包各跑一遍并截图存 `docs/agent-debug-console/evidence/`。
- [ ] Step 5: 全量测试 + `assembleRelease` 双包验证 release 未解锁端口拒连，commit `feat(console): session/log + agent 速查与验收档案`。

---

## 执行备注

- 任务 1-4 严格串行（后续全依赖 router/AppClock）。Task 2、3、5、6 在 Task 1 后可并行派 agent（文件不重叠；JwImportViewModel/JwWebViewLoginScreen 各有唯一属主）。
- 设备验证一律 `com.lingion.sleepy.debug` 包名（memory 教训），装机走 hw-adb-install 且需用户明示。
- 每任务完成汇报附「用户原话 vs 实现行为」对照行。
