[evidence=-] 整理 2026-09-17

# 魅族 Flyme — 实时信息卡片缺口清单

## 已破

- 用户手册「实况通知」(docId 317) /「灵动环」(306) /「通知管理」(263) /「Aicy 建议」(270) /「Aicy 纵览」(272) 原文 = apiopen.flyme.cn doc-wiki/detail 破口, evidence=A。
- 接入机制 = GitHub demo 仓库源码逐行 (extras 键全表)。

## 未破缺口

1. **三个枚举值语义**: `notification.live.operation` / `notification.live.type` / `notification.live.capsuleType` 的合法值域与语义无任何公开资料 (demo 作者自己标 idk)。破解路径: 反编译 Flyme AIOS SystemUI/通知服务, 找 `notification.live.` 前缀读取点; 或真机逐值试探。
2. **官方开发者文档不存在**: 开放平台文档树 8 目录全量核对, 无实况通知 API 页。魅族是否计划开放、是否仅合作制 (12306 走商务), 无公开信息。
3. **胶囊布局约束**: capsule.content.remote.view 的尺寸/复杂度上限 (demo 只试了单 TextView) 未验证; 苹果对 compact 形态有严格尺寸规范, Flyme 未公开对应约束。
4. **审核态度**: 应用商店审核规范 (docId 110) 无实况通知相关条款; extras 隐藏键方案上架是否被拦无案例。
5. **纵览联动条件**: 12306 实况通知联动 Aicy 纵览卡片, 是合作制专属还是 extras 方案可达, 未知。
6. **机型/版本铺开矩阵**: flyme.com/aios 注明"部分功能陆续上线"; 哪些机型/版本实况通知可用无官方矩阵 (已知 11.0.0 起 21 系列, 12.3.0.0A 覆盖 20/21/Lucky 08)。
7. **息屏形态**: 预热稿提"息屏流转提醒", 用户手册只录锁屏横幅; 息屏上实况通知的具体形态无文档。
8. **国际版/海外 Flyme**: 是否同能力无资料。
