[evidence=B] 整理 2026-09-12 · 依据: requestPinAppWidget 渠道矩阵 (CSDN 147627142, 社区 2025-05 实测) + One UI 官方消费者页 (samsung.com) + Android 官方 AppWidget 文档。One UI 无公开的 AppWidget 专属开发者规范页。

# 三星 One UI — Android AppWidget 开发者行为笔记

## 定性: 无厂商 SDK, 全走 Android 原生

One UI 桌面 (One UI Home) 的 AppWidget 宿主行为以 AOSP 为基线, 无额外厂商
SDK/审核通道。三星的差异化在消费者功能层 (Good Lock 主题增强、外屏小组件),
不改变 AppWidget 协议本身。

## requestPinAppWidget 渠道矩阵 (三星行)

| 行为 | 三星 |
|---|---|
| 弹系统确认框 | 是 |
| 桌面当前页空间不足时自动开新页 | **是** (与华为/荣耀相反, AOSP 默认行为) |
| 成功回调 PendingIntent | 社区实测可用 (AOSP 行为) |

Sleepy 对策: 无需特殊处理; pin 流程按 AOSP 预期工作。

## One UI 特有注意点

- **外屏小组件** (Galaxy Z Flip 系列外屏): One UI 支持在外屏摆放/操作 AppWidget,
  外屏 cell 尺寸与内屏差异大, 依赖 `OPTION_APPWIDGET_SIZES` 正确取当前尺寸
  (Sleepy `WidgetSizeCore.pickSizeDp` 方向契约已覆盖)。
- **折叠屏 cell 差异**: 同一变体在大屏/展开态占格数与手机不同, minWidth/minHeight
  是 dp 下界, 实际渲染尺寸必须读 options (Sleepy 全部走 `computeSizeDp`, 无硬编码)。
- One UI 的"大图标/网格密度"设置会改变 cell 实际 dp, 同样依赖 options 读取,
  不依赖 targetCellWidth/Height 精确值。
- Good Lock / 主题商店不介入 AppWidget 渲染, bitmap face 无被二次处理的风险。

## 与 Sleepy 公共层的关系

公共层规则全部直接适用, 三星专属动作 = 0。三星是"最接近 AOSP"的大厂启动器,
新渲染路径 (scrollable / static nav) 在三星上的回归风险最低。
