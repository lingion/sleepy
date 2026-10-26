[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.android.com/develop/ui/views/appwidgets, https://developer.android.com/develop/ui/views/appwidgets/advanced, https://developer.android.com/develop/ui/views/appwidgets/layouts, https://developer.android.com/reference/android/appwidget/AppWidgetProvider, https://developer.android.com/reference/android/appwidget/AppWidgetManager, https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo

# AppWidget 官方核心约束

## 组件和生命周期

Android AppWidget 由三部分组成：

- `AppWidgetProviderInfo`：XML 元数据，描述布局、更新频率和 provider 类。
- `AppWidgetProvider`：`BroadcastReceiver` 的便利子类，分发 widget 广播。
- XML 初始布局：RemoteViews 可跨进程显示的布局。

`AppWidgetProvider` 的生命周期回调包括：

- `onEnabled()`：该 provider 的第一个实例被创建时调用。
- `onUpdate()`：系统要求为一个或多个实例提供 RemoteViews 时调用；触发原因包括新实例、请求的更新间隔到期或系统启动。
- `onAppWidgetOptionsChanged()`：实例尺寸或 options 改变时调用。
- `onDeleted()`：实例被删除时调用。
- `onDisabled()`：该 provider 的最后一个实例被删除时调用。
- `onRestored()`：实例从备份恢复时调用；随后系统会立即调用 `onUpdate()`。如果应用保存了实例配置，应在此处把旧 ID 映射到新 ID，并设置 `OPTION_APPWIDGET_RESTORE_COMPLETED`。

## 更新链路

`AppWidgetManager.updateAppWidget()` 是完整更新：传入的 RemoteViews 会替换并缓存为 widget 的完整表示。`partiallyUpdateAppWidget()` 是增量更新，只能在该实例至少收到一次完整更新后生效；它不替换完整缓存表示。

更新方法可以在 `ACTION_APPWIDGET_UPDATE` 广播内调用，也可以在广播处理器之外调用，但调用者必须与 AppWidgetProvider 属于同一 UID。

`updatePeriodMillis` 的请求不会以高于每 30 分钟一次的频率投递。设为 `0` 可禁用周期更新。官方建议需要异步或更灵活调度时把它设为 `0`，改用 WorkManager；WorkManager 仍受系统电量限制。

广播接收器通常在主线程运行，系统一般允许最多约 10 秒；耗时工作应使用 `goAsync()` 或调度 WorkManager。广播处理期间执行的工作会阻塞后续广播。

## 尺寸 options

`AppWidgetManager` 为实例 options 提供以下尺寸键，单位为 dp：

- `OPTION_APPWIDGET_MIN_WIDTH`
- `OPTION_APPWIDGET_MIN_HEIGHT`
- `OPTION_APPWIDGET_MAX_WIDTH`
- `OPTION_APPWIDGET_MAX_HEIGHT`

Android 12（API 31）增加 `OPTION_APPWIDGET_SIZES`，类型为 `List<SizeF>`，表示 launcher 提供的实例可取尺寸列表。launcher 不支持此字段时，列表可能为空或为 null。provider 可在 `onAppWidgetOptionsChanged()` 中读取它并提交按尺寸映射的 `RemoteViews`。

尺寸改变时，系统调用 `onAppWidgetOptionsChanged()`。Android 12+ 推荐使用响应式布局或精确尺寸布局，避免每次尺寸变化都唤醒应用。

## 尺寸与缩放元数据

Android 12+ 可在 `appwidget-provider` 中使用：

- `targetCellWidth` / `targetCellHeight`：默认占用的 launcher 网格单元数。
- `maxResizeWidth` / `maxResizeHeight`：launcher 允许的最大调整尺寸。
- `minResizeWidth` / `minResizeHeight`：允许调整到的最小尺寸。
- `resizeMode`：`none`、`horizontal`、`vertical` 或 `both`。

`RESIZE_HORIZONTAL` 表示仅横向可调整，`RESIZE_VERTICAL` 表示仅纵向可调整，`RESIZE_BOTH` 表示两个方向均可调整，`RESIZE_NONE` 表示不可调整。实例仍必须适配 `minResize*` 到 `maxResize*` 范围内的尺寸；官方同时说明实际尺寸可能大于 `maxResizeWidth`/`maxResizeHeight`。

## Pin API 与 launcher 责任

Android 8.0（API 26）及以上，支持固定快捷方式的 launcher 也应支持固定 widget。应用先检查 `isRequestPinAppWidgetSupported`，再调用 `requestPinAppWidget()`。第三个参数的 PendingIntent 只在成功固定后接收新 widget ID；固定失败时应用不会收到该回调。

AOSP 对设备实现者的要求是：launcher 提供带有 `CONFIRM_PIN_SHORTCUT` 和 `CONFIRM_PIN_APPWIDGET` intent filter 的确认 Activity；确认后把 widget/shortcut 添加到主屏。AOSP 指南建议以 Launcher3 为实现参考。

## RemoteViews 与安全边界

widget 布局基于 RemoteViews。官方明确指出 RemoteViews 不支持所有布局和 View，不能使用自定义 View 或受支持 View 的子类。RemoteViews 在另一个进程中展开，因此 provider 只提交受支持的布局、控件和操作。

完整更新的 RemoteViews 中 bitmap 总内存不得超过填充屏幕 1.5 倍所需的内存，即：`screen width × screen height × 4 × 1.5` 字节。

## 参考页面更新时间

本次抓取的 Android Developers 页面显示：核心页面 2026-09-08 更新；`AppWidgetProvider` reference 2026-08-03 更新；`AppWidgetManager` reference 2026-08-14 更新；`AppWidgetProviderInfo` reference 页面为当前 API reference。
