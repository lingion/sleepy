# OPPO / ColorOS 流体云官方接入调查

更新时间：2026-07-30
来源：OPPO 开放平台官方文档 API

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

## 流体云到底是什么

OPPO 官方定义：流体云是泛在服务卡片的一种展示形式，不是普通 Android Notification 的样式扩展。

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

因此：NotificationCompat + IMPORTANCE_HIGH + heads-up 不是流体云。

## API 与系统版本

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

## 必须具备的工程形态

流体云不是直接加到现有 APK 的 Notification channel 上，而是一个 Pantanal Service Package / UPK 服务工程。

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

Sleepy 属于“有宿主服务”方向，因为课程数据和提醒调度都在现有 APK 内。

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

## 对 Sleepy 当前代码的准确判断

当前 Sleepy 已实现：

- Android NotificationChannel
- 课前提醒
- 高优先级通知
- 普通横幅
- 结构化课程文本
- ColorOS 上的 Heads-up 展示

当前 Sleepy 尚未实现：

- Pantanal UPK 工程
- config.json
- card-config.json
- OML 流体云模板
- 流体云页面和组件
- SeedlingSupportSDK
- SeedlingCardWidgetProvider
- OPPO 分配的 service ID
- OPPO 分配的 intent action/domain
- OPPO 事件码和事件名
- OPPO 服务库发布/签名/审核
- requestShowPanel/grade 等白名单能力

准确结论：

```text
普通通知横幅：已实现
Android Heads-up：已在一加真机验证
ColorOS 真正流体云：尚未接入
```

## 推荐实现路径

1. 先申请 OPPO 泛在服务资格和 service ID
2. 申请/确认课程提醒对应的 intent action、domain、eventCode、event 名称
3. 安装 Pantanal DevStudio，创建“有宿主 + 流体云”工程
4. 创建 Sleepy 的 UPK：config.json、card-config.json、OML 页面
5. 选择 immediate 或 live 服务类型
6. 在 card-config.json 支持 notification_sm、notification_md、notification_lg
7. 将 SeedlingSupportSDK lite 3.0.7 集成到 Sleepy
8. 实现 Provider 生命周期和业务数据更新
9. 用真实课程数据调用 SDK 创建/更新泛在卡片
10. 在 OnePlus/ColorOS 设备上用 OPPO 调试工具真机验证胶囊、展开、收起和更新
11. 通过平台审核后再进入正式版本

在拿到 OPPO service ID、intent、event 协议和 SDK/DevStudio 包之前，不应再声称 Sleepy 已支持真正流体云。
