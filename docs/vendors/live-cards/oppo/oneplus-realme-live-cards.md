[evidence=B] https://www.coloros.com/article/A00000075/ · https://www.coloros.com/version/coloros16/ · https://www.realme.com/cn/support/kw/doc/2194587 (搜索快照) · https://www.realme.com/cn/support/kw/doc/2193096 (搜索快照) · https://news.qq.com/rain/a/20250818A065CE00 · https://zh.wikipedia.org/wiki/定制Android固件列表 · https://www.ithome.com/0/890/345.htm 抓取时间 2026-09-18

# OPPO · oneplus-realme-live-cards.md

> 证据等级说明: A=官方落盘原文 / B=官方域名搜索快照或主流媒体报道 / C=社区博客需二次确认。本文件一加/realme 侧**无 A 级开发者文档**——两家子品牌均未提供独立的实时卡片接入文档,实时卡片能力随系统整体继承。

## 一、结论先行

一加(中国)与 realme 的实时卡片 = **同一个 ColorOS 流体云内核**。一加 2021 年后中国区放弃氢OS 回归 ColorOS;realme UI 是 ColorOS 的子品牌换皮。流体云**不是按品牌裁剪的能力包,而是按系统大版本走**——ColorOS 15 ↔ realme UI 6.0,ColorOS 16 ↔ realme UI 7.0。开发者侧**没有任何子品牌专属接入通道**,三方接入统一走 OPPO 开放平台潘塔纳尔体系(见 [fluid-cloud-access-gate.md](./fluid-cloud-access-gate.md))。

## 二、系统版本沿革

### 2.1 一加: 氢OS → ColorOS (中国区)

| 时间 | 事件 | 证据 |
|---|---|---|
| 2015-2021 | 中国区氢OS (HydrogenOS),海外 OxygenOS,同一内核两套皮肤 | Wikipedia 定制Android固件列表 (B) |
| 2021-09-20 | 一加宣布 OxygenOS 13 起与 ColorOS 合并为一个系统(2022-02 调整为共享代码库、保留双品牌) | Wikipedia (B) |
| 氢OS 终态 | 基于Android 11,此后不再独立演进 | Wikipedia (B) |
| 现在 | 一加中国官网导航并列 "ColorOS / HydrogenOS(遗留支持)",新机出厂 ColorOS | oneplus.com/cn/hydrogenos (B) |

含义: **"一加实时卡片差异" 不是一个技术命题**——一加中国新机就是 ColorOS,流体云版本与 OPPO 同步推送(例: 2025-08-17 流体云接入铁路12306,覆盖一加 13T、一加 Ace5 至尊版;ColorOS 16 首批内测含一加机型)。

### 2.2 realme: realme UI = ColorOS 换皮

| realme UI 版本 | 对应 ColorOS | 基础 Android | 流体云状态 |
|---|---|---|---|
| realme UI 5.0 | ColorOS 15 同代 | 15 | 泛在服务-流体云上线(官方社区"玩机技巧"帖) |
| realme UI 6.0 | ColorOS 15.x | 15 | **流体云 2.0**(realme 官方支持页 doc 2194587) |
| realme UI 7.0 | ColorOS 16 同代 | 媒体口径 15/16 不一(见 gaps) | 2025-11-04 发布,首发 GT8 Pro、14 Pro+ 等 |

## 三、流体云在子品牌上的功能面(用户侧)

### 3.1 realme UI 6.0 流体云 2.0 官方支持页要点 (doc 2194587, B 级快照)

- 系统类支持: **手电筒、音频播放器、生物识别验证(锁屏/应用锁/关机验证)、通话、热点、计时器、录音、录屏、投屏、跨屏互联**。
- 音乐流体云(doc 2193096): 后台播放媒体时状态栏以流体云显示曲目,点击弹出音乐控制面板(播放/暂停/下一曲)。
- 交互(官方社区玩机技巧帖, realme UI 5.0): ① 卡片式: 点击进应用,上滑/左滑/右滑收起,长按管理服务;② 状态栏胶囊式。

### 3.2 一加侧(ColorOS 通用行为)

- 开关路径: 设置 ➜ 通知与控制中心 ➜ 流体云,**按应用逐个开启**(一加社区《ColorOS 15 用机指南》帖)。
- 三方履约类覆盖: 航班行程、网约车、外卖等(ColorOS 官网 A 级原文,见 [fluid-cloud-overview.md](./fluid-cloud-overview.md))。

### 3.3 差异实测清单(未证实项全部记入 [gaps.md](./gaps.md))

| 疑似差异点 | 状态 |
|---|---|
| realme UI 流体云支持的三方 app 数量少于 OPPO 同期 | ⚠️ 未证实,无官方对照表 |
| 一加 Ace 系列流体云动效/形态与 Find 系列差异 | ⚠️ 未证实 |
| realme UI 升级节奏滞后 OPPO 同代 ColorOS 数月 | ⏱ realme UI 6.0(2024-11 内测)vs ColorOS 15(2024-10 发布)约滞后 1 个月,个案非规则 |

## 四、开发者接入: 无子品牌通道

| 通道 | 一加 | realme |
|---|---|---|
| 独立开发者平台实时卡片文档 | ❌ 无 | ❌ 无(dev.realme.com 文档树未含流体云/泛在服务,本 session 未逐页核实,记 gaps) |
| 实际归属 | OPPO 开放平台潘塔纳尔服务库 | 同左 |
| 商务入口 | `fwst@oppo.com`(doc 12715) | 同左 |
| ColorOS 16 起的 Live Updates 兼容 | 遵循 Android 16 原生 Live Updates API 即可直接适配流体云(IT之家 2025-10-17, B) | 待 realme UI 7.0 实测(记 gaps) |

## 五、Sleepy 落点

| 判断 | 说明 |
|---|---|
| 一加/realme 不需要单独适配 | 只要过了 OPPO 潘塔纳尔准入,子品牌机型随 ColorOS 版本自然覆盖 |
| 真正的机会在 ColorOS 16 Live Updates 路线 | 若 Sleepy 按 Android 16 原生 Live Updates(实时进度通知)规范开发,ColorOS 16+ 的一加/OPPO/未来 realme UI 7.0 机型**可能绕开潘塔纳尔商务制**——此为 B 级媒体口径,需等官方开发文档落盘验证 |
| 现实选择 | 与 OPPO 主品牌一致: 短期走 AppWidget(一期兼容层已覆盖),中期盯 Live Updates API |
