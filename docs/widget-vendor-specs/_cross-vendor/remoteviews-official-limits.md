[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.android.com/reference/android/widget/RemoteViews, https://developer.android.com/develop/ui/views/appwidgets/collections, https://developer.android.com/develop/ui/views/appwidgets, https://developer.android.com/reference/android/appwidget/AppWidgetManager

# RemoteViews 官方能力边界

## 支持的布局白名单

RemoteViews 只支持以下布局：

- `AdapterViewFlipper`
- `FrameLayout`
- `GridLayout`
- `GridView`
- `LinearLayout`
- `ListView`
- `RelativeLayout`
- `StackView`
- `ViewFlipper`

## 支持的控件白名单

RemoteViews 只支持以下控件：

- `AnalogClock`
- `Button`
- `Chronometer`
- `ImageButton`
- `ImageView`
- `ProgressBar`
- `TextClock`
- `TextView`

API 31（Android 12）起额外支持：

- `CheckBox`
- `RadioButton`
- `RadioGroup`
- `Switch`

官方明确：**这些类的子类不被支持**；也不能使用自定义 View。RemoteViews 另外支持 `ViewStub`，可在运行时懒加载布局资源。

## 有状态控件

Android 12 起 RemoteViews 支持 `CheckBox`/`Switch`/`RadioButton` 的有状态显示，但 widget 本身仍是无状态的——应用必须自行存储状态并注册状态变化事件。官方提示：必须始终通过 `RemoteViews.setCompoundButtonChecked` 显式设置当前勾选状态，否则在拖拽或调整大小时可能出现意外结果。

监听勾选变化通过 `RemoteViews.setOnCheckedChangeResponse(viewId, RemoteViews.RemoteResponse.fromPendingIntent(...))` 完成；回调 Intent 会带上 `EXTRA_CHECKED` 表示当前状态。同理，点击响应可用 `setOnClickResponse(viewId, RemoteResponse)`（等价于 `View.OnClickListener` 触发给定的 RemoteResponse），RemoteResponse 可由 `fromPendingIntent(PendingIntent)` 或 `fromFillInIntent(Intent)` 构造，还可通过 `addSharedElement()` 添加跨 Activity 场景动画的共享元素（API 29 引入 RemoteResponse）。

## Bitmap 内存上限

`AppWidgetManager.updateAppWidget(int[], RemoteViews)` 文档规定：RemoteViews 对象使用的总 Bitmap 内存不能超过填满屏幕 1.5 倍所需内存，即 `屏幕宽 × 屏幕高 × 4 × 1.5` 字节。

## 集合 widget（ListView / GridView / StackView / AdapterViewFlipper）

集合类 widget 必须由 `RemoteViewsService` + `RemoteViewsService.RemoteViewsFactory` 驱动：

- manifest 中声明 service 时必须带 `android:permission="android.permission.BIND_REMOTEVIEWS"`。
- 布局 XML 必须包含 `ListView` / `GridView` / `StackView` / `AdapterViewFlipper` 之一；空 View 必须是集合 View 的兄弟节点。
- provider 的 `onUpdate()` 必须调用 `setRemoteAdapter(viewId, intent)`，intent 指向 `RemoteViewsService` 并携带 `EXTRA_APPWIDGET_ID`。
- `RemoteViewsFactory.onCreate()` 中建立数据连接；注释明确：重活必须放到 `onDataSetChanged()` 或 `getViewAt()`，`onCreate()` 超过 20 秒会触发 ANR。
- 不能依赖 `RemoteViewsService` 实例或其数据的持久性；持久数据应使用 `ContentProvider`。

集合子项不能用 `setOnClickPendingIntent()` 单独设点击，只能用「模板 + 填充」模式：集合整体设 `setPendingIntentTemplate()`（模板 PendingIntent 必须是 mutable），子项在 `RemoteViewsFactory` 里调 `setOnClickFillInIntent()`。

数据刷新：调用 `AppWidgetManager.notifyAppWidgetViewDataChanged()` 会触发 `RemoteViewsFactory.onDataSetChanged()`；该方法可在其中同步执行耗时任务，`getViewAt()` 耗时时会显示 `getLoadingView()` 指定视图。

## RemoteCollectionItems（Android 12）

Android 12 起可用 `setRemoteAdapter(int viewId, RemoteViews.RemoteCollectionItems items)` 直接传入集合，无需实现 RemoteViewsFactory，也无需调用 `notifyAppWidgetViewDataChanged()`。适用于集合较小的场景；集合包含大量 Bitmap（传给 `setImageViewBitmap`）时不适用。若集合的布局不固定，需要 `setViewTypeCount()` 指定最大唯一布局数，否则更新使用新布局时 adapter 会被重建。

## 废弃信号（Android 15 / API 35）

`AppWidgetManager.notifyAppWidgetViewDataChanged(int, int)` 及数组重载已在 API 35 废弃，伴随 `RemoteViews.setRemoteAdapter(int, Intent)` 废弃；官方要求改用 `RemoteViews.setRemoteAdapter(int, RemoteViews.RemoteCollectionItems)` 并用 `updateAppWidget(...)` / `partiallyUpdateAppWidget(...)` 更新视图。

## Android 12 新增运行时修改方法

widget 增强页列出 Android 12 起的 RemoteViews 运行时修改方法示例：`setColorStateList(viewId, "setProgressTintList", ColorStateList)`（运行时设置进度条颜色）、`setViewLayoutMargin(viewId, RemoteViews.MARGIN_END, 8f, TypedValue.COMPLEX_UNIT_DIP)`（精确尺寸 margin）。完整清单见 RemoteViews API reference。

## 与已有 `remoteviews-limits.md` 的关系

本文是官方原文层；`remoteviews-limits.md`（B 级）保留 AOSP 源码注释、vivo 实测异常文本和 GL 纹理上限等实测层内容。两者互补：官方上限=1.5× 屏幕内存公式；实测上限=GL 纹理 4096px 静默失败与厂商严格校验。
