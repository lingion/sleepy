[evidence=B] 抓取 2026-09-13 · 源URL: https://dev.mi.com/xiaomihyperos/documentation

# 抓取缺口

## 1. 桌面网格的版本化统一规格

- 主题名：HyperOS/MIUI 桌面网格（4x6/5x6/5x7/5x8）按版本与机型的统一矩阵。
- 尝试 URL：
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664 （设计规范，写 4x6+5x8/5x7）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584 （技术规范，写 4x6+5x6）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1586 （审核规范，写 4x6+5x6）
- 失败原因：三个官方页文字互相冲突（5x8/5x7 vs 5x6），且无按 HyperOS/MIUI 版本区分的 Launcher 规格页。
- 建议渠道：邮件 `miui-widget@xiaomi.com` 索取当前验收矩阵；目标机实测时归档 ROM 版本 + 桌面布局设置 + 截图。

## 2. HyperOS 深色模式的版本化强制反色策略

- 主题名：HyperOS 各版本对第三方 App 的强制反色、白名单、WebView 行为矩阵。
- 尝试 URL：
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1595 （深色模式适配说明）
  - https://dev.mi.com/console/doc/detail?pId=2298 （旧版 MIUI 深色模式适配说明，console 域 SPA 页需 JS，抓不到正文，仅拿到 searxNG 缓存摘要）
- 失败原因：官方文档说明 MIUI12 起默认全局反色、`force_dark_google` 关闭法与 WebView/图片限制，但没有 HyperOS 版本化的行为矩阵，也没有第三方 WebView 的统一保证。
- 建议渠道：目标 ROM 实机矩阵（DayNight / Force Dark / `force_dark_google` / WebView / Widget night 资源）；研发问题可联系官方页给出的 `darkmode@xiaomi.com`。

## 3. 小米应用商店当前统一 targetSdk 要求

- 主题名：新提交/更新 APK 的当前 targetSdkVersion 门槛。
- 尝试 URL：
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1974 （提高API等级重要通知，仅 2019-08-01 起 API 26 条款）
  - https://dev.mi.com/docs/appsmarket/auditing&evaluation/auditing_criterion/ （审核规范正文）
  - https://dev.mi.com/xiaomihyperos/app-distribute （应用分发首页）
- 失败原因：公开官方页只有 2019 年 API 26 自律公约条款；targetSdkVersion≥30（2024-01 起）的报道仅见中关村在线等媒体（C 级），未在官方页核实。
- 建议渠道：提交时以开发者后台的版本校验提示与最新公告为准；必要时走工单系统确认。

## 4. 应用商店通用审核 SLA

- 主题名：普通 APK 提交的统一审核完成时限。
- 尝试 URL：
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1080
  - https://dev.mi.com/xiaomihyperos/app-distribute
  - https://dev.mi.com/docs/appsmarket/distribution/app_submit/
- 失败原因：审核规范只列材料与功能要求，无 SLA 数字。能确认的“1-3 个工作日”只属于小部件开放平台审核，不可外推到商店 APK 审核。
- 建议渠道：开发者后台提交页面/工单系统确认；应用分发客服。

## 5. WorkManager/AlarmManager 的执行保证

- 主题名：后台任务在 Powerkeeper/主动清理/省电策略下的 API 行为。
- 尝试 URL：
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1607 （进程管理）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1628 （Powerkeeper）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1624 （自启动）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584 （小部件技术规范）
- 失败原因：官方说明进程查杀分类、Powerkeeper 触发条件、自启动默认关闭、Widget 三条刷新路径，但没有任何 WorkManager/AlarmManager 的版本化保证。
- 建议渠道：目标机实测并记录 WorkManager/AlarmManager 触发延迟与 `am_kill` 日志；工程上以曝光、主动刷新、MI PUSH 透传三条官方路径为准。

## 6. console 旧版文档正文（SPA 页抓不到）

- 主题名：dev.mi.com/console/doc 下的 MIUI 旧版文档正文（如 pId=2298 深色模式、pId=2466 MIUI小部件规范、pId=2465、pId=2474）。
- 尝试 URL：
  - https://dev.mi.com/console/doc/detail?pId=2466
  - https://dev.mi.com/doc/detail?pId=2466
  - https://dev.mi.com/console/doc/detail?pId=2298
- 失败原因：console 域是 SPA，返回 "You need to enable JavaScript to run this app"；经 cfp-fetch 抓到的正文为空。内容已从 searxNG 缓存摘要 + yimenapp 转载（https://www.yimenapp.com/kb-yimen/12279/ 、/12287/，B/C 级转载）部分还原，未当作 A 级来源写入。
- 建议渠道：带 JS 的浏览器（control-chrome）渲染后抓取；或用 Google/Bing 缓存；或以 yimenapp 等转载核对（注意转载基于 2022 年版本，可能与 HyperOS 现行规范有差异）。

## 7. 小部件开放平台内部字段与设备兼容矩阵

- 主题名：小部件中心排序权重、发布设备字段、各机型能力矩阵（isMiuiWidgetSupported 支持清单）。
- 尝试 URL：
  - https://widget.xiaomi.com/ （登录墙，返回 Xiaomi Account 登录页）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1588 （只到流程层）
  - https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1926 （负一屏商业化资源，非第三方能力矩阵）
- 失败原因：开放平台需企业开发者账号登录；公开文档不包含内部字段与机型矩阵。
- 建议渠道：企业账号登录后导出实际表单；邮件 `miui-widget@xiaomi.com` 申请测试环境（官方《三方适配全流程指导》含自测方法，需提测大礼包）。
