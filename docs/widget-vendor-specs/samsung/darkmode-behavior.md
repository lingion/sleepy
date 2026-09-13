[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.samsung.com/one-ui/color/theme.html (+ https://developer.samsung.com/one-ui/color/system.html / accessibility/color-contrast.html / structure/visual-depth.html · developer.samsung.com One UI 官方设计规范原文)

# 三星 One UI 深色模式官方规范 (Dark mode)

## 一、Theme 页官方原文要点 (color/theme.html)

One UI Color - Theme 章节核心条款 (原文直译):

- **对比度双向要求**: 亮色/深色两种模式下, 文字与背景的对比度都要足够高以保证可读性; 同时背景与 focus block (焦点块) 之间的对比度要足够小, 以保持视觉舒适。
- **深色模式优势与测试要求**: 深色模式在夜间有特别的优势 (减少眩光、缓解眼疲劳), **"apps are optimized for it" (应用必须为深色模式优化) 被写为官方要求**。
- **开启后的表现**: 开启后背景、菜单和其他 UI 元素转为黑色或深灰色, 为用户减少眩光; 但同时可能让图片和其他内容显得刺眼 (visually jarring)。
- **发布前双模式测试是硬要求**: "Be sure to test apps and features in both Light and Dark modes before release." (发布前必须在亮色和深色两种模式下测试应用和功能。)
- 官方示例图覆盖三个场景: Quick panel (快捷面板)、Messages list (消息列表)、Apps (应用)。

## 二、色彩体系与深浅色双值 (color/system.html)

One UI 主色的官方双模式色值 (原文给出的 hex 值):

| Token | Light theme | Dark theme |
|---|---|---|
| Primary dark (主蓝) | `#0072de` | `#3e91ff` |
| Primary (sub text color) | `#0381fe` (Light/Dark 同值) | 同左 |
| Color control activated (滑块/勾选激活态) | `#3e91ff` (Light/Dark 同值) | 同左 |

- 设计原则: 蓝色象征信任/希望/稳定, 是三星品牌核心色; 为保证亮色与深色两种模式下的最优可读性, 主蓝以 3 种方式调整 (浅色主题深蓝 #0072de, 深色主题亮蓝 #3e91ff)。
- **功能组件颜色在两种模式下保持一致** ("we also made sure function components have the same color in both Light mode and Dark mode") — 通知/接受/拒绝等语义色不随模式变化。
- 背景色 (Background): 大面积用平静 (calm) 色保证视觉舒适; focus block 在亮/深两种模式下都用单调色 (monotone); 可选替代方案 = focus block 用全出血 (full bleed) 图片填充。
- 渐变 (gradient): One UI 渐变用细节纹理或类似色 (analogous colors) 组合; 全屏用暖渐变增加愉悦感。
- 色彩结构原则: 大面积平静色 + 小面积明亮醒目色; 渐变引导用户下一步点击位置 (attention flow)。
- Focus block 颜色 3 型: Type 1 = 标准单调色 (功能驱动内容如消息列表, 最常用); Type 2 = 从应用色彩体系取浅色 (on/off 开关, 亮/深两种模式分别调整亮度); Type 3 = 渐变型 (慎用, 会使屏幕复杂)。

## 三、无障碍对比度要求 (accessibility/color-contrast.html)

- **最小对比度**: 小文本与背景对比度至少 **4.5:1**; 大文本 (普通字重 >18dp 或粗体 >14dp) 至少 **3:1**。
- 色盲/灰度用户: 仅靠颜色区分信息不可行, 必须提供附加标记 (additional marks); 可切灰度模式自查信息传达。
- 官方推荐工具: The Paciello Group Colour Contrast Analyser。

## 四、视觉深度与深色模式 (structure/visual-depth.html)

- 模糊 (blur) 效果必须均匀应用于整个背景, 并**根据当前主题搭配浅色 dim 或深色 dim** — 同一 blur 组件在亮/深主题下的 dim 层不同, 是主题感知组件。
- 新屏幕与前一屏幕相关时只加阴影 (shadow); 不相关时对前一屏加 blur+dim。
- dim + shadow 不可同时使用 (重复叠加会增加视觉疲劳)。

## 五、对第三方 App 的影响 (与 AppWidget 落点)

1. **深色模式优化是官方要求**: 三星是六厂商中唯一把「发布前双模式测试」写成硬性要求的厂商; AppWidget 未适配深色在三星上属于偏离官方规范 (但无审核拦截, Galaxy Store 分发指南中无深色模式条款)。
2. **实现通道 = Android 标准 DayNight**: One UI 文档只定义视觉规范, 不定义私有 API; 深色跟随走 Android `-night` 资源限定符 / `Configuration.UI_MODE_NIGHT_*`, 无三星专属要求。
3. **主色双值**: 若 widget 用到三星系主蓝, Light=#0072de / Dark=#3e91ff 是官方 token; 自有品牌色按「功能色双模式一致 + 文本对比度 4.5:1」执行。
4. **焦点块低对比原则**: widget 的卡片/条带背景与系统背景对比度要小 — 深色模式下 widget 背景应为深灰而非纯黑与系统争对比 (与小米「深色模式必须适配」结论同向, 但三星给出的是可执行视觉参数)。
5. **图标深色适配动态**: One UI 8.5 起三星在系统层面推进应用图标深色适配 (社区报道, 见 samsung/gaps.md 等级 C), 图标层第三方深色素材的必要性在上升; AppWidget bitmap 渲染面 (非图标) 不受该机制影响。
6. **主题感知 dim**: 若 widget 用 blur/shadow 类效果 (RemoteViews 无 blur API, 实际不适用), 官方要求按主题切换 dim — 对 Sleepy 无操作面, 仅作设计规范存档。

## 原文页面清单 (全部 evidence=A, 2026-09-13 抓取)

| 页面 | URL |
|---|---|
| Color - Theme (深色模式规范) | https://developer.samsung.com/one-ui/color/theme.html |
| Color - Color system and usage (主色双值) | https://developer.samsung.com/one-ui/color/system.html |
| Accessibility - Color and contrast (4.5:1 / 3:1) | https://developer.samsung.com/one-ui/accessibility/color-contrast.html |
| Structure - Visual depth (主题感知 dim) | https://developer.samsung.com/one-ui/structure/visual-depth.html |
| One UI Overview (四原则: Dark mode 列为「visibly comfortable」核心手段) | https://developer.samsung.com/one-ui/overview.html |

> One UI Overview 原文: 四大设计原则第 3 条 "Be visibly comfortable" 明示 "features like Dark mode to decrease eye fatigue and glare" — 深色模式是 One UI 可视舒适性的官方支柱之一。
