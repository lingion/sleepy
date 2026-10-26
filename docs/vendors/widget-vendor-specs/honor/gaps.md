[evidence=B] 抓取 2026-09-13 · 源URL: https://developer.honor.com/cn/ · https://developer.honor.com/cn/docs/magic-os · https://developer.honor.com/cn/kitdoc?kitId=11100

# 荣耀文档抓取缺口

抓取方法记录: 荣耀开发者平台是 Vue SPA， 匿名 UA 只回 9.5KB 空壳 (meta description 无正文)； 文档数据 API (`/connect-api/document/portal/*`) 一律要求登录 (`code 4002 当前用户未登录`)。**带 Googlebot UA (`Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)`) 可拿到 SSR 全文**， 本批 A 级文件全部经此法抓取。后续抓荣耀文档直接复用此法。

## 1. MagicOS 桌面 Launcher 对原生 AppWidget 的网格/尺寸限制

- 主题: 荣耀桌面(魔力桌面)对标准 AppWidgetProvider 的完整网格映射表、 minWidth/minHeight 强制规则、 负一屏第三方 AppWidget 注册 API。
- 尝试过的 URL: `https://developer.honor.com/cn/docs/magic-os` (Googlebot UA 返回 0B， 疑似文档中心深层页需登录或路径失效)、 `https://developer.honor.com/cn/kitdoc?kitId=11100` (拿到通用适配指导， 无 launcher 规格)、 100170/100173 (拿到荣耀智慧服务卡片规格， 非原生 AppWidget 通道)。
- 失败原因: 荣耀公开文档把"安卓卡片(widget卡片)"定义在智慧服务接入体系内， 原生 AppWidget 通道无独立公开规范页； `/cn/docs/magic-os` 路径 0B。
- 建议后续渠道: 荣耀开发者账号登录后的文档中心； 荣耀云测试真机跑标准 AppWidget 矩阵； #31 真机实测路径 (已有 appwidget-dev-notes.md)。

## 2. 系统强制深色/智能反色与"深色模式优化"开关

- 主题: MagicOS 深色模式对第三方 App 的强制深色/智能反色算法、 控件白名单、 uiMode configChanges 专属差异。
- 尝试过的 URL: `https://developer.honor.com/cn/doc/guides/100170`、 `https://developer.honor.com/cn/doc/guides/100173`、 `https://www.honor.com/cn/support/content/zh-cn15819173/` (消费者帮助页， 只有开关操作无第三方 App 行为)、 `https://developer.honor.com/cn/docs/magic-os` (0B)。
- 失败原因: 荣耀卡片体系公开规范走"开发者自带浅/深两套色值"路线， 未发布小米式强制深色/反色白名单文档； 消费者帮助页不含开发者行为。
- 建议后续渠道: 目标 MagicOS 版本真机逐项实测 (普通 View、 RemoteViews、 WebView、 Compose 四形态 × 开关状态)， 采集 uiMode 与配置回调。

## 3. 荣耀服务 vs 华为 HMS 逐项差异表

- 主题: 账号/推送/地图等服务的逐 API 差异。
- 尝试过的 URL: `https://developer.honor.com/cn/`、 `https://developer.honor.com/cn/doccenter` (摘要级)、 `https://developer.honor.com/cn/docs/11002/guides/kit-history` (需登录)。
- 失败原因: 官方未发布两平台服务差异对照； kit-history 文档更新说明页需登录。
- 建议后续渠道: 分别读荣耀与华为各服务 SDK 集成指南 (包名/API/权限清单)， 按版本建迁移矩阵； 禁用品牌名推导兼容性。

## 4. 荣耀应用市场对上架 APK 的 targetSdk 强制下限

- 主题: 与华为"上架 APK 需 targetSdk>=XX"条款对应的荣耀要求。
- 尝试过的 URL: 100885 (应用审核FAQ)、 101269 (平台审核FAQ)、 101581 (隐私自检) — 三份官方 FAQ 均无 targetSdk 条款。
- 失败原因: FAQ 未覆盖； 审核规范全文页 (在线提单系统引用的《荣耀应用市场审核规范》) 未在匿名可抓路径找到。
- 建议后续渠道: 登录应用市场开发者后台读《荣耀应用市场审核规范》全文； 或提交测试版本实测。

## 5. fair_memory_scheduling (公平运行内存适配) 全文

- 主题: 荣耀公平运行内存机制的预警/查杀广播契约 (与 widget 后台刷新相关)。
- 尝试过的 URL: `https://developer.honor.com/cn/docs/adaptation_guide/guides/fair_memory_scheduling` (33B 短页， Googlebot UA 也只回短页)、 `/cn/docs/11100/guides/fair_memory_scheduling` (0B)、 `https://dev.honor.com/...` (超时)。
- 失败原因: 该页对匿名+Googlebot UA 均不可达； 搜索引擎索引摘要显示其存在且内容为"内存预警广播→应用应及时释放内存； 查杀广播→立即释放"。
- 建议后续渠道: 登录态抓取； 或从荣耀开发者社区帖找全文转载。已知部分内容 (来自搜索摘要， evidence=B): 触达预警条件时系统发内存预警广播， 应用接收后应及时释放内存； 持续增长触达查杀条件时发查杀广播， 应用接收到广播后应立即释放。

## 6. 锁屏小组件三方接入通道

- 主题: 三方 App 锁屏小组件公开接入 API。
- 状态: 已有 lockscreen-widget-rollout.md 记录首批 20+ 为系统应用、 三方未开放； 本轮未找到新的开放公告， 维持原结论。
- 建议后续渠道: 荣耀 MagicOS 版本发布说明 (IT之家等已报道四月升级含"自定义锁屏小组件"， 待官方开发者文档跟进时再抓)。
