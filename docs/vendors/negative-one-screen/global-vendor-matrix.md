# 全球 Android 厂商负一屏能力矩阵（2026-09）

> 抓取时间：2026-09-19
> 抓取原则：优先厂商官方 developer.* / Android 官方；查无公开协议的显式记录，不编造私有字段。
> 目的：把 Android APK 一侧能直接做的负一屏/小组件兼容能力落到本地决策面，避免向任何无公开协议的品牌伪造私有 meta-data。

## 接入方式三档

| 等级 | 含义 | 谁属于这一档 |
|---|---|---|
| A — APK 自助 | 单方面修改 manifest / 资源 / 渲染管线即可生效 | Pixel、Samsung、Sony、Motorola、ASUS、Lenovo、HMD、Nokia、Nothing、TECNO、Infinix、itel、ZTE、TCL、Sharp、Fujitsu、Panasonic、Kyocera、CAT、Unihertz、Doogee、Ulefone、Blackview 等 AOSP 兼容阵营 |
| B — APK 自助 + 商务/审核加分 | 基础靠 APK 声明；扩展（原子组件、推荐位、SmartDock 等）需要厂商白名单或商店审核 | vivo、小米、OPPO（一部分 Pantanal UPK）、Honor（一部分 MagicOS 卡片）、Huawei EMUI/HarmonyOS NEXT 之前的 AppWidget |
| C — 平台层，不能由 APK 替代 | 需厂商独立生态或 HAP/Snapdragon Spaces / HarmonyOS 元服务 | Huawei HarmonyOS NEXT 服务卡、Snapdragon Spaces 3D 入口、各厂商快应用 / 米卡 / MagiCard 等品牌卡片生态 |

## 已实现（A 级全部 + B 级部分）

A 级对当前代码零额外动作：

- AOSP `AppWidget` 标准接收器（13 个变体）
- 三档尺寸 2×2 / 4×2 / 4×4
- `targetCellWidth/Height` 与 `resizeMode="horizontal|vertical"`
- `initialLayout` / `previewLayout` / `previewImage`
- 完整本地化 description（values / values-en / values-es / values-ja / values-zh-rCN / values-zh-rTW）
- 无数据状态引导页（`widget_create_schedule`）
- 无配置白屏（不声明 `android:configure`）
- `updatePeriodMillis=0` + `WidgetUpdateWorker` 15 min 兜底

B 级已落地：

- vivo 原子组件三件套（`vivo_widget=true` / `vivoWidgetVersion` / `vivo.widget.description`）
- 小米曝光刷新声明（`miuiWidget=true` / `miuiWidgetRefresh=exposure` / `miuiWidgetRefreshMinInterval=20000` / `miui.appwidget.action.APPWIDGET_UPDATE` / `miuiWidgetVersion=1`）
- 小米根布局要求（`@android:id/background` 兜底）
- 曝光广播复用到既有 `onUpdate`（`WidgetVendorActions.dispatchXiaomiUpdate`）

## 显式不做（B/C 级不能 APK 替代）

- vivo 原子组件平台审核
- 小米 widget.xiaomi.com 商店审核 / 推荐位 / 小爱建议商业化
- 小米 `:widgetProvider` 独立进程 ≤35M（需要架构级拆分，与 Room 直读冲突）
- OPPO Pantanal UPK / 泛在服务商务授权
- Huawei HarmonyOS NEXT 服务卡（独立生态，需新增独立 HAP 仓库）
- 荣耀 YOYO 商务白名单 / MagicOS 卡片
- 魅族 Aicy 接入（无任何公开协议）
- Snapdragon Spaces 3D 入口 / 各厂商快应用

## APK 侧交叉验证边界

这份矩阵不是把每个品牌都宣称为“已通过真机认证”。它把可由 APK 自助完成的共同契约与必须由厂商平台完成的准入分开：

- 代码注册表：`GlobalWidgetCompatibility.kt`，每个主要生态都有稳定 ID、接入等级和边界说明。
- JVM 契约：`GlobalWidgetCompatibilityTest` 锁唯一 ID、标准 AppWidget 路径、平台绑定说明和主要生态覆盖。
- Provider 契约：`WidgetInfoXmlContractTest` 锁 13 个 provider 的 manifest、尺寸、预览、resize、重配置与多语言资源。
- 官方共同依据：Android [AppWidget 概览](https://developer.android.com/develop/ui/views/appwidgets/overview)、[预览规范](https://developer.android.com/develop/ui/views/appwidgets/previews)、[AppWidgetProvider API](https://developer.android.com/reference/android/appwidget/AppWidgetProvider) 与 AOSP [Widgets and shortcuts](https://source.android.com/docs/core/display/widgets-shortcuts)。
- 仍需设备级验证：不同厂商 launcher 的具体裁剪、刷新策略、后台限制、折叠屏尺寸和私有负一屏入口；JVM 契约不能冒充真机认证。

## 公共兼容层契约

| 约束 | 来源 | 测试 |
|---|---|---|
| 全部 13 个 receiver 不声明 `android:configure` | 荣耀 #31 实测 configure 回滚 | `WidgetInfoXmlContractTest::no info xml declares android configure - add-to-home must be transparent` |
| 全部 13 个 provider `widgetFeatures=reconfigurable` | 标准 launcher 长按编辑 | `WidgetInfoXmlContractTest::every info xml declares widgetFeatures reconfigurable` |
| 全部 provider description 指向本地化完整描述 | AOSP picker / OEM picker | `WidgetInfoXmlContractTest::every provider uses its localized widget description resource` + `all shipped locales define every widget description string` |
| 全部 receiver 声明 miuiWidget 曝光刷新 | 小米 tech-spec §2 | `WidgetInfoXmlContractTest::every widget receiver declares Xiaomi widget metadata and refresh action` |
| 全部 receiver 接收 `miui.appwidget.action.APPWIDGET_UPDATE` | 小米 tech-spec §2 | 同上 |
| 初始布局根 `@android:id/background` | 小米 tech-spec §1 | `WidgetInfoXmlContractTest::bitmap widget container has Xiaomi compatible root background` |
| `application` 级 `miuiWidgetVersion>0` | 小米 tech-spec | `WidgetInfoXmlContractTest::application declares a positive Xiaomi widget version` |
| vivo 三件套 | vivo 原子组件 SDK | `WidgetInfoXmlContractTest::every widget receiver declares vivo atomic component metadata` |
| 同步 RemoteViews + `goAsync` | OPPO ColorOS Glance 冻结 / 全厂商 | `WidgetUpdaterWiringTest` |
| 同步 Receiver 入口派生 `ALL_WIDGET_VARIANTS` | 全厂商刷新广播 | `WidgetUpdaterWiringTest` |
| Pin 路由覆盖全部变体 | 全厂商 | `PinWidgetRoutingTest` |
| 无数据引导页 `widget_create_schedule` | 小米 qa-faq §9 | `WidgetBitmapLifecycleTest` |
| RemoteViews 路径只依赖受支持 view/bitmap | AOSP RemoteViews 上限 | `WidgetBitmapLifecycleTest` |
| 多语言覆盖 5 个 locale | 全球 launcher | `WidgetInfoXmlContractTest::all shipped locales define every widget description string` |
