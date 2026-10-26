[evidence=A] 抓取 2026-09-17 · OPPO 开放平台 `POST /oneoppoapi/doc/detail` · doc_id=12639 (快速开始) / 12645 (config.json) / 12646 (card-config.json) / 12719 (SeedlingSupportSDK) / 13330 (SeedlingCardOptions 设置项) / 12703 (流体云组件) / 12658 (流体云模板)

# OPPO 流体云 — API 字段全量

> 流体云不是单一 API, 是四件套:
> ① UPK 工程描述文件 `config.json` (服务维度)
> ② UPK 工程描述文件 `card-config.json` (卡片维度)
> ③ OML 模板描述文件 (UI 维度, 见 fluid-cloud-overview.md)
> ④ Android SDK 类 `SeedlingCardWidgetProvider` + `SeedlingCardOptions` (宿主 APK 维度)
> 本文件按四件套逐表列出每个字段.

---

## 一、config.json (服务维度, doc 12645)

工程根目录 `src/config.json`. 顶层 4 个对象:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `identifier` | 对象 | 是 | 服务基础信息 |
| `intent` | 对象 | 是 | 分发信息 (由 OPPO 分配) |
| `runtime` | 对象 | 是 | 运行信息 |
| `meta-data` | 对象 | 是 | 扩展配置信息 |

### 1.1 `identifier` 子字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `url` | String | 是 | 唯一标识符, 即服务的"包名", 大小写敏感. 字母/数字/下划线/点号组成, 必须字母开头. 长度 7~127 字节. 反向域名形式, 一级建议 `com` |
| `id` | String | 是 | 服务 ID, 唯一身份号, 由潘塔纳尔服务库分配. 大小写敏感 |
| `type` | String | 是 | `seedling` (泛在服务) / `apk` (速览服务) / `rpk` (快应用服务) |
| `name` | String | 是 | 服务名称, ≤255 字节, 大小写敏感. 动态多语言写法: `"@string:string.name"` |
| `description` | String | 是 | 服务副标题, ≤255 字节, 同上多语言写法 |
| `version` | String | 是 | 版本号, ≤127 字节. 格式 `A.B.C` 或 `A.B`, 数字 0~999 |

### 1.2 `intent` 子字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `action` | String[] | 是 | 意图动作列表, OPPO 分配 |
| `domain` | String | 是 | 意图域, OPPO 分配 |

### 1.3 `runtime` 子字段 (流体云相关)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `platform-version` | Int | 是 | 流体云 = `2000000` (API 2.0, ColorOS 14.0) |
| `interactive.seedling-type` | String | 是 | `live` (实时状态) / `immediate` (即时提醒). 默认 `live` |
| `use-template` | String | 否 | 车机入口选择 `desktop` / `notification` (流体云) |

### 1.4 完整 config.json 示例 (流体云 + 有宿主, doc 12639)

```json
{
  "identifier": {
    "url": "com.example.helloseedling",
    "id": "替换成你的服务ID",
    "type": "seedling",
    "name": "HelloSeedling",
    "version": "1.0.1",
    "versionCode": 1000001
  },
  "intent": {
    "action": ["替换成你的意图 action"],
    "domain": "替换成你的意图 domain"
  },
  "runtime": {
    "platform-version": 2000000,
    "interactive": {
      "seedling-type": "live"
    }
  },
  "meta-data": {}
}
```

---

## 二、card-config.json (卡片维度, doc 12646)

工程 `src/assets/card-config.json`. 顶层 3 个对象:

### 2.1 `host` (宿主信息, 无宿主无需配置)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `packageName` | String | 是 | 宿主包名, 大小写敏感 |
| `componentName` | String | 是 | 宿主实现数据通信接口的 Provider 全名 |
| `minHostVersion` | Int | 是 | 兼容该卡片的最小宿主 APK versionCode. UPK 依赖宿主变更时**必须升级**此号 |

### 2.2 `card` (卡片展示)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `card.support` | String | 是 | 支持的卡片尺寸, `\|` 分隔多值. 流体云 API 2.0 取值: `notification_sm` (气泡/胶囊) / `notification_md` (大胶囊) / `notification_lg` (展开面板). 车机选 `notification` 模板必须支持 `notification_lg` |
| `card.subscribe.size` | String | 否 | 该订阅配置对应的尺寸, 必为 support 子集 |
| `card.subscribe.name` | String (or 变量) | 是 | 订阅时卡片名称, 变量在 `i18n/xx.json` 定义 |
| `card.subscribe.desc` | String (or 变量) | 是 | 订阅时卡片描述 |
| `card.subscribe.groupTitle` | String (or 变量) | 是 | 订阅时卡片组名 |
| `card.subscribe.groupImage` | String | 是 | 订阅时卡片组图标 |
| `card.subscribe.previewImage` | String | 是 | 订阅预览图, 默认 `@image:images/xxx.png`; 亮暗色写法 `r('images.logo')` |
| `card.subscribe.cardLoadingIcon` | String | 否 | 卡片加载时图 |
| `card.subscribe.skeletonImage` | String | 否 | 亮色骨架图 |
| `card.subscribe.skeletonDarkImage` | String | 否 | 暗色骨架图 |
| `card.subscribe.loadFailImage` | String | 否 | 加载失败图 |
| `card.subscribe.loadFailDp` | String | 否 | 加载失败跳转 DeepLink |
| `card.subscribe.settingUrl` | String | 否 | 卡片设置页 activity action, 例: `com.oplus.notice.ACTION` |

### 2.3 `click` (点击事件, 数组)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | String | 是 | `activity` (跳原生应用) / `uri` (跳快应用) |
| `packageName` | String | 当 type=activity | 跳转目标包名 |
| ... | | | 后续字段按 type 分支 |

### 2.4 完整 card-config.json 示例 (流体云 + 有宿主)

```json
{
  "card": {
    "support": "notification_sm|notification_md|notification_lg"
  },
  "click": [{
    "type": "activity",
    "packageName": "com.example.host"
  }],
  "host": {
    "packageName": "com.seedling.card.support.demo",
    "componentName": "com.seedling.card.support.demo.DemoSeedlingCardProvider",
    "minHostVersion": 10001100
  }
}
```

---

## 三、SeedlingSupportSDK — 宿主 APK 端 (doc 12719)

### 3.1 引入方式

```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])
}
```

### 3.2 AndroidManifest.xml

```xml
<application>
    <meta-data
        android:name="com.oplus.ocs.card.AUTH_CODE"
        android:value="替换成你的授权码" />
</application>

<provider
    android:name=".DemoSeedlingCardProvider"
    android:authorities="com.xxx.xxx"
    android:enabled="true"
    android:exported="true"
    android:permission="com.oplus.permission.safe.ASSISTANT">
    <intent-filter>
        <action android:name="com.oplus.seedling.action.SEEDLING_CARD" />
    </intent-filter>
</provider>
```

### 3.3 SeedlingCardWidgetProvider 全回调

| 回调 | 触发时机 |
|---|---|
| `onCardCreate(context, card)` | 卡片创建 |
| `onShow(context, card)` | 卡片可见 |
| `onHide(context, card)` | 卡片不可见 |
| `onDestroy(context, card)` | 卡片销毁 |
| `onSubscribed(context, card)` | 卡片被订阅 |
| `onUnSubscribed(context, card)` | 卡片被取消订阅 |
| `onCardObserve(context, cards: List<SeedlingCard>)` | 卡片订阅的集合数量变化 |
| `onUpdateData(context, card, data: Bundle)` | 卡片收到初始化参数 |
| `onSizeChanged(context, card, oldSize, newSize)` | 胶囊↔面板 size 切换 |

**注意**: `onSizeChanged` 是流体云专属回调 (其他 AppWidget 不会有), `oldSize/newSize` 取值 `notification_sm` / `notification_lg`.

### 3.4 `updateData(card, JSONObject)` 业务逻辑 (有宿主)

```kotlin
override fun onUpdateData(context: Context, card: SeedlingCard, data: Bundle) {
    // 1. 收到卡片初始化参数 (可能为 null)
    Log.i(TAG, "收到卡片 $card 的初始化参数, data = $data")
    // 2. 准备业务数据
    val businessData = JSONObject().apply {
        put("comTitle", "新的服务名称")
        put("describe", "新的描述文本")
    }
    // 3. 更新卡片数据
    updateData(card, businessData)
}
```

### 3.5 seedling.js (无宿主, 业务逻辑)

```javascript
export default {
    onCreate(cardInfo) { console.log("onCreate") },
    onShow(cardInfo) {
        this.setData(cardInfo, {
            "comTitle": "新的服务名称",
            "describe": "新的描述文本"
        }, {
            isMilestone: false,    // 是否里程碑事件 (配合 grade)
            pageId: 'pages/index'  // 切换卡片页面
        })
    },
    onHide(cardInfo) { },
    onDestroy(cardInfo) { }
}
```

`setData(cardInfo, uiData, options)` 三参:
- `cardInfo`: 系统传入
- `uiData`: OML 中 `<data>` 标签声明过的 key 集合
- `options`: `{ isMilestone: Boolean, pageId: String }`

---

## 四、SeedlingCardOptions (流体云卡片设置项, doc 13330)

通过 `updateData` 或触发卡片时传入, 控制卡片行为.

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `pageId` | String | pages[0] | 切换卡片页面, 取值 `config.json` 中 pages 内容 |
| `dataSourcePkgName` | String | null | 数据来源应用包名. 例: 百度步行导航作为中转, 此字段填百度地图包名 → 进入百度地图时自动隐藏流体云 |
| `requestShowPanel` | Boolean | null | true=显示面板 / false=显示胶囊 / null=不操作. 互斥, **需 OPPO 加白名单**; 建议改用 `remindType` |
| `requestHideStatusBar` | Boolean | false | true=临时隐藏胶囊/面板 |
| `isMilestone` | Boolean | false | 是否里程碑事件. 里程碑数据影响卡片可见性, 非里程碑数据不影响可见性但仍可更新数据. **建议 `onUpdateData` 回调时把数据标记为 milestone=true** |
| `grade` | Int | - | 重要级别 1~5 (`GRADE_1` ~ `GRADE_5`). 需与 OPPO 沟通, 不能自用. 不同 grade 在不同入口表现不同 |
| `notificationIdList` | List? | null | 当应用同时用泛在卡 + 普通通知时, 把通知 ID 同步给 SystemUI 做去重, 优先展示泛在卡 |
| `showHostMap` | Map<SeedlingHostEnum, Boolean>? | null | 动态控制哪些入口展示卡片. 仅 SystemUI 入口生效. 例: `mapOf(StatusBar to true)` = 状态栏可见 |
| `lockScreenShowHostMap` | Map<SeedlingHostEnum, Boolean>? | null | 锁屏态下入口可见性. **受 showHostMap 控制** (showHostMap 关闭某入口时, lockScreenShowHostMap 再开启该入口也不生效) |
| `panelActionConfigMap` | Map<PanelActionEnum, CancelPanelActionConfigEnum>? | null | 替代旧字段 `cancelPanelActionConfig`. 支持面板内滑动 vs 面板外点击空白处分别配置. **生命周期内持久生效**, 变更通过更新数据 |

### 4.1 panelActionConfigMap 取值

| PanelActionEnum (key) | CancelPanelActionConfigEnum (value) |
|---|---|
| `PANEL_SLIDE` (面板内滑动) | `Retract` (收起为胶囊) / `Disappear` (消失) / `NoAction` (不做响应, 新增) |
| `OUTSIDE_CLICK` (面板外点空白) | 同上 |

例: 首次意图 `mapOf(PANEL_SLIDE to Retract, OUTSIDE_CLICK to NoAction)` → 滑动收起胶囊, 点击空白不响应.

### 4.2 SeedlingHostEnum 入口枚举

- `StatusBar` — 状态栏
- (下拉通知 / 锁屏 等其他枚举见 SDK)

---

## 五、模板组件 API (doc 12703, doc 12658)

流体云模板 (OML 描述) 不允许 CSS. 容器组件:

| 容器 | 用途 | 约束 |
|---|---|---|
| `compact` | 胶囊态容器, 根节点, 必须含 leading/trailing | 用 `compact` 时 `config.json` 入口 entry 需配 `statusbar` (反之不必须) |
| `expanded` | 展开态容器, 根节点, 必须含 leading/center/trailing | - |

子组件 level 一览 (modular 模板):

| level | 描述 | 必填 | 可选组件 |
|---|---|---|---|
| Z | 语音播报内容 | 否 | - |
| A1* | 服务图标 (AOD/手表/气泡 兜底展示) | 是 | - |
| A/A0 | 顶部标题/副标题 | 否 | text |
| B/B0/B1* | 顶部次级信息 | 否 | text/image |
| C1/C2/C3 | 核心标题 | 否 | text |
| D1* | 状态 | 否 | text |
| E1*-E21* | 各类操作按钮 | 否 | button |
| F1 | 自定义背景色/图 | 否 | bg |

注: 标 `*` 的 level 强调"动态/特殊", 优先级判断可能影响形态.

---

## 六、模板类型枚举 (doc 12658)

| category | 中文名 | 起始 API |
|---|---|---|
| `modular` | 组合模板 | 3.0.0 |
| `general` | 通用模板 | 2.0.0 (API 同期) |
| `symmetry` | 对称模板 | 2.0.0 |
| `media` | 音乐模板 | 2.0.0 |

`symmetry` 与 `general` 多用于左右对称信息 (出发地/目的地, 比分等); `media` 用于播放控制 (音乐/视频); `modular` 用于顶部+核心+底部三段信息.

---

## 七、关键 Action / Permission / meta-data 全表

| 项 | 值 | 用途 |
|---|---|---|
| Service Action | `com.oplus.seedling.action.SEEDLING_CARD` | 流体云 Provider intent-filter |
| 必需 Permission | `com.oplus.permission.safe.ASSISTANT` | 内部业务强制, 三方按需 |
| Auth Code meta-data name | `com.oplus.ocs.card.AUTH_CODE` | 授权码载体 |
| 真机调试日志关键字 | `ULE_APP` | `adb logcat \| findstr "ULE_APP"` |

---

## 八、对 Sleepy 的落点

1. **config.json / card-config.json 写法已锁定**, 三方接入时严格按上表填字段, **错字段名 (大小写) 直接被平台拒**.
2. **`minHostVersion` 强制递增**, 任何宿主 APK 升级 → 必须同步 bump card-config.json 的 minHostVersion.
3. **`isMilestone=true` 是 "当前课时" 类实时卡片的关键** — 课程节次切换就是里程碑, 设为 true 才能让卡片在课程表类场景里正确显示/隐藏.
4. **`grade` 不能自用**, 走"接入准备"流程时与 OPPO 沟通 grade=2 或 3 (实时状态类).
5. **`showHostMap` / `lockScreenShowHostMap`** 是课程表避免误触的开关 — 上课时把状态栏入口关闭, 下课时再开.
6. **`dataSourcePkgName`** 课程表不需要中转数据 → 留 null 即可.
7. **`panelActionConfigMap` 课程表推荐配置**: 滑动收起为胶囊 (`PANEL_SLIDE to Retract`), 点击空白不响应 (`OUTSIDE_CLICK to NoAction`), 避免上课时误触空白处把面板收起.
