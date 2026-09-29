# WakeUp 课程表：定制 ROM 可靠性逆向方案

日期：2026-09-24
范围：WakeUp v6.1.06 反编译工程、Sleepy 当前 Android 实现、已落盘厂商公开资料
证据分级：A=厂商官方文档或本地源码直接证据；B=开源实现/反编译交叉证据；C=待真机或准入验证

## 结论

WakeUp 对定制 ROM 的核心策略不是依赖一套“通用保活 API”，而是四层组合：

1. 使用 Android 公共调度和系统组件承载课程提醒与小组件刷新。
2. 对 Android 6+ 暴露“忽略电池优化”入口，对所有设备引导用户进入应用详情页开启后台运行/自启动。
3. 对 Android 12+/31+ 在启用课程提醒前检查通知权限和精确闹钟权限，不满足时拒绝打开提醒。
4. 对厂商桌面扩展单独声明组件和 metadata：MIUI/vivo widget 刷新、荣耀 Suggestions Kit、华为/厂商 SDK；能力不足时仍保留标准 AppWidget/普通通知。

这解释了其跨 ROM 的可靠性边界：系统调度可提高一致性，用户授权和厂商白名单决定实际后台存活；不应把私有 extras 或桌面 metadata 当作保活替代品。

## 分层布局

```text
课程数据 / 当前节次
        |
        +--> 公共调度层: AlarmManager / WorkManager / DATE_CHANGED
        |        |
        |        +--> 普通通知（所有 ROM 的可靠回退）
        |        +--> AppWidget（桌面刷新，受 launcher/ROM 策略影响）
        |        +--> 前台服务（仅用户主动的进行中课程窗口）
        |
        +--> 厂商增强层（能力探测 + 白名单/开关通过才发送）
                 |
                 +--> Xiaomi Focus / vivo SuperX / Flyme extras
                 +--> Android 16 Live Updates（公共 API）
                 +--> Huawei/HONOR/OPPO 私有平台（需准入，未准入不发送）

权限与生命周期诊断横切全部层：
通知权限 · 精确闹钟 · 电池优化白名单 · 自启动/后台限制 · widget 绑定 · 进程/任务恢复
```

## WakeUp 直接证据

### 1. 用户引导和电池优化

`wakeup-android-project/app/src/main/java/com/suda/yzune/wakeupschedule/settings/SettingsActivity.java:303-314,416-445`：

- 设置页明确提供“后台运行”分组和“自动启动”入口。
- 自动启动点击只打开 `android.settings.APPLICATION_DETAILS_SETTINGS`，并提示用户到系统设置允许后台运行和自启。
- Android 6+ 检查 `PowerManager.isIgnoringBatteryOptimizations(packageName)`。
- 在 manifest 声明 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 且系统 Intent 可解析时显示入口。
- 用户点击后打开 `android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；失败只提示用户手动设置。

这是一条合规的用户授权引导，不是静默绕过省电策略。

### 2. 精确提醒前置闸

`AdvancedSettingsActivity.java:436-450` 和 `TodayCourseAppWidget.java:349-350`：

- Android 12+/API 31 检查 `AlarmManager.canScheduleExactAlarms()`。
- 通知权限和精确闹钟权限不满足时，不开启课程提醒并提示用户去设置。
- Widget 更新时也复核通知/精确闹钟状态，已开启提醒但权限失效会被关闭。

### 3. 日期恢复与 widget 更新

`BaseApplication.java:116-128,538-544`：

- 动态注册 `DATE_CHANGED`、`TIME_SET` receiver。
- 收到日期/时间变化后刷新 AppWidget，并调用应用内部刷新逻辑。
- 这类动态 receiver 只覆盖进程存活时的变化；它不能替代系统持久化闹钟或解决进程被杀后的恢复。

### 4. MIUI/vivo widget 元数据

`wakeup_decoded/AndroidManifest.xml:247-270`：

- 为 MIUI/vivo 专用今日 widget 声明 `miui.appwidget.action.APPWIDGET_UPDATE`。
- 声明 `miuiWidget=true`、`miuiWidgetRefresh=exposure`、`miuiWidgetRefreshMinInterval=10000`、`vivo_widget=true` 等 metadata。
- 使用独立 `:widgetProvider` 进程。

这证明 WakeUp 针对厂商桌面刷新做过适配；未证明这些 metadata 能使课程提醒在后台无限运行。独立进程也可能带来数据库初始化、进程间状态和版本兼容成本。

### 5. 厂商 ContentProvider 是“被系统读取”的数据面，不是保活面

`ScheduleContentProvider.java:213-223` 对调用包做白名单校验，允许的调用方包括：

- 荣耀卡片/Quick Engine：`com.wakeup.schedule.card`、`com.wakeup.schedule.honorcard`、`com.hihonor.quickengine`；
- ColorOS/OPPO：`com.coloros.assistantscreen`、`com.oplus.metis`、`com.oplus.pantanal.ums`、`com.coloros.sceneservice`、`com.heytap.speechassist`；
- vivo：`com.vivo.aiengine`；
- WakeUp 自身：`com.suda.yzune.wakeupschedule`。

该 Provider 后续按 URI 返回课表、下一节课等数据。它解决的是“系统卡片如何取数据”，调用方仍需要自己决定何时查询；因此不能把 Provider 白名单或导出属性当成后台执行保证。Sleepy 如果未来提供类似数据面，必须同时做调用方鉴权、最小字段返回和版本化 URI，不能直接复制导出 Provider。

### 6. 荣耀 Suggestions / vivo Suggestion 是反馈链，不是课程提醒链

`OooOo.java:21-37` 先检查荣耀 Suggestions Kit 的 `FEEDBACK_PLAN` feature，再以 `WorkManager` 提交带 `honor_feedback` tag 的 `HonorSuggestionWorker`；`o0000.java:55-65` 对 vivo 做同类 feature 检查，并提交 `vivo_feedback` worker。两条链的输入是计划反馈/建议数据，不是课程通知调度，也没有启动 Activity、前台服务或闹钟的逻辑。

因此：

- Manifest 中的 `com.hihonor.hcs.client.appid`、`com.hihonor.intelligence.suggestion-kit.enableFlag` 只能证明集成了荣耀建议能力；
- `com.vivo.aiengine` 权限只能证明接入过 vivo 能力；
- 不能据此推导“荣耀/ vivo 会替应用保活”或“系统卡片一定显示”。

### 7. MIUI 能力探测和组件启停

`o000OOo.java:61-68,280-282` 通过厂商 `SystemPropertiesEx`/`SystemProperties` 兼容读取系统属性，并调用 `content://com.miui.personalassistant.widget.external` 的 `isMiuiWidgetSupported`；`BaseApplication.java:523-533` 据此启用或禁用两个 MIUI widget receiver。该设计有两个工程含义：

1. 不支持 MIUI widget 时不暴露对应组件，减少桌面枚举和错误回调；
2. 支持时才启用组件，但真正刷新仍依赖 launcher/系统进程的 widget 生命周期。

这比“所有设备统一注册一套 MIUI receiver”更稳妥，但仍然只是桌面扩展适配，不是后台保活。

### 8. 日期/时间变化只触发刷新，不替代持久化调度

`BaseApplication.java:116-128` 的 `dateChangedReceiver` 收到 `DATE_CHANGED`/`TIME_SET` 后只做两件事：刷新全部 AppWidget、触发应用内部刷新；receiver 是动态注册（`BaseApplication.java:536-541`），进程死亡即失效。它不重建精确闹钟、不重排课程提醒，也不能解决进程被杀后的恢复——重启/包更新后的恢复仍依赖 `BOOT_COMPLETED` 类静态入口。

Sleepy 当前已有：

- `AlarmManager` 精确提醒权限声明。
- `WorkManager`/后台调度和 widget 更新链。
- 前台服务 `FluidCloudService`，并遵守 Android 12+ 先 `startForeground` 的约束。
- Xiaomi/vivo/Meizu/Samsung live-card 探测和普通通知降级。
- 厂商 settings Intent 候选和标准 AppWidget metadata。

Sleepy 当前缺口：

1. 没有 WakeUp 那样统一的“后台运行/自启动 + 电池优化”诊断入口，尤其没有按厂商给出最短设置路径。
2. 调度、通知、Widget 的权限状态没有形成一份可观察的可靠性诊断模型。
3. OEM 私有 live-card 适配与课程提醒的后台可靠性耦合不应继续扩大；私有卡片显示失败必须独立回退普通通知。
4. Android 16 Live Updates 应作为公共 transport 预研，不应把 Samsung/OPPO 未公开 extras 当稳定协议。
5. HarmonyOS NEXT Live View 是 ArkTS/HAP + 准入路径，不能在当前 Android APK 中伪装实现。

## 用户原话 vs 逆向结论

| 用户关切 | WakeUp / 公开证据实际支持的结论 | 当前实现边界 |
|---|---|---|
| “其他人的手机上后台更严重” | 定制 ROM 可能暂停/限制后台进程；WakeUp 通过设置入口要求用户开启后台运行、自启动并取消电池优化 | 这些授权只能降低概率，不能承诺进程永不被杀 |
| “是不是后台优化导致课表异常” | 后台限制会改变 Activity、Widget、通知和调度的生命周期；它是触发条件/严重度放大器，不是业务周次值的直接写入者 | 业务状态仍需单一真源和恢复测试，不能用 ROM 适配掩盖状态竞争 |
| “各厂商要不要各写一套方案” | 公共 Alarm/WorkManager/通知/AppWidget 是共同底座；厂商私有卡片是增强层，且大量能力需要白名单或商务准入 | 未准入厂商统一普通通知/AppWidget 回退 |
| “WakeUp 的厂商逆向能不能直接搬” | 可搬的是架构事实和已许可的协议构造思路；不能搬无许可证代码、Xposed/LSPosed 绕过、系统私有签名能力 | Sleepy 只采用可验证、可降级、合规的公共路径 |

## 厂商方案矩阵

| ROM | 可实施方案 | 不可承诺的部分 | 优先级 |
|---|---|---|---|
| AOSP/Pixel/Samsung 标准 Android | 精确 Alarm + 标准通知 + AppWidget；Android 16 使用 Live Updates 能力探测 | 不保证 Samsung 私有 Now Bar | P0 |
| 小米 HyperOS | 标准通知/AppWidget；已有 Focus extras 仅在 `canShowFocus` 和 feature flag 双通过时发送；引导通知、电池/自启动设置 | 不能绕过白名单、不能把 Xposed/Shizuku 当产品依赖 | P0 |
| vivo/iQOO | 标准通知/AppWidget；SuperX 仅在 scene 注册成功后发送；普通通知作为强制回退 | 白名单/逐步放量未知，不能承诺原子岛 | P0 |
| 魅族 Flyme | 标准通知/AppWidget；Flyme 11+ 且 live provider 明确开启才探测隐藏 extras；枚举值仍需真机矩阵 | 没有官方 API，不能声称稳定支持 | P1 |
| OPPO/一加/realme | 标准通知/AppWidget；Android 16+ 预研公共 Live Updates；ColorOS 15 流体云暂不做私有逆向 | Pantanal 定邀、教育场景未在白名单，不能承诺流体云 | P1 |
| 荣耀 | 标准通知/AppWidget；正式 Suggestions Kit/全局触达需平台注册和 SDK 后再做 | 无公开胶囊绘制 API，不能从 demo 推导 | P1 |
| 华为 EMUI | 标准通知/AppWidget；系统公共 Android 能力优先 | HarmonyOS NEXT Live View 不属于 Android Kotlin APK | P1 |
| HarmonyOS NEXT | 单独 ArkTS/HAP 产品线，复用官方 Live View Kit sample 的生命周期思想 | 不能在 Sleepy Android 包内实现 | P2 |

## 建议的工程落地顺序

### Phase 1：公共可靠性诊断，不碰私有协议

- 添加统一 `BackgroundReliabilitySnapshot`：通知权限、精确闹钟、忽略电池优化、AppWidget 是否绑定、厂商识别、当前 live-card capability。
- 在设置页显示事实状态和系统设置入口，不声称“已保活”。
- 补 `DATE_CHANGED`、`TIME_SET`、重启/包更新后的调度恢复契约测试。
- 调度失败时保留普通通知和 widget，不因 live-card 失败取消课程提醒。

### Phase 2：公共实时更新

- Android 16+ 使用官方 `ProgressStyle`/promoted ongoing 能力探测。
- 低版本继续使用当前普通 ongoing notification。
- ColorOS 16 若官方 API 文档和真机验证补齐，复用同一公共 transport；否则不加私有 OPPO extras。

### Phase 3：厂商增强层

- 小米/vivo：保留现有 best-effort adapter，加入 telemetry/debug-only capability result，不把 unknown 当 enabled。
- 魅族：先做 `type × capsuleType × version` 真机实验，不把社区枚举票数转成产品契约。
- 荣耀/华为：只有取得平台准入和 SDK/AAR 后才新增代码。

## 禁止事项

- 不申请 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 就假设可保活。
- 不用 Xposed/LSPosed/Shizuku、系统签名权限、冒充其他包名绕过厂商白名单。
- 不把厂商私有通知 extras 当后台调度机制。
- 不把普通通知显示称为荣耀 YOYO、OPPO 流体云或华为实况窗。
- 不为每个 ROM 硬编码一个“保活 Intent”后宣称已解决；设置路径必须以设备 resolveActivity 和真机行为为准。

## 来源

- Android 官方：[PowerManager.isIgnoringBatteryOptimizations](https://developer.android.com/reference/android/os/PowerManager) — “Return whether the given application package name is on the device’s power allowlist.”
- Android 官方：[AlarmManager.canScheduleExactAlarms](https://developer.android.com/reference/android/app/AlarmManager) — “Apps targeting Build.VERSION_CODES.S or higher can schedule exact alarms only if they have the Manifest.permission.SCHEDULE_EXACT_ALARM permission or they are on the device’s power-save exemption list.”
- Android 官方：[Create live update notifications](https://developer.android.com/develop/ui/views/notifications/live-update) — promoted ongoing 要求 `POST_PROMOTED_NOTIFICATIONS`、ongoing、非 RemoteViews、非 groupSummary、非 colorized、通道非 IMPORTANCE_MIN；系统可降级为标准通知。
- Android 官方：[Progress-centric notifications](https://developer.android.com/about/versions/16/features/progress-centric-notifications) — Android 16 `Notification.ProgressStyle`。
- Sleepy：`docs/live-cards/huawei/REUSE.md`、`honor/REUSE.md`、`xiaomi/REUSE.md`、`vivo/REUSE.md`、`meizu/REUSE.md`、`samsung/REUSE.md`
- Sleepy：`docs/live-cards/oppo/community-notes.md`、`docs/live-cards/oppo/realtime-notification.md`、`docs/live-cards/oppo/gaps.md`
- WakeUp 反编译工程：`~/readme-audit-worktrees/wakeup-mod/wakeup-android-project/`（jadx 源）与 `wakeup_decoded/`（apktool smali + manifest）
- Android 官方：<https://developer.android.com/reference/android/app/AlarmManager#canScheduleExactAlarms()>
- Android 官方：<https://developer.android.com/about/versions/16/features/progress-centric-notifications>
- Sleepy：`docs/live-cards/_cross-vendor/china-live-cards-comparison.md`
- Sleepy：`docs/live-cards/_cross-vendor/opensource-replicas.md`
- Sleepy：`docs/live-cards/{huawei,honor,xiaomi,vivo,meizu,oppo,samsung}/REUSE.md`
- WakeUp：`readme-audit-worktrees/wakeup-mod/wakeup-android-project/...`
