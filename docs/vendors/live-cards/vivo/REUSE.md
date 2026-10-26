[evidence=repo-code] 核验时间 2026-09-22 · 范围：公开 GitHub/Gitee 代码搜索、GitHub REST code search（`notification.superx` / `island.superx` / `api-push.vivo.com.cn`）、候选仓库文件/许可证/路径级 commit 核对。

# vivo / iQOO 实现复用核验

## 结论

- **可直接搬（许可证允许，仍需按原许可证保留声明）**：`cameleonnbss/originos-toolkit` 的 OriginIsland payload/sender；`lladlam/MeloX-Android` 的最小音乐播放桥接。两者都是公开、可读、实际写入 `notification.superx.*` 的 Android 代码，但都不能绕过 vivo 的白名单/场景准入。
- **可参考（不可直接搬）**：`imwangwang/vivo-service` 与 `imwangwang/vivo-apps` 的系统侧反编译源（无 LICENSE）；`death34018-hue/AionsHome` 的通知监听/解析（MIT，但它读取并转发通知，不发送原子通知）；`fvhde/origin-isle` 的协议字典/Builder（代码完整，但许可证是自定义 Source-Available、明确禁止再分发和衍生）。
- **不可用/准入封闭**：vivo 原子通知、原子岛、普通胶囊的系统展示仍受 vivo 侧 app/scene 白名单与逐步放量控制；开源代码不能替代 `oosyztz@vivo.com` 申请。公开搜索没有找到可直接用于三方 app 的 VPush 云端发送实现。
- **明确空壳**：`2756826865/android-sms-forwarder` 的 `VivoAtomicIslandProvider.kt` 只保留 provider 接口和注释，写明待 vivo 平台批准后再填 `notification.superx.*`，不能复用为实现。

## 可直接搬

### 1. cameleonnbss/originos-toolkit（MIT）

- Repo: https://github.com/cameleonnbss/originos-toolkit
- License: MIT（GitHub API `repos/cameleonnbss/originos-toolkit/license`）
- HEAD（核验时）：`b72f9b9319893a1cc039f07ea91448fe2da31dc2`，2026-09-17；相关 payload 文件路径级 commit：`124b1c9ac3c22b598c37cdb4440b9b0a77ea6e80`。
- 关键文件：
  - [`OriginIslandTemplates.kt`](https://github.com/cameleonnbss/originos-toolkit/blob/main/app/src/main/kotlin/dev/cameleonnbss/originostoolkit/core/OriginIslandTemplates.kt)（blob `be8e75fb9c6ce8a4c20d1d9f4d41e4adc6e9017b`）
  - [`OriginIslandSender.kt`](https://github.com/cameleonnbss/originos-toolkit/blob/main/app/src/main/kotlin/dev/cameleonnbss/originostoolkit/service/OriginIslandSender.kt)（blob `998c73d5b579e3e0b018b9c773caee8b98cfeaaf`）
- 关键行（HEAD 文件行号）：`OriginIslandTemplates.kt:24-38` 说明 operation/showNotify/template/scene/baseInfos/capsule/infos/shortInfos/island 的 wire format；`OriginIslandSender.kt:61-93` 创建 channel、构造标准 Notification、`builder.extras.putAll(...)`、`manager.notify(...)`；`OriginIslandSender.kt:97-110` 用 `operation=2` 尝试优雅卸载后 cancel；`OriginIslandSender.kt:120-130` 反射调用隐藏的 `setSuperXInfosSceneList`。
- 适用：把 payload 构造、创建/更新/结束三态和普通通知 fallback 逻辑移植到 Sleepy 的 renderer；必须自行审查该项目的上游依赖和其“upstream”来源，不能把 scene 注册反射当作绕过准入。

### 2. lladlam/MeloX-Android（GPL-3.0）

- Repo: https://github.com/lladlam/MeloX-Android
- License: GPL-3.0（GitHub API `repos/lladlam/MeloX-Android/license`）
- HEAD（核验时）：`6858d23937603a49bd7c83381aa260fb26945889`，2026-09-19；相关文件路径级 commit：`e4dd437daafe335b80b087daee793cfd31caece8`。
- 关键文件：[`VivoAtomicIslandBridge.kt`](https://github.com/lladlam/MeloX-Android/blob/main/android/app/src/main/kotlin/com/lladlam/melox/platform/vivo/VivoAtomicIslandBridge.kt)（blob `b15d620e51ec1b5d4e0f6825a9aa5832c6af78db`）。
- 关键行：`22-35` 定义 channel/id、10 秒更新间隔、8 小时上限和 `notification.superx.*` key；`62-86` 区分同一歌曲的 update/create，结束使用 `operation=2`；`99-124` 写入 operation/showNotify/template/clickResp/scene/baseInfos。
- 适用：最小可读的本地原子岛样例，尤其适合参考 10 秒更新和 8 小时生命周期保护。若复制到非 GPL 兼容模块，必须先处理 GPL-3.0 许可义务；本报告不建议逐段复制到闭源 app。

## 可参考

### 3. imwangwang/vivo-service（无 LICENSE，不可搬）

- Repo: https://github.com/imwangwang/vivo-service
- License：GitHub API 未返回 LICENSE；按无授权代码处理。
- HEAD：`14edba76f19c60a2091b8207ee4f33576a08b27d`，2025-12-19，`initial creation`。
- 文件：[`VivoSuperXNotificationManager.java`](https://github.com/imwangwang/vivo-service/blob/master/com/android/server/notification/VivoSuperXNotificationManager.java)，路径级/文件 blob `0c652bfadad60c1ad7c6b93f7bd96f9cb49e66f6`，同一初始 commit。
- 关键行：`62-127` 可见 SuperX 常量、`CREATE_OPERATION=0`、`FINISH_OPERATION=2`、`CUSTOM_TEMPLATE=7`、`DELIVERY/MOVIE/FLIGHT/FOCUSMODE/HEALTH/NAVIGATION` scene、`island.superx.*` key 及错误码常量。
- 用途：只用于核对系统行为、scene/template/失败码和反编译证据；禁止将其源文件或改写代码带入 Sleepy。

### 4. imwangwang/vivo-apps（无 LICENSE，不可搬）

- Repo: https://github.com/imwangwang/vivo-apps
- License：GitHub API 未返回 LICENSE。
- HEAD：`6aff38cfa6133250fc58a2fdc5f6452c676440aa`，2025-12-19，`initial creation`。
- 文件：[`VivoSuperXNotificationManager.java`](https://github.com/imwangwang/vivo-apps/blob/master/main/java/com/android/server/notification/VivoSuperXNotificationManager.java)，blob `0c652bfadad60c1ad7c6b93f7bd96f9cb49e66f6`。
- 关键行：`62-127` 与 `vivo-service` 同源/同内容的 SuperX 系统实现，提供交叉核验，不构成第二个独立可复用实现。

### 5. fvhde/origin-isle（不可搬，协议参考）

- Repo: https://github.com/fvhde/origin-isle
- GitHub API 的 license 类型为 `NOASSERTION`；仓库 LICENSE 明文写 `Source-Available, No-Redistribution License`，禁止未经书面许可再分发、镜像、修改版本、fork 或衍生作品。
- HEAD：`4c83b6be859eecffcca6b51045ab3b2347ba9589`，2026-09-17；文件路径级 commit：`OriginIslandConstants.kt` 为 `3cacaaf391671179f5686ac6aea26dbb02493fea8`，`docs/PROTOCOL.md` 为 `7eb542d3f96a22504d422a949376bdf8ab3a444`。
- 关键文件：[`OriginIslandConstants.kt`](https://github.com/fvhde/origin-isle/blob/main/app/src/main/java/com/originisle/android/island/OriginIslandConstants.kt)，blob `ad9b306a602cb7375a45ec0384740a06280085d8`；[`docs/PROTOCOL.md`](https://github.com/fvhde/origin-isle/blob/main/docs/PROTOCOL.md)，blob `7c74f0ad408651453556b0d86c2830344633fe23`。
- 关键行：常量文件 `17-45` 列出 top-level SuperX keys；`48-55` 列出 base info keys；协议文档 `13-40` 说明白名单/隐藏接口、tag `VIVO_SUPERX_TAG` 和 notify 生命周期；`43-69` 说明四个子 bundle 与 template。
- 用途：最接近完整公开协议说明，但只能阅读/核对，不能复制 `OriginIslandBuilder`、常量文件或文档内容进 Sleepy。

### 6. death34018-hue/AionsHome（MIT，读取侧）

- Repo: https://github.com/death34018-hue/AionsHome
- License: MIT。
- 关键文件路径级 commit：`ecf4469ae98b4a0d10e173667d3c5a253f4cf73c`，2026-08-23。
- 文件：[`AionNotificationListenerService.java`](https://github.com/death34018-hue/AionsHome/blob/main/AionApp/src/main/java/com/aion/chat/AionNotificationListenerService.java)，blob `2240a5a7e8e7559a6496e6fe5b1d6292422c673b`。
- 关键行：`18-24` 定义 `notification.superx.` 前缀；`41-63` 从 `StatusBarNotification` 读取普通/ SuperX 文本并分类；它是通知监听、提取、转发，不是原子通知发送器。
- 适用：参考如何识别/读取已有 vivo SuperX 通知；不可作为 Sleepy 的发送实现。

### 7. STAR-10086/ShuiKeBang（MIT，vivo 分支需谨慎）

- Repo: https://github.com/STAR-10086/ShuiKeBang
- License: MIT。
- HEAD（核验时）：`bf81736617cb0685036ab83b53b391f43fea4d78`，2026-09-03；关键文件路径级 commit：`9fcb978a30c79d5d209d05871a40636d2d5c0904`。
- 文件：[`VendorIslandNotifier.java`](https://github.com/STAR-10086/ShuiKeBang/blob/main/app/src/main/java/com/star/shuikebang/island/VendorIslandNotifier.java)，blob `7543ae44d376139912c9bca0ccf6b48e99ae6b7c`。
- 关键行：`71` 明确写 vivo 无公开能力查询，真实展示取决于厂商授权且开关默认关闭；`94-124` 仅把 VIVO 路由到自身 `vivoNotifyCapsule/vivoNotifyQuestion/vivoEnd`。
- 适用：参考多厂商降级架构和“授权未知即关闭”策略；不能把它当已验证的 vivo 原子岛实现。

## 不可用/空壳

### 8. 2756826865/android-sms-forwarder（GPL-3.0，未实现）

- Repo: https://github.com/2756826865/android-sms-forwarder
- License: GPL-3.0。
- HEAD：`bea3f8c9463c205b4bacb463409476692c2ea9e8`，2026-09-17；文件路径级 commit：`a9a7309d9c73b424569e72d6011165079b24a52b`。
- 文件：[VivoAtomicIslandProvider.kt](https://github.com/2756826865/android-sms-forwarder/blob/main/app/src/main/kotlin/org/fossify/messages/helpers/liveisland/VivoAtomicIslandProvider.kt)，blob `2422554ceff944bc82a039977bcad7b9ae6e13c5`。
- 关键行：`1-24` 只定义 provider 类型，并注释“Reserved”“enable when vivo atomic notification permission is granted”“extras will be added here after vivo platform approval”；没有 Bundle 写入、notify、capsule 或 island payload。

## 搜索范围与负证据

- GitHub REST code search（核验时共返回）：`"notification.superx"` 43 条结果；`"island.superx"` 13 条结果；`"api-push.vivo.com.cn"` 未发现可作为独立 VPush 客户端的公开实现。
- `notification.superx` 命中的实际候选除本仓外主要是：`fvhde/origin-isle`、`cameleonnbss/originos-toolkit`、`lladlam/MeloX-Android`、`2756826865/android-sms-forwarder`、`STAR-10086/ShuiKeBang`、`death34018-hue/AionsHome`、`imwangwang/vivo-service`、`imwangwang/vivo-apps`，另有 `lingion/sleepy` 自身结果。
- `island.superx` 结果集中没有发现另一套带许可、带完整 payload、独立于上述候选的成熟实现；`originos-toolkit`、`origin-isle` 和 `vivo-service/vivo-apps` 已覆盖主要实现与系统侧来源。
- GitHub `originos` topic 结果主要是设备兼容、ROM/主题、解锁、调优工具，不是原子通知 SDK；`spike0en/better_vivo` 为 CC0 调优资料，未发现 SuperX/OriginIsland 代码。
- Gitee 公开搜索只返回 Gitee 首页/帮助页及阿里云转载的官方文档，未找到可核验的开源 SuperX 实现仓库；因此没有把搜索摘要当作代码证据。
- `Medvedev91/timeto.me` 的公开页面和搜索结果显示为通用时间管理 app，未发现 vivo/OriginOS/SuperX 实现；其 Android Live Updates 不能证明 vivo 原子通知兼容。
- Android 16 `Notification.ProgressStyle` 示例、Material You Dynamic Island、Xiaomi/HyperOS island 桥接和通用 Live Updates 项目均不是 vivo `notification.superx.*` 协议，归入不可直接复用的相邻实现。

## 准入边界

现有 [access-gate.md](./access-gate.md)、[atomic-notification.md](./atomic-notification.md)、[realtime-capsule-api.md](./realtime-capsule-api.md) 已记录官方协议和准入。代码复用只能解决 Bundle 构造/发送形式，不能证明 Sleepy 已获 app/scene 权限，也不能证明 iQOO 与 vivo 在具体机型上的放量矩阵相同。任何未获准入或未放量机型都必须保留普通通知降级路径。
