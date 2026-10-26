[evidence=-] 整理 2026-09-18 · 本轮 Apple 官方原文未抓齐

# Apple — Live Activities 资料缺口

| # | 缺口 | 当前状态 | 补证路径 |
|---|---|---|---|
| 1 | ActivityKit 当前最低系统与 SDK 版本 | B 级通用资料记为 iOS 16.1+, 待核 | developer.apple.com ActivityKit 文档 |
| 2 | Live Activity 具体生命周期上限 | B 级资料常记 8h, 待核当前系统 | ActivityKit 生命周期文档 |
| 3 | Dynamic Island 区域尺寸、字数与同时活动规则 | N/A | Apple HIG + WidgetKit API |
| 4 | `Activity.request` / `update` / `end` 当前签名 | N/A | 最新 SDK API Reference |
| 5 | push-to-start 与活动专用 token 规则 | N/A | ActivityKit remote updates 文档 |
| 6 | APNs `content-state`、`stale-date`、`dismissal-date` 完整约束 | N/A | Live Activity push notification 文档 |
| 7 | token 轮换、失效码和重试退避 | N/A | APNs provider API 文档 |
| 8 | iPad、Apple Watch、CarPlay 的 Live Activity 支持矩阵 | N/A | Apple 平台支持矩阵 |
| 9 | App Store 审核对课程/教育提醒的具体要求 | N/A | App Review Guidelines + 实测 |
| 10 | 中文字体、RTL、动态字体下的布局限制 | N/A | HIG accessibility/typography 文档 |

## Sleepy 当前边界

Apple 目录暂时只能作为概念和数据模型参照, 不能把 B 级资料直接当实现契约。补抓官方原文后再锁定 API 签名、生命周期和推送字段。
