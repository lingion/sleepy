# 魅族文档抓取 gaps (2026-09-13)

> 与 samsung/gaps.md 对应的魅族侧 gaps (部分与 samsung/gaps.md 的魅族段重复, 此处为魅族目录内完整版)。

## 1. Flyme 10/11/12 桌面 AppWidget 官方 changelog 全链条 (部分覆盖)

- 主题名: Flyme 10 → 11 (AIOS) → 12.6 (AIOS 2) 桌面/小组件官方变化全链条。
- 尝试过的 URL:
  - https://www.flyme.com/aios/ (JS 渲染 SPA, 无 JS 空壳)
  - https://www.flyme.com/aios2/ (同上, 空壳)
  - https://www.flyme.com/flyme10/index.html (JS 渲染 SPA, 空壳)
  - https://www.flyme.cn/firmwarelist-186.html (成功, 魅族 17 Flyme 10.5.0.0A 官方更新记录, 已落盘 flyme-docs-index.md)
  - https://www.flyme.cn/firmwarelist-198.html (成功, 魅族 21 Flyme 12.6.0.0A 官方更新记录, 已落盘)
  - https://www.flyme.cn/firmwarelist-199.html (成功, 但仅显示最新版 12.6, 不含 Flyme 11 历史版本记录)
- 失败原因: flyme.com 主站全部页面为 JS 渲染 SPA; flyme.cn 固件页只显示当前可更新版本的更新记录, Flyme 11.0.0/11.2.0 的完整更新记录页面不展示。
- 已覆盖部分: Flyme 10.5 与 12.6 两版桌面/小组件变化; Flyme AIOS 11.0.0/11.2.0 的 IT之家整理稿 (ithome.com/0/782/724.htm「支持更多桌面小组件、优化第三方应用插件显示效果」、/0/807/506.htm「桌面堆叠插件新增智能切换」) 证据等级 C 未单独落盘。
- 建议后续渠道: 魅族社区 bbs.flyme.cn 历史版本公告; 真机系统更新页查历史版本记录。

## 2. Flyme requestPinAppWidget 真机实测行为

- 主题名: 魅族桌面 pin 弹框/空间不足自动开新页行为。
- 尝试过的 URL: 社区渠道矩阵 https://blog.csdn.net/qq_41904106/article/details/147627142 (已存 _cross-vendor/, 无魅族行); searxNG 多轮搜索。
- 失败原因: 社区实测矩阵不覆盖魅族; 无公开魅族真机实测数据。
- 建议后续渠道: 魅族真机 (魅族 21 系) 手动验证; 魅族开发者社区提问; launcher 市场反馈。

## 3. Flyme 深色模式对 AppWidget 的宿主缓存机制 (未公开)

- 主题名: Flyme 宿主是否缓存 RemoteViews / 深色切换是否重建 widget (小米 QA 揭示的机制在 Flyme 是否同款)。
- 尝试过的 URL: open.flyme.cn 文档树全部 60+ 篇 (无此主题); searxNG 搜索。
- 失败原因: 魅族无 AppWidget 深色适配开发者规范页; 「深色应用管理」机制有官方文档但 widget 渲染层行为无描述。
- 建议后续渠道: 真机实测深色切换时 widget 是否走 options 回调; 魅族开发者社区。

## 4. 魅族应用商店 widget 专项审核条款 (确认不存在, 非抓取失败)

- 主题名: 魅族应用商店的 AppWidget 专项审核条款。
- 尝试过的 URL: https://open.flyme.cn/docs?id=110 应用审核规范 (全文已抓取落盘 appstore-review.md)。
- 失败原因: 非抓取失败 — 审核规范全文确无 widget 专项条款 (无 Xiaomi 式小部件独立审核); 已在 appstore-review.md 记录此结论。
- 建议后续渠道: 无需补充 (结论 = 无专项条款, 小组件随 APK 整体审核)。
