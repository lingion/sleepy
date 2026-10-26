# WakeUp 协议族跨仓验证 — current-code-state (Step 5)

日期: 2026-09-16 · 基线 commit: 7a67b7f8 (cherry-pick 恢复后的 0955119c 等价提交)

## 实现文件

- app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt
  - WakeUpCompat.parse 总分派: parseJson → parseJsonCourses → parseJzJson → parseJzHtml → parseMarkedTable → parseDelimited
  - parseJzJson: xqj/djj/qmz/dsz (反转语义: 1→单, 2→每周, else→双)
  - parseJzHtml: #CourseFormTable <hr>/<br> 字段切分
  - parseKingoInfo: week1..week7 列号 day / jcxx a-b / week=row 索引
  - parseKingoTaskActivity: TaskActivity JS 网格, index=a*unitCount+b → day=a+1 (跨仓修正后), node 13→10/<9→+1/else+2, 位图周次+连续段压缩
  - parseXju: #ctl00_contentParent_dgData, sundayFirst 翻转, rowspan 折叠
  - parseSuda: DataGrid1/MainWork_DataGrid1 双选择器, 三教师通道, rowspan 折叠 (跨仓修正后)
  - parseShuwei: JSON activities 递归, parseActivity 字段族, parseWeekTokens 单双
  - parseCumtb: lessonList join scheduleList, timeToNode/durationToNodes, "未知" 占位 (跨仓修正后)
  - parseSouthSoft: studentTableVms/xq_jc KEY/SKSJ JSON 通路
  - parseChaoxing: kckbData 双兜底, xq/djc/zctype/zc
- Parser 注册: JwKingoParser/JwJzParser/JwSouthSoftParser/JwChaoxingLegacyParser/JwShuweiParser/JwSudaParser/JwCumtbParser/JwXjuParser
- JwProtocol.kt: TYPE_KINGO_NEW/TYPE_JZ/TYPE_SOUTH_SOFT/TYPE_CHAOXING_LEGACY/TYPE_SHUWEI/TYPE_SUDA_POST/TYPE_CUMTB/TYPE_XJU_POST 8 族已注册

## 测试

- app/src/test/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParserTest.kt: 11 用例
  (chaoxing/cumtb×2/south_soft/jz JSON/jz HTML/kingo/xju/suda×2/shuwei)
- 全套: 1851 tests 0 failures (--rerun-tasks fresh)

## 已知 lint 基线

- lintDebug 38 errors 全部 pre-existing (origin/main 已有): 36 MissingTranslation + 1 NewApi (HighRefreshRate.kt, 并发 agent 提交 9f1e97e7 带入) + 1 ByteOrderMark; 本轮 diff 0 新增
