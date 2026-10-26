# 华为 · 负一屏

> 证据等级: A —— developer.huawei.com / consumer.huawei.com 官方文档实测(2026-09-17);JS 墙站点用消费者支持页与官方博客补。
> 其他能力: [长按菜单](../long-press-menu/huawei.md) · [桌面形态](../desktop-morph/huawei.md) · [设计规范](../oem-design-specs/huawei.md) · [开源](../oem-open-source/huawei.md)

## 体系
- 负一屏 = 「智慧助手·今天」,现包装为**服务分发增长平台**:以负一屏为中心,依托小艺、全局搜索、主题、元服务生态流量。官方入口: `https://developer.huawei.com/consumer/cn/assistanttoday/`

## 自助接入四步
1. 元服务 / 应用 / 快应用 接入准备
2. AGC「服务分发 > 服务直达」上传卡片(子服务卡 / 商品卡 / 门店卡)
3. 审批上架(**约 1 小时**)
4. 探索流 / 卡片中心 / 搜索 / 小艺 露出
- 文档: `https://developer.huawei.com/consumer/cn/doc/service/harmonyos-service-0000001238266717/`

## 版本与前提
- HarmonyOS NEXT 负一屏"卡片"专区内置卡片市场;加负一屏需 HarmonyOS 5.0.0.135 SP1+ 且智慧助手·今天 15.0.12.300+: `https://consumer.huawei.com/cn/support/content/zh-cn16038618/`
- EMUI 残留: 负一屏"服务"页精选**合作制**,未装 app 拉起快应用,无自服务 API: `https://consumer.huawei.com/cn/support/content/zh-cn15968525/`
- **NEXT 不兼容 APK** —— 负一屏卡片形态需鸿蒙原生(元服务/ArkTS 卡片),Android APK 无形态。

## AI 化动向
- 小艺开放平台 Agent / Skill / MCP 接入(2026-07 更新),Today-Task Skill 动态接入。

## 缺口
- 服务直达卡的曝光权重、审核驳回原因无公开资料。
