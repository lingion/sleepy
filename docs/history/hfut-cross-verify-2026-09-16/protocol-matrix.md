# HFUT issue #46 — protocol matrix (增量形态)

基线: `docs/history/hfut-cross-verify-2026-09-09/protocol-matrix.md` (#25 四段链定稿) + 本轮增量。

## 增量维度: 节次来源 (datum startTime → 节点)

| 维度 | 现网 v2 (fb39af5a) | 真实服务端 (#46 采集包) | 社区共识 (11 仓) |
|---|---|---|---|
| 节次数据源 | JVM 端 inferNodes 硬编码 5 段×2 节 985 表 | `POST /ws/schedule-table/timetable-layout {timeTableLayoutId}` → `result.courseUnitList[]` (indexNo/startTime/endTime/dayPart) | 全部 fetch layout: HFUTer(Swift)/AISchedule/classduck(小爱系)/Chiu-xaH/hfut-soft-ware/SnowingFox/onlineG2; USTC kirsh1 同构 |
| 映射算法 | startTime ±10min 吸到段起点 → node=sec*2+1; endTime 段内 → +1, 跨段 → sec*2 | schedule.startTime **精确等于** courseUnit.startTime (122/122 行) | HFUTer/kirsh1: startTime→indexNo 查表; kirsh1 查不到再按区间落点; abydym: 布局缺失退本地默认表 |
| 宣城 12 节实况 | (1550,1730)→null 段→兜底 node1..; (1920,2100)→node9-10 | 1550→unit7, 1645→unit8 (7-8 节); 1920→unit10, 2015→unit11 (10-11 节) | — |
| 错行率 | 18/122 (15%): 10 行错到上午第 1-2 节, 8 行偏 1 节 | — | — |

## datum 122 行 (startUnit,endUnit) 分布 (采集包实测)

(1,2)×22 · (3,4)×47 · (5,6)×35 · (7,8)×10 · (10,11)×8

courseUnitList (layout id=122「各校区春秋季课表布局（新）」):
unit1 800-845 · 2 855-940 · 3 1000-1045 · 4 1055-1140 · 5 1400-1445 · 6 1455-1540 · 7 1550-1635 · 8 1645-1730 · 9 1740-1825 · 10 1920-2005 · 11 2015-2100 · 12 2110-2155

映射规则 (全 122 行零失败): startUnit = unit.startTime == schedule.startTime 的 indexNo; endUnit = max{unit.endTime <= schedule.endTime} 的 indexNo。

## 门户 (one.hfut.edu.cn) 增量

| 维度 | #25 时点 | #46 采集包 |
|---|---|---|
| 课表能力 | 无 (8eef1ae7 拦截) | 课表预览卡: `GET /api/operation/course-timetable/search/1/<date>` (Authorization: Bearer oauth token) |
| 数据范围 | — | 本周 + 下周 (thisWeek 4 行 + nextWeek 8 行), 字段 skjc=起始节次/cxjc=节数/dayOfWeek/jxdd/kcmc/jsxm |
| 决策 | 拦截+引导去 jxglstu | **拦截策略不变** (预览非全学期; 全学期只有 datum 18 周 122 行); 文案改准确 |

## 不变量 (维持)

- get-data bizTypeId: 23=宣城 (hfut-soft-ware 旁证 2=合肥), dataId=studentId
- datum body: `{lessonIds:[…], studentId:<int>, weekIndex:""}` → 全学期所有周
- lessonId2Flag (publish/dontNeedSchedule) 服务端已过滤 datum scheduleList, 客户端不重复过滤
- 会话失效: finalUrl 含 /login
