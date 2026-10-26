[evidence=B/C] 复核 2026-09-22。本文只记录公开仓库中实际出现 `notification.live.*` / Flyme 私有实况通知键的代码；不能把相似名称的 Android 16 Live Updates、华为 Live View 或小米超级岛实现当作 Flyme 协议。

# Flyme 实况通知复用清单

## 结论

- **可直接复用协议片段（许可证允许，仍须自行核验依赖和真机行为）**：`BryceWG/Pinme`（Apache-2.0）、`Relianttt/lightxin`（MIT）、`lightStarrr/starSchedule`（Apache-2.0）。这里的“直接”只指可在相应许可证条件下摘取协议构造代码，不等于可直接复制整个业务文件。
- **可参考，不能直接搬整个实现**：`Ruyue-Kinsenka/Flyme-Live-Notification-Demo`（仓库没有 LICENSE）、`SoilZhu/ChillEast`（仓库没有 LICENSE）、`photometro/guanji-app`（仓库没有 LICENSE）、`YXMAX/OrderAgents`（仓库没有 LICENSE）。
- **不可直接搬（GPL 项目；只能在兼容 GPL 的整体授权策略下使用，或仅参考协议）**：`Kauid323/Yhchat_MD3`（GPL-3.0）、`AIXINJUELUOAI/Will-do`（GPL-3.0）。
- **最小可信基线**：`Ruyue-Kinsenka/Flyme-Live-Notification-Demo` 是最早且最小的公开 demo；后续项目证明同一组隐藏 extras 被复用，但不同项目对枚举值有冲突，不能把任一值域当官方契约。
- **Gitee**：截至本次检索，没有找到能打开源码并核验 `notification.live.*` Flyme 键的公开 Gitee 仓库；Gitee 搜索结果主要是通用通知/监控项目，未计入候选。不能据此断言 Gitee 私有仓库不存在。

## 逐项证据

### 1. Ruyue-Kinsenka/Flyme-Live-Notification-Demo

- URL：<https://github.com/Ruyue-Kinsenka/Flyme-Live-Notification-Demo>
- 固定 commit：`0a3a55167207ad615d5f1f2e6d09367452e2d93b`（`Update README.md`，2025-09-15）；源码树在该 commit 下核验。
- License：**未发现 LICENSE/COPYING/NOTICE，GitHub API 的 `license` 为 null**。不能把它当作可直接复制授权来源；应向作者取得许可，或只按事实参考。
- 关键源码：`app/src/main/java/org/avium/test/LiveNotificationManager.kt:36-95`。
- 行为：`42-53` 构造 capsule bundle；`56-61` 写 `is_live`、`operation=0`、`type=2` 和 capsule；`78-94` 用普通 `Notification.Builder`、`addExtras`、`contentView` 发出通知。`app/src/main/res/layout/live_notification_capsule.xml:2-20` 是单 TextView 的胶囊 RemoteViews；展开布局在 `app/src/main/res/layout/live_notification_hello_world.xml:2-37`。
- 复用边界：**参考协议最小样本**；可以核对键名、Bundle 层级和双 RemoteViews 形态，不能直接复制其无许可证代码或把 README 的 `capsuleType=1` 当源码事实。源码实际是 `capsuleType=5`（`42-45`），README 为 `README.md:13-21` 的早期值。

### 2. BryceWG/Pinme

- URL：<https://github.com/BryceWG/Pinme>
- 固定 commit：`e537144e9e94523bbbf5f0291887b8f4edf2c1b8`。
- License：**Apache-2.0**（仓库 `LICENSE`）。
- 关键源码：`app/src/main/java/com/brycewg/pinme/notification/UnifiedNotificationManager.kt:220-249,251-280,297-445`；Manifest 的 Flyme permission 声明也需核对 `app/src/main/AndroidManifest.xml`。
- 行为：`251-275` 通过 `Build.DISPLAY` 判断 Flyme，并调用 `flyme.permission.READ_NOTIFICATION_LIVE_STATE` + `content://com.android.systemui.notification.provider` / `isNotificationLiveEnabled`；`332-355` 构造 capsule/live Bundle；`433-445` 发通知并附 `RemoteViews`。其源码用 `capsuleType=1`、`type=10`，还增加 `capsuleTitle` 与 `contentColor`。
- 复用边界：**可直接复用协议构造思路和检测代码（Apache 条件下）**；不能把 `type=10`、permission/provider 的可用性或 `capsuleTitle` 视为官方保证，需 Flyme 真机复验。

### 3. SoilZhu/ChillEast

- URL：<https://github.com/SoilZhu/ChillEast>
- 固定 commit：`3bf70d0fe2402b7723a5ca1c42df67d02e531844`。
- License：**未发现 LICENSE/COPYING/NOTICE**。
- 关键源码：`android/app/src/main/kotlin/su/soilzhu/chilleast/FlymeLiveManager.kt:17-22,24-35,121-133,150-207`。
- 行为：明确说明其实现照抄 Ruyue demo（`17-22`）；`163-180` 使用 `capsuleType=5`、`operation=0`、`type=2` 和胶囊 RemoteViews；`189-204` 构造 ongoing 通知并写入 extras；`205-207` 吞掉通知权限异常。
- 复用边界：**只能参考**；无许可证且作者明确声明照抄，不能直接搬。它适合证明 demo 的 `type=2/capsuleType=5` 组合被另一个应用采用，不足以证明枚举语义。

### 4. photometro/guanji-app

- URL：<https://github.com/photometro/guanji-app>
- 固定 commit：`c113bcf4c7729d0978ded3f37f3ab76496334af1`。
- License：**未发现 LICENSE/COPYING/NOTICE**。
- 关键源码：`android/app/src/main/java/com/guanji/app/TimerService.kt:136-183,186-223`。
- 行为：`136-162` 使用 `RemoteViews` 和系统 Chronometer；`165-183` 写 `capsuleType=5`、`type=2`、`operation=0`；`212-223` 将 Flyme extras 和自定义展开视图附加到前台服务通知，并按 Flyme 分支构建。
- 复用边界：**只能参考**；无许可证。可参考计时器/Chronometer 与前台服务的业务组合，但不应把其 `isFlyme()` 和参数值当能力检测或官方协议。

### 5. AIXINJUELUOAI/Will-do

- URL：<https://github.com/AIXINJUELUOAI/Will-do>
- 固定 commit：`01499d459cafbaea90f8553ce289bbc5d59c3328`。
- License：**GPL-3.0**（仓库 `LICENSE`）。
- 关键源码：`app/src/main/java/com/antgskds/calendarassistant/platform/capsule/provider/FlymeCapsuleProvider.kt:29-109,170-197`；Manifest 还声明了 `n.permission.READ_NOTIFICATION_LIVE_STATE` 和 `com.meizu.n.live_notification` 相关元数据，需以该 commit 的 Manifest 为准。
- 行为：`90-99` 将 `createFlymeExtras` 加入通知；`178-196` 写 `capsuleType=1`、`operation=0`、`type=10`，并有 `android.substName` / `android.title`；`101-167` 展示其动作 PendingIntent 设计。
- 复用边界：**GPL 约束下不可直接摘取到 Sleepy 的非 GPL 代码**，除非先完成兼容授权决策。可参考 type=10、颜色对比计算和动作绑定；其 extras 没有 `capsule.content.remote.view`，不能作为双 RemoteViews 证据。

### 6. YXMAX/OrderAgents

- URL：<https://github.com/YXMAX/OrderAgents>
- 固定 commit：`2e26de9d06844523c6fd7d9d0320bf84dd90964a`。
- License：**未发现 LICENSE/COPYING/NOTICE**。
- 关键源码：`app/src/main/java/com/yxmax/orderagents/notification/LiveNotificationManager.kt:116-188`。
- 行为：`139-161` 构造胶囊 RemoteViews 和 Bundle，使用 `capsuleType=5`、`operation=0`、`type=2`；`163-187` 以普通 `Notification.Builder` 加 extras，并为展开卡片设置 `contentView`；`169-173` 绑定完成/截图按钮。
- 复用边界：**只能参考**；无许可证。它是订单/取件场景的真实业务实现，可参考胶囊文本清理和 RemoteViews 动作，但不能直接复制。

### 7. Relianttt/lightxin

- URL：<https://github.com/Relianttt/lightxin>
- 固定 commit：`b62e38d1181204e39cacbef6a72c5f9b3444ef25`。
- License：**MIT**（仓库 `LICENSE`）。
- 关键源码：`app/src/main/java/com/lightxin/core/notification/FlymeLiveBackend.kt:17-76,79-99`；配套协议说明在 `codestable/reference/live-notification-platform-contracts.md:181-243`（该仓库自己的参考文档，明确标注私有协议、非官方文档）。
- 行为：`37-55` 构造 `capsuleType=3`、`operation=0`、`type=10` 的 Bundle，并写 `contentColor`；`57-76` 构造 RemoteViews 通知；`79-99` 按课程/跑步业务选择展开布局。仓库文档同时记录 Flyme 开关检测、`RemoteViews` 与枚举冲突。
- 复用边界：**可直接复用协议片段（MIT 条件下）**，但需保留许可证和版权声明；不能把它的 `type=10/capsuleType=3` 作为稳定值域。该仓库文档的 Android 16 部分不能混用于 Flyme：其自身明确将两条分支隔离。

### 8. lightStarrr/starSchedule

- URL：<https://github.com/lightStarrr/starSchedule>
- 固定 commit：`89a5eb141676444611fc0087d597de24788358c2`。
- License：**Apache-2.0**（仓库 `LICENSE`）。
- 关键源码：`app/src/main/java/com/star/schedule/notification/NotificationManager.kt:166-201,203-215,280-375`；Manifest 中声明权限和设置页相关项也在该 commit 的 `app/src/main/AndroidManifest.xml`。
- 行为：`173-200` 用 permission + SystemUI ContentProvider 检测开关（`178-194`），`323-342` 构造课程通知 extras，源码使用 `capsuleType=3`、`type=10`；`344-375` 用 RemoteViews 组装课程卡片和通知；`197-201` 只在 Flyme >= 11 且开关开启时走该分支。
- 复用边界：**可直接复用协议片段和检测思路（Apache 条件下）**；不能直接复制其业务、数据库或课程调度。它与 Pinme/Will-do/lightxin 一起证明 `type=10`、`capsuleType=1/3` 是社区后续常见组合，但不是官方值域证明。

## 冲突与不能下结论的地方

### `notification.live.type`

源码实际出现至少两组值：

| 值 | 出现于 | 当前结论 |
|---:|---|---|
| `2` | Ruyue demo `LiveNotificationManager.kt:59`；ChillEast `FlymeLiveManager.kt:178-179`；guanji `TimerService.kt:180-182`；OrderAgents `LiveNotificationManager.kt:157-160`；Yhchat 的音频实现同样使用 `1577-1582` | 社区早期/计时/媒体/订单实现采用；**语义未公开** |
| `10` | Pinme `UnifiedNotificationManager.kt:350-353`；Will-do `FlymeCapsuleProvider.kt:189-193`；lightxin `FlymeLiveBackend.kt:49-54`；starSchedule `NotificationManager.kt:336-342` | 后续多项目采用；**语义未公开** |

不能把 `2` 或 `10` 宣称为“正确值”。项目之间可能对应不同 Flyme 版本、不同通知类型或不同逆向试验；没有公开官方枚举表。

### `notification.live.capsuleType`

源码实际出现至少三组值：

| 值 | 出现于 | 当前结论 |
|---:|---|---|
| `1` | Pinme `332-345`；Will-do `178-186`；Ruyue README `README.md:14-21` | 后续实现/README 采用；**与 demo 源码不同** |
| `3` | lightxin `37-47`；starSchedule `323-334` | 后续课程/活动实现采用；**语义未公开** |
| `5` | Ruyue 源码 `42-53`；ChillEast `163-179`；guanji `165-176`；OrderAgents `144-153`；Yhchat `1565-1575` | 最小 demo 与多个复制/参考实现采用；**语义未公开** |

### 其他未定项

- `capsuleStatus=1` 在候选中较一致，但没有官方公开枚举说明。
- `notification.live.capsule.content.remote.view` 不是所有实现都提供；Ruyue、ChillEast、guanji、OrderAgents、Yhchat 使用，Pinme/Will-do/lightxin/starSchedule 的关键 bundle 代码没有统一提供它。不能假设该字段在所有 Flyme 版本都必需或必然生效。
- `flyme.permission.READ_NOTIFICATION_LIVE_STATE` 和 `content://com.android.systemui.notification.provider` 的调用只来自社区实现；没有找到可公开核验的 Flyme 官方开发者 API 文档。权限/provider 是否随版本、签名或系统组件变化，必须真机验证。
- GitHub code search 还命中 Sleepy 自己的实现和测试文件；它们不是外部社区证据，已排除在上述 8 个候选之外。

## Sleepy 的使用建议

1. 若只需要协议构造参考，优先读 Apache/MIT 项目的最小片段；保留对应许可证声明，不复制业务代码。
2. 首轮真机探测应把 `type={2,10}`、`capsuleType={1,3,5}` 当作独立实验变量，记录 Flyme 版本、通知开关、是否出现胶囊、展开卡片和按钮行为；禁止在生产实现中凭社区多数票硬编码“官方值”。
3. `RemoteViews` 胶囊和展开卡片要分别验证；普通 Android 16 promoted Live Updates 的约束不能套到 Flyme 私有分支。
4. 在许可证澄清前，不要复制 Ruyue/ChillEast/guanji/OrderAgents 的源码；GPL 项目仅可作为参考，不能直接并入 Sleepy 当前授权策略。
