[evidence=A] 抓取 2026-09-13 · 源URL: https://dev.mi.com/xiaomihyperos/documentation

# HyperOS 开发者门户总览

## 站点结构

小米澎湃 OS 开发者平台的文档中心把应用开发文档分为：

- 系统适配：多设备 UI 适配、通知与状态栏、多任务与多窗口、小部件适配、功能适配、硬件适配、桌面适配、权限管理、系统设置、使用规范、安卓适配、资源支持、兼容与安全检测。
- 服务能力：服务接入指南、一键登录、小米账号服务、小米超级岛、VoIP、CloudKit、应用接力、网页秒开引擎、钱包服务等。
- 分发文档：应用分发、游戏分发、电视应用分发、快应用分发、小游戏分发、服务分发、内容分发。
- 开发者账号与支持：注册标准、注册指南、团队账号、常见问题、开发者活动、联系我们。

文档中心的“小部件适配”分类包含：Xiaomi HyperOS 小部件设计规范、小部件技术规范与系统能力说明、Widget 适配建议及示例、小部件审核规范、展示图规则须知、小部件提交审核与上传操作指南、小部件开放平台协议、小部件中心品牌展示图注意事项、小部件适配常见问题 Q&A。

## 能力清单

### 小部件与桌面

官方将 Xiaomi HyperOS 小部件定义为基于 Android AppWidget 的能力，并在系统适配层增加了独立 Widget 进程、曝光刷新、负一屏/桌面展示、Widget 详情页和小部件中心。详见：

- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664

### 推送

文档中心有独立的“推送服务”目录，包含 Android 客户端 SDK 集成指南、Android 快速接入指南、服务端 API、消息规则、推送限制与推送运营规范。

小部件技术文档另有“Push 透传刷新服务”：当小米 Widget 状态改变、Widget 不可见且主应用未启动时，开发者可调用小部件服务端的更新接口（域名 `https://developer.assistant.miui.com`，路径 `/openapi/widget/{cpCode}/refresh`），经由 MI PUSH 发送透传消息给负一屏/桌面客户端，由系统拉起 Widget 独立进程，不唤醒应用主进程。接口需要 `app-id`、`access-token`、`sign`、`timestamp`、`trace-id` 五个 Header，Body 需带 `oaid`、`widgetId`、`widgetProviderName`。

来源：

- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1529
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584

### 账号

“服务能力”下的“小米账号服务”包含快速接入指南、OAuth2.0 协议原理、OAuth2.0 授权码模式、OpenID Connect 授权码模式、OAuth SDK 合规使用说明、隐式授权模式下线帮助、账号开放平台 SDK 下载、场景化登录、令牌生命周期说明、访问令牌更新接口、MAC 签名验证、开放数据接口权限列表、账号开放数据接口、相关错误码定义与常见问题。

来源：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1512

### 支付与变现

公开文档目录里没有单一的“HyperOS 支付 API”页面；与付费/变现相关的官方能力分散在多个业务目录：

- 游戏 SDK：小米游戏联运 SDK 接入指南、Unity 接入、游戏参数获取、错误代码表（`pId=1377` 等）。
- 应用联运：联运 SDK 接入操作指南、应用联运接入流程（`pId=1150`、`pId=1088`）。
- 广告联盟（米盟）：应用/游戏/快应用/小游戏广告接入、结算与合规（`pId=1931` 等）。
- 钱包服务：CCC 车钥匙接入指南（`pId=1751`）。

## HyperOS 与 MIUI 文档的关系

HyperOS 文档中心保留了大量 MIUI 命名与 MIUI 时期的文档：功能适配目录下仍有“MIUI无极音量适配说明”“MIUI进程管理适配说明”“深色模式适配说明”等页面；权限管理目录下有“Powerkeeper对应用的管控说明”。小部件的接口名和 Manifest 元数据同样是 MIUI 时代命名：`miuiWidget`、`miuiWidgetRefresh`、`miuiWidgetVersion`、`miui.appwidget.action.APPWIDGET_UPDATE`、`:widgetProvider`。

工程结论：以当前 HyperOS 页面为首要来源，同时保留 MIUI 旧系统与原生 Android Widget 的回退路径。产品名称换了，接口没有换。

## 相关平台入口

- HyperOS 文档中心：https://dev.mi.com/xiaomihyperos/documentation
- 应用分发：https://dev.mi.com/xiaomihyperos/app-distribute
- 小部件开放平台：https://widget.xiaomi.com/ （需登录）
