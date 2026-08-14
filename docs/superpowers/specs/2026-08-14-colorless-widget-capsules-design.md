# Spec: 无色小组件选项 (Colorless Widget Capsules)

## Objective

在"更多设置"页加一个开关："小组件无色模式"。开启后，所有 5 个桌面小组件里的彩色课程胶囊背景变成统一中性灰底（surfaceVariant），文字用 onSurfaceVariant。App 内课表的颜色不受影响。

用户之前见过 WeekList 的一个无色版本，觉得那个风格也不错。这个开关让用户自己选。

## 范围

**改**：5 个小组件的胶囊背景色
- Today / TwoDay / WeekList → `WidgetBitmapRenderers.kt` 的 `pickCourseColor`（2 处调用：drawCourse L130, renderWeekList L348）
- WeekView (第5个) → 纯文本无胶囊，**不受影响**（已经是 plain-text）
- WeekGrid → `WeekGridWidgetProvider.kt` 的局部 `pickCourseColor`（L169, 调用 L377）

**不改**：
- App 内课表 (`CourseTableView.kt` 的 `pickCourseColor`) — 用户没要求改 App 内
- 第5个 WeekView widget — 它本来就是纯文本列表，没胶囊

## 假设

1. 开关只影响小组件，不影响 App 内课表网格（用户说"所有的小组件里面"）
2. 统一中性灰底 = 用主题的 surfaceVariant（跟 DaySummaryCell 的 chipBg 一致），文字 onSurfaceVariant
3. 开关存 AppPrefs（跟 displayMode/showDate 等 widget 相关设置放一起）
4. 开关改变后需要主动刷新小组件（跟切主题一样走 WidgetUpdater）

## 技术方案

### 1. AppPrefs 加一个布尔开关

```kotlin
const val KEY_WIDGET_COLORLESS = "widget_colorless"  // bool default false

fun isWidgetColorless(ctx: Context): Boolean =
    sp(ctx).getBoolean(KEY_WIDGET_COLORLESS, false)

fun setWidgetColorless(ctx: Context, v: Boolean) {
    sp(ctx).edit().putBoolean(KEY_WIDGET_COLORLESS, v).apply()
}
```

### 2. WidgetBitmapRenderers.pickCourseColor 加 colorless 分支

把 `scheme: Scheme` 里的 surfaceVariant / onSurfaceVariant 当灰底用：

```kotlin
private fun pickCourseColor(course: CourseEntity, isDark: Boolean): Int {
    // ... 用户自定义颜色优先 (保留)
    // 无色模式 → 统一中性灰
    if (colorless) return scheme.surfaceVariant   // 新增参数
    // 否则黄金角 HSL
    ...
}
```

需要把 `colorless: Boolean` 透传进去。`Scheme` data class 加一个字段，或在 renderXxx 入口读一次 prefs 传参。

**推荐**：在 `Scheme` data class 加 `surfaceVariant` 已经有了；直接在 `pickCourseColor` 签名加 `colorless: Boolean`，调用方从入口的 `AppPrefs.isWidgetColorless(context)` 读。

### 3. WeekGridWidgetProvider.pickCourseColor 同理

它是局部 fun，读 prefs 后加同样分支。

### 4. MoreSettingsScreen 加"小组件设置"卡片

在可见天数卡片后面加第 5 个 item：

```
小组件设置 (settings_widget)
  └─ 无色模式 (settings_widget_colorless)
       开关：开启后所有小组件课程胶囊变为统一灰色
       onChange → AppPrefs.setWidgetColorless + WidgetUpdater.notifyDataChanged
```

### 5. 开关联动小组件刷新

开关一变，广播 APPWIDGET_UPDATE 让所有小组件重绘。复用现有的 `WidgetUpdater.notifyDataChanged()`。

## 成功标准

1. 更多设置里出现"小组件设置"分组，内含"无色模式"开关
2. 开关开启 → Today/TwoDay/WeekList/WeekGrid 四个有胶囊的小组件胶囊变灰底
3. 开关关闭 → 恢复彩色
4. WeekView（第5个纯文本）不受影响
5. App 内课表颜色不受影响
6. 切换后小组件即时刷新，不用重开 App

## 改动文件清单

| 文件 | 改动 |
|------|------|
| `AppPrefs.kt` | +KEY_WIDGET_COLORLESS + getter/setter |
| `WidgetBitmapRenderers.kt` | pickCourseColor 加 colorless 分支 + 透传 |
| `WeekGridWidgetProvider.kt` | 局部 pickCourseColor 加 colorless 分支 |
| `MoreSettingsScreen.kt` | +小组件设置卡片 + 无色模式开关 |
| `strings.xml` ×6 | +settings_widget / settings_widget_colorless 文案 |

## 风险

- **颜色逻辑三份拷贝**：App / BitmapRenderers / WeekGrid 各有一份 pickCourseColor。这次只改后两份。不改 App 内的（范围明确）。
- **Scheme 字段**：surfaceVariant 已在 WidgetBitmapRenderers.Scheme 里（L37），WeekGrid 的 scheme 也有（L146）。两处都有灰底可用，不用加字段。
