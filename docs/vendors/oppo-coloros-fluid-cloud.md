# OPPO / ColorOS 流体云接入状态与官方资料

实现复核：2026-10-29（当前仓库代码；本次未执行真机或 DevStudio 验证）

平台资料：2026-07-30 的 OPPO 开放平台官方文档 API 快照；下文 Pantanal / Seedling 规范不代表平台现行准入政策已重新核实。

## 当前接入状态

**Sleepy 已接入 Android 16 Live Updates 通道，用于支持该能力的 ColorOS 流体云展示；Pantanal / Seedling SDK + UPK 专用通道尚未接入。** 两条通道需分别判断，不能用 UPK 未完成推断实时通知也未实现。

| 通道或资产 | 当前状态 | 仓库依据 |
|---|---|---|
| Android Live Updates 通知构建 | 已实现 `ProgressStyle`、`setOngoing(true)`、`setRequestPromotedOngoing(true)` 和 `setShortCriticalText` | [VendorLiveCardRenderer.kt](../../app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt) |
| OPPO / 一加 / realme 展示分支 | 已实现课程信息、倒计时副行、胶囊主字段和进度环图标；未添加 OPPO 私有通知 extras | 同上，`isColorOs` 分支 |
| 前台服务与进度生命周期 | 已接入课程提醒调度；每 15 秒更新进度，上课时结束；测试窗口为 2 分钟，支持主动停止 | [FluidCloudService.kt](../../app/src/main/java/com/lingion/sleepy/widget/notification/FluidCloudService.kt)、[FluidNotificationSession.kt](../../app/src/main/java/com/lingion/sleepy/widget/notification/FluidNotificationSession.kt)、[CourseNotificationScheduler.kt](../../app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt) |
| 权限与设置入口 | Manifest 声明 `POST_NOTIFICATIONS` / `POST_PROMOTED_NOTIFICATIONS`；测试入口检查通知权限、实时更新授权和厂商能力状态 | [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)、[ReminderScreen.kt](../../app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt) |
| OPPO 家族设置跳转 | API 36+ 优先尝试 `android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS`，再依次尝试通知渠道、厂商及应用通知页 | [VendorLiveNotificationAdapters.kt](../../app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdapters.kt) 中的当前候选链 |
| Seedling SDK AAR | 仅存放在 `app/libs/seedling-support-lite-3.0.7.aar`，未声明为构建依赖 | [app/build.gradle.kts](../../app/build.gradle.kts) |
| Seedling Provider 与业务桥接 | 未实现 `SeedlingCardWidgetProvider`，未在 Manifest 注册，也没有课程数据的 Seedling 创建/更新/结束调用 | APK 源码与 Manifest |
| Pantanal UPK | 已有 config、card-config 和 OML 源工程骨架；仓库无生成的 UPK 产物或发布、平台验收证据 | [UPK README](../../oppo-fluid-cloud-upk/README.md) |

### 使用与回退

在「我的 → 提醒」开启课前提醒后，选择实验性「流体云」。默认形态为横幅，两者互斥，切回横幅会停止流体云服务。胶囊主显示内容可选课程名、时间、教室或倒计时；测试入口投放示例课程 2 分钟，也可提前结束。

Android 16 / API 36+ 设备可使用原生 promoted Live Updates。当前 `NotificationCompat.ProgressStyle` 构建在旧系统上回退为普通进度通知；系统未提升通知时，也不能保证出现胶囊。通知权限关闭会影响可见性。

[Android 官方 Live Updates 文档](https://developer.android.com/develop/ui/views/notifications/live-update)说明了通知样式、promoted 请求、权限和持续通知等要求，并明确厂商可增加展示条件。`canPostPromotedNotifications()` 只检查应用是否获准发送 promoted 通知，不能证明某条通知已被提升或显示。当前厂商探测在无法确定时保留 `UNKNOWN`；测试通过权限检查也不代表取得 Pantanal 平台准入。

### 验证范围

- [ColorOS 渲染契约测试](../../app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveCardRendererColorOsContractTest.kt)的注释记录了 RMX6688 / ColorOS 16 的胶囊短文本、卡片进度条和进度环行为；这些是仓库已有记录，本次未重新执行真机验收
- [投放闸门契约测试](../../app/src/test/java/com/lingion/sleepy/widget/notification/FluidCastGateContractTest.kt)和[会话测试](../../app/src/test/java/com/lingion/sleepy/widget/notification/FluidNotificationSessionTest.kt)覆盖权限闸门、状态对账、首帖补发及生命周期等代码行为，不能代替厂商渲染验证
- 「投放中」来自进程内 `FluidCastState` 服务状态，不是通知可见性或系统胶囊展示成功信号；不据此承诺所有 OPPO / 一加 / realme 机型、系统版本及触点可用
- `REPLACE_WITH_OPPO_*`、预留 Provider 名称和本地 AAR 不能作为 Seedling 通道接入成功或平台审核通过的证据

## 官方文档入口

- 流体云产品介绍（doc_id=13270）
  https://open.oppomobile.com/documentation/page/info?id=13270
- 流体云卡片（doc_id=12965）
  https://open.oppomobile.com/documentation/page/info?id=12965
- 流体云模板（doc_id=12658）
  https://open.oppomobile.com/documentation/page/info?id=12658
- 流体云组件（doc_id=12703）
  https://open.oppomobile.com/documentation/page/info?id=12703
- 流体云设置项（doc_id=13330）
  https://open.oppomobile.com/documentation/page/info?id=13330
- 泛在服务产品规范（doc_id=12745）
  https://open.oppomobile.com/documentation/page/info?id=12745
- 快速开始（doc_id=12639）
  https://open.oppomobile.com/documentation/page/info?id=12639
- config.json（doc_id=12645）
  https://open.oppomobile.com/documentation/page/info?id=12645
- card-config.json（doc_id=12646）
  https://open.oppomobile.com/documentation/page/info?id=12646
- SeedlingSupportSDK 接入指南（doc_id=12719）
  https://open.oppomobile.com/documentation/page/info?id=12719
- 服务发布（doc_id=12715）
  https://open.oppomobile.com/documentation/page/info?id=12715
- 泛在服务审核规范（doc_id=12525）
  https://open.oppomobile.com/documentation/page/info?id=12525

正文详情接口（官方文档 SPA 使用）：
POST https://open.oppomobile.com/oneoppoapi/doc/detail
Content-Type: application/json
{"doc_id": 12965}

## Pantanal / Seedling 流体云是什么

以下为 Pantanal / Seedling 专用通道资料。这里的 UPK、SDK、意图、服务 ID 和审核要求不应套用于当前 Android Live Updates 通道。

在这套 OPPO 官方资料中，流体云是泛在服务卡片的一种展示形式。

它由 ColorOS 泛在服务框架负责渲染，支持：

- 气泡
- 胶囊
- 展开面板
- 多入口流转
- 实时状态更新
- 里程碑事件
- 卡片收起/展开
- 卡片点击和面板交互

官方产品介绍明确描述为“气泡、胶囊、面板不同形态呈现”，并将其定义为系统核心组件的实时活动全局触达。

仅有 `IMPORTANCE_HIGH` 和 heads-up 横幅不能证明流体云展示成功；当前已实现的 Live Updates 则需结合系统授权、通知提升和具体机型表现判断。

## Pantanal API 与系统版本

流体云卡片从 API 2.0 / ColorOS 14.0 开始支持。

config.json：

```json
{
  "platform-version": 2000000
}
```

官方平台版本：

- API 1.0 / ColorOS 13.1：1000001
- API 1.1 / ColorOS 13.2：1001001
- API 2.0 / ColorOS 14.0：2000000

快速开始文档要求开发调试设备 ColorOS 不低于 13.1；但流体云能力本身需要 API 2.0 / ColorOS 14.0。

## Pantanal 专用通道的工程形态

这条专用通道需要 Pantanal Service Package / UPK 服务工程，不能仅靠现有 APK 的 Notification channel 完成。

典型结构：

```text
src/
├── assets/
│   ├── images/
│   └── res/
├── i18n/
├── pages/
│   └── index.oml
├── card-config.json
├── config.json
└── seedling.js          # 仅无宿主服务
```

两种模式：

1. 无宿主服务
   - UPK 自带 seedling.js
   - 不需要 APK 集成 SeedlingSupportSDK

2. 有宿主服务
   - Sleepy APK 作为宿主
   - UPK 提供 UI 模板
   - APK 通过 SeedlingSupportSDK 提供数据、生命周期和更新
   - 需要 AndroidManifest 注册 Provider

Sleepy 预留的 UPK 骨架采用“有宿主服务”方向，因为课程数据和提醒调度都在现有 APK 内；当前 APK 尚未实现该宿主桥接。

## 接入前置条件

官方快速开始要求开发者：

1. 完成 OPPO 开放平台开发者认证
2. 联系 OPPO 申请：
   - 授权码
   - 意图 action
   - 意图 domain
   - 服务 ID
3. 提供 applicationId、服务 URL、服务名及 PRD/场景说明
4. 下载 Pantanal DevStudio
5. 如采用有宿主模式，下载 SeedlingSupportSDK
6. 使用 OPPO 手机真机调试

config.json 的 identifier.id 不是任意字符串，必须填写潘塔纳尔服务库分配的服务 ID。

intent.action 和 intent.domain 也不是自定义测试值，必须使用 OPPO 为场景分配的值。

## config.json 关键配置

官方文档给出的核心字段包括：

```json
{
  "identifier": {
    "url": "服务 URL",
    "id": "OPPO 分配的服务 ID",
    "type": "seedling",
    "name": "Sleepy",
    "version": "1.0.0",
    "versionCode": 1000000
  },
  "intent": {
    "action": ["OPPO 分配的 action"],
    "domain": "OPPO 分配的 domain"
  },
  "runtime": {
    "interactive": {
      "seedling-type": "live"
    },
    "entry": ["notification"]
  },
  "meta-data": {}
}
```

流体云服务类型：

- live：实时状态类；未配置时默认 live
- immediate：即时提醒类

课前提醒更接近 immediate；如果要持续显示“下一节课倒计时/课程进行状态”，才适合 live。最终意图和事件类型需要 OPPO 配置。

config.json 的 entry 需要支持 notification。

官方文档同时说明：车机 notification 入口需要 card-config.json 支持 notification_lg。

## card-config.json 关键配置

流体云支持尺寸：

```json
{
  "card": {
    "support": "notification_sm|notification_md|notification_lg"
  }
}
```

含义：

- notification_sm：流体云胶囊形态，包含气泡
- notification_md：流体云通知大胶囊形态
- notification_lg：流体云展开面板形态

如果 config.json 的 notification 入口使用模板展示，必须支持 notification_lg。

这三个字段是系统识别流体云卡片形态的关键之一；普通通知渠道没有这些能力。

## OML 模板

流体云页面使用 OML，不使用普通 Android XML，也不能用 CSS 自由描述布局和样式。

官方支持的模板类型包括：

- modular / 组合模板
- general / 通用模板
- symmetry / 对称模板
- media / 音乐模板

官方新版模板文档还列出 capsule、graphic、text 等模板分类，实际可用模板由平台版本和文档版本决定。

模板只能使用官方允许的组件和绑定变量，不能自行伪造胶囊外观。

常用组件：

- div
- image
- text
- span
- button
- lottie
- widget
- voice
- progress
- video

text 组件超长时会自动滚动，每次滚动后停顿约 3 秒。

## 胶囊与展开面板

流体云组合模板包含：

- 胶囊态/气泡态
- 展开态
- 顶部信息
- 核心信息
- 底部信息

官方明确描述：

- 默认展示核心信息
- 顶部、核心、底部区域按模板规则组合
- 长按胶囊可进入面板
- 面板和胶囊之间存在尺寸切换
- 需要通过 onSizeChanged 感知 notification_sm 与 notification_lg 的变化

这才是“灵动”的系统卡片行为，不是普通 Heads-up 通知。

## 数据更新与 SDK

官方 SeedlingSupportSDK 3.0.7 文档给出的 Maven 依赖：

```gradle
implementation("com.oplus.pantanal.card:seedling-support-liteQuick:3.0.7")
implementation("com.oplus.pantanal.card:seedling-support-external:3.0.7")
implementation("com.oplus.pantanal.card:seedling-support-lite:3.0.7")
```

选择：

- liteQuick：泛在服务 + 快应用轻卡
- external：泛在服务 + 速览
- lite：仅泛在服务

Sleepy 只需要泛在服务方向，理论上选择 lite；但最终以当前 OPPO SDK/平台要求为准。

官方 SDK 自动初始化，不需要额外初始化操作。

有宿主 APK 必须实现 SeedlingCardWidgetProvider，接收：

- onCardCreate
- onShow
- onHide
- onDestroy
- onSubscribed
- onUnSubscribed
- onUpdateData
- onCardObserve

并在 AndroidManifest 注册 Provider，authority 需符合官方规则：

```text
${applicationId}.card.event.provider
```

SDK 回调在子线程中执行，不能直接操作 UI。

## 触发和更新模型

官方模型不是发布 Android 通知，而是：

1. 注册/申请服务
2. 通过意图或 SDK 让系统创建泛在卡片
3. 系统从云端获取 UPK
4. 泛在框架订阅并渲染卡片
5. APK 通过 SeedlingSupportSDK 更新 UI 数据
6. 系统根据入口和等级展示胶囊/面板

IntelligentData 关键字段：

```kotlin
IntelligentData(
    timestamp = System.currentTimeMillis(),
    eventCode = OPPO_ASSIGNED_EVENT_CODE,
    event = "OPPO_ASSIGNED_EVENT_NAME",
    data = params,
    businessData = uiData,
    seedlingCardOptions = cardOptions,
    serviceInstanceId = instanceId
)
```

官方明确要求 eventCode 和 event 与 OPPO 沟通获取，不能自行编造。

示例文档出现过 eventCode=10601 和 20104，但这些是具体服务/示例场景，不应直接拿来当 Sleepy 的生产事件码。

## SeedlingCardOptions 关键字段

官方设置项包括：

- pageId：切换 UPK 页面
- dataSourcePkgName：数据来源应用包名
- requestShowPanel：强制显示面板或胶囊；需要 OPPO 白名单
- requestHideStatusBar：临时隐藏状态栏胶囊/面板
- isMilestone：是否里程碑事件
- grade：入口重要级别；需要与 OPPO 沟通
- notificationIdList：同步需要去重的普通通知 ID
- showHostMap：控制入口显示
- lockScreenShowHostMap：控制锁屏入口显示
- panelActionConfigMap：控制面板滑动、点击外部区域后的收起/消失
- remindType：提醒强度
- extensibleActionMap：传递 shouldShake 等扩展动作

remindType 官方值：

- 0：不显示面板
- 11：面板显示 3 秒
- 12：面板显示 5 秒
- 13：一直显示面板

requestShowPanel 明确写着“需要 OPPO 添加到白名单才能生效”，不能仅靠客户端设置打开。

grade 当前有 GRADE_1 到 GRADE_5，但官方明确要求与 OPPO 沟通，不能自行使用。

## 发布和权限门槛

官方“服务发布”文档写明：泛在服务库目前处于定邀测试阶段。

需要：

- OPPO 开放平台账号
- 企业认证
- 申请成为服务库开发者
- 联系 OPPO 商务：fwst@oppo.com
- 提供 applicationId、服务内容、覆盖度、可用性、运营更新要求等材料
- OPPO 评估后开通泛在服务软件管理和潘塔纳尔服务库入口
- 创建服务并获得服务 ID
- 上传签名后的 UPK
- 预览/真机测试
- 提交审核
- 审核通过后上架

官方写明审核通常在 5 个工作日完成。

因此普通个人 APK 即使实现了 SDK 调用，也不代表 ColorOS 会把它显示为正式流体云；服务 ID、意图、UPK、签名、审核和平台资格都属于链路的一部分。

## Pantanal / Seedling 后续接入步骤

此处仅列专用通道的剩余工作，Android Live Updates 当前已实现的链路见本文开头。

1. 按届时有效的平台规则申请泛在服务资格、service ID 及课程提醒的 intent action/domain、eventCode 和 event 名称
2. 复核已有 [UPK 骨架](../../oppo-fluid-cloud-upk/README.md)：当前为 API 2.0、`immediate`、`notification/statusbar` 入口及 sm/md/lg 尺寸；替换占位值，核对调试/发布宿主配置，并用 Pantanal DevStudio 验证 OML
3. 将合适版本的 Seedling SDK 声明为构建依赖，实现并注册 Provider 生命周期桥接
4. 用真实课程数据接入 SDK 创建、更新和结束卡片，独立验证权限、异常及与普通通知的去重行为
5. 使用对应设备和 OPPO 调试工具验证胶囊、展开、收起和更新，再完成签名、审核及发布

在完成这些步骤前，只能表述为“专用通道材料和源工程骨架已准备”，不能声称 Seedling SDK 已集成、UPK 已发布或平台准入已通过。
