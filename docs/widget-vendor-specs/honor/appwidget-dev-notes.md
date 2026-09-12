[evidence=B] 整理 2026-09-12 · 依据: issue #31 荣耀真机实测 (荣耀Win RT, MagicOS 10.0.0.170, Android 16) + requestPinAppWidget 渠道矩阵 (CSDN 147627142 / 技术栈转载, 社区 2025-05 实测) + 荣耀官方消费者页 (honor.com/cn/support)

# 荣耀 MagicOS — Android AppWidget 开发者行为笔记

## 定性: 桌面小组件走 Android 原生 AppWidget

MagicOS 桌面上的"经典小组件/经典小工具"通道就是标准 AppWidget 体系
(AppWidgetProvider + appwidget-provider xml)。荣耀的"主题小组件"(荣耀主题 App
内分发)和"锁屏小组件"(MagicOS 10.0.0.150+ 独立推送)是**另外两套消费者通道**,
与 AppWidget 不互通, 不接入也能正常上桌面。Sleepy 只走 AppWidget 通道。

## #31 实锤: 添加时丢弃 configure Activity → 回滚

- 症状: 添加小组件后立即消失 (启动器收到配置"取消"结果, 回滚绑定)。
- 根因: MagicOS 启动器在添加流程中不保留外部 configure Activity 的启动链,
  `android:configure` 声明的 Activity 被丢弃 → 启动器视为用户取消。
- 修复: 全部 10 个 info XML 不声明 `android:configure`, 保留
  `widgetFeatures="reconfigurable"` + 应用内编辑 (「我的 → 通用设置 → 小组件」)。
- 契约锚点: `WidgetInfoXmlContractTest` (锁 manifest + xml 一致, 锁无 configure)。
- 状态: v1.0.53 修复后报告人确认"无消失情况"。

## requestPinAppWidget 渠道矩阵 (荣耀行)

| 行为 | 荣耀/Huawei |
|---|---|
| 弹系统确认框 | 是 |
| 桌面当前页空间不足时自动开新页 | **否** — 提示"当前页面空间不足", 放置失败 |
| 成功回调 PendingIntent (第三参) | **华为不支持**; 荣耀社区数据未证实, 视为不可靠 |

Sleepy 对策: `PinWidgetActivity` (auto-finish OK) 兜底 + 应用内引导手动添加。
空间不足是启动器行为, 代码层无解, 用户自行清桌面。

## 已知 UI 行为

- 字体放大 (系统 fontScale) 后, AppWidget 内 TextView 可能遮挡相邻控件
  (cross-vendor 通用风险, #31 报告人在 2×2 尺寸实见"字体遮盖箭头")。
  对策见 `TodayWidget.navHeaderTier` (逐档真实度量, fontScale 参与测量)。
- 添加小组件后壁纸景深效果消失 (MagicOS 官方行为, 为避免关键信息被覆盖)。
- 锁屏小组件首批 20+ 为系统应用 (步数/天气/YYO 等), 三方 App 锁屏小组件
  尚未开放公开接入通道 (MagicOS 10.0.0.150+, 2026-03 首批推送)。

## 与 Sleepy 公共层的关系

公共层规则 (INDEX.md 对照表) 对荣耀全部成立: 无 configure、同步 RemoteViews、
`ALL_WIDGET_VARIANTS` 派生、pin 路由回落。荣耀专属动作 = 0 (无厂商 SDK 要求)。
