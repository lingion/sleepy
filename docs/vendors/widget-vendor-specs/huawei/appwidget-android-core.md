[evidence=B] 整理 2026-09-12 · 依据: 华为官方开发者页定性 (developer.huawei.com, JS 渲染墙仅存目) + requestPinAppWidget 渠道矩阵 (CSDN 147627142, 社区 2025-05 实测) + HarmonyOS NEXT 生态分界 (INDEX.md 已有结论, 本文件细化安卓内核分档)。

# 华为 — AppWidget 在安卓内核档位的行为与生态分界

## 三档分界 (关键: 用户说"华为鸿蒙(安卓内核)"指档位 2)

| 档位 | 系统 | 桌面卡片体系 | Android AppWidget |
|---|---|---|---|
| 1 | EMUI 1.x–10.x (Android 10 及以下内核) | 标准桌面小工具 | **完整 AOSP 行为** |
| 2 | HarmonyOS 2–4 / EMUI 11–12 (**安卓内核 + AOSP 兼容层**) | 桌面"服务卡片"/万能卡片 + 经典小工具并存 | **可用** — 标准小工具通道保留, AOSP 行为为基线 |
| 3 | HarmonyOS NEXT (纯血鸿蒙, 无安卓内核) | ArkTS 服务卡片 (Form Extension) | **不可用** — 独立生态, Android APK 侧无法接入 |

Sleepy 的 APK 装在档位 1/2 设备上 = 标准 AppWidget 通道; 档位 3 需要 HarmonyOS
原生工程 (独立仓库工作量, 明确不做, 见 INDEX.md"不在适配范围")。

## 档位 2 (安卓内核鸿蒙) 上的 AppWidget 行为

- 桌面长按 → "窗口小工具"列表 = 标准 AppWidgetProvider 枚举, 与 AOSP 一致。
- 华为桌面对 `requestPinAppWidget` 的实测行为:
  - 弹系统确认框: **是**;
  - 空间不足自动开新页: **否** (提示"当前页面空间不足", 与荣耀一致);
  - 成功回调 PendingIntent (第三参): **不支持**, 添加成功与否都不触发。
- 服务卡片 (万能卡片) 是华为自己的卡片协议, 只对上架华为市场的 HarmonyOS
  应用开放; Android APK 拿不到服务卡片形态, 桌面上呈现的就是经典小工具。

## Sleepy 对策

- 无厂商专属代码。公共层 (同步 RemoteViews、无 configure、options 读真实尺寸)
  在档位 2 全部成立。
- 华为设备用户量按 issue 渠道零星出现 (当前无华为专属 issue), 不为档位 2
  做单独优化; 通用兼容即覆盖。
- 未来若华为专属问题到达 (类似荣耀 #31), 先按"档位 2 = AOSP 基线 + 桌面魔改"
  假设排查, 再对照本文档矩阵。

## 与荣耀的关系

荣耀 MagicOS 源自华为体系, 档位 2 的行为矩阵两厂高度相似 (确认框弹/空间不足
不扩页)。荣耀实测结论 (`honor/appwidget-dev-notes.md`) 可作为华为档位 2 的
先验参考, 但华为侧未真机验证, 标注 [evidence=B] 维持。
