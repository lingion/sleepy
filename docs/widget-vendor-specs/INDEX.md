# 大陆主流启动器 / 小组件厂商规范速查

> 目录目的: 把 Sleepy 小组件在大陆主要厂商启动器 (华为/荣耀/小米/OPPO/vivo/魅族/三星) 的官方能力边界与适配要求落到本地,作为后续“单组件、跨厂商兼容”适配工作的事实底座。
> 抓取时间: 2026-09-10
> 抓取原则: 优先厂商官方页 → 备份社区/CSDN/IT之家二次核实。纯 JS 渲染/登录墙的厂商页用社区内容补齐,每文件头部首行即标注证据等级。

## 适配目标澄清

Sleepy 是 Android App,只能运行 Android AppWidget 体系。下列“厂商规范”是它们在 Android AppWidget 之上额外做的 OEM 限制/扩展;Sleepy 不需要也无法参与厂商“主题商店”的上架流程(华为服务卡片 / OPPO ColorOS 主题组件 / 小米小部件审核流程等都是 HarmonyOS / ColorOS / HyperOS 独立应用生态)。

本目录的事实底座只回答一个问题:**Android AppWidget 在每家启动器上跑起来会遇到什么坑,以及为什么我们要做哪些兼容**。

## 目录

| 厂商 | 子目录 | 关键坑 |
|---|---|---|
| 小米 HyperOS | [xiaomi/](./xiaomi/) | `:widgetProvider` 独立进程 ≤35M,曝光刷新(去掉定时刷新),不支持 fork |
| 荣耀 MagicOS | [honor/](./honor/) | 锁屏小组件独立推送路径,桌面小组件走 Android 原生,主题包渠道独立;新增 appwidget-dev-notes (#31 实锤 configure 回滚修复 + pin 矩阵) |
| OPPO ColorOS | [oppo/](./oppo/) | 历史 Glance 冻结问题 (已有 memory),主题组件 SDK 是独立通道 |
| vivo OriginOS | [vivo/](./vivo/) | 原子组件基于原生 + meta-data,挂件内存/GIF 性能受限;新增 atomic-widget-dev-notes (pin 无 SDK 完全无效 + 内存上限实锤) |
| 魅族 Flyme | [meizu/](./meizu/) | 无独立厂商 SDK,走 Android 原生 |
| 三星 OneUI | [samsung/](./samsung/) | 无独立厂商 SDK,走 Android 原生;新增 appwidget-dev-notes (pin 弹框+空间不足开新页,折叠屏/外屏 cell 差异) |
| 跨厂商经验 | [_cross-vendor/](./_cross-vendor/) | CSDN 14K 实战总结 (小米/vivo/华为/OPPO 适配差异);新增 remoteviews-limits (bitmap 内存 1.5× 屏 / GL 4096 静默失败 / fontScale 遮挡 / pin 矩阵总表) |
| 华为 HarmonyOS | [huawei/](./huawei/) | 三档分界:EMUI/HarmonyOS 2-4 (安卓内核) = AppWidget 可用 AOSP 行为;NEXT 服务卡片 = 独立生态不可达 (appwidget-android-core);新增 formkit-service-cards (Form Kit 全量:postCardAction 三事件/刷新四路/form_config 字段) + launcher-behavior (桌面入口/万象小组件 8 类) + theme-darkmode (深色模式无单应用开关,跟随=app 自身策略) + dev-portal-overview (SPA 墙+DevEco/CLI 下载渠道) + next-apk-compat (NEXT 仅 HAP,卓易通容器) + gaps (文档中心 SPA 抓取缺口) |

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
