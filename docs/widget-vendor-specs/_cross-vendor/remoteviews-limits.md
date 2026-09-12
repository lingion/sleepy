[evidence=B] 整理 2026-09-12 · 依据: AOSP AppWidgetServiceImpl 源码注释 (bitmap 内存 1.5× 屏幕上限) + vivo 实测异常文本 (desktop-widget-painpoints.md L281-289) + Android 官方 AppWidget 文档 (developer.android.com) + issue #31 荣耀实见。

# RemoteViews / AppWidget 跨厂商硬限制速查

## 1. bitmap 内存上限 (系统硬校验, vivo 实测抛异常)

```
RemoteViews 内全部 bitmap 总内存 ≤ 1.5 × 屏幕
  = 6 × screenW × screenH 字节 (1.5 × 4 bytes/px)
```

- 出处: AOSP `AppWidgetServiceImpl` 注释 "Cap memory usage at 1.5 times the
  size of the display"; vivo 严格校验, 超限抛
  `IllegalArgumentException: RemoteViews for widget update exceeds maximum
  bitmap memory usage`。
- 对策 (Sleepy): 渲染按 `OPTION_APPWIDGET_SIZES` 真实当前 dp, 不按 MAX 边界;
  长图 = 内容高度非屏幕整数倍叠加。`WidgetBitmapLifecycleTest` 锁。

## 2. GL 纹理上限 (启动器静默失败)

- 单张 bitmap 高/宽 > `GL_MAX_TEXTURE_SIZE` (常见 4096px) 时, 启动器侧
  GL 上传失败, 表现为**该行/该图静默不渲染** (无异常、无日志), 即
  "显示不完全"类症状的隐藏根因。
- 对策: 长图高度预算 = ceil(contentHdp × density), 高密度大屏 (平板/折叠)
  上 500dp+ 内容 × 3.5 密度即触 4096。超出风险档位应拆条带
  (`ScrollStripService` 预留横切能力) 或压缩内容档位。

## 3. 字体缩放 (fontScale) 遮挡

- 系统"显示大小/字体大小"调大后, AppWidget 内固定 dp 布局的 TextView 文字
  变宽, 可遮挡相邻控件 (cross-vendor 通用; issue #31 报告人在荣耀 2×2 实见
  "字体遮盖切换箭头")。
- 对策: 顶栏类真实视图布局按 fontScale 实测文字宽度做降级档位
  (`TodayWidget.navHeaderTier`: FULL → TWO_CHAR → SHORT_TITLE → HIDE_TODAY,
  Paint 带 fontScale 测量), 装不下的档位整体隐藏导航而非让控件互相压叠。

## 4. requestPinAppWidget 渠道矩阵 (见各厂商 dev-notes)

| 厂商 | 确认框 | 空间不足开新页 | 成功回调 |
|---|---|---|---|
| 华为 | 弹 | 否(提示不足) | **不触发** |
| 荣耀 | 弹 | 否(提示不足) | 未证实, 视为不可靠 |
| 小米 | **不弹** | 是 | 可用 |
| OPPO/三星 | 弹 | 是 | 可用 |
| vivo | **不弹** | 是 | **无 SDK+上架 = 完全无效** |
