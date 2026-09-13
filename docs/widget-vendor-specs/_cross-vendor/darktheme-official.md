[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.android.com/develop/ui/views/theming/darktheme, https://developer.android.com/develop/ui/views/appwidgets/enhance, https://developer.android.com/develop/ui/views/theming/dynamic-colors, https://source.android.com/docs/core/display/material

# DayNight、深浅色与动态颜色

## Dark theme 与 DayNight

Android 10（API 29）及以上提供系统 Dark theme。应用可使用继承自 DayNight 的主题，使应用主题跟随系统 night mode flags。官方列出的应用内模式对应关系是：Light=`MODE_NIGHT_NO`，Dark=`MODE_NIGHT_YES`，System default=`MODE_NIGHT_FOLLOW_SYSTEM`。

API 31 及以上可使用 `UiModeManager#setApplicationNightMode` 告知系统应用主题；API 30 及以下可使用 `AppCompatDelegate.setDefaultNightMode()`。

应避免写死仅适用于浅色主题的颜色或图标，改用主题属性或 night-qualified 资源。官方建议在 launcher widget 和自定义通知 View 上同时测试浅色与深色主题，重点检查：背景是否被假定为浅色、文字颜色是否写死、背景与默认文字颜色是否冲突、图标是否为固定单色。

`values-night`、`drawable-night` 等限定资源可用于静态适配不同 night mode。主题改变会触发 `uiMode` configuration change，Activity 默认会重建。若 Activity 声明 `android:configChanges="uiMode"`，主题变化时回调 `onConfigurationChanged()`，而不是由系统自动重建 Activity。

当前 night mode 可由 `configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK` 读取，并与 `UI_MODE_NIGHT_NO` / `UI_MODE_NIGHT_YES` 比较。

## Force Dark

Android 10 提供 Force Dark。应用必须在主题中设置 `android:forceDarkAllowed="true"` 才能选择加入。系统在绘制前分析浅色主题的 View 并自动应用深色主题。若应用使用深色主题或 DayNight 主题，Force Dark 不会应用。可通过 View 的 `android:forceDarkAllowed` 或 `setForceDarkAllowed()` 对单个 View 控制。

官方没有把 Force Dark 作为 launcher widget 的替代测试方案；对 widget 的明确建议仍是同时测试浅色和深色内容，并避免写死颜色。

## Widget 动态颜色

Android 12（API 31）起，widget 可以使用设备主题颜色。官方列出两种方式：

- 根布局使用 `@android:style/Theme.DeviceDefault.DayNight`。
- 使用 Material Components for Android 1.6.0+ 的 `Theme.Material3.DynamicColors.DayNight`。

设置根主题后，根布局及其子 View 可通过颜色属性取值，例如 `?attr/primary`、`?attr/primaryContainer`、`?attr/onPrimary`、`?attr/onPrimaryContainer`。动态颜色只在 Android 12+ 可用；低版本应提供默认主题，并用 `values-v31` 提供动态颜色主题。

Android Developers 的 widget 指南明确建议：Material 3 Dynamic Colors 可使按钮、背景等组件随设备主题变化，并在浅色与深色模式间保持一致。

## AOSP Material You 对 OEM 的公共要求

AOSP Material You 文档要求设备实现支持 Android 12 的 widget 布局、尺寸和软件参数 API，包括圆角尺寸；实现应通过 API 正确提供参数，并让用户可以调整和配置 widget。

AOSP 文档将动态颜色定义为从壁纸或主题源色生成 5 个色调调色板、每个调色板 13 个色阶，共 65 个颜色属性。OEM 应采用 AOSP 的颜色提取逻辑，以保持设备和应用生态的一致性。

AOSP widget checklist 将以下能力列为系统/第一方 widget 的改进项：可缩放预览、widget 描述、平滑过渡、避免 broadcast trampoline、改进尺寸和布局、动态颜色、圆角、新复合按钮，以及简化 RemoteViews 集合和运行时 API。

## 小米官方差异（与公共层相关）

小米澎湃 OS 小部件规范（2024-10-17）写明：小米 Widget 只能通过 XML 静态适配深色模式（如 `drawable-night`、`values-night`），不支持在 RemoteViews 中用代码动态设置深色模式。小米 FAQ 进一步写明，新版本切换深色模式时宿主缓存 RemoteViews 并用上一次的 RemoteViews 重建 widget，因此不能依赖 options 回调在深色切换时触发。
