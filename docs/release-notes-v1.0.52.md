# Sleepy v1.0.52

> Each course can carry its own node range and clock times, home-screen widgets are managed from inside the app, and UCAS, Beihang, and Northeastern University import natively.

## What's New

### Per-course irregular times

A course that does not fit the standard period grid can now be set on its own card: two periods merged into one, an evening session, or a class at 07:10 before first period. The switch and the time fields live on the course editor, and other courses keep their own settings.

Time grids that start before period 1 or run past the last period can declare edge nodes (第 0 节 / 第 N+1 节). Courses on an edge node appear in the grid, and the node's clock times can be adjusted from the course editor, which offers candidates read from the grid.

In the grid view, a course with custom clock times is drawn at its real position: the capsule starts at the course's start time and ends at its end time, so a 40-minute class takes 40 minutes of height.

### Widget management and editing in-app

Settings now has a Manage home-screen widgets entry: it lists every widget you have placed, and each widget binds to a timetable of your choice, so one launcher can show different timetables in different widgets. What each widget shows is edited on the same screen.

Pressing a widget's edit control on the launcher opens this in-app editor directly, including on third-party launchers.

### Same-name courses with multiple locations

The import preview now warns when a course name appears with more than one location or teacher, instead of merging them silently. Each block keeps its own teacher, room, note, and color. Colors can be automatic, per-group, or custom. Edits apply per row, so fixing one block no longer rewrites the whole course.

### UCAS and Beihang

- University of Chinese Academy of Sciences (#18): week numbers are read from the course detail page, so grid cells show the real weeks instead of a placeholder. The parsing was checked against the DOM captured in the v1.2 collection package.
- Beihang University: native import through the school's iEAS system.
- The about page credits the public repositories behind these adaptations.

### Northeastern University

Northeastern University (jwxt.neu.edu.cn) imports through the school's mobile JSON API: the app reads the current term from the login session, picks the campus, then POSTs the schedule detail form and parses the returned course list (#27). The parser keeps every week/room group a course carries, recognizes lab courses, fills rooms that only name a campus as "待定" (to be assigned), and odd/even week ranges end at the correct week.

This adaptation comes from @jim139129 (PR #29), who verified the import with a real school account.

## Fixes

- Flipping the irregular-time switch in the course editor crashed the app. Fixed.
- Course names carrying parentheses or English text were trimmed on zf_new and classic EAMS imports (#20); they are now stored verbatim.
- Deleting a course, a whole group, or applying an import diff left its edge-node rows behind in the time grid. Unused edge nodes are reclaimed now.
- Widgets on some launchers rendered a blank frame; the bitmaps behind them now stay alive through the whole render pass.
- The today and two-day widgets dropped part of the day's courses; they now show all of them.
- Adding a widget from a third-party launcher could hang on a transparent configure screen. The configure activity now finishes on its own and only opens the editor when you actually edit the widget.
- Widget previews were missing from the system picker.
- Upgrading from a database version 3 install could wipe timetable data; an explicit migration replaces the destructive fallback.
- Invalid custom times in the course editor showed no feedback; they now report what is wrong.
- The schedule-capture helper follows course details that open on another domain, and its tutorial (v1.2) now says to tick every visible field and pack one file per timetable page.

## Known Limitations

- The UCAS detail-page parsing was verified against captured DOM, not against the live system with a school account. If the school changes the page, the parser needs re-checking.
- Northeastern University: the import was verified with a real school account by the contributor (PR #29); the maintainer has no NEU account. If the school changes the API paths or the response shape, the parser needs re-checking.
- `jw.ahu.edu.cn` (added in v1.0.51): reachable off campus, but no import has been run with a real account (the maintainer has no AHU account). Unchanged since v1.0.51.
- `jwxt.nit.net.cn` (added in v1.0.45): campus network or VPN only; off-campus access times out at the TCP layer. Unchanged since v1.0.45.

## Verification

- Tests: 1270 cases, 0 failures, 0 errors.
- APK SHA-256:
  - arm64-v8a: `778d1c55a724b04ed2c201d7aeac346909597406472d50d609fe3bca99043b44` (2,870,815 bytes)
  - armeabi-v7a: `98e9436a3df23e199ceae8a784045e45f481349ea117dfc75b0edf7aee5a6667` (2,868,123 bytes)
  - x86_64: `39a0f4a4fcdb976ed03c3c604f210df566fbf4576e30bcf8fb2f3885b3da76f6` (2,869,921 bytes)
- Build: versionName `1.0.52`, versionCode `53`

---

# Sleepy v1.0.52

> 每门课可以单独设自己的节次和钟点时间;桌面小组件在应用内直接管理和编辑;中国科学院大学、北航、东北大学原生导入。

## 新增功能

### 逐卡非常规节次与时间

一门课不落标准节次格子——两节连上、晚上加课、早上 07:10 的零节——现在在它自己的课卡上设置,不影响别的课。

时间轴比第一节早、比最后一节晚的课表,可以声明边缘节次(第 0 节 / 第 N+1 节)。落在边缘节次上的课会出现在网格里,节次的钟点时间可以在课程编辑页里调,编辑页会给出从网格读出的候选。

网格视图里,设了自定义时间的课按真实钟点绘制:胶囊从上课时间开始、下课时间结束,40 分钟的课占 40 分钟的高度。

### 应用内管理桌面小组件

设置里多了"管理桌面小组件"入口:列出桌面上每个小组件,各自绑定一张课表,同一个桌面可以放不同课表的小组件。每个小组件显示什么,也在这一屏里改。

在桌面点小组件的编辑,直接进应用内的编辑页,第三方桌面同样走这条路。

### 同名课程多地点

导入预览发现同名课程有多个地点或老师时会提醒,不再悄悄合并。每个课块保留自己的老师、地点、备注、颜色。颜色可以自动、按组或自定义。修改按行落库,改一个课块不再重写整门课。

### 中国科学院大学与北航

- 中国科学院大学(#18):周次从课程详情页读取,网格显示真实周次,不再是占位。解析按 v1.2 采集包里的真实 DOM 验证。
- 北京航空航天大学:走学校 iEAS 系统原生导入。
- 关于页已加这些适配所参考的公开仓库致谢。

### 东北大学

东北大学(jwxt.neu.edu.cn)通过学校移动端 JSON 接口导入:应用从登录会话读当前学期、选校区,再提交课表详情表单,解析返回的课程列表(#27)。解析器保留每门课携带的全部周次/教室组合,识别实验课,只写校区没写教室的填"待定",单双周范围的结束周落到正确的周。

本适配来自 @jim139129(PR #29),适配者已用真实学号验证导入。

## 修复

- 课程编辑页一开非常规节次开关就闪退。已修。
- zf_new 和经典 EAMS 导入会削掉带括号、英文的课程名(#20),现在原样入库。
- 删课、删课程组、应用导入 diff 之后,时间轴上的边缘节次行会残留在网格里;没人用了就自动回收。
- 部分桌面上小组件渲染成空白框;画小组件用的 bitmap 现在整个渲染期保持存活。
- 今天和两天小组件会漏掉当天的课,现在显示全。
- 第三方桌面添加小组件时,可能被一个透明配置页卡住;现在配置页自己会结束,只有你真正编辑小组件时才打开编辑器。
- 系统选择器里小组件预览图缺失。
- 数据库版本 3 的安装升级时可能清空课表数据;破坏性回退已换成显式迁移。
- 课程编辑页填了无效的自定义时间,之前没反应,现在会说明哪里不对。
- 采集工具能跟进跨域打开的课程详情;采集教程(v1.2)更新:勾选全部可显示信息,多课表页各打一个包。

## 已知限制

- UCAS 详情页解析按采集到的 DOM 验证,未用学号在真实系统上跑过导入。学校改版后解析需重查。
- 东北大学:导入由贡献者用真实学号验证(PR #29);维护者没有东大账号。若学校改动接口路径或响应结构,解析需重查。
- `jw.ahu.edu.cn`(v1.0.51 收录):校外可达,但没有真实账号跑过导入(维护者没有安大账号)。自 v1.0.51 未变。
- `jwxt.nit.net.cn`(v1.0.45 收录):仅校内网或 VPN 可达,校外直连在 TCP 层超时。自 v1.0.45 未变。

## 验证

- 测试: 1270 cases, 0 failures, 0 errors。
- APK SHA-256:
  - arm64-v8a: `778d1c55a724b04ed2c201d7aeac346909597406472d50d609fe3bca99043b44`(2,870,815 bytes)
  - armeabi-v7a: `98e9436a3df23e199ceae8a784045e45f481349ea117dfc75b0edf7aee5a6667`(2,868,123 bytes)
  - x86_64: `39a0f4a4fcdb976ed03c3c604f210df566fbf4576e30bcf8fb2f3885b3da76f6`(2,869,921 bytes)
- 构建: versionName `1.0.52`, versionCode `53`
