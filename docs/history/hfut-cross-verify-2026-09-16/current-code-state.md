# HFUT issue #46 — 现状代码阅读

## 入口链 (动手前已核)

- `JwImportActivity.kt:426` onHtmlCaptured(html, sch, periods, termStartDate)
  → effectiveType = sch.type ?: detectProtocol → `jwViewModel.parseHtml` → 配置确认页
  → `periods` 参数: 桥回调 `periods[]` {node,start,end} → 确认页作息表预填 (`periodMap`, JwImportActivity.kt:466)
- `JwWebViewLoginScreen.kt:342-344` CaptureBar: type=eams5 → `EAMS5_FETCH_JS.replace(EAMS5_PREFIX_PLACEHOLDER, prefix)`
- `handleWiseduResult` (JwWebViewLoginScreen.kt:139): ok → `data`(string) + `periods[]`(node/start/end) → onHtmlCaptured
- `Eams5PathPrefix.kt`: EAMS5_STUDENT_ID_REGEX (跨语言 invariant, 与 JS 逐字符相等, HfutPortalEams5WebViewContractTest 锁)

## EAMS5_FETCH_JS 四段链 (现网)

1. GET `{PREFIX}/for-std/course-table` → html/finalUrl 提 studentId (regex A-F 形态; 兜底 /info/(\d+))
2. GET `/for-std/course-table/info/<sid>` → bizTypeId regex (+semesterId 兜底)
3. GET `/for-std/course-table/get-data?bizTypeId=&dataId=<sid>[&semesterId=]` → 顶层 lessonIds[]
4. POST `/ws/schedule-table/datum` {lessonIds, studentId(int), weekIndex:''} → 回传 `data=txt, periods:[]`

## JwEams5Parser 三形态

- AHU studentTableVms[0].activities (confidence 100)
- AHU data.lessons (85)
- HFUT result.lessonList+scheduleList (95): courseName 查表 / room.nameZh / personName / weekday / weekIndex 单值 / inferNodes(startTime,endTime,periods)

## inferNodes 现状 (缺陷落点)

- `sectionIndex(time)`: 5 个硬编码段起点 [480,610,840,970,1140] ±10min
- startNode = sec*2+1; endTime 同段 → +1; 跨段 → sec*2; null 段 → 兜底 node1..periods
- 宣城 12 节实况下 (1550,1730)→null(兜底错), (1920,2100)→sec4→node9-10(差1)

## 回归面

- CUMTB / 老采集包: 无 courseUnitList → heuristic 路径必须保持逐字节等价
- Eams5StudentIdExtractionTest / Eams5PathPrefixTest / HfutEams5DatumChainContractTest / HfutPortalEams5WebViewContractTest / HfutIssue25UrlEntryTest: 全部不动语义
