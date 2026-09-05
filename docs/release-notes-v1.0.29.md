# Sleepy v1.0.29

### All widgets use the app's course colors
Today, TwoDay, and WeekList now share the same golden-angle HSL color logic as the app and WeekGrid. Custom colors remain authoritative, with separate light and dark adjustments.

### TwoDay is split into two columns
Today and tomorrow now appear side by side, each with its own scrollable list.

### Widget refresh works on OPPO launchers
Before, ColorOS could return an empty list through the old refresh path. Widget updates now use the system `APPWIDGET_UPDATE` broadcast.

### Small layout fixes
Time and room text is darker, course names and locations are vertically centered, and WeekGrid height increased from 300dp to 360dp.

### Build
- Release tag: `v1.0.29`
- ABI APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`

— Lingion

---

# Sleepy v1.0.29

### 所有小组件使用统一课程配色
Today、TwoDay 和 WeekList 现在与 App、WeekGrid 共用黄金角 HSL 配色逻辑。手动颜色仍然优先，浅色和深色模式分别调整明度与饱和度。

### TwoDay 改为左右两栏
今天和明天现在并排显示，各自独立滚动。

### OPPO 启动器上的小组件可以刷新
之前 ColorOS 通过旧刷新路径返回空列表，导致小组件不更新。现在改用系统 `APPWIDGET_UPDATE` 广播。

### 小布局修正
时间和地点文字改为更深的颜色，课程名和地点垂直居中，WeekGrid 高度从 300dp 增加到 360dp。

### 构建
- Release 标签：`v1.0.29`
- ABI APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`

— Lingion
