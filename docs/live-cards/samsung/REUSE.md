[evidence=B] 公开仓库源码核验，抓取时间 2026-09-22

# Samsung One UI 实时通知复用核验

## 结论先行

- **可直接搬（通用 Android）**：Android 16 的 promoted ongoing / `Notification.ProgressStyle`。它不是 Samsung 私有 API，Samsung 只是 One UI 8 等版本上的一个系统消费方。优先复用 [Besser-Bahn `TripLiveUpdate.kt`](https://github.com/chuk-development/Besser-Bahn/blob/6e6e031b43c5e6da270240ff0afc5fba934e2e75/flutter-app/packages/live_update/android/src/main/kotlin/dev/chuk/betterbahn/liveupdate/TripLiveUpdate.kt)，许可证为 WTFPL-2.0（仓库 `LICENSE.txt`）。关键实现见下表。
- **可参考，不宜无审查复制（Samsung 私有 extras）**：[`MukulBolt9/NowBrief`](https://github.com/MukulBolt9/NowBrief) 和 [`YetAnotherBusDeveloper/yetanotherbusapp`](https://github.com/YetAnotherBusDeveloper/yetanotherbusapp)。两者都直接写 `android.ongoingActivityNoti.*` bundle keys；这证明社区已有真实代码，但这些 keys 没有 Samsung 公共 SDK/API 文档。NowBrief 没有可识别 license；YetAnotherBusDeveloper/yetanotherbusapp 是 AGPL-3.0，均不能把源码直接带入 Sleepy。
- **可条件参考**：[`JoshAtticus/CompressorEdge`](https://github.com/JoshAtticus/CompressorEdge)，MIT license 文件明确存在；其 Android 16 标准路径可直接参考，Samsung extras 只能当 best-effort。固定提交 `3e7268068c06abcfd8b9a9987c76bb52e6ef48e5`。
- **不可用作产品依赖**：[`Myxoz/SamsungNowbarProxy`](https://github.com/Myxoz/SamsungNowbarProxy) 通过冒充/复用 `com.kakao.taxi` 包名和显式广播代理通知。仓库 README 自己说明这不是 Samsung 官方能力、存在平台策略/IP 风险，且仓库无识别 license；不应引入 Sleepy。
- **Samsung 私有 API 负证据**：未找到公开 Samsung SDK 中可供第三方稳定调用的 Now Bar/Live Notification API。能找到的是 Samsung framework/SystemUI 的源码常量和内部实现，以及社区逆向 extras；它们不是应用开发者公开 API。Samsung 官方 One UI 文档资料库也只描述系统/桌面行为，没有第三方 Now Bar API 页面。

## 逐仓库证据

### 1. Android 16 通用 Live Update：可直接复用

| 项目 | 核验结果 |
|---|---|
| Repo / 固定 commit | `chuk-development/Besser-Bahn`, `6e6e031b43c5e6da270240ff0afc5fba934e2e75` |
| URL | https://github.com/chuk-development/Besser-Bahn |
| License | WTFPL-2.0；https://github.com/chuk-development/Besser-Bahn/blob/6e6e031b43c5e6da270240ff0afc5fba934e2e75/LICENSE.txt |
| 文件 | `flutter-app/packages/live_update/android/src/main/kotlin/dev/chuk/betterbahn/liveupdate/TripLiveUpdate.kt` |
| 关键行 | 175-191：`setOngoing(true)`、`setRequestPromotedOngoing(true)`、禁用 `setColorized`；242-271：`NotificationCompat.ProgressStyle`、segments/points；274-301：post 后检查是否真的 promoted；305-317：检查 `FLAG_PROMOTED_ONGOING` |

复用边界：把课表状态映射到 `Spec`、`Segment`、`progressMinutes` 即可；保持通知渠道不是 `IMPORTANCE_MIN`，并保留“发布后检查实际 promotion、失败继续普通通知”的降级逻辑。该仓库源码还明确把 Android 16、Android 17 与低版本分层，适合放进通用 transport，而不是 Samsung 分支。

官方 Android 依据：

- [Progress-centric notifications](https://developer.android.com/about/versions/16/features/progress-centric-notifications)：Android 16 引入 `Notification.ProgressStyle`、segments/points 的进度通知模板。
- [Create live update notifications](https://developer.android.com/develop/ui/compose/notifications/live-update)：官方说明 promoted ongoing、`FLAG_PROMOTED_ONGOING` 和 `canPostPromotedNotifications()` 的检查方式。
- [`Notification.Builder.setProgress`](https://developer.android.com/reference/android/app/Notification.Builder)：低版本普通进度通知 API。

### 2. Samsung extras：真实社区实现，但未文档化

#### `MukulBolt9/NowBrief`

- 固定 commit：`ae2a7efb3b676f4cf3f32009d07ab4713dbfa394`。
- License：GitHub API 的仓库元数据为 `license: null`，根目录没有 `LICENSE` 文件；**不可直接搬入**。
- 文件：[NowBriefNotificationHelper.java](https://github.com/MukulBolt9/NowBrief/blob/ae2a7efb3b676f4cf3f32009d07ab4713dbfa394/android/app/src/main/java/com/nowbrief/NowBriefNotificationHelper.java)
  - 20-63：声明 Samsung One UI 7 Now Bar 的 extras 和“whitelist bypass”说法。
  - 74-95：定义 `android.ongoingActivityNoti.style`、`primaryInfo`、`secondaryInfo`、`chip*`、`nowbar*`、`progress*` keys。
  - 167-194：写入 extras；`style=1`、drawer 文案、chip、Now Bar 文案、progress。
  - 203-228：`setOngoing(true)`、`setOnlyAlertOnce(true)`、`addExtras(extras)`、普通 `setProgress`。
- Manifest：[AndroidManifest.xml](https://github.com/MukulBolt9/NowBrief/blob/ae2a7efb3b676f4cf3f32009d07ab4713dbfa394/android/app/src/main/AndroidManifest.xml)，38-40：`com.samsung.android.support.ongoing_activity=true` metadata。
- 归类：**可参考**。它是真实调用代码，不等于公开稳定 API；README 把 `akexorcist.dev` 和 `realMoai/NowbarMeter` 作为来源，并要求开发者选项“Live notifications for all apps”，这些都是社区/逆向证据，不是 Samsung SDK 契约。

#### `YetAnotherBusDeveloper/yetanotherbusapp`

- 固定 commit：`2e7619abeb35a9d6e9fb96f2816d4546da0e1c42`。
- License：AGPL-3.0；根目录 `LICENSE`。若复用代码，必须接受 AGPL 义务，故 Sleepy **不直接复制**。
- 文件：[FeatureDetector.kt](https://github.com/YetAnotherBusDeveloper/yetanotherbusapp/blob/2e7619abeb35a9d6e9fb96f2816d4546da0e1c42/android/app/src/main/kotlin/com/nowbar/api/FeatureDetector.kt)，20-29：探测 `com.samsung.feature.nowbar` 系统 feature；47-60：Samsung/Android 16 平台选择。
- 文件：[OngoingExtrasBuilder.kt](https://github.com/YetAnotherBusDeveloper/yetanotherbusapp/blob/2e7619abeb35a9d6e9fb96f2816d4546da0e1c42/android/app/src/main/kotlin/com/nowbar/api/notification/OngoingExtrasBuilder.kt)，11-45：集中定义 Samsung `android.ongoingActivityNoti.*` keys；100-115：进度/分段输入校验；165-229：将 style、文案、progress、segments、chronometer、capsule 等写入 `Bundle`。
- 文件：[CapsuleConfig.kt](https://github.com/YetAnotherBusDeveloper/yetanotherbusapp/blob/2e7619abeb35a9d6e9fb96f2816d4546da0e1c42/android/app/src/main/kotlin/com/nowbar/api/notification/CapsuleConfig.kt)，7-25：明确这是针对 Samsung 折叠屏 cover-screen capsule 的推断配置，并声称字段来自 Voice Recorder 反编译。
- 归类：**可参考 API 形状/探测方式，不可直接复制**。AGPL 许可证和未文档化 extras 都是硬边界；`hasSystemFeature` 只能证明 ROM 暴露了 feature flag，不能证明第三方包有访问权限或 Now Bar 一定显示。

#### `JoshAtticus/CompressorEdge`

- 固定 commit：`3e7268068c06abcfd8b9a9987c76bb52e6ef48e5`。
- License：MIT；[LICENSE](https://github.com/JoshAtticus/CompressorEdge/blob/3e7268068c06abcfd8b9a9987c76bb52e6ef48e5/LICENSE)，首行明确 `MIT License`。
- 文件：[BackgroundCompressionService.kt](https://github.com/JoshAtticus/CompressorEdge/blob/3e7268068c06abcfd8b9a9987c76bb52e6ef48e5/app/src/main/java/compressedge/joshattic/us/compression/BackgroundCompressionService.kt)
  - 116-126：前台服务通知。
  - 202-253：API 36 `Notification.ProgressStyle` 通知。
  - 286-291：`android.requestPromotedOngoing=true`，并合并 Samsung extras。
  - 294-314：低版本 `NotificationCompat.setProgress` 降级。
  - 317-331：只在 Samsung manufacturer 下写 `android.ongoingActivityNoti.*`，并明确“best effort”。
- Manifest：[AndroidManifest.xml](https://github.com/JoshAtticus/CompressorEdge/blob/3e7268068c06abcfd8b9a9987c76bb52e6ef48e5/app/src/main/AndroidManifest.xml)，7-10：`POST_NOTIFICATIONS`、`POST_PROMOTED_NOTIFICATIONS`；58 行附近声明 Samsung ongoing activity metadata。
- 归类：**通用部分可搬，Samsung 部分仅参考**。MIT 允许复用，但 Samsung extras 的行为和 whitelist/系统版本依赖仍未获得官方保证。

### 3. 不采用的代理/旁路

#### `Myxoz/SamsungNowbarProxy`

- 固定 commit：`afcdf356e10dc86869393ae56a7948e10467bd68`。
- License：GitHub API 元数据 `license: null`，根目录无识别 `LICENSE`。
- [README](https://github.com/Myxoz/SamsungNowbarProxy/blob/afcdf356e10dc86869393ae56a7948e10467bd68/README.md)，1-17：作者明确 experimental、非 Samsung 官方、使用 `com.kakao.taxi` 包名是为了取得系统处理待遇，并提示法律/平台/IP 风险；21-49：通过广播把已构造的 `Notification` 交给代理包再 post。
- 归类：**不可用**。它不是 API 适配，而是包身份/代理旁路；无许可证也排除代码复用。

## Samsung 私有 API 的负证据

1. Samsung framework 的公开镜像 [Notification.java](https://github.com/488315/samsung_framework/blob/30cd25b68f67be792ab78fb46fb9f63af0964522/framework/sources/android/app/Notification.java)（固定 commit `30cd25b68f67be792ab78fb46fb9f63af0964522`）在 184-219 行暴露了 `EXTRA_ONGOING_ACTIVITY_*` 字符串常量，包括 `style`、`primaryInfo`、`progress`、`nowbarPrimaryInfo` 等；这些是 framework 内部常量镜像，不是 `developer.samsung.com` 上的第三方 SDK。
2. Samsung SystemUI 镜像 [OngoingActivityController.java](https://github.com/488315/android_samsung_frameworks_base/blob/d7c13fe69a1ad46f8dff0254f1abd32596fef5cd/packages/SystemUI/src/com/android/systemui/statusbar/phone/ongoingactivity/OngoingActivityController.java)（固定 commit `d7c13fe69a1ad46f8dff0254f1abd32596fef5cd`）属于系统 UI 内部包 `com.android.systemui.statusbar.phone.ongoingactivity`，不是第三方应用可链接的 SDK。
3. GitHub 精确代码检索 `android.ongoingActivityNoti.style` 找到的主要是上述社区实现、反编译/系统镜像和普通通知项目；Gitee 检索未找到可核验的 Samsung Now Bar/Live Notification 开源实现。故当前可写的负结论是：**没有找到 Samsung 官方公开、稳定、面向第三方应用的 Now Bar API；只有逆向 extras 和系统源码证据。** 这不是“绝对不存在”的证明，后续若 Samsung 发布 SDK/开发者文档应重新核验。

## Sleepy 落点

- 不新增 Samsung 私有 extras 到现有适配器，除非用户明确接受未文档化、版本绑定和显示不保证的实验性开关。
- 现有 `VendorLiveNotificationCapability` 已正确把 Samsung 私有能力保持为 `UNKNOWN`，而不是猜测 `ENABLED`；见 `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationCapability.kt:5-13`。
- 未来若接 Android 16 通用路径，放在共享 Live Update transport：API 36+ 用 `ProgressStyle`/promoted ongoing，低版本用标准 `setProgress`，发布后检测实际 promotion，失败继续普通 ongoing notification。
- 若实验性验证 Samsung extras，必须单独 feature flag、保留标准通知回退，并在真实 One UI 版本/机型上实测；不得把 metadata whitelist bypass、包名代理或 root/系统镜像方案作为出货依赖。

## 证据边界

- 仓库链接均固定到核验过的 commit；行号是该 commit 内容的 `nl -ba` 行号。
- “可直接搬”只适用于许可证允许且属于 Android 公共 API 的代码；Samsung 私有 key 即使能在公开仓库找到，也只能标为参考/实验。
- 未找到 Gitee 候选仓库；本页不把搜索结果摘要当成实现证据。
