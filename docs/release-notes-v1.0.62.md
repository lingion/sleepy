# Sleepy v1.0.62

> A nationwide school catalog (4,140 entries), theme-aware course colors, merged multi-table reminders, and a rebuilt widget page.

## What's New

### National school catalog: 349 to 4,140 entries

The school picker was a 349-school list. It is now a 4,140-entry national catalog with explicit import status per school: 1,478 entries carry a routing protocol (41 protocol families), 2,662 are catalog-only registrations pending parser work and are marked as such in-app, so you always know whether "not supported yet" means "wrong school" or "not wired yet". City attribution for 3,821 schools moved to audited per-campus evidence.

**Evidence:** PR #179 (2026-10-18).

### Unverified-school import success reports

Silent successes had no channel — failures could be reported, wins disappeared. Importing from a catalog entry that hasn't been verified online now pops a success report you can copy and send to the maintainer: app version, date, school name, catalog ID, protocol, course count. Nothing is uploaded automatically; the report text only contains school-side public facts.

**Evidence:** PR #186 (2026-10-18).

### Course colors follow your theme

Three modes in Appearance → Course colors: Classic (the old hash scatter, unchanged), Follow Theme (course hues live on a safe arc of your current theme color — the theme band ±30° and the complement band are carved out, Material 3 rules), Custom (drag a seed hue once, all courses re-harmonize around it). Hues persist per course group ID, so renaming a course or re-importing a timetable no longer reshuffles colors. Controls are rail-style segmented switches everywhere.

**Evidence:** PRs #189 (2026-10-18), #198 (2026-10-21). Database upgraded v11 → v12 (course_colors table).

### Widget management rebuilt

"Desktop widgets" moved from a shared settings page into its own page named "Add or Manage Widgets", with a gallery showing all 13 widget variants, an empty state that points at the add button, and the refresh button relocated next to the widgets it refreshes. The launcher previews were skeleton blocks — they're now re-rendered real widget faces at 6px/dp, matching each variant's true layout. Fixed a column-order bug in the three-day window face.

**Evidence:** PRs #188 (2026-10-18), #190 (2026-10-19), #193 (2026-10-19).

### Multi-table reminders become one notification

Daily and tomorrow reminders used to fire one notification per timetable. Now all tables merge into a single compact list sorted by class time, with a [timetable] prefix per entry when more than one table contributes. Tapping it opens the unified daily agenda, which also accepts a date and jumps straight there.

**Evidence:** PRs #176 (2026-10-14, contributor @Cold577), #178 (2026-10-14).

### Before-class reminder: banner / fluid cloud as one switch

The old pair of switches had a trap — with fluid cloud on, the banner never fired, silently. They're now one exclusive tab pair: banner (plain dismissable notification, naturally wearable-friendly) or fluid cloud (promoted live card, experimental label kept). Flipping back to banner truly stops the cast service instead of letting a 15s loop resurrect it. Old settings migrate without loss.

**Evidence:** PR #105 (2026-10-17).

### Per-table reminder switches, collected

The per-timetable reminder switches moved to their own page (Mine → Reminders → Table reminders). All tables and their switches in one list; you can preconfigure while the master switch is off.

**Evidence:** PR #175 (2026-10-13).

### Week view shows dates

When "show weekday" is on, the week view now prints the actual date (M月d日) next to each course and in the day detail panel — no more counting back from class week for a Tuesday at 8am.

**Evidence:** PRs #181 (2026-10-16), #191 (2026-10-19).

### Long breaks render as floating bands

Long break gaps between classes render as thin floating bands instead of stretched dead rows: row heights stay rigid, cards keep full height next to a band, meal-break geometry applies proportionally, importing always creates a time-schedule, and the header can hide the time row.

**Evidence:** PRs #107 (2026-10-13), #183 (2026-10-17), #184 (2026-10-18).

### Share text is now sleepy-v1

Both share entry points (export page, top-bar share sheet) emit the native sleepy-v1 format instead of the legacy URL-encoded blob — round-trip through import is preserved, and shared tables are readable as plain text.

**Evidence:** PR #180 (2026-10-16).

### Export to Xiaoai class table

A new export target prepares your timetable locally and hands it to the Xiaoai (Xiaomi assistant) class-table importer via deep link, with validation for week range, dates, and time-schedule fields before launch.

**Evidence:** PR #160 (2026-10-13).

### System calendar import works with no account

Devices without a Google account (or any calendar account) had an empty writable-calendar list, so calendar export was a dead end. Sleepy now creates its own hidden local calendar on demand, idempotently; incremental fingerprint upsert is kept, so re-exporting never wipes your manual edits in the calendar app.

**Evidence:** PR #106 (2026-10-13).

### Privacy policy entry on the About page

The About page links the hosted privacy policy (required for app-store review).

**Evidence:** PR #187 (2026-10-18).

### New capsule launcher icon

The brand capsule replaced the old launcher icon at every density, plus store artwork and the repository social preview.

**Evidence:** PRs #223 (2026-10-22), #224 (2026-10-23).

### Week grid widget jumps to the nearest busy day

The week grid desktop widget now skips empty days the same way the phone-column widget does — no more Sunday staring at a blank Monday-to-Friday wall during winter break.

**Evidence:** PR #158 (2026-10-11), contributor @YYiChen.

### School parser wave: nine platforms, one new school

Fixes and adaptations landed for: Tsinghua (PKU) import, East China University of Political Science & Law (coordinate-accurate), South China Agricultural (week coordinates), Southeast University v2 auto-fetch, Northwestern Polytechnical University graduate (old interface), new Qingguo three timetable layouts (old grid still compatible), new URP array variant (old fallback kept), new & old Superzhi structures, Kingo ZX report + week-grid parsing, and South China "zf" output-format drift under the updated parser library. Nanning Normal University's new Superzhi timetable is newly supported (user capture: 15 courses, 33 schedule records pinned in tests).

**Evidence:** PRs #119, #120, #121, #122, #123, #128, #129, #177 (2026-10-13/14), #124, #126, #131 (2026-10-13, contributor @Kerry1020), #218 (2026-10-21), all cross-verified per the cross-repo SOP.

## Fixes

- **Switching to week view could crash the app.** Equal-height day capsules used a feedback loop that grew the strip until the screen overflowed. Two-pass measurement replaced it; both the ratchet and the broken first attempt are pinned by contract tests. **Evidence:** PR #192 (2026-10-19).
- **Course cards shrank next to long-break bands.** The break was deducted twice (once in coordinates, once in height), clipping capsules touching a band. Card height now ignores the band entirely. **Evidence:** PR #184 (2026-10-18).
- **Grid lines and course cards were misaligned.** Horizontal rules sat on row tops while cards filled rows, so card tops hugged a line and bottoms floated. Rules moved to the gap midpoint; cards breathe symmetrically (widget and Compose views both). **Evidence:** PR #159 (2026-10-11, contributor @YYiChen).
- **Makeup-day class copying was wrong.** Swapped-class scheduling now filters by the source day's full date (week range, odd/even weeks included) and places by the target date; details resolve back to the original course. **Evidence:** PR #162 (2026-10-13).
- **Three-day window widget face had scrambled column order across Sunday boundaries.** **Evidence:** PR #188 (2026-10-18).

## Known Limitations

- **Catalog vs. support.** The 2,662 pending entries are registrations only — import shows "not yet adapted", with no parser behind them yet. Waves land per protocol family in future releases.
- **Package size.** APKs grew about 2.7 MB per ABI — the bundled school catalog data (schools + city evidence JSONs).
- **Xiaoai export acceptance.** Validation and hand-off are tested; the final import on a physical Xiaoai client was not device-verified.

## Verification

- Audit range: `v1.0.61..845af574` (= origin/main).
- Unit tests: `:app:testDebugUnitTest` — 3,026 cases, 0 failures, 0 errors (343 suites) on main HEAD; CI re-verifies on the release tag.
- Lint: 0 errors, warnings within `app/lint-baseline.xml`; CI android-lint green.
- Local release build succeeded for 4 variants; `versionName` 1.0.62 / `versionCode` 10062 derive from the tag at build time.
- Local APK SHA-256 (JDK 17; CI rebuilds and publishes `sha256sums.txt` on tag push):
  - `app-arm64-v8a-release.apk`: `4ac41013176ca0ce7394f7abeab387abb75b3cdaf7aafad27cdc292a16aba8c5` (7,322,110 bytes)
  - `app-armeabi-v7a-release.apk`: `c5179dfafd6370aec01c9b82c6980b2dc35e4ff9925e8a92d9b55cf8a9ffb501` (7,319,418 bytes)
  - `app-x86_64-release.apk`: `115168a1a978a43a5014fc5f0385871278320c45bdf457609f634d7fe93bc047` (7,321,216 bytes)
  - `app-universal-release.apk`: `4371f1bb239197122f6a9468943f9bda7ff49c2f8ce62e9f2fc4c5474587e0cd` (7,420,046 bytes)
  - Signing certificate SHA-256 `5D:97:52:2B:A8:FA:B0:C1:4A:BC:D8:14:FE:92:BB:0D:EF:19:91:49:AB:D0:DF:72:40:1A:52:80:87:B7:0F:09` — same identity as every prior release; release CI asserts it against the secret keystore.

- Contributors this release: @Kerry1020 (school parser wave — PRs #124, #126, #131), @YYiChen (widget day-skip #158, grid alignment #159), @Cold577 (merged multi-table reminders #176), @jim139129 (Gradle/AGP build compatibility #182, CI Temurin check #222).

---

# Sleepy v1.0.62

> 全国学校目录 4140 条、课程配色跟随主题、多表提醒合并、小组件管理重做。

## 新增功能

### 全国学校目录:349 条扩到 4,140 条

学校选择页从 349 条清单换成 4,140 条全国目录,逐校标注导入状态:1,478 条带协议可路由(41 个协议族),2,662 条为目录登记、尚未接解析器,App 内明示——"没适配"和"没这所学校"从此两回事。3,821 所学校的城市归属改为逐校审核证据。

**依据:** PR #179(2026-10-18)。

### 未验证学校导入成功报告

导入失败有 issue 通道,成功却静默——维护者收不到。从未在线验证过的学校导入成功后弹出报告,一键复制:版本、日期、校名、目录 ID、协议、课程数。不发任何网络请求,报告只含学校侧公开信息。

**依据:** PR #186(2026-10-18)。

### 课程配色跟随主题

外观 → 课程配色三档:经典撒色(老行为,零变化)/ 跟随主题(课程色相落在当前主题色的安全弧上,主题带 ±30° 与补色带挖除,Material 3 同源规则)/ 自定义(拖一次种子色,全表重新编排)。色相按课程组 ID 落库,改名、重导入不再换色。开关控件统一为轨道式分段器。

**依据:** PR #189(2026-10-18)、#198(2026-10-21)。数据库 v11 → v12(course_colors 表)。

### 小组件管理重做

「桌面小组件」从通用页拎出独立二级页,改名「添加或管理桌面小组件」:13 个组件全部进添加画廊、空态直接指向加号、刷新按钮迁到组件旁边。启动器略缩图以前是骨架示意块,现在按真实渲染器等比重绘(6px/dp),每个变体的脸就是它本来的样子。三天窗口脸跨周边界的列序错误已修。

**依据:** PR #188(2026-10-18)、#190(2026-10-19)、#193(2026-10-19)。

### 多课表提醒合并为一条通知

每日/明日提醒不再一课表发一条。所有课表合并成一条紧凑列表,按上课时间排序,多表时每条带 [课表名] 前缀。点按进入统一每日议程,支持给定日期直达。

**依据:** PR #176(2026-10-14,贡献者 @Cold577)、#178(2026-10-14)。

### 课前提醒:横幅 / 流体云二选一

旧双开关有个坑:流体云开着,横幅永远不出。现在合并为一对互斥 tab:横幅(普通可划通知,手环天然兼容)/ 流体云(实况卡片,保留实验标签)。切回横幅会真正停掉投放服务,不再被 15 秒循环复活。旧配置无损迁移。

**依据:** PR #105(2026-10-17)。

### 逐表提醒开关集中成列表

每张课表的提醒开关挪进独立页(我的 → 提醒 → 课表提醒),全部课表一屏排开;总开关关着也能先配置。

**依据:** PR #175(2026-10-13)。

### 周视图显示日期

「显示星期」开启时,周视图课程旁与日详情栏打印真实日期(M月d日)——周二早上八点的课,不用再从教学周倒推。

**依据:** PR #181(2026-10-16)、#191(2026-10-19)。

### 长课间改为悬浮薄带

课间长空档不再拉高行:悬浮薄带、行高刚性、紧邻薄带的课程卡满高、用餐时段按时间比例标识、导入恒建作息表、表头可隐藏时间行。

**依据:** PR #107(2026-10-13)、#183(2026-10-17)、#184(2026-10-18)。

### 分享文本切换 sleepy-v1 格式

两个分享入口(导出页、顶栏分享)输出 sleepy-v1 原生格式,替代旧的 URL 编码块;导入回环不变,分享出来的课表当纯文本也能读。

**依据:** PR #180(2026-10-16)。

### 导出到小爱课程表

新增小爱导出目标:本地备好课表、校验周次/日期/作息字段后经深链交给小爱课程表导入。

**依据:** PR #160(2026-10-13)。

### 无账户也能导系统日历

没登 Google 账号(或任何日历账户)的设备此前可写日历列表恒空。App 现在按需自建隐藏的本地日历,幂等;保留增量指纹导入,重导不覆盖你在日历 App 里的手动编辑。

**依据:** PR #106(2026-10-13)。

### 关于页隐私政策入口

关于页新增隐私政策卡片,直达在线政策页(商店审核要求)。

**依据:** PR #187(2026-10-18)。

### 新胶囊启动图标

胶囊 logo 全密度替换启动图标,商店推广图与仓库分享图同步。

**依据:** PR #223(2026-10-22)、#224(2026-10-23)。

### 周网格小组件自动跳最近有课日

周网格桌面组件与手机列组件口径统一:无课日自动跳到最近有课的一天,假期不再对着一整面空墙。

**依据:** PR #158(2026-10-11),贡献者 @YYiChen。

### 教务解析器波次:九个平台、一所新学校

本轮落地:清华导入、华政按真实坐标、华南农周次坐标、东南大学 v2 自动抓取、西工大研究生旧版界面、新青果三种课表布局(旧网格兼容保留)、新 URP 数组变体(旧版回退保留)、新旧强智结构、Kingo ZX 报表与星期网格解析、以及解析库升级后旧正方输出格式漂移适配。南宁师范大学新版强智课表新支持(用户采集样本:15 门课程、33 条排课记录入测试)。

**依据:** PR #119、#120、#121、#122、#123、#128、#129、#177(2026-10-13/14)、#124、#126、#131(2026-10-13,贡献者 @Kerry1020)、#218(2026-10-21),全部按跨仓验证 SOP 核过。

## 修复

- **切周视图闪退。** 顶部胶囊等高此前走正反馈棘轮,切几次周撑爆屏幕。改为两遍测量;棘轮本身和失败过的第一版都由契约测试锁死。**依据:** PR #192(2026-10-19)。
- **薄带旁的课程卡回缩。** 长课间带厚被双扣(坐标让位一次、卡高再减一次),贴带胶囊不满行。卡高彻底不含带厚。**依据:** PR #184(2026-10-18)。
- **网格线与课程卡不对齐。** 横线画在格顶、卡占满格,卡顶贴线卡底悬空。横线移到行间隙中点,上下对称(课表视图与小组件同步)。**依据:** PR #159(2026-10-11,贡献者 @YYiChen)。
- **调休补课取错课。** 调课现在按源日期完整筛周次与单双周,按目标日期排程,详情回原课程实体。**依据:** PR #162(2026-10-13)。
- **三天窗口组件跨周日列序错乱。** **依据:** PR #188(2026-10-18)。

## 已知限制

- **目录与支持是两回事。** 2,662 条 pending 仅登记、无解析器,导入明示"未适配";后续版本按协议族逐波接通。
- **包体增大。** 每 ABI 约增 2.7 MB——目录数据(schools + 城市证据 JSON)随包内置。
- **小爱导出验收。** 校验与交接已测;真机小爱客户端的最终导入未实机验收。

## 验证

- 审计范围:`v1.0.61..845af574`(= origin/main)。
- 单元测试:`:app:testDebugUnitTest` — 3,026 条,0 失败,0 错误(343 套件,main HEAD 实测);CI 在发布 tag 上复验。
- Lint:0 错误,警告在 `app/lint-baseline.xml` 基线内;CI android-lint 绿。
- 本地 release 构建四变体成功;`versionName` 1.0.62 / `versionCode` 10062 由 tag 构建期派生。
- 本地 APK SHA-256(JDK 17;CI 在 tag 推送时重建并发布 `sha256sums.txt`):
  - `app-arm64-v8a-release.apk`: `4ac41013176ca0ce7394f7abeab387abb75b3cdaf7aafad27cdc292a16aba8c5`(7,322,110 字节)
  - `app-armeabi-v7a-release.apk`: `c5179dfafd6370aec01c9b82c6980b2dc35e4ff9925e8a92d9b55cf8a9ffb501`(7,319,418 字节)
  - `app-x86_64-release.apk`: `115168a1a978a43a5014fc5f0385871278320c45bdf457609f634d7fe93bc047`(7,321,216 字节)
  - `app-universal-release.apk`: `4371f1bb239197122f6a9468943f9bda7ff49c2f8ce62e9f2fc4c5474587e0cd`(7,420,046 字节)
  - 签名证书 SHA-256 `5D:97:52:2B:A8:FA:B0:C1:4A:BC:D8:14:FE:92:BB:0D:EF:19:91:49:AB:D0:DF:72:40:1A:52:80:87:B7:0F:09` — 与此前所有版本同一身份;CI 发布前对 secret keystore 断言此指纹。
- 本版贡献者:@Kerry1020(教务解析器波次 — PR #124、#126、#131)、@YYiChen(组件跳无课日 #158、网格线对齐 #159)、@Cold577(多表提醒合并 #176)、@jim139129(Gradle/AGP 构建兼容 #182、CI Temurin 检查 #222)。
