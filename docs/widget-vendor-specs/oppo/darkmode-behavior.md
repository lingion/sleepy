[evidence=A] 抓取 2026-09-13 · 源URL: https://open.oppomobile.com/wiki/doc/detail?id=10658 / 10161 / 11410 (官方 API 直采)

# ColorOS 深浅色跟随(暗色模式适配)

## 官方立场(暗色模式适配指导, doc 10658)

- Android Q 提供全新暗色主题背景; OPPO "基于原生方案实现, 可以完全通过原生的适配方案处理"(官方原文)。
- Force Dark: AndroidQ 新机制, 系统底层直接对颜色和图片转换处理, "此方案对于视图的分析机制并不完美, 需要应用在此基础上进行核查和完善"。

## 两条适配路径(官方步骤)

1. 主题适配(early night mode): 用不开启 Force Dark 的主题(Theme.Material 或自定义 theme 中 `android:forceDarkAllowed=false` / `android:windowDarkAppearanceEnabled` 其一或都为 false — 此项不强制, 为推荐)。系统暗色开启后优先读取带 night 的资源目录(如 drawable-night-xxhdpi); 颜色推荐 `?attr/colorTextPressed` 替代 `@color/...`, 只需定义 values / values-night 两套 Theme 自动切换。
2. Force Dark 适配: 用 Theme.AppCompat.DayNight 或自定义主题开启; 开启后核查视图, 问题 View 用 `setForceDarkAllowed(false)` / `android:forceDarkAllowed="false"` 关闭该 View 及子 View 的 Force Dark, 再走主题适配。

## 系统开关读取(官方代码)

```java
public static boolean isNightMode(Context context) {
    Configuration configuration = context.getResources().getConfiguration();
    int currentNightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return Configuration.UI_MODE_NIGHT_YES == currentNightMode;
}
```

## 重建行为(官方注意事项)

- 暗色开关切换触发全局 Configuration 改变, 默认导致 Activity 重建, 应用需做好数据保存恢复。
- Manifest 中 Activity 配 `android:configChanges="uiMode"` 则不重建只回调 onConfigurationChanged; 但"所有资源不会重新读取, 也就是主题适配将不会刷新, 这种情况下必须应用自己处理样式上面的变化"(官方原文)。

## 测试入口与兼容性

- Android Q 原生机启用深色: 设置 > 显示 > 主题背景; 或通知栏快捷设置图块。
- Force Dark 接口和属性需做 API 29 区分; 资源目录需按情况加 v29 过滤。

## 快应用卡片侧的暗色(doc 11410, 与 AppWidget 无关但同属 ColorOS 生态)

- manifest.json `themeMode`: -1 跟随系统 / 0 固定日间 / 1 固定夜间。
- themeMode=-1 只保证自动反色, 指定色值需 onShow 里 `configuration.getThemeMode` + computed 控色。
- 组件加 `forcedark="false"` 豁免自动反色。
- 已知限制(官方 FAQ 原文): "卡片在暗色模式下, 卡片宿主中无法自动反色 — 目前卡片宿主不支持"(负一屏反色, 宿主 Demo 不反色)。

## 与已有 theme-component-dev-notes.md 的分工

已有文档记录的是 Glance widget 在 ColorOS 桌面被冻结的兼容问题(widget 层); 本档案补的是 uiMode/DayNight/Force Dark 系统行为层。两者不重叠。

## ColorOS 多级暗色(中文维基侧证, evidence=B)

中文维基 ColorOS 条目: ColorOS 11 起提供"无限息屏、多级暗色模式"(多档位暗色为 ColorOS 11 引入的用户侧特性)。此为系统设置侧描述, 对第三方 App 的行为仍以上述 Android 原生机制为准。
