[evidence=A] 抓取 2026-09-17 · OPPO 开放平台 `POST /oneoppoapi/doc/detail` (doc_id: 13270/12639/12965/12658/12703/13330/12645/12646/12719/12715/12525) · 视频素材 `openfs.oppomobile.com/open/oop/202412/08/09ea16c922da292a1d955d6e9d14d26a.mp4` (流体云官方演示)

# OPPO ColorOS — 流体云 (Fluid Cloud) 能力总览

> 结论先行: **流体云是 OPPO 泛在服务 (Ubiquitous Service / 泛在服务卡片) 体系下的一种展现形态, 不是普通 Android Notification 的样式扩展**. 形态 = 气泡 + 胶囊 + 面板 三形态按优先级升降级, 入口覆盖手机/平板/折叠屏/车机/手表/耳机, 与苹果 Live Activities 的 compact/minimal/expanded 同构, 但**走的是 UPK 包 (类似 PWA) + 宿主 APK 两条路**, 不是 ActivityKit 风格的本地 Capability.

## 一、官方定性 (doc_id=13270 原文)

> "流体云是信息的轻量形态,聚焦精炼,并能在不同设备之间自由流动,把系统中的多种交互形式进行了整合和归一,再统一分发,在适当的场景智能显示,让用户能够更直观地接收到这些信息."
> "流体云功能将不同的服务、状态消息,根据优先级,以气泡、胶囊、面板不同形态呈现. 做到服务随人的同时降低打扰. 无论是打车、外卖、火车行程、航班行程,还是计时器录音、投屏…流体云都能实时获知您的服务状态,并以不同形式及时提醒,让您无需切换应用,就能一眼获知重要信息."

- 产品关键词: "实时活动全局触达" / "设计交互全新升级" / "全新胶囊设计让信息展示更加清晰直观"
- 流体云**不是**普通的样式扩展, 它是 "泛在服务" 体系下的一种展现形式. 真正的能力来源 = **泛在服务框架 (Ubiquitous Service Framework)**
- 适配 ColorOS 14.0 (API 2.0) 起, 13.1 及以下不支持

## 二、三形态与多入口 (doc_id=12658 组合模板章节原文)

### 2.1 三种基础形态

| 形态 | 含义 | 来源 |
|---|---|---|
| **气泡 (Bubble)** | 最轻量提示, 短时出现自动消失 | doc 12658 |
| **胶囊 (Capsule)** | 状态栏常驻胶囊, 单行关键信息 | doc 12658 |
| **面板 (Panel)** | 展开大卡片, 多区域组合信息 | doc 12658 |

形态之间**按优先级升降级**:
- 普通状态 → 胶囊
- 长按胶囊 → 弹出面板 (size: notification_sm → notification_lg)
- 面板消失 → 回到胶囊 (notification_lg → notification_sm)

(代码验证: `onSizeChanged` 回调入参 `oldSize/newSize` 取值为 `notification_sm`/`notification_lg`)

### 2.2 多入口覆盖 (doc 12658 入口表原文)

| 入口编号 | 入口 | 展示元素组合 |
|---|---|---|
| 入口1 | 直板/大折/平板状态栏**胶囊** | A+A0+B+B0 (最少一个) |
| 入口2 | 直板/大折/平板状态栏**卡片** | 顶部+核心+底部+背景 全区域 |
| 入口3 | 直板/大折/平板**锁屏** | 同入口2 (与状态栏卡片共用布局) |
| 入口4 | 直板/大折/平板 **AOD** | A/A0*+B1*+C1/C2/C3 & D1*+B1*+C1/C2/C3 |
| 入口5 | 外屏**底部胶囊** (折叠屏外屏) | 同 AOD |
| 入口6 | 外屏**通知中心** | 同 AOD |
| 入口7 | **手表息屏** | A/A0 |
| 入口8 | **手表亮屏** | A/A0 |
| 入口9 | **耳机播报** | A/A0 (语音播报) |

注: 这是"组合模板 (modular)" 的入口表, 其他模板 (general/symmetry/media) 的入口组合更精简.

### 2.3 模板四分类 (doc 12658 模板分类)

| category | 描述 |
|---|---|
| `modular` | 组合模板 (API 3.0+ 起支持) — 胶囊态+展开态两态, 顶部+核心+底部三块任意组合 |
| `general` | 通用模板 |
| `symmetry` | 对称模板 |
| `media` | 音乐模板 |

设计约束:
- **流体云模板不允许开发者修改样式**, 不支持使用 CSS 修改布局和样式
- **支持绑定变量和点击事件**
- 状态栏黑色背景卡 = "彩卡"; 锁屏/通知中心跟随彩卡; 若未设计彩卡 → 用 1.0 亮/暗模式卡 (区分亮暗)
- 彩色卡片**必须有描边**, 描边颜色自定义
- 状态栏黑卡允许**动态背景图**

## 三、接入路线 (doc 12639 原文)

### 3.1 两条路线

| 路线 | 适用场景 | 是否需要宿主 APK |
|---|---|---|
| **无宿主服务** | 纯卡片数据独立运行 | 否 |
| **有宿主服务** | 数据来自三方 APK, 卡片是数据展示壳 | 是 (应用 applicationId + SeedlingSupportSDK) |

### 3.2 五步接入 (无宿主 + 有宿主 共用)

1. **认证开发者** (open.oppomobile.com 开发者实名认证)
2. **接入准备** — 提交申请清单给 OPPO 申请 **授权码 + 意图 + 服务ID**:
   - url: UPK 工程的 config.json url 属性
   - name: UPK 工程的 config.json name 属性
   - applicationId (仅宿主需要): APK 包名
   - appName (仅宿主需要): APK 应用名
   - PRD: 产品描述文档, OPPO 据此分配意图
3. **下载 IDE** — Pantanal DevStudio
4. **下载 SDK (可选)** — Seedling Support SDK (有宿主需要, 无宿主不需要)
5. **准备 OPPO 手机** — ColorOS ≥ 13.1

### 3.3 意图分发 (doc 12639 §意图配置)

`config.json` 的 `intent` 字段:
```json
"intent": {
    "action": ["替换成你的意图 action"],
    "domain": "替换成你的意图 domain"
}
```

意图由 OPPO 平台侧基于 PRD 分配, 三方**不能自创意图**, 必须先报备.

## 四、关键 API 与 Bundle 字段

### 4.1 SeedlingCardWidgetProvider (有宿主, doc 12719)

```kotlin
class DemoSeedlingCardProvider : SeedlingCardWidgetProvider() {
    override fun onCardCreate(context: Context, card: SeedlingCard) {}
    override fun onShow(context: Context, card: SeedlingCard) {}
    override fun onHide(context: Context, card: SeedlingCard) {}
    override fun onDestroy(context: Context, card: SeedlingCard) {}
    override fun onSubscribed(context: Context, card: SeedlingCard) {}
    override fun onUnSubscribed(context: Context, card: SeedlingCard) {}
    override fun onCardObserve(context: Context, cards: List<SeedlingCard>) {}
    override fun onUpdateData(context: Context, card: SeedlingCard, data: Bundle) {
        // 1. 收到卡片初始化参数 (可能为 null)
        // 2. 准备业务数据
        val businessData = JSONObject().apply {
            put("comTitle", "新的服务名称")
            put("describe", "新的描述文本")
        }
        // 3. 更新卡片数据
        updateData(card, businessData)
    }
    override fun onSizeChanged(context: Context, card: SeedlingCard, oldSize: Int, newSize: Int) {
        // notification_sm ↔ notification_lg 切换时触发
    }
}
```

### 4.2 关键 Action / Provider 声明 (AndroidManifest)

```xml
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

`<application>` 内 meta-data:
```xml
<meta-data
    android:name="com.oplus.ocs.card.AUTH_CODE"
    android:value="替换成你的授权码" />
```

### 4.3 无宿主 seedling.js 业务逻辑 (doc 12639 原文)

```javascript
export default {
    onCreate(cardInfo) { console.log("onCreate") },
    onShow(cardInfo) {
        this.setData(cardInfo, {
            "comTitle": "新的服务名称",
            "describe": "新的描述文本"
        }, {
            isMilestone: false,
            pageId: 'pages/index'
        })
    },
    onHide(cardInfo) { },
    onDestroy(cardInfo) { }
}
```

## 五、事实矩阵

| 事实 | 来源 | 证据等级 |
|---|---|---|
| 流体云是泛在服务框架的一种展现形式, 不是普通 Notification 样式扩展 | doc 13270 §流体云 + 合作伙伴视频 | A |
| API 2.0 (ColorOS 14.0) 起支持流体云开发 | doc 12965 §概述 | A |
| 流体云三形态: 气泡 / 胶囊 / 面板, 按优先级升降级 | doc 12658 §组合模板 | A |
| 多入口: 状态栏/锁屏/AOD/外屏/手表/耳机播报 等 9 个入口 | doc 12658 §组合模板入口表 | A |
| 模板四类: modular / general / symmetry / media | doc 12658 §模板分类 | A |
| 接入分两条路: 无宿主 (UPK 独立包) / 有宿主 (宿主 APK + SeedlingSupportSDK) | doc 12639 §快速开始 | A |
| 三方接入必须 OPPO 侧申请 **授权码 + 意图 + 服务ID** | doc 12639 §接入准备 | A |
| 设备要求 ColorOS ≥ 13.1 | doc 12639 §环境准备 | A |
| 流体云模板**不允许开发者修改样式**, 不支持 CSS | doc 12658 §概述 | A |
| 状态栏黑色背景卡 = 彩卡; 锁屏/通知中心跟随彩卡 | doc 12658 §组合模板规则 4 | A |
| onSizeChanged 回调触发胶囊↔面板切换 (sm ↔ lg) | doc 12719 DemoSeedlingCardProvider | A |
| Service Action = `com.oplus.seedling.action.SEEDLING_CARD` | doc 12719 AndroidManifest | A |
| SeedlingCardWidgetProvider 必需 permission `com.oplus.permission.safe.ASSISTANT` | doc 12719 AndroidManifest | A |
| Pantanal DevStudio 是官方 IDE (定制版 VSCode) | doc 12639 §下载 IDE | A |
| 真机调试日志关键词: `adb logcat \| findstr "ULE_APP"` | doc 12639 §预览&调试 | A |

## 六、与小组件库一期结论的衔接

| 维度 | 流体云 (本期) | AppWidget (一期) |
|---|---|---|
| 底层协议 | 泛在服务 (UPK + SeedlingSupportSDK) | AppWidget (标准 Android) |
| 卡片定义位置 | OML (类 HTML 自家 DSL) | RemoteViews (系统原生) |
| 卡片渲染位置 | 系统级 (ColorOS 渲染进程) | AppWidgetHost (Launcher) |
| 数据来源 | UPK 内的 seedling.js 或宿主 APK ContentProvider | AppWidgetProvider.onUpdate |
| 热更新 | updateData(card, JSONObject) | AppWidgetManager.updateAppWidget |
| 形态变更 | onSizeChanged (sm/lg) | AppWidget 单元格 resize |
| 接入门槛 | OPPO 授权码 + 意图 + 服务ID (需审批) | 标准 AndroidManifest 即可 |
| 跨厂商复用 | 仅 OPPO / 一加 (ColorOS 同源), realme UI 走另一套 | 几乎所有 Android Launcher |

## 七、对 Sleepy 的落点

1. **不直接接入流体云** — Sleepy 是课程表 App, 不在 OPPO 允许的意图清单 (打车/外卖/火车/航班/计时器/投屏) 之中, 走"接入准备" 会被商务拒. 即使被接受, "授权码 + 意图 + 服务ID" 三件套审批周期长, 投入产出比低.
2. **可作为"接收方" 监听流体云事件** — ColorOS 14+ 系统级流体云出现在屏幕时, 可作为 Android 通知被 Sleepy 监听 (MediaSession/NotificationListener), 用于"上课时隐藏流体云" 体验优化 (录屏/录屏中不希望系统弹流体云).
3. **课程表场景在 ColorOS 上的最佳载体仍是 AppWidget** — 一期 Widget 跨厂商兼容层 (pin 路由派生 ALL_WIDGET_VARIANTS 全 10 变体) 已经把 OPPO 桌面/负一屏/Shelf 全覆盖, 流体云不是必需.
4. **如 OPPO 后续开放教育/课程意图**, 申请 "实时活动" 类意图, 用 modular 模板 (有宿主), 走 SeedlingSupportSDK 接 SeedlingCardWidgetProvider; 课程节次表对 "顶部信息 + 核心信息 (课节/教师/教室) + 底部信息 (下课时间)" 三段式天然契合 modular 模板的拆分.
5. **真机调试校验关键字 `ULE_APP`**, 日志异常时优先看这一条.
