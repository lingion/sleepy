# Sleepy v1.0.60

> Auto DND during class with alarm boundary pairing and self-calibration, tablet master-detail navigation overhaul, and city-based school recommendations.

Baseline: the post-squash v1.0.59 tag `v1.0.59` through `origin/main` HEAD — 36 commits. Every statement below is sourced from that range; where evidence is missing, that is stated instead of guessed.

## What's New

### Auto DND during class — alarm boundary pairing + self-calibration

The app now automatically enables Do Not Disturb when a class starts and restores the previous state when it ends. The feature works by:

- Pairing each alarm with its corresponding class boundary (start/end time)
- Detecting which alarms are "class alarms" based on their time alignment with the schedule
- Creating a "fluid notification channel" on ColorOS/MIUI devices that survives foreground service restarts
- Self-calibrating on each schedule change — recalculating alarm ownership and rescheduling if needed

The user can opt in/out via Settings → Reminders → "Auto Do Not Disturb during class". The DND rules apply only to the notification filter, not the full DND mode, preserving alarm audibility.

**Evidence:** Commits `9800e1db` through `49a0139d` (11 commits). The feature is gated behind `ReminderPreferences.useClassDnd`. Tests cover alarm pairing, DND state preservation, and self-calibration on schedule changes.

### Tablet master-detail navigation rewrite

The tablet layout (master-detail pattern) has been completely rewritten:

- New "track" style navigation items — course + today combined in one highlitable item
- Master-detail rail now shows 3 schedule slots + current capsule + animated right-half content
- Red dot (update indicator) now works correctly on both capsule and regular rail items
- Strict separation: red dot toggle only affects non-compact branches (not phone/compact)

**Evidence:** Commits `c4e60548` through `4bd47850` (15 commits). New contract tests lock the design.

### City-based school recommendations

The school selection screen now shows "possible schools" cards based on the user's city location:

- Click to trigger location request (FINE/COARSE permissions)
- Coordinates are never saved or uploaded — used only for city matching
- Up to 5 nearby schools displayed with expand/collapse
- Offline city boundary data bundled in the APK (759 KB, down 38% from previous)

**Evidence:** PR #113 (merged 2026-10-06T15:00:36Z), contributed by @echo0731. Assets: `school_cities.json` (348 entries) + `school_city_boundaries.*` (streaming SCB1 parser). Attribution: ChinaGeoJson (MIT) + DataV.GeoAtlas.

### Seven-day alarm scheduler retained

The previous "retain seven-day alarm scheduler" feature has been preserved and hardened:

- Alarms persist across schedule edits without being cancelled and recreated
- Fallback DND resynchronization on class boundary changes
- Settings changes now route through a debounce hub to prevent burst scheduling

**Evidence:** Commits `ef9c6065` through `ddd0a1dc` (6 commits).

## Fixes

- **HFUT semester selection recovered:** Fixed an issue where semester selection didn't work for Hefei University of Technology.

  **Evidence:** PR #125 (merged 2026-10-07T09:07:32Z).

- **Holiday skip test regressions repaired:** Multiple test fixes after the holiday-skip feature merge.

  **Evidence:** PR #133 (merged 2026-10-07T23:41:55Z), contributed by @jim139129.

- **Notification channel creation before foreground start:** Fixed a race condition where the fluid notification channel wasn't ready when the foreground service started.

- **Right-half content callbacks all routed to navigator:** Tablet right-half sub-page callbacks now correctly connect to the navigator.

- **Meal breaks marked in timetable grids:** Visual indication for meal break periods in the grid view.

  **Evidence:** PR #118 (merged 2026-10-07T01:28:12Z), contributed by @YYiChen.

- **ColorOS fluid cloud course reminder style adaptation:** Styling adaptation for ColorOS fluid notification appearance.

  **Evidence:** PR #117 (merged 2026-10-07T01:09:03Z), contributed by @Cold577.

- **CI release signing:** Use the project's existing signing key to avoid signature warnings on upgrade.

  **Evidence:** PR #111 (merged 2026-10-06T03:52:20Z).

## Known Limitations

- **Vendor live cards still depend on the manufacturer.** Xiaomi Super Island and similar surfaces require vendor services, notification permission, and device support; an AOSP emulator cannot verify their rendering.
- **City-based recommendations require location permission.** Users who deny location access will not see nearby school suggestions.
- **Auto DND does not apply full DND.** The feature only adjusts notification filters to preserve alarm audibility.

## Verification

- Range audited: `v1.0.59..origin/main`; 36 commits.
- Unit tests: `:app:testDebugUnitTest` — CI will verify on tag push.
- Lint: `:app:lintDebug` — CI will verify on tag push.
- Build: CI will verify on tag push.
- APK SHA-256: CI will compute on build.

---

# Sleepy v1.0.60

> 上课自动勿扰（课程边界配对 + 状态自校准），平板 master-detail 导航重构，城市定位学校推荐。

基线：v1.0.59 到 origin/main HEAD，共 36 个提交。以下每一条都来自该范围的实际记录；找不到依据的地方直接写明，不做推测。

## 新增功能

### 上课自动勿扰 — 闹钟边界配对 + 自校准

课程开始时自动开启勿扰，课程结束时恢复之前的通知状态。实现原理：

- 将每个闹钟与对应的课程边界（开始/结束时间）配对
- 根据时间是否与课表对齐来判断是否是"课程闹钟"
- 在 ColorOS/小米设备上创建"流体通知通道"，在 foreground service 重启后仍然有效
- 每次课表变更时自校准——重新计算闹钟归属并在需要时重新排程

用户可在设置 → 提醒 → "上课自动勿扰" 中开启/关闭。该功能仅调整通知过滤规则（非完整勿扰模式），保留闹钟声音。

**依据：** 提交 `9800e1db` 到 `49a0139d`（11 个提交）。功能由 `ReminderPreferences.useClassDnd` 控制。测试覆盖闹钟配对、勿扰状态保留、课表变更后自校准。

### 平板 master-detail 导航重构

平板布局（master-detail 模式）已完全重写：

- 新的"跑道型"导航项 — 课表+今日合并为一个可高亮的项
- Master-detail rail 现在显示 3 个课表槽位 + 当前胶囊 + 右侧 animated content
- 红点（更新指示器）现在在胶囊和普通 rail 项上都能正常工作
- 严格分离：红点 toggle 只影响非 compact 分支（不影响手机/compact）

**依据：** 提交 `c4e60548` 到 `4bd47850`（15 个提交）。新契约测试锁定设计。

### 城市定位学校推荐

学校选择页面现在根据用户城市位置显示"可能的学校"卡片：

- 点击触发定位请求（需要 FINE/COARSE 权限）
- 坐标永不保存或上传，仅用于城市匹配
- 最多显示 5 所附近学校，支持展开/收起
- 离线城市边界数据打包进 APK（759 KB，比之前减少 38%）

**依据：** PR #113（2026-10-06T15:00:36Z 合并），贡献者 @echo0731。资产：`school_cities.json`（348 条）+ `school_city_boundaries.*`（流式 SCB1 解析器）。致谢：ChinaGeoJson (MIT) + DataV.GeoAtlas。

### 七天闹钟调度器保留

之前的"保留七天闹钟调度器"功能已加固：

- 课表编辑时闹钟不会被取消重建，而是保留
- 课程边界变更时的 DND 后备重新同步
- 设置变更现在通过防抖枢纽路由，防止突发排程

**依据：** 提交 `ef9c6065` 到 `ddd0a1dc`（6 个提交）。

## 修复

- **HFUT 学期选择恢复：** 修复合肥工业大学学期选择失效的问题。

  **依据：** PR #125（2026-10-07T09:07:32Z 合并）。

- **节假日跳过测试回归修复：** holiday-skip 功能合并后的多个测试修复。

  **依据：** PR #133（2026-10-07T23:41:55Z 合并），贡献者 @jim139129。

- **Foreground 启动前创建通知通道：** 修复流体通知通道未就绪时 foreground service 启动的竞态条件。

- **右侧内容回调全路由到 navigator：** 平板右侧子页面回调现在正确连接到 navigator。

- **课表网格中标记用餐休息：** 网格视图中显示用餐休息时段的可视化标识。

  **依据：** PR #118（2026-10-07T01:28:12Z 合并），贡献者 @YYiChen。

- **ColorOS 流体云课程提醒样式适配：** ColorOS 流体通知外观样式适配。

  **依据：** PR #117（2026-10-07T01:09:03Z 合并），贡献者 @Cold577。

- **CI 发布签名：** 使用项目现有签名密钥，避免升级时出现签名警告。

  **依据：** PR #111（2026-10-06T03:52:20Z 合并）。

## 已知限制

- **厂商悬浮卡片仍依赖厂商。** 小米超级卡片及其他类似 surface 需要厂商服务、通知权限和设备支持；AOSP 模拟器无法验证其渲染。
- **城市推荐需要定位权限。** 用户拒绝定位权限后将无法看到附近学校推荐。
- **自动勿扰不是完整勿扰。** 该功能仅调整通知过滤规则，保留闹钟声音。

## 验证

- 范围：`v1.0.59..origin/main`；36 个提交。
- 单元测试：`:app:testDebugUnitTest` — CI 将在 tag 推送时验证。
- Lint：`:app:lintDebug` — CI 将在 tag 推送时验证。
- 构建：CI 将在 tag 推送时验证。
- APK SHA-256：CI 将在构建时计算。
