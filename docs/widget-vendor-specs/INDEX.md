# 大陆主流启动器 / 小组件厂商规范速查

> 目录目的: 把 Sleepy 小组件在大陆主要厂商启动器 (华为/荣耀/小米/OPPO/vivo/魅族/三星) 的官方能力边界与适配要求落到本地,作为后续“单组件、跨厂商兼容”适配工作的事实底座。
> 抓取时间: 2026-09-10 初版 · 2026-09-13 全量扩充 (64 文件, 7 agent 并行抓取)
> 抓取原则: 优先厂商官方页 → 备份社区/CSDN/IT之家二次核实。纯 JS 渲染/登录墙的厂商页用社区内容补齐,每文件头部首行即标注证据等级。
> SPA 破口实录: vivo=`webapi/doc/info?id=` · 魅族=`apiopen.flyme.cn/api/web/v1/doc-wiki/detail?id=` · 荣耀=Googlebot UA · 三星=静态 HTML 直抓 · 华为 developer.huawei.com=JS 墙未破(用消费者支持页+OpenHarmony 文档补)

## 适配目标澄清

Sleepy 是 Android App,只能运行 Android AppWidget 体系。下列“厂商规范”是它们在 Android AppWidget 之上额外做的 OEM 限制/扩展;Sleepy 不需要也无法参与厂商“主题商店”的上架流程(华为服务卡片 / OPPO ColorOS 主题组件 / 小米小部件审核流程等都是 HarmonyOS / ColorOS / HyperOS 独立应用生态)。

本目录的事实底座只回答一个问题:**Android AppWidget 在每家启动器上跑起来会遇到什么坑,以及为什么我们要做哪些兼容**。

## 目录

### 文件索引 (2026-09-13 全量, 每文件首行标注 evidence 等级与源URL)

| 厂商 | 文件 | 内容 |
|---|---|---|
| 华为 HarmonyOS | [huawei/appwidget-android-core.md](./huawei/appwidget-android-core.md) | EMUI/HarmonyOS 2-4 安卓内核档 AppWidget AOSP 行为 |
| | [huawei/formkit-service-cards.md](./huawei/formkit-service-cards.md) | Form Kit 全量: postCardAction 三事件/刷新四路(定时偏差30min机制)/form_config 字段 |
| | [huawei/launcher-behavior.md](./huawei/launcher-behavior.md) | 桌面入口(服务卡片→窗口小工具)/万象小组件 8 类 (evidence=B) |
| | [huawei/theme-darkmode.md](./huawei/theme-darkmode.md) | 深色模式无单应用开关, 跟随=app 自身策略 |
| | [huawei/dev-portal-overview.md](./huawei/dev-portal-overview.md) | SPA 墙现状 + DevEco/CLI 下载渠道 |
| | [huawei/next-apk-compat.md](./huawei/next-apk-compat.md) | NEXT 仅 HAP, 卓易通容器兼容 Android APK |
| | [huawei/gaps.md](./huawei/gaps.md) | 文档中心 SPA 抓取缺口 |
| 荣耀 MagicOS | [honor/dev-portal-overview.md](./honor/dev-portal-overview.md) | 门户总览 + 与华为 HMS 的边界 |
| | [honor/launcher-behavior.md](./honor/launcher-behavior.md) | 桌面/负一屏网格规格 + YOYO 建议卡片 |
| | [honor/darkmode-behavior.md](./honor/darkmode-behavior.md) | 深色 #262626 禁纯黑 + 按压反馈分模式 + 开发者双套色值路线 |
| | [honor/android16-adaptation.md](./honor/android16-adaptation.md) | MagicOS 10 / Android 16 适配要求与版本映射 |
| | [honor/appmarket-review.md](./honor/appmarket-review.md) | 应用市场上架与安全审核 |
| | [honor/appwidget-dev-notes.md](./honor/appwidget-dev-notes.md) | #31 实锤 configure 回滚修复 + pin 矩阵 |
| | [honor/lockscreen-widget-rollout.md](./honor/lockscreen-widget-rollout.md) | 锁屏小组件独立推送路径 |
| | [honor/theme-widget-user-guide.md](./honor/theme-widget-user-guide.md) | 主题包渠道 (独立生态) |
| | [honor/gaps.md](./honor/gaps.md) | 抓取缺口 |
| 小米 HyperOS | [xiaomi/tech-spec.md](./xiaomi/tech-spec.md) | `:widgetProvider` 独立进程 ≤35M/曝光刷新/禁 fork |
| | [xiaomi/design-spec.md](./xiaomi/design-spec.md) | 小米小部件设计规范 |
| | [xiaomi/qa-faq.md](./xiaomi/qa-faq.md) | 常见问题 (清数据广播时机等) |
| | [xiaomi/dev-portal-overview.md](./xiaomi/dev-portal-overview.md) | 开发者平台总览 |
| | [xiaomi/launcher-behavior.md](./xiaomi/launcher-behavior.md) | 桌面行为 |
| | [xiaomi/darkmode-behavior.md](./xiaomi/darkmode-behavior.md) | 强制深色 + 智能反色白名单体系 |
| | [xiaomi/background-restrictions.md](./xiaomi/background-restrictions.md) | 后台限制 |
| | [xiaomi/appstore-review.md](./xiaomi/appstore-review.md) | 应用商店审核 |
| | [xiaomi/gaps.md](./xiaomi/gaps.md) | 抓取缺口 |
| OPPO ColorOS | [oppo/dev-portal-overview.md](./oppo/dev-portal-overview.md) | 门户总览 |
| | [oppo/appstore-review.md](./oppo/appstore-review.md) | 应用审核规范 (10004/10071 原文) |
| | [oppo/coloros-android-app-adaptation.md](./oppo/coloros-android-app-adaptation.md) | ColorOS 对 Android App 的适配要求 |
| | [oppo/coloros-theme-component-dev.md](./oppo/coloros-theme-component-dev.md) | 主题组件 (物光引擎 11377/11378) |
| | [oppo/darkmode-behavior.md](./oppo/darkmode-behavior.md) | 深色模式行为 |
| | [oppo/oneplus-realme-diffs.md](./oppo/oneplus-realme-diffs.md) | OxygenOS/realmeUI 同源差异 |
| | [oppo/shelf-launcher-behavior.md](./oppo/shelf-launcher-behavior.md) | 负一屏/桌面行为 |
| | [oppo/theme-component-dev-notes.md](./oppo/theme-component-dev-notes.md) | 主题组件开发笔记 (历史) |
| vivo OriginOS | [vivo/dev-portal-overview.md](./vivo/dev-portal-overview.md) | 门户 + 文档树 API 全量 |
| | [vivo/launcher-behavior.md](./vivo/launcher-behavior.md) | 华容网格/原子组件全族 833-850/物理滑动/分屏小窗 |
| | [vivo/darkmode-behavior.md](./vivo/darkmode-behavior.md) | 深色 #202020/白名单/资源同名配对/预览图双套 |
| | [vivo/background-restrictions.md](./vivo/background-restrictions.md) | 公平运行内存 (四家统一 PSS/3 秒 Binder 回调) |
| | [vivo/appstore-review.md](./vivo/appstore-review.md) | 审核规范 v2026-07-28/SLA/金标认证 |
| | [vivo/theme-store-widgets.md](./vivo/theme-store-widgets.md) | 妙玩组件/蓝河卡片/原子通知 + 五条 widget 通道边界 |
| | [vivo/atomic-widget-dev-notes.md](./vivo/atomic-widget-dev-notes.md) | pin 无 SDK 完全无效 + 内存上限实锤 |
| | [vivo/desktop-widget-painpoints.md](./vivo/desktop-widget-painpoints.md) | 桌面挂件痛点 (社区, evidence=C 系) |
| | [vivo/gaps.md](./vivo/gaps.md) | 6 条 gap 详录 |
| 魅族 Flyme | [meizu/flyme-docs-index.md](./meizu/flyme-docs-index.md) | 20 篇官方 API 原文逐篇索引 (apiopen.flyme.cn 破口) |
| | [meizu/appstore-review.md](./meizu/appstore-review.md) | 审核规范/发布流程/下架/备案/年龄分级 |
| | [meizu/launcher-behavior.md](./meizu/launcher-behavior.md) | 桌面/插件/Aicy 纵览 (负一屏接受三方 AppWidget) |
| | [meizu/darkmode-behavior.md](./meizu/darkmode-behavior.md) | 深色模式跟随 (evidence=B) |
| | [meizu/flyme-open-portal.md](./meizu/flyme-open-portal.md) | 门户首页存档 (初版) |
| | [meizu/gaps.md](./meizu/gaps.md) | 4 条 gap |
| 三星 OneUI | [samsung/oneui-overview.md](./samsung/oneui-overview.md) | One UI 总览 (初版) |
| | [samsung/darkmode-behavior.md](./samsung/darkmode-behavior.md) | 唯一完整深色模式官方规范: 双向对比度 + #0072de/#3e91ff 双值 |
| | [samsung/largescreen-foldable.md](./samsung/largescreen-foldable.md) | 600/840dp 窗口分级/分栏比例表/Flex mode/cover-main 连续性 |
| | [samsung/launcher-behavior.md](./samsung/launcher-behavior.md) | One UI 7 网格变化 (4x5/5x5 移除, 三方 widget 不自动加标签) |
| | [samsung/galaxystore-review.md](./samsung/galaxystore-review.md) | Seller Portal 入驻 + 分发指南硬条款 |
| | [samsung/dex-large-screen.md](./samsung/dex-large-screen.md) | DeX: widget-only 应用 Not supported, manifest 禁声明 touchscreen |
| | [samsung/appwidget-dev-notes.md](./samsung/appwidget-dev-notes.md) | pin 弹框+空间不足开新页, 折叠屏/外屏 cell 差异 |
| | [samsung/gaps.md](./samsung/gaps.md) | 4 条 gap |
| 跨厂商 | [_cross-vendor/appwidget-core-official.md](./_cross-vendor/appwidget-core-official.md) | Android AppWidget 官方核心约束 (developer.android.com 原文) |
| | [_cross-vendor/darktheme-official.md](./_cross-vendor/darktheme-official.md) | Android 深色主题官方 (uiMode/configChanges/isSystemInDarkTheme) |
| | [_cross-vendor/remoteviews-official-limits.md](./_cross-vendor/remoteviews-official-limits.md) | RemoteViews 官方上限 |
| | [_cross-vendor/remoteviews-limits.md](./_cross-vendor/remoteviews-limits.md) | bitmap 1.5× 屏/GL 4096/fontScale/pin 矩阵总表 (社区) |
| | [_cross-vendor/background-refresh-survival.md](./_cross-vendor/background-refresh-survival.md) | 后台刷新存活 |
| | [_cross-vendor/rom-framework-mods.md](./_cross-vendor/rom-framework-mods.md) | ROM 框架魔改 |
| | [_cross-vendor/appwidget-china-adapt.md](./_cross-vendor/appwidget-china-adapt.md) | CSDN 14K 实战总结 |
| | [_cross-vendor/gaps.md](./_cross-vendor/gaps.md) | 缺口补录 |

### 厂商 × 关键坑速查

| 厂商 | 关键坑 |
|---|---|
| 小米 HyperOS | `:widgetProvider` 独立进程 ≤35M, 曝光刷新(去掉定时刷新), 不支持 fork; 强制深色白名单体系 |
| 荣耀 MagicOS | 锁屏小组件独立推送; 卡片深色禁纯黑 #262626; 开发者双套色值路线; Android 16 适配映射 |
| OPPO ColorOS | 历史 Glance 冻结 (OplusHansManager); 主题组件=物光引擎独立通道; OxygenOS/realmeUI 同源 |
| vivo OriginOS | 原子组件平台审核 (`requestPinAppWidget` 一键加桌需审核); 桌面内存 ≤10M/被动刷新 ≥12h/Bitmap 100K 大数据线; 公平运行内存 3 秒 Binder 回调 |
| 魅族 Flyme | 无 AppWidget 专属规范 (60+ 篇无); 负一屏 Aicy 纵览官方接受三方 AppWidget (超级课程表先例) |
| 三星 OneUI | One UI 7 网格 4x5/5x5 移除; 三方 widget 不自动加标签; DeX 对 widget-only 应用 Not supported; manifest 禁声明 touchscreen |
| 华为 HarmonyOS | 三档分界: EMUI/HarmonyOS 2-4 = AppWidget AOSP 行为; NEXT 服务卡片 = 独立生态不可达; NEXT 仅 HAP |
| 跨厂商 | RemoteViews bitmap 1.5× 屏 / GL 4096 静默失败 / fontScale 遮挡; 后台刷新存活; pin 矩阵总表 |

## 证据等级约定

- **A**: 厂商 developer.* 官方原文落盘,可被搜索引擎二次检索。
- **B**: 厂商域名搜索摘要 + 社区二次核实,正文来自非官方但细节与官方一致。
- **C**: 仅社区博客/IT 之家/掘金,需后续从厂商源头二次确认。

落到本地文件时,每个文件头部首行即标注 `[evidence=A/B/C]` 与抓取时间。

## 不在适配范围: 华为 HarmonyOS 服务卡片 (Form Widget)

- 体系: HarmonyOS ArkTS 卡片,使用 ArkUI 声明式 UI + Form Extension
- 入口: 华为开发者联盟 developer.huawei.com(本次抓取被 JS 渲染/登录墙挡住,CFP 拿到 CF 防护页)
- **结论**: HarmonyOS 服务卡片是 HarmonyOS 原生应用生态,与 Android AppWidget 不互通。Sleepy 作为 Android APK 无法发布到 HarmonyOS 服务卡片目录;HarmonyOS NEXT 上对 Android App 的兼容由华为自己保障。
- 后续如需支持 HarmonyOS 原生应用 (Stage 模型),需要新增独立的 HarmonyOS 工程 + ArkTS 卡片代码,与本仓库 Android 侧是两个仓库。这是单独的工作量,不在本次“跨厂商兼容 Android AppWidget”目标里。

## 已落地公共层规则 vs 厂商平台专属能力（2026-09-11 对照）

Sleepy 的适配原则：**一套 widget、一份 Android AppWidget 公共层代码，规则对全部厂商同时成立**。
下表是逐条核对文档后落地的对照 — 左列已由代码实现并用测试锁死，右列明确不做伪装
（接入厂商专属通道必须走该厂商的平台审核，代码层无法单方面实现）。

| 约束 | 来源厂商/文档 | Sleepy 实现 | 测试锚点 |
|---|---|---|---|
| 添加时不弹强制配置页（OEM 丢弃 configure Activity → 取消回滚） | 荣耀 MagicOS (#31 实测) | 全部 10 个 info XML 不声明 `android:configure`，保留 `reconfigurable` + 应用内编辑 | `WidgetInfoXmlContractTest` |
| 同步刷新先于冻结窗口 | OPPO ColorOS（OplusHansManager 冻 Glance 异步 Session） | 全部变体走同步 RemoteViews AppWidgetProvider（`goAsync` + `updateAppWidget`），无 Glance | `WidgetUpdaterWiringTest` |
| 刷新广播覆盖全部变体 | 全厂商 | `ALL_WIDGET_VARIANTS` 单一事实来源派生广播列表 | `WidgetUpdaterWiringTest` |
| Pin 入口覆盖全部变体（含历史 adb key 兼容） | 全厂商（HyperOS 无审核时不弹商店走标准 pin） | `PinWidgetRouting.resolveClass` 派生自 `ALL_WIDGET_VARIANTS`，未知/null 回落 WeekGrid | `PinWidgetRoutingTest` |
| 清数据/无数据 → 默认视图 | 小米 tech-spec §9（清数据广播时机） | 渲染器 `hasTable=false` 画 `widget_create_schedule` 引导页，不崩溃不空白 | `WidgetBitmapLifecycleTest` / 各渲染单测 |
| RemoteViews bitmap 上限（≤1.5× 屏幕） | vivo painpoints（Binder 传输上限实测） | 渲染按 `computeSizeDp` 真实 dp 画，条带 48dp 横切；长图高度=内容高度非屏幕整数倍叠加 | `WidgetBitmapLifecycleTest` |
| 曝光刷新声明 `miuiWidget*` + `miui.appwidget.action.APPWIDGET_UPDATE` | 小米 tech-spec §2 | **暂不落地** — HyperOS「去掉定时刷新」前提是接入小米 Widget 审核（`requestPinAppWidget(addType=appWidgetDetail)` 走商店详情页）；Sleepy 未上架小米商店，未审核状态下 meta-data 无效。走标准 Android 路径（WorkManager 15min 兜底）在小米上行为正确 | — |
| `:widgetProvider` 独立进程 ≤35M、禁 fork | 小米 tech-spec §1 | **不做** — 独立进程会隔离 `SleepyApp.repository`（receiver 渲染链直读 Room），需要架构级拆分；且该规范是小米商店上架审核项，非公共层正确性问题 | — |
| vivo 原子组件 meta-data / 华为服务卡片 | vivo / HarmonyOS | **平台层，不做伪装** — 必须接 vivo 原子组件 SDK + 平台审核；HarmonyOS 卡片是独立生态（见上） | — |

> 小米曝光刷新与独立进程是「上架小米 Widget 商店」的审核要求。Sleepy 当前分发渠道是
> GitHub/Gitee APK 侧载，不经商店审核，未声明这两项在小米上等价于标准 Android widget
> 行为（系统定时刷新被去后由 WorkManager 兜底）— 若未来上架小米商店再按 tech-spec §1/§2 补。

## 配套使用

本目录不参与 build (位于 `docs/`)。源码适配原则落到 `app/src/main/java/com/lingion/sleepy/widget/WidgetUpdater.kt` 顶部 KDoc;关键不变量在 `app/src/test/java/com/lingion/sleepy/widget/` 锁定。
