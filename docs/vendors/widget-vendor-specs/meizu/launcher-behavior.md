[evidence=B] 整理 2026-09-13 · 依据: 魅族开放平台用户手册官方文档 (open.flyme.cn/docs?id=260 添加桌面插件 / 272 Aicy 纵览 / 258 桌面设置 / 254 屏幕效果, evidence=A 原文已存 flyme-docs-index.md) + Flyme 官方固件更新页 (flyme.cn/firmwarelist-186, Flyme 10.5 官方更新记录) + 社区核实 (requestPinAppWidget 渠道矩阵 CSDN 147627142 中无魅族行, 魅族无社区实测数据)。

# 魅族 Flyme — 桌面 (Flyme 桌面) 启动器行为

> 已有 meizu/flyme-open-portal.md 为开放平台门户首页存档; meizu/flyme-docs-index.md 为文档树逐篇索引。本文件为桌面/小组件/Aicy 行为的专题整理。
> 证据等级 B: 桌面行为条款全部引自魅族官方文档 (open.flyme.cn 原文, 见 flyme-docs-index.md), 但 requestPinAppWidget 渠道行为无魅族真机实测 (社区矩阵无魅族行)。

## 一、定性: 无独立厂商 SDK, 走 Android 原生 AppWidget

- 魅族开放平台文档树 (8 个一级目录, 全部 60+ 篇) 中**无 AppWidget 专属规范页** — 与小米 (tech-spec/design-spec/qa-faq 三篇) 不同, Flyme 未对 Android 小组件做厂商规范约束。
- Flyme 桌面 AppWidget 通道 = Android 原生体系 (AppWidgetProvider + appwidget-provider xml); 厂商差异化在消费者功能层 (Aicy 纵览/桌面插件互通/桌面角标), 不改变 AppWidget 协议。

## 二、桌面插件添加行为 (open.flyme.cn/docs?id=260, 官方)

- 添加路径: 长按桌面空白处或双指捏合进入自定义状态 → 点击「添加插件」选取应用插件 → 长按拖动至桌面相应位置松手。
- 系统时钟无单独数字时钟插件 (用聚合天气插件替代)。
- 第三方应用插件 (AppWidget) 可从「用户手册 > 智能操作 > Aicy 纵览」的插件选择页添加 (docId 260 官方提示)。

## 三、Aicy 纵览 (负一屏) 与桌面插件互通 (docId 272 + firmwarelist-186, 官方)

- Aicy 纵览 = Flyme 负一屏 (桌面右划), 系统功能卡片 (股市/快递/智能家居/通勤/停车等)。
- **官方明示支持第三方应用插件**: "Aicy 纵览支持添加展示第三方应用插件, 在功能卡片页面选择对应的应用并添加" — 第三方 AppWidget 可进负一屏。
- **Flyme 10.5 (2024-03-29) 官方更新记录**: "支持 Aicy 纵览及桌面之间互相拖放系统应用插件" — 纵览与桌面插件互通; "安装超级课程表 App 后支持添加课程表插件至负一屏" (与 Sleepy 同类的课程表 AppWidget 有官方适配先例)。
- 优化添加插件页面的显示布局, 支持搜索插件 (Flyme 10.5)。
- 卡片管理: 长按移除/编辑 (仅部分卡片); 长按拖至相同尺寸卡片上方可堆叠, 滑动切换显示; Flyme 11.2 新增堆叠插件「智能切换」(根据插件动态自动轮换, 长按堆叠插件 → 编辑堆叠 → 智能切换)。

## 四、桌面设置与网格 (docId 258, 官方)

- 桌面编辑: 长按空白处或双指捏合; 成组/对齐 (摇一摇机身对齐)。
- **图标布局支持 4x6 或 5x6 两档排列**, 实时预览, 可恢复默认 — cell 实际 dp 随此两档变化, AppWidget 必须依赖 `OPTION_APPWIDGET_SIZES` 读真实尺寸 (与小米/vivo 结论同构)。
- 图标风格: 默认/经典/集合 3 种; 装主题/图标包后跟随主题, 不受此设置影响。
- 图标大小/名称大小可调 (滑条); 更多设置: Flyme 风格图标转换、卸载后补位、锁定布局。
- 最近任务: Flyme 沿用 Android 最近运行卡片形式, 暂不支持改堆叠等其他排列。
- Flyme 10.5 桌面相关: 桌面批量卸载; 侧边索引列表支持分身应用/快捷方式/快应用/小程序; 系统字体大小修改时桌面应用名称大小跟随修改; 拖动图标/插件支持一根手指按住 + 另一根手指翻页。

## 五、深色模式跟随行为 (docId 254, 官方)

- 启用方式 3 种: 手动 (设置 > 显示和亮度 或 控制中心); 定时切换 (日落到日出, 按时钟+地理位置判定; 或自定义时段)。
- **「深色应用管理」**: 深色模式设置内可手动开启/关闭部分 (未自动适配) 第三方应用的深色模式 — Flyme 对未适配深色的第三方 App 有系统级强制染色通道; AppWidget 渲染结果可能被系统调色处理。
- 色调「默认/柔和」二选; 「调暗壁纸」降低亮色壁纸亮度。
- 屏幕色彩: 自适应/标准/鲜艳 + 色温手动; 刷新率 144/120/90/60Hz; 分辨率 QHD+/FHD+。
- 「超大字体简易模式」: 更大字体图标 + 导航自动切安卓导航栏 — AppWidget 需容忍 fontScale 放大 (跨厂商通用风险)。
- Flyme 12.6 (2026-06-30) 修复记录含「修复深色模式下微信界面显示异常的问题」— 深色模式对三方 App 适配是 Flyme 持续修补面。

## 六、requestPinAppWidget 渠道行为 (无实测数据)

- 社区渠道矩阵 (CSDN 147627142, 2025-05) 覆盖华为/honor/小米/vivo/OPPO,**无魅族实测行**。
- Flyme 无厂商 pin 拦截的公开记录; 按 AOSP 默认预期 (弹系统确认框 + 空间不足自动开新页) 工作的可能性高, 但**未经真机验证** — 不做行为断言。
- Sleepy 对策: pin 流程按 AOSP 预期 + `PinWidgetActivity` (auto-finish OK) 兜底与手动添加引导, 与华为/荣耀对策同构; 若魅族真机反馈到达再按实测补矩阵行。

## 七、桌面图标角标 (docId 207, 官方, 可选增强项)

- ContentProvider 通道: `content://com.meizu.flyme.launcher.app_extras/badge_extras`, `change_badge`/`query_badge`, 权限 `com.meizu.flyme.launcher.permission.WRITE_BADGE_EXTRAS`。
- 生效条件 (用户侧): 应用通知权限开启 + 桌面角标设置选「数字通知」。
- 打开应用/清理通知不清角标, 需开发者自行调用清除。

## 八、与 Sleepy 公共层的关系

- 公共层规则 (INDEX.md 对照表) 对魅族全部成立: 无 configure、同步 RemoteViews、`ALL_WIDGET_VARIANTS` 派生、pin 路由回落、渲染按真实 dp。
- 魅族专属动作 = 0 (无厂商 SDK 要求); 风险项 = 深色模式「深色应用管理」强制染色 (系统层, 代码无解, 静态资源适配深色可降低触发)。
