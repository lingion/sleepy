[evidence=A/B] 抓取 2026-09-13 · 源URL: https://source.android.com/docs/core/display/widgets-shortcuts, https://source.android.com/docs/core/display/material, https://source.android.com/docs/core/display/conv-notifications, https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584, https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1591, https://android.googlesource.com/platform/frameworks/base/+/master/services/appwidget/

# 国内厂商对 AppWidget 公共层的修改面 (launcher / framework 层)

> 维度: 各家 ROM 对 AOSP framework 层 AppWidgetService/AppWidgetManager 的已知修改, 以及
> launcher 侧(宿主)对 widget 行为的公共篡改面。证据等级: A=官方原文落盘, B=摘要+社区核实。

## AOSP 基线 (source.android.com, A 级)

- Android 8.0 引入 requestPinAppWidget 新流: 应用从 app 内添加 widget, 替代旧广播方式; launcher 必须实现带
  `android.content.pm.action.CONFIRM_PIN_APPWIDGET` / `CONFIRM_PIN_SHORTCUT` filter 的确认 Activity,
  确认后带新 widget ID 添加到主屏; 设备实现者以 Launcher3 为参考。
- Material You 文档要求 OEM 支持 Android 12 的 widget 布局/尺寸/软件参数 API (含圆角尺寸),
  实现应让 widget 可调整、可配置; 动态颜色按 AOSP 提取逻辑 (单源色→5 调色板×13 色阶→65 API)。
- CTS/D 层: CTS-D 是厂商可定制性限制的测试套; 站点 NEWS 引用 Android Police 的 CTS-D 解读,
  说明厂商对 AppWidget 行为的偏离理论上受 CTS-D 约束 (细节未落盘, 见 gaps.md)。

## 小米 HyperOS/MIUI (A 级, dev.mi.com 官方原文 2024-10-17)

小米是对 AppWidget 公共层修改面最大、且唯一给出官方公开文档的国产厂商:

1. **独立进程强制** (tech-spec §1): 通过审核的 Widget 必须在 `:widgetProvider` 进程运行
   (receiver/service/provider 均须声明 `android:process=":widgetProvider"`); 进程内存 ≤35M
   (`dumpsys meminfo` 可查); 禁止 fork 拉起其他进程; 只能跑内容准备与刷新逻辑; Activity 不得进该进程。
   adj 值较高, 资源紧张时易被系统回收。
2. **去掉系统定时刷新 + 曝光刷新** (tech-spec §2): 声明 `miuiWidget=true` 后, 系统 updatePeriodMillis
   定时刷新被去掉, 改为"用户滑到 widget 所在屏才触发一次刷新"; 需 meta-data
   `miuiWidgetRefresh=exposure` + `miuiWidgetRefreshMinInterval` (最短 10 秒) 申请,
   intent-filter 加 `miui.appwidget.action.APPWIDGET_UPDATE` 并在 onReceive 里分派。
3. **深色模式静态化** (tech-spec §12.5 + Q&A): 只能在 xml 用 `drawable-night`/`values-night` 静态适配;
   不支持在 RemoteViews 用代码动态设置。新版桌面切换深色时不再有 options 回调 (避免拉起三方进程),
   宿主缓存 RemoteViews 并用上一次的 RemoteViews 重建 widget。
4. **配置迁移时机新增** (tech-spec §5): 除系统 onRestored 外, 新增 `miuiIdChanged`/`miuiOldIds`/
   `miuiNewIds`/`miuiIdChangedComplete` options 键, 在 onAppWidgetOptionsChanged 里处理。
5. **尺寸仅三档** (tech-spec §3): 支持 2x2/4x2/4x4, minWidth/minHeight 按 110/110、300/110、300/250dp 配置。
6. **receiver 类名禁改 + 禁移除 miuiWidget 标识** (tech-spec §12.6/12.7): 改名=旧版添加的 widget 升级后消失。
7. **审核门禁** (Q&A): 中高端机型且 MIUI 13+ 才有 HyperOS widget; 未过审核的 widget 不出现在小部件中心,
   且安卓原生组件池里不再显示同名 widget (上了小米小部件中心的会被隐藏)。
8. **格子计算澄清** (Q&A): xml 里 minHeight 只用来算 Y 轴格子数, 与最终展示高度无直接关系;
   最终高度 = ceil(minHeight/cell)*cell − paddings, 不要与 OPTION_APPWIDGET_MIN_HEIGHT 混淆。

## framework 层魔改 (B 级, 社区核实)

- 小米 /others 对 framework 的具体改动没有公开 diff; 已知行为面 (曝光刷新替代定时刷新、独立进程、
  options 回调移除) 均通过 launcher 侧+私有 service 实现, 不经 AOSP AppWidgetService 的公开 API。
- OPPO ColorOS: 无官方公开 AppWidget 层文档; 已知面=OplusHansManager 对异步组件 (Glance SessionWorker)
  的冻结行为 (见 oppo/ 目录与 Sleepy memory), 以及 launcher 侧对 requestPinAppWidget 的确认框实现。
- 华为 EMUI/HarmonyOS: 无 AppWidget 层公开文档; 后台限制面在 HwPFWService/PowerGenie (省电层, 见
  background-refresh-survival.md), 不在 AppWidgetService 本身。
- vivo OriginOS: 原子组件是 AppWidget 之上的 meta-data 扩展 (vivo 原子组件 SDK + 平台审核),
  未接入时 requestPinAppWidget 完全无效 (见 appwidget-china-adapt.md 矩阵)。
- 荣耀 MagicOS: 桌面 widget 走 Android 原生路径; 锁屏小组件走独立推送路径 (见 honor/ 目录)。

## 与 AOSP 基线的对照结论

- AOSP 要求 launcher 支持 CONFIRM_PIN_APPWIDGET 确认流; 实测国内厂商确认框行为四分五裂
  (华为不触发成功回调、小米/vivo 不弹框、vivo 无 SDK 时无效) — 见 appwidget-china-adapt.md。
- AOSP 要求支持 OPTION_APPWIDGET_SIZES 精确尺寸; 小米官方只配三档尺寸并明确 minHeight 只是格子数计算
  参数 — 宿主实现各自为政, 精确尺寸路径在国内 launcher 上不可依赖。
- 结论: AppWidgetService 公开 API 层未发现厂商公开 diff; 篡改面集中在 (a) launcher 宿主行为
  (pin/确认框/格子计算/曝光触发) 与 (b) 省电层服务 (进程冻结/wakelock 强杀)。
