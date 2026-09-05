# Sleepy v1.0.49

### Direct import: three schools added

Direct import now includes Guangdong Medical University, Guangzhou Medical University, and Jilin Business and Technology College. Guangdong Medical University uses the `zf_new` protocol. Guangzhou Medical University uses Qiangzhi (`qz`) through its `jsxsd` entry point. These are separate schools with separate entries. Jilin Business and Technology College is the first school supported by the new ChaoXing parser.

### ChaoXing timetable import

ChaoXing's timetable response is JSON rather than a standard HTML table. The new parser handles `queryKbForGrdb` personal timetable responses, strips HTML anchors from course, teacher, and room fields, parses both comma-separated weeks and ranges, splits discontinuous week runs, merges consecutive period rows, and falls back to the row index when `xjc` is absent. A real Jilin Business and Technology College capture contains 22 input rows and produces 11 courses after merging. The class timetable endpoint, `sdpkkblist`, is detected but is not imported in this release.

### School entry and protocol corrections

The school catalog was checked against current entry points. 60 entries were corrected, including protocol changes, HTTPS migrations, renamed schools, and dead or outdated domains. Three dead or duplicate entries were removed and the three schools above were added, so the catalog remains at 179 schools.

Notable corrections include Beijing Institute of Technology and Beijing Information Science and Technology University moving to Wisedu, Anhui Jianzhu University moving to HTTPS, Shandong Second Medical University moving from `sdmpu` to `sdsmu`, and Bohai University moving from the dead `jw.bhu` entry to `bdjw.bhu.edu.cn/jsxsd/` with protocol `qz`.

### Wuhan University of Technology and Hefei University of Technology

Wuhan University of Technology could stop at `Welcome come to EMAP`; the entry now goes through `forceCas`, switches to the required student role with `changeAppRole`, and then fetches the personal timetable. Hefei University of Technology's old root entry returned 404; its EAMS5 path is now used instead. The Wuhan University of Technology fix was verified from the reproduction report in [issue #14](https://github.com/lingion/sleepy/issues/14) and the capture pack in [issue #15](https://github.com/lingion/sleepy/issues/15), submitted by Aster-poros.

### sleepy-v1 import and export format

The app previously had one plain-text format. It now also supports the `sleepy-v1` format while keeping the existing format available. `sleepy-v1` adds deterministic records, `Nd` folding for courses that meet every week, a `chk` integrity field, and a share form designed to preserve line endings when text passes through messaging apps.

The format is available from the export/share screen, for saved timetable tables, and from the import detail screen. The built-in conversion prompt is localized in all six release locales. The old format remains the default.

### Collector retries and logs

The collector could stop after a transient browser or response failure. WebView fetch and replay now retry up to three times, `GetResponseBody` retries once after `No data`, initial browser navigation retries twice, and package generation retries for up to three rounds with a status prompt between rounds. Refreshed collector binaries are included for macOS, Linux, Windows, and Android.

Collection logs are now written to the in-page log panel, the terminal, and `collect-log.txt` in the captured package. The third copy makes failures diagnosable without connecting the phone to a terminal.

### Acknowledgements and license information

The About screen now has a dedicated open-source acknowledgements page. It lists 26 upstream projects with their licenses and the part of Sleepy that each project informed. The list includes the projects referenced by the new school adapters, the full-school catalog review, the ChaoXing and native-format work, and the Wuhan University of Technology investigation. Aster-poros is credited for the Wuhan University of Technology reproduction report and capture pack in issues [#14](https://github.com/lingion/sleepy/issues/14) and [#15](https://github.com/lingion/sleepy/issues/15).

### Smaller fixes

- Format examples now preserve their intended line breaks in every locale. The source uses escaped `\\n`, and the packaged resources were checked to contain the line-feed byte.
- A known limitation of importing a single `INTERVAL=2;BYDAY=...` ICS event through `sleepy-v1` is documented in the format details and covered by tests.
- The English README now ships alongside the Chinese README, with a language switcher.
- The adaptation guide now links to the collector in the repository, so the operation instructions do not depend on a release-page asset.

### Tests

The release verification run completed with 1,073 tests and zero failures. It includes the ChaoXing parser cases, school catalog consistency checks, protocol detection, native-format dispatch, import/export behavior, collector retry logic, and the Wuhan University of Technology fixes.

### Build

- versionName: `1.0.49`
- versionCode: `50`
- APKs: `app-arm64-v8a-release.apk` (most phones), `app-armeabi-v7a-release.apk` (older 32-bit ARM phones), `app-x86_64-release.apk` (emulators)

— Lingion

---

# v1.0.49

### 教务直连新增三所学校

教务直连现在收录广东医科大学、广州医科大学和吉林工商学院。广东医科大学使用 `zf_new` 协议；广州医科大学使用强智 (`qz`) 的 `jsxsd` 入口。这是两所不同的学校，各有独立条目。吉林工商学院是第一所接入新超星解析器的学校。

### 超星综合教务导入

超星课表接口返回 JSON，不是普通 HTML 表格。新解析器处理 `queryKbForGrdb` 个人课表响应，剥掉课程名、教师和教室字段外层的 HTML 链接，识别逗号周次和连续周次，拆分不连续的周次段，合并连续节次，并在 `xjc` 缺失时回退到行号。吉林工商学院的真实采集包有 22 行输入，合并后得到 11 门课。教师端查看全班课表的 `sdpkkblist` 这一版只做入口识别，尚未接入导入。

### 学校条目和协议修正

本轮按当前入口逐校核对学校目录，修正了 60 条记录，涉及协议类型、HTTPS、学校更名以及失效或过期域名。删去 3 条死链或重复条目，再补入上面 3 所学校，目录总数仍为 179 所。

其中包括：北京理工大学、北京信息科技大学改用 Wisedu；安徽建筑大学改用 HTTPS；山东第二医科大学从 `sdmpu` 迁到 `sdsmu`；渤海大学从已经失效的 `jw.bhu` 迁到 `bdjw.bhu.edu.cn/jsxsd/`，协议改为 `qz`。

### 武汉理工大学和合肥工业大学

武汉理工大学入口之前会停在 `Welcome come to EMAP`，现在改走 `forceCas`，并在获取个人课表前通过 `changeAppRole` 切换到所需的学生角色。合肥工业大学原入口返回 404，现在改用 EAMS5 课表路径。武汉理工大学这条修复根据 Aster-poros 在 [issue #14](https://github.com/lingion/sleepy/issues/14) 提交的复现信息和 [issue #15](https://github.com/lingion/sleepy/issues/15) 提交的采集包完成核验。

### sleepy-v1 导入导出格式

原来只有一种纯文本格式。现在新增 `sleepy-v1`，旧格式仍然保留。`sleepy-v1` 使用确定性的记录处理；每周都上的课程可以用 `Nd` 折叠；文件带有 `chk` 完整性字段；分享形态尽量避免文本经过聊天软件后换行被破坏。

在导出/分享页面、已保存课表的导出操作和导入详情页中都可以看到这个格式。内置转换 Prompt 已覆盖六个发布语言。旧格式仍是默认格式。

### 采集器重试和日志

采集器之前遇到浏览器或响应的临时失败可能直接结束。现在 WebView fetch 和回放最多重试三次，`GetResponseBody` 遇到 `No data` 重试一次，首次浏览器导航重试两次，打包过程最多重试三轮，每轮之间显示状态提示。macOS、Linux、Windows 和 Android 四个平台的采集器产物均已更新。

采集日志现在同时写入页面日志面板、终端和采集包内的 `collect-log.txt`。多出的文件让用户不连接终端也能判断采集失败发生在哪一步。

### 开源致谢和许可证

关于页现在有独立的开源声明页面，列出 26 个上游项目、各自许可证以及 Sleepy 参考的部分。名单覆盖新学校适配器、全量学校目录复核、超星和原生格式工作，以及武汉理工大学适配调查中参考的项目。另感谢 Aster-poros 在 [issue #14](https://github.com/lingion/sleepy/issues/14) 提供武汉理工大学登录问题的复现信息，并在 [issue #15](https://github.com/lingion/sleepy/issues/15) 提供采集包。

### 其他修正

- 各语言的格式示例现在能保留原本的换行。源码使用转义后的 `\\n`，并已检查打包资源中确实包含换行字节。
- `INTERVAL=2;BYDAY=...` 形式的单条双周 ICS 事件经过 `sleepy-v1` 导入时存在已知限制，详情页已说明，测试也固定了当前行为。
- 英文 README 已补上，与中文 README 并列，顶部提供语言切换。
- 适配教程现在直接链接仓库内的采集器，操作说明不再依赖发布页附件。

### 测试

发布验证共通过 1,073 个测试，零失败。测试覆盖超星解析器、学校目录一致性、协议识别、原生格式分派、导入导出、采集器重试逻辑和武汉理工大学修复。

### 构建

- versionName：`1.0.49`
- versionCode：`50`
- APK：`app-arm64-v8a-release.apk`（多数手机）、`app-armeabi-v7a-release.apk`（较旧的 32 位 ARM 手机）、`app-x86_64-release.apk`（模拟器）

— Lingion
