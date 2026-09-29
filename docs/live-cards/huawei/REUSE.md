# 华为实况窗 / Live View 开源代码复用核验

> 核验日期：2026-09-22。目标是寻找公开 GitHub/Gitee 仓库中已经实现 Huawei/HarmonyOS 实况窗、Live View 或通知适配的真实代码，不把官方宣传文档或 Markdown 代码片段误算为可运行实现。
>
> 结论先行：目前只找到 1 个满足“公开仓库 + 可编译 ArkTS 工程 + 真实 Live View API 调用”的实现：华为官方 Gitee 样例。没有找到第三方公开仓库中可直接搬用的 Huawei Live View 实现。

## 可直接搬

### 1. Huawei 官方 Gitee Live View Kit 样例

- **仓库**：`https://gitee.com/harmonyos_samples/live-view-kit_-sample-code_-clientdemo_-arkts`
- **固定核验 commit**：`c84e8d72761747480d783be8b5f67e2987e94a02`
- **许可证**：Apache-2.0；仓库 `LICENSE` 第 1-15 行明确版权和 Apache License 2.0。
- **真实性**：这是完整 HarmonyOS ArkTS 工程，不是文档仓库；固定 commit 下有 `entry/src/main/ets/`、`AppScope/`、`hvigor/` 和多个可运行页面/工具类。
- **覆盖场景**：即时配送、打车、火车、排队、赛事比分、导航、航班、计时、租赁、取餐、运动等多个页面；每个场景有独立的 `*LiveViewUtil.ets` 或页面控制器。

**最小可搬生命周期骨架**（配送场景）：

- `entry/src/main/ets/utils/DeliveryLiveViewUtil.ets:16-20`：导入 `liveViewManager`、错误类型、上下文和图片工具。
- `.../DeliveryLiveViewUtil.ets:30-35`：先调用 `isLiveViewEnabled()`，关闭时返回失败。
- `.../DeliveryLiveViewUtil.ets:37-46`：构造 `LiveView`，再调用 `liveViewManager.startLiveView(...)`。
- `.../DeliveryLiveViewUtil.ets:56-68`：更新序号和状态。
- `.../DeliveryLiveViewUtil.ets:270`：调用 `liveViewManager.updateLiveView(...)`。
- `.../DeliveryLiveViewUtil.ets:314-317`：写入结束态后调用 `liveViewManager.stopLiveView(...)`。
- `.../DeliveryLiveViewUtil.ets:324-334`：封装开关检查和错误日志。
- `.../DeliveryLiveViewUtil.ets:337-380`：构造 `event: 'DELIVERY'`、primary 卡片、扩展区和 capsule。

该样例还实际展示了进度模板和胶囊数据，例如同文件 `:85-100` 的 `LAYOUT_TYPE_PROGRESS`、进度值、节点图标和 `CAPSULE_TYPE_TEXT`。这是当前唯一可直接作为 HarmonyOS ArkTS 实现起点的公开仓库。

**不能直接搬的部分**：样例是 HarmonyOS 原生 ArkTS 工程，依赖 `@kit.LiveViewKit`、HarmonyOS SDK、DevEco 构建链和华为侧服务权益；不能移植到当前 Android APK 的 Kotlin/Compose 进程，也不能绕过华为实况窗开通/审核。

## 可参考

### 2. liasica/harmonyos-skills：官方文档抓取中的可执行代码片段

- **仓库**：`https://github.com/liasica/harmonyos-skills`
- **固定核验 commit**：`1560b86e99ea12165736331e6a987fdf86d8ad3e`
- **许可证**：MIT（仓库 `LICENSE`）。
- **分类**：可参考，不是可直接搬的工程。Live View 内容位于 Markdown 文档快照，不是 `.ets` 源文件或可编译应用。

有价值的真实代码片段：

- `harmonyos/references/best-practices/bpta-video-background-export.md:332-348`：创建 `LiveView`、调用 `startLiveView`、捕获错误并保存模板。
- 同文件 `:351-382`：以 `isUpdating` + `nextProgress` 合并连续更新请求，避免并发更新。
- 同文件 `:384-397`：用时间戳和单调进度过滤过于频繁或回退的更新。
- 同文件 `:399-434`：设置结束态、`keepTime`、胶囊内容并调用 `stopLiveView`。
- `harmonyos/references/best-practices/bpta-lock-screen-immersive-live-window.md:65-94`：锁屏沉浸实况窗所需的 `liveViewLockScreenAbilityName` 和参数字段。
- `harmonyos/references/best-practices/bpta-shared-bicycle.md:352-378`：说明本地实况窗依赖应用进程，Push Kit 可用于远程更新/结束，并展示 `RENT` 场景创建封装。

这些片段适合作为并发、频控、结束态和锁屏关联的设计参考；不能把 Markdown 中的示例类、上下文或 SDK 类型当成已经可编译的第三方实现。该仓库内容本身还是官方文档的镜像/抓取，代码版权和使用边界应按其 MIT 仓库许可及被抓取文档的原始权利分别确认。

### 3. HMS-Core/hms-push-serverdemo-java：Push Kit 后端发送参考

- **仓库**：`https://github.com/HMS-Core/hms-push-serverdemo-java`
- **固定核验 commit**：`63d96aeb9111c1f3759c669df8a7bf9fb86aa6bd`
- **许可证**：仓库有 `LICENSE`，Apache-2.0（HMS Core 官方 Push Kit server demo）。
- **真实代码范围**：Java 服务端消息客户端、access token 和发送请求封装；例如 `src/main/java/com/huawei/push/messaging/HuaweiMessageClient.java:29-38` 定义发送接口。
- **复用价值**：如果将来获得华为 Live View 云侧权限，可参考其 Push Kit 服务端认证/发送结构。
- **明确限制**：该仓库没有 `liveViewManager`、ArkTS `LiveView` 对象或 Live View payload/schema；不能作为 Huawei 实况窗实现，不能单独实现实况窗。

## 不可用 / 准入封闭

### 4. yonglunyao/harmonyos-docs-fetcher：文档快照

- **仓库**：`https://github.com/yonglunyao/harmonyos-docs-fetcher`
- **固定核验 commit**：`bbbfd4dd840f9d067ec1a1ac1ed22b8b16dc3205`
- **许可证核验**：仓库根目录没有 `LICENSE`/`COPYING` 文件；不能把它当有明确开源许可的代码依赖。
- **负证据**：`harmonyos-docs-full/` 下的 Live View 内容是 `.md` 文档快照，例如 `guides/应用服务/Live View Kit（实况窗服务）/开发实况窗场景/构建本地实况窗.md` 和 `references/应用开发/liveview-liveviewmanager.md`；没有可编译 `.ets` 应用工程。
- **结论**：不可作为代码依赖；最多查资料，且应回到华为原始开发者文档核对版本和许可。

### 5. GitHub 上的 Android “Dynamic Island / Live Updates” 项目

已有跨厂商盘点中的 `appsfolder/livebridge`、`1812z/HyperIsland`、`D4vidDf/HyperBridge`、`abh80/smart-edge`、`cengiztoru/JetIsland` 等是真实开源项目，但它们分别是 Android 16 Live Updates、HyperOS/LSPosed 模块或悬浮窗模拟；没有 Huawei `@kit.LiveViewKit` / `liveViewManager` 实现。

- 它们不能获得华为系统级胶囊/卡片位。
- LSPosed/Xposed 路线需要 root/解锁和特定系统，不能作为 Sleepy 普通用户产品依赖。
- Android 16 Live Updates 是另一套 Android API，不能替代 HarmonyOS Live View Kit。
- 详见现有盘点：`docs/live-cards/_cross-vendor/opensource-replicas.md:24-52`。

## 已查范围与负证据

- GitHub 镜像仓库/搜索：`liasica/harmonyos-skills`、`yonglunyao/harmonyos-docs-fetcher`、`HMS-Core/hms-push-serverdemo-java`；搜索 `LiveViewKit`、`liveViewManager.startLiveView`、`@kit.LiveViewKit`、`LiveViewLockScreenExtensionAbility`。
- Gitee：`harmonyos_samples/live-view-kit_-sample-code_-clientdemo_-arkts`，确认官方完整工程；同时用 Gitee/本地 searXNG 搜索 HarmonyOS 实况窗与 `liveViewManager`。
- 本地已有资料：`community-notes.md`、`live-view-access-gate.md`、`live-view-gaps.md`、`live-view-kit.md`、`live-view-terms.md`，以及 `_cross-vendor/opensource-replicas.md`。
- 没有发现第三方（非 Huawei/HMS 官方）公开仓库同时满足：真实可编译 HarmonyOS ArkTS 工程、Live View Kit API 调用、明确可复用许可证。

## Huawei 准入边界（复用代码不能绕过）

已有 `live-view-access-gate.md` 记录的门槛仍适用：Push Kit 服务、Live View Kit 权益、AppGallery 上架、HarmonyOS 5+ 设备和华为侧审核/正式权限。代码仓库开源只解决客户端代码参考，不等于 Sleepy 获得实况窗服务资格；当前 Android APK 不能通过复制 ArkTS 文件获得该能力。

## 最终判定

| 分类 | 仓库/材料 | 判定 |
|---|---|---|
| 可直接搬 | Huawei 官方 Gitee Live View Kit sample | 有真实 ArkTS 工程和 API 生命周期；仅适用于 HarmonyOS 原生项目 |
| 可参考 | liasica/harmonyos-skills | MIT；官方文档快照中的并发/频控/锁屏代码片段 |
| 可参考 | HMS-Core/hms-push-serverdemo-java | Apache-2.0；Push Kit 服务端发送，不含 Live View |
| 不可用/准入封闭 | yonglunyao/harmonyos-docs-fetcher | 无 LICENSE，文档快照，无可编译实现 |
| 不可用/准入封闭 | Android Dynamic Island / HyperOS / Live Updates 项目 | 非 Huawei Live View API，不能取得 HarmonyOS 系统级位 |
