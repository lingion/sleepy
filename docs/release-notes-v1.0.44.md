# Sleepy v1.0.44

### Conflicting courses are visible everywhere
Before, one course could cover another. The grid, Week View, Today page, and widgets now show both courses, with selectable conflict styles and a remembered default top course.

### Three grid conflict styles
Choose stacked offset, folded corner, or side rail. The grid also provides an inset control; Week View, Today, and widgets use side-by-side lanes.

### A slot accepts at most two overlapping courses
Adding a third overlapping course is blocked with an explanation. Import preview reports conflicts, and “append non-conflicting” skips courses that cannot fit within two lanes.

### Navigation and sharing are simpler
The week pager is centered, the toolbar share action opens a format picker, and Back returns to the schedule before offering the exit prompt.

### Verification
The release line has 755 tests with one pre-existing fixture-count failure in `JwProtocolFixtureMatrixTest`.

### Build
- Release tag: `v1.0.44`
- versionCode: `45`

— Lingion

---

# Sleepy v1.0.44

### 冲突课程在所有页面都可见
之前一门课可能完全盖住另一门。现在网格、周视图、今日页和小组件都会显示两门课，并支持选择冲突样式和记住默认置顶课程。

### 网格提供三种冲突样式
可以选择叠层偏移、折角揭示或侧边竖轨，并调整顶卡收窄量。周视图、今日页和小组件使用左右分栏。

### 一格最多容纳两门冲突课程
新增或编辑第三门重叠课程时会被拦截并说明原因。导入预览会列出冲突，“仅追加无冲突”会跳过无法放进两栏的课程。

### 返回和分享更直接
周次切换器移到顶栏中央，分享按钮会打开格式选择，其他页面按返回会先回到课表页，再显示退出提示。

### 验证情况
本版本线共 755 项测试，其中 `JwProtocolFixtureMatrixTest` 有一个既有的 fixture 数量断言失败，与本版本改动无关。

### 构建
- Release 标签：`v1.0.44`
- versionCode：`45`

— Lingion
