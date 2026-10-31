# SPEC: Sleepy Agent Debug Console（调试模式 / 超级控制面）

> 状态: 已批准并裁决 · 起草 2026-10-29 · 用户裁决同日（口令 + 其余按功能最强） · 分支: feat/agent-debug-console · 基线: main 2b896ffd
> 需求来源（用户裁决逐条）: 打开调试模式后，所有功能必须能通过 ADB 或其他协议让 Agent（Codex / Claude 等）操作——所有数据操作、所有控制、所有点击、所有拖动滑动、数据注入、消息触发；只要手指能点到的全都可以，手指点不到的（时间、后台事件、协议响应、未渲染数据）调试模式下也必须可以。安装范围=debug 包 + release 隐藏开关；传输=HTTP over adb forward；虚拟时钟=全量；UI 控制=语义操作 + 坐标兜底。

## 1. Objective

给 Sleepy 建立一个**只在调试模式激活时存在**的本地 HTTP 控制面，让任何能执行 adb 的 agent 以 JSON 命令实现对应用的全量驱动：

- 手指能点的：点击、长按、滑动、拖动、滚动、文本输入、系统键，全部走语义定位（testTag / 文案 / 类名），不依赖屏幕分辨率。
- 手指点不到的：虚拟时钟（模拟任意日期时间/周次）、事件触发（通知、上课提醒、流体云、桌面实时卡）、教务协议 mock（WebView 回放任意 HTML/JSON 响应）、数据直读写（课表、课程、作息、提醒、导入草稿、目录 status）、状态快照（UI 树、当前 Activity、DB dump、偏好、日志尾部）。

成功形态：Claude / Codex 拿一份 skill 文档（端点速查表），一条 curl 注入一节课并让 5 分钟后的课真实响铃；一条 curl 把系统时间感知推到第 16 周让周次 UI 全跟随；一条 curl 回放某校教务 HTML 完成无凭据端到端导入验证。

## 2. 能力地图与构建顺序

| 模块 id | 职责 | 依赖 |
|---|---|---|
| bridge | 开关态、鉴权、HTTP 服务器、命令分发、审计日志 | — |
| data-ops | DB/偏好/目录 status 的读写导出 | bridge |
| ui-ops | 语义查找、点击/长按/滑动/拖动/滚动/输入、截图、UI 树 | bridge |
| virtual-clock | 应用级时钟收敛与偏移 | bridge |
| event-trig | 通知/alarm/流体云/实时卡/教务同步触发 | bridge, virtual-clock, data-ops |
| jw-mock | WebView 教务协议 mock 回放 | bridge, data-ops |

构建顺序: bridge → data-ops → ui-ops → virtual-clock → event-trig → jw-mock。后五个模块互不依赖，可并行派 agent。

## 3. 命令面（HTTP 契约草案）

单端口 `tcp:3737`（`adb forward tcp:3737 tcp:3737`，服务器绑 `127.0.0.1`；NanoHTTPD 2.3.1 仅支持 TCP ServerSocket，localabstract 方案经实现核验不可行，2026-10-29 定稿改此），除 `/v1/hello` 外全部要求 `X-Sleepy-Token` 头。响应统一 `{"ok":true,"data":...}` / `{"ok":false,"error":...}`。

| 端点 | 语义 |
|---|---|
| POST /v1/hello | 探活+能力清单+debug 包/release 开关态。无需 token |
| POST /v1/enable | **release 隐藏开关专用**: 仅当 UI 侧连点版本号 7 次进入"等待解锁"态(内存标志, 60s 过期)时接受; 提交口令 `sleepy.is-my.app` → 签发 24h token, 写内存标志位; 口令错 3 次后指数退避防刷。TCP 回环无法核调用者 uid(旧稿 /proc 校验仅 localabstract 可行, 随传输定稿废止)。debug 包跳过此步(编译期常量默认开) |
| POST /v1/clock | `{mode:"offset"|"absolute"|"reset", epochMs 或 dayOffset}` 设置虚拟时钟; GET 读当前虚拟 now + 偏移量 |
| GET /v1/ui/tree | 当前前台 Activity、Compose 语义节点树(testTag/role/text/bounds)、可交互元素清单 |
| POST /v1/ui/find | `{testTag? text? textContains? className? role? index?}` → bounds/可点击性/scroll 可达性 |
| POST /v1/ui/action | `{action:"tap"|"longTap"|"doubleTap"|"swipe"|"drag"|"scroll"|"input"|"back"|"home"|"recents"|"key", selector? x,y? text? direction? amount? durationMs?}` 语义优先, x,y 兜底; 返回动作后 UI 指纹 diff |
| GET /v1/ui/screenshot | PNG base64 (上限降采样) |
| GET /v1/data/tables | 课表全量 + 每表课程/作息 |
| POST /v1/data/table | 增删改课表 / 设默认 |
| POST /v1/data/course | 注入课程(完整 entity 或 ScheduleParser 文本/base64 复用现通路) |
| POST /v1/data/timesetting | 节次时间/作息写入 |
| GET/POST /v1/data/prefs | 首选项全量读写 |
| POST /v1/data/catalog | 按 name 查/改 schools.json 运行时行的 status/type/url (仅 debug 实例可写; release 隐藏模式只读) |
| POST /v1/trigger | `{type:"before_class_alarm"|"fluid_cloud"|"live_card"|"notification"|"sync", ...payload}` 复用并系统化现有 DebugImportReceiver 六个能力 |
| POST /v1/jw/mock | `{school, url_pattern, respond_with: file|b64|html, headers?}` WebView 层拦截回放; 响应体从 app files 目录或 inline 读取 |
| GET /v1/log?since=ms | 应用 Logcat 尾部(仅 Sleepy tag) JSON |
| POST /v1/session/reset | 清全部测试表/草稿/偏移/mock, 一键回到干净态 |

## 4. 技术选型（已实测确认，非猜测）

- 语言/框架: Kotlin；HTTP 服务器采用 **新增依赖 org.nanohttpd:nanohttpd:2.3.1**（2026-10-29 规划期实测证伪了本稿早前"已是依赖"的错误声明——build.gradle.kts 与 libs.versions.toml 均无此库）。按 SPEC 自身纪律"引入新 Gradle 依赖 = Ask first"，此条已经用户同轮"其他的怎么功能强怎么来"裁决豁免：NanoHTTPD 为纯 Java 单 jar、体积极小，是功能最强且成本最低的方案。核验注记：2.3.1 仅支持 TCP ServerSocket（绑 127.0.0.1 回环），localabstract 传输不可行，已随 §3 定稿改为回环 TCP。备选裸 ServerSocket 微服务器仅在依赖引入被 CI/规则阻断时回退。
- 不用 AccessibilityService（用户态要授权、UI 无法全自动开）；语义手势走 `View.performClick()` / 向视图树派发 `MotionEvent`（坐标兜底），Compose 语义定位靠补全的 `Modifier.testTag` + `findViewByPredicate` 遍历。
- Compose 全库 testTag 补全: 一次性给所有交互控件补 `Modifier.testTag(...)`，纳入 ui-ops。
- 虚拟时钟: 新建 `object AppClock : java.time.Clock`，收敛 **58 处** `LocalDate.now()/LocalTime.now()` + `System.currentTimeMillis()` 调用点（实测 33 文件，规划期复核）。改动面大，须有测试锁。
- release 隐藏开关: 设置页（实测落点 AboutScreen.kt:285 版本号文本）连点版本号 7 次进入解锁页 → `POST /v1/enable` 需 adb-local 调用者验证 + 口令 **`sleepy.is-my.app`**（用户 2026-10-29 定令；内置常量，build 时可覆盖，不做每次安装随机——按"功能强"裁决取 agent 可预知的固定口令）→ 签发 24h token, 写内存标志位。默认 release 完全不监听端口；解锁仅在进程存活期有效, 重启即失效, 全程 UI 顶栏显示 "DEBUG UNLOCKED" 红条。

## 5. 安全红线（三条，违反即回退）

1. debug 源集组件在 release APK 中物理不存在；release 路径只有隐藏开关解锁后才绑端口。
2. 服务器只绑 `127.0.0.1` / localabstract；token 校验在分发器统一入口，无 token = 401，无例外；`/v1/data/catalog` 写、`/v1/jw/mock` 在 release 隐藏模式自动降级为只读/禁用（防真实用户被外部 app 借道改教务 URL）。
3. release 解锁不可持久化：不写 SharedPreferences 标志、不通知 Service 重启保活，进程死即归零；用户随时可在解锁页一键立刻失活。
4. 口令 `sleepy.is-my.app` 只作为编译期常量参与 `/v1/enable` 校验，不落任何日志、不进响应体、不写文件；尝试解锁须先在"关于"页连点版本号 7 次，普通用户无入口。

## 6. Testing Strategy

- 单元: AppClock 偏移正确性(冻结时间断言)、命令分发器 schema 校验、release 鉴权拒绝路径。
- 端到端: instrumented 测试跑通每个端点一次真调用(已有 androidTest 设施与 WeekGridMealBreakPixelTest 等真机例)。
- 契约: 每个端点的 request/response JSON schema 固化测试；agent 速查 skill 文档由同一 schema 生成，防文档漂移。

## 7. Boundaries

- Always: 每 commit 跑全量 testDebugUnitTest；token/鉴权改动必有红绿用例；commit 身份与消息遵循 commit-sop。
- Ask first: 引入任何新 Gradle 依赖（唯一豁免：org.nanohttpd:nanohttpd:2.3.1，经 2026-10-29 用户"功能强"裁决预授权，见 §4）；改 .github/workflows；动 app/src/main 业务代码非收敛 Clock 所需的最小范围。
- Never: release 包默认开监听；token 落明文持久化；把 debug 组件混入 release 源集；删用户数据不经 session/reset 显式调用。

## 8. Success Criteria（验收=agent 实测，逐条可证伪）

1. `adb forward` + `curl /v1/hello` 在 debug 包返回能力清单。
2. 一条 curl 注入“现在+2分钟”的课，2 分钟后真实收到课前通知并截图证明。
3. 一条 curl 设虚拟时间到未来某周三，GET /v1/ui/tree 显示周次/今天课程全跟随，回退 reset 复原。
4. 一条 curl tap 到设置页主题切换项（语义 selector），UI 真实变化，无需任何坐标。
5. 一条 curl 注入 mock HTML 后走 jw 导入通路，无凭据完成解析入库。
6. release 包未解锁时端口不监听(nmap/nc 实测拒连)；解锁后 tap 可用；进程被杀重启后端口又拒连。
7. 全量测试套 0 失败；lint 0 警告。

## 9. Open Questions（2026-10-29 用户裁决”其他的怎么功能强怎么来”，逐条结案）

1. 内置口令的值与是否需要每次安装随机 → **已定**：口令 = `sleepy.is-my.app`；不随机，取编译期固定常量（agent 可预知 = 功能强）。
2. release 隐藏开关是否要在未来公开为”诊断模式” → **本期裁决**：不公开，保持纯内部隐藏；功能最大化全部落在 debug 包（debug 无条件全开），release 只给解锁后的操作面子集（catalog/mock 降级）。
3. jw-mock 第一期覆盖到哪个协议族 → **裁决按功能强 = 全覆盖**：拦截层做成 url-pattern 通用回放，协议族无关，qz / zf_new / eams5 / classic_eams / chaoxing / kingo / suda / yethan 等全部可 mock，不做逐族适配。
4. 端口/abstract name 争用 → **第一期裁决**：固定 `tcp:3737` + `localabstract:sleepy-debug`，不做多实例协商；绑定失败在 `/v1/hello` 前的启动日志显式报错，agent 换 forward 端口属使用侧问题。
