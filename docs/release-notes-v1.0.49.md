# v1.0.49

36 commits. School count remains 179: three dead or duplicate entries were removed, and Guangdong Medical University, Guangzhou Medical University, and Jilin Business and Technology College were added.

Two new schools were added to direct import: Guangdong Medical University, verified with a captured login flow, and Guangzhou Medical University, verified separately from its official entry point. They use different protocols: `zf_new` and Qiangzhi. ChaoXing general academic affairs protocol, brand new parser. A parallel `sleepy-v1` import-export format running next to the old text one. Acknowledgements grew from 14 to 26. The collector (Go binary) got rewritten so a transient failure can be retried. Four smaller fixes ride along.

Tests: 1073, zero failures in the release verification run. This note describes the planned `versionName 1.0.49` / `versionCode 50` release; the current checkout still declares `versionName 1.0.48` / `versionCode 49` until the release-number commit is made.

---

## Schools

### Guangdong Medical University

The `zf_new` protocol was verified against the school's login flow and added through the existing Zhengfang parser.

### Guangzhou Medical University

This is a separate school from Guangdong Medical University. Its official website links to a Qiangzhi `jsxsd` entry point, which was used to classify and verify the school.

### ChaoXing (Superstar) general academic affairs — new protocol

New parser for `queryKbForGrdb` JSON. First school on it: Jilin Business and Technology College (`jlbtc`).

What it does:

- Strips the `<a>` HTML wrappers from `kcmc`, `tmc`, `croommc`. The server returns them wrapped, so naive parsing would have `<a>Linux 基础</a>` end up as the course name.
- Reads both `1,2,3` and `1-16` week formats.
- Splits continuous-week segments via `weekRuns`. A `1,2,3,8,9` row becomes two courses instead of one with weird semantics. Same logic the Wisedu parser already uses.
- Merges consecutive `xjc` rows into a single course with `startNode..endNode`.
- Falls back to `rqxl` (row index) when `xjc` is missing.

Tested against a real JLBTC capture: 22 input rows merge to 11 output courses. 12 parser tests pin the behaviour.

HTML marker: `powered by chaoxing` or `超星综合教务`. URL marker: anything matching `/xsd` or containing `querykbforgrdb` or `sdpkkblist`.

`queryKbForGrdb` is the personal table. `sdpkkblist` is the class table (the one a teacher uses to look up the whole class). The latter is not wired in this version.

### Protocol and domain corrections

These changes do not affect the total count, but update the import URL or protocol for the listed schools:

- **Beijing Institute of Technology** and **Beijing Information Science and Technology University** — moved to `wisedu`.
- **Anhui Jianzhu University** — forced HTTPS. The HTTP variant was unreachable.
- **Shandong Second Medical University** — `sdmpu → sdsmu`. School renamed in 2026, new domain live, old one dead. Verified against three sources: 教务处公告, 学生手册, DNS.
- **Bohai University** — `jw.bhu` is dead. Migrated to `bdjw.bhu.edu.cn/jsxsd/`, protocol `qz`.
- **Chongqing Three Gorges University**, **Chongqing University of Posts and Telecommunications Yitong College**, **Dalian Polytechnic University Art and Information Engineering College** — domain and protocol fixes.

The 179-school cross-validation in this batch changed 60 entries and removed 3 dead or duplicate entries. The final catalog remains 179 because the three new entries replace those three removals.

### WHUT and HFUT got the deep fix

WHUT's iWut portal was stuck behind a `Welcome come to EMAP` shell until the entry was rerouted through `forceCas`. HFUT's root path was returning 404, which broke the import script the moment you pointed it at the homepage. Both are verified end-to-end now. A `changeAppRole` step was added before the WHUT timetable fetch, since the personal page is hidden behind a role switch.

---

## sleepy-v1: a new import-export format

This is the bigger half of the release. v1.0.49 ships a parallel format alongside the existing text import, called `sleepy-v1`. It keeps the hand-editable plain-text approach, with a tighter specification.

What you get:

- `Nd` folding. A class that meets every week becomes a single token instead of being spread across `r` blocks. Cleaner output, easier to scan.
- A `chk` checksum for integrity. If you hand-edit the file and break it, the importer tells you where.
- Deterministic group handling. Same input always produces the same output.
- A share-shape variant. Round-trips through messaging apps without line-ending damage.

Where it shows up in the app:

- **Import**: there's a new fourth entry on the export/share screen that writes to this format and shares via the system share sheet.
- **Export**: any saved course table can be exported as a `sleepy-v1` block.
- **AI prompt**: an in-app prompt (six locales) explains the format directly so a user can paste it into any model and get a valid `sleepy-v1` block back.
- **Detail screen**: the `ImportDetailScreen` text was rewritten top to bottom to match the v1 spec.

This is opt-in. The old format is untouched and still the default.

---

## Collector: stop giving up on the first try

The collector (Go binary, ships in `app/src/main/assets/collector/`) used to stop too quickly after a failed fetch, even when a second attempt could succeed.

What changed:

- `evalAsyncRetry` for the WebView `fetch` and replay stages (three attempts).
- `GetResponseBody` retries once on "No data".
- Browser-nav retries twice on initial load.
- Top-level package retry loop: three rounds with a user prompt between them.

Compile artifacts refreshed for all four platforms (macOS, Linux, Windows, Android).

### Collector also gained a log channel

Three places write the same log: an in-page log panel, the terminal, and a `collect-log.txt` packed alongside the captured course data. When a capture fails, open the file on your phone and read what happened. No terminal required.

---

## Acknowledgements

The "About" screen now lists 26 upstream projects. Three rounds:

1. Baseline of 14.
2. +9 student-maintained repos we had been reading from in passing — BIT-Login, iBistu, JdaAssist, CQYTZFCheckScores, ScheduleXParser_SCAU, JW-spider, BohaiServiceDome, courseTable, shangkeschedule.
3. +3 from the evidence archive — WeNEPU, HeraldStudentCurriculum, the dhu_dlsf_app reference for Donghua.

Each entry is listed with its project name, license, and relationship to Sleepy in About → 开源声明.

---

## Smaller fixes

- **Format-detail dialog example was glued onto one line.** The previous round wrote raw newlines into the string resource file, and aapt folded them to spaces during packaging. Fix: write literal `\n` in source and let aapt2 escape them at build time. Verified at the arsc byte level — the stored character is `0x0A`. The example block now reads as four lines in every locale.
- **Type drift in single-`INTERVAL=2` ICS exports is now documented.** A course exported as `INTERVAL=2;BYDAY=...` does not round-trip cleanly through sleepy-v1 — the alternating-week detection collapses it. Behaviour pinned in the test suite and footnoted in the format detail.
- **README in English** alongside the Chinese one, with a working language switcher at the top.
- **Adaptation tutorial rewritten** end to end. The collector is now linked from the repo, not the release page, so the operation doc never goes stale again.

---

## Known limitations

- ChaoXing capture covers `queryKbForGrdb`, the personal timetable endpoint. `sdpkkblist`, the class timetable endpoint, is detected but not wired in this version.
- The collector fails on macOS 26+ notarisation warnings. Run `xattr -d com.apple.quarantine` once after download. Documented in the operation page.
- `sleepy-v1` share output uses the share sheet, not the system clipboard. Some older Android skins show only the share UI, not the system share action — use "Copy" if you need the literal block.
- The `sleepy-v1` tests cover the dispatcher and the schedule samples currently in the repository, but not every possible hand-written grammar corner case. Include the original input and error message when reporting an import failure.

---

## Credits

The complete list of upstream projects and their licenses is available in About → 开源声明.

---

# v1.0.49

36 个 commit。学校总数仍为 179 所:删去 3 条死链或重复条目,新增广东医科大学、广州医科大学和吉林工商学院。

这一版值得说在前面:新接入两所不同的学校。广东医科大学按 `zf_new` 协议接入;广州医科大学根据官网入口单独核验为强智 `qz`。超星综合教务协议加了一个新 parser,吉林工商学院(`jlbtc`)是第一个;出了一套 `sleepy-v1` 导入导出格式,跟老的并列共存;致谢从 14 条扩到 26 条;采集工具整个重写,不再第一次失败就结束。还有五个小修。

1073 个测试,零失败。`versionCode 50`。

---

## 学校

### 广东医科大学

协议 `zf_new` 已按学校登录流程核验,由现有正方解析器处理。

### 广州医科大学

这是另一所学校,不是广东医科大学。根据学校官网快速链接和 `jsxsd` 入口核验为强智 `qz`。

### 超星综合教务 — 新协议

`queryKbForGrdb` JSON 解析器。第一个学校:吉林工商学院(`jlbtc`)。

它能干这些事:

- `kcmc/tmc/croommc` 三个字段都剥 `<a>` HTML。服务端包了一层,直接解析会把 `<a>Linux 基础</a>` 当成课名。
- 周次两种写法都认:`1,2,3` 和 `1-16`。
- 连续周段切分。一条 `1,2,3,8,9` 拆成两条,而不是塞一条怪课。跟 Wisedu parser 同一套逻辑。
- 连续 `xjc` 行合成一条 `JwCourse`,`startNode..endNode`。
- 缺 `xjc` 的时候回退到 `rqxl`(行号)。

吉林工商学院的真实抓包测过:22 行输入合并成 11 条。12 个 parser 测试钉死行为。

HTML 标志:`powered by chaoxing` 或 `超星综合教务`。URL 标志:含 `/xsd`、`querykbforgrdb` 或 `sdpkkblist`。

`queryKbForGrdb` 是个人课表。`sdpkkblist` 是教师查全班课表用的。这一版没接后者。

### 中途改判的几所

学校总数没变,但以下条目的导入 URL 或协议类型已经更新:

- **北京理工**、**北京信息科技大学** — 改判 `wisedu`。
- **安徽建筑大学** — 强转 HTTPS。HTTP 版校外打不通。
- **山东第二医科大学** — `sdmpu → sdsmu`。学校 2026 年改名,新域名生效,老域名死了。三方实锤:教务处公告、学生手册、DNS。
- **渤海大学** — `jw.bhu` 死了。迁到 `bdjw.bhu.edu.cn/jsxsd/`,协议 `qz`。
- **重庆三峡学院**、**重庆邮电大学移通学院**、**大连工业大学艺术与信息工程学院** — 域名、协议修对。

179 校全量交叉验证落在这个批里:修正 60 条,删去 3 条死链或重复条目。新增的 3 所学校补回了总数,最终仍为 179 所。

### WHUT 和 HFUT 顺手做了深度体检

WHUT 的 iWut 入口卡在 `Welcome come to EMAP` 那个页面,改成走 `forceCas` 才打通。HFUT 根路径返回 404,指向首页立刻断。两边都端到端验过了。WHUT 取课表前加了 `changeAppRole` 切本科生角色 — 个人页藏在角色后面,不切看不到。

---

## sleepy-v1:新的导入导出格式

这一版的大头。`sleepy-v1` 跟现有文本导入并行。思路一样,用户能直接手改的纯文本,只是规范更紧。

具体多了四件事:

- `Nd` 折叠。每周都上的课合成一个 token,不散在 `r` 块里。导出干净,扫读快。
- `chk` 校验和。手动改错了,导入器直接告诉你在哪一行。
- 组处理确定性强。同样输入永远同样输出。
- 分享形态变体。能从微信/QQ round-trip 回来,不被换行符咬。

App 里出现的地方:

- **导入**:导出/分享屏加了第四个入口,写这个格式走系统分享面板。
- **导出**:任意已存课表都能导成 `sleepy-v1` 块。
- **AI Prompt**:应用内 Prompt(六 locale)直接讲这个格式,用户贴给任何模型都能吐回 `sleepy-v1`。
- **详情屏**:`ImportDetailScreen` 文本整页重写。

可选。旧格式没动,默认还是旧的。

---

## 采集工具:别再第一次失败就装死

此前采集失败后缺少明确提示,而再次操作有时又能成功。采集工具(Go 二进制,在 `app/src/main/assets/collector/`)之前过早结束。

改了:

- WebView `fetch` 和回放阶段,`evalAsyncRetry` 重试三次。
- `GetResponseBody` 遇 "No data" 重试一次。
- 浏览器初次导航失败重试两次。
- 最外层打包重试循环,跑三轮,中间弹用户提示。

macOS / Linux / Windows / Android 四平台产物都刷了。

### 日志三路消费

同一份日志写到三个地方:页面日志面板、终端、采集包里的 `collect-log.txt`。抓失败了你打开手机里的文件就知道哪儿挂了,不用守着终端。

---

## 致谢

"关于"页列了 26 个上游项目。三轮补的:

1. 基线 14 条。
2. +9 条学生项目,路过一直在参考但没挂名的:BIT-Login、iBistu、JdaAssist、CQYTZFCheckScores、ScheduleXParser_SCAU、JW-spider、BohaiServiceDome、courseTable、shangkeschedule。
3. +3 条证据档案里翻出来的:WeNEPU、HeraldStudentCurriculum、东华那个 dhu_dlsf_app。

致谢条目对应 About → 开源声明中的完整清单;项目名称、许可证和参考范围均列在应用内。

---

## 几个小修

- 格式详情弹窗示例之前挤一行。上一轮把裸换行写进 string 资源,aapt 打包时折成空格。改回源码里写字面 `\n`,让 aapt2 构建期转义。arsc 字节级验过,落地就是 `0x0A`。每个 locale 都还原成四行。
- `INTERVAL=2;BYDAY=...` 导出的 ICS 课再走 sleepy-v1 会折 — 双周识别把它压成普通单周课。行为钉进测试套件,格式详情加了脚注。
- README 出了英文版,跟中文并列,顶部加了语言切换,导航生效。
- 适配采集手册整本重写,采集器入口改仓库直链。文档不会再因为新版本发版而过期。

---

## 已知限制

- 超星只接了 `queryKbForGrdb`(个人课表)。`sdpkkblist`(教师查全班)目前尚未接入。
- macOS 26+ 上采集工具首次启动会卡签名警告,下完一次 `xattr -d com.apple.quarantine` 就行。文档里写过。
- `sleepy-v1` 分享走系统面板,不写系统剪贴板。部分老 Android 皮肤只显示分享 UI 不显示系统分享动作 — 需要原样复制就点 "复制"。
- `sleepy-v1` 测试覆盖当前仓库中的分派器边界和真实样例,但不涵盖所有可能的手写语法组合。遇到无法导入的内容,请提交原始输入和错误信息。

---

## Credits

完整致谢与许可证信息见应用内 About → 开源声明。
