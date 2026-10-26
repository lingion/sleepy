# 华为 · 桌面形态

> 证据等级: A —— developer.huawei.com 官方文档 + 消费者支持页实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/huawei.md) · [长按菜单](../long-press-menu/huawei.md) · [设计规范](../oem-design-specs/huawei.md) · [开源](../oem-open-source/huawei.md)

## ArkTS 服务卡片 (Form Kit)
- 展示位: 桌面 / 锁屏 / 待机屏保;机制: FormExtensionAbility,支持多实例、卡片编辑模板、定时刷新
- 尺寸: 微 / 小 / 中 / 大 + 圆形(锁屏/穿戴);**上架必须包含 2×2**
- 原理文档: `https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-ui-widget-working-principles-V5`
- ⚠️ 原文注意: "卡片使用方当前仅系统应用"
- 社区实操(`supportDimensions: ["2x2","2x4"]`,2026-04): `https://developer.huawei.com/consumer/cn/blog/topic/03211654901481014`

## 元服务(原原子化服务)万能卡片
- 免安装秒开,可入桌面 + 负一屏: `https://developer.huawei.com/consumer/cn/fa/`
- 消费者侧说明: `https://consumer.huawei.com/cn/support/content/zh-cn15954316/`

## 实况窗 Live View(实时形态)
- 第三方可接 Live View Kit(2026-09-14 更新): `https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/live-view-kit-guide`
- → 实时卡片域详见 [live-cards](../live-cards/INDEX.md)

## 非鸿蒙 Android app 的现实
- EMUI / HarmonyOS 4.x: 仅剩标准 AppWidget + shortcuts + 快应用
- **HarmonyOS NEXT 不兼容 APK** —— 全部桌面形态需鸿蒙原生迁移

## 缺口
- OPPO 式长按连续拖拽变形: **未找到公开资料**。
