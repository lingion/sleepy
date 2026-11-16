# Sleepy v1.0.66

> Preserve WakeUp import details, identify missing period times, correct academic imports and glass interface rendering, and clean up update previews.

## What's New

This maintenance release focuses on the fixes below.

## Fixes

<a id="en-wakeup-fields"></a>

- Preserve the timetable name, semester length, course notes and enabled custom class times when importing a WakeUp sharing code. WakeUp JSON and encoded share text also retain the declared semester length.

  Evidence: PR #461.

<a id="en-import-periods"></a>

- Suggest missing period times without overlapping the surrounding source times. If a period cannot fit within the day, leave it for you to complete. Import confirmation checks invalid clock times, overlapping periods and missing times required by courses; explicit source times remain available for review.

  Evidence: PR #460.

<a id="en-xiaoai-missing-time"></a>

- Show the course name and the first period missing a configured time when exporting to Xiaoai Schedule. You can locate the missing entry directly instead of receiving only a general invalid-timetable message. Course period ranges remain intact.

  Evidence: PR #462.

<a id="en-update-preview-html"></a>

- Render HTML metadata in the update preview as intended. Release-note anchors and other HTML tags no longer appear as literal source, while visible text and inline code remain readable.

  Evidence: PR #465.

<a id="en-gdut-import"></a>

- Read Guangdong University of Technology timetable data inside nested frames. Recognize complete course records in supported Chengfang, RUC and SHCC script layouts even when page variables are renamed. Check accessible frames for an expired session before accepting cached course data.

  Evidence: PR #448.

<a id="en-script-json"></a>

- Read supported Zhengfang, URP and Xiamen University script data by its course fields when variable names or object wrappers change. Ignore sample data inside comments and strings, while retaining each format's week numbering and course grouping.

  Evidence: PR #452, included in PR #448.

<a id="en-script-bindings"></a>

- Follow the actual course and coordinate references in Classic EAMS, old Shuwei, East China University of Political Science and Law, SIMC and Xi'an University of Architecture and Technology scripts. Accept supported declaration and whitespace changes while retaining teachers, week patterns, period mappings and explicit class times.

  Evidence: PR #451, PR #454, PR #458, included in PR #448.

<a id="en-tsinghua-import"></a>

- Retain Tsinghua course details when timetable script functions or data buffers are renamed. Keep inherited courses, calendar adjustments, explicit class times and teacher notes associated with the corresponding course.

  Evidence: PR #453, included in PR #448.

<a id="en-complete-semester"></a>

- Complete all teaching-week requests before reporting a successful import from Qiangzhi mobile or Boya postgraduate systems. Boya uses the identified current semester and includes the final week in its date range. Report an incomplete response instead of importing only part of the semester.

  Evidence: PR #455, included in PR #448.

<a id="en-frame-selection"></a>

- Select a frame using the course data it actually contains. Empty decorative tables no longer hide a readable timetable elsewhere, and a successfully parsed timetable is retained even if the page still contains loading text.

  Evidence: PR #456, included in PR #448.

<a id="en-table-layouts"></a>

- Find supported BFA, SDU, JXAU, GDEI, NUA and DHU course tables after decorative containers are renamed or menu and title rows are added. Read equivalent repeated layouts once and reject ambiguous candidates that contain different timetables.

  Evidence: PR #457, included in PR #448.

<a id="en-json-arrangements"></a>

- Preserve courses spanning empty periods, week lists, commas in room names, all date groups and numbered class slots in the affected KUST, NUIT, SCU, ZJU, YETHAN and WISEDU formats. Isolate malformed rows so valid courses remain readable; reject explicit invalid durations, endpoints and weekdays.

  Evidence: PR #459, included in PR #448.

<a id="en-postgraduate-tables"></a>

- Read eleven supported postgraduate HTML layouts from their actual course tables after surrounding containers or menus change. Retain period positions across blank rows and merged cells, including Zhejiang University's multi-page tables. Added title rows no longer shift the affected grid period numbers.

  Evidence: PR #463.

<a id="en-gxnzd-entry"></a>

- Update Guangxi Agricultural Vocational and Technical University's academic entry to `https://vpn.gxnzd.com.cn`, retaining its existing Zhengfang protocol.

  Evidence: PR #449.

<a id="en-glass-menu"></a>

- Align the glass backing with the text and outline of single-choice menus during opening, closing and fractional-pixel placement.

  Evidence: PR #446 (contributor @Kerry1020).

<a id="en-glass-import-rendering"></a>

- Keep the academic login page's hardware rendering separate from glass backdrop replay. Retain and update the floating controls' webpage background during scrolling, correcting the clearing and coordinate handling that could cause white flashes or a displaced backdrop.

  Evidence: PR #447 (contributor @Kerry1020).

<a id="en-glass-import-controls"></a>

- Place the glass import page's back action, collapsible address field, edit/confirm action, conditional academic-entry shortcut, user-agent switch and refresh action on one row. Show the school name below it, and separate the bottom instructions from the import button.

  Evidence: PR #447 (contributor @Kerry1020).

<a id="en-import-back"></a>

- Return from the academic login page to the school list with the search preserved. The top back action and system Back without webpage history follow the same route in both themes; leaving the school list exits the import flow.

  Evidence: PR #447 (contributor @Kerry1020).

## Known Limitations

- Missing or overlapping source times still require correction before import or export. Generated suggestions cannot establish a school's actual timetable. Xiaoai continues to enforce its supported week and period ranges; local payload validation does not establish a successful import in the Xiaoai app.
- The academic changes have offline capture and regression coverage. They do not establish a fresh authenticated import at every affected school. If your school requires a VPN or a new login, complete that step before importing.
- The final glass import rendering changes and menu-edge corrections still need further device validation. They are not a guarantee that every GPU or WebView rendering issue is resolved.

## Verification

- Release baseline: published `v1.0.65` (`3a7cbed352f4f8e1144e2e532840b9bea93b25d3`). Candidate source: `9976ecd464f23055474dfbf65d06f35788d0c901`.
- CI run `43902187903` passed all seven checks: commit lint, school catalog, collector tests, academic fetch scripts, Android unit tests, Android lint and APK assembly. The test job ran the full app and glass-runtime host suites, the Room schema check and the Release console lifecycle check.
- Planned release version: `1.0.66 / 10066`, derived from the release tag. These source checks are separate from verification of the final tagged APKs and their SHA-256 fingerprints.

### Maintenance and documentation

- Move glass research material out of the application repository and remove unused Dock experiment hooks. Production rendering and product regression coverage remain in the app.

  Evidence: PR #443 (contributor @Kerry1020), PR #445 (contributor @Kerry1020).

- Clarify the Android and iOS release scope in the product documentation, link the official user guides, and identify WebDAV as manual backup and restore already available in v1.0.65.

  Evidence: PR #450 (contributor @Kerry1020).

### Contributors this release

- @Kerry1020: glass menu alignment, academic import rendering and controls, return navigation, research cleanup and product documentation. PR #443, PR #445, PR #446, PR #447, PR #450.

---

# Sleepy v1.0.66

> 保留 WakeUp 导入字段，定位缺失节次时间，修复教务导入与玻璃界面绘制。

## 新增功能

本版集中修复已有功能，具体变化如下。

## 修复

<a id="zh-wakeup-fields"></a>

- WakeUp 分享口令导入保留课表名称、学期总周数、课程备注和已启用的自定义上课时间。WakeUp JSON 与编码分享文本同样保留声明的学期总周数。

  依据：PR #461。

<a id="zh-import-periods"></a>

- 缺失节次的时间建议避开前后来源作息。当天放不下时留空，由你补充。导入确认检查非法钟点、跨节重叠及课程所需的缺失时间，来源明确给出的时间保留供检查。

  依据：PR #460。

<a id="zh-xiaoai-missing-time"></a>

- 导出到小爱课程表时，缺少作息会显示具体课程名称和首个缺失节次，可直接定位需要补充的时间。课程原有节次范围保持完整。

  依据：PR #462。

<a id="zh-update-preview-html"></a>

- 更新预览会正确处理 HTML 元数据。更新说明中的锚点和其他 HTML 标签不再以源码形式显示，标签包裹的文字与行内代码仍保持可读。

  依据：PR #465。

<a id="zh-gdut-import"></a>

- 读取广东工业大学多层嵌套页面中的课表。乘方、人大和上海海关的已支持脚本布局按完整课程字段识别，页面变量改名后仍可读取；接受缓存课程前检查可访问页面的登录状态。

  依据：PR #448。

<a id="zh-script-json"></a>

- 正方、URP 与厦大已支持的脚本数据按课程字段读取，兼容变量名和对象包装变化。忽略注释、字符串中的示例数据，保留各格式原有周次编号与课程分组。

  依据：PR #452，随 PR #448 合入。

<a id="zh-script-bindings"></a>

- 经典 EAMS、旧树维、华政、SIMC 与西建大脚本沿实际课程和坐标引用读取，兼容已支持的声明方式及空白变化，保留教师、周次规则、节次映射与明确上课时间。

  依据：PR #451、PR #454、PR #458，随 PR #448 合入。

<a id="zh-tsinghua-import"></a>

- 清华课表脚本更换函数或数据缓冲名称后仍保留课程明细。继承课程、调课校历、明确上课时间与教师备注继续关联到对应课程。

  依据：PR #453，随 PR #448 合入。

<a id="zh-complete-semester"></a>

- 强智移动端与博雅研究生平台完成全部教学周请求后再返回导入成功。博雅使用已识别的当前学期，日期范围包含结束日所在周；响应不完整时明确报错，避免只导入部分学期。

  依据：PR #455，随 PR #448 合入。

<a id="zh-frame-selection"></a>

- 根据页面实际课程数据选择课表所在的 frame，空装饰表不再挡住其他页面中的可读课表。已经解析成功的课程不会因页面残留加载文字而丢失。

  依据：PR #456，随 PR #448 合入。

<a id="zh-table-layouts"></a>

- BFA、SDU、JXAU、GDEI、NUA、DHU 已支持的表格布局兼容装饰容器改名、菜单表和标题行变化。相同的重复课表只读取一次，不同候选存在歧义时拒绝猜选。

  依据：PR #457，随 PR #448 合入。

<a id="zh-json-arrangements"></a>

- 修正 KUST、NUIT、SCU、ZJU、YETHAN、WISEDU 相应格式的安排读取，保留跨空节课程、周次列表、教室名内逗号、全部日期组与数字课次槽。坏行单独隔离，其他有效课程继续读取；明确非法的时长、终点和星期拒绝导入。

  依据：PR #459，随 PR #448 合入。

<a id="zh-postgraduate-tables"></a>

- 十一种已支持的研究生 HTML 布局按实际课程表格读取，兼容外围容器和菜单变化。保留空白行、合并单元格对应的物理节次，包括浙大多页课表；相应网格新增标题行后，课程节次不再偏移。

  依据：PR #463。

<a id="zh-gxnzd-entry"></a>

- 广西农业职业技术大学教务入口更新为 `https://vpn.gxnzd.com.cn`，继续使用原有正方协议。

  依据：PR #449。

<a id="zh-glass-menu"></a>

- 修正单选菜单在展开、收起与半像素位置时，玻璃背板与文字、轮廓的错位。

  依据：PR #446（contributor @Kerry1020）。

<a id="zh-glass-import-rendering"></a>

- 教务登录网页保持硬件渲染，玻璃背景采样与网页自身绘制分开处理。滚动时保留并更新悬浮控件的网页背景，修正可能造成白闪、背景错位的清空与坐标处理。

  依据：PR #447（contributor @Kerry1020）。

<a id="zh-glass-import-controls"></a>

- 玻璃教务导入页的返回、可收缩地址框、编辑或确认、按条件显示的教务入口、UA 切换和刷新保持同一行。学校名称独立显示在下一行，底部操作提示与导入按钮分开。

  依据：PR #447（contributor @Kerry1020）。

<a id="zh-import-back"></a>

- 从教务登录页返回学校列表并保留搜索状态。两个主题的顶部返回、无网页历史时的系统返回采用同一路径；在学校列表再次返回才退出导入流程。

  依据：PR #447（contributor @Kerry1020）。

## 已知限制

- 来源作息缺失或重叠时，仍需修正后再导入或导出。自动建议不能证明学校真实上课时间。小爱仍受其周次和节次范围限制，本地载荷校验不等同于小爱应用实机导入成功。
- 教务改动有离线采集包与回归测试覆盖，不代表每所受影响学校都重新完成了账号认证导入。需要学校 VPN 或重新登录时，请先完成这些操作。
- 最终玻璃教务页绘制与菜单边缘修正仍需补充设备验证，不承诺解决所有 GPU 或 WebView 绘制问题。

## 验证

- 发布基线：已发布 `v1.0.65`（`3a7cbed352f4f8e1144e2e532840b9bea93b25d3`）。候选源码：`9976ecd464f23055474dfbf65d06f35788d0c901`。
- CI `43902187903` 的七项检查全部通过：提交规范、学校目录、采集器测试、教务采集脚本、Android 单元测试、Android lint 与 APK 构建。测试任务执行了完整应用与玻璃运行时 host 测试、Room schema 核对及 Release 控制台生命周期检查。
- 计划发布版本为标签派生的 `1.0.66 / 10066`。上述源码检查与最终标签对应 APK 的版本、签名和 SHA-256 核验分别记录。

### 工程与文档维护

- 将玻璃研究资料移出应用仓库，移除无使用方的 Dock 实验入口。应用保留生产绘制与产品回归。

  依据：PR #443（contributor @Kerry1020）、PR #445（contributor @Kerry1020）。

- 产品文档分别说明 Android 与 iOS 的发布范围，补充官方使用指南入口，明确 WebDAV 是 v1.0.65 已有的手动备份与恢复。

  依据：PR #450（contributor @Kerry1020）。

### 本版贡献者

- @Kerry1020：玻璃菜单对位、教务导入绘制与控件、返回层级、研究资料清理及产品文档。PR #443、PR #445、PR #446、PR #447、PR #450。
