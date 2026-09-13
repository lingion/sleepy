[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.honor.com/cn/docs/11100/guides/ 通用适配指导 (kitId=11100) · https://developer.honor.com/cn/docs/adaptation_guide/guides/android_16_compatibility_adaptation_guide · https://developer.honor.com/cn/docs/adaptation_guide/guides/android_17_compatibility_adaptation_guide

# MagicOS 10 / Android 16 适配要求与版本映射

来源: 荣耀开发者服务平台《适配/设计规范》系列 (适配指导 kit)。

## MagicOS 版本映射表 (官方通用适配指导原文)

| 宣传版本 | Android | API level | Build.VERSION.MAGIC_SDK_INT |
|---|---|---|---|
| MagicUI 4.0 | Q | 29 | 不存在 |
| MagicUI 5.0 | R | 30 | 不存在 |
| MagicUI 6.0 | S | 31 | 33 |
| MagicOS 7.0 | S | 31 | 35 |
| MagicOS 7.1 | T | 33 | 36 |
| MagicOS 7.2 | T | 33 | 37 |
| MagicOS 8.0 | U | 34 | 38 |
| MagicOS 8.0.1 | U | 34 | 39 |
| MagicOS 9.0 | V | 35 | 40 |
| MagicOS 9.0.1 | V | 35 | 41 |
| **MagicOS 10.0** | **W** | **36** | **42** |

- MagicOS 10.0.0.150+ 为锁屏小组件版本； #31 实测 MagicOS 10.0.0.170 = Android 16， 与表中 W=API36 一致。
- 设备判定: `Build.MANUFACTURER.equalsIgnoreCase("HONOR")`。
- 折叠屏判定: `hasSystemFeature("com.hihonor.hardware.sensor.posture")` — feature 命名空间 `com.hihonor.*` (非华为 `com.huawei.*`)。
- MagicOS 版本判定: SDK_INT>=31 时读 `com.hihonor.android.os.Build.VERSION.MAGIC_SDK_INT`； MagicOS 6.0 以下按 Android 版本号推断。

## Android 16 对所有应用的变更 (荣耀官方指南)

- **锁屏/通话挂断后 MediaProjection 自动断开**: 投屏/录屏需实现 `MediaProjection.Callback.onStop()`， 释放资源并更新界面 (WidgetRenderActivity 等录屏路径相关)。
- **JobScheduler 配额收紧**: 待机分桶运行时配额变更(Exempted 从无限制改为每 20 分钟运行 10 分钟等)， 顶部状态启动的作业、 与前台服务并行的作业均遵循配额； 影响 WorkManager/JobScheduler/DownloadManager。验证: `adb shell am set-standby-bucket <pkg> active|working_set|frequent|rare|restricted`； 调试停止原因用 `WorkInfo.getStopReason()`。
- **非 exported 组件的 PACKAGE_CHANGED 广播不再发给其他应用**(仅系统和自身应用)； 有监听其他应用组件变化的业务在 Android 16 上失效。
- **有序广播优先级不再跨进程全局生效**， 且被限制在 (SYSTEM_LOW_PRIORITY+1, SYSTEM_HIGH_PRIORITY-1)； 跨进程协调需换通道。Sleepy 的 widget 刷新广播若依赖跨进程 priority 需注意。
- **ART 内部变更**: `Class.iFields/sFields` 合并为 `fields`， 反射访问会 NPE； 依赖 ART 内部结构的库(HiddenApiBypass、 v2025.0224.1629 之前的 FlyCore)可能间歇性崩溃， 需升级。

## targetSdk 36 (Android 16) 行为变更

- **大屏自适应强制**: sw600dp 以上屏幕忽略 `screenOrientation`/`resizableActivity`/`minAspectRatio`/`maxAspectRatio`/`setRequestedOrientation()`； 应用填满窗口， 不再 pillarboxing。临时豁免: `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (仅过渡用， targetSdk 37 时该豁免失效)。游戏类 (`android:appCategory="game"`) 豁免。
- **edge-to-edge 强制**: `windowOptOutEdgeToEdgeEnforcement` 在 targetSdk 36 失效， 无法退出全屏布局； 必须处理系统栏 Insets。
- **预测性返回默认生效**: `onBackPressed`/`KEYCODE_BACK` 监听失效； 迁移 AndroidX `OnBackPressedCallback`(需 androidx.activity 1.6.0+)， 或临时 `android:enableOnBackInvokedCallback="false"`。
- **hideSoftInputFromWindow 恒返回 true**: 改用 `WindowInsetsController#hide()` + `View.OnApplyWindowInsetsListener` 监听键盘状态。

## Android 17 (targetSdk 37) 前瞻要点 (荣耀官方指南已发布)

荣耀已发布 Android 17 兼容性指南与 Beta 3 预览计划。对 widget/普通应用影响最大的条目:

- **安卓开发者认证 (ADV)**: 海外 GMS 设备强制， 未注册 Google ADV 的 APK 无法安装； 国内设备无影响。所有 APK(含不上架 Play 的)都需在开发者账号下备案包名+签名。
- **后台音频强化**: 后台音频 API 静默拦截/失败； targetSdk 37 前台服务需 WIU 功能。验证: `adb shell cmd audio set-enable-hardening enable|disable|throw`。
- **文件操作模式严格校验**: `ParcelFileDescriptor.parseMode` 白名单制， "rwa"/"ra"/"rt" 等矛盾模式抛 IllegalArgumentException (本仓 dataSync/文件路径相关)。
- **线程优先级越界抛异常**: `Process.setThreadPriority()` 传入 [-20,19] 外的值直接抛异常(此前静默截断)。
- **Parcel 回收后读取抛 BadParcelableException**； Parcel 校验增强(writeToParcel/readFromParcel 不对称即崩)。
- **禁止加载可写 so 文件** (targetSdk 37): `System.load()` 前确保 so 只读。
- **新增本地网络权限** `android.permission.ACCESS_LOCAL_NETWORK` 与回环权限 `android.permission.USE_LOOPBACK_INTERFACE` (targetSdk 37； 双向同意)。
- **证书透明度默认启用** (targetSdk 37)： 无 SCT 的证书 HTTPS 握手失败。
- **Activity 安全性增强**： `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` 废弃， 换三个新常量； `IntentSender.sendIntent()` 不再自动豁免 BAL 检查 — **requestPinAppWidget 的 PendingIntent 路径需回归测试**。
- **大屏自适应豁免失效**： Android 16 的 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` 在 17 失效。
- **静态 final 字段禁改**： 反射抛 IllegalAccessException， JNI 直接进程崩溃。
- **MessageQueue 无锁化**： 反射 `mMessages` 恒为 null； 可 `adb shell am compat enable/disable USE_NEW_MESSAGEQUEUE <pkg>` 切换验证。
- **WebView 139+ IME Inset**: 键盘弹出改变可视视口触发 resize， 可能清焦点导致键盘弹出后自动隐藏； opt-out 用 `setOnApplyWindowInsetsListener` 消费 insets。与 webview 版本绑定， Android 16 及以下升级 webview 也会触发。

## targetSdk 要求事实

- Android 15 起 targetSdkVersion < 24 的应用无法安装 (荣耀指南引 Google 规则)； `adb install --bypass-low-target-sdk-block` 可测试。
- Android 17 起 ADV 海外强制； 国内荣耀设备 (无 GMS) 不受影响。
- 荣耀官方未公布对上架荣耀应用市场的 APK 的 targetSdk 强制下限 (与华为"上架需 targetSdk>=XX"的条款对应物未找到， 见 gaps.md)。
