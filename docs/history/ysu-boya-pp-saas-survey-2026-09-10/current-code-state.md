# 当前代码状态

- `JwProtocol.TYPE_BOYA_PP` 已登记，`ALL_TYPES` priority=17。
- `JwParserRegistry` 将 `TYPE_BOYA_PP` 路由到 `JwBoyaPpParser`。
- `JwBoyaPpParser` 支持 `{rows:[...]}`、裸数组和 `{code,data}` 三种输入；过滤 suspended/deleted 行；按课程、星期、教室、教师集合聚合；仅在周集合相等时合并连续节次。
- `JwWebViewLoginScreen` 的 BOYA_PP fetch 分支逐周请求 `byStudent?whichWeek=N`，抓取 term、lessonConfig 和课表；401 终止并报告登录态失效；回传 `termStartDate`。
- YSU fixture 是 `app/src/test/resources/jw/fixtures/boya_pp/ysu-2026-2027-1.json`，对应 19 周、390 行实采数据。
- 当前终树来自 PR 原始提交 `4fb70c0` 的保留作者历史，合并提交为 `f59d63f`；与此前已验证终树 byte-identical。
- 本调研没有修改 parser 或入口代码。
