# 课程提醒后台可靠性与耗电优化调查报告

- **调查日期**：2026-09-22
- **调查范围**：课程课前提醒、`AlarmManager`、`FluidCloudService`、WorkManager、小米 HyperOS、vivo OriginOS、OPPO 流体云及现有设计/代码资产
- **当前阶段**：调查与方案讨论，尚未修改提醒实现
- **核心目标**：App 未打开时让课程提醒尽可能由系统可靠触发，同时避免用高频轮询换取表面可靠性

## 1. 执行摘要

当前问题不能直接归因于“提醒需要补发”，也不能直接把固定 15 秒刷新改成分级刷新。现有链路中至少有四个独立故障面：

1. 课前 `AlarmManager` 是否按时触发；
2. `BeforeClassNotifyReceiver` 是否被系统放行并执行；
3. `FluidCloudService` 是否成功启动并保持前台服务；
4. 通知渠道及小米/vivo 厂商通知系统是否实际展示通知。

当前实现还有一个职责耦合点：小组件每 15 分钟运行的 `WidgetUpdateWorker` 同时调用 `ensureActiveFluidCloud()`。这使小组件刷新周期可能参与维持课程提醒服务，既增加功耗，也让故障定位变得困难。

**推荐顺序：**

1. 先在真实小米 HyperOS 和 vivo Orange 6 设备上采集 Alarm、Receiver、前台服务和通知渠道的完整证据；
2. 在证据确认后，优先让 WorkManager 只负责小组件，不再无条件承担流体云服务维持职责；
3. 保留普通 Android 通知作为核心回退路径；
4. 将厂商增强卡片与课程提醒核心链路分离；
5. 在完成真机视觉、耗电和后台存活验证前，不改动 15 秒刷新节奏。

## 2. 用户目标与边界

### 2.1 目标

- App 处于后台、进程未打开时，课程提醒能够尽可能按预定时间触发；
- 减少不必要的进程驻留和周期唤醒；
- 不因优化小组件或厂商增强通知而破坏普通课前提醒；
- 能用日志和系统状态区分调度故障、进程故障和通知展示故障。

### 2.2 不属于当前目标

- 打开 App 后把已错过提醒批量补发；
- 仅为了让进度显示变化而增加更高频的轮询；
- 在未取得厂商准入的情况下宣称已经接入正式流体云、超级岛或原子通知；
- 先改代码再用结果反推原因。

## 3. 当前实现链路

### 3.1 课程提醒链路

```text
课程数据 / 提醒设置
        |
        v
CourseNotificationScheduler.scheduleAll()
        |
        v
AlarmManager 单次课前闹钟
        |
        v
BeforeClassNotifyReceiver
        |
        +--> 普通 Android Notification
        |
        +--> FluidCloudService 前台服务通知
                    |
                    v
        Android 通知系统 / 厂商通知系统
```

### 3.2 小组件周期链路

```text
SleepyApp / WidgetUpdater
        |
        v
WorkManager 15 分钟周期任务
        |
        +--> WidgetUpdater.notifyDataChanged()
        |
        +--> ensureActiveFluidCloud()
```

第二条链路的两个职责目前混在同一个 Worker 中：

- 小组件需要周期刷新；
- 流体云服务是否应该存在。

这两个职责的时间要求、耗电模型和失败后果并不相同，后续应拆开验证。

## 4. 现有代码事实

### 4.1 `CourseNotificationScheduler`

` scheduleAll()` 当前会：

1. 取消已有提醒；
2. 检查全局提醒开关；
3. 调度每日提醒；
4. 调度前一晚提醒；
5. 调度当天课前提醒；
6. 调用 `ensureActiveFluidCloud()`。

课前提醒主要按每节课创建单次 Alarm。目标时间到达时：

- Android 12 及以上、没有精确闹钟能力时，退回 `AlarmManager.set()`；
- 有精确闹钟能力时，使用 `setExactAndAllowWhileIdle()`；
- 更旧版本使用 `setExact()`。

当前 `scheduleTodayBeforeClassAlarms()` 会跳过 `epoch <= now` 的课前提醒。这说明现有语义是“不补发已经过去的提醒”，不能未经确认改成打开 App 后立即补发。

### 4.2 `SleepyApp`

App 回到前台时当前只调用：

```kotlin
notificationScheduler.ensureActiveFluidCloud()
```

这个路径没有调用完整的 `scheduleAll()`。因此“打开 App 后才看到提醒”不能简单解释为打开 App 后重新调度了全部提醒，还必须观察 Alarm、Receiver 和通知实际发生了什么。

### 4.3 `FluidCloudService`

当前服务具备以下行为：

- Android 26 及以上通过前台服务展示通知；
- 使用 `NotificationCompat.ProgressStyle()`；
- 通过 Handler 固定每 15 秒调用一次 `postProgressNotification()`；
- 上课时间到达后停止前台服务并停止自身；
- 返回 `START_NOT_STICKY`；
- 附加小米、vivo/iQOO、魅族等厂商 Bundle extras；
- 当前并非正式 OPPO Pantanal/Seedling 流体云实现。

因此 15 秒是当前普通前台服务通知的实现细节，不能直接等同于任一厂商正式实时卡片协议的要求。

### 4.4 `WidgetUpdateWorker`

当前 Worker 每次执行时：

1. 刷新小组件数据；
2. 调用 `ensureActiveFluidCloud()`；
3. 返回成功。

它是当前耗电和后台职责耦合的首要检查点，但不应在没有真机验证和功能矩阵的情况下直接删除整个周期任务。

## 5. 厂商资料审计结论

证据等级说明：

- **A**：厂商官方开发者文档或官方平台资料；
- **B**：官方页面、主流媒体或官方资料的二次整理；
- **C**：反编译、社区项目或实测线索，需要真机复核。

### 5.1 OPPO / 一加 / realme：正式流体云

**A 级结论：**

- 正式流体云属于 ColorOS 泛在服务体系；
- 官方路线涉及 UPK、SeedlingSupportSDK/宿主、服务 ID、授权码、场景配置和平台审核；
- 当前普通 Android 前台服务通知不等于正式 OPPO 流体云；
- 现有资料没有公开一个可以直接用于 Sleepy 的统一刷新频率上限；
- 教育/课程场景是否准入仍存在不确定性，需要平台沟通或正式申请确认。

**B 级观察：**

- ColorOS 16 被报道为兼容 Android 16 Live Updates；
- 这可能成为未来降低跨厂商接入成本的方向；
- 当前项目没有足够的官方 API 文档落盘，也没有 Sleepy 真机验证，因此不能作为本轮修复依据。

**结论：**

不能把当前 `FluidCloudService` 设计成“按 OPPO 流体云规范刷新”，也不能指望普通前台服务自动获得 OPPO 的后台保活能力。

### 5.2 vivo / iQOO：原子通知和原子岛

**A 级结论：**

- 原子通知是邮件申请、应用上架、场景分配、需求/UI 双方评审、联调和逐步放量的准入体系；
- 普通通知渠道需要先完成相应备案，否则可能出现通知栏可见但不弹出的情况；
- 未准入、未放量或不支持的设备必须回落普通通知；
- 官方场景示例包含演出提醒等有明确起止时间的日程型场景，课程提醒在语义上可以论证，但不代表自动获得准入；
- 单个活动有时长限制，全天课表不应作为一个长时间单活动维持；应按节次或时间段拆分。

**C 级补充线索：**

- 反编译资料显示系统存在频控和活动清理规则；
- 资料中出现了约 10 秒级刷新、5 分钟和 60 分钟计数限制；
- 不同 OriginOS 版本、普通通知与原子通知路径的组合行为还需要真机核实；
- 有线索表明某些计时器可能由系统驱动，从而减少应用主动刷新，但尚未完成 Sleepy 真机验证。

**结论：**

当前不能用普通前台服务的 15 秒刷新频率推导 vivo 原子通知的最佳频率。应先把普通提醒与原子通知能力分离，并优先确认普通通知渠道备案和后台限制。

### 5.3 小米 HyperOS：焦点通知和超级岛

**A 级结论：**

- 超级岛需要开发者注册、服务开通、场景预审、正式方案审核、设备白名单联调和正式权限；
- 本地客户端实现可以不依赖 MIpush，但仍需要超级岛服务和场景审核；
- OS2 老焦点通知还有独立的邮件申请和权限流程；
- 官方要求申请资料包含展示时机、消失时机、刷新节点、变更频次和预计持续时间；
- 官方没有承诺 `AlarmManager` 或 WorkManager 在 HyperOS 后台一定按时执行；
- 小米小组件的曝光刷新属于小组件宿主机制，不等于应用主进程课前提醒保证；
- 官方小组件资料明确区分独立 Widget 进程、曝光刷新和 App 主动刷新。

**结论：**

小米增强通知是平台准入能力，不是普通通知加一个 Bundle 就能保证的能力。课程提醒核心链路仍应依赖标准 Android Alarm + 普通通知，并将小米增强能力作为可选层单独验证。

## 6. 当前耗电点分析

### 6.1 前台服务驻留

`FluidCloudService` 从提醒触发到课程开始期间保持前台服务。若服务只用于展示进度，而通知内容在一段时间内没有实际变化，持续驻留的收益需要与功耗、厂商后台策略和系统限制进行权衡。

### 6.2 固定 15 秒更新

每 15 秒执行一次通知更新会带来：

- Handler 唤醒；
- 通知对象重新构建；
- 前台服务通知更新；
- 可能的厂商通知解析和 UI 刷新；
- 进程在服务生命周期内持续存活。

但它是否可以降频不能只从耗电推断，还要确认：

- 进度条是否必须连续变化；
- 厂商通知系统是否按更新事件处理；
- 是否存在系统驱动的倒计时能力；
- 降频后是否破坏用户看到的卡片体验。

因此本轮不建议直接改成 60 秒、30 秒或其他分级刷新。

### 6.3 15 分钟 WorkManager 耦合

WorkManager 15 分钟周期本来服务小组件刷新，但当前额外调用 `ensureActiveFluidCloud()`。潜在问题是：

- 小组件没有变化时仍然唤醒应用逻辑；
- 课程提醒服务的生命周期被小组件刷新周期间接影响；
- 难以判断一次服务启动是课程提醒触发还是周期 Worker 触发；
- 删除整个 Worker 又可能破坏小组件功能。

更稳妥的方向是先拆分职责，再分别评估周期任务是否必要。

### 6.4 `scheduleAll()` 全量重排

提醒设置、课程数据等变化时，`scheduleAll()` 会取消并重新安排全部提醒。这是配置变化时的可接受成本，但不应该作为常规后台保活手段被周期调用。

## 7. 可能的故障定位树

### A. Alarm 没有按时触发

可能原因：

- 未获得精确闹钟能力；
- Alarm 被厂商后台策略延迟、取消或冻结；
- 课程或提醒设置变化后没有完成正确重排；
- 日期、时区或设备时间发生变化；
- PendingIntent 标识发生冲突或被覆盖。

### B. Alarm 触发但 Receiver 没有进入

可能原因：

- 应用进程被冻结或被厂商清理；
- BroadcastReceiver 未被系统及时放行；
- 厂商后台限制或自启动限制；
- PendingIntent/组件配置问题。

### C. Receiver 进入但通知没有出现

可能原因：

- `POST_NOTIFICATIONS` 权限或系统通知总开关；
- 通知渠道重要性被用户或厂商降级；
- 前台服务启动限制或服务启动异常；
- vivo 渠道备案、原子通知准入或场景权限问题。

### D. 通知已发布但用户看不到

可能原因：

- 通知渠道为静默；
- 厂商通知管理隐藏弹窗；
- 厂商增强卡片未准入或未放量；
- 设备处于后台限制、勿扰模式或特殊省电状态；
- 通知展示层和普通通知层被错误地当成同一能力。

## 8. 可行方案对比

| 方案 | 核心做法 | 后台可靠性 | 耗电 | 厂商依赖 | 用户授权 | 改动范围 | 验证难度 |
|---|---|---:|---:|---:|---:|---:|---:|
| A. 标准通知优先 | 单次 Alarm 负责触发，普通通知负责保底，周期 Worker 不维持流体云 | 中到高，取决于 ROM | 低到中 | 低 | 需要通知、精确闹钟和后台设置 | 中 | 中 |
| B. 提醒与增强卡片分离 | 核心提醒和厂商卡片两条独立链路，未准入时回落普通通知 | 高于当前职责耦合结构 | 可控 | 中到高 | 需要厂商准入及用户开关 | 中到高 | 高 |
| C. 设备证据驱动 | 先采集 Alarm、Receiver、服务、通知四层证据，再确定代码改动 | 不能直接提升，但能避免误修 | 暂不改变 | 低 | 需要测试设备和调试权限 | 低 | 中到高 |
| D. 高频周期保活 | 用更短 WorkManager 或轮询提高“看起来可靠” | 低，ROM 仍可延迟/冻结 | 高 | 中 | 仍需用户授权 | 低到中 | 低，但结论不可靠 |
| E. 正式厂商能力优先 | 先接入小米/ vivo/OPPO 正式卡片协议 | 仅在准入和设备覆盖成立时较高 | 未知，取决于协议 | 很高 | 平台审核、备案、白名单 | 高 | 很高 |

### 8.1 方案 A：标准通知优先

做法：

- 每节课前保留单次 Alarm；
- Alarm 触发后才创建通知或启动短时前台服务；
- 不让周期 WorkManager 负责维持 FluidCloudService；
- 仅在课程、提醒设置、开机、应用更新、日期或时区变化时重排 Alarm；
- 普通通知始终保留；
- 增强展示只在明确需要时启动。

优点：实现边界清楚，功耗更容易控制，不依赖厂商正式卡片准入。

风险：不能保证所有 ROM 都按时放行 Alarm，必须配合设备设置引导和真机验证。

### 8.2 方案 B：提醒与增强卡片分离

做法：

- 普通课前提醒作为核心功能；
- 小米焦点通知/超级岛、vivo 原子通知、OPPO 流体云作为可选展示层；
- 能力状态使用 `UNKNOWN`、`DISABLED`、`NOT_SUPPORTED` 等真实状态；
- 未准入、未放量、用户关闭或设备不支持时，自动回落普通通知；
- 每个厂商单独记录展示结果，不用一个“流体云已开启”状态覆盖所有平台。

优点：增强能力失败不会拖垮核心提醒，后续可以分厂商逐步接入。

风险：状态模型和测试矩阵变复杂，正式厂商能力需要审核、联调和持续适配。

### 8.3 方案 C：设备证据驱动

先不改行为，只完成诊断：

- 在小米 HyperOS 和 vivo Orange 6 各选一台设备；
- 设置 2 至 5 分钟后的测试课程提醒；
- 分别测试前台、后台、锁屏、划掉最近任务、电池无限制、自启动和通知渠道状态；
- 采集 Alarm、Receiver、服务和通知四层时间戳；
- 将问题归类后，再选择 A 或 B 中的代码变化。

优点：改动最小，能直接排除错误假设。

风险：需要设备、ADB 和可重复的测试窗口，不能立即产生代码层“修复感”。

### 8.4 不推荐方案 D：更短周期保活

将 15 分钟 Worker 改成更短周期，或增加轮询来提高后台可靠性，不推荐作为解决方案：

- WorkManager 本身不保证精确执行；
- 厂商仍可能冻结或延迟任务；
- 频繁唤醒会增加耗电；
- 不能证明 Alarm 或通知渠道问题已经解决；
- 容易把“调度不可靠”变成“更多后台活动”。

## 9. 推荐实施顺序

### 阶段 0：保持现状，禁止猜测式修改

- 不增加错过提醒补发；
- 不改 15 秒刷新；
- 不删除整个 WorkManager；
- 不新增厂商正式协议接入；
- 先建立可复现测试条件。

### 阶段 1：真机证据矩阵

至少覆盖：

| 设备 | 系统 | App 状态 | 屏幕 | 电池策略 | 自启动 | 通知渠道 | 结果 |
|---|---|---|---|---|---|---|---|
| 小米目标机 | HyperOS 具体版本 | 前台/后台/划掉 | 亮屏/锁屏 | 无限/受限 | 开/关 | 高/默认/静默 | 待采集 |
| vivo Orange 6 | OriginOS 具体版本 | 前台/后台/划掉 | 亮屏/锁屏 | 无限/受限 | 开/关 | 高/默认/静默 | 待采集 |

每次测试记录：

- 设备型号、系统版本、应用版本、包签名；
- 提醒目标时间；
- Alarm 预计触发时间；
- Receiver 实际进入时间；
- 服务启动时间；
- 通知发布和用户看到时间；
- 是否只有打开 App 后才出现；
- `dumpsys alarm`、`dumpsys notification`、服务状态和相关 `logcat`。

### 阶段 2：低风险职责拆分

若证据确认周期 Worker 不是提醒可靠性的必要条件：

- 让 `WidgetUpdateWorker` 只负责小组件刷新；
- 移除其对 `ensureActiveFluidCloud()` 的无条件调用；
- 保留课程设置、课程数据、开机、应用更新、日期和时区变化时的重排；
- 为 Alarm、Receiver、服务启动和通知发布增加最小结构化日志；
- 验证小组件和课程提醒分别不回归。

### 阶段 3：优化增强展示层

只有在阶段 1 和阶段 2 有证据后，才讨论：

- FluidCloudService 是否需要全生命周期前台服务；
- 是否可以用系统驱动倒计时减少主动刷新；
- 15 秒刷新是否仅用于特定厂商或特定展示状态；
- 是否拆分“提醒前准备阶段”和“课程进行中展示阶段”；
- 是否按厂商能力状态启用不同展示路径。

### 阶段 4：正式厂商能力接入

按准入和收益决定优先级：

1. 小米：协议公开度较高，但需要焦点通知/超级岛权限和场景审核；
2. vivo：课程日程语义较契合，但需要上架、备案、邮件准入和逐步放量；
3. OPPO：当前仍需确认正式入口和教育场景准入，暂不把普通通知 extras 当作正式接入。

## 10. 明确不建议现在做的事情

- 不增加“打开 App 后补发错过提醒”；
- 不直接把固定 15 秒改成 60 秒、30 秒或其他分级刷新；
- 不直接删除整个 15 分钟 WorkManager；
- 不把 WorkManager 改成更短周期来提高可靠性；
- 不把当前普通前台服务通知称为正式 OPPO 流体云；
- 不根据厂商品牌或 Intent 是否可解析就判定增强能力已启用；
- 不跳过准入、备案和真机验证直接接入 vivo 原子通知或小米超级岛；
- 不以“用户已给权限”推断自启动、后台活动、通知渠道和厂商场景开关全部正确；
- 不把小组件曝光刷新机制当作课程提醒后台保证；
- 不在没有能耗基线的情况下声称某种刷新频率“更省电”。

## 11. 仍未解决的资料和实测缺口

1. OPPO 官方资料没有公开当前项目可以直接采用的统一刷新频率；
2. OPPO ColorOS 16 的 Android 16 Live Updates 兼容路径尚未取得足够官方开发 API 资料，也没有 Sleepy 真机验证；
3. vivo 原子通知的不同 OriginOS 版本频控组合和计时器自驱动行为需要真机验证；
4. 小米官方没有给出 AlarmManager/WorkManager 在 HyperOS 后台的执行保证；
5. 尚未采集小米目标机和 vivo Orange 6 的 `dumpsys alarm`；
6. 尚未采集通知渠道实际重要性、厂商通知管理状态和 `dumpsys notification`；
7. 尚未确认 vivo 普通通知渠道是否完成系统侧备案；
8. 尚未确认小米设备是否具备正式超级岛权限、协议版本和课程场景白名单；
9. 尚未确认 15 分钟 Worker 调用 `ensureActiveFluidCloud()` 是否实际延长服务生命周期，还是仅做幂等检查；
10. 尚未建立 FluidCloudService 运行期间的实际功耗基线。

## 12. 结论

当前最合理的决策不是立即修改刷新频率，而是先把“准时触发提醒”和“实时增强展示”拆成两个问题：

- **准时触发提醒**：由单次 Alarm + 普通 Android 通知承担，重点验证系统和厂商是否放行；
- **实时增强展示**：由 FluidCloudService 和厂商协议承担，必须按准入状态、设备能力和实际视觉需求单独优化。

推荐先执行“设备证据矩阵”，然后实施低风险职责拆分：让小组件周期任务只负责小组件，不再无条件维持流体云服务。完成真机证据和功耗基线后，再决定是否调整服务生命周期或刷新策略。

在用户确认具体方案前，本报告不授权对提醒代码进行修改。

## 附录 A：已审计资料

### 项目内厂商实时卡片资料

- `docs/live-cards/oppo/fluid-cloud-overview.md`
- `docs/live-cards/oppo/fluid-cloud-api.md`
- `docs/live-cards/oppo/realtime-notification.md`
- `docs/live-cards/oppo/fluid-cloud-access-gate.md`
- `docs/live-cards/oppo/gaps.md`
- `docs/live-cards/vivo/notification-behavior.md`
- `docs/live-cards/vivo/realtime-capsule-api.md`
- `docs/live-cards/vivo/access-gate.md`
- `docs/live-cards/vivo/community-notes.md`
- `docs/live-cards/vivo/gaps.md`
- `docs/live-cards/xiaomi/notification-behavior.md`
- `docs/live-cards/xiaomi/access-gate.md`
- `docs/live-cards/xiaomi/community-notes.md`
- `docs/live-cards/xiaomi/gaps.md`
- `docs/live-cards/_cross-vendor/china-live-cards-comparison.md`
- `docs/live-cards/INDEX.md`

### 后台与小组件资料

- `docs/widget-vendor-specs/xiaomi/background-restrictions.md`
- `docs/widget-vendor-specs/vivo/background-restrictions.md`
- `docs/widget-vendor-specs/_cross-vendor/background-refresh-survival.md`
- `docs/widget-vendor-specs/xiaomi/tech-spec.md`
- `docs/widget-vendor-specs/xiaomi/qa-faq.md`
- `docs/widget-vendor-specs/INDEX.md`

### 设计与代码资产

- `docs/superpowers/specs/2026-09-21-vendor-live-notification-authorization-design.md`
- `docs/superpowers/plans/2026-09-21-vendor-live-notification-authorization.md`
- `app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt`
- `app/src/main/java/com/lingion/sleepy/widget/notification/FluidCloudService.kt`
- `app/src/main/java/com/lingion/sleepy/widget/WidgetUpdateWorker.kt`
- `app/src/main/java/com/lingion/sleepy/widget/WidgetUpdater.kt`
- `app/src/main/java/com/lingion/sleepy/SleepyApp.kt`
- `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt`
- `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdapters.kt`
- `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationCapability.kt`
