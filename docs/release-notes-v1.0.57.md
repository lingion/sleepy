# Sleepy v1.0.57

> Release notes covering the user-visible changes after the v1.0.56 baseline through the current release candidate.

## Detailed change inventory and references

This section expands the grouped summary into the concrete user-facing work shipped in this range. References are included only where the repository contains an explicit PR or issue reference; unreferenced items are not assigned invented numbers.

### Timetable, period tables, irregular times, and schedule data

- **Irregular and edge-period courses ([issue #23](https://github.com/lingion/sleepy/issues/23)).** Added per-card editing for non-standard periods and times, including leading/trailing edge slots such as period 0 and period N+1. Grid capsules are positioned using real minute proportions; `effectiveCourseTime` is the single effective-time contract; edge slots are excluded from normal-node normalization; Add Course, rendering, notifications, widgets, imports, and exports now preserve the same time semantics. The point-selection crash and edge-node/`ownTime` hand-off were fixed.
- **Mixed-duration period tables ([issue #23](https://github.com/lingion/sleepy/issues/23)).** Added duration assignments, duration groups mirroring break groups, inference from manual rows, classification of inferred duration/break groups, preservation of edge rows during automatic synchronization, and lossless manual ↔ automatic round-tripping. Imported rows can seed the live smart-period configuration without discarding user edits.
- **Independent/owned period tables ([issue #40](https://github.com/lingion/sleepy/issues/40)).** Added Room v7 period-table ownership and binding data, effective-table reads across grid/notifications/widgets, binding-aware editor routing, atomic metadata writes, undo snapshots, bound-table discovery, and three-way conflict resolution: edit only this table, create an independent table, or synchronize the shared table. Rebinding no longer overwrites the original table; cancelled new-table edits no longer leave empty shells; old-format parsing and preview omissions were fixed.
- **Holiday makeup mapping ([issue #44](https://github.com/lingion/sleepy/issues/44)).** Replaced the one-off holiday toggle with explicit holiday-to-target-date mappings, a tree-style settings UI, per-row editing, in-place dropdown behavior, Material 3 menus, correct empty-row handling, same-shape menu styling, and date-aware highlighting. The mapping is consumed by the main timetable, Today, WeekGrid, widgets, reminders, and timetable deletion cleanup. A mapped holiday remains active instead of being dimmed as a non-class day; saving refreshes all consumers.
- **Week and day selection behavior ([issue #57](https://github.com/lingion/sleepy/issues/57)).** Restoring a timetable no longer allows Pager callbacks to overwrite the restored week. When there are no remaining classes, the schedule and widgets can select the nearest busy day, with a configurable multi-day window and holiday dimming. The home header remains week-only and is not rewritten by that selection. Next-week display after the current week is supported.
- **Course list and visual schedule fixes.** Week view hides genuinely empty course days, course arrangements are separated by location, new period information is reused in course lists, and the undo/cancel-rebind capsules are available in the relevant flows.

### Academic imports and diagnostics

- **Import-mode restoration ([PR #54](https://github.com/lingion/sleepy/pull/54)).** Restored the explicit academic-import choices for new, overwrite, append, and existing-timetable paths, including the complete five-mode flow and correct writes to the real owning period table.
- **New Qingguo NTSS protocol (`cf_new`).** Added WebView-based weekly parallel fetch and merge, protocol metadata registration, routing gates, a sanitized regression fixture, and first-school coverage for Jiangxi University of Chinese Medicine. Unsupported envelopes fail explicitly rather than being treated as generic Qingguo pages.
- **XJU postgraduate timetable shape B.** Reworked parsing for the time-column + Chinese-period + rowspan-grid shape, with frame capture through parser coverage and regression fixtures.
- **Additional school coverage.** Added or expanded Northwest University of Political Science and Law, Shanghai Lixin (`classic_eams`), Kunming University of Science and Technology (`kust`), Guangdong Neusoft University (`nuit`), and Beihang University’s new `byxt` portal. The directory grows from 340 to 345 schools. The repository also contains explicit PR #50/PR #51 merge evidence for the XJU/`cf_new` integration line and the four-school adaptation line; no additional PR number is inferred for the school-specific commits whose subjects contain none.
- **Full diagnostic export.** Import failures now offer a unified AlertDialog with an export action. The export captures page context, request headers, all frames, cookies, storage, links, download bodies, logs, and an index file. The nine-step pipeline displays the active stage and percentage, uses an atomic output flow, and keeps the dialog action buttons on one row where space permits.
- **Bound-period import correctness.** Imports validate and write against the actual owning period table, avoiding false “period 1 time is empty” errors and incorrect date-field highlighting.
- **Non-standard time exchange ([issue #55](https://github.com/lingion/sleepy/issues/55)).** ICS, shared text, and WakeUp JSON preserve custom `ownTime` values in both directions. CSV and HTML plain-text import retain and validate irregular times instead of silently normalizing them.

### Reminders, notifications, and vendor capabilities

- **Previous-evening preview ([PR #48](https://github.com/lingion/sleepy/pull/48)).** Added a dedicated tomorrow preview with its own switch and time. Reminder UI uses a single parent card with expandable child settings; today summaries and tomorrow previews use separate copy; no-class tomorrow text is generated from tomorrow’s data. Six-language strings and contributor acknowledgement are included.
- **Background reliability.** Added a factual reliability snapshot, vendor capability adapters, settings fallbacks, an explanatory diagnostics card with a settings route, and explicit ROM-background boundaries. Standard notification fallback and experimental reminder-tag behavior are contract-tested.
- **Android 16 live updates.** Course reminders can use the official `ProgressStyle` transport. Android 16 promoted-ongoing authorization is probed as `allowed` / `not allowed` / `not applicable`; older APIs return unavailable rather than a false permission result. Boot and upgrade recovery restore reminders and remove orphaned alarms after timetable/course deletion.
- **Vendor live-card path.** Added a unified `CourseLiveCardState` and renderer path for Xiaomi, vivo, Meizu, OPPO, Huawei, and Honor, with the Fluid Cloud service routed through the renderer and audit contracts. Private vendor authorization states remain `UNKNOWN` when the OS does not expose verifiable capability.

### Widgets and OEM integration

- Added Android 15 no-data picker previews for all 13 widget variants, localized provider labels/descriptions, a global OEM capability registry, Xiaomi AppVault metadata, vivo Atomic Component declarations, and lock-screen category declarations.
- Today/TwoDay metadata now keeps time and location on independent half-width single lines; WeekGrid classroom badges stay inside a reserved footer band even on very short cards.
- Widgets share holiday dimming and nearest-busy-day selection with the main schedule. The target-date label is normalized, horizontal status-bar behavior is corrected, and the vendor verification matrix distinguishes code support from device evidence.

### Navigation, Material 3, settings, and presentation

- Migrated the custom 14-screen overlay stack to official typed AndroidX Navigation 3 / Compose 2.10.1 routes and Material transitions while preserving the selected tab when returning from child pages.
- Completed the official Material 3 color/component migration: the custom `SleepyTheme.colors` bridge and obsolete widget/theme defaults are removed, Compose UI reads the official color scheme, and modal/button/card/switch styling follows official components.
- Split Appearance from timetable-display settings and added a dedicated period-header settings page. Added legacy, horizontal, vertical, and three-line layouts; period-number styling; “Period X” display; hanging-position controls; live preview; shared geometry; and adaptive typography used by the app grid and WeekGrid widgets.
- Reorganized general settings into five stable groups: timetable display, widgets, screen/navigation, language, and laboratory features. Custom theme saves apply immediately across the app and widgets, including newly created themes.
- Fixed predictive-back and splash behavior to avoid misleading shrinking previews, dark-mode flashes, white return surfaces, centered-logo regressions, and residual splash frames. Update notices are version-scoped and appear consistently in About, Mine navigation, bottom bar, floating Dock, and NavigationRail.
- Added the missing period-header translations for all supported locales and restored the required settings-string parity.

### Reference index

- **Issues:** #23 (irregular/edge periods and mixed duration), #40 (independent/owned period tables), #44 (holiday makeup mapping), #55 (custom-time import/export round-trip), #57 (restored-week flicker).
- **Pull requests:** #48 (previous-evening tomorrow preview and reminder redesign), #50 and #51 (XJU/`cf_new` integration line), #52 (nearest busy day in widgets), #54 (academic import modes), and the repository’s `origin/pr/56` branch (calendar/grid laboratory line; kept behind lab flags).
- **Reference policy:** commit subjects and merge messages in this range contain the references above. Items without an explicit reference are listed by behavior and protocol only; no PR/issue number is fabricated.

## Known Limitations

- More than two overlapping courses are retained, but three-way overlaps do not have a three-column layout.
- Experimental widget scrolling and live-card rendering depend on OEM services; some Xiaomi or vivo ROMs may show blank content, stall, or fail to refresh.
- Fluid Cloud/Super Island depends on OS support, notification permission, and vendor qualification; some private authorization states remain `UNKNOWN`.
- Android 16 promoted-ongoing authorization is probed only on API 36+.
- Unsupported timetable protocols fail explicitly. `cf_new` expects the NTSS JSON/WebView envelope and is not a generic parser for arbitrary Qingguo portals.

## Verification

- Baseline: `v1.0.56` (`4392594a`) through `33ee9572`; the full range was audited with merge commits de-duplicated.
- Tests: `./gradlew :app:testDebugUnitTest` — 2,349 tests, 0 failures, 0 errors.
- Lint: `./gradlew :app:lintDebug` — successful; 470 warnings, 27 hints, 0 errors.
- Build: `./gradlew :app:assembleRelease` — successful; `versionName 1.0.57`, `versionCode 63`.
- APK SHA-256 and sizes:
  - `app-universal-release.apk`: `61066a1b8092f8f69bfb5bfcabac180d5a8348549df91ced8a40b3ecaf714c64` (3,713,973 bytes)
  - `app-arm64-v8a-release.apk`: `665cd84da0408f87069e4d1d2eac89b5d15dcb745c26b2b767a553e8ed2f098a` (3,616,037 bytes)
  - `app-armeabi-v7a-release.apk`: `7f84fe74168df52087704b5f2a264b69fa873c2ed509d2ab74652717fa1f11d0` (3,613,345 bytes)
  - `app-x86_64-release.apk`: `4ab1522a4fd4fc52cfc9bbb72278cf25bdfb08b5a8125a267a65532c039ef6e8` (3,615,143 bytes)
- Baseline §12: 18 checks reviewed; code-level baseline sync verified against the current local baseline document. Device-only checks remain subject to the connected-device matrix.

---

# Sleepy v1.0.57

> 覆盖 v1.0.56 基线至 `33ee9572` 的全部用户可感知改动。

## 详细变更清单与引用

本节与上方英文详细清单逐条对应，按用户可感知行为列出具体变更。仅使用仓库提交和合并记录中明确出现的 PR/Issue 引用；没有明确编号的改动不臆造编号。

### 课表、作息表、非常规时间与课表数据

- **非常规与边缘节次课程（[Issue #23](https://github.com/lingion/sleepy/issues/23)）。** 增加逐卡编辑非常规节次和时间的能力，支持第 0 节、第 N+1 节等前置/后置边缘节次。网格课程胶囊按真实分钟比例定位；由 `effectiveCourseTime` 统一有效时间契约；边缘槽位不再参与普通节次归一化；加课、渲染、通知、小组件、导入和导出保持一致的时间语义。修复了点击非常规节次闪退，以及边缘节点与 `ownTime` 之间的传递问题。
- **混合时长作息表（[Issue #23](https://github.com/lingion/sleepy/issues/23)）。** 增加每节时长配置、与课间组对应的时长组编辑、从手动行推导配置、区分推导出的时长组/课间组，以及自动同步时保留边缘行。手动模式和自动模式可以无损往返；导入的节次行可以写入实时智能作息配置而不覆盖用户已有编辑。
- **独立/归属作息表（[Issue #40](https://github.com/lingion/sleepy/issues/40)）。** 增加 Room v7 作息表归属与绑定数据；网格、通知和小组件统一读取有效作息表；编辑页按绑定关系路由；元数据原子写入；撤回快照；绑定课表查询；以及三路冲突处理：只修改当前表、新建独立表、同步回共享表。换绑不再覆盖原作息表；取消新建作息表不再留下空壳；同时修复旧格式解析和预览漏课。
- **调休映射（[Issue #44](https://github.com/lingion/sleepy/issues/44)）。** 将单一调休开关改为明确的“放假日→目标上课日”映射；设置页改为树状结构，支持逐行编辑、原地展开下拉、Material 3 菜单、正确处理空行、菜单与母格保持同形样式，并按实际日期高亮。主课表、Today、WeekGrid、小组件、提醒和课表删除清理统一消费该映射。已映射的放假日会保持有效，不再被错误置灰；保存后所有消费者即时刷新。
- **周次与日期选择（[Issue #57](https://github.com/lingion/sleepy/issues/57)）。** 恢复课表时，Pager 回调不再覆盖已恢复的周次。当当天没有剩余课程时，课表和小组件可选择最近有课日，并支持可配置的多日窗口与节假日置灰。首页表头始终只显示周次，不会被最近有课日改写；当前周结束后支持显示下一周课表。
- **课程清单与课表视觉修复。** 周视图正确隐藏真正无课的日期；课程安排按地点区分；课程清单复用新的节次信息；相关流程增加撤回、取消撤回和换绑取消胶囊。

### 教务导入与排查诊断

- **导入模式恢复（[PR #54](https://github.com/lingion/sleepy/pull/54)）。** 恢复教务导入时明确选择新建、覆盖、追加和既有课表路径的能力，完整提供五种导入模式，并确保数据写入真实归属作息表。
- **新青果 NTSS 协议 `cf_new`。** 增加基于 WebView 的逐周并行抓取与合并、协议元数据注册、路由闸门、脱敏回归 fixture，并首先覆盖江西中医药大学。不支持的页面封装会明确失败，不会被误当成通用青果页面处理。
- **XJU 研究生课表形态 B。** 重写“时间列 + 中文节次 + rowspan 网格”形态的解析流程，并将帧捕获、解析覆盖和回归 fixture 串成完整验证链。
- **新增学校覆盖。** 新增或扩展西北政法大学、上海立信（`classic_eams`）、昆明理工（`kust`）、广东东软（`nuit`）以及北航新的 `byxt` 门户。学校目录从 340 所增至 345 所。仓库中存在 PR #50/#51 对 XJU/`cf_new` 集成线及四校适配线的明确合并证据；对于学校提交标题中没有编号的改动，不额外推断 PR 号。
- **完整排查包导出。** 教务导入失败时提供统一 AlertDialog 和导出操作。导出内容包括页面上下文、请求头、全部 frame、cookie、storage、链接、下载正文、日志和索引文件。九步管线显示当前阶段和百分比，使用原子化输出流程，并在空间允许时保持弹窗动作按钮横向排列。
- **绑定作息表导入正确性。** 导入校验和写入均针对真实归属作息表，避免误报“第 1 节时间不能为空”和错误标红日期字段。
- **非常规时间交换（[Issue #55](https://github.com/lingion/sleepy/issues/55)）。** ICS、分享文本和 WakeUp JSON 双向保留自定义 `ownTime`；CSV 与 HTML 纯文本导入保留并校验非常规时间，不再静默标准化掉这些时间。

### 提醒、通知与厂商能力

- **前一晚明日预告（[PR #48](https://github.com/lingion/sleepy/pull/48)）。** 增加独立的明日预告开关和时间。提醒页改为单张父卡展开子项；今日摘要与明日预告使用不同文案；明日无课文案根据明日数据生成。补齐六语言文案，并保留贡献者致谢。
- **后台可靠性。** 增加事实型可靠性快照、厂商能力适配器、设置回退、可跳转设置的诊断卡，以及明确的 ROM 后台限制说明。标准通知回退和实验性提醒标签行为均有契约测试约束。
- **Android 16 实时更新。** 课程提醒可使用官方 `ProgressStyle` transport。Android 16 的 promoted-ongoing 授权探测为“已允许/未允许/不适用”三态；旧版本返回不可用，不伪造权限结果。开机和升级恢复会恢复提醒；删除课表或课程后会清理孤立闹钟。
- **厂商实时卡片链路。** 增加统一的 `CourseLiveCardState` 和渲染器，覆盖小米、vivo、魅族、OPPO、华为和荣耀；Fluid Cloud 服务改由统一渲染器处理，并增加审计契约。系统无法提供可验证能力时，私有厂商授权状态保持 `UNKNOWN`。

### 小组件与 OEM 集成

- Android 15 为全部 13 个小组件变体登记无数据 picker 预览；提供方标签和描述本地化；增加全局 OEM 能力注册表、小米 AppVault 元数据、vivo 原子组件声明和锁屏类别声明。
- Today/TwoDay 小组件的时间和地点各自保持半宽单行；WeekGrid 教室角标始终限制在底部预留带内，即使卡片高度很低也不会压入课程名称区域。
- 小组件与主课表共用节假日置灰和最近有课日选择；统一目标日期标签，修正横屏状态栏行为；厂商验证矩阵区分代码支持与真实设备证据。

### 导航、Material 3、设置与呈现

- 将自研 14 屏 Overlay 栈迁移到官方、带类型的 AndroidX Navigation 3 / Compose 2.10.1 路由和 Material 转场；从子页面返回时保留当前 Tab。
- 完成官方 Material 3 色彩和组件迁移：移除自定义 `SleepyTheme.colors` 桥接和过时的小组件/主题默认值，Compose UI 直接读取官方 color scheme；弹窗、按钮、卡片和 Switch 统一使用官方组件语义。
- 将外观设置与课表显示设置拆分，并增加独立的节次表头设置页。支持旧式、横排、竖排和三行布局；节次编号样式；“第 X 节”显示；悬挂位置；实时预览；共享几何；以及 App 网格和 WeekGrid 小组件共用的自适应排版。
- 通用设置重排为五个稳定分组：课表显示、小组件、画面/导航、语言和实验室功能。自定义主题保存后立即应用到全 App 和小组件，包括新创建的主题。
- 修复预测性返回和启动页行为，避免误导性的缩小预览、暗色闪白、返回白底、居中 Logo 回归和残留启动画面。更新提醒按版本持久化，并统一显示在关于页、我的入口、底栏、悬浮 Dock 和 NavigationRail。
- 补齐所有支持 locale 的节次表头文案，并恢复设置字符串 parity 要求。

### 引用索引

- **Issues：** #23（非常规/边缘节次与混合时长）、#40（独立/归属作息表）、#44（调休映射）、#55（非常规时间导入导出往返）、#57（恢复课表时周次闪烁）。
- **Pull requests：** #48（前一晚明日预告与提醒重构）、#50 和 #51（XJU/`cf_new` 集成线）、#52（小组件最近有课日）、#54（教务导入模式），以及仓库中的 `origin/pr/56` 分支（日历/网格实验室功能线，默认受实验室开关控制）。
- **引用原则：** 上述引用均来自本范围内的提交标题或合并信息。没有明确 PR/Issue 编号的改动只按实际行为和协议列出，不臆造编号。

## 已知限制

- 多于两门课程重叠时会保留全部课程，但暂不提供三列并排显示。
- 实验性小组件滚动和实时卡片依赖 OEM 服务，部分小米或 vivo ROM 可能空白、卡住或不刷新。
- Fluid Cloud/Super Island 取决于系统支持、通知权限和厂商服务资格，部分私有授权状态保持 `UNKNOWN`。
- Android 16 promoted-ongoing 授权只在 API 36 及以上探测。
- 不支持的教务协议会明确失败；`cf_new` 依赖 NTSS JSON/WebView 格式，不是任意青果门户的通用解析器。

## 验证

- 基线：`v1.0.56`（`4392594a`）至 `33ee9572`；230 个提交已审计，合并提交未重复计入。
- 单测：`./gradlew :app:testDebugUnitTest` — 2,349 个测试，0 失败，0 错误。
- lint：`./gradlew :app:lintDebug` — 成功；470 条 warning、27 条 hint、0 error。
- 构建：`./gradlew :app:assembleRelease` — 成功；`versionName 1.0.57`、`versionCode 63`。
- APK SHA-256 与大小：
  - `app-universal-release.apk`：`61066a1b8092f8f69bfb5bfcabac180d5a8348549df91ced8a40b3ecaf714c64`（3,713,973 字节）
  - `app-arm64-v8a-release.apk`：`665cd84da0408f87069e4d1d2eac89b5d15dcb745c26b2b767a553e8ed2f098a`（3,616,037 字节）
  - `app-armeabi-v7a-release.apk`：`7f84fe74168df52087704b5f2a264b69fa873c2ed509d2ab74652717fa1f11d0`（3,613,345 字节）
  - `app-x86_64-release.apk`：`4ab1522a4fd4fc52cfc9bbb72278cf25bdfb08b5a8125a267a65532c039ef6e8`（3,615,143 字节）
- 基线 §12：已核对 18 项；代码级基线同步已按当前本地文档复核，真机项目仍以设备矩阵为准。
