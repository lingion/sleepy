[evidence=A] 抓取 2026-09-17 · 源URL: https://open.flyme.cn/docs?id=241 (Flyme Link 接入规范, 一期已抓) · https://www.flyme.com/aios/ · flyme.com/firmwarelist-198.html (Flyme 12.6 Car Link)

# 魅族 Flyme — Flyme Link 互联实时卡片

> 一期已抓 Flyme Link 接入规范 (docId 241), 本篇只补"实时信息卡片"视角的增量与定性。

## 一、体系定性 (docId 241, evidence=A)

- Flyme Link = 手机-车机互联方案: 无感连接 / 应用上车 / 服务接力 / 硬件共享。
- 支持车型: 领克 / 吉利银河 / 极星等; 支持机型: 魅族 18~21 / Lucky 08。
- 接入条件: **商务合作 + 认证测试** (台架 2 台 + 实车 1 辆), 验收通过才可搭载 — 无自助开放入口。
- 手机域 AppWidget/通知与车机卡片不互通: 车机生态是独立框架 (一期结论仍然成立)。

## 二、实时卡片视角增量

- "服务接力"是唯一涉及实时状态的环节 (手机上正在进行的任务跨到车机), 官方文档无三方实时卡片 API; 12306 实况通知类能力无车机联动公开资料。
- Flyme 12.6 (2026-06-30, 魅族 21) 更新记录含 Car Link 升级, 无手机侧实时卡片新增 (flyme.com/firmwarelist-198)。
- flyme.com/aios 官方页功能清单中 "无界相册: 需配合 Flyme Auto 版本更新支持" — 手机-Auto 联动功能按版本逐步铺开, 与实况通知"部分功能陆续上线"同节奏。

## 三、结论

魅族互联链路对三方开发者 = 0 个可自助接入的实时卡片入口。Sleepy 无落点 (课程表无车机场景), 仅作体系完整性记录。
