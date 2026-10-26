# 负一屏 (Minus-One Screen) 全球厂商接入门槛速查

> 目录目的: 只回答一件事 —— **第三方 app 怎样才能把内容放上各家负一屏**。接入路径 / 载体形态 / 审核门槛 / 商务条件 / 自助程度。全球没有公开私有协议的厂商统一走标准 Android AppWidget；完整全球矩阵见 [global-vendor-matrix.md](./global-vendor-matrix.md)。
> 抓取时间: 2026-09-17 (3 research agent 并行实测官方开发者平台: 华为+荣耀 / OPPO+vivo / 小米+魅族)
> 抓取原则: 厂商官方页优先(JS 渲染站实测读正文)→ 社区二次核实;每文件首行标注证据等级;查无公开资料的显式记录,不编造 URL。
> 相邻域(内容互不重复): [长按菜单](../long-press-menu/INDEX.md) · [桌面形态与变形](../desktop-morph/INDEX.md) · [设计规范](../oem-design-specs/INDEX.md) · [开源与社区](../oem-open-source/INDEX.md) · [实时信息卡片](../live-cards/INDEX.md) · [AppWidget 启动器兼容](../widget-vendor-specs/INDEX.md)

## 一页矩阵

| 厂商 | 负一屏名称 | 第三方能否自助 | 载体形态 | 门槛 |
|---|---|---|---|---|
| **华为** | 智慧助手·今天 / 服务分发增长平台 | ✅ 自助 | 元服务卡片 / 快应用 / AGC 服务直达卡 | 需鸿蒙原生;AGC 审批约 1h |
| **荣耀** | YOYO 建议 + 智慧服务 | ❌ 商务白名单 | 快捷服务图标 / 快应用 JS 卡 / 模板卡 / 安卓 widget / 内容接口 | 邮件 BD(Developers_BD@honor.com) |
| **OPPO** | 负一屏(泛在服务 Pantanal) | ❌ 商务合作制 | UPK(JS+CSS+OML) / 标准 AppWidget 插件位 | 认证开发者 + 商务授权码(需 PRD) |
| **vivo** | 智慧桌面(原子组件) | ✅ 全自助 | 原子组件(= AppWidget + 3 meta-data) | 上架 vivo 商店 + 邮件申请 + 平台审核 |
| **小米** | 负一屏(appvault) | ✅ 自主接入 | MIUI 小部件 / maml 模板卡 | 邮件报备 + widget.xiaomi.com 审核;推荐位=付费 |
| **魅族** | Aicy | ❌ 无任何公开渠道 | — | 无门 |

**接入难度排序(易→难)**: vivo ≈ 小米 < 华为 < 荣耀 < OPPO ≪ 魅族

## 文件索引

| 厂商 | 文件 |
|---|---|
| 华为 | [huawei.md](./huawei.md) |
| 荣耀 | [honor.md](./honor.md) |
| OPPO | [oppo.md](./oppo.md) |
| vivo | [vivo.md](./vivo.md) |
| 小米 | [xiaomi.md](./xiaomi.md) |
| 魅族 | [meizu.md](./meizu.md) |

## Sleepy 代码侧支持边界（APK 内已完成）

- **全部厂商**：同一套标准 Android AppWidget，13 个课表变体、2x2/4x2/4x4 三档尺寸、可调尺寸、预览图、无配置白屏、无数据引导页、深色主题资源和曝光/系统刷新后的标准重绘。
- **vivo**：13 个 receiver 均声明原子组件三件套，继续走标准 AppWidget 渲染；原子组件平台审核和商店上架不由 APK 单方面完成。
- **小米**：13 个 receiver 均声明 `miuiWidget` 曝光刷新字段并接收 `miui.appwidget.action.APPWIDGET_UPDATE`；初始布局使用 Xiaomi 兼容 background 根节点。独立进程和小米 Widget 商店审核暂不伪造，避免破坏 Room/渲染初始化。
- **华为 / 荣耀 / OPPO / 魅族 / 三星**：没有可由 Android APK 单方面替代的公开负一屏私有卡片协议，使用标准 AppWidget 兼容路径；原生服务卡、商务白名单、UPK/主题生态或推荐位需另行平台准入。

## 缺口账本(本域)
- 荣耀/OPPO: 商务准入的实际通过率、周期、量级门槛无公开数据,只能邮件实测。
- OPPO: 《卡片接入指南》id=11981 实测"无权限查看"= 白名单实锤,需工单/商务开权限。
- 小米: 小爱建议推荐位除采买外无免费通道。
- 魅族: 开放平台全目录无负一屏/Aicy/卡片接入项(2025-09 实测),持续关注。
