[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.samsung.com/one-ui/largescreen-and-foldable/intro.html (+ .../large_screen_layout.html / .../designing_for_foldable.html / .../layout/grid.html · developer.samsung.com One UI 官方设计规范原文)

# 三星 One UI 折叠屏 / 大屏布局官方规范 (Large screen & Foldable)

> One UI 设计规范的 "Large screen & Foldable" 章节共 3 页: Designing for large screens / Layout design for large screens / Designing for foldables。本文件为三页原文要点 + Grid system 页的边距/拒触区规范。

## 一、窗口尺寸分级 (Window size classes, large_screen_layout.html)

官方分级 (宽度断点, 原文表格):

| Window class | Breakpoint | 代表设备 |
|---|---|---|
| Compact | Width < 600dp | 手机竖屏 |
| Medium | 600 ≤ Width < 840dp | Z Fold 竖屏 / Z Fold 横屏 / 小平板竖屏 |
| Expanded | 840 ≤ Width | 平板竖屏 / 平板横屏 |

- 官方 "Things to check": **屏幕宽度 ≥600dp 时使用大屏布局**。
- 与 Android 官方 Window Size Classes 同构 (官方指路 developer.android.com Window size classes)。

## 二、多窗格布局 (Multi-pane, intro.html)

- 大屏用 multi-pane 展示更多信息 + 扁平化导航 (flatten navigation), 避免全屏转场。
- 官方推荐分栏比例 (原文表格):

| Breakpoint (dp) | 1st pane | 2nd pane |
|---|---|---|
| 600 ≤ Width < 960 | 42% | 58% |
| 960 ≤ Width | 38% | 62% |
| Foldable only | 50% | 50% |

- Z Fold 设备官方推荐 50:50; 拉伸铺满 (stretched-out) 的大屏应用更难读且浪费空间。
- 官方指路: Sliding pane layout / Activity Embedding (Android Developers)。

## 三、导航控件适配 (intro.html)

| Window class | Panes | Navigation |
|---|---|---|
| Compact (Width < 600) | 1 | 底部导航栏 (navigation bar) / 模态抽屉 |
| Medium (600 ≤ Width < 840) | 1 (推荐) 或 2 | 侧边 navigation rail / 模态抽屉 |
| Expanded (840 ≤ Width) | 1 或 2 (推荐) | navigation rail / 模态或标准抽屉 |

- 官方 "Things to check": Z Fold 竖屏用 navigation rail; 平板横屏用三窗格 (triple-pane)。
- 官方指路: Navigation rail / Navigation drawer (Material Design)。

## 四、大屏其他规范 (intro.html + large_screen_layout.html)

- **弹窗就近原则**: 小信息量输入用小 pop-over 而非全屏; 大屏上 pop-up 出现在触发按钮附近, 减少手指移动距离。
- **拖放**: multi-window 视图中用 drag & drop 移动/复制/附加内容 (文本/图片/网址), 提供拖起与可放位置的视觉反馈。
- **多任务**: 大屏最多 3 个分屏 + 5 个 pop-up 窗口同时开启, 可自定义 resize。
- **菜单与内容同屏**: 菜单常驻屏幕, 用户不用切屏即可导航。
- **网格随大屏调整**: "Adjust your app's grid structure for large screens so more information can be shown at once" — 应用网格结构要随大屏调整, 一次展示更多信息。
- **可达性 (One UI 相机示例)**: 控件按握持姿势放置 (如相机控件放右侧而非底部)。
- **避免切换上下文**: 简短任务用小 pop-up, 保留前一屏。

## 五、折叠屏规范 (designing_for_foldable.html)

- Galaxy Fold 2019 发布 (首款 One UI 折叠屏); Z Fold / Z Flip 双屏 (cover screen 合上可见, main screen 展开可见)。
- **App continuity (连续性) 是硬要求**:
  - 展开时应用应出现在 main screen 上, 停在用户离开的位置。
  - 官方 "Things to check": 切换屏幕时应用应填满整个屏幕; 滚动内容切换屏幕时保持滚动位置; 切换前在输入文字的, 输入框和键盘应保留。
- **Responsive layout 是硬要求**:
  - 官方 "Things to check": 各种尺寸/宽度/宽高比的屏幕显示正确; 支持横屏; cover screen 与 main screen 都显示正确; **填满整屏不出现 letterboxing (黑边)**。
  - 官方 checklist 三项: ① Apps should be resizable ② Apps should support landscape ③ Apps should provide a large screen layout。
- **Flex mode (半折)**:
  - 内容显示在上半屏 (倾斜朝上), 控件按钮在下半屏 (平放); 应用应在半折时自动调整。
  - 官方 "Things to check": 内容在上控件在下; 进入/退出 Flex mode 时显示视觉过渡效果; **交互元素避开屏幕中缝 (crease)**; 部分界面 (如设置列表) 无需改变布局。
  - 适用场景示例: 相机拍照 / 视频播放 / 视频通话。
- 官方指路: App continuity (Android Developers + Samsung Developers) / Flex mode (Samsung Developers) / New form factors。

## 六、桌面网格与边距规范 (layout/grid.html)

- **Keyline 与 margin**: 为避免与曲面屏边缘冲突, 信息展示与交互组件左右边距**至少 24dp** (原文: "margins of at least 24 dp on both the left and right sides")。
- **Reject zone & Grip zone (拒触区)**: One UI 在边距内屏蔽触摸 — Reject zone 屏蔽交互区边缘误触; Grip zone 屏蔽握持时手掌与三指误触。
- **Cutout area (挖孔区)**: 屏幕内容按设备优化以避开摄像头挖孔 (90°/270° 横屏各有 Do/Don't 示例)。
- **全设备适配**: One UI 为手机/平板/折叠屏/Samsung DeX 外接显示器/单屏分屏视图提供多套尺寸 (multiple dimensions)。

## 七、外屏 (Flex Window) AppWidget 专属规范 (与已有 appwidget-dev-notes.md 的分工)

已有 samsung/appwidget-dev-notes.md 记录外屏 widget 行为笔记; 此处为官方原文规范 (evidence=A, 补齐原文出处):

- Flex Window = Galaxy Z Flip5 起的更大 Cover Screen (官方页面 galaxy-z/flex_window.html)。
- 外屏 widget 声明要求 (官方原文代码):
  - `android.appwidget.provider` xml: `minWidth="352dp"`, `minHeight="339dp"`, `resizeMode="horizontal|vertical"`, `widgetCategory="keyguard"` — 四个属性缺一不可。
  - 三星私有 meta-data `com.samsung.android.appwidget.provider` 的 xml 中 `display="sub_screen"` 声明 widget 显示在 Flex Window。
- 外屏 widget 启动 Activity: `ActivityOptions.makeBasic().launchDisplayId` 指定屏幕 (MAIN_SCREEN_ID=0, COVER_SCREEN_ID=1), 通过 PendingIntent 附带。
- 外屏 ongoing notification: `NotificationCompat.Builder.setOngoing(true)` 常驻通知 (codelab 注明该特性 One UI 6.0 起可用)。
- codelab (developer.samsung.com/codelab/galaxy-z/widget-flex-window.html): 外屏 widget 支持不透明/透明背景切换 + 垂直滚动 (预览更多通知); 用户启用路径 = 设置 > Cover screen > Widgets。
- 尺寸落点: 外屏 cell (352x339dp 声明值) 与内屏差异大, `OPTION_APPWIDGET_SIZES` 取真实当前 dp 是官方机制要求的适配 (widget bitmap 按 OPTION_APPWIDGET_SIZES 当前真实尺寸画, 与已有 memory 结论一致)。

## 对 Sleepy 的落点 (仅事实归纳)

1. 宽度 ≥600dp (Medium/Expanded) 的 Samsung 大屏上, 官方要求大屏布局; Sleepy widget 在平板/Fold 展开态的占格数由启动器网格决定, 渲染尺寸必须读 options (与 appwidget-dev-notes.md 一致)。
2. 左右边距 ≥24dp + Reject/Grip zone: widget 内容贴边部分在三星上可能被边距遮挡区裁剪, 内容设计留边距余量。
3. Flex Window 外屏 widget 需要三星私有 meta-data (`com.samsung.android.appwidget.provider` + `display="sub_screen"`) 才能进外屏 — Sleepy 未声明, 外屏上仅系统预设 widget 可用, 第三方 widget 不进外屏是当前默认行为。
4. 折叠屏 continuity/resizable 要求针对 Activity (应用本体), AppWidget 协议本身无 continuity 要求。
