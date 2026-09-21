# Scope — 新疆大学研究生 (yjspy) 学期课表信息查询解析修复

日期: 2026-09-17
分支: worktree-fix-xju-parse (HEAD a453b5f7, base origin/main)
工作目录: /Users/lingion_k/sleepy/.claude/worktrees/fix-xju-parse
配套 fix-archive cross-verify 2026-09-16: docs/xju-postgraduate-cross-verify-2026-09-16/

## 用户原话

「新疆大学依然显示识别不了。」
（连续追问 "就做完行不行？这么简单的一个东西，你干一个下午了，你干不完，你非要中断中断中断" — 反映用户对中途反复被打断的强烈不满; 要求不停顿一次性完成）

## 现象与既往归档的关系

- 2026-09-16 一轮完成适配（commit 22feef70 收录 + edbf1f69 anchor 修复）后, 用户仍报"识别不了"。
- 检索范围: 当晚基于真实抓取页验证 → 见 real-dgdata-table.html (sanitized 12 行课表), 形态与 SOP v1.11 §4 "Gwork 研究生族" 描述不一致:
  - SOP/旧 parser 默认 形态 A: th="节次|周X", 节次=阿拉伯数字, 单元格 "｛名(周次)[教师:X,地点:Y]｝"
  - 真实 学期课表信息查询 = 形态 B: th="时间|节次|周X", 时间列 rowspan=4, 节次=中文数字"一".."十二", 课程名在花括号外 "名｛周次[…]:…｝", 课程行用 rowspan 跨节次合并 (连堂).
- 旧 parser 形态 A 假定全部不命中 → 解析出 0 门课 → 用户可见 "当前页面未检测到课表容器".

## 根因（修 bug 必写）

旧 parseXju 直接用 col 索引当 day，遇到形态 B 时两个错位叠加：
1. th 序为 时间|节次|周一..周日, ths.firstDayIdx=2 → col=2 才是周一, 旧代码 col-0=周一 → 全部日列左移 2.
2. 上午/下午/晚上 三个 td 用 rowspan=4 占 4 行, 旧代码不追踪 rowspan 残留 → 后续 4 行的 col 游标不前进, 数据格落错列.
3. 旧代码以 "第 N 节" 阿拉伯行标为前提 → 中文 "一".."十二" 全部识别为 0, 全部 rowToNode=0 → 没有节点写入 grid → 0 课.
4. 旧提取规则要求 "｛(周次)[…]｝" → 形态 B 课程名在花括号外 → regex 不命中 → 0 课.

## 修复（按 jw-cross-verify-sop §4 Gwork 形态归并, 不靠 git 仓名做数维判定）

`app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt::parseXju` 全面重写:
1. 节点 ID 识别: 中文一..十二 + Arabic 1..20 + "第N-M节" 区间起点, 任一命中即用.
2. 第二遍遍历跟踪 (col, row) 网格: 每行先按 colExpire 跳过上方 rowspan 残留列; 课程格按 colspan/rowspan 写入 (col, rowIdx..rowIdx+rs-1).
3. th[firstDayCol] 取首列日, grid[day,node] = raw; 连续相同 raw 合并成 endNode (rowspan 延展与连堂合并覆盖).
4. extractXjuCourses 自动判形态 A / 形态 B: 块以 ｛ 开头且首段含 (周次)+[教师/地点] → 形态 A (旧 fixture 兼容); 否则为形态 B, 课程名=namePrefix, 每个 ｛…｝ 走 parseXjuWeeksWithBrackets.

## 用户原话 vs 实现行为 diff

| 用户原话 | 实现行为 |
|---|---|
| "新疆大学依然显示识别不了" | parseXju 形态 B 路径走通, 真实页解析出 ≥ 6 条 (3 课 × 2 时段) |
| 隐含: 仍是同一份 SOP, 别新增协议 | type=xju_post 复用, schools.json 不动 |
| 隐含: 旧 fixture 不能打破 | 形态 A 解析仍在 / 旧 3 个 JwWakeUpCompatParserTest 仍全绿 |
| 隐含: 别新增致谢 | 致谢入口已含 eduData-GoBack (huhu415) / SCAU-Grad (jiefing) / shiguang_warehouse (XingHeYuZhuan), 无新增 |
| 隐含: 真实 token 不可外泄 | real-dgdata-table.html / gwork-testform.html 抓取证据已 SANITIZE: VIEWSTATE / EVENTVALIDATION / EID / UID 全部脱敏 |

## 测试锁

新增 `xju-postgraduate-real-gwork.html` fixture (12 数据行, 时间列三段 rowspan=4, 课程行 rowspan=2).
`XjuPostgraduateAdmissionTest#real gwork 学期课表信息查询 page parses courses with time-band header and chinese node labels` 锁:
- 高级算法 星期三 第 3-4 节 教师 孙冬璞 周 12-19
- 高级算法 星期一 第 5-6 节 教师 孙冬璞 周 12-19
- 机器学习 星期二 第 5-6 节 教师 陈晨 周 3-10
- 机器学习 星期四 第 7-8 节 教师 陈晨 周 3-10
- 组合数学 星期二 第 7-8 节 教师 高峻 周 4-11
- 组合数学 星期四 第 9-10 节 教师 高峻 周 4-11
- 总数 ≥ 6

`real form B page inside PageFrame iframe is selected and parsed end to end` 整链锁:
frameset + PageFrame(StuCourseQuery.aspx 形态 B) → dgData 锚选中 → JwXjuParser ≥6 条 →
抽验 day/startNode/teacher 端到端不换样.

红绿验证 (TDD §6): 撤掉 JwWakeUpCompatParsers.kt 修复 → 该测试由绿翻红 → 恢复 → 仍绿.
整链测试红绿同法: revert 时 selectBestFrame 仍选 PageFrame (锚在), 但 parser 出 0 课 → 断言红.

## SOP 影响

未触发 SOP 改动: jw-cross-verify-sop §4 Gwork 形态已含 A/B, 形态 B 此处补全; §1 12 步归档 + §6 致谢不需扩展 (致谢未增).

## 文件变更清单

- M app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt — parseXju 重写 + extractXjuCourses 形态自动判 + XjuWeekSlot 数据类
- M app/src/test/java/com/lingion/sleepy/data/jw/XjuPostgraduateAdmissionTest.kt — 新增 #11 real gwork 学期课表信息查询 page parses courses with time-band header and chinese node labels, 锁 6 个 (day, node) 节段
- A app/src/test/resources/jw_fixtures/xju-postgraduate-real-gwork.html — 真实页脱敏复刻 fixture
- A docs/xju-postgraduate-parse-fix-2026-09-17/scope.md (本文)
- A docs/xju-postgraduate-parse-fix-2026-09-17/findings.json
- A docs/xju-postgraduate-parse-fix-2026-09-17/attribution-candidates.json
- A docs/xju-postgraduate-parse-fix-2026-09-17/real-dgdata-table.html (sanitized)
- A docs/xju-postgraduate-parse-fix-2026-09-17/gwork-testform.html (sanitized)

## 测试结果

- XJU 单测 12/12 全绿 (含整链帧捕获测试)
- 全套件 1966/1966 全绿
- Lint 38 errors / 434 warnings / 1 hint = 与 main pre-existing 基线一致 (零新增)