[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.samsung.com/one-ui/structure/basic-structure.html (+ https://www.samsung.com.cn/one-ui/features/ One UI 7 官方消费者页 + https://www.samsung.com.cn/support/mobile-devices/changes-to-the-home-screen-on-the-samsung-galaxy-devices/ 官方支持页 · 均为 samsung.com/developer.samsung.com 原文)

# 三星 One UI 桌面 (One UI Home) — 启动器行为与网格

> 已有 samsung/appwidget-dev-notes.md 记录 AppWidget pin 矩阵; 本文件为桌面网格/标签/横屏/结构的官方规范 (evidence=A), 补齐桌面行为事实。

## 一、桌面结构 (structure/basic-structure.html 官方原文)

- Lock screen: 防误触 + 生物识别保护; 未解锁即可查看简单信息与访问基础功能。
- **Home screen: 用户可移动应用自定义环境; 小组件 (Widgets) 可添加到主屏, 不进应用即可快速获取信息**; 主屏与 Apps 屏之间上下滑动切换 (原文: "Users can switch between the Home and Apps screen with a quick swipe up or down")。
- Recents: 按最近使用顺序显示缩小版应用; 点应用上方图标切分屏/pop-up 视图。
- Quick panel: 顶部 Conversations 区 (消息类通知) + 下方一般通知; 功能开关默认常用项, 可增删排序。
- Edge panel: 从 Apps edge panel 拖应用放屏幕上即创建 pop-up 或分屏视图。

## 二、One UI 7 桌面网格 (samsung.com.cn/one-ui/features/ 官方消费者页)

- **主屏简化网格布局 (One UI 7 新增)**: 新的标准网格布局实现整体对称美感, 增强 One UI 标准尺寸小组件的便利性。
- **主屏横屏显示升级**: 横屏下主屏视觉统一; 小组件在横屏模式宽高比不一致的问题已解决; 图标文字标签从「侧边显示」改为「图标下方显示」。
- **应用与小组件个性化**: 可调应用图标大小; 可选图标下方是否显示文字标签; 精选小组件标题可选显示; **每个小组件的设置选项中可调整小组件的形状、背景颜色以及透明度**。
- **小组件单独显示日历**: 可控制桌面小组件中显示的日历内容 (选 1 个日历或创建 2 个独立日历小组件分别呈现)。
- **倒计时小组件**: 事件详情页「更多选项」→「添加倒计时小组件」直接生成桌面小组件。
- 通知图标与主屏应用图标完全一致 (One UI 7 提升辨识度)。

## 三、One UI 7 主屏变化官方支持页 (samsung.com.cn 支持文档)

- **网格布局变化 (原文)**: Galaxy 设备主屏网格已优化; **运行 One UI 7 的设备上, 4x5、5x5 网格排列不再在默认选择中可用**。
- **横屏网格重定位 (原文)**: 应用和小组件在纵向/横向模式下外观一致; 横视图中应用自动重新对齐 (从占最多空间的应用开始、从左角开始); **网格按布局重定位 (例如 4x6 布局在横向视图中调整为 6x4)**。
- **横屏编辑受限**: 为在横/竖保持相同布局格式, 横视图中不支持移动或删除应用图标和小组件。
- **小组件标签 (One UI 7 新增, 原文)**: 之前版本小组件无标签导致与并排应用图标不对齐; 新版引入小组件标签; **添加标签不适用于来自第三方应用的小组件** (为防标签被切断); **标签占用空间会降低小组件整体高度, 有时限制显示内容**; 必须打开应用标签才能启用小组件标签。
- **推荐小组件列表改进**: 可一次预览所选小组件的各种格式, 更易选择适配当前布局的格式; 拖动滑块更改应用图标大小 (小/中/大)。
- 通知与快速设置面板可从主屏一次滑动分别访问。

## 四、对 AppWidget 渲染的影响 (事实归纳)

1. **网格密度**: One UI 7 起默认网格从 4x5/5x5 移除, 以标准网格 + 4x6/6x4 (横屏) 为主; cell 实际 dp 随网格模式与图标大小滑块变化 — AppWidget 必须依赖 `OPTION_APPWIDGET_SIZES` 运行时读真实尺寸, 禁依赖 minWidth 精确值 (与 appwidget-dev-notes.md 结论一致, 此处补官方出处)。
2. **标签占高**: 三方应用小组件不自动加标签, 但系统小组件标签机制表明标签条带会占用 widget 高度预算; Sleepy widget 无标签, 高度预算为完整 cell。
3. **形状/背景/透明度可调**: 用户可在小组件设置中调整形状与透明度 — widget bitmap 背景需容忍系统级透明度/圆角处理 (与 OPPO 冻结风险的差异: 三星在消费者设置层, 非渲染层冻结)。
4. **横屏重定位**: 4x6 ↔ 6x4 网格重定位 + 横屏编辑受限 — 横竖屏切换时 widget 占格数可能变化, options 回调覆盖。
5. 桌面行为 = AOSP 基线 + 网格/标签层魔改, 无厂商 SDK; requestPinAppWidget 弹框 + 空间不足自动开新页 (见 appwidget-dev-notes.md 矩阵)。

## 原文页面清单 (全部 evidence=A, 2026-09-13 抓取)

| 页面 | URL |
|---|---|
| Structure - Basic structure (Lock/Home/Recents/Quick panel/Edge panel) | https://developer.samsung.com/one-ui/structure/basic-structure.html |
| One UI 7 特性页 (主屏简化网格/横屏/小组件个性化) | https://www.samsung.com.cn/one-ui/features/ |
| One UI 7 主屏变化支持页 (4x5/5x5 移除/标签/横屏重定位) | https://www.samsung.com.cn/support/mobile-devices/changes-to-the-home-screen-on-the-samsung-galaxy-devices/ |
