# Sleepy v1.0.57

> This release covers all changes merged after the v1.0.56 baseline. It adds safer schedule editing, broader timetable import coverage, a more dependable notification and widget experience, and a full Navigation 3 / Material 3 migration pass.

## What's New

- **Mixed-duration period tables** — assign different lesson durations to individual periods, edit duration groups alongside break groups, and round-trip the configuration through import/export while preserving older data.
- **Holiday makeup mapping** — map a holiday date to its actual teaching date per timetable. The mapping is reflected in the main schedule, Today view, week grid, notifications, widgets, and cleanup flows. The new tree view makes each holiday and target date explicit.
- **Nearest busy day** — when the current day has no remaining classes, the app can show the nearest day with classes instead of jumping straight to next week. The setting is shared by the main schedule and all five widget families.
- **Previous-evening tomorrow preview** — the daily reminder card can preview tomorrow's first class and total class count, with a separate time and switch under one master reminder card.
- **Vendor live-notification guidance** — supported device families now expose capability-aware guidance and vendor settings fallbacks. Unknown private-platform states remain `UNKNOWN` instead of being guessed from the device brand or an openable settings page.
- **Android 15 widget previews** — all 13 widget variants register a no-data picker preview on Android 15+, while Android 14 and below keep the existing preview fallback. Widget labels and provider descriptions are localized.
- **Widget compatibility coverage** — the widget layer now records global OEM capability boundaries and adds Xiaomi AppVault and vivo Atomic Component metadata paths.
- **Update notices** — version-scoped update notices are persisted and surfaced consistently in the About page, Mine navigation, bottom bar, floating dock, and navigation rail.
- **Official Navigation 3** — the former custom overlay stack is replaced with typed AndroidX Navigation 3 routes and Material transitions, while preserving tab highlighting when returning from child pages.
- **Material 3 migration** — theme and component rendering now use the official Material 3 color and component APIs, with the migration recorded and the old theme bridge removed.
- **Custom theme application** — saving a custom theme applies it immediately across the app and refreshes widgets, including newly created themes.
- **General Settings reorganization** — settings are grouped in a stable order: timetable display, widgets, screen and navigation, language, and laboratory features.
- **Diagnostic export** — timetable import failures use one AlertDialog with a confirm action and an export action for a complete diagnostic package containing captured page context, request/frame data, and collected logs.
- **New timetable protocols and schools** — the directory grows from 340 to 345 schools. New protocol coverage includes Jiangxi University of Chinese Medicine (`cf_new`), Northwest University of Political Science and Law and Shanghai Lixin University (`classic_eams`), Kunming University of Science and Technology (`kust`), Guangdong Neusoft University (`nuit`), and Beijing University of Aeronautics and Astronautics' new `byxt` portal. The four-school and BUAA paths were cross-verified against external implementations.

## Improved & Fixed

- Small Today and TwoDay widgets keep time and location metadata on one line with independent half-width truncation, so metadata cannot push course content out of the card.
- WeekGrid classroom badges stay inside a reserved footer band, including on extremely short cards; course names and badges no longer overlap.
- Android 15 widget registration is guarded by API level, with older devices returning an explicit unsupported result.
- Predictive-back preview is disabled where the OEM preview produced a misleading shrinking rectangle, and the splash backdrop is degraded after the first frame to prevent dark-mode flashes and residue.
- Reminder previews and course-slot summaries use the user's configured display choices.
- Automatic schedule synchronization preserves edge rows and inferred period configuration, including smart/manual mode round-trips.
- Import parsing synchronizes inferred JW configuration into live state and supports the new `cf_new` weekly fetch path.
- The lint run is back at the v1.0.57 baseline shape: 32 pre-existing errors remain, with no newly introduced error IDs or messages. The full unit suite passes.

## Verification

- Full unit suite: **2,221 tests, 0 failures**.
- Release build: **versionName 1.0.57, versionCode 63**, three ABI APKs produced.
- APK SHA-256:
  - `arm64-v8a`: `333b63038374626e88b94d036088761d78ecbf257efe571793d7d4b2ca859d24`
  - `armeabi-v7a`: `adba576f6211e95af343b3c0d46f083aead6cb84956cc31c06d79771274907a6`
  - `x86_64`: `c9fe38f2c0bf0d9b723d512e87c6635ba5301fbbeff8b52aabd8f5d76e1a39e2`

---

# Sleepy v1.0.57

> 本版本覆盖 v1.0.56 基线之后合入的全部改动:强化作息表编辑、扩大课表导入覆盖,提升通知与桌面卡片可靠性,并完成 Navigation 3 / Material 3 迁移。

## 新增

- **混合时长作息表** —— 每个节次可分配不同上课时长;作息表编辑器与课间时长组同步维护;导入导出可完整往返,旧数据保持兼容。
- **调休映射** —— 每张课表可将放假日映射到实际上课日。主课表、今日页、周网格、通知、桌面卡片及删表清理统一遵循映射;树状页面逐一展示放假日与目标日期。
- **最近有课日** —— 当天已无剩余课程时,可选择展示最近有课的日期,不再直接跳到下一周;主课表与五类桌面卡片共用该设置。
- **前一天晚上明日预告** —— 每日提醒卡可预告明日首节课与总课数,单独设置时间和开关,与今日摘要共用一个总开关卡。
- **厂商实时通知授权引导** —— 支持的厂商族显示能力感知的引导和设置回退入口;私有平台状态未知时保持 `UNKNOWN`,不从设备品牌或设置页可打开推断已授权。
- **Android 15 桌面卡片预览** —— 13 个卡片变体在 Android 15+ 登记无数据 picker 预览;Android 14 及以下继续使用原预览回退。卡片标签与提供方描述已本地化。
- **桌面卡片厂商兼容覆盖** —— 建立全球厂商能力边界登记,接入小米 AppVault 与 vivo 原子组件元数据路径。
- **版本更新提醒** —— 更新提醒按版本持久化,并统一显示在关于页、我的页面入口、底栏、悬浮 Dock 与 NavigationRail。
- **官方 Navigation 3** —— 自研 Overlay 栈迁移到带类型的 AndroidX Navigation 3 路由与 Material 动画,从子页面返回时保留 Tab 高亮。
- **Material 3 迁移** —— 主题和组件统一使用官方 Material 3 色彩与组件 API,旧主题桥下线并留下迁移账本。
- **自定义主题即时应用** —— 保存自定义主题后全 app 立即换色并刷新桌面卡片,新建主题同样生效。
- **通用设置重排** —— 设置组固定为:课表显示、小组件、画面与导航、语言、实验室。
- **排查全量包** —— 教务导入失败统一弹窗,提供「确定」与「导出排查包」双按钮;排查包包含页面上下文、请求/帧信息与日志。
- **新教务协议与学校** —— 目录由 340 所增至 345 所。新增江西中医药大学(`cf_new`)、西北政法大学与上海立信会计金融学院(`classic_eams`)、昆明理工大学(`kust`)、广东东软学院(`nuit`),以及北京航空航天大学新 `byxt` 门户;四校与北航通路均按外部实现交叉核验。

## 优化与修复

- Today/TwoDay 小尺寸卡片的时间与地点信息各自占半宽单行截断,不再挤出课程内容。
- WeekGrid 教室角标始终限制在底部预留带内,极矮卡也不与课程名重叠。
- Android 15 预览登记增加 API 保护,旧设备明确返回不支持结果。
- 关闭 OEM 预测性返回预演并在首帧后降级启动页底衬,修复矩形缩小、暗色闪白与残留画面。
- 提醒预览与课程时段摘要遵循用户的显示设置。
- 自动同步保留边缘节次与推导出的作息配置,手动/自动模式可往返转换。
- 导入解析会把推导出的教务配置同步回实时状态,并支持 `cf_new` 逐周抓取链路。
- lint 回到 v1.0.57 基线形态:保留 32 条既有 error,未新增 error ID 或 message;全量单测通过。

## 验证

- 全量单测: **2,221 个,0 失败**。
- Release 构建: **versionName 1.0.57, versionCode 63**,三 ABI APK 已产出。
- APK SHA-256:
  - `arm64-v8a`: `333b63038374626e88b94d036088761d78ecbf257efe571793d7d4b2ca859d24`
  - `armeabi-v7a`: `adba576f6211e95af343b3c0d46f083aead6cb84956cc31c06d79771274907a6`
  - `x86_64`: `c9fe38f2c0bf0d9b723d512e87c6635ba5301fbbeff8b52aabd8f5d76e1a39e2`
