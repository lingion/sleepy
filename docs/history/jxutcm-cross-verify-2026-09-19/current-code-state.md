# `cf_new` 当前代码状态

日期：2026-09-19

## 路由与解析

- `app/src/main/java/com/lingion/sleepy/data/jw/JwCfNewParser.kt`
  - 读取 `sleepyCfNtss` envelope。
  - 扫描 `weeks`，建立 `periods` 与同响应行的时间到节次映射。
  - 解析 `ps/pe`、`qssj/jssj`、`zc`，按既有周次折叠器输出 `JwCourse`。
  - 对空课名、非法星期、无法定位节次、缺少周次的行做丢弃。
- `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCfNewFetchJs.kt`
  - 在同源 frame 树中查找 `/new/student/xsgrkb` 课表窗口。
  - 透传 `businessHours` 节次时间；取得学期和第一周周一。
  - 优先请求 `zc=''` 的全量形态，识别失败后并行回退 `zc=1..22`。
  - 只做抓取和组合，不解码课程字段；通过 `__sleepyBridge.onWiseduResult` 回传。

## 注册与接线

- `JwProtocol.TYPE_CF_NEW = "cf_new"`。
- `JwProtocol.ALL_TYPES`、display name、category 已登记；category 为 `cf`。
- `JwParserRegistry` priority 为 45，factory 指向 `JwCfNewParser`。
- `JwImportViewModel.parseHtml(..., "cf_new")` 通过 registry 选 parser；WebView 导入侧使用 `CF_NEW_FETCH_JS`。
- `JwCfNewFectJs.kt` 已更名为 `JwCfNewFetchJs.kt`，内容为纯文件名修正。

## 测试与 fixture

- `JwCfNewParserTest.kt`：7 个 JVM 测试，覆盖 envelope、跨周聚合、空 `ps/pe` 时间反推、混合周次、bucket 周次回退、`bapjxcd=1`、负例、confidence/matchedFeatures、周次列表解析。
- `JwProtocolAllTypesTest.kt`：锁定 `cf_new` 的存在、显示名和 category。
- `JwParserRegistryTest.kt`：类型数量及优先级顺序从 32 调整为 33。
- `app/src/test/resources/jw_fixtures/cf-new/ntss_mixed.synthetic.json`：脱敏合成 fixture，不是真实登录态采集包。

## 验证结果与剩余边界

- `:app:testDebugUnitTest`：全量通过，Gradle BUILD SUCCESSFUL。
- `:app:lintDebug`：未通过，共报告 38 errors、432 warnings、1 hint；报告未命中本次 `cf_new` 改动文件，首个错误为既有 `app/src/main/java/com/lingion/sleepy/util/HighRefreshRate.kt:20` 的 API 30 调用（minSdk 26）。
- 补充并脱敏真实响应/采集包仍待完成，当前合成 fixture 不能替代现场证据；端点、字段名、周次和节次语义仍需现场复核。
- 本 checkout 不含 `docs/sop/feature-baseline.md`，因此本分支没有可同步的 feature-baseline 文档；需在主仓库文档恢复后另行补记。
