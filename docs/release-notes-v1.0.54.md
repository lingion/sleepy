# Sleepy v1.0.54

> Build your own theme color by hand or with one tap; going back now lands on the exact page you left; the timetable management page gains an export shortcut; the direct-import browser keeps its desktop mode and survives rotation; UCAS grid pages and HEBZYHJ multi-week pulls import correctly.

## What's New

### Custom theme colors

The appearance page grid lists the five presets, then every theme you have created, and ends with a dashed "+" card that always stays last. Tap it to create a theme: name it, then either tap **Random** for an instant scheme, pick one seed color and let the app derive the full set, or hand-pick each of the four source roles — Primary, Secondary, Tertiary, and the surface neutral — each with a plain-language note on where it shows up. A live preview redraws the top bar, the week capsule, and a sample course card as you drag the color pickers.

A saved theme appears as a card right after the presets and follows the app instantly, widgets included; the "+" card keeps moving to the end as the grid grows. Tap the pencil on its card to fine-tune later, or scroll to the bottom of the editor to delete it (deleting the theme in use falls back to Lilac). There is no cap on how many you keep.

The preset formerly labeled "默认淡紫" (Default Lilac) is now just "Lilac" — it was never more default than the others.

### Back returns to the exact page you left

Leaving a stacked page (settings sub-pages, the import flow, the appearance page, and the other overlay screens) and coming back used to reset everything: the general-settings page collapsed its folds and jumped to the top, the school list forgot what you had typed and scrolled to, the tab you switched away from reset its scroll. Each page's state now survives the round trip — scroll position, expanded folds, typed search text, the exact step of the import flow. The one deliberate exception: leaving the add-course form still discards the empty form instead of bringing it back half-filled.

### Export from the management page

The timetable management page has a fifth card, "导出当前课表" (Export current timetable). It opens the existing export panel, which shares the native format, WakeUp JSON, ICS, or plain text.

### Course-name options join the home display settings

The week-view and grid-view course-name options (original vs. alias) moved into the "主页显示设置" (home display) fold, as two toggle rows: "周视图显示别名" (week view shows alias) and "网格视图显示别名" (grid view shows alias). Same keys, same behavior — the switches are just where you'd look for them now.

### Desktop-mode toggle in direct import (issue #18)

The direct-import WebView has a desktop-site toggle. The button icon now switches with the state — phone icon in mobile mode, desktop icon in desktop mode — and rotating the screen no longer drops you out of the import.

### UCAS and HEBZYHJ import fixes

- **UCAS (issue #18)**: grid pages whose course links use id patterns outside the common anchor set (the reporter's school serves two extra shapes) are now recognized by fingerprint, with the reporter's own captures replaying as fixtures. The SEP portal page gives a precise path hint instead of a dead end.
- **HEBZYHJ (河北资源环境职业技术学院, `qz_app`)**: the school's mobile endpoint returns one week per request, so only the current week made it into the timetable. The app now walks the term's week list and merges every week, dropping duplicate rows.

## Fixes

- Back/navigation no longer resets scroll position, fold state, search text, or import progress (see above).
- Editing a course's alias (the display name set in the course editor) now actually saves: an internal row comparison missed the alias field, so alias-only edits were silently dropped.
- UCAS personSchedule grid pages with unusual link shapes now import.
- HEBZYHJ timetables keep all weeks of the term, not just the current one.
- The direct-import screen stays put across rotation; the desktop-mode icon reflects its own state.

## Known Limitations

- Custom themes live in app storage (a JSON blob), not the timetable database: they do not ride along with timetable backup/export files.
- UCAS: verified against the reporter's captured pages; the maintainer has no UCAS account. If UCAS changes its page shapes, the fingerprint rules need re-checking.
- HEBZYHJ: verified against captured responses; the live login path has not been run with a real account.

## Verification

- Tests: 1694 cases, 0 failures, 0 errors
- APK SHA-256:
  - arm64-v8a: 7846f274af91deb6b7929ee2823a17464a7e39b1bae4e701268ebd1d7c14a2a8
  - armeabi-v7a: 7fce6352bdbb783c6911385b53e9d411a0e8d16bb22e34ee696ea73ee6bbd925
  - x86_64: 4059171799b40e793f35970411fd8f3f26410055eba78f0b13c46ebb09d7f331
- Build: versionName 1.0.54 / versionCode 58

---

# Sleepy v1.0.54

> 主题颜色支持一键随机或逐项自定并保存为皮肤;返回恢复到你离开时的那一页;课表管理页新增导出入口;教务直连浏览器桌面模式图标随状态切换且横屏不再退出;UCAS 网格页与 HEBZYHJ 逐周抓取导入正确。

## 新增功能

### 自定义主题颜色

外观页网格顺序为:五套预设 → 你创建的主题逐张紧随 → 末尾一张虚线加号卡,加号永远排整个网格最后。点按创建主题:命名后可以「随机生成」直接出一套,选一个主色让应用派生全套,或对四个源角色(Primary/Secondary/Tertiary/表面中性色)逐个手选,每个角色附一句人话说明它出现在哪些位置。拖动取色器时,实时预览同步重绘顶栏、周胶囊和课程卡样例。

保存后新主题以卡片形式紧跟预设排入网格,应用即时跟随,小组件同步;主题越多,加号越往后挪,始终垫底。卡上铅笔进微调;编辑器滑到底可删除(删除使用中的主题回落到淡紫)。数量不设上限。

原「默认淡紫」更名为「淡紫」——它并不比其他四套更默认。

### 返回恢复到离开时的页面

从叠层页(设置二级页、导入流程、外观页等)离开再返回,之前会全部重置:通用设置折叠全收、跳回顶部;学校列表丢搜索词、回到列表开头;切走的 tab 丢失滚动位置。现在滚动位置、折叠展开、搜索词、导入流程步骤全部原样恢复。唯一例外:离开加课表单仍按原设计丢弃空表单,不恢复半填状态。

### 管理页导出入口

课表管理页新增第五张卡「导出当前课表」,进入既有导出面板,支持原生格式 / WakeUp JSON / ICS / 纯文本分享。

### 课程名选项归入主页显示设置

周视图与网格视图的课程名选项(原名/别名)移入「主页显示设置」折叠卡,呈现为两行开关:「周视图显示别名」「网格视图显示别名」。存储键与行为不变,只是位置归组。

### 教务直连桌面模式开关 (issue #18)

教务直连 WebView 的桌面模式按钮图标随状态切换(手机模式手机图标/桌面模式桌面图标),横屏旋转不再退出导入。

### UCAS 与 HEBZYHJ 导入修复

- **UCAS (issue #18)**:报告人学校的网格页课程链接 id 形态在通用锚点集之外,现按双指纹识别,报告人真实采集包已回放为 fixture;SEP 门户页给出精确动线指引。
- **HEBZYHJ (河北资源环境职业技术学院, `qz_app`)**:该校移动端点单次只返回一周,导致只抓到当前周。现按学期周列表逐周抓取合并,整行去重。

## 修复

- 返回不再重置滚动位置、折叠状态、搜索词、导入进度(见上)。
- 编辑课程填写的别名现在能正常保存:内部行比对漏了别名字段,只改别名的编辑被静默丢弃。
- UCAS personSchedule 非常规链接形态的网格页可正常导入。
- HEBZYHJ 课表保留学期全部周次,不再只剩当前周。
- 教务直连页横屏旋转不退出;桌面模式图标反映自身状态。

## 已知限制

- 自定义主题存于应用存储(JSON),不随课表备份/导出文件走。
- UCAS:以报告人采集页验证;维护者无 UCAS 账号,页面形态变更需重新核对。
- HEBZYHJ:以抓包响应验证;真实账号登录路径未跑通验证。

## 验证

- 测试:1694 条,0 失败,0 错误
- APK SHA-256:
  - arm64-v8a: 7846f274af91deb6b7929ee2823a17464a7e39b1bae4e701268ebd1d7c14a2a8
  - armeabi-v7a: 7fce6352bdbb783c6911385b53e9d411a0e8d16bb22e34ee696ea73ee6bbd925
  - x86_64: 4059171799b40e793f35970411fd8f3f26410055eba78f0b13c46ebb09d7f331
- 构建:versionName 1.0.54 / versionCode 58
