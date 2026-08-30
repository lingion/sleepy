# CF/乘方 协议 fixture 集合

**协议族**: CF (ChengFang) = 乘方教务 = 青果教务（同源厂商；shiguang_warehouse JXFU_01.js 与 dIT8Zv/WakeupSchedule_BUPT ChengFangParser.kt 共用同一字段名)
**上游参考**: https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/ChengFangParser.kt
**字段 Bean**: https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/bean/ChengFangInfo.kt
**JS 参考**: https://github.com/XingHeYuZhuan/shiguang_warehouse/blob/master/resources/JXFU/JXFU_01.js

## 数据结构契约

页面源 HTML 内嵌：`var kbxx = [ChengFangInfo, ...]`（通常在 `<script>` 块，可跨多行；上游 Kotlin `substringAfter("var kbxx = ").substringBefore(';')`，JXFU JS 用非贪婪 `\[([\s\S]*?)\]\s*;`）

ChengFangInfo 字段（全部为 String）:
| 字段 | 含义 | 解析到 JwCourse |
|------|------|-----------------|
| `kcmc` | 课程名 | `name` |
| `teaxms` | 教师（多教师以 `;` 或 `/` 分隔，详见 uncertainties） | `teacher` |
| `jxcdmcs` | 上课地点（含嵌套引号的汉字串） | `room` |
| `xq` | 星期，1-7 字符串 | `day.toInt()` |
| `jcdm2` | 节次代码，逗号分隔，如 `"1,2"` / `"05,06"` / `"9,10,11"` | 第一个是 startNode，最后一个是 endNode；step = endNode - startNode + 1 |
| `zcs` | 周次，逗号分隔的单号集合，如 `"1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16"` 或 `"1,3,5,7,9"` (单周) 或 `"2,4,6,8"` (双周) | 归并成 (startWeek, endWeek, type) |

类型字段语义（与 JwCourse.type 完全对齐）:
- `0` = 每周（连续周，相邻差 1）
- `1` = 单周（差 2 且起始为奇数）
- `2` = 双周（差 2 且起始为偶数）

归并算法遵循 upstream `Common.weekIntList2WeekBeanList`：扫描排序后数组，按相邻 gap=1/2/>2 切换段。

## fixture 列表

| 文件 | 场景 | 课程数 |
|------|------|--------|
| `typical_two_courses.html` | 正常 2 课，连续 1-16 周，每天 2 节；星期 1+3 | 2 |
| `single_double_weeks.html` | 跨厂商：单周 体育 + 双周 实验，时段与室内外场地 | 2 |
| `multi_segment_weeks.html` | 多段周次（带缺口）+ 单节 + 3 节连堂 | 4 |
| `empty_timetable.html` | 合法空 `var kbxx = []`（学期无排课） | 0 |
| `login_page_no_kbxx.html` | 未登录态纯登录页，**没有** `var kbxx` 块 | 0 |
| `missing_fields.html` | 字段边缘：缺教师、缺教室、空周次 | 3（缺周次跳过） |
| `escaped_quotes_multiline.html` | 多行 script + 字符串内含 `""` 转义引号 + 中文括号课程名 | 2 |

## 关于 expected.json 语义的说明

1. **对齐策略**: 严格 follow upstream `ChengFangParser.generateCourseList` + `Common.weekIntList2WeekBeanList` 的行为；
2. **顺序**: 同一 (day, startNode, name) 重复出现时，upstream 没有去重，expected 按 JSON 输入顺序输出；
3. **缺失字段**: 上游用 `it.zcs.split(',').forEach { str -> weekList.add(str.toInt()) }`，对 `zcs=""` 会抛 NumberFormatException。sleepy 移植版（参考 JwNewUrpParser 已有的 `?: continue` 模式）应改为 `toIntOrNull() ?: continue` 跳过这一行；`missing_fields.expected.json` 反映这一容忍行为；
4. **跨周缺口的"双周误判"陷阱**: 当 `zcs="1,2,3,4,5,6,7,8,10,11,...,16"` 时，8→10 的 gap=2，算法会从 1 一路连到 8 输出 type=0 (1-8)，再从 10 输出 type=1 (10-16)?? — **不会**；因为 1→2 gap=1 锁定 temp.type=0 后，遇到 8→10 gap=2 时 `if (temp.type != -1) reset = 1` 触发，切段。所以 1-8 段 type=0，10 再开始判断 10→11 gap=1 → 第二段 type=0 (10-16)。`multi_segment_weeks.expected.json` 的"线性代数"已验证为 2 段各 type=0。

## 与上游字段名的差异

| sleepy 字段 | upstream 字段 | 备注 |
|------|------|------|
| `day` | `xq.toInt()` | xq 是 String |
| `startNode` | `jcdm2.split(',')[0].toInt()` |  |
| `endNode` | `startNode + step - 1` | **不直接是 jcdm2 末元素**，而是按 step 重算 |
| `startWeek` / `endWeek` | 来自 WeekBean 归并 |  |
| `type` | 来自 WeekBean.type | 0/1/2 见上表 |

## 不确定项 (uncertainties)

1. **多教师/多教室拆分**: 多所院校实际页面 `teaxms` 含 `/` `;` `、` 等分隔符（如 "张老师 / 李老师"）。upstream 不拆分，作为整体塞进 `teacher` 字段；sleepy 移植应保持原样，由 UI 层处理。fixture 用 "周老师 / 课程组" 验证 trailing 字符能完整保留。
2. **字符串转义**: 上游 Gson 默认容错 `JSONObject("...")` 处理 `\"` 等内嵌引号；本次 fixture 把 `jxcdmcs` 写成 `主教学楼 "304" 室`（注意是字面中文引号 `\"304\"`，不是 JSON 字符串的转义），未来如果对真实页面采样出现 `主体教学楼 \"304\" 室`（JSON 转义双引号），可能因为 `substringBefore(';')` 越过 `;` 包含过多内容。需要在 W2 fixture 测试中验证 charsBeforeSemicolon 边界。
3. **登录页被服务端代理伪装**: 部分院校未登录时也写 `var kbxx = []`（与 `empty_timetable` 一致）；本次 fixture 选择纯登录页（无 `var kbxx`）模拟另一类未登录场景。
4. **Fn 步骤值**: `step` 是节次数，不是节次差本身 (如 "9,10,11" step=3)。这影响 `endNode` 的算法：startNode=9，endNode=9+(3-1)=11。
5. **没有真实登录态样本**: 所有 fixture 都是基于上游算法 + 公开语义手写脱敏，没有任何来自 `jxfw.gdut.edu.cn` 等真实登录后页面的 HTML；下游必须用真实登录后 View Source 校验。
