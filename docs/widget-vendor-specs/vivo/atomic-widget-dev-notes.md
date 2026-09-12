[evidence=B] 整理 2026-09-12 · 依据: requestPinAppWidget 渠道矩阵 (CSDN 147627142, 社区 2025-05 实测) + vivo 开放平台原子组件文档中心 (dev.vivo.com.cn/documentCenter/doc/845, 登录墙仅存目) + 本地 desktop-widget-painpoints.md (vivo 渲染实测)。

# vivo OriginOS — Android AppWidget 开发者行为笔记

## 定性: 标准 AppWidget 可用, "原子组件"是叠加生态

OriginOS 桌面同时存在两条通道:
1. **标准 AppWidget** (应用挂件): Android 原生体系, 无需 vivo 审核, 直接可用。
2. **原子组件** (磁贴式): vivo 专有, 需接入 vivo 原子组件 SDK + 上架组件平台 +
   通过 vivo 审核才生效。两者 UI 形态相似但协议不互通。

Sleepy 走通道 1, 不做通道 2 伪装 (平台审核项, 代码层无法单方面实现)。

## requestPinAppWidget 渠道矩阵 (vivo 行 — 最严)

| 行为 | vivo/iQOO |
|---|---|
| 弹系统确认框 | **否** (静默) |
| 空间不足自动开新页 | 是 |
| 无原子组件 SDK + 未上架时调用效果 | **无任何效果** — 不能主动添加到桌面 |

Sleepy 对策: vivo 上 pin 通道 (`PinWidgetActivity` → `requestPinAppWidget`) 可能
静默无效, 用户兜底路径 = 桌面长按 → 添加挂件 → 找到 Sleepy。应用内"添加小组件"
入口在 vivo 上必须保留"手动添加"引导文案, 不承诺一键添加。

## 渲染实测坑 (引 desktop-widget-painpoints.md)

- RemoteViews 总 bitmap 内存上限 = 1.5× 屏幕内存
  (`6 × screenW × screenH` 字节, AOSP `AppWidgetServiceImpl` 硬校验, vivo 严格抛
  `IllegalArgumentException: RemoteViews for widget update exceeds maximum bitmap
  memory usage`)。整张长图方案在 vivo 必须按此预算。
- GIF/多帧位图: 单帧与总量双阈值, 超限静默截断 (Sleepy 无 GIF, 不适用)。
- 挂件内存/GIF 性能受限 (OriginOS 对挂件进程有更紧的内存压力)。

## OriginOS 版本注意

- OriginOS 4/5 的大图标模式与"变形器"会改变 cell 实际 dp, 同样依赖
  options 读取而非 targetCell 精确值 (Sleepy 已覆盖)。
- vivo 报过的冻结行为见 `desktop-widget-painpoints.md` 原文; Sleepy 全同步推送
  (goAsync + updateAppWidget) 不依赖异步 Session, 与 OPPO 冻结对策同构。

## 与 Sleepy 公共层的关系

公共层规则全部适用; vivo 专属动作 = 0 (原子组件明确不做)。
风险最高项 = bitmap 内存预算, 已由"渲染按真实 dp + 条带横切"覆盖
(`WidgetBitmapLifecycleTest` 锁)。
