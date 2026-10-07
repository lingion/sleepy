# NWPUPostParser 当前代码状态

日期: 2026-10-07

## 路由与解析

- `app/src/main/java/com/lingion/sleepy/data/jw/JwNwpuPostParser.kt`
  - 解析 `<table id="sample-table-1">` 一维清单 HTML.
  - 列头按 `院系/课程编号/课程名称/班级名称/主讲教师/学分/班级说明/上课时间` fuzzy-match.
  - 上课时间 cell: `<br>` → `TextNode("\n")`, 再 `wholeText()` 取, 保留多时段换行.
  - 节次正则 `([上中下晚])?(\d+)(?:-(\d+))?节?` + 范围展开 (baseline: 上→0, 中→4, 下→6, 晚→10).
  - 周次 `第(\d+)-(\d+)周` / `第(\d+)周` + 单双周判定 (`单周`→type=1, `双周`→type=2).
  - 星期分段 `星期.+?(?=<|星期|$)`, 聚类连续节次, 每段产出一条 `JwCourse`.
  - 置信度: sample-table-1 锚点 +70, 上课时间/课程编号 +20, 主讲教师/班级名称 +10.

## 注册与接线

- `JwProtocol.TYPE_NWPU_POST = "nwpu_post"`.
- `JwProtocol.ALL_TYPES`、`displayName` (`西北工业大学研究生教务`)、category (`other`) 已登记.
- `JwParserRegistry` priority 为 16, factory 指向 `JwNwpuPostParser`.
- `JwImportViewModel.parseHtml(..., "nwpu_post")` 通过 registry 选 parser.

## 测试与 fixture

- `app/src/test/java/com/lingion/sleepy/data/jw/JwNwpuPostParserTest.kt`: 2 个 JVM 测试.
  - `parse nwpu graduate schedule sample-table-1 table` — 3 行 × 5 课程段全字段断言 (课程名、星期、节次起止、周次、单双周、教室、老师).
  - `confidence and matched features` — 置信度 ≥90, 必含 `id:sample-table-1` + `col:上课时间`.
- `app/src/test/resources/jw/nwpu/sample-table-1.html`: 合成 fixture (3 行覆盖 上/中/下/晚 节次 + 单周/双周/无类型 + 5 课程段).

## 验证结果与剩余边界

- `:app:testDebugUnitTest`: 全量通过, Gradle BUILD SUCCESSFUL (32 actionable tasks).
- `:app:lintDebug`: 未跑, 历史既有错误 (HighRefreshRate.kt:20 API 30 调用) 与本 PR 无关.
- 跨仓求证: 51 候选, 0 POSITIVE / 20 INDIRECT / 31 NEGATIVE. 详 `findings.json`.
- 协议层 source of truth = WakeUp 反编译 smali (无 GitHub 二级校验).
- 致谢: 20 INDIRECT 仓库全量入 LicenseScreen.kt + 6 语 strings.xml + AttributionTest.