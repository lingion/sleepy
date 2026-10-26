[evidence=C] 抓取 2026-09-13 · 源URL: https://zh.wikipedia.org/wiki/鴻蒙作業系統5 ; https://cloud.tencent.com/developer/article/2486636 ; https://blog.soarli.top/archives/996.html

# HarmonyOS NEXT 对 Android APK 的兼容现状

## 分界（社区与百科一致，华为官方声明存目见 gaps.md）

| 系统 | APK 支持 |
|---|---|
| EMUI 全系（Android 内核） | 支持，标准 Android 安装通道 |
| HarmonyOS 2–4 / 4.2 及更早（双框架，OpenHarmony + AOSP） | 支持 APK；社区实测需关闭“纯净模式/增强防护”并允许外部来源应用下载 |
| HarmonyOS NEXT（鸿蒙 5.0/6.x，纯血鸿蒙，去 Linux 内核） | 不支持 APK 直接安装；仅支持 HAP 原生应用安装格式 |

维基百科对 HarmonyOS NEXT 的表述：使用自主设计的鸿蒙微内核、去除 Linux 内核，仅支持鸿蒙系统原生应用程序；该版本不属于 Android，不可与 Android 应用程序兼容，但用户可通过第三方基于容器虚拟化技术的兼容框架（卓易通、出境易等）下载并运行 Android 应用。

腾讯云社区文章（2025-01）：HarmonyOS 4.2(含) 之前是 OpenHarmony + AOSP，可安装 APK；HarmonyOS Next 之后不再支持安装 APK 文件，只允许安装 HAP 文件，同时不再搭载 Linux 内核。

## NEXT 上的 APK 侧载现状（社区路径，非华为官方承诺）

soarli 博客（2026-04）描述的两条路径：

- 应用市场路径：HarmonyOS NEXT 的应用市场中，尚未推出鸿蒙原生版的应用会提示“由卓易通提供服务”；安装后系统自动配置兼容环境。
- 第三方 APK 路径：先安装“卓易通”App，再点击 APK 文件选择“使用卓易通打开”完成安装。

卓易通的社区技术描述（同文，属第三方分析）：非传统虚拟机，采用华为自研 iSulad 容器技术，共享内核、环境隔离，在鸿蒙内核上拉起精简安卓运行时；APK 运行在独立沙箱，权限经二次过滤；内置黑名单/引导机制，市场已有鸿蒙原生版时优先引导原生版。

该文同时给出原生 HAP 与卓易通 APK 的体验对比（性能/功耗/系统融合），属于社区观点。

## 对 Sleepy 的含义

- HarmonyOS 2-4（档位 2）设备：Sleepy APK 仍走标准 Android AppWidget 通道（见 appwidget-android-core.md 的三档分界）。
- HarmonyOS NEXT（档位 3）设备：Sleepy APK 无法安装或只能经卓易通容器间接运行；容器内 Android AppWidget 与华为桌面的对接行为没有官方文档支持，社区亦无可靠实测记录，不做适配假设。
- 华为官方对“NEXT 不兼容 APK”的正式文字声明未在可抓取页面中获得（developer.huawei.com 文档中心为 SPA 墙，consumer.huawei.com 的 NEXT 页只做营销性表述如“从源头就纯净……不满足安全要求的应用就无法上架、安装和运行”，未直接点名 APK）。

## 证据等级说明

本文件主体为 C 级（百科 + 社区技术博客），用于定性“NEXT 与 APK 不兼容、卓易通为兼容通道”这一社区共识；不作为华为官方协议依据。任何“卓易通内 AppWidget 可用/不可用”的结论都需要真机验证，当前为零证据。

## 来源

- https://zh.wikipedia.org/wiki/鴻蒙作業系統5 （HAP 安装格式、微内核、去 Linux 内核、卓易通/出境易兼容框架）
- https://cloud.tencent.com/developer/article/2486636 （4.2 分界、APK/HAP、内核变化）
- https://blog.soarli.top/archives/996.html （卓易通安装路径与容器原理，第三方分析）
- 华为官方 NEXT 消费者页（营销表述，无 APK 条款）：https://consumer.huawei.com/cn/harmonyos-next/
