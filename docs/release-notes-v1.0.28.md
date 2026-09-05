# Sleepy v1.0.28

### Important: the application ID changed
The package changed from `com.lingion.sleepy.debug` to `com.lingion.sleepy`. Android installs this as a separate app. Export or back up the timetable from the old app, uninstall it, install this build, import the data, and add the widgets again.

### WeekGrid text fits small widgets
Before, vertical course names could overflow short or narrow cards. Characters are now measured individually and kept within the available height, including mixed CJK, Latin, and punctuation.

### WeekGrid colors match the app
The widget now uses the stable course-group color logic used by the timetable. User-selected course colors still take priority.

### Vertical punctuation is optional
A disabled-by-default setting can replace horizontal punctuation with vertical Unicode forms in vertical course names.

### Build
- Release tag: `v1.0.28`

— Lingion

---

# Sleepy v1.0.28

### 重要：应用包名已改变
包名从 `com.lingion.sleepy.debug` 改为 `com.lingion.sleepy`。Android 会把它安装成独立应用。请先在旧应用导出或备份课表，卸载旧应用，安装本版本，重新导入课表，再添加桌面小组件。

### WeekGrid 小组件文字不再溢出
之前，卡片较窄或较矮时竖排课程名可能超出边界。现在会逐字测量并限制在可用高度内，中英文和标点混排也会处理。

### WeekGrid 颜色与 App 统一
小组件现在使用与课表相同的稳定课程组配色逻辑，手动选择的课程颜色仍然优先。

### 竖排标点可选
新增一个默认关闭的设置，可以把竖排课程名中的横排标点替换为竖排 Unicode 形式。

### 构建
- Release 标签：`v1.0.28`

— Lingion
