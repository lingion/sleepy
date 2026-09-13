# 魅族 / 三星文档抓取 gaps (2026-09-13)

> 本次抓取未能落地的主题。其余主题已全部落盘 (见 meizu/ 与 samsung/ 目录)。

## 魅族 gaps

### 1. Flyme 10/11 桌面 AppWidget 行为变化的官方 changelog 汇总 (部分覆盖)

- 主题名: Flyme 10 → 11 (AIOS) → 12.6 (AIOS 2) 桌面/小组件官方变化全链条。
- 尝试过的 URL:
  - https://www.flyme.com/aios/ (JS 渲染 SPA, 无 JS 空壳)
  - https://www.flyme.com/aios2/ (同上, 空壳)
  - https://www.flyme.com/flyme10/index.html (JS 渲染 SPA, 空壳)
  - https://www.flyme.cn/firmwarelist-186.html (成功, 魅族 17 Flyme 10.5 官方更新记录, 已落盘)
  - https://www.flyme.cn/firmwarelist-198.html (成功, 魅族 21 Flyme 12.6 官方更新记录, 已落盘)
  - https://www.flyme.cn/firmwarelist-199.html (成功, 但仅显示最新版 12.6, 不含 Flyme 11 历史版本记录)
- 失败原因: flyme.com 主站全部页面为 JS 渲染 SPA (curl/pandoc 拿不到内容); flyme.cn 固件页只显示**当前可更新版本**的更新记录, 历史版本 (Flyme 11.0.0 / 11.2.0 的完整更新记录) 页面上不展示。
- 已覆盖部分: Flyme 10.5 与 12.6 两版桌面/小组件变化 + Flyme AIOS 11.0.0 / 11.2.0 的 IT之家整理稿 (ithome.com/0/782/724.htm 与 /0/807/506.htm, 证据等级 C, 含「支持更多桌面小组件」「桌面堆叠插件智能切换」等条目, 未单独落盘)。
- 建议后续渠道: 魅族社区 (bbs.flyme.cn) 历史版本公告; 盖乐世社区式客户端查询; 或用真机 (魅族 21 系) 系统更新页查看历史版本记录。

### 2. Flyme requestPinAppWidget 真机实测行为

- 主题名: 魅族桌面 pin 弹框/空间不足行为。
- 尝试过的 URL: 社区渠道矩阵 https://blog.csdn.net/qq_41904106/article/details/147627142 (已存 _cross-vendor/, 无魅族行); searxNG 多轮搜索。
- 失败原因: 社区实测矩阵不覆盖魅族; 无公开的魅族真机实测数据。
- 建议后续渠道: 魅族真机 (魅族 21) 手动验证; 魅族开发者社区提问。

## 三星 gaps

### 1. One UI Home 桌面网格 cell 尺寸的官方 dp 规格 (未公开)

- 主题名: One UI 桌面网格的 cell 实际 dp 值官方规格。
- 尝试过的 URL:
  - https://developer.samsung.com/one-ui/layout/grid.html (成功, 但内容是应用内布局的 keyline/margin 24dp + Reject/Grip zone, 非桌面网格)
  - https://developer.samsung.com/one-ui/structure/basic-structure.html (成功, Home screen 概述)
  - https://www.samsung.com.cn/support/mobile-devices/changes-to-the-home-screen-on-the-samsung-galaxy-devices/ (成功, 网格布局变化 4x5/5x5 移除 + 横屏重定位)
- 失败原因: 三星未公开桌面网格的 cell dp 规格页 (AppWidget cell 尺寸随设备/网格模式/图标大小滑块变化, 官方只给机制不给数值)。
- 建议后续渠道: 真机 `dumpsys` / uiautomator 实测各机型 cell dp (与小米 2k 屏 14.48dp 圆角规格的获取方式同构); Good Lock Home Up 模块文档。

### 2. One UI 深色模式对 AppWidget 渲染层的宿主缓存行为 (小米式机制是否存在于三星)

- 主题名: 三星宿主是否缓存 RemoteViews / 深色切换时是否重建 widget (小米 QA 揭示的机制在三星是否有同款)。
- 尝试过的 URL: developer.samsung.com 全 One UI 文档树 (无此主题页); searxNG 搜索。
- 失败原因: 三星 One UI 文档只定义视觉规范 (color/theme), 无 AppWidget 深色切换机制描述; 该行为属宿主实现细节, 未公开。
- 建议后续渠道: 三星开发者论坛 (forum.developer.samsung.com/c/samsung-dex/26 同类); 真机实测深色切换时 widget 是否走 options 回调。

### 3. Galaxy Store 审核流程中 widget 专项条款 (确认不存在, 非抓取失败)

- 主题名: Galaxy Store 审核流程中的 AppWidget 专项条款。
- 尝试过的 URL: https://developer.samsung.com/galaxy-store/distribution-guide.html (全文已抓取落盘)。
- 失败原因: 非抓取失败 — App Distribution Guide 全文确无 widget 专项条款 (小组件随 APK 整体审核); 已在 galaxystore-review.md 中记录此结论。
- 建议后续渠道: 无需补充 (结论 = 无专项条款)。

### 4. One UI Beta (One UI 8/9) 桌面/小组件新特性官方页 (部分覆盖)

- 主题名: One UI 8/9 Beta 桌面与小组件新特性官方文档。
- 尝试过的 URL: https://developer.samsung.com/one-ui-beta (未单独抓取, One UI 主文档已覆盖 One UI 通用规范)。
- 失败原因: 未深入; Beta 页主要是招募/版本信息, 设计规范与主文档同源。
- 建议后续渠道: samsung.com.cn/one-ui/ (One UI 9 消费者页已存 oneui-overview.md)。
