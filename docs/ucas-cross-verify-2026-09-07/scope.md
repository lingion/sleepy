# Scope — 中国科学院大学（UCAS）

Issue: [#18](https://github.com/lingion/sleepy/issues/18) "求适配中国科学院大学"。

- 入口：`https://xkgo.ucas.ac.cn:3000/course/personSchedule`
- 类型：初次适配，服务端 HTML 课表网格。
- 采集包：`sleepy-adapt-0906-223916.zip`，SHA-256 `71c79d315c8d4eff38ebdd1f79cc056f1094cc04b5a9a310c86a9a110bc49119`。
- 执行：单线程；未使用 subagent。

## SOP 状态

1. 完成：Issue、附件、URL 和真实页面范围锁定。
2. 完成：GitHub 多源检索。
3. 完成：直接采集证据与上游项目协议提取。
4. 完成：协议矩阵与现有 parser 对比。
5. 完成：新增 `ucas` 注册、课表网格 parser、脱敏 fixture、定向测试。
6. 完成：`sleepy-collector v1.2` 修复跨来源详情页被页面上下文 CORS 和伪造 POST 阻断的问题；新包会把课程详情页写入 `4-detail-nav/`，并输出去重结果汇总。
7. 未完成：现有附件没有成功的详情页或 `courseTimeList` 响应，无法验证每门课的实际周次。现有 parser 的 `1–16` 是临时回退，不能作为 #18 完成证据。

## 完成门槛

必须取得一份由 v1.2 或更高版本产生的新 UCAS 采集包，且其中包含成功详情页 DOM 或带 `courseWeek` 的 JSON；随后实现位图周次解析、加入脱敏 fixture 和断言，再重跑应用测试。缺少这三项时，#18 保持未完成。

## 隐私边界

原始附件只留在本地 `capture/`，不提交。测试 fixture 仅保留脱敏的 table 网格。
