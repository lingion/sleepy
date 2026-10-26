# 官方实时通知 / 实况窗 / 胶囊 SDK 示例仓库核验

> 核验日期：2026-09-22。范围限定为厂商官方 GitHub/Gitee 组织账号，目标是能证明实时通知、实况窗、胶囊、焦点通知或其官方 SDK 接入的公开仓库。普通推送通知示例只有在源码中确实调用通知 API 时才列为正例；不能把“仓库名含 sample/push”当作实时卡片证据。
>
> 元数据来源：GitHub REST API（仓库元数据、许可证、默认分支、最近提交、源码文件）；源码行号按对应提交内容计算。`NOASSERTION` 表示 GitHub 没识别到 SPDX 许可证，不能等同于 Apache-2.0。Maven 坐标只在示例构建文件明确出现时记录；“可公开拉取”表示坐标来自公开 Maven 依赖声明，不代表该 SDK 提供实时卡片能力。

## 结论摘要

| 厂商 | 官方实时卡片 / 实况窗正例 | 结论 |
|---|---|---|
| 华为 | 2 个 HMS-Core Push 示例（Push 通知，不是 Live View Kit 实况窗） | 找到可复核官方通知 API；未找到公开 Live View Kit 示例仓库 |
| 荣耀 | 无 | 未找到官方胶囊 SDK / 示例仓库 |
| 小米 | 无 | 找到 XiaoMi 组织，但未找到焦点通知 / 超级岛 SDK 示例 |
| OPPO / Pantanal | 无 | 未找到官方 Pantanal / 流体云示例组织或仓库 |
| 一加 / realme | 无 | 未找到官方实时卡片示例仓库 |
| vivo | 无 | 未找到官方原子通知 / 实时胶囊示例仓库 |
| 三星 | 无 | Samsung 组织有 Tizen 样例，但未找到 Android 实时胶囊 / Live Notification SDK 样例 |
| 魅族 | 无 | MEIZU 官方 GitHub 组织无公开仓库；未找到可核验官方 Flyme 实况通知样例 |

## 一、可核验正例

### 1. Huawei / HMS-Core：Android Push Kit client demo

- 仓库：<https://github.com/HMS-Core/hms-push-clientdemo-android>
- 官方组织核验：`HMS-Core` 是 GitHub Organization；仓库属于该组织。
- 默认分支：`master`
- License：`Apache-2.0`；文件：<https://github.com/HMS-Core/hms-push-clientdemo-android/blob/master/LICENSE>
- 最近提交（核验时）：`2c85fdd0624ae3798cd5ea438545f8743e30ece8`，2023-08-22；提交页：<https://github.com/HMS-Core/hms-push-clientdemo-android/commit/2c85fdd0624ae3798cd5ea438545f8743e30ece8>
- 真实通知 API 调用：<https://github.com/HMS-Core/hms-push-clientdemo-android/blob/2c85fdd0624ae3798cd5ea438545f8743e30ece8/KotlinApp/src/main/java/com/huawei/loveandshare/MyPushService.kt>
  - 第 21 行：`import com.huawei.hms.push.RemoteMessage`
  - 第 56 行：`override fun onMessageReceived(message: RemoteMessage?)`
  - 第 100 行：读取 `message.notification`
  - 第 134 行：写入 `onMessageReceived` 处理结果。
- Maven 依赖：`com.huawei.hms:push:6.7.0.300`；文件：<https://github.com/HMS-Core/hms-push-clientdemo-android/blob/2c85fdd0624ae3798cd5ea438545f8743e30ece8/KotlinApp/build.gradle>，第 54-55 行（第 55 行为 `implementation 'com.huawei.hms:push:6.7.0.300'`）。这是公开 Maven 坐标；该仓库证明的是 HMS Push 通知，不证明 Live View Kit / 实况窗能力。

### 2. Huawei / HMS-Core：HarmonyOS Push Kit demo

- 仓库：<https://github.com/HMS-Core/hms-pushkit-ohos-demo>
- 官方组织核验：`HMS-Core` GitHub Organization。
- 默认分支：`main`
- License：`Apache-2.0`；文件：<https://github.com/HMS-Core/hms-pushkit-ohos-demo/blob/main/LICENSE>
- 最近提交（核验时）：`6b1a92b0763a5ef22aa95bd05aeb8cb2d16a93fd`，2023-05-04；提交页：<https://github.com/HMS-Core/hms-pushkit-ohos-demo/commit/6b1a92b0763a5ef22aa95bd05aeb8cb2d16a93fd>
- 真实通知 API 调用：<https://github.com/HMS-Core/hms-pushkit-ohos-demo/blob/6b1a92b0763a5ef22aa95bd05aeb8cb2d16a93fd/entry/src/main/java/com/huawei/ohos/pushsample/DemoHmsMessageServiceAbility.java>
  - 第 7 行：`import com.huawei.hms.push.ohos.HmsMessageService`
  - 第 12 行：`DemoHmsMessageServiceAbility extends HmsMessageService`
  - 第 14 行：`onMessageReceived(ZRemoteMessage message)`
  - 第 15 行：读取 `message.getData()`。
- Maven 依赖：`com.huawei.hms:push-ohos:5.2.0.306`；文件：<https://github.com/HMS-Core/hms-pushkit-ohos-demo/blob/6b1a92b0763a5ef22aa95bd05aeb8cb2d16a93fd/entry/build.gradle>，第 25 行。该坐标是公开依赖声明；示例仍是 Push Kit 消息服务，不是 Live View Kit 实况窗。

## 二、按厂商的负证据与检索范围

### 华为（HuaweiDevEco / huaweicloud / HiHarmonyOS / hms-*）

已核验的 GitHub 组织：

- `HuaweiDevEco`：`GET /orgs/HuaweiDevEco` 返回 404；未形成可检索的官方组织仓库集合。
- `huaweicloud`：组织存在，公开仓库约 163 个。按仓库名及源码相关关键词检索 `live`、`view`、`notification`、`capsule`、`island`、`focus`、`push`、`sample`、`demo`；仅发现云服务/Push 一般样例，未发现 Live View Kit / 实况窗示例。`huaweicloud` 云 SDK 与移动端实况窗无关。
- `HiHarmonyOS`：`GET /orgs/HiHarmonyOS` 返回 404。
- `HMS-Core`：公开仓库约 110 个；检索 `live`、`view`、`notification`、`capsule`、`island`、`focus`、`push`、`sample`、`demo`。上面两个 Push 样例是可复核正例；未找到名称或源码能证明 Live View Kit 的公开示例。
- `Harmony-OS`：组织存在但公开仓库为无关的 `XboxLiveAPI`；无 Huawei Live View 示例。
- `openharmony`：组织存在且有通知框架源码/应用样例，但这些是 OpenHarmony 项目本身，不是 HuaweiDevEco/HMS 的厂商 Live View Kit 样例；未将其冒充华为私有实况窗 SDK。

### 荣耀（hihonor / honor-os）

- `GET /orgs/hihonor`：404。
- `GET /orgs/honor-os`：404。
- `GET /orgs/honor`：404（GitHub API 组织查询）。
- GitHub 关键词范围：`hihonor`、`honor-os`、`honor capsule`、`MagicOS capsule`、`dynamic capsule notification`、`live notification`；未找到可归属于荣耀官方组织的公开 SDK 示例。
- 结论：无正例，不能从公开官方 GitHub/Gitee 仓库给出 groupId:artifactId:version；现有资料中的“灵动胶囊”不能据此推导存在公开 Maven SDK。

### 小米（Xiaomi-MiPush / micredit / HyperOS SDK samples）

- `GET /orgs/Xiaomi-MiPush`：404。
- `GET /orgs/micredit`：404。
- `GET /orgs/HyperOS-SDK`：404。
- `XiaoMi` 组织存在（约 83 个公开仓库），但按 `focus`、`miui.focus`、`super island`、`HyperOS SDK`、`live notification`、`capsule`、`notification`、`sample` 检索未发现官方焦点通知/超级岛 SDK 示例；组织中能命中的样例为与实时卡片无关的 `PowerTestDemo` 等。
- `XiaomiMiMo` 组织存在（约 18 个公开仓库），未发现上述关键词对应的系统通知/焦点通知 SDK 示例。
- Gitee 范围：检索 `Xiaomi-MiPush`、`小米 焦点通知 示例`、`HyperOS SDK sample`、`miui.focus.param`；未找到能同时证明官方组织归属、许可证、源码 API 调用和 Maven 坐标的仓库。
- 结论：无可核验正例。不能把社区使用 `miui.focus.param` 的 demo 当作小米官方 SDK 示例，也未发现公开 Maven 坐标。

### OPPO / Pantanal（含 oneplus、realmedeveloper）

- `GET /orgs/OPPO-Pantanal`：404。
- `GET /orgs/oneplus`：404。
- `GET /orgs/realmedeveloper`：404。
- `GET /orgs/realme`：404（按组织 API 查询）。
- GitHub 关键词范围：`OPPO Pantanal SDK`、`fluid cloud sample`、`流体云 示例`、`oneplus live notification`、`realme live card`、`泛在服务`、`com.oplus`、`com.heytap`；未找到可归属官方组织的公开示例仓库。
- Gitee 范围：`OPPO Pantanal`、`一加 流体云 SDK`、`realme 流体云 示例`；未找到可复核官方仓库及源码调用。
- 结论：无正例；无可从公开官方仓库确认的 Maven `groupId:artifactId:version`。

### vivo（vivo-open-source）

- `GET /orgs/vivo-open-source`：404。
- GitHub 关键词范围：`vivo-open-source`、`vivo atomic notification sample`、`vivo realtime capsule SDK`、`superx notification`、`原子通知 示例`、`实时胶囊 API`；未找到官方组织下的公开实时通知 SDK 示例。
- Gitee 范围：`vivo-open-source`、`vivo 原子通知 SDK`、`vivo 实时胶囊 示例`；未找到同时具备官方归属、LICENSE、源码调用和 Maven 坐标的仓库。
- 结论：无正例；不能据现有公开仓库给出 Maven 坐标。

### 三星（SamsungGalaxyDeveloperPrograms / samsung）

- `GET /orgs/SamsungGalaxyDeveloperPrograms`：404。
- `Samsung` 组织存在（约 187 个公开仓库）；按 `live`、`notification`、`capsule`、`island`、`Galaxy live`、`real-time notification`、`Android sample` 检索，未找到 Galaxy Android 实时胶囊/Live Notification SDK 样例。
- `Samsung/tizen-samples` 存在但仓库为空，GitHub commits API 返回 409 `Git Repository is empty`；不能作为源码正例。
- `Samsung/Tizen-CSharp-Samples`、`Samsung/tizen-extension-sample` 是 Tizen 样例，未发现目标 Android 实时卡片 API 调用，因此未列为正例。
- Gitee 范围：`SamsungGalaxyDeveloperPrograms`、`三星 灵动胶囊 SDK`、`Galaxy live notification sample`；未发现官方公开仓库。
- 结论：无正例；无目标 SDK Maven 坐标。

### 魅族（flyme）

- `GET /orgs/flyme`：404。
- `GET /orgs/Meizu` 与 `GET /orgs/meizu` 均解析到 `MEIZU` 组织，但公开仓库数为 0；无可读取的仓库、LICENSE、commit 或示例源码。
- GitHub 关键词范围：`flyme live notification`、`Meizu capsule SDK`、`notification.live`、`Flyme 实况通知 示例`；未找到厂商官方公开仓库。
- Gitee 范围：`flyme 实况通知`、`魅族 实况通知 SDK`、`notification.live`、`Flyme live card sample`；未找到能证明为官方组织账号并含源码 API 调用的公开样例。
- 结论：无正例；社区 demo 或隐藏 extras 反射用法不能标记为官方 SDK，也不能给出公开 Maven 坐标。

## 三、口径限制

1. “Push Kit 能发通知”与“实时卡片/实况窗 SDK”是两件事。上面华为两个正例只证明通知消息回调和公开 Maven 依赖，不能证明 Live View Kit 已在 GitHub/Gitee 开源。
2. 仓库最近提交时间是仓库 `pushed_at` 与首个 commits API 记录的核验快照，不代表 SDK 版本发布日期。
3. 对负证据只声明“在列出的官方组织、组织 API 和关键词范围内未找到”，不把搜索未命中扩大为厂商绝对不存在该能力。
4. 本文件未改动任何单厂商目录。
