# Sleepy v1.0.58

> Release notes for the changes after v1.0.57.

## What changed

### Xiaomi Super Island

- Restored the v1.0.56 Xiaomi/HyperOS notification path. Xiaomi, Redmi, and POCO now use the public `ProgressStyle` plus promoted-ongoing notification without `miui.focus.*` extras. On the tested Xiaomi device, the Fluid Cloud test card appears in the status-bar Super Island again instead of falling back to a drawer card with a progress bar.
- Kept vendor-specific extras for vivo/iQOO, Meizu, and Samsung behind their existing capability checks. Other vendors continue to use the public Android notification path.

### Fluid Cloud and settings

- Added the debug-only Fluid Cloud test button. It starts the real `FluidCloudService` with a two-minute test window, so the test exercises the same foreground-service and renderer path used by reminders.
- Added direct routes for the relevant notification and battery-optimization settings. The vendor-specific route is used when available; the Android settings page remains the fallback.

### Timetable and widgets

- Centered wrapped course names in the grid while keeping the existing course-card geometry.
- Kept period-header text readable in previews, the timetable grid, and widgets with adaptive sizing and column measurement.
- Restored Android 15 no-data previews for the widget variants and removed stale preview declarations that conflicted with the generated preview path.
- Kept widget backgrounds aligned with the selected light/dark theme.

### Maintenance

- Removed machine-specific fixture paths from tests and verification helpers.
- Retained the debug package identity (`.debug` application ID and `-debug` version suffix) and the `.sleepybackup` import/export path from v1.0.57.

## Known limitations

- Super Island and other live-card surfaces depend on OEM services, notification permission, and device qualification. The Xiaomi Super Island path was verified on a Xiaomi device; an AOSP emulator cannot prove OEM rendering.
- Android 16 promoted-ongoing authorization is probed only on API 36 and later.
- Some vendor-specific live-card capabilities remain unavailable when the OS exposes no verifiable authorization state.

## Verification

- Source: merged `origin/main` with the verified Xiaomi extras-free renderer state; no `miui.focus.*` payload is present in the Xiaomi branch.
- Tests: `./gradlew :app:testDebugUnitTest` — 2,379 tests, 0 failures, 0 errors.
- Lint: `./gradlew :app:lintDebug` — successful.
- Xiaomi device: Fluid Cloud test passed; the notification appeared in the status-bar Super Island.

---

# Sleepy v1.0.58

> 以下内容对应上方英文说明。

## 变更

### 小米超级岛

- 恢复 v1.0.56 的小米/HyperOS 通知通路。小米、Redmi、POCO 使用公共 `ProgressStyle` 和 promoted-ongoing 通知，不再附加 `miui.focus.*` 私有 extras。已在小米真机通过流体云测试，通知重新进入状态栏超级岛，不再只显示带进度条的普通通知卡片。
- vivo/iQOO、魅族、三星仍按各自能力探测结果使用厂商 extras；其他厂商继续使用 Android 公共通知通路。

### 流体云与设置

- 增加仅 debug 包可见的流体云测试按钮。按钮启动真实 `FluidCloudService`，使用两分钟测试窗口，测试经过与正式提醒相同的前台服务和渲染器路径。
- 增加通知和电池优化设置的直达入口。有厂商专用页面时优先进入该页面，否则回退到 Android 设置页。

### 课表与小组件

- 课表网格中的换行课程名改为居中，同时保持课程卡片几何尺寸不变。
- 预览、课表网格和小组件中的节次表头使用自适应字号和列宽测量，减少截断。
- 恢复 Android 15 无数据 picker 预览，并移除与生成式预览路径冲突的旧声明。
- 小组件背景继续跟随当前浅色/深色主题。

### 维护

- 清理测试和校验脚本中的机器相关路径。
- 保留 v1.0.57 的 debug 包身份规则，以及 `.sleepybackup` 导入导出路径。

## 已知限制

- 超级岛和其他实时卡片依赖厂商系统服务、通知权限及设备资格。小米超级岛已在小米真机验证；AOSP 模拟器不能证明厂商界面渲染。
- Android 16 promoted-ongoing 授权只在 API 36 及以上探测。
- 系统未提供可验证授权状态时，部分厂商实时卡片能力仍不可用。

## 验证

- 源码：已合并 `origin/main`，并保留已验证的小米 extras-free 渲染状态；小米分支不存在 `miui.focus.*` 载荷。
- 单测：`./gradlew :app:testDebugUnitTest`，2,379 个测试，0 失败，0 错误。
- lint：`./gradlew :app:lintDebug`，成功。
- 小米真机：流体云测试通过，通知进入状态栏超级岛。
