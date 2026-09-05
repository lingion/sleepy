# Sleepy v1.0.43

### About can show available updates
On startup, About can check GitHub Releases once for a newer version. A banner opens the release page, failed checks stay quiet, and the feature can be disabled from About.

### Import can become a new timetable
Import preview now offers “append as new schedule”. The imported courses are combined with the current courses in a new timetable and the timetable expands when more periods are needed.

### Week View has independent zoom
Grid zoom and Week View zoom are now separate, both ranging from 70% to 130%.

### Manual course validation follows the timetable
Course start and length limits now use the timetable's actual maximum period. Existing out-of-range data is reported with the exact slot and period numbers.

### Courses with explicit weeks stay on those weeks
When an imported course lists a specific week without a type, it is now shown only on that week. The editor also has a by-weeks option.

### Display settings apply immediately
Changing week zoom, two-column layout, hidden empty days, grid zoom, or corner radius now redraws as soon as the control is released.

### Build
- Release tag: `v1.0.43`
- versionCode: `44`

— Lingion

---

# Sleepy v1.0.43

### 关于页可以提示新版本
启动时关于页可以检查 GitHub Releases 是否有更新。横幅会打开发布页，检查失败不会打扰你，也可以在关于页关闭这项功能。

### 导入可以追加为新课表
导入预览现在提供“追加为新课表”。导入课程会和当前课程一起放入新课表，需要更多节次时课表会自动扩展。

### 周视图拥有独立缩放
网格缩放和周视图缩放现在分开设置，范围都是 70%–130%。

### 手动课程校验跟随课表节数
课程起始节和连续节数上限现在使用课表实际最大节次。已有越界数据会显示具体时段和节次。

### 指定周次的课程只显示在指定周
导入课程明确列出某一周但没有类型时，现在只会在该周显示。编辑器也新增了按周次选项。

### 显示设置立即生效
松开周视图缩放、两栏、隐藏无课日、网格缩放或圆角滑杆后，界面会立即重绘。

### 构建
- Release 标签：`v1.0.43`
- versionCode：`44`

— Lingion
