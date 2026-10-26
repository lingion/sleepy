# 小米 · 桌面形态

> 证据等级: A —— dev.mi.com 澎湃OS开发者平台逐页实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/xiaomi.md) · [长按菜单](../long-press-menu/xiaomi.md) · [设计规范](../oem-design-specs/xiaomi.md) · [开源](../oem-open-source/xiaomi.md)

## MIUI 小部件(标准 AppWidget 超集)
- 《小部件技术规范与系统能力说明》: `https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584`
- 原文: "小米小部件基于原生 Android Widget,开发一个小米小部件和开发一个原生 Android Widget 基本一致"
- 技术要求:
  - 独立进程 `:widgetProvider`(内存 ≤35 MB)
  - `miuiWidget=true` 标识
  - 曝光刷新 meta: `miuiWidgetRefresh` / `miuiWidgetRefreshMinInterval`,最短 10 s
  - action: `miui.appwidget.action.APPWIDGET_UPDATE`
  - 尺寸: 2×2 / 4×2 / 4×4
- 版本表: v1.0.0(2021-04) ~ v1.0.7(2022-08)
- **接入**: 邮件 `miui-widget@xiaomi.com` 报备 + 小部件开放平台 `https://widget.xiaomi.com/` 审核上架(《提交审核与上传操作指南》pId=1588)
- **纯原生 AppWidget 无需小米审核**,HyperOS 桌面经"搜索 — 安卓小部件"添加(pId=1664)
- 无任意拖拽变尺寸公开说明

## 焦点通知 → 超级岛(实时形态)
- 《小米超级岛·业务介绍》(2026-01-29): `https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2140`
- "技术架构 & 产品方案在焦点通知基础上升级";HyperOS 2 时期焦点通知仅 20 余合作应用
- HyperOS 3 超级岛开放**方案提报制**,准入"服务生命周期 ≤12h、禁营销类"
- 配套: 开发指南 pId=2131、接入流程 pId=2132、摘要/展开态设计规范 pId=2143/2142
- 展示位: 桌面置顶 / 息屏 / 锁屏 / 通知栏;下拉小窗、拖拽分享
- 白名单反证: LSPosed 模块 `L-aros/HyperFocus` 专门移除焦点通知白名单限制(→ [开源](../oem-open-source/xiaomi.md))
- → 实时卡片域详见 [live-cards](../live-cards/INDEX.md)

## 大图标
- MIUI 14 大图标 = 系统级换肤能力,**无第三方适配文档**

## 小窗 / 分屏
- 基于 Android Freeform,适配即 Google 多窗口规范(`resizeableActivity`)
- 《全局自由窗口适配说明》pId=1593、分屏 pId=1592

## 缺口
- "超级小卡"无官方资料(疑与其他厂商混淆)。
