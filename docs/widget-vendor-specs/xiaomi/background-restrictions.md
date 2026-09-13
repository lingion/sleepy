[evidence=A] 抓取 2026-09-13 · 源URL: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1607

# MIUI/HyperOS 后台管控与小部件刷新链路

官方页：《MIUI进程管理适配说明》（pId=1607，更新 2024-09-25）、《Powerkeeper对应用的管控说明》（pId=1628）、《自启动权限管理说明》（pId=1624）、《后台弹出页面权限管理说明》（pId=1625）、《小部件技术规范与系统能力说明》（pId=1584）。

## 小米 Widget 的刷新模型（pId=1584）

- 小米 Widget 将去掉系统原有的定时刷新（小米展示刷新机制下）；用户滑动到有 Widget 的页面时，系统判定需要刷新并通知应用。
- 默认曝光不刷新；需在 Manifest 声明 `miuiWidgetRefresh=exposure` + `miuiWidgetRefreshMinInterval`（毫秒，最短 10 秒），监听 `miui.appwidget.action.APPWIDGET_UPDATE`。
- 应用在前台或后台存活时可调用 `AppWidgetManager.updateAppWidget()` 主动刷新。
- Widget 状态改变、Widget 不可见且主应用未启动时，可调用服务端更新 API（`https://developer.assistant.miui.com/openapi/widget/{cpCode}/refresh`），经 MI PUSH 透传给负一屏/桌面客户端，由系统拉起 Widget 独立进程刷新，不唤醒主进程。
- 不支持小米 Widget 的系统与非小米手机上，刷新机制与原生 Widget 一致，需按原生机制（定时刷新等）另行适配。

## 独立进程约束与被回收风险

- Widget 必须运行在 `:widgetProvider` 独立进程；不能拉起其他任意进程（包括主进程）；禁止 native fork。
- 技术规范：进程内存不能超过 35M；审核规范：未超 40M。工程预算按 35M。
- 官方明确：Widget 进程系统分配的 adj 值较高，系统资源不足时容易被回收；系统资源紧张时 Widget 进程容易被回收。
- Activity 不能放在 Widget 进程。播放器类小部件例外可运行在非 Widget 进程，但必须关闭曝光刷新、使用前台 Service、主动刷新。

## MIUI 进程管理：被杀原因分类（pId=1607）

用户主动触发（用户在入口操作）：

| 名称 | 触发入口 | Reason |
| --- | --- | --- |
| 一键清理 | 最近任务/悬浮球 | OneKeyClean |
| 强力清理 | 负一屏 | ForceClean |
| 垃圾清理 | 安全中心 | GarbageClean |
| 锁屏清理 | 安全中心 | LockScreenClean |
| 游戏清理 | 安全中心 | GameClean |
| 优化清理 | 安全中心 | OptimizationClean |
| 上滑清理 | 最近任务 | SwipeUpClean |

系统被动触发（应用异常时系统清理）：

| 名称 | 场景 | Reason |
| --- | --- | --- |
| Power 异常查杀 | 应用过度耗电 | AutoPowerKill |
| Thermal 异常查杀 | 应用使手机发热 | AutoThermalKill |

定位方法（官方原文）：`adb logcat -b events | grep am_kill`，`am_kill` 事件最后一列即被杀 Reason，与上表比对定位。例如 `am_kill: [0,5253,com.eg.android.AlipayGphone,500,LockScreenClean]` 即用户锁屏清理触发。

被 `AutoPowerKill`/`AutoThermalKill` 频繁查杀时，官方要求开发者先自查应用是否过度耗电/发热；确认质量无问题后打 bugreport 联系小米三方团队深度分析。应用被用户主动杀死后，官方建议引导用户在安全中心打开自启动开关。

## Powerkeeper 应用智能省电（pId=1628）

- 用户入口：设置 — 省电与电池 — 右上角 — 应用智能省电。
- 任何应用都可以调用小米的页面查看对自己的限制。
- 触发限制的行为（官方原文）：后台长期持锁、置于后台但短时间内耗电过多、长时间置于后台不使用、非法持有传感器等；措施包括释放锁资源、kill 应用进程。
- 用户选择“无限制”后不施加限制。
- 应用调用 `isBackgroundRestricted()` 时返回 true（该项限制生效时）。
- 前台程序不会被限制。

## 自启动与后台弹窗（pId=1624 / pId=1625）

- 自启动默认关闭；应用自启动功能需告知用户，用户可自行打开。
- 后台弹出页面权限默认拒绝；特殊场景白名单例如音乐（歌词显示）、运动、VOIP（来电）；白名单应用出现推广等恶意行为将永久取消白名单。特殊需求联系 `miui-security-open@xiaomi.com`。

## WorkManager/AlarmManager 的证据边界

已抓取的官方页面没有对 WorkManager 或 AlarmManager 给出 HyperOS/MIUI 版本化的执行保证，也没有承诺定时任务可维持 Widget 刷新。官方给出的 Widget 刷新路径只有三条：曝光刷新、应用存活时主动刷新、MI PUSH 透传刷新。

对 Sleepy 的工程结论：不要把 WorkManager/AlarmManager 当作小米端 Widget 刷新或进程存活的保证机制；定时任务只能作为数据准备的补充，其实际触发情况必须在目标 ROM 观测。这不是“小米必然杀死某 API”的官方断言，而是官方文档未提供该保证的事实边界。

## Sleepy 刷新链路验收建议

- 记录四类事件的日志：曝光广播（`miui.appwidget.action.APPWIDGET_UPDATE`）、主动 `updateAppWidget`、MI PUSH 透传、Widget 进程被回收（`am_kill`）。
- 覆盖场景：一键/强力/锁屏清理、应用智能省电各档（含“无限制”）、重启、桌面/负一屏重启、后台静置。
- 检查 Widget 进程内存（`adb shell dumpsys meminfo <pkg>:widgetProvider`）是否在 35M 内。
- 周课表类 Widget 的时间显示直接使用原生 `AnalogClock` 类控件（官方设计规范建议，系统智能调整刷新频率时仍保持时间准确）。
