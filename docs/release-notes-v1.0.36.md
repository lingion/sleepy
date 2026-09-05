# Sleepy v1.0.36

### Today, TwoDay, and WeekList widgets can scroll
Before, courses below the fixed widget height disappeared. These list widgets now render their full content and expose it through a scrollable strip. WeekView and WeekGrid remain static grids.

### WakeUp ICS exports import with their schedule intact
The ICS importer now reads period ranges, rooms, teachers, week patterns, and the semester anchor from WakeUp exports. It distinguishes real alternating-week courses from courses that alternate rooms, and can use timetable times carried by the events.

### Material 3 layout tokens are consistent
Dark colors, dialog corners, button heights, filled text fields, dividers, and disabled tints now use shared theme tokens across the app.

### Settings cards animate when opened
Collapsible settings sections now expand and fade in together, with a rotating chevron.

### Build
- Release tag: `v1.0.36`
- versionName: `1.0.36`
- versionCode: `37`
- ABI APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`

— Lingion

---

# Sleepy v1.0.36

### 今日、两日和周列表小组件支持滚动
之前，超出桌面小组件固定高度的课程会直接消失。现在这三个列表小组件会渲染完整内容，并通过可滚动条带查看。周视图和周网格仍保持静态网格布局。

### WakeUp ICS 导出可以完整导入
现在会从 WakeUp 导出文件读取节次、教室、教师、周次和学期锚点。解析器能区分真正的单双周课程与交替教室课程，也会使用事件中携带的作息时间。

### Material 3 布局令牌统一
深色配色、弹窗圆角、按钮高度、填充式输入框、分隔线和禁用态颜色现在由统一主题令牌控制。

### 设置卡片展开有动画
可折叠设置现在会同步展开和淡入，箭头也会随之旋转。

### 构建
- Release 标签：`v1.0.36`
- versionName：`1.0.36`
- versionCode：`37`
- ABI APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`

— Lingion
