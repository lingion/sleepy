# Sleepy v1.0.46

### Course conflicts are easier to follow
Before, schedules with three or more overlapping courses could leave the visible course ambiguous. Sleepy now rotates the conflicting courses in place, so each course gets a turn without changing the current week.

### Conflict details are visible when saving
Before, saving an overlapping course did not show enough context to tell which existing course would be affected. Sleepy now lists the actual conflicting courses at the point of confirmation.

### Overlap styles handle any number of layers
Before, the overlap decoration assumed a fixed number of stacked courses. The style now supports arbitrary overlap layers, and the two conflict-style sliders control their independent visual dimensions.

### The bottom bar stays out of the way
Before, the bottom navigation occupied a fixed area while browsing the timetable. Sleepy now uses a floating dock-style bar, with high-refresh-rate animation where the device supports it.

### Settings are easier to scan
Before, the settings screen put unrelated options into one long list. The settings are now organized into tabs, with shorter labels and clearer grouping.

### Direct import works for Chongqing University
Before, Chongqing University was not available in the direct-import list. It can now be imported directly, bringing the supported-school count to 159.

### Small interaction details are preserved
Editing a course now keeps the current week instead of jumping back to the default week. The status-bar icon also follows Sleepy's in-app theme.

### Test coverage and fixtures
Before, third-party login behavior was not covered by a complete fixture. The fixture set now includes that path, and the release was verified with 824 unit tests.

### Build
- versionName: `1.0.46`
- versionCode: `47`
- ABI APKs: `arm64-v8a`, `armeabi-v7a`, `x86_64`

— Lingion

---

# Sleepy v1.0.46

### 冲突课程更容易看清
之前，同一时间有三门以上课程冲突时，当前显示的课程不够明确。现在 Sleepy 会在原位置轮换显示冲突课程，每门课都会轮到，不会改变当前周次。

### 保存时直接显示冲突详情
之前，保存重叠课程时上下文不够，难以判断会和哪门已有课程冲突。现在确认保存时会列出实际冲突的课程。

### 叠层样式不再受固定层数限制
之前，冲突装饰默认按固定数量的课程叠放。现在叠层样式支持任意层数，冲突样式的两个滑杆也分别控制各自的视觉尺寸。

### 悬浮底栏减少遮挡
之前，底部导航固定占用课程表空间。现在改为悬浮 Dock 底栏；设备支持时，课程表交互也会使用高刷新率表现。

### 设置页更容易查找
之前，不同设置项集中在一个较长的列表里。现在设置页改为分 Tab 展示，文案也做了精简，选项分组更清楚。

### 支持直连导入重庆大学
之前，直连导入列表中没有重庆大学。现在可以直接导入重庆大学，支持的学校总数达到 159 所。

### 小操作会保留当前状态
编辑课程后，现在会保留当前周，不再跳回默认周。状态栏图标也会跟随 Sleepy 的应用内主题变化。

### 测试与数据夹具
之前，第三方登录路径没有完整的测试夹具覆盖。现在已补齐该路径，本版本通过 824 项单元测试验证。

### 构建
- versionName：`1.0.46`
- versionCode：`47`
- ABI APK：`arm64-v8a`、`armeabi-v7a`、`x86_64`

— Lingion
