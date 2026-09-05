# Sleepy v1.0.31

### Choose Light, Dark, or Follow system
Before, the theme was controlled by one dark-mode switch. The theme screen now offers Light, Dark, and Follow system. On Android 12 and later, Follow system can use Material You wallpaper colors.

### Theme changes reach every widget
Before, widgets could keep cached or hardcoded colors after a theme change. App and widget surfaces now resolve light/dark mode and theme colors from the same settings, including system changes while the app is running.

### Refresh all widgets with one tap
Before, refreshing could update only the WeekGrid or fail on launchers that paused background rendering. The refresh action now keeps the widget update work active long enough for all widget types to finish, with a direct receiver update path for affected OPPO/ColorOS cases.

### Settings sections can collapse
The More Settings page now opens with the first section expanded and the remaining sections collapsed.

### Build
- Release tag: `v1.0.31`
- versionName: `1.0.31`
- versionCode: `32`
- ABI APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`

— Lingion

---

# Sleepy v1.0.31

### 主题支持浅色、深色和跟随系统
之前主题只有一个深色开关。现在主题页提供浅色、深色和跟随系统三种模式。Android 12 及以上选择跟随系统时，可以使用 Material You 的壁纸配色。

### 所有小组件跟随主题
之前，主题变化后小组件可能继续使用缓存颜色或写死的颜色。现在 App 和小组件会从同一套设置解析深浅色与主题，系统运行中切换主题也会同步。

### 一次刷新所有小组件
之前，刷新操作可能只更新周网格，或者在部分启动器暂停后台渲染时失败。现在刷新会确保所有小组件完成更新，并为受影响的 OPPO/ColorOS 场景保留直接更新路径。

### 设置分组可以折叠
更多设置页现在默认展开第一组，其余分组收起。

### 构建
- Release 标签：`v1.0.31`
- versionName：`1.0.31`
- versionCode：`32`
- ABI APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`

— Lingion
