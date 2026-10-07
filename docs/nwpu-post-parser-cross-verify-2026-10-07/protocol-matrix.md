# 西北工业大学 (NWPU) `NWPUPostParser` 协议核验矩阵

日期: 2026-10-07
学校: 西北工业大学 (Northwestern Polytechnical University)
协议: NWPUPostParser — WakeUp 反编译识别的**研究生**教务课表 HTML 解析协议
跨仓求证: 51 候选仓库, 0 POSITIVE / 20 INDIRECT / 31 NEGATIVE

## 证据边界

WakeUp 反编译端实锤 NWPUPostParser 协议锚点; 51 个 GitHub 候选仓库**零开源实现**该协议层.
所有命中仓库均迁向新门户 `jwxt.nwpu.edu.cn` 的 print-data JSON 端点, 协议族与本解析器锚定的
HTML 一维清单(`id="sample-table-1"`)完全异构. 故协议层证据只能来自 WakeUp 反编译, 跨仓求证
仅作为 NWPU 生态校核, 不构成协议层 source of truth.

## 形态矩阵

| 形态 | 锚点 | 字段 | 证据状态 |
|---|---|---|---|
| 一维清单 (本解析器目标) | `id="sample-table-1"`, `table.table-striped` | `院系/课程编号/课程名称/班级名称/主讲教师/学分/班级说明/上课时间` | WakeUp 反编译 + 合成 fixture 实锤 |
| 上课时间 cell | `<br>` 隔开的多时段 | 教室 + 周次 + 单/双周 + 星期 + 节次 + 节? | WakeUp 反编译 + 自测 fixture 5 课程段 |
| 学期选择 | `select#xq` | `option[selected]` 取学期名 | WakeUp 反编译 |
| 新门户 print-data (跨仓旁证) | `/student/for-std/course-table/semester/{xq}/print-data/{sid}` | `studentTableVm.activities[]` + `timeTableLayout.courseUnitList[]` | 6+ 仓 (Lorcas, CR200J1-A, ShiGuang, qllokirin, top-tree, iiijiashu 等) 全 INDIRECT — 不属本协议 |
| 智慧校园 ecampus (跨仓旁证) | `ecampus.nwpu.edu.cn` | `tr.lessonInfo` HTML 行 | fantian-bilibili INDIRECT — 不属本协议 |

## Parser 契约

- 锚点: `<table id="sample-table-1">` 必须存在; 缺失即整页放弃.
- 列头匹配: `院系/课程编号/课程名称/班级名称/主讲教师/学分/班级说明/上课时间`. 用 `thHeaders[colIdx]` 文本 contains 匹配, 顺序敏感.
- 上课时间 cell: `<br>` 显式替换为 `TextNode("\n")` 后用 `wholeText()` 取, 避免 Jsoup `text()` 规范化吃掉换行. 多时段一行一段.
- 时段格式: `教室-房间(第{start}-{end}周[单周|双周] 星期{X}{时段时间})`. 例: `教学西楼D-101(第1-16周 星期一上1-2节)`.
- 节次正则: `([上中下晚])?(\d+)(?:-(\d+))?节?`. group 1 时段(上/中/下/晚), group 2 起节次序号, group 3 止节次序号(范围). baseline 偏移: 上→0, 中→4, 下→6, 晚→10.
- 节次展开: `for (n in startNum..endNum) nodes.add(base + n)`. 单节 (`X=空, endNum=空`) 时仅加入 `base + startNum`.
- 周次: `第(\d+)-(\d+)周` 取区间, `第(\d+)周` 取单周. `timeContent` 包含 `单周` → `type=1`; `双周` → `type=2`; 其余 (默认含起止相同区间) → `type=0`.
- 星期分段: `星期.+?(?=<|星期|$)`. 每段取前 3 字符查 dayNames 映射 (`星期一~星期日` → 1~7). `星期一` 在 dayNames 中下标 1, 故 `dayIdx > 0` 时采用 1..7.
- 合并: 对节点数组排序后聚类连续节次, 每段生成一条 `JwCourse(day, startNode..endNode)`. 同一时段一行可能产生多条 (跨多日).
- 名称合成: 有班级名称时 `课程名称(班级名称)`, 无则仅课程名称.
- 聚合字段: `note = 院系 + 课程编号 + 班级说明` 空格拼接, 作为元数据.
- 置信度: 命中 sample-table-1 锚点 +70, 同时含 `上课时间` 与 `课程编号` +20, 含 `主讲教师` 或 `班级名称` +10. 上限 100.
- 匹配特征: `id:sample-table-1`, `col:上课时间`, `col:主讲教师`.

## 协议族边界

- **NWPUPostParser** vs **NWPU 新门户 print-data**: 本协议锚 HTML, 不入 JSON. 新门户仓 6+ 全 INDIRECT, 标记 `print-data` 端点但与本解析器不兼容.
- **NWPUPostParser** vs **BUAA byxt.buaa.edu.cn**: BUAA 用 GSMIS 周次位图; NWPUPostParser 用 `第N-M周` 区间. 不同学校不同协议.
- **NWPUPostParser** vs **Wisedu 正方 zf_new**: 无共同字段. zf_new 用 `sleepyCfNtss` envelope, NWPUPostParser 是裸 HTML.
- **NWPUPostParser** vs **Jwxxt 强智 URP**: 无共同字段. URP 用 print-data JSON, NWPUPostParser 是 HTML.

## 改进点 (vs WakeUp 原版)

1. **节次范围解析**: WakeUp 用 `[上中下晚]?\d+`, 对 `下1-2节` 错误匹配 `[7, 2]` → 排序后断开. 本解析器改用 `([上中下晚])?(\d+)(?:-(\d+))?节?` + 范围展开, 正确产出 `[7, 8]`.
2. **换行保留**: WakeUp 用 Jsoup `text()`, 吃掉 `<br>` 换行. 本解析器显式 `replaceWith(TextNode("\n"))` + `wholeText()`, 保留多时段结构.
3. **双周判定**: 严格 `timeContent.contains("双周")` 检查, 不依赖区间对齐.

## 结论

- 本轮可确认: 协议锚点 / 列头 / 时段格式 / 节次范围 / 周次单双周判定 / 星期分段 / 5 课程段 fixture 全部契约已实现并通过 JVM 测试.
- 不能确认: GitHub 上无任何项目直接实现此协议层; 反编译端证据无法被外部仓库二次校验.
- 协议层 source of truth = WakeUp 反编译 smali (详见 `wakeup-jadx/sources/.../NWPUPostParser.java`).
- 跨仓求证 51 仓全部已记录于 `findings.json`, 致谢 20 个 INDIRECT 仓库 (新门户/智慧校园生态同校佐证).
