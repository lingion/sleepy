# zf-old-table1 fixture set

老正方教务系统（default2.aspx 时代，xskbcx.aspx 课表页，`table#Table1`）协议族 contract fixtures。
对应 plan T1/T10，关联 upstream `ZhengFangParser.kt` + `Common.kt`（dIT8Zv/WakeupSchedule_BUPT, Apache-2.0）。

## 文件

| HTML 样本 | expected 期望 | 场景 |
|---|---|---|
| `standard_single_course.html` | `standard_single_course.expected.json` | type=0 标准单课程格（4 字段 name/time/teacher/room），跨节 1,2 节；另一门带 `\|双周` |
| `standard_multi_course.html` | `standard_multi_course.expected.json` | 同格 `<br><br>` 双课 + 单双周 + 周次边界 17-17 + 周日列末节 |
| `abnormal_br3.html` | `abnormal_br3.expected.json` | type=0 异常变体：`<br><br><br>` 三连 br 分隔，3 行 [名,时间,老师]，room="" |
| `property_row_extended.html` | `property_row_extended.expected.json` | 命中 T1 修复点：上游 47 词表中 sleepy 缺的"通识必修/专业必修/学科必修/体育必" |
| `digit_headers.html` | `digit_headers.expected.json` | 数字行头"第1节"~"第8节"+"中午"组头（OTHER_HEADER 缺"中午"无实际影响）+ 通识必修/体育必属性行 |
| `empty_table.html` | `empty_table.expected.json` | 边界：完整表头/组头/行头，所有课程格 `&nbsp;` → 0 课程 |
| `login_page.html` | `login_page.expected.json` | 边界：登录页无 `Table1` → 0 课程，JwProtocolDetector 应识别为 TYPE_LOGIN |

辅助：`_verify.py` —— 忠实移植 upstream ZhengFangParser type=0 路径的 Python 实现，对所有 fixture 自检。
跑法：`python3 _verify.py`，预期全 PASS。

## 字段语义（与 JwCourse.kt 一致）

- `name`：课程名（"<br>" 前第一个 token）
- `day`：1=周一 … 7=周日（chineseWeekList 下标）
- `startNode`/`endNode`：节次起止（行头节点 → startNode，时间串决定 step）
- `startWeek`/`endWeek`：周次区间，`{第N-M周}` 解析失败时默认 1/20
- `type`：0=每周，1=单周，2=双周
- `teacher`/`room`：上游字段顺序为 [time, teacher, room] → 5 行; [time, room] → 4 行（teacher=""）; 三连 br 异常时 3 行 [time, teacher]（room="")

## 与上游的关键差异点（sleepy 需修复，T1）

1. **COURSE_PROPERTY 词表差 23 项**：fixture `property_row_extended.html` 与 `digit_headers.html` 命中"通识必修/专业必修/学科必修/体育必"。
2. **OTHER_HEADER 缺"中午"**：fixture `digit_headers.html` 含"中午"组头，验证无回归（无实际影响）。
3. **CHINESE_WEEK_LIST 缺"周天"**：fixture 未覆盖，样本中暂用"周日"。

## 断言依据

W2 阶段 `JwParserFixtureTest.kt` 应遍历本目录每个 `*.html`，断言：
1. 解析出的 `JwCourse` 数量 == `expected.courses.length`
2. 逐字段（name/day/startNode/endNode/startWeek/endWeek/type/teacher/room）相等
3. 边界样本（`empty_table`、`login_page`）期望空列表

## 已知 uncertain

- 上游三连 br 异常布局（`<br><br><br>`）的 3 行解析中，teacher/room 分配语义与 4 行常规的差异：上游 `parseImportBean` 对 3 行的判断 `if (!isAbnormal) room=split[2]` 而 `else teacher=split[2], room=""`，反向成立。这是上游原代码，本 fixture 忠实保留。
- room 字段上游不做 nbsp 清洗（如 `<br>&nbsp;</a>`），导致 room 为字面量 `"&nbsp;"`。本 fixture 全部用真实房间字符串，规避此问题。
- 行头 `第1-2节` 合并节次变体上游和 sleepy 都漏处理，**未在本 fixture 覆盖**（属 P2 范围）。