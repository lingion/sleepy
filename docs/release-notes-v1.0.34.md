# Sleepy v1.0.34

### Widget and in-app course colors are independent
Before, one colorless switch affected both widgets and the in-app schedule. The settings are now separate: `widget_colorless` controls widgets, while `course_colorless` controls the schedule grid and Today page. The new course setting starts off after updating.

### Course colors and text contrast share one implementation
Course color calculation is now consistent across cards, lesson rows, Today, and widgets. Text on dark custom course colors changes to white so it remains readable.

### Widgets use synchronous rendering
All five widgets now use the same synchronous RemoteViews + Canvas path. This removes the asynchronous rendering path that could be frozen by some launchers.

### Settings are reorganized
Appearance and General now have clearer responsibilities, and Follow System is split into light and dark choices.

### Odd/even courses use their actual weeks
Before, single- and double-week courses could be expanded into one continuous start-to-end range. Courses now appear only on their recorded weeks.

### Android backup rules point to the real preferences file
The platform backup and device-transfer rules now reference `sleepy_prefs.xml`, which is the file used by AppPrefs.

### ICS export handles alternating weeks and custom times
Single- and double-week courses now export with `INTERVAL=2`, and courses with custom times export those times.

### Build
- Release tag: `v1.0.34`
- versionName: `1.0.34`
- versionCode: `35`
- ABI APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`

— Lingion

---

# Sleepy v1.0.34

### 小组件和 App 内课程颜色分开控制
之前一个“统一课程底色”开关同时影响小组件和 App 内课表。现在分成两个设置：`widget_colorless` 只控制小组件，`course_colorless` 控制课表网格和今日页。更新后新的课程设置默认关闭。

### 课程颜色和文字对比度统一处理
课程卡、lesson 行、今日页和小组件现在使用统一的课程配色逻辑。深色自定义课程底色会自动使用白色文字，保持可读。

### 小组件统一使用同步渲染
五种小组件现在都走同步 RemoteViews + Canvas 路径，移除了部分启动器可能冻结的异步渲染路径。

### 设置页重新分组
外观和通用设置的职责更清楚，跟随系统也拆成浅色和深色两个选择。

### 单双周课程按实际周次显示
之前单双周课程可能被扩展成连续的起止周区间。现在只会在课程记录的实际周次显示。

### Android 备份规则指向真实偏好文件
平台自动备份和设备迁移规则现在指向 AppPrefs 实际使用的 `sleepy_prefs.xml`。

### ICS 导出支持单双周和自定义时间
单双周课程现在以 `INTERVAL=2` 导出，设置了自定义时间的课程也会导出对应时间。

### 构建
- Release 标签：`v1.0.34`
- versionName：`1.0.34`
- versionCode：`35`
- ABI APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`

— Lingion
