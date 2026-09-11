# Sleepy v1.0.53

> The today home-screen widget scrolls as one piece again on OPPO launchers; UCAS now lands on the real SEP login; courses get an optional alias field; five more schools import natively; NEU ICS files parse by teaching block instead of by event.

## What's New

### Today widget overflow on real OPPO launchers

The today widget on the OPPO launcher was stuck — its top bar vanished, two layers appeared stacked on top of each other, and scrolling did nothing. That was a regression that came back with the v10 per-row children attempt. This release returns the today widget to the same form as TwoDay and WeekList: one opaque long bitmap inside the launcher's native ListView. The top bar is back, the layers don't double up, and the launcher scrolls the whole thing itself.

Cross-vendor compatibility layer is documented for the six main launchers (Huawei, Honor, Xiaomi, OPPO, vivo, Meizu, Samsung) in `docs/widget-vendor-specs/`. Every pin-routable variant has a single source of truth in `PinWidgetRouting`.

### UCAS lands on the SEP login portal

UCAS (`sep.ucas.ac.cn` based) used to drop users onto an error page ("登录失败!") at the start of import — the WebView's X-Requested-With header was getting rejected by SEP's SSO at `/appStore`. The SEP X-Requested-With header is now stripped inside the WebView, and the entry URL is the real SEP portal. UCAS users now see the SEP login form instead of an error page.

### Course alias (issue #26)

Each course now has an optional alias field. You can leave it blank for nothing to change, or set per-scene visibility (week view, grid view, widget) and pick the language. The course keeps its official name internally; the alias shows only where you turn it on.

The native `sleepy-v1` import/export format grows an eleventh optional column for the alias; old files without the column still import unchanged.

### Five more schools import natively

- **Hebei Vocational College of Resources and Environment (河北资源环境职业技术学院)** — the school uses `强智移动教务` (`type=qz_app`); the app reads `ApiUrl` and token from `serverconfig.json`, then fetches through the school's mobile JSON endpoint.
- **Yanshan University graduate platform (燕山大学研究生平台, boya_pp 博雅研究生)** — full adaptation for the graduate school, revived from a community PR.
- **Beijing Jiaotong University (北京交通大学, AA 教学支撑平台, issue #19)** — a same-origin fetch plus a composite source assembles the schedule from the AA portal.
- **Jiangsu Ocean University (江苏海洋大学)** — reuses the existing `zf` (old Zhengfang) path; the entry URL goes through the school's CAS so multi-domain SSO stays alive.
- **Hefei University of Technology (合肥工业大学, issue #25)** — the typed portal URL is rejected by EAMS5 detection, so the app routes through the school catalog entry instead.

The about page credits the public sources behind each adaptation in all six supported languages.

### NEU ICS periods by teaching block (issue #28)

Northeastern University's `.ics` export used to map classes to the wrong period slots when one teaching block contained several course shapes (the ICS harvester took the longest event in a time slot and spread its step count across every event starting there, then lost track of node times). The new parser works at the teaching-block level: a block is the span between two consecutive event start times; within a block, events map to periods by 50-minute steps and the time table is interpolated uniformly, independent of the order events appear in the file. Periods are now monotonic 1..N, anchors land on real clock times, and the seven time shapes in the report map to the correct spans.

Changing the wakeup-schedule table now re-maps course periods to the new absolute times automatically. The import-confirmation dialog's "auto" mode calls the smart config callback through to completion.

### In-app editing honors launchers

Pressing a widget's edit control on the launcher now lands on the in-app editor on the three launchers that don't reach it natively, including Honor / OEM launchers that disappear the widget the moment a transparent configure screen opens. First-time widget add skips the OEM configure page entirely.

The widget picker on every launcher now shows the preview.

## Fixes

- Today widget: scrolling on OPPO launchers now moves; the top bar no longer disappears; no more stacked layers.
- Today widget: the top-row buttons are no longer squashed on narrow 148 dp widths.
- The 15-minute periodic refresh fallback is back in `SleepyApp.onCreate` (the today widget was relying on it after some launchers stopped honoring widget refresh timers).
- Editing a course, then using undo, restores the row. (The undo button disappeared because the apply-diff path stopped capturing the previous state; the capture now runs inside apply-diff itself.)
- Undo's copy-timetable step wraps insert-table + insert-courses in one batch, so the new timetable and its courses undo as a single step.
- "Append only the non-conflicting rows" no longer marks the whole import as conflicting just because the existing table has overflow days. The gate now compares relative to the new import, not to the global ceiling.
- Course editor with irregular times no longer crashes when you flip the switch on.
- Importing a 5-minute placeholder row no longer renders an invisible color stripe; the placeholder has a visible minimum weight.
- Editing a course and switching tabs no longer bounces the schedule view back to the launch default; the view mode now lives at the app-root session layer.
- The "empty timetable" state shows two clear buttons (import / create) instead of routing through the add-course form.
- The course-editor "irregular options" lives under a fold labeled `非常规选项` (irregular options) so the form reads shorter.
- An irregular course that ends inside a gap is now drawn at its real start..end, and overlapping classes with custom times are flagged by their real minute ranges.
- Reading the cross-domain follow-on of a schedule capture now actually follows the detail page when it opens on another domain.
- Honoring a widget from Honor / OEM launchers no longer hides it the moment it lands on the launcher.
- Conflict-row geometry inside the same time axis: own-time courses now anchor by their real minute span and the cluster splits along the time axis, not along a fake minute step.
- The grid time axis is weighted by minutes: a 40-minute period takes 40 minutes of height. Mixed-domain fake conflicts disappear.
- Database version 6: an explicit `MIGRATION_5_6` adds the alias column; the destructive fallback for v3 installs that wiped data is gone.
- The contributors block on the about page links outward to each contributor's profile; PR and issue numbers stay out of the in-app text.

## Known Limitations

- NEU ICS parser: verified against the reporter's own 148-event ICS export (5 teaching blocks, 7 time shapes). The maintainer has no NEU account; if NEU changes the export shape, the parser needs re-checking.
- UCAS: parsing was verified against captured DOM and against the SEP login portal reaching HTTP 200 with and without the X-Requested-With header. The maintainer has no UCAS account; if UCAS changes the page or the SEP auth flow, the parser needs re-checking.
- HEBZYHJ (`qz_app`): server config + 13 parser cases + 2 WebView contract cases; the maintainer has no school account, so the live path has not been run end-to-end. If the school changes the mobile endpoint contract, the parser needs re-checking.
- YSU graduate (`boya_pp`): verified against the original PR author's setup; the maintainer has no YSU account. If the school changes the response shape, the parser needs re-checking.
- BJTU: 12 parser cases (end-to-end, negative, cross-language invariant, routing). The maintainer has no BJTU account, so the live path has not been run end-to-end. If the school changes the AA portal contract, the parser needs re-checking.
- JOU: 6 regression cases; `login_cas.aspx` flow verified by following the SOP. The maintainer has no JOU account, so the live import has not been run. If the school changes the CAS target or the legacy `zf` page, the parser needs re-checking.
- HFUT (issue #25): typed portal URL redirect verified; the maintainer has no HFUT account. If the school changes the EAMS5 portal layout, the route needs re-checking.
- `jw.ahu.edu.cn` (added in v1.0.51): reachable off campus, but no import has been run with a real account (the maintainer has no AHU account). Unchanged since v1.0.51.
- `jwxt.nit.net.cn` (added in v1.0.45): campus network or VPN only; off-campus access times out at the TCP layer. Unchanged since v1.0.45.

## Verification

- Tests: 1639 cases, 0 failures, 0 errors.
- APK SHA-256:
  - arm64-v8a: `1431babe8df7bd6b64109dd89c76b2ad620aa0f23dbdfb219605c6973631d353` (2,916,991 bytes)
  - armeabi-v7a: `5b4f75f721aa895d099b9375082097b13193b6408ae275e21987c226f24c2ad3` (2,914,299 bytes)
  - x86_64: `ffcdb2b46185cc1fd5cb6e6c75e8b6673917f7a0e35af2a2f19906d43af69a12` (2,916,097 bytes)
- Build: versionName `1.0.53`, versionCode `57`

---

# Sleepy v1.0.53

> 今日小组件在 OPPO 桌面上重新能拖;UCAS 进的是真正的 SEP 登录页;课程有别名;五所新学校原生导入;NEU ICS 按教学块整表重建节次链路。

## 新增功能

### 今日小组件 OPPO 桌面滚动修复

之前 OPPO 桌面上的今日小组件卡死了:顶栏不见,两层图错位叠在一起,拖动没反应。这是 v10 改逐行子项带回来的回归。本版回到 TwoDay / WeekList 的形态:一张不透明的长位图,包在桌面自带的 ListView 里。顶栏回来了,图层不重叠,桌面自己滚。

跨厂商兼容层落地文档已写进 `docs/widget-vendor-specs/`,覆盖六家主流桌面(华为 / 荣耀 / 小米 / OPPO / vivo / 魅族 / 三星)。所有可 pin 的小组件变体在 `PinWidgetRouting` 里有单一真值源。

### UCAS 进的是 SEP 登录门户

UCAS(以 `sep.ucas.ac.cn` 为入口)以前打开导入页就丢用户进一个错误页(标题「登录失败!」),根因是 WebView 强注的 `X-Requested-With` 头被 SEP 在 `/appStore` 拦截后改返 JSON 401。SEP 域的 `X-Requested-With` 头现在被剥掉,入口 URL 直指 SEP 门户。UCAS 用户打开应用看到的是 SEP 登录表单,不再是错误页。

### 课程别名(issue #26)

每门课现在有一个可选的别名字段。可以空着(等同没改),也可以按场景(周视图 / 网格 / 小组件)分别打开,并挑选文案语言。课程内部仍存官方名,别名只在你开启的位置显示。

原生 `sleepy-v1` 导入导出格式多了一列可选的别名;老的没有这列的文件导入行为不变。

### 五所新学校原生导入

- **河北资源环境职业技术学院** — 学校用的是强智移动教务(`type=qz_app`),应用从 `serverconfig.json` 读出 `ApiUrl` 和 token,再走学校的移动端 JSON 接口。
- **燕山大学研究生平台(boya_pp 博雅研究生)** — 研究生端全套适配,从社区 PR 复活合入。
- **北京交通大学(AA 教学支撑平台,issue #19)** — 同源 fetch 加组合源拼接,从 AA 门户拼出课表。
- **江苏海洋大学** — 复用既有的 `zf`(老正方)通路,入口走学校自建 CAS,跨域 SSO 不被切断。
- **合肥工业大学(issue #25)** — 类型化门户 URL 被 EAMS5 探测拒绝时,应用改走学校目录项入口。

关于页已经收录所有这些适配所参考的公开来源,六语言展示。

### NEU ICS 教学块模型节次(issue #28)

东北大学的 `.ics` 导出,以前一个教学块里夹了多种时间形态(同一开始时刻里最长事件步数套给该时刻所有事件,块内的共享 end 节点时间倒挂、节点整行丢失)就会把节次打乱。新解析器在教学块级别工作:块 = 相邻事件开始时刻之间的区间;块内事件按 50 分钟一节折算、节次跨块累加;时间表按块内均匀插值,与事件在文件里的出现顺序无关。节次现在单调 1..N,锚点落在真实钟点上,导出里的七种时间形态各自映射到正确的区间。

改了作息表之后,课程节次按新绝对时间自动重映射。导入确认框的「自动」模式现在能完整接通 smartConfig 回调。

### 应用内编辑兼容更多桌面

在桌面上点小组件的编辑按钮,即使桌面本身不直跳,也能落到应用内的编辑页;荣耀 / OEM 桌面被透明 configure 页卡住再消失小组件的链路断了。首次添加小组件时绕过 OEM 配置页。

所有桌面的小组件选择器里现在能看到预览图。

## 修复

- 今日小组件:OPPO 桌面上现在能拖动;顶栏不再消失;没有图层错位。
- 今日小组件:148 dp 窄档下,顶行按钮不再被挤变形。
- 15 分钟 periodic 兜底重新挂在 `SleepyApp.onCreate` 上(部分桌面取消了对小组件定时刷新的支持后,今日组件靠它兜底)。
- 编辑课程后,撤回按钮恢复(之前撤销按钮消失是因为 apply-diff 路径不再 capture 旧状态,现在 capture 在 apply-diff 内部完成)。
- 撤回的复制课表步把建表和插课包成一个批,新表和表里的课作为一步撤回。
- 「仅追加不冲突」不再因为原表已有超层天就把整次导入标成冲突;判定改为相对导入,不相对全局上限。
- 课程编辑页打开非常规节次开关不再闪退。
- 5 分钟的占位行不再渲染成隐形色条;占位有了可见下限。
- 编辑课程后切 tab 不再把课表视图弹回启动默认;视图模式提升到 AppRoot 会话层。
- 「无表空态」显示导入 / 建表两个清晰的按钮,不再借道加课表单。
- 课程编辑页的「非常规选项」收进标着 `非常规选项` 的折叠栏,主表单读起来更短。
- 终止于空隙的非常规课按真实起止钟点绘制;带自定义时间的重叠按分钟级真实区间判定冲突。
- 课表采集跨域打开的课程详情现在真跟过去了。
- 荣耀 / OEM 桌面添加小组件不再落地即消失。
- 同一时间轴内的冲突簇:own-time 课按真实分钟跨度锚点;聚簇按时间域切,不按虚假的分钟步切。
- 网格时间轴按分钟加权:40 分钟的节占 40 分钟的高度。混合域的假冲突消除。
- 数据库 v6:`MIGRATION_5_6` 显式添加 alias 列;v3 安装上曾经清空课表数据的破坏性回退已移除。
- 关于页贡献者区块改为链接外指,应用内文案里不再出现具体 PR / issue 编号。

## 已知限制

- NEU ICS 解析器:已在报告者本人的 148-event ICS 导出(5 教学块、7 时间形态)上验证通过。维护者没有 NEU 账号,学校若改动导出形态,解析器需重查。
- UCAS:解析已按采集 DOM 验证,SEP 登录门户在带/不带 `X-Requested-With` 头下均返 200。维护者没有 UCAS 账号,学校若改版或 SEP 鉴权流程变动,解析器需重查。
- HEBZYHJ(`qz_app`):服务器配置 + 13 条解析器用例 + 2 条 WebView 契约用例已落地;维护者没有该校账号,未在真实系统上跑过完整链路。学校若改动移动端契约,解析器需重查。
- 燕大研究生(`boya_pp`):已按原作者环境验证;维护者没有燕大账号。学校若改动响应结构,解析器需重查。
- BJTU:12 条解析器用例(端到端、负例、跨语言 invariant、路由)已落地;维护者没有 BJTU 账号,未在真实系统上跑过完整链路。学校若改动 AA 门户契约,解析器需重查。
- JOU:6 条回归用例已落地,`login_cas.aspx` 链路已按 SOP 走通;维护者没有 JOU 账号,未在真实系统上跑过导入。学校若改动 CAS 目标或老正方页面,解析器需重查。
- HFUT(issue #25):类型化门户 URL 跳转已验证;维护者没有 HFUT 账号。学校若改动 EAMS5 门户布局,路由需重查。
- `jw.ahu.edu.cn`(v1.0.51 收录):校外可达,但没有真实账号跑过导入(维护者没有安大账号)。自 v1.0.51 未变。
- `jwxt.nit.net.cn`(v1.0.45 收录):仅校内网或 VPN 可达,校外直连在 TCP 层超时。自 v1.0.45 未变。

## 验证

- 测试: 1639 cases, 0 failures, 0 errors。
- APK SHA-256:
  - arm64-v8a: `1431babe8df7bd6b64109dd89c76b2ad620aa0f23dbdfb219605c6973631d353`(2,916,991 bytes)
  - armeabi-v7a: `5b4f75f721aa895d099b9375082097b13193b6408ae275e21987c226f24c2ad3`(2,914,299 bytes)
  - x86_64: `ffcdb2b46185cc1fd5cb6e6c75e8b6673917f7a0e35af2a2f19906d43af69a12`(2,916,097 bytes)
- 构建: versionName `1.0.53`, versionCode `57`