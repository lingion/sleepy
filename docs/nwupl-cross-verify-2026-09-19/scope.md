# 西北政法大学跨仓验证

- 用户原话：把 4 所学校按照 SOP 流程全部做完，然后并且最终交叉验证。
- 学校：西北政法大学（NWUPL）
- 类型：新增学校适配；复用 `classic_eams`
- 入口：`https://tam.nwupl.edu.cn/eams/courseTableForStd.action`
- 采集包 SHA-256：`8040c31533b27b4b57412f07ca47820fd25497229d25d29916326488def1048e`
- 证据：真实采集包 `3-res/eams_courseTableForStd.action.html` 与 `4-net-live/eams_courseTableForStd_courseTable.action_2.html`，包含 `var unitCount = 10`、`new TaskActivity`、`index = day * unitCount + section`。
- 当前代码结论：现有 `JwClassicEamsParser` 可直接解析协议；需补学校条目、fixture、协议契约和回归测试。
- 证据边界：真实采集包用于协议确认；提交的 fixture 脱敏且明确标为真实形态样本，不包含 Cookie、账号或令牌。
