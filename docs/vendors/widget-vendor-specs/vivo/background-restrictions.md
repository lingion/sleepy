[evidence=A] 抓取 2026-09-13 · 源URL: https://dev.vivo.com.cn/webapi/doc/info?id=1013 (公平运行内存机制适配全文) + id=832 (Android 16 开发者适配文档 - 后台任务三场景配额) + id=1010 (Android 17 开发者适配文档 - 后台音频强化) + id=797 (Android 15 - dataSync 前台服务配额) + id=116 (安装标准 - 功耗/后台行为) ; 原子组件刷新约束 = id=841/845 (doc 全文)

# vivo 后台管控 — 对 AppWidget 刷新链路的影响

## 公平运行内存机制 (doc 1013, vivo/小米/OPPO/荣耀 金标联盟共建, 原文)

### 机制定性

应用内存使用缺乏统一约束导致低内存、频繁回收 (用户感知: 卡顿、发热、应用
重新加载、闪退、后台不驻留)。公平运行内存机制对应用内存建立边界与约束,
**vivo、小米、OPPO、荣耀四家统一采用 PSS 作为物理内存统计指标**。

### 高优先级进程判定 (§2.1.1, 原文)

以下进程视为高优先级进程:
- 进程 oom_score_adj ≤ 200;
- 应用处于后台时,**UI 进程**视为高优进程;
- 与满足前两个条件的进程有绑定关系的进程;
- 系统低内存时被查杀后仍然频繁拉起的进程。

### 预警/查杀广播 (§2.2/§3.2, 原文)

| 项 | TRIM (内存预警) | KILL (查杀) |
|---|---|---|
| action | `itgsa.intent.action.TRIM` | `itgsa.intent.action.KILL` |
| 说明 | 通知应用释放内存 | 通知应用保存数据以便下次打开继续使用 |
| 触发 | 应用物理内存或 Java 堆占用触达预警条件 | 持续增长触达查杀条件 |

- 额外数据: `common` Bundle (notifyType/notifyId/reason/action/callback Binder) +
  `extra` Bundle (物理内存异常: pss/pssLimit KB; Java 堆异常: heapAlloc/heapCapacity KB)。
- 异常通知类型: 物理内存异常 = 1000, Java 堆内存异常 = 2000。
- reason 取值: `"Excessive PSS Usage"` / `"Excessive Java Heap Usage"`。
- **应用收到广播后需在 3 秒内**完成释放内存/备份现场数据,并通过广播里的
  Binder 对象回调处理结果 (result: 0=正确处理, 1=未正确处理)。
- 应用收到查杀广播后应立即保存现场数据 (Activity Record),保证再次打开接续。

### 未适配的影响 (§3.1, 原文)

- 无法及时感知内存风险 (错过最佳释放时机)。
- 无法有效保存现场数据 (现场数据丢失)。
- **后台留存时长下降** (更易因高内存占用被清理)。
- 前台闪退风险增加。

### 对 Sleepy 挂件链路的含义

- 挂件 receiver 所在进程属应用进程; 高内存时收到 TRIM 广播应及时清缓存,
  收到 KILL 广播前保存状态。Sleepy 渲染直读 Room (repository 挂 App 单例),
  进程被查杀后 widget 下次 update 时由 AppWidgetProvider 重新渲染 —
  "清数据/无数据 → 默认视图"规则 (xiaomi tech-spec §9 同款) 在 vivo 同样成立。
- UI 进程后台高优判定对 receiver 进程不直接适用,但 oom_score_adj ≤ 200
  的前台/绑定进程被保护 — 挂件刷新依赖的 App 进程保活与内存占用挂钩。

## Android 16 后台任务三场景配额 (doc 832, vivo 适配文档, 原文)

- Android 16 起,系统基于**三大场景**动态限制后台任务执行时长:
  1. **应用活跃度**: 根据应用后台使用频率 ("活跃"/"受限"分组) 分配不同配额,
     高频应用配额更高;
  2. (原文以表格列举场景,详见 doc 832 §2.x);
  3. **可见性变化**: 任务启动时应用在前台,但转入后台后仍需遵守配额限制
     (原可无限执行)。
- 影响: 后台大文件传输、长时间数据同步等任务可能被强制中断;
  依赖前台服务保活的后台逻辑需重构。
- 适配建议 (原文): 检查后台任务在新配额规则下可正常执行; 用 WorkManager
  管理任务; 耗时短且高优先级任务改用加急任务 `setExpedited()`
  (不依赖前后台状态); 移除 `setImportantWhileForeground` 依赖调用。
- 后台启动 Activity 权限变更 (doc 832 §2.8): PendingIntent 需用新 flag
  `MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` /
  `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`。

## Android 15 dataSync 前台服务配额 (doc 797, 原文)

- dataSync 类型前台服务有 6 小时配额; 应用进入前台会将剩余时间重置回 6h
  (例: 后台已跑 4h 剩 2h,起 Activity 进前台,剩余时间又变 6h)。
- targetSdk ≥ 35 生效。

## Android 17 后台音频强化 (doc 1010, 原文)

- 后台音频 (音乐/播客/熄屏续播) 必须依赖带 WIU (While-In-Use) 能力的 FGS,
  且 FGS 需在用户主动触发音频操作时启动。
- 未按规范配置 FGS 声明权限: 后台定时任务播放提醒音、BOOT_COMPLETED 响应中
  播放音频等行为将被拦截 — 接口不返回异常,代码层难捕获。
- targetSdk 37+ 系列条款; OTP 短信程序化读取延迟 3 小时 (SMS Retriever /
  User Consent API 不受限)。

## 安装期功耗/后台行为管控 (doc 116, 原文)

应用被判定"功耗不达标"的情形 (下载/安装过程检测,侧载 APK 同样适用):
- 同等条件下,应用前后台功耗指标不能超过行业同类平均水平的 5%;
- 进入后台后仍有服务在运行 (与用户正在使用的功能相关且必要的不受限);
- 进入后台后,未经用户选择私自启动;
- 进入后台后,有持锁行为;
- 进入后台后,占用设备资源 (无线网络、摄像头等);
- 进入后台后,伪装成前台应用。

## 用户侧管控路径 (evidence=B, 社区核实)

vivo 系统提供用户手动管控: i 管家应用冻结/解冻、"后台高耗电管理"限制、
睡眠模式批量冻结非白名单应用、自启动管理阻断后台唤醒、开发者选项
后台进程数量限制 (PHP 中文网/太平洋科技 2025 教程, 与官方 i 管家功能一致)。
被用户冻结 (强制停止) 的 App 挂件不再刷新,直到用户手动打开应用。

## 原子组件刷新约束 (doc 841/845, 原文 — 若接入)

- 被动间隔刷新时间 **≥ 12h**; `updatePeriodMillis` 示例 43200000 (=12h)。
- 桌面内存占用 ≤ 10M。
- 刷新场景: 应用主动 / 用户主动触发 / 间隔刷新。

## 与 Sleepy 挂件刷新链路的对照

| vivo 机制 | Sleepy 现状 | 结论 |
|---|---|---|
| 公平运行内存机制 TRIM/KILL 广播 | 未监听 `itgsa.intent.action.TRIM/KILL` | 未适配 = 后台留存下降 + 现场数据丢失; Sleepy 挂件渲染无状态 (每次从 Room 重画),KILL 后恢复成本 = 0; TRIM 释放图片缓存可作后续增强项,非必需 |
| Android 16 后台三场景配额 | 刷新走 WorkManager 15min 兜底 + 系统定时 | WorkManager 属受管任务,配额内执行; 无 dataSync FGS,不受 6h 配额影响 |
| 原子组件 12h 刷新约束 | 未接入原子组件体系,不适用 | 标准 AppWidget 的 `updatePeriodMillis` 不受 vivo 12h 下限约束 (该约束是原子组件平台审核条款,非系统行为) |
| 安装期功耗检测 | 挂件刷新为分钟级定时 + 用户触发的即时刷新 | 无后台持锁/资源占用,不触发动耗不达标 |
| 用户侧冻结 | 被冻结后挂件停更,重新打开 App 恢复 | 与小米 HyperOS 曝光刷新外的冻结行为同构,属系统设计,无 App 侧对策 |
