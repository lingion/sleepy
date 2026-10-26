# NWPUPostParser 当前代码状态

## 路由、证据与边界

- FrameTraversalTree.ANCHORS 已加入 sample-table-1，测试覆盖合成 frame 捕获锚点到通用 registry 选中本解析器并产出五段课程。
- 未知星期、缺失或越界周次现在拒绝该时段，不再误填星期一/第 1 周；登录失效页须产出空课程。
- 本解析器只产出课程段，不产出校区作息或学期起始日期。下方原始协议矩阵描述了反编译功能，不能当作本实现已覆盖功能。
- 节次扫描允许无时段前缀的数字续项（上1节,2节），在星期段内剔除“第N周”周次后扫描；转换后限于每天 1..13 节；重复节次去重，空课程名不输出。
- 2025-08-29 西北工业大学教务部通知要求研究生从翱翔门户进入翱翔教务查看课表（https://jiaowu.nwpu.edu.cn/info/1164/23928.htm）；现有 sample-table-1 仅有反编译和合成样本，未核实当前生产页面仍采用该 HTML 协议。因此暂不以旧 yjsjy 入口新增学校目录记录；旧 HTML 的通用捕获仍可解析。

审核日期: 2026-10-13；原始取证日期: 2026-10-07

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

- `app/src/test/java/com/lingion/sleepy/data/jw/JwNwpuPostParserTest.kt`: 7 个 JVM 测试（待最终复验）.
  - `parse nwpu graduate schedule sample-table-1 table` — 3 行 × 5 课程段全字段断言 (课程名、星期、节次起止、周次、单双周、教室、老师).
  - `confidence and matched features` — 置信度 ≥90, 必含 `id:sample-table-1` + `col:上课时间`.
- `app/src/test/resources/jw/nwpu/sample-table-1.html`: 合成 fixture (3 行覆盖 上/中/下/晚 节次 + 单周/双周/无类型 + 5 课程段).

## 验证结果与剩余边界

- 2026-10-07 原 PR 曾全量通过；2026-10-13 重基修复后的全量测试待独占 Gradle 令牌复验。
- 重基修复后的 lint 与 assemble 均待复验；不沿用旧提交的测试结论。
- 跨仓求证: 51 候选, 0 POSITIVE / 20 INDIRECT / 31 NEGATIVE. 详 `findings.json`.
- 协议层 source of truth = WakeUp 反编译 smali (无 GitHub 二级校验).
- 致谢: 51 候选逐仓 verdict = 0 POSITIVE / 20 INDIRECT / 31 NEGATIVE；20 INDIRECT 与 15 个教务相关 NEGATIVE 入 LicenseScreen.kt、6 语 strings.xml 与 AttributionTest；16 个学习资料/生活服务/空仓等非教务候选仅留审计记录。入选清单以 attribution-candidates.json 为准。
