[evidence=A] 抓取 2026-09-13 · 源URL: https://open.oppomobile.com/wiki/doc/detail?id=10797 / 11127 / 11314 / 11311 / 10960 / 10658 (官方 API 直采)

# ColorOS Android App 开发适配(OPPO 手机适配指南)

## 官方适配文档索引(menu 树 + ES 搜索核实)

- OPPO 手机适配指南: 微信分享SDK策略更新(doc 10797)、OPPO沉浸式状态栏适配说明(doc 10161)、暗色模式适配指导(doc 10658)、OPPO凹形屏适配说明(doc 10159)、挖孔屏适配指导(doc 10667)、OPPO日历私有数据库接入(doc 10783)、OPPO手机应用分屏适配说明(doc 10160)
- Android 适配文档: Android 12(doc 11073 总览/10960 兼容性适配/10324 开发者预览版/11015 视频), Android 13(doc 11311 兼容性适配/11421 常见问题/11314 开发者预览版), Android 11(doc 10724), Android Q(doc 10325), 折叠屏(doc 11122/11308 指导书/11127 平行视窗/11413 WebView), 平板(doc 11173/11172/11170 响应式UI框架)
- 开发者支持(menu 树): 应用审核规范 doc=12129, 升级64位架构 doc=10948, **Android 17 适配 doc=11814**, 折叠屏适配 doc=11815, 平板适配 doc=11819, 手机适配 doc=10797

## 微信分享 SDK 策略更新(doc 10797, 官方原文)

- Android 11 软件包可见性变更导致第三方应用通过 OpenSDK 拉起微信受限(仅对 targetSdkVersion=30 的应用产生影响)。
- 适配: AndroidManifest.xml 增加包可见性标签指定微信包名; 需升级编译工具(Android Studio ≥ 3.3 建议 4.0+; Build-Tools ≥ 30; gradle ≥ 3.6.0 建议 3.6.4)。
- Android 11+ 分享消息含文件路径(如图片消息)需用 FileProvider, 否则分享失败。

## OPPO 沉浸式状态栏适配(doc 10161, 官方代码)

```java
// Android 5.0 以后
Window window = activity.getWindow();
View decorView = window.getDecorView();
decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE ...);
```
(完整 flag 序列与凹形屏/挖孔屏适配见 doc 10159/10667, 本档案未全量抓取正文。)

## 折叠屏平行视窗(doc 11127, 官方原文 — OPPO 自研方案)

- 背景安卓大屏设备显示异常; OPPO 系统提供兼容模式、平行视窗等多种方案。
- 通用应用适配: AndroidManifest application 内新增 meta-data + assets 下 easygo.json。
- easygo.json 关键字段(官方原文):
  - `easyGoVersion: "1.0"`, `client: 应用包名`
  - `logicEntities[].head.function: "magicwindow"`, `head.required: "true"`(预留)
  - `body.mode`: 0=购物模式(activityPairs 不生效), 1=自定义模式(含导航栏模式)
  - `body.activityPairs.from/to`: 触发分屏的源/目标 Activity, to 可为 "*" 任意; 自定义模式 A 启动 B 触发分屏(A 左 B 右), 导航栏模式 to="*"
  - `body.defaultDualActivities.mainPages/relatedPage`: 冷启动默认双屏; mainPages 可多个分号隔开; mainPages 与 relatedPage 只能配 1 对, 需具体 Activity 名不支持通配符
  - `body.transActivities`: 过渡页面列表
  - `body.Activities[].name/defaultFullScreen/lockSide`: defaultFullScreen 默认 false; lockSide 当前仅支持锁定 primary 侧(锁定后另一侧启动新 Activity 不会轻易平推窗口过来, 除非推过来的窗口也是 primary 锁定; 典型场景: 直播购物)
  - `body.UX.supportDraggingToFullScreen` / `supportVideoFullscreen`

## Android 13 开发者预览版(doc 11314, 官方原文)

- 支持 OPPO Find X5 Pro / Find N; Find X5 Pro Android 13 DP2 基于 Google Android 13 Beta2; Find N DP1 基于 Beta1。
- "建议开发者尝鲜, 但不建议普通用户刷此版本"; 升级到开发者预览版后手机存储将被格式化。

## 与已有文档的分工

- darkmode-behavior.md: 暗色模式专项(10658) — 本档案只列索引不重复正文。
- shelf-launcher-behavior.md: 负一屏/卡片生态。
- 本档案: 其余 App 开发适配(分屏/异形屏/平行视窗/微信 SDK/预览版)。

## 抓取边界

doc 12129(新版应用审核规范)、11814(Android 17 适配)、13257/13258(智慧服务/服务接入指南)、13270/13271(流体云/设计规范)等 2023 后新增文档正文在登录态接口 `POST /oneoppoapi/doc/detail` 之后, 未能抓取 — 见 gaps.md。
