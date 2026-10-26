# 华为 · 长按菜单

> 证据等级: A —— developer.huawei.com 官方文档实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/huawei.md) · [桌面形态](../desktop-morph/huawei.md) · [设计规范](../oem-design-specs/huawei.md) · [开源](../oem-open-source/huawei.md)

## 图标长按
- 标准 shortcuts(静态)+「服务卡片」按钮
- NEXT 配置文档: `https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/typical-scenario-configuration`
- 桌面快捷方式设计规范(2026-09-02,含"长按 → 全网搜索拖至桌面"交互): `https://developer.huawei.com/consumer/cn/doc/design-guides/shortcat-0000002550987962`

## 唯一官方扩展位: 服务卡片长按菜单
- 两层:
  1. **系统级添加菜单** —— 配置后不可改
  2. **应用自定义卡片菜单** —— 支持编辑 / 排序 / 更改内容 ← 第三方真正的扩展点
- 设计规范(2026-02-10): `https://developer.huawei.com/consumer/cn/doc/design-guides/system-features-service-widget-0000002087671904`
- 前提: 需鸿蒙原生 ArkTS 服务卡片(见 [桌面形态](../desktop-morph/huawei.md));Android APK 在 NEXT 无形态

## 缺口
- shortcuts 数量上限 / 菜单分组规则: **未找到公开资料**。
