# 四校最终交叉验证（2026-09-19）

## 用户原话 vs 实现行为

用户原话：把 4 所学校按照 SOP 流程全部做完，然后并且最终交叉验证。

| 学校 | 证据形态 | 实现行为 | 验证结果 |
|---|---|---|---|
| 西北政法大学 | classic EAMS `TaskActivity`、`unitCount=10`、`index=D*unitCount+P`、周次位图 | `JwClassicEamsParser` 按页面节次数、index 和位图展开 | fixture parser 通过；URL 路由为 `classic_eams` |
| 上海立信会计金融学院 | classic EAMS Beangle `newActivity` + `addActivityByTime` | `JwClassicEamsParser.timeActivities()` 解析绝对分钟、日、教室、教师和周次 | fixture parser 通过；URL 路由为 `classic_eams` |
| 昆明理工大学 | `/api/uppcard/kbsz/queryAllTerm` + `/queryAWeekSchedule`，`resultsJsonArr` 网格 | `JwKustParser` 解析 `data.resultsJsonArr`，去重重复网格格子并合并连续节次 | fixture parser 通过；URL/HTML 路由和 registry 契约通过 |
| 广东东软学院 | Wisedu `currentUser.do` + `student/courses.do`，`classDateAndPlace` | `JwNuitParser` 解析周次、星期、节次、教师、教室并逐周展开 | fixture parser 通过；URL/HTML 路由和 registry 契约通过 |

## 不变量核验

- 四个学校条目存在于 `app/src/main/assets/schools.json`，测试资源副本逐字同步。
- `TYPE_KUST`、`TYPE_NUIT` 存在于 `ALL_TYPES`、display name、category 和 parser registry。
- classic EAMS 两校共享 parser family，但 LIXIN 的绝对时间 activity 形态由独立分支处理。
- KUST 与 NUIT 不路由到 NEU `arrangedList` parser。
- 所有提交 fixture 均为脱敏/合成 fixture，不冒充现场采集包；现场证据仅以 scope 中的 capture hash 和字段记录作为边界。
- 未提交 Cookie、账号、令牌或其他认证材料。

## 验证命令

- `./gradlew :app:testDebugUnitTest --tests '*FourSchoolParserTest*' --no-daemon`：通过。
- `./gradlew :app:testDebugUnitTest --tests '*FourSchoolProtocolContractTest*' --no-daemon`：通过。
- `./gradlew :app:testDebugUnitTest --tests '*FourSchool*' --tests '*AboutLicenseAttributionTest*' --no-daemon`：通过。
- `./gradlew :app:testDebugUnitTest --no-daemon`：通过，1997 tests。
- `./gradlew :app:lintDebug --no-daemon`：未通过；38 errors/435 warnings，首个及基线同为既有 `HighRefreshRate.kt:20` `NewApi`，未发现新增错误路径。

## 证据与剩余边界

- 四个 capture package 的 SHA-256 分别记录在各自 `scope.md`。
- 采集包不进入 checkout；因此不能把其内容描述为已提交的原始包。
- LIXIN 的 minute-to-section 映射按捕获页面时间槽归一化；若门户后续更改作息表，应重新采集并更新映射证据。
- KUST fetch 当前取接口返回的首个学期；若门户返回顺序不再代表当前选中学期，需要基于新的 capture 增加选中学期字段映射。
