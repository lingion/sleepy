# Scope — 中国科学院大学（UCAS）

Issue: [#18](https://github.com/lingion/sleepy/issues/18) "求适配中国科学院大学"。

- 入口：`https://xkgo.ucas.ac.cn:3000/course/personSchedule`
- 类型：初次适配，服务端 HTML 课表网格。
- 采集包：`sleepy-adapt-0906-223916.zip`，SHA-256 `71c79d315c8d4eff38ebdd1f79cc056f1094cc04b5a9a310c86a9a110bc49119`。
- 采集包 v1.2：`sleepy-adapt-0908-222517.zip`，SHA-256 `38f9c901c8dee8ee4a069c2b8809af78d764b8957b4da8cf9c680e91287d68e9`（damifan3 回传，2026-09-08，`4-detail-nav/` 含成功详情页 DOM）。
- 执行：单线程；未使用 subagent。

## SOP 状态

1. 完成：Issue、附件、URL 和真实页面范围锁定。
2. 完成：GitHub 多源检索。
3. 完成：直接采集证据与上游项目协议提取。
4. 完成：协议矩阵与现有 parser 对比。
5. 完成：新增 `ucas` 注册、课表网格 parser、脱敏 fixture、定向测试。
6. 完成：`sleepy-collector v1.2` 修复跨来源详情页被页面上下文 CORS 和伪造 POST 阻断的问题；新包会把课程详情页写入 `4-detail-nav/`，并输出去重结果汇总。
7. 完成：v1.2 采集包（`sleepy-adapt-0908-222517.zip`）`4-detail-nav/` 含成功详情页 DOM；实现详情页周次解析（跨源免登录详情站 `xkcts.ucas.ac.cn:8443` 原生 HTTP 直抓 + `splitWeekRuns` 带洞周次拆段），脱敏 fixture 2 份 + 断言，UCAS 测试 16 条全绿，全量 1248 tests / 0 failures / 0 errors。完成门槛三项全部满足。

## 完成门槛

✅ 已满足（2026-09-08）：v1.2 采集包（`sleepy-adapt-0908-222517.zip`）含成功详情页 DOM → 详情页周次解析 + `splitWeekRuns` 带洞周次拆段 + 脱敏 fixture + 断言 → 全量测试绿。#18 代码侧适配完成，issue 状态变更（回复/关闭）待用户明示批准。

## 隐私边界

原始附件只留在本地 `capture/`，不提交。测试 fixture 仅保留脱敏的 table 网格。
