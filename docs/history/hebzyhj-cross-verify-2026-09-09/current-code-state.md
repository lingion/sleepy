# hebzyhj 现状代码状态 (Step 5)

## 既有 (adapt/hebzyhj-qzapp 分支起点 a6e2cac = main HEAD)

- JwProtocol.kt: 28 协议 type 常量 + ALL_TYPES + displayName/category
- JwParserRegistry: TYPE_PRIORITY + FACTORIES, 兜底裁决 (selectBest)
- JwWebViewLoginScreen: 通用 Wisedu 流 + NEU/CQU/CHAOXING/WHUT/EAMS5 fetch 分支
- qz 系 6 parser: qz/qz_crazy/qz_br/qz_with_node/qz_ieas/qz_old (全部 HTML/旧 JSON 形态)

## 新增 (本分支未 commit 改动)

| 文件 | 改动 |
|------|------|
| app/src/main/assets/schools.json | +1 条目 (河北资源环境职业技术学院, type qz_app, url /dist/#/login) |
| JwProtocol.kt | +TYPE_QZ_APP="qz_app" + ALL_TYPES 29 项 + displayName "强智移动教务" + category qz |
| JwParserRegistry.kt | +TYPE_PRIORITY 141 + FACTORIES → JwQzAppParser |
| JwWebViewLoginScreen.kt | +TYPE_QZ_APP dispatch 分支 + QZ_APP_FETCH_JS 常量 (serverconfig 发现+token 头+401 路由) |
| JwQzAppParser.kt (新) | JSON 课表解码: classTime/classWeek/classWeekDetails → JwCourse |
| fixtures/qz_app/curriculum.sample.json (新) | 去敏 fixture (PII 已清) |
| JwQzAppParserTest.kt (新) | 13 测试: 全量计数/day histogram/classTime 不变量/周段拆分/字段映射/对抗输入/confidence 锚 |
| JwQzAppWebViewContractTest.kt (新) | 2 测试: dispatch 分支 + JS 契约 (serverconfig 发现/token 头/401/信封/跨语言 invariant) |
| JwParserRegistryTest.kt | 28→29 ALL_TYPES + qz 系 6→7 |
| SchoolsJsonConsistencyTest.kt | declared 集合 + TYPE_QZ_APP |
| Schools179CrossValidationTest.kt | 校数 181→182 |

## 测试/lint 状态

- 全量 :app:testDebugUnitTest BUILD SUCCESSFUL
- :app:lintDebug 35 errors 全部 pre-existing (strings.xml MissingTranslation 33 + HighRefreshRate NewApi + ScheduleParser BOM + widget_scroll_clip MissingPrefix); 本改动 0 新 lint 项
