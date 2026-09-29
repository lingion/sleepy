# 课程提醒后台可靠性与耗电优化迁移方案

- **状态**：调查完成，待用户审阅；本文件不授权修改产品代码
- **日期**：2026-09-22
- **范围**：课程课前提醒、每日/明日摘要、AlarmManager、BroadcastReceiver、FluidCloudService、WorkManager、小米 HyperOS、vivo OriginOS、OPPO/一加/realme、魅族及标准 Android
- **明确排除**：错过提醒补发、无证据的刷新频率改动、常驻保活进程、绕过厂商准入、未经批准的产品代码变更

## 1. 决策摘要

Sleepy 当前问题必须拆成两个独立问题：

1. **时间可靠性**：App 没有打开、进程不在前台时，系统是否按时触发课程闹钟，Receiver 是否开始执行。
2. **展示可靠性**：Receiver 已执行后，标准通知、前台服务通知和厂商增强卡片是否被系统接受并展示。

迁移目标是先提高第一条链路的可观测性和可恢复性，再将第二条链路做成可选增强层。标准 Android 通知始终是核心回退；小米 Focus/Island、vivo SuperX/原子通知、OPPO 流体云和魅族胶囊不得成为课程提醒唯一成功条件。

推荐目标：

- 每节课继续使用单次 AlarmManager 触发，不用 WorkManager 模拟准点闹钟；
- WorkManager 只承担小组件刷新、低频健康检查和非准点维护；
- 课表/设置变化、开机、时间/时区变化、应用升级时进行幂等重排；
- 新旧调度双轨并行，由 feature flag 控制，逐阶段采集证据；
- 厂商增强适配隔离在 renderer/adapter 层，增强失败立即回退标准通知；
- 用结构化事件区分 Alarm、Receiver、Service、notify、系统增强结果和用户可见结果；
- 只有在能力账本、功耗基线、厂商真机矩阵和回滚演练全部通过后，才允许删除旧路径。

## 2. 用户原话 vs 实现契约

| 用户原话/目标 | 本方案的可验证实现行为 | 不得替换成的伪目标 |
|---|---|---|
| “后台可靠性” | 记录并验证 Alarm 预定/触发、Receiver 进入、通知发布的时间链；对开机、时间变化、权限变化重排 | 打开 App 后把过去课程补发 |
| “耗电优化” | 解除 WidgetUpdateWorker 对 FluidCloudService 的无条件维持；以测量结果决定服务生命周期和刷新频率 | 把 WorkManager 改成更短周期或增加轮询 |
| “效果起码不能比现在还差” | 19 条能力保存账本、标准通知永远可用、旧轨可回滚 | 只验证原子岛截图，忽略普通提醒、摘要、调休和导入数据 |
| “能力边界不能丢失” | 旧通知 ID、厂商 extras、API 兼容、无权限降级、孤儿清理和开关矩阵均有契约测试 | 用新的状态布尔值覆盖真实的 UNKNOWN/DISABLED/NOT_SUPPORTED |
| “拓宽到业内最顶尖” | 引入证据驱动、厂商能力状态机、可观测性、正式准入路线和系统级回退 | 把 Xposed、Shizuku、隐藏 API 或社区字段包装成普通 App 的稳定能力 |
| “调查 50 个相关项目” | 附录 A 列出 50+ 个唯一项目/代码来源、证据等级和复用边界 | 以项目数量代替官方合同和真机验证 |
| “先调查、再改代码” | 本文件是待审阅方案；任何产品代码、提交、发布均需后续明确批准 | 以本方案自动授权实施 |

## 3. 当前架构与真实痛点

### 3.1 当前链路

```text
课程数据 / 提醒设置
        |
        v
CourseNotificationScheduler.scheduleAll()
        |
        +--> 每日/明日摘要 Alarm
        |
        +--> 每节课课前单次 AlarmManager
                    |
                    v
          BeforeClassNotifyReceiver
                    |
                    +--> 标准 Android Notification
                    |
                    +--> FluidCloudService / ProgressStyle
                                |
                                v
                  Android 通知系统 / 厂商增强层

WidgetUpdateWorker --15 min--> WidgetUpdater
        |
        +--> 小组件刷新
        +--> ensureActiveFluidCloud()  (当前职责耦合)
```

### 3.2 已确认的实现事实

- `scheduleAll()` 会取消旧提醒后重排每日、明日和当天课前提醒；已过去的课前时间会跳过，当前语义不是补发。
- 有精确闹钟能力时使用 `setExactAndAllowWhileIdle()`；没有时在新系统上降级为 `set()`；旧系统使用 `setExact()`。
- `SleepyApp` 回到前台当前主要调用 `ensureActiveFluidCloud()`，不能据此断言打开 App 后重新调度了全部课程。
- `FluidCloudService` 在 API 26+ 以 specialUse 前台服务展示 `ProgressStyle`，目前每 15 秒主动更新，课程时间到达后停止，`START_NOT_STICKY`。
- 2026-09-26 起 `VendorLiveCardRenderer.progressStyleFor()` 统一构造 `NotificationCompat.ProgressStyle`（API 36+ 映射平台样式，低版本自动降级普通进度条）；`BackgroundReliabilitySnapshot.promotedOngoingAllowed` 三态探测 `canPostPromotedNotifications()`，null=低于 Android 16。
- 当前 extras 覆盖小米、vivo/iQOO、魅族等厂商，但当前服务不是正式 OPPO Pantanal/Seedling 接入。
- `WidgetUpdateWorker` 每 15 分钟刷新小组件并无条件调用 `ensureActiveFluidCloud()`，使小组件和提醒服务产生功耗、生命周期和故障定位耦合。

### 3.3 已发现的风险

| 风险 | 后果 | 计划处理 |
|---|---|---|
| `scheduleAll()` 无显式 Mutex/generation token | 并发重排时旧任务可能覆盖新任务 | 阶段 2 引入调度代数、幂等提交和契约测试 |
| `BeforeClassNotifyReceiver` 为 exported=true 且无 intent-filter | 外部组件调用面过大，触发语义不清 | 阶段 1 先取证，阶段 2 按最小暴露面收紧 |
| specialUse FGS 缺少 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | 声明/审核完整性不足 | 以 Manifest 变更作为独立审阅项，不在本方案自动修改 |
| Daily/Tomorrow Receiver fire-and-forget 协程 | 广播生命周期结束后工作可能被中止 | 使用 `goAsync()` 或等价受控生命周期，需测试证明 |
| Widget Worker 维持 FluidCloud | 无小组件变化时仍唤醒服务逻辑 | 拆分职责，先验证再删除无条件调用 |
| `USE_EXACT_ALARM` 发行政策风险 | Play 渠道可能不接受用途 | 保留 `canScheduleExactAlarms()` 状态和合法降级，不默认依赖政策豁免 |
| 共享通知 ID | 错误更新/取消可能影响另一条展示 | 保留兼容 ID，同时建立明确 owner 和生命周期 |
| 15 秒更新被误当作厂商协议要求 | 增加耗电且不能证明更可靠 | 阶段 3 以真机功耗和展示证据决定 |

## 4. 能力保存账本

下表是迁移的硬性不变量。任何阶段失败都必须回滚到仍满足账本的旧轨；“增强展示未出现”不等于核心提醒失败，但“标准通知未发布”属于阻断问题。

| 编号 | 必须保留的能力 | 证明方式 |
|---:|---|---|
| 1 | 每日提醒 | 调度契约 + 无 App 前台真机测试 |
| 2 | 明日课程提醒 | 调度契约 + 日期边界测试 |
| 3 | 无课程日摘要 | 摘要 Receiver 测试 + 通知发布证据 |
| 4 | 每门课课前精确闹钟 | Alarm 请求参数/时间矩阵 |
| 5 | FluidCloud/前台服务能力 | 启停、通知和课程结束测试 |
| 6 | `ensureActiveFluidCloud()` 恢复语义 | 进程/服务状态测试 |
| 7 | 厂商开关矩阵 | 每家状态适配器与 UI 状态测试 |
| 8 | 开机后的恢复调度 | `BOOT_COMPLETED` 真机/静态契约 |
| 9 | 无精确闹钟权限的降级 | API 31+ 权限矩阵 |
| 10 | 节假日调休映射 | 跨日/跨周课表测试 |
| 11 | 学期日期范围 | 学期边界和过期课程测试 |
| 12 | 异常课表数据钳制 | 空值、负数、超范围输入测试 |
| 13 | 未授予通知权限时的静默失败语义 | API 33+ 权限测试 |
| 14 | API 26 以下通知兼容 | minSdk 设备/编译分支测试 |
| 15 | 孤儿 Alarm 清理 | 删除课程/换表/重排后 `dumpsys alarm` |
| 16 | 共享通知 ID 兼容行为 | 更新、取消、重启后 ID 契约 |
| 17 | 厂商 extras | 每个 adapter 的 best-effort 测试，不能阻塞标准通知 |
| 18 | capability flow | `UNKNOWN` 不得伪装为 `ENABLED`，失败必须回退 |
| 19 | Android 16 promoted ongoing | API 条件、权限拒绝和普通 ongoing 降级 |

## 5. 官方合同和硬边界

### 5.1 Android

AlarmManager 是系统在应用进程之外负责定时触发的机制，适合离线课程提醒；精确 Alarm 受 Android 版本和权限约束。`setExactAndAllowWhileIdle()` 提升 Doze 下的准点机会，但不是 OEM 后台策略的保证。`SCHEDULE_EXACT_ALARM` 的可用状态必须运行时判断，`USE_EXACT_ALARM` 还要单独评估发行渠道政策。

WorkManager 的 force-stop 处理只恢复 WorkManager 自己持有的任务和内部状态，不能恢复 Sleepy 直接注册的 AlarmManager PendingIntent，也不能证明课程 Alarm 已按时触发。它只能承担小组件、低频健康检查、数据维护等非准点工作。

Android 13+ `POST_NOTIFICATIONS`、通知总开关、channel importance、Android 16 promoted ongoing 都是独立状态。任一状态失败都必须保留普通通知路径的明确降级。

### 5.2 小米 HyperOS Focus/Island

官方文档：`https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131`。客户端普通 Notification 可携带 `miui.focus.param`、`miui.focus.pics`、`miui.focus.actions`；`notification_focus_protocol` 的 1/2/3 对应不同 Focus/Island 代际，OS2 的 status-bar focus data 不能当作 OS3 Island。`persist.sys.feature.island` 和 `contentResolver.call(content://miui.statusbar.notification.public, canShowFocus, ...)` 只能作为运行时能力/权限线索，不是渲染成功证明。

图片大小、HTTPS、系统等待和数量限制必须按官方合同执行。本次调查发现官方页面数量描述存在 6/10 的内部不一致，实施前取更保守的较小限制并在代码注释/测试中固定来源，不凭社区值放宽。

官方 Focus/Island 文档没有证明 Sleepy 自动拥有白名单、签名、审批或场景资格。2026 推送分类、模板、channel 申请和用户订阅资料（`pId=1654/2321/2314/2320`）描述的是远程推送治理，不能替代本地课表 Alarm。

### 5.3 vivo SuperX

vivo 系统源码线索（`VivoNotificationManagerServiceImpl`、`VivoSuperXNotificationManager`）显示 OriginNotificationLimiter 使用 MultiRateLimiter：**每 5 分钟最多 10 次、每小时最多 60 次、最长存活时间 8 小时**；还存在 `SUPERX_VALUE` 结果广播和 `dismissWhenKill` 等生命周期语义。

这些数值是该源码/版本的系统实现证据，不能未经真机和版本核对写成所有 OriginOS 版本的公开通用合同。迁移上必须做到：

- 不把当前每 15 秒刷新推导为 vivo SuperX 的最佳频率；
- 不把持续 8 小时的课程或全天课表作为单个活动；
- 以节次/时间段拆分有明确起止的活动；
- 记录系统结果广播（若设备提供）但仍以标准通知兜底；
- 普通通知渠道备案、原子通知准入、设备支持和用户设置分别建模。

### 5.4 OPPO、魅族和远程推送

OPPO 正式流体云属于 Pantanal/UPK/Seedling 等平台及审核、服务 ID、授权码、场景配置流程；当前普通 FGS 不能称为正式流体云。ColorOS 16 Live Updates 需具体 API 和真机证据后另行启用。

魅族 `notification.live.*` 等字段是 best-effort 厂商层，不能阻塞标准通知。HMS Push、MiPush、UnifiedPush 是远程链路，需要服务端、注册凭据、网络和用户订阅；即使到达，也不能替代离线本地准点提醒。

## 6. 目标架构

```text
                 +-----------------------------+
                 | Course / Settings Changes  |
                 | Boot / Time / TZ / Upgrade |
                 +--------------+--------------+
                                |
                                v
                 +-----------------------------+
                 | Schedule Coordinator       |
                 | Mutex + generation +       |
                 | idempotent diff/reconcile  |
                 +------+----------------------+
                        |
          +-------------+--------------+
          |                            |
          v                            v
+---------------------+       +----------------------+
| AlarmBackend        |       | MaintenanceBackend   |
| one-shot exact/     |       | WorkManager          |
| inexact fallback    |       | widget + health only  |
+----------+----------+       +----------------------+
           |
           v
+---------------------+
| Receiver Boundary   |
| goAsync / validation|
| event timestamps    |
+----------+----------+
           |
           v
+---------------------+
| Reminder Decision   |
| standard path first |
| enhancement best    |
| effort, never gate  |
+----+------------+---+
     |            |
     v            v
+---------+  +-------------------------+
| Standard|  | Vendor Adapter Registry |
| Notify  |  | Xiaomi/vivo/OPPO/etc.  |
| fallback|  | UNKNOWN-safe            |
+---------+  +------------+------------+
                             |
                             v
                 +-----------------------------+
                 | Observability              |
                 | alarm -> receiver -> post  |
                 | -> vendor result -> visible|
                 +-----------------------------+
```

分层原则：调度层不读取厂商私有状态；Receiver 不承担长期保活；标准通知是所有设备的核心路径；厂商 adapter 只增补字段或正式 SDK 调用；FGS 只在确有进行中展示需求时运行；WorkManager 不替代每门课 Alarm；状态使用 `ENABLED`、`DISABLED`、`NOTIFICATION_PERMISSION_REQUIRED`、`SETTINGS_REQUIRED`、`NOT_SUPPORTED`、`UNKNOWN`，不能用品牌匹配或可解析 Intent 推导 `ENABLED`。

## 7. 六阶段双轨迁移

### 阶段 0：冻结猜测式修改，建立基线

保持现有提醒、15 秒刷新和 Worker；不加补发、不删 Worker、不接入未准入正式协议。建立 APK、签名、日志格式、通知 ID 和 19 条账本基线。出口：能采集一次 Alarm→Receiver→服务→notify 完整链路。

### 阶段 1：设备证据矩阵

小米 HyperOS 和 vivo Orange 6 至少覆盖前台/后台/划掉、亮屏/锁屏、无限制/受限电池、自启动开关、通知总开关、channel importance、精确闹钟权限。每次记录设备、ROM、App 版本/签名、目标时间、Alarm 预定/实际、Receiver、服务、notify、用户可见时间和 `dumpsys alarm`/`dumpsys notification`/logcat。

小米额外记录 protocol、`persist.sys.feature.island`、`canShowFocus` 和增强字段结果；vivo 记录 SuperX 结果（如可用）、节流和最长生命周期。出口：每个失败样本能归类到 Alarm、Receiver、服务、标准通知或增强展示中的一层。

### 阶段 2：职责拆分与调度一致性

让 WidgetUpdateWorker 只刷新小组件（是否移除 `ensureActiveFluidCloud()` 由阶段 1 证据决定）；给 `scheduleAll()` 增加 Mutex/单线程协调和 generation token；统一开机、时间/时区、升级和课表变化的幂等 reconcile；广播异步使用 `goAsync()` 或等价受控生命周期；收紧内部 Receiver 暴露面；单独审阅 specialUse subtype 声明。

出口：并发重排、重复广播、重启、升级、时区变化、孤儿 Alarm、课程删除和小组件独立运行全部通过；19 条账本无回归。回滚 flag：`scheduler_v2=false`、`widget_fluidcloud_decoupled=false`。

### 阶段 3：展示生命周期和功耗

测量 FGS 时长、CPU/唤醒、通知更新次数和电量，分别评估提醒前、课程进行中、课程结束生命周期；只有内容或倒计时语义实际变化时更新；优先系统驱动进度/倒计时。禁止把更短 WorkManager 周期、常驻 FGS 或无测量的 30/60 秒刷新当成优化。

出口：标准提醒成功率不下降、功耗在预设预算内、增强失败可逐 flag 回退旧刷新。

### 阶段 4：厂商 adapter 和正式准入

小米先做公开合同的 protocol/property/permission 探测和 best-effort 字段；vivo 先确认普通渠道备案和原子通知准入；OPPO 只有获得正式 API 和课程场景准入才进入正式 adapter；其他厂商保持标准通知和真实 `UNKNOWN/NOT_SUPPORTED`。每个 adapter 都要有异常隔离、标准回退、版本/权限探测和真机视觉或 NotificationListener/UIAutomator 证据。

### 阶段 5：灰度、长时稳定性和旧路径删除

按设备/ROM/版本逐步灰度，不按品牌字符串全量开启。收集 Alarm 延迟、Receiver 进入率、标准发布率、增强接受率、可见率、服务时长、唤醒次数、功耗和崩溃。只有连续多版本、19 条账本通过、新轨标准通知不低于旧轨、无未解释丢提醒、迁移和回滚演练通过、正式准入仍有效且用户明确批准，才能删除旧路径。

## 8. 厂商能力矩阵

| 能力 | 官方合同 | 普通 App 可证明的状态 | 迁移策略 |
|---|---|---|---|
| AOSP Alarm | AlarmManager/Doze API | 预定/触发可由日志和 dumpsys 证明 | 核心本地调度 |
| AOSP WorkManager | 非准点后台工作 | 仅证明 WorkManager 自身执行 | 小组件/维护 |
| 小米 Focus/Island | 官方 Focus 文档 | protocol、property、provider、notify 可探测；渲染需真机 | best-effort + 标准回退 |
| vivo SuperX | 官方准入/系统实现线索 | 普通渠道和结果广播（若有） | 不依赖其完成提醒 |
| OPPO Pantanal | 平台/UPK/Seedling/审核 | 当前无通用公开状态证明 | 暂不宣称正式接入 |
| 魅族 Live | 厂商字段/渠道 | 无通用准入证明 | best-effort + 标准回退 |
| MiPush/HMS/UnifiedPush | 服务端远程推送链路 | token/注册/到达可观测 | 远程增强，非本地核心 |
| Android 16 promoted ongoing | Android API/权限 | 权限和通知状态可探测 | 普通 ongoing 降级 |
| 常驻 FGS | Android FGS 合同 | 服务状态可证明 | 不作为默认保活 |
| 自启动/电池设置 | 多为私有 OEM 设置 | 只能确认状态或打开候选页，不能泛化 | 用户引导，不虚报 |

## 9. 证据等级和迁移边界

- **A**：Android 官方、厂商官方开发者文档、官方 SDK/源码。
- **B**：成熟开源应用的生产路径，能证明实现方式，不能证明所有 OEM 都渲染。
- **C**：社区逆向、Xposed/LSPosed、反编译或单设备实测，只能作为线索。
- **D**：演示项目或无可复现设备证据，只能参考 API 形状。

`notify()` 返回、Bundle 已构造、Provider 允许、或 MiPush 已发送，都不等于屏幕真的出现 Focus/Island。展示成功至少需要真实设备截图、NotificationListener/UIAutomator 或厂商结果广播的一种；正式准入还需平台状态。

不可迁移为普通用户路径的做法包括 Xposed/LSPosed SystemUI hook、root/KernelSU、Shizuku、隐藏 `IConnectivityManager`、切断 `com.xiaomi.xmsf` 网络、常驻进程和隐藏 API。它们可用于调查或实验，不能作为 Sleepy 正常安装包的可靠性前提。

## 10. 功耗策略

1. 仅在课程/提醒设置、日期/时区、开机、应用更新等状态变化时重排 Alarm。
2. 小组件刷新和课程进行中展示分开测量、分开开关。
3. 记录 FGS 起止、更新次数、唤醒和电量变化。
4. 仅在通知内容或进度语义实际变化时更新。
5. 优先系统进度/倒计时能力，厂商 extras 只作 best-effort。
6. 用户关闭增强能力后，标准课前提醒仍可用。
7. 无能耗基线不宣称任何刷新频率更省电。

## 11. 验证矩阵与契约

覆盖 API 26 以下兼容、26-30、31-32、33-35、36/Android 16；AOSP/Pixel 类、小米 HyperOS、vivo Orange 6，取得设备后扩展 OPPO/魅族；前台、后台、划掉、进程被杀、重启；亮屏、锁屏、息屏；通知/精确 Alarm/电池权限组合；时间、时区、开机、升级、Doze；单课、多课、无课、跨日、调休、学期边界、异常字段；增强可用、拒绝、未知和权限撤销。

必须锁定：

- 调度重排的顺序和 generation token，而不只是最终存在某个 Alarm；
- 删除课程后没有孤儿 Alarm；
- 同一课程实例不会重复发布通知；
- 增强失败不取消标准通知、不阻塞调度；
- `UNKNOWN`、`DISABLED`、`NOT_SUPPORTED` 不得写成 `ENABLED`；
- Receiver 外部触发面符合最小权限；
- 广播异步工作在生命周期结束前完成或被明确取消；
- 打开 App 不会未经用户要求补发已错过课程提醒；
- 标准路径先于或独立于增强渲染失败。

建议事件字段：`eventId`、`courseInstanceId`、`scheduleGeneration`、`alarmRequestCode`、`expectedAt`、`actualAt`、`receiverAt`、`serviceAt`、`notifyAt`、`vendor`、`protocol`、`permissionState`、`channelState`、`enhancementResult`、`fallbackUsed`、`appVersion`、`deviceModel`、`osVersion`。不得记录账号、课程敏感内容或令牌。

## 12. 风险登记和删除门禁

| 风险 | 缓解 | 回滚条件 |
|---|---|---|
| OEM 延迟/冻结 Alarm | 真机证据、设置引导、保留降级 | 标准通知成功率低于旧轨 |
| OEM 字段变化 | adapter 隔离、版本探测、UNKNOWN | 增强异常影响标准通知 |
| vivo 限流或生命周期变化 | 节次拆分、结果观测、标准回退 | 超出限流或活动丢失 |
| FGS 耗电过高 | 按需生命周期和预算 | 超预算 |
| 调度并发覆盖 | Mutex、generation、幂等 reconcile | 旧代 Alarm 触发 |
| Receiver 生命周期不足 | goAsync/受控协程测试 | 进入但通知缺失 |
| 政策/权限变化 | 运行时检查和合法降级 | 发行渠道拒绝 |
| 社区逆向误判 | A/B/C/D 分级和真机证明 | 仅 Bundle 成功无视觉结果 |

删除旧路径前必须全部满足：19 条账本逐条有证据；新轨标准通知不低于旧轨；没有未解释后台丢提醒；旧数据/设置兼容；feature flag 和回滚演练完成；所有正式准入仍有效；维护者完成“用户原话 vs 实现行为”复核；用户明确批准删除。

## 附录 A：50+ 已调查项目和来源

下表记录本次调查中可复核的项目/代码来源。项目实现可以证明“某种实现存在”，不自动证明 OEM 展示或后台必达；许可证和当前 commit 在进入代码前仍需复核。

| # | 项目/来源 | 关键模块/结论 | 级别 |
|---:|---|---|:---:|
| 1 | Android AlarmManager | 官方 API/Doze；本地时间触发 | A |
| 2 | Android WorkManager | 官方 force-stop/周期边界 | A |
| 3 | Android ProgressStyle/Live Updates | 官方展示 API | A |
| 4 | Android 16 promoted ongoing | 官方权限/ongoing API | A |
| 5 | Xiaomi HyperOS Focus/Island | `dev.mi.com` pId=2131，protocol 1/2/3 | A |
| 6 | Xiaomi HyperOS push rules | pId=1654/2321/2314/2320，订阅/模板/channel | A |
| 7 | vivo-service | SuperX limiter：10/5min、60/hour、8h、结果广播线索 | A/B |
| 8 | OPPO Pantanal/Seedling docs | 项目内正式平台/审核资料 | A/B |
| 9 | `xzakota/HyperNotification` | `FocusUtils.kt`，protocol/property/provider | B/C |
| 10 | `wxxsfxyzm/InstallerX-Revived` | `MiIslandNotificationBuilder.kt`，V3 DSL | B |
| 11 | `Aliothmoon/MAA-Meow` | `HyperOsFocusPublisher.kt`，V3 progress/result | B/C |
| 12 | `Aliothmoon/MAA-Meow` | `XmsfNetworkGate.kt`，Shizuku/netd 绕过 | C |
| 13 | `D4vidDf/HyperBridge` | `XiaomiNotificationHelper.kt`，runtime 检查 | B/C |
| 14 | `1812z/HyperIsland` | Xposed `NotificationHook.kt`/SystemUI | C |
| 15 | `yusufyorunc/IslandKit` | root/KernelSU/Termux + focus extras | C |
| 16 | `1812z/HyperCopy` | `MiuiSuperIslandNotification.kt`，V3 extras/actions | B/C |
| 17 | `FoedusProgramme/Gramophone` | `IsLandHelp.kt`，media-only Island | B |
| 18 | `cwuom/NeriPlayer` | `IsLandHelp.kt`，media-only focus | B |
| 19 | `xfqwdsj/Knowmad` | `ClassProgress.kt`，课程状态/地点/进度 | B |
| 20 | `ybhgl/Reminder` | standard/live/island fallback | B |
| 21 | `ybhgl/Reminder` | `XiaomiBypassHelper.kt`，Shizuku/隐藏 API | C |
| 22 | `XomaDev/MIUI-Autostart` | 旧 MIUI 属性检测 | B |
| 23 | `judemanutd/AutoStarter` | 自启动设置页 fallback | B |
| 24 | `ankidroid/Anki-Android` | 多 MIUI Intent fallback | B |
| 25 | `ywwynm/EverythingDone` | 通知/channel/Alarm/电池/自启动诊断 | B |
| 26 | `cdz-hy/LiteTask` | exact alarm、通知、MIUI AppOps 线索 | B/C |
| 27 | `easychen/PushDeer` | MiPush 注册/RegID | B |
| 28 | `GetStream/stream-android-push` | Xiaomi token/register/delegate | B |
| 29 | `aliyun/alicloud-android-demo` | MiPush RegID demo | B |
| 30 | `michaelbel/LiveUpdateNotifications` | Live Update API 示例 | B/D |
| 31 | `iVamsi/Live-Notifications-Demo` | live notification 示例 | D |
| 32 | `NicosNicolaou16/Live_Update_Notifcation_Android` | live update 示例 | D |
| 33 | `gkhnakbs/GALiveNotification` | live notification 示例 | D |
| 34 | `KaushalVasava/LiveUpdateNotification` | progress/live 示例 | D |
| 35 | `LawnchairLauncher/lawnchair` | launcher/通知生命周期 | B |
| 36 | `tiann/KernelSU` | root/service 边界 | B/C |
| 37 | `oasisfeng/island` | profile/background 管理 | B |
| 38 | `guolindev/giffun` | Android 生命周期/通知 | B |
| 39 | `nekomangaorg/Neko` | 后台通知路径 | B |
| 40 | `SuperMonster003/AutoJs6` | 自动化/后台路径 | B/C |
| 41 | `SagerNet/sing-box-for-android` | FGS/通知生命周期 | B |
| 42 | `princekin-f/EasyFloat` | overlay/服务边界 | B |
| 43 | `Zackratos/UltimateBarX` | System UI 集成参考 | B |
| 44 | `caiyonglong/MusicLake` | media progress 通知 | B |
| 45 | `DSAppTeam/PanelSwitchHelper` | Android UI/lifecycle | B |
| 46 | `LinkSheet` | Intent/settings fallback | B |
| 47 | `jd1378/otphelper` | 低频提醒实践 | B |
| 48 | `AndroidCSOfficial/android-code-studio` | Android service/notification | B |
| 49 | `IReaderorg/IReader` | 后台/通知 | B |
| 50 | `DimensionDev/Flare` | service/notification | B |
| 51 | `appdevforall/CodeOnTheGo` | Android background path | B |
| 52 | `Pool-Of-Tears/Myne` | notification/background | B |
| 53 | `Fankes` | Android background/notification | B |
| 54 | `easybangumiorg/EasyBangumi` | notification scheduling | B |
| 55 | `xiaowine/Lyric-Getter` | Xiaomi/live 字段线索 | B/C |
| 56 | `xfqwdsj/IAmNotADeveloper` | vendor notification 字段线索 | B/C |
| 57 | `syncthing/syncthing-android` | persistent/service 行为 | B |
| 58 | `NewPipe` | media service/notification | B |
| 59 | `InstallerX Revived` | channel/update/fallback | B |
| 60 | `vodafone/vlc-android` | media FGS 生命周期 | B |
| 61 | `bitfireAT/davx5-ose` | background sync | B |
| 62 | `signalapp/Signal-Android` | notification/channel/隐私日志 | B |
| 63 | `tusky` | notification/sync | B |
| 64 | `kdeconnect` | 跨设备服务生命周期 | B |
| 65 | `osmand` | alarms/notifications/设置引导 | B |
| 66 | `organicmaps` | 功耗约束 | B |
| 67 | `signalapp/Signal-Chat-Backup` | service/notification | B |
| 68 | `mifosio/mifos-mobile` | 标准通知 | B |
| 69 | `libretorrent` | background service | B |
| 70 | `droid-ify` | 后台更新通知 | B |
| 71 | `HMS-Core/hms-push-clientdemo-android` | Huawei Push SDK | A/B |
| 72 | `HMS-Core/hms-push-serverdemo-java` | Push server credentials | A/B |
| 73 | `HMS-Core/hms-push-serverdemo-go` | Push server credentials | A/B |
| 74 | `HMS-Core/hms-push-serverdemo-php` | Push server credentials | A/B |
| 75 | `HMS-Core/hms-push-serverdemo-python` | Push server credentials | A/B |
| 76 | `MiPushFramework` | MiPush integration | B |
| 77 | `MEIZUPUSH/PushDemo` | Meizu Push SDK | A/B |
| 78 | `Luomingbear/Push` | vendor push demo | B |
| 79 | `Marafon228/Push` | vendor push demo | B |
| 80 | `ahmedelfateh/huawei_pushkit` | Huawei PushKit | B |
| 81 | `miosegit/MiPushSDK` | MiPush SDK | B |
| 82 | `nextcloud/talk-android` | push/background | B |

## 附录 B：固定来源

- Xiaomi Focus/Island 官方：`https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131`
- Xiaomi 推送新规：`https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1654`
- Xiaomi 2026 分类：`https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2321`
- Xiaomi 模板：`https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2314`
- Xiaomi 订阅：`https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2320`
- HyperNotification：`https://github.com/xzakota/HyperNotification/blob/c704a4de3e1d069e213508363303f62b5e057526/README.md`
- InstallerX-Revived：`https://github.com/wxxsfxyzm/InstallerX-Revived/blob/8ede27250d04b73631b59464724c240378406cdd/app/src/main/java/com/rosan/installer/framework/notification/builder/MiIslandNotificationBuilder.kt`
- MAA-Meow：`https://github.com/Aliothmoon/MAA-Meow/blob/f79422a5d5f9e0d2f91585a8d57ae7819704eea6/app/src/main/java/com/aliothmoon/maameow/data/notification/live/HyperOsFocusPublisher.kt`
- HyperBridge：`https://github.com/D4vidDf/HyperBridge/blob/e19ede13b87150028d89d31106ed898660e4c145/app/src/main/java/com/d4viddf/hyperbridge/util/XiaomiNotificationHelper.kt`
- Knowmad 课程进度：`https://github.com/xfqwdsj/Knowmad/blob/3c4f7a060d2aecdbc24f37f1c1f2aaeff7b2830f/app/src/main/kotlin/top/ltfan/knowmad/notification/ClassProgress.kt`
- EverythingDone 可靠性检查：`https://github.com/ywwynm/EverythingDone/blob/76eff8a808b1a4ea6d0b267e7bb476bc0d71502e/app/src/main/java/com/ywwynm/everythingdone/helpers/NotificationReliabilityHelper.kt`
- LiteTask 权限检查：`https://github.com/cdz-hy/LiteTask/blob/83ff4e0a6007f903ee4692075d016a25608587c4/app/src/main/java/com/litetask/app/reminder/PermissionHelper.kt`

## 附录 C：执行边界

本文件完成后，下一步只能是用户审阅和选择阶段。用户明确批准产品代码改动后，才可以按阶段 0/1/2 顺序建立测试和实现。任何 commit、push、tag、release、部署或厂商平台申请仍需分别获得相应明确授权；本迁移方案本身不构成这些授权。
