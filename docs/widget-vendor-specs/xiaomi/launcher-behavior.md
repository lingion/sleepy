[evidence=A] 抓取 2026-09-13 · 源URL: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664

# HyperOS 桌面 Launcher 与小部件行为

## 桌面、负一屏、小部件中心三个入口

- 桌面网格：官方重新设计了桌面以承载小部件，用户可将小部件添加到桌面，也可快速拖动到负一屏。桌面支持“无字模式”（隐藏应用与小部件名称），路径：桌面双指捏合 → 左下角设置图标 → 无字模式。
- 负一屏（原智能助理）：位于桌面最左侧，桌面左滑进入，是智能服务聚合页，通过场景化快捷入口、实时提醒和自定义小部件提供服务。
- 小部件中心：入口有两个 —— 桌面双指捏合进入编辑模式后点击“小部件”；或在负一屏点击右上角“+”。支持直接拖拽小部件到桌面或负一屏；同时保留 Android 原生小部件入口（“搜索 — 安卓小部件”）。

来源：

- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1923

## 小米小部件与 Android 原生小部件互斥展示

官方 Q&A 明确：小部件中心展示的都是已通过审核正式上线的小部件；当应用上线了按小米规范开发的组件后，安卓原生组件池里就不会再显示这个小米小部件。举例：应用有 A（小米规范）、B、C（原生）三个组件，小部件中心只显示 A，原生池不显示 B、C。

仅适配安卓原生小部件不需要走小米审核。

## 桌面网格：官方页面之间的文字冲突

- 设计规范（pId=1664）原文：“桌面支持4x6和5x8布局，需保证4x6布局下完美显示，5x7布局下无显示缺陷问题（设置方式：设置—桌面—桌面布局规则）”。
- 技术规范（pId=1584）原文：“需保证桌面4x6、5x6网格体系都能正常显示（设置方式：设置—桌面—桌面布局）”。
- 审核规范（pId=1586）原文：“兼容负一屏和不同布局的桌面（4*6和5*6）……需保证4*6布局下完美显示，5*6布局下无显示缺陷问题”。

设计规范页面出现 5x8/5x7 与技术/审核规范的 5x6 不一致，官方未标注原因。工程上按 4x6 必须完美 + 5x6/5x7/5x8 尽力无缺陷处理，不做单一网格假设。

## 尺寸档位与 minWidth/minHeight

小米手机（含折叠屏）支持 2x2、4x2、4x4；小米平板支持 2x2、4x2、4x4、Pad4x2。技术建议尺寸：

| 规格 | 建议 minWidth | 建议 minHeight |
| --- | ---: | ---: |
| 2x2 | 110dp | 110dp |
| 4x2 | 300dp | 110dp |
| 4x4 | 300dp | 250dp |

`minWidth`/`minHeight` 只用于计算占用的格子数，不等于最终渲染高度。官方 Q&A 给的算法：假设桌面格子为 70x80dp，则展示高度 = `Math.ceil(110/80)*80 - paddings`，并提示不要与 `AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT` 混淆。

官方要求根布局宽高使用 `match_parent`、内容区在根布局内居中，并声明 `@android:id/background` 且根布局必须有非全透明背景色（系统据此统一加圆角、做切换动画）。

## 自适应布局的官方实践方案

《Widget适配建议及示例》给出三类方案：

1. 快捷卡片：按 `OPTION_APPWIDGET_MIN_WIDTH` 阈值（示例为 300）在两套写死尺寸的 layout 之间切换；并需在 `onAppWidgetOptionsChanged` 中记录并比对 min/max 变化。
2. 列表类卡片（示例为股票）：不给 `layout_width/height` 固定值，改用 `maxHeight`/`minHeight` 区间；示例按 1080x2340、4x2 最小 418px/最大 440px、外边距 40px、最多 3 行算出 40.97dp~43.64dp。
3. 其他：LinearLayout `layout_weight` 等分或 RelativeLayout 相对布局。

## 加载态

未配置加载态时，手机重启或桌面/负一屏重启会呈现空白卡片。官方方案是用 `android:initialLayout` 指定加载态布局，收到 update 广播后再换成实际布局。

## 曝光刷新与独立进程

- 小米 Widget 去掉了系统原有的定时刷新，改为“用户滑动到有 Widget 的页面时判定需要刷新并通知应用”。
- 默认曝光不刷新。需要时在 receiver 声明 `miuiWidgetRefresh=exposure` 与 `miuiWidgetRefreshMinInterval`（毫秒，最短 10 秒），并监听 `miui.appwidget.action.APPWIDGET_UPDATE`。
- 曝光刷新只存在于支持小米 Widget 的系统；在不支持的系统与非小米手机上，刷新机制与原生 Widget 一致。
- 小部件必须使用名为 `:widgetProvider` 的独立后台进程；不能拉起其他进程，禁止 native `fork`；技术规范写内存不能超过 35M，审核规范写“未超 40M”；Activity 不能放在 Widget 进程；Widget 进程 adj 值较高，资源不足时容易被系统回收。
- 播放器类小部件可运行在非 Widget 进程，但必须关闭曝光刷新、必须使用前台 Service，并用主动刷新更新 UI。
- 应用在前台或后台存活时，可调用 `AppWidgetManager.updateAppWidget()` 主动刷新。

## 负一屏排序与优先级

应用可通过 `updateAppWidgetOptions` 写入 `miuiWidgetEventCode` 与 `miuiWidgetTimestamp`，负一屏据此对卡片动态排序。官方事件码表包含 opening/closing（股票证券）、commuting（出行）、live1/replay（直播）、connected/disconnect、notice1、progress1、info1、state1、other。官方明确：新增或修改事件需与小米商务确认，上报未确认的 code 不生效，恶意错报被识别后会降低推荐权重。

`updateAppWidgetOptions` 必须在 `updateAppWidget()` 之前，且两者在同一线程。

## 引导添加与详情页

`requestPinAppWidget(provider, extras, null)` 携带 `addType="appWidgetDetail"` 可拉起小部件中心的应用详情页（仅 Android 8.0+），可选 `widgetName` 定位组件，`widgetExtraData` 最多携带 5 个 String 自定义参数（超过 5 个全部丢弃）。该流程不支持用户拖动添加。是否支持小米 Widget、是否支持详情页可通过 `content://com.miui.personalassistant.widget.external` 的 `isMiuiWidgetSupported` / `isMiuiWidgetDetailPageSupported` 查询。

## 大屏（折叠屏/平板）

负一屏和桌面对大屏小部件做了全局缩放，一般无需额外适配；如需自行精细适配，可在 receiver 设置 `miuiAutoScale=false` 关闭全局缩放（官方提示：使用 GridView/ListView 的小部件不能用该配置）。已适配普通手机的小部件可直接在折叠屏和平板上经桌面缩放显示。

## 负一屏的商业化形态（供理解排序生态）

负一屏商业化文档写明：负一屏月活 1 亿+，主要展示形态为各类小部件；资源位包含“小部件强插（分行业 4x2 卡片，maml 模板卡片）”“客户独占卡片”“服务直达（小爱建议版 2x2 卡片/固定 icon）”“小部件中心首页推荐卡”“负一屏底部推荐卡”等，其中部分位置标注为权益、不商业化。这说明负一屏卡片位存在商业投放，第三方自建 Widget 的自然排序与这些运营卡片共存。

来源：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1926
