[evidence=B] 抓取 2026-09-13 · 源URL: https://en.wikipedia.org/wiki/OxygenOS (厂商域名摘要+社区核实) + https://zh.wikipedia.org/wiki/ColorOS + https://www.realme.com/in/realme-ui-7 (厂商官网原文)

# OnePlus OxygenOS / realme realmeUI 与 ColorOS 的差异

## 系统底座对应关系

### ColorOS 版本 → Android(中文维基 + Grokipedia 一致, evidence=B)

| ColorOS | Android | 首发 |
|---|---|---|
| 1.0 | 4.1/4.2 | 2013-09 |
| 2.0 | 4.4 | 2014-03 |
| 3.0 | 5.x/6.x(维基两源有 6.0/7.1 出入, 按维基条目 3.0 于 2016 发布) | 2016 |
| 5.0 | 8.1 | 2018-03 |
| 6.0 | 9.0 | 2019-03 |
| 7.0 | 10 | 2019-11 |
| 11.0 | 11 | 2020-09 (跳过 8/9/10 与 Android 对齐) |
| 12.0 | 12 | 2021-09 |
| 13.0 | 13 | 2022-08 |
| 14.0 | 14 | 2023-11 |
| 15.0 | 15 | 2024-10 |
| 16.0 | 16 | 2025-10 |

自 ColorOS 11 起版本号与 Android 直接对齐; ColorOS 11 之前编号不同步。

### OxygenOS 版本 → Android(英文维基版本表, evidence=B)

| OxygenOS | Android |
|---|---|
| 1.0 | 5.0.1 (OnePlus One, flashable ZIP) |
| 4.0.0 | 7 Nougat (2016-12-31) |
| 5.0.3 | 8 Oreo (2018-01-31) |
| 9.0 | 9 Pie (OnePlus 6T) |
| 10.0 | 10 (2019-09-21) |
| 11.x | 11 |
| 12.0/12.1 | 12 |
| 13.0 | 13 |
| 14.0 | 14 |
| 15.0 | 15 |
| 16.0 | 16 (OnePlus 15/15R) |

### realme UI 底座

realme UI 基于 ColorOS 设计(中文维基原文: "Realme 其手机及智能设备搭载的 realme UI 也是基于 ColorOS 设计")。realme UI 1.0 基于 ColorOS 7 + Android 10(realme 官方社区帖); realme UI 7.0 基于 Android 16(realme 官网 realme-ui-7 页面为 GT 8 Pro 首发 UI 7.0)。

## 合并史(英文维基 OxygenOS History, evidence=B)

- 2016-09: XDA 采访披露 OnePlus "actively merging both platforms (OxygenOS and HydrogenOS) into a single cohesive operating system based on Android"。
- 2021-07: OnePlus 将 OxygenOS 与 OPPO ColorOS 合并 — 两家软件保持独立并服务各自区域(OxygenOS 面向 OnePlus 全球, ColorOS 面向中国区 OnePlus 与 OPPO 设备), 但共享共同代码库; OnePlus 称此举是标准化软件体验、简化后续 OxygenOS 更新的开发流程。
- 2026-07: OPPO 宣布美国与欧洲的既有 OnePlus 设备将从 OxygenOS 切换到 ColorOS 接收后续系统更新; 软件更新与售后支持继续, 用户可回滚到 OxygenOS。
- 2026-07 同期社区/媒体报道: realme 亦确认将弃用 realme UI 改用 ColorOS(社区 + 媒体 threads/facebook 转述, evidence=C 侧, 待厂商原文二次确认)。

## 各自特色行为(对第三方 App/widget 的含义)

### OxygenOS 侧

- 同底座下行为与 ColorOS 基本一致; 社区对比(Reddit r/oneplus 等, evidence=C)的共识: OxygenOS 略轻(更少预装/更少侵入), ColorOS 生态集成更深。此为社区观感, 非官方行为断言。
- OxygenOS 15 官方宣传点(oneplus.com/global/oxygenos15, evidence=B): 系统 storage 相比 OxygenOS 14 在 OnePlus 13 上减少 20%+。

### realme UI 侧(realme.com/in/realme-ui-7 官网原文, evidence=A 页面为厂商官网)

- realme UI 7.0 设计语言: "material design inspired by the interplay of light and shadow"; ICE CUBE ICONS(高透明度+浅景深图标); MISTY GLASS CONTROL CENTER(控制中心磨砂玻璃背景); BREATHING DOCK(dock 布局优化)。
- 多任务: 侧边栏最多挂 12 个 app; 长按后台运行; 双高负载应用同时跑 1 小时; 游戏可不保持前台更新。
- 生态互联: Call and Message Sharing(与 iPhone 互通来电/短信/通知)、Touch to Share、Screen Mirroring 到 Mac、Clipboard Sharing。
- realme UI 无独立开发者门户可抓(developer.realme.com 返回 error 1016/530, 走 CDN 阻断); realme 的开发者文档实际归入 OPPO 开放平台体系(open.oppomobile.com)。

## 对 Sleepy 的落点

1. 三家同底座 → ColorOS 的 AppWidget 行为(桌面挂件、Glance 冻结史、pin 行为)在 OnePlus 与 realme 机器上按同底座推断; 但各家桌面 launcher 是独立 APK, cell 尺寸/添加动线仍可能有差异, 真机验证仍需逐家做。
2. OPPO 开放平台的服务(软件商店审核、推送、账号)面向三家共用; 包名规范(.nearme.gamecenter 例外)同样适用。
3. 欧加系合并后(2026 起)文档入口只会更向 open.oppomobile.com 收敛; realme UI 专属开发者文档不可依赖。
