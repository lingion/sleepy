# gaps.md — 抓取失败 / 未覆盖的主题清单

[evidence=-] 记录 2026-09-13

## 1. CTS-D 对 AppWidget 行为的具体测试项

- 主题名: CTS-D (厂商定制性兼容测试) 中与 AppWidget 相关的具体 test case 清单
- 尝试过的 URL: source.android.com/docs/compatibility/cts (抓到但正文无 widget 相关条目);
  source.android.com/docs/compatibility/cdd (抓到 CF 防护/短页, 无 widget 细节);
  本地 searxNG "CDD compatibility definition app widgets home screen requirements"
- 失败原因: CTS-D 的 test case 列表在 source.android.com 未公开索引; CDD 全文是 PDF 大文件
  (android-cdd.pdf), cfp-fetch 只抓到目录页
- 建议后续渠道: 直接下载 https://source.android.com/docs/compatibility/android-cdd.pdf 后
  本地 pandoc 提取 "App Widgets" 章节; 或查 AOSP cts/tests/ 源码仓
  (android.googlesource.com/platform/cts, 需镜像 gh.qdp.qzz.io)

## 2. AOSP AppWidgetServiceImpl 完整源码落盘

- 主题名: AppWidgetServiceImpl.java 源码级行为 (bitmap 1.5× 上限实现、adj 分配、曝光逻辑基线)
- 尝试过的 URL: android.googlesource.com/platform/frameworks/base/+/master/services/appwidget/
  (搜索结果给出链接, 未抓取正文)
- 失败原因: android.googlesource.com 直连未测; git 仓建议走镜像
- 建议后续渠道: `git clone https://gh.qdp.qzz.io/https://android.googlesource.com/platform/frameworks/base`
  (大仓库, 只取 services/appwidget/ 目录) 或 raw 直链
  android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java?format=TEXT

## 3. 华为 Form Widget (HarmonyOS 卡片) 官方文档

- 主题名: developer.huawei.com 的 Form/卡片接入指南原文
- 尝试过的 URL: developer.honor.com/cn/doc/guides/100171 (荣耀, 抓到 CF 防护短页 9553B, 正文空)
- 失败原因: 华为/荣耀开发者门户是 JS 渲染 + 登录墙; cfp-fetch 拿到的是防护页
- 建议后续渠道: CSDN 荣耀官方账号文章 (blog.csdn.net/HONOR_Developer/article/details/126829344)
  作 B 级补齐; 华为侧确认 NEXT 卡片与 Android AppWidget 不互通后此维度不再需要

## 4. OPPO ColorOS 小部件开放平台文档

- 主题名: ColorOS 小部件 (主题组件) 开放平台能力边界
- 尝试过的 URL: 本地 searxNG "ColorOS 小部件 开放平台 widget 文档"
- 失败原因: search 未返回官方 open.oppomobile.com 的 widget 文档页; OPPO 小部件开放平台需要开发者登录
- 建议后续渠道: open.oppomobile.com 登录后人工导出; 或社区二手 (CSDN/掘金) B 级补齐

## 5. MIUI framework 层 AppWidget diff (私有 service 实现)

- 主题名: MIUI/HyperOS 曝光刷新、独立进程在 framework/priv-app 层的具体实现
- 尝试过的 URL: 本地 searxNG "MIUI AppWidgetService framework 修改 widget 调度"
- 失败原因: 无公开 diff; 小米未公开 frameworks/base 私有分支
- 建议后续渠道: 拆 MIUI ROM (需对应机型 payload.dumper + Python 工具链, 工作量大); 或以
  dev.mi.com 官方行为文档 (已落盘 rom-framework-mods.md) 为准, 不再深挖实现层

## 6. 荣耀/华为 pin 成功回调行为的官方口径

- 主题名: 华为/honor requestPinAppWidget 第三个参数 PendingIntent 不触发的官方说明
- 尝试过的 URL: developer.honor.com (防护页); developer.huawei.com (登录墙)
- 失败原因: 厂商不公开该 API 偏差; 只有实测记录 (appwidget-china-adapt.md / issue #31)
- 建议后续渠道: 维持 C 级实测矩阵; 华为开发者论坛人工提问确认
