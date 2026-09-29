[evidence=B] 核验日期 2026-09-22 · 范围: GitHub/Gitee 公开户源仓库，按 `miui.focus.param`、`miui.focus.pics`、`miui.focus.actions`、HyperOS/MIUI Super Island 关键词检索；每条均固定到当前 default-branch commit。本文只记录公开代码证据，不替代小米官方接入审核。

# 小米 HyperOS 焦点通知 / 超级岛复用审计

## 结论

- **可直接搬（唯一明确候选）**: [D4vidDf/HyperIsland-ToolKit](https://github.com/D4vidDf/HyperIsland-ToolKit), Apache-2.0。它是独立 Kotlin 库，直接生成 `miui.focus.param` JSON 和 `miui.focus.pics` / `miui.focus.actions` Bundle；demo 以普通 `NotificationCompat.Builder` 发出通知。适合提取为 Sleepy 的小型 adapter，仍须按小米平台的应用/场景审核和真机白名单流程接入。
- **最接近课程表业务，但不可直接搬**: [Mercury000/xiaoaiisland](https://github.com/Mercury000/xiaoaiisland)。它确实把课程通知改造成三阶段（课前倒计时、上课中正计时、下课后正计时）超级岛，但实现是 LSPosed/Xposed 对“超级小爱”通知的注入，依赖宿主通知结构和模块运行环境；仓库没有根目录 license，不能作为可直接引入的依赖。
- **可参考，不直接搬**: `kmod-midori/WearFocus`（解析/转发协议）、`wxxsfxyzm/IslandRecorder`（GPL-3.0，屏幕录制通知的完整标准模板）、`D4vidDf/HyperBridge`（Apache-2.0，但产品是跨应用通知桥，依赖 Shizuku/系统通知监听）、`FrancoGiudans/Capsulyric`（GPL-3.0，媒体歌词/自定义 Focus 解析）。
- **不可用/准入封闭**: `1812z/HyperIsland`（MIT，但核心是 Xposed 注入到 SystemUI，不是普通应用发通知的 adapter）；`C4RL-DANIEL/HyperNotify-Lab`（未发现 license，且本轮未发现 `miui.focus.param` 代码）。任何依赖 root/LSPosed/Shizuku、系统进程 hook 或宿主私有通知结构的实现，都不能作为 Sleepy 普通 APK 的直接接入方案。
- **Gitee**: 本轮以公开代码搜索和候选仓库核验为范围，未找到可核验的 Gitee 项目（同时满足真实 `miui.focus.*` 代码、可定位 commit、可确认 license）。不能把搜索结果中的转载文章/文档代码算作 Gitee 开源实现。

## 可直接搬

### 1. D4vidDf/HyperIsland-ToolKit

- URL: https://github.com/D4vidDf/HyperIsland-ToolKit
- 固定 commit: `9cca1ceed472ba9937c4be3b810223b18a816463` (`main`, 2026-09-21)
- License: Apache-2.0，根目录 `LICENSE`；Gradle 发布元数据也声明 Apache 2.0（`hyperisland-kit/build.gradle.kts:54-58`）。
- 代码路径与证据（以上 commit）：
  - `hyperisland-kit/src/main/java/io/github/d4viddf/hyperisland_kit/HyperIslandNotification.kt:738-755`：构造 `miui.focus.actions` 和 `miui.focus.pics` Bundle，并把 action/icon key 加上 `miui.focus.action_` / `miui.focus.pic_` 前缀。
  - 同文件 `:761-782`：组装 `ParamV2`，序列化为 `miui.focus.param` 所需 JSON；包含 `business`、`timeout`、`updatable`、`param_island`、`actions` 等字段。
  - 同文件 `:880-893`：用 `Build.MANUFACTURER`、`content://miui.statusbar.notification.public` 的 `canShowFocus` 和 `persist.sys.feature.island` 做支持性检查。
  - `demo/src/main/java/com/d4viddf/hyperisland_kit/demo/DemoNotificationManager.kt:1197-1209`：标准模板路径，`addExtras(builder.buildResourceBundle())`、`putString("miui.focus.param", builder.buildJsonParam())`，然后 `NotificationManager.notify`。
  - 同 demo `:1216-1228`：自定义 RemoteViews 路径，使用 `buildCustomExtras()`，再 `notify`。
- Sleepy 复用建议：只拿库的模型/序列化/Bundle 组装思路，优先标准模板；不要搬 demo 全部 1000+ 行。对课表只保留 `business`、短 `ticker`、`baseInfo`、`param_island`、有限 action/icon 和状态更新所需字段。复用前核对该仓库版本是否与目标 Android/Kotlin 工具链兼容。
- 许可证边界：Apache-2.0 允许修改和集成，但保留许可证/版权声明并记录来源；不能把小米平台审核、机型覆盖或模板字段支持误认为该库的保证。

## 最接近课程表，但不可直接搬

### 2. Mercury000/xiaoaiisland

- URL: https://github.com/Mercury000/xiaoaiisland
- 固定 commit: `ba82092e3880ef0f15c0c5bd766adcde228a8127` (`main`, 2026-09-09)
- License: GitHub API 返回 `NONE`；根目录没有 `LICENSE`，仅 `hyperx-compose/LICENSE` 存在，不能推定主模块被同一许可覆盖。
- 代码路径与证据：
  - `app/src/main/java/com/xiaoai/islandnotify/hook/MainHook.java:2056-2062`：`applyIslandParams` 在 `nm.notify()` 前注入超级岛参数，并安排状态更新/取消闹钟。
  - 同文件 `:2079-2088`：根据当前时间计算 `倒计时/上课中/已下课` 三状态并构造 extras。
  - `app/src/main/java/com/xiaoai/islandnotify/hook/IslandContentBuilder.java:468-481`：解析现有 `miui.focus.param`，向 `param_v2.param_island` 注入 `highlightColor`。
  - 同文件 `:2057` 的注释明确是“向通知注入超级岛参数”，README 将项目定位为课程表超级岛。
- 为什么不可直接搬：这不是 Sleepy 自己创建通知的普通 Android API；它 hook 宿主应用（小爱/课程表）并依赖 LSPosed/Xposed 的注入入口、宿主字段和系统环境。还缺少主工程的明确开源许可证。**可参考业务状态机与课程节点更新，不可复制代码或作为依赖。**

## 可参考

### 3. kmod-midori/WearFocus

- URL: https://github.com/kmod-midori/WearFocus
- 固定 commit: `0a487c37d3102b3edf96474743aa5dc63ebd4c0f` (`main`, 2026-05-28)
- License: GitHub API 返回 `NONE`；未发现根目录 license。
- 代码路径与证据：
  - `common/src/main/java/moe/reimu/wearfocus/common/FocusParam.kt:10-26`：解析 `miui.focus.param` 顶层 JSON，`param_v2` 映射为结构化模型。
  - 同文件 `:29-36`：记录 `miui.focus.pics`、`miui.focus.actions` 和 RemoteViews extras 形态。
  - `mobile/src/main/java/moe/reimu/wearfocus/FocusNotificationService.kt:111-135`：从 `StatusBarNotification.extras` 读取标准/自定义焦点通知。
  - 同文件 `:137-177`：将 JSON 压缩、提取图片 Bundle 并经 Wear OS Data Layer 转发。
- 用途：可参考字段解析、标准/自定义路径区分和 icon Bundle 处理；无许可证，禁止直接复制进 Sleepy。

### 4. wxxsfxyzm/IslandRecorder

- URL: https://github.com/wxxsfxyzm/IslandRecorder
- 固定 commit: `51faf047c4ef683ca3291961c06b6affa28ace3d` (`main`, 2026-07-29)
- License: GPL-3.0（根目录 `LICENSE`）。
- 代码路径与证据：`app/src/main/java/com/island/recorder/framework/notification/RecordingNotificationManager.kt:232-306`。
  - `:255-300` 构造 `param_v2`，设置 `protocol=1`、`updatable=true`、`business=screen_recording`、`param_island` 和两个 action 引用。
  - `:302-306` 把 JSON、`miui.focus.actions`、`miui.focus.pics` 放入 Bundle。
- 用途：参考持续性状态、计时器和 action/picture key 的配对方式。GPL-3.0 与 Sleepy 当前集成边界不匹配，除非明确采用相容的整体分发方案；不直接搬入现有 APK。

### 5. D4vidDf/HyperBridge

- URL: https://github.com/D4vidDf/HyperBridge
- 固定 commit: `e19ede13b87150028d89d31106ed898660e4c145` (`master`, 2026-09-19)
- License: Apache-2.0（根目录 `LICENSE`）。
- 代码路径与证据：`app/src/main/java/com/d4viddf/hyperbridge/service/NotificationReaderService.kt:305-325` 和 `:1080-1086`。
  - `:305-323` 从通知桥生成 `HyperIslandNotification`，构造资源 Bundle 和 JSON。
  - `:323-325` 将 `miui.focus.param` 写入通知并通过 `ShizukuManager.notify` 发出。
  - `:1080-1086` 处理普通通知发出/取消路径。
- 用途：参考“收到既有通知 -> 翻译成岛通知”的更新、取消和去重思路；不能直接搬其 Shizuku、NotificationListener、跨应用桥接权限和产品状态管理。Apache-2.0 只解决代码许可，不解决运行时准入。

### 6. FrancoGiudans/Capsulyric

- URL: https://github.com/FrancoGiudans/Capsulyric
- 固定 commit: `45703be68f48e12d203e73268e668acb16a98541` (`main`, 2026-08-29)
- License: GPL-3.0（根目录 `LICENSE`）。
- 代码路径与证据：
  - `app/src/main/java/com/example/islandlyrics/ui/overlay/superisland/SuperIslandHandler.kt:283-287`：读取 `miui.focus.param` / `miui.focus.param.custom` 并以签名去重。
  - `app/src/main/java/com/example/islandlyrics/ui/overlay/superisland/render/SuperIslandCustomFocusBuilder.kt:185-204`：合并 custom 与 standard Focus JSON 的 `param_island`，缺失时记录日志。
- 用途：参考媒体通知的 custom/standard 合并和重复更新抑制；GPL-3.0、媒体专用且以监听/渲染为主，不能直接搬到课表通知发送链路。

## 不可用 / 准入封闭

### 7. 1812z/HyperIsland

- URL: https://github.com/1812z/HyperIsland
- 固定 commit: `b5cc58aae570e038b9117b0bd1dcc24bcd2224e1` (`main`, 2026-09-21)
- License: MIT（根目录 `LICENSE`）。
- 证据：`android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/SystemUI/NotificationHook.kt`（958 行）和 `.../islanddispatch/invoke/IslandDispatcherNotifier.kt`（502 行）是核心 SystemUI/Xposed hook；GitHub code search 在 `NotificationHook.kt`、dispatcher 和 renderer 中命中 `miui.focus.param`。
- 结论：许可可用不等于集成可用。它修改/拦截系统 UI 通知流，依赖 Xposed/root/系统进程，不是 Sleepy 普通 APK 的可复用适配层。

### 8. C4RL-DANIEL/HyperNotify-Lab

- URL: https://github.com/C4RL-DANIEL/HyperNotify-Lab
- 固定 commit: `7f24bdc88af3538001cc2a32323d18080807780c` (`main`, 2026-09-20)
- License: GitHub API 返回 `NONE`。
- 证据：GitHub code search 对 `repo:C4RL-DANIEL/HyperNotify-Lab miui.focus.param` 返回 `total=0`；README/项目定位是 Android 16/HyperOS 平板通知测试器，但没有本轮可定位的官方 Focus extra 实现。
- 结论：只能列为搜索到的相邻项目，不能当作已实现且可复用的 Xiaomi Focus 代码；虽然记录了当前 commit，但没有可引用的实现文件，不应作为依赖。

## 查过范围与负证据

- 公开代码关键词：`miui.focus.param`、`miui.focus.param.custom`、`miui.focus.pics`、`miui.focus.actions`、`notification_focus_protocol`、`HyperOS`、`HyperIsland`、`Super Island`。
- 托管范围：GitHub 公共代码/仓库搜索；Gitee 公共搜索结果与候选页面核验。
- 正向证据：找到 7 个 GitHub 仓库中的真实源码命中；其中只有 `HyperIsland-ToolKit` 同时满足“普通 Android 应用可调用 + 公开 license + 直接构造/发送 Focus 通知”。
- 课程表正向证据：只有 `xiaoaiisland` 明确实现课程三阶段状态机，但它是 Xposed/LSPosed 注入且主工程无 license，因此只能参考行为模型。
- 负证据：没有找到可确认 license、可作为普通 APK 依赖、且专为课表/日程设计的独立 Xiaomi Focus adapter；没有找到满足同样条件的 Gitee 实现。
- 这些负证据不等于“互联网不存在”，只表示截至 2026-09-22、本次公开索引与候选仓库核验范围内没有可准入实现。

## Sleepy 采用建议

1. 直接实现时优先以官方文档为协议真相，复用 `HyperIsland-ToolKit` 的 Apache-2.0 模型/序列化/Bundle 结构；保留来源和许可证文本。
2. 课表状态机可参考 `xiaoaiisland` 的三阶段语义，但在 Sleepy 自己的通知更新链中实现，不引入 LSPosed/Xposed、Shizuku 或宿主注入。
3. 默认只实现官方已确认的标准模板字段；模板库未落盘的字段不要从第三方猜测扩大。无权限、非 Xiaomi、OS2/OS3 不匹配时回退普通通知。
4. 复用前仍需单独做许可证法务确认、APK 体积/依赖审计、真机白名单联调和平台审核；开源代码不能绕过小米准入。
