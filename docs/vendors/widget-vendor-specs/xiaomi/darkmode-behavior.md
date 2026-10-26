[evidence=A] 抓取 2026-09-13 · 源URL: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1595

# MIUI/HyperOS 深浅色跟随

官方页：《深色模式适配说明》（更新时间 2024-09-24），位于 HyperOS 文档中心 → 应用开发 → 系统适配 → 功能适配。

## 系统行为

用户在 MIUI 有两个入口打开深色模式：设置—显示—深色模式，以及下拉控制中心。开启后全局变黑。

对第三方 App 的处理规则（官方原文）：“对于适配深色模式的应用来说，我们会优先启用应用的深色模式。未接入的应用，则会通过算法进行反色。” 用户如果不希望某个应用被反色，可以手动关闭该应用的反色功能。

MIUI12 起推出全局深色模式，用户开启深色模式后默认开启全局反色。

## 小米官方建议的开关设计

- 务必提供“跟随系统深色模式”的选项；可默认跟随系统，或在监测到系统切换时提示用户。
- 可以额外提供手动切换开关。

## 两条适配路径

1. 适配深色模式资源：DayNight 主题 + `-night` 资源目录。优点是所有安卓版本、所有厂商都能用，体验好。
2. 适配全局反色（Force Dark）：优点是工作量小；官方列出的缺点是仅安卓 Q 手机可用、复杂页面难度大、非原生 WebView 无法反色、图片无法反色。

官方推荐的组合做法：整个应用主题使用 DayNight 并设 `<item name="android:forceDarkAllowed">false</item>`；已支持深色主题或用其他方法实现深色的页面务必禁用全局反色（否则与已有深色效果冲突）；确实需要反色的页面使用 Light 主题并设 `forceDarkAllowed=true`；其余页面单独适配。

## 强制反色的生效条件（官方原文整理）

全局反色生效需同时满足：系统开启深色模式；Activity 对应主题是 Light 的（Dark/DayNight 主题不生效）；控件允许反色。

- 控件未声明 `forceDarkAllowed`：是否生效取决于全局反色开关是否开启。
- 声明 `forceDarkAllowed=false`：无论如何都不生效。
- 声明 `forceDarkAllowed=true`：只要开启深色模式，全局反色就生效。
- 覆盖原则：子 View 覆盖父 View，Activity 覆盖应用级声明。可在主题 xml 静态声明，也可 `View.setForceDarkAllowed()` 动态设置（动态优先）。

接入 Force Dark 的前置：`compileSdkVersion` 设为 29，否则编译失败；官方写明 `targetSdkVersion` 貌似没有要求。

## 禁用 MIUI 强制反色的官方方法

官方 FAQ 第 3 条给出：应用自己的深色模式适配完成后、或适配阶段想要测试时，在 AndroidManifest 配置 meta-data 关闭 MIUI 强制反色，即可显示应用自己的深色效果。

```xml
<meta-data android:name="force_dark_google" android:value="true" />
```

这是官方文档明确写出的“让 MIUI 不要反色我的 App”的开关，区别于 Android 原生 `forceDarkAllowed`。

## 资源应用顺序

切换到深色模式时资源查找顺序（官方原文）：应用提供的 `-night` 资源 → 应用设置的默认资源 → 系统默认深色资源 → 系统默认亮色资源。应用没用 DayNight 主题时，只会变化应用自己提供的 `-night` 深色资源，其他不变化，系统也不会应用标准控件的默认深色资源。

## 官方列出的已知坑

- 全局反色只是实验性功能，存在很多 bug，不可过于依赖。
- 不会反色图片。
- WebView/Flutter 支持不完善；页面含 Flutter、WebView 或亮色图片时，官方建议改用深色模式资源适配。
- 需要监听系统 UI Mode 变化时，manifest 中 activity 需设 `android:configChanges="uiMode"` 并重写 `onConfigurationChanged`（声明后系统不重建 Activity）。
- 可用 `adb shell cmd uimode night <auto|yes|no>` 切换（Android O 起支持 adb）。

## 小部件的特殊限制（对 Sleepy 最关键）

- 技术规范第 12.5 条原文：“小米Widget 只能在xml中静态适配深色模式（通过配置drawable-night、values-night等资源文件适配），不支持在RemoteViews通过代码动态设置深色模式。”
- Q&A 解释了原因：老版本桌面切换深色模式时会有 options 变化回调；新版本考虑到 options 回调会拉起三方进程，改为宿主（桌面/负一屏）缓存 RemoteViews，切换时用上一次 RemoteViews 重建 widget。该方案要求只能 xml 静态适配。
- 设计规范将“支持深色模式”列为必须项：未适配也要保证切深色后显示和功能正常。
- 审核规范要求小部件在浅色和深色模式下均能完美显示；若应用适配了深色模式，预览图需同时提供深色版本。
- 圆角与字色在 Dark 下有对应规范（如标题透明度为 40% 的白）。

## 联系方式（官方页给出）

商务合作 liushuo3@xiaomi.com；研发问题 darkmode@xiaomi.com。

## Sleepy 验收要点

- 深色开关切换前后 Widget 内容完整，不依赖 RemoteViews 运行时改色。
- `drawable-night`/`values-night` 与默认资源成对存在。
- 含 WebView、图片的页面单独检查，不以 Force Dark 成功为结论。
- 应用已自带深色时，评估是否需要 `force_dark_google` 与 `forceDarkAllowed=false` 双保险，避免二次反色。
