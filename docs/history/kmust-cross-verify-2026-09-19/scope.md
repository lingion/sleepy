# 昆明理工大学跨仓验证

- 用户原话：把 4 所学校按照 SOP 流程全部做完，然后并且最终交叉验证。
- 学校：昆明理工大学（KMUST/KUST）
- 类型：新增独立协议；统一门户二维网格 JSON
- 入口：`https://i.kust.edu.cn/`
- 采集包 SHA-256：`3b53ac0cd57022a85dd4122fa6750758447863cc236ff5cfc9d95cd08bf52ff3`
- 证据：真实接口 `GET /api/uppcard/kbsz/queryAllTerm`、`GET /api/uppcard/kbsz/queryAWeekSchedule`；响应含 `data.resultsJsonArr`、`weekcount`、`swskjc`、`xwskjc`、`wsskjc`。
- 当前代码结论：现有 parser 均不匹配；新增 `kust` parser，按网格去重并解析课程/教师/教室/周次/星期/节次。
- 证据边界：fixture 仅使用脱敏接口响应；不提交会话凭据或门户个人信息。
