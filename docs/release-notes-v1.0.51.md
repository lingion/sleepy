# Sleepy v1.0.51

> Anhui University (jw.ahu.edu.cn) self-built new academic system now imports through print-data and full week-range parsing; README and about-page credits corrected per PR #16 review.
>
> 安徽大学 (jw.ahu.edu.cn) 自建新教务支持 print-data 课表导入与完整周次位图解析;README 与关于页致谢按 PR #16 反馈校正。

## What's New

### Anhui University: new academic system import

`https://jw.ahu.edu.cn/` now imports through the school's self-built new system (金智 EAMS 新版部署形态), replacing the previous behaviour of POSTing to `/student/ws/schedule-table/datum` (which the school does not expose and returns HTTP 500).

The import now:

- Reads the semester selector from the real Thymeleaf-rendered `<select id="allSemesters">` (5 cross-checked repositories agree this is the upstream's actual rendering).
- Falls back to `/student/for-std/course-table/semester/<id>/print-data` and parses `studentTableVms[0].activities[]`, which carries `weekday`, `startUnit`,`/endUnit`, `weekIndexes`, `campus`/`building`/`room`, and a list of teacher names per activity.
- Detects an expired session by `name="lt"` + `name="execution"` appearing together in the response body, or a `Location` header pointing at `one.ahu.edu.cn/cas` / `/cas/login` — the same shape Zeraora-807/Anhui-Univ-DSH-Tool uses internally.

Activities whose `weekIndexes` bitmap encodes a range with a `单` (odd) or `双` (even) suffix now emit a single `JwCourse` with the matching `type` (Sleepy's `JwParity.adjustedRange` adjusts the start week to the correct parity). A bitmap of `1-16单` becomes `type=1` starting at week 1; `1-16双` becomes `type=2` starting at week 2.

The about page now credits the five repositories whose code informed this change:

- MoeclubM/AHU-AIO
- qiqqqqq517/shangkeschschedule (Apache-2.0)
- abydym/Ahu_Plus (GPL-3.0)
- Landon-3314/AHU-TimeTable
- Zeraora-807/Anhui-Univ-DSH-Tool (GPL-3.0)

### README and about-page credits corrected

The README's `platforms;android-37.0` badge and the project-structure section are corrected to reflect what the app actually builds against. The about-page credit list now reaches 32 schools, grouped by project per PR #16 review. A duplicate boilerplate "report omissions via GitHub Issue" line is removed.

## Fixes

- Anhui University's previous import attempt failed with `POST /student/ws/schedule-table/datum` returning HTTP 500. The import now uses the school's actual public endpoint.
- Week-range parsing for Anhui University previously emitted only the first week of a multi-week course; full bitmap parsing now emits each week in the range, with `type=0` for full weeks, `type=1` for odd weeks, and `type=2` for even weeks.
- A type-encoding mismatch (this release previously emitted `type=2`/`type=3` for odd/even weeks) was fixed to align with the existing `JwCourse.type` convention of `0=full week, 1=odd week, 2=even week` used elsewhere in Sleepy.

## Known Limitations

`jw.ahu.edu.cn` is reachable from outside the campus network. No real-device import test was performed before this release (the maintainer has no AHU account and cannot log in to the academic system). The protocol shape was verified by reading five public cross-repositories; if the school changes the path or the response shape, the next release will need to be re-validated.

For `https://jwxt.nit.net.cn/` (Zhejiang University Ningbo Institute of Technology, added in v1.0.45): the host is reachable only from the campus network or VPN; direct off-campus access times out at the TCP layer. This was already noted in v1.0.45's release and is unchanged.

## Verification

- Tests: 1125 cases, 0 failures, 0 errors.
- APK SHA-256:
  - arm64-v8a: `861c59c96c56459679a07a504386bf0aa85d3829e79988d0e46920d1482c5fa3` (2,797,111 bytes)
  - armeabi-v7a: `0d6748d50048c97d8cf1e06c46493b5e11d663bf087d312e6b97e1347d87f15f` (2,794,419 bytes)
  - x86_64: `e3009d1e53927d942543a0e9e8b55f643ebf7693666b32dbe4b579913dbed8a6` (2,796,217 bytes)
- Build: versionName `1.0.51`, versionCode `52`

---

# Sleepy v1.0.51

> 安徽大学 (jw.ahu.edu.cn) 自建新教务支持 print-data 课表导入与完整周次位图解析;README 与关于页致谢按 PR #16 反馈校正。

## 新增功能

### 安徽大学:自建新教务导入

`https://jw.ahu.edu.cn/` 现在通过学校自建的新教务系统(金智 EAMS 新版部署形态)导入,替换之前 POST `/student/ws/schedule-table/datum`(该端点学校并未对外开放,返回 HTTP 500)。

新的导入流程:

- 从真实的 Thymeleaf 渲染 `<select id="allSemesters">` 读学期选项(5 个跨仓仓库一致认定这是上游的实际渲染)。
- 退到 `/student/for-std/course-table/semester/<id>/print-data` 并解析 `studentTableVms[0].activities[]`,其中含 `weekday`、`startUnit`/`endUnit`、`weekIndexes`、`campus`/`building`/`room` 以及每位教师姓名列表。
- 会话失效检测:响应体内同时出现 `name="lt"` 与 `name="execution"`,或 `Location` 头指向 `one.ahu.edu.cn/cas` / `/cas/login` —— 与 Zeraora-807/Anhui-Univ-DSH-Tool 内部所用判定一致。

`weekIndexes` 位图编码的范围,若后缀是 `单`(奇)或 `双`(偶),现在 emit 一条带对应 `type` 的 `JwCourse`(`JwParity.adjustedRange` 把起始周调到正确的奇偶)。位图 `1-16单` → `type=1` 起始周 1;`1-16双` → `type=2` 起始周 2。

关于页新增 5 个为本变更提供代码证据的仓库致谢:

- MoeclubM/AHU-AIO
- qiqqqqq517/shangkeschschedule (Apache-2.0)
- abydym/Ahu_Plus (GPL-3.0)
- Landon-3314/AHU-TimeTable
- Zeraora-807/Anhui-Univ-DSH-Tool (GPL-3.0)

### README 与关于页致谢校正

README 的 `platforms;android-37.0` 标签和项目结构章节已校正以反映应用实际编译目标。关于页致谢现在覆盖 32 所学校,按 PR #16 反馈改为按项目聚合。删除了关于页里那句"如有遗漏请通过 GitHub Issue 反馈"的 boilerplate。

## 修复

- 安徽大学之前导入在 `POST /student/ws/schedule-table/datum` 返回 HTTP 500 时失败;现在改用学校实际公开端点。
- 安大周次位图解析之前只 emit 多周课程的首周;现在完整位图解析 emit 范围内每一周,全周 `type=0`、单周 `type=1`、双周 `type=2`。
- 安大周次类型编码(本发布前曾 emit `type=2`/`type=3` 表示单/双周)的不一致已修正,统一为 Sleepy `JwCourse.type` 既有约定 `0=每周, 1=单周, 2=双周`。

## 已知限制

`jw.ahu.edu.cn` 校外可达,但本次发布前**未做真机导入验证**(维护者没有安大账号,无法登录教务系统)。协议形态由 5 个公开跨仓仓库代码验证;若学校后续修改路径或响应结构,下一版本需重新验证。

`https://jwxt.nit.net.cn/`(浙大宁波理工学院,v1.0.45 收录):仅校内网或 VPN 可达,校外直连在 TCP 层超时。该限制在 v1.0.45 已说明,本版本未变。

## 验证

- 测试: 1125 cases, 0 failures, 0 errors。
- APK SHA-256:
  - arm64-v8a: `861c59c96c56459679a07a504386bf0aa85d3829e79988d0e46920d1482c5fa3`(2,797,111 bytes)
  - armeabi-v7a: `0d6748d50048c97d8cf1e06c46493b5e11d663bf087d312e6b97e1347d87f15f`(2,794,419 bytes)
  - x86_64: `e3009d1e53927d942543a0e9e8b55f643ebf7693666b32dbe4b579913dbed8a6`(2,796,217 bytes)
- 构建: versionName `1.0.51`, versionCode `52`