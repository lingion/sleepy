# 实时信息卡片 (Live Cards) 厂商规范速查

> 目录目的: 把大陆主要厂商 + 苹果参照系的"实时信息卡片"体系 (华为实况窗/荣耀灵动胶囊/OPPO流体云/vivo原子通知/小米焦点通知/魅族/* 对标苹果 Dynamic Island + Live Activities) 的开发者能力边界落到本地, 作为 Sleepy 实时卡片类功能适配的事实底座。
> 抓取时间: 2026-09-17 初版 (7 agent 并行抓取) · 2026-09-18 补全 (OPPO/vivo/跨厂商 12 文件二轮落盘, 小米/Apple 收尾落盘, 术语表考证完毕)
> 抓取原则: 与一期 widget-vendor-specs 相同 — 优先厂商官方页 → 社区二次核实。每文件首行标注证据等级。
> 一期 SPA 破口实录沿用: vivo=`dev.vivo.com.cn/webapi/doc/info?id=` · 魅族=`apiopen.flyme.cn/api/web/v1/doc-wiki/detail?id=` · 荣耀=Googlebot UA · 华为 developer.huawei.com=JS 墙 (用消费者支持页/开源仓库补)

## 术语对照 (2026-09-18 考证完毕)

| 厂商 | 体系名称 | 对标苹果 | 系统版本 | 三方接入 |
|---|---|---|---|---|
| 苹果 (参照系) | Live Activities / Dynamic Island | — | iOS 16.1+ (8h 上限自 16.2) | 开放 (ActivityKit) |
| 华为 | 实况窗 Live View Kit | Live Activities | HarmonyOS NEXT 5.0+, ArkTS-only | AGC + Push Kit + 权益申请 (月活 1000 门槛[B]) |
| 荣耀 | 灵动胶囊 (YOYO 建议承载) | Dynamic Island | MagicOS 8.0+ | **无公开三方 API**, 智慧服务平台协议签署制 |
| OPPO/一加/realme | 流体云 (泛在服务) | Live Activities | ColorOS 15+ (realme UI 6.0=流体云 2.0; ColorOS 16 双兼容 Android 16 Live Updates) | 潘塔纳尔定邀企业制, fwst@oppo.com, **教育不在场景白名单** |
| vivo/iQOO | 原子通知 / 实时胶囊 | Live Activities | OriginOS 4/5+ | extras `notification.superx.*` 本地 + VPush 云端; 邮件 oosyztz@vivo.com (7-10 工作日, 须先上架 vivo 商店) |
| 小米 | 焦点通知 (HyperOS 2) → 超级岛 (HyperOS 3) | Live Activities | HyperOS 2+ (OS1 官方不建议接入) | extras `miui.focus.param` + MIPUSH; 邮件 mipush-permission@xiaomi.com |
| 魅族 | 实况通知 (社区叫法, 无官方名) | Live Activities | Flyme AIOS 11.0.0+ (2024-07) | **无官方 API**; extras 隐藏键 `notification.live.*` (社区 demo 实证, 零门槛) |

**Sleepy 优先级结论** (详见 _cross-vendor/china-live-cards-comparison.md): 魅族出 demo (1 天) → 小米正式接入 → vivo 第三站; 华为有余力再上; OPPO/荣耀挂起观察。共同长线 = Android 16 Live Updates (一次接入全 Android 16+ 生效)。

## 目录

### 文件索引 (2026-09-18 收尾版)

| 厂商 | 文件 | 内容 |
|---|---|---|
| 华为 | [huawei/live-view-kit.md](./huawei/live-view-kit.md) | 实况窗 Live View Kit: 能力/形态/API/接入条件 |
| | [huawei/live-view-terms.md](./huawei/live-view-terms.md) | 术语考证: 实况窗/流体云/通知胶囊/灵动胶囊 |
| | [huawei/live-view-access-gate.md](./huawei/live-view-access-gate.md) | 三方接入门槛与申请流程 |
| | [huawei/community-notes.md](./huawei/community-notes.md) | 社区实战要点 |
| | [huawei/live-view-gaps.md](./huawei/live-view-gaps.md) | 缺口 |
| 荣耀 | [honor/capsule-overview.md](./honor/capsule-overview.md) | 灵动胶囊能力总览 |
| | [honor/capsule-api.md](./honor/capsule-api.md) | API/接入文档 |
| | [honor/capsule-access-gate.md](./honor/capsule-access-gate.md) | 接入门槛 |
| | [honor/smart-notification-cards.md](./honor/smart-notification-cards.md) | YOYO 建议/智慧通知卡片 |
| | [honor/community-notes.md](./honor/community-notes.md) | 社区要点 |
| | [honor/gaps.md](./honor/gaps.md) | 缺口 |
| OPPO | [oppo/fluid-cloud-overview.md](./oppo/fluid-cloud-overview.md) | 流体云能力总览 |
| | [oppo/fluid-cloud-api.md](./oppo/fluid-cloud-api.md) | API/接入文档 |
| | [oppo/fluid-cloud-access-gate.md](./oppo/fluid-cloud-access-gate.md) | 接入门槛 |
| | [oppo/realtime-notification.md](./oppo/realtime-notification.md) | ColorOS 实时通知行为 |
| | [oppo/oneplus-realme-live-cards.md](./oppo/oneplus-realme-live-cards.md) | 一加/realme 差异 |
| | [oppo/community-notes.md](./oppo/community-notes.md) | 社区要点 |
| | [oppo/gaps.md](./oppo/gaps.md) | 缺口 |
| vivo | [vivo/atomic-notification.md](./vivo/atomic-notification.md) | 原子通知全量 |
| | [vivo/bluelm-cards.md](./vivo/bluelm-cards.md) | 蓝心智能卡片 |
| | [vivo/realtime-capsule-api.md](./vivo/realtime-capsule-api.md) | 实时胶囊 API |
| | [vivo/notification-behavior.md](./vivo/notification-behavior.md) | 通知体系行为 |
| | [vivo/access-gate.md](./vivo/access-gate.md) | 接入门槛 |
| | [vivo/community-notes.md](./vivo/community-notes.md) | 社区要点 |
| | [vivo/gaps.md](./vivo/gaps.md) | 缺口 |
| 小米 | [xiaomi/focus-notification.md](./xiaomi/focus-notification.md) | 焦点通知全量 |
| | [xiaomi/hyperos-interconnect.md](./xiaomi/hyperos-interconnect.md) | 跨端互联实时卡片 |
| | [xiaomi/notification-behavior.md](./xiaomi/notification-behavior.md) | 通知体系行为 |
| | [xiaomi/access-gate.md](./xiaomi/access-gate.md) | 接入门槛 |
| | [xiaomi/community-notes.md](./xiaomi/community-notes.md) | 社区要点 |
| | [xiaomi/gaps.md](./xiaomi/gaps.md) | 缺口 |
| 魅族 | [meizu/live-cards-overview.md](./meizu/live-cards-overview.md) | 实况通知总览: 形态(状态栏胶囊+悬浮展开+锁屏横幅)/版本/术语(灵动环=闪光灯灯效,非灵动岛) |
| | [meizu/live-notification-api.md](./meizu/live-notification-api.md) | extras 隐藏键全表 (notification.live.*) — demo 逐行实证, 三枚举未定 |
| | [meizu/aicy-live-cards.md](./meizu/aicy-live-cards.md) | Aicy 纵览实时卡片 + 智慧插件 + 12306 三场景联动先例 |
| | [meizu/notification-behavior.md](./meizu/notification-behavior.md) | 通知体系: 常驻通知/四级提醒模式/收纳箱/保活 |
| | [meizu/flyme-link-cards.md](./meizu/flyme-link-cards.md) | Flyme Link 互联实时卡片 (纯商务制, 无自助入口) |
| | [meizu/community-notes.md](./meizu/community-notes.md) | 唯一开源 demo 仓库 + 媒体时间线 + 社区生态现状 |
| | [meizu/gaps.md](./meizu/gaps.md) | 8 条缺口 (枚举值/审核态度/纵览联动条件等) |
| 苹果参照 | [apple/live-activities-official.md](./apple/live-activities-official.md) | ActivityKit 官方 API 全量 |
| | [apple/live-activities-hig.md](./apple/live-activities-hig.md) | HIG 设计规范 |
| | [apple/dynamic-island-forms.md](./apple/dynamic-island-forms.md) | 灵动岛四形态布局规格 |
| | [apple/push-update-chain.md](./apple/push-update-chain.md) | APNs push 更新链路 |
| | [apple/gaps.md](./apple/gaps.md) | 缺口 |
| 跨厂商 | [_cross-vendor/china-live-cards-comparison.md](./_cross-vendor/china-live-cards-comparison.md) | 六厂商横评表 |
| | [_cross-vendor/opensource-replicas.md](./_cross-vendor/opensource-replicas.md) | 开源灵动岛复刻项目清单 |
| | [_cross-vendor/community-design-notes.md](./_cross-vendor/community-design-notes.md) | 社区设计方案要点 |
| | [_cross-vendor/gaps.md](./_cross-vendor/gaps.md) | 缺口 |

## 证据等级约定 (与一期一致)

- **A**: 厂商 developer.* 官方原文落盘
- **B**: 厂商域名搜索摘要 + 社区二次核实
- **C**: 仅社区博客/媒体, 需后续二次确认
