# Sleepy v1.0.45

### Undo timetable data changes
The top bar now offers Undo for imports, replacements, appends, timetable changes, and course edits. A complete import is one undo step; switching timetables is navigation and is not recorded.

### Append conflicts into the current timetable
Import now has an option to append all incoming courses, including conflicts. If a day exceeds two overlapping courses, the app reports the affected days instead of silently dropping them.

### Switch and copy timetables from the home screen
The top-bar timetable switcher shows all tables, and each table can be copied with its settings and courses. Import time is shown, and deleting the last table leaves an explicit empty state.

### Folded-corner size is adjustable
When the folded-corner style is selected, a size slider controls the corner, symbol, and tap target together.

### Zhejiang Ningbo Polytechnic is listed
The school was added to direct import. Its academic system may require the campus network or VPN.

### Imports preserve longer schedules and conflicts
Import paths now keep the larger period count when the incoming timetable exceeds the current table. Fold switching, week-scoped conflict details, stale default-top selections, editing existing conflicts, and append-as-new-table data preservation were fixed.

### Verification
The release line has 798 tests with one pre-existing fixture-count failure in `JwProtocolFixtureMatrixTest`.

### Build
- Release tag: `v1.0.45`
- versionCode: `46`

— Lingion

---

# Sleepy v1.0.45

### 课表数据支持撤回
顶栏现在提供撤回，可以回退导入、覆盖、追加、课表变更和课程编辑。一次完整导入算一个撤回步骤；切换课表属于导航，不会记录。

### 可以把冲突课程追加到当前课表
导入现在可以把所有课程连同冲突一起追加到当前课表。如果某天超过两门重叠课程，App 会指出受影响的日期，不会静默丢课。

### 首页可以切换和复制课表
顶栏课表切换器会列出所有课表，每张课表都可以连同设置和课程一起复制。列表显示导入时间，删掉最后一张后会显示明确的空状态。

### 折角幅度可以调整
选择折角样式后，新增滑杆会同时控制折角、符号和点击区域的大小。

### 新增浙大宁波理工学院入口
学校已加入教务直连列表，但当前访问教务系统可能需要校园网或 VPN。

### 导入保留更长节次并修正冲突状态
当导入课表需要的节次多于当前课表时，所有导入路径现在都会保留较大的节次数。折角切换、按周判定冲突、删除课程后的置顶残留、编辑已有冲突，以及追加为新课表时保留原课表数据等问题也已修正。

### 验证情况
本版本线共 798 项测试，其中 `JwProtocolFixtureMatrixTest` 有一个既有的 fixture 数量断言失败，与本版本改动无关。

### 构建
- Release 标签：`v1.0.45`
- versionCode：`46`

— Lingion
