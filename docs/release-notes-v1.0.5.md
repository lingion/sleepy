# Sleepy v1.0.5

### Dark mode keeps text readable
Before, text in text fields, dialogs, and switches could remain black on a dark background because Material 3 components were using light-theme defaults. The theme provider now supplies the active palette throughout the app, so those controls remain readable in dark mode.

### Courses with custom times show their own schedule
Before, a course with an individual start and end time was displayed using the standard period map in the Today card, detail sheet, and list view. Those views now use the course's custom times.

### Build
- Release tag: `v1.0.5`
- The tagged Gradle configuration reports versionName `1.0.4` and versionCode `3`
- Debug APKs: `app-arm64-v8a-debug.apk`, `app-x86_64-debug.apk`

— Lingion

---

# Sleepy v1.0.5

### 深色模式下的文字可以正常阅读
之前，输入框、弹窗和开关里的文字可能仍然是黑色，深色背景下几乎看不见，因为 Material 3 组件使用了浅色主题的默认颜色。现在主题提供器会把当前配色传给整个应用，深色模式下这些控件也能正常阅读。

### 自定义时间课程显示自己的时间
之前，单独设置了起止时间的课程，在今日卡片、详情弹窗和列表中仍会按标准节次显示。现在这些页面会使用课程自身的自定义时间。

### 构建
- Release 标签：`v1.0.5`
- 标签对应 Gradle 配置记录的 versionName 为 `1.0.4`、versionCode 为 `3`
- Debug APK：`app-arm64-v8a-debug.apk`、`app-x86_64-debug.apk`

— Lingion
