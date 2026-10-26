[evidence=A] 抓取 2026-09-17 · OPPO 开放平台 `POST /oneoppoapi/doc/detail` · doc_id=12965 (流体云卡片) / 13330 (SeedlingCardOptions) / 12658 (流体云模板-组合/通用/对称/音乐四模板) · 视频 `openfs.oppomobile.com/open/oop/202412/08/09ea16c922da292a1d955d6e9d14d26a.mp4`

# OPPO 流体云 — 实时通知行为与模板规格

> 流体云不是普通通知的样式升级, 是 ColorOS 系统级 "实时活动" 通道. 行为覆盖: 形态切换/优先级/分组/锁屏/AOD/外屏/手表/耳机/车机. 本文件按 "模板分类 + 信息流转 + 实时更新机制" 三维度落盘.

## 一、形态切换与优先级 (doc 12965 + doc 12658)

### 1.1 三种形态枚举值 (card-config.json.support)

| 枚举值 | 形态 | 触发场景 |
|---|---|---|
| `notification_sm` | 气泡 (Bubble) + 胶囊 (Capsule) | 普通状态/低优先级 |
| `notification_md` | 大胶囊 | 中优先级 (新形态, 与 sm 同高但内容更多) |
| `notification_lg` | 展开面板 (Panel) | 长按胶囊 / 高优先级 |

`support` 字段多值用 `|` 分隔, 流体云推荐三态全配: `"notification_sm|notification_md|notification_lg"`.

### 1.2 形态升降级回调 (SeedlingCardWidgetProvider.onSizeChanged)

```kotlin
override fun onSizeChanged(context: Context, card: SeedlingCard, oldSize: Int, newSize: Int) {
    // 1. 长按胶囊 → 弹出面板: oldSize=notification_sm, newSize=notification_lg
    // 2. 面板消失 → 显示胶囊: oldSize=notification_lg, newSize=notification_sm
}
```

### 1.3 requestShowPanel vs remindType (doc 13330)

| 字段 | 优先级 | 限制 |
|---|---|---|
| `requestShowPanel` | 高 | **需 OPPO 加白名单**, 不可滥用. true=显示面板, false=显示胶囊, null=不操作 |
| `remindType` | 中 | OPPO 建议优先用, 功能覆盖 requestShowPanel 大部分场景 |

## 二、四种模板与信息流转 (doc 12658)

### 2.1 组合模板 modular (API 3.0+ 起)

| 特性 | 说明 |
|---|---|
| 起始 API | 3.0.0 |
| 形态数 | 2 (胶囊态 + 展开态) |
| 区域 | 顶部信息 + 核心信息 + 底部信息 (三段任意组合, 除核心信息可单独, 顶部/底部不可单独) |
| 背景 | 支持状态栏黑卡动态背景图 |
| 彩卡 | 状态栏黑色背景 = 彩卡, 锁屏/通知中心跟随; 不区分亮暗模式; 彩色必须有描边 |
| 入口数 | 9 (状态栏胶囊/卡片/气泡/锁屏/AOD/外屏胶囊/外屏通知中心/手表息屏/手表亮屏/耳机播报) |

### 2.2 通用模板 general (API 2.0+ 起)

| 特性 | 说明 |
|---|---|
| 起始 API | 2.0.0 (ColorOS 14.0) |
| 形态数 | 多 |
| 信息流转 | 入口1=胶囊 `A*+B*`; 入口2=卡片 `A1*+C1+F3+B1*+F1+D1+C2+C3+C4+D2+F2+D3+E1+E2+E3+D4+D5`; 入口3=气泡 `A*`; 入口5=AOD/外屏胶囊/外屏通知中心 `A1*+B1*+C2`; 入口11=耳机 `Z`; 入口12=车机 `A1*+B1*+C1+C2+D*` |
| 字段约束 | D3 与 E1/E2/E3 不能共存; G1=A1*+C1 同时存在才显示; G2=D4+D5; G3=C4+D2 |
| 信息示例 | name + status + info + voiceLabel (支持进度条 D4/D5) |
| 按钮 | E1/E2/E3 三选一, 图形按钮最多 2 个, 文本按钮与图形按钮二选一 |
| 背景 | F1 = 静态/动态背景图, 常态高斯模糊 |

### 2.3 对称模板 symmetry (API 2.0+ 起)

| 特性 | 说明 |
|---|---|
| 起始 API | 2.0.0 |
| 车机 | **OS 14.0 暂不支持车机上展示** |
| 形态数 | 多 |
| 适用 | 左右对称信息 (出发/到达/比分/起止时间等) |
| 信息流转 | 入口2=卡片 `B1*+C1+C2*+C3+C4+C5+D1+D2+D3+D4+E`; 其他同 general |
| 字段约束 | G1=A*+C1 同时存在才显示; C2* 必填; G=C4/C5/D1/D2/D3/D4 (支持 if 控制显隐) |
| 信息示例 | name + additional + content + number + checkIn + departureLocation/destLocation + departureTime/destTime + extraDays |
| 设备类型 | phone: "aod", "statusbar", "notification", "lockscreen", "headset", "" |

### 2.4 音乐模板 media (API 2.0+ 起)

| 特性 | 说明 |
|---|---|
| 起始 API | 2.0.0 |
| 适用 | 音乐/视频/有声书等播放控制 |
| 字段 | 专辑封面 + 标题/作者 + 播放控制 (上一首/播放暂停/下一首) + 进度条 + 时间 |

## 三、实时更新机制

### 3.1 三种触发更新方式

| 方式 | API | 适用 |
|---|---|---|
| **宿主主动 push** | `updateData(card, JSONObject)` | 有宿主路线, 业务事件触发 (如"骑手到达") |
| **系统事件驱动** | `onUpdateData(context, card, data: Bundle)` 回调 | 有宿主路线, UPK 推送新数据时触发 |
| **客户端轮询** | UPK 内 `seedling.js` 中 `setInterval` | 无宿主路线, 客户端主动拉 |

### 3.2 数据更新三大原则 (doc 13330)

1. **数据必须落在 OML 声明的 uiData key 集合内** — 超出集合的 key 被丢弃
2. **isMilestone=true 是里程碑数据** — 影响卡片可见性, 非里程碑数据不影响可见但可更新内容
3. **grade 1~5 必须与 OPPO 沟通** — 等级 1=最低, 等级 5=最高, 课程表类通常 grade=2~3

### 3.3 数据中转 (dataSourcePkgName, doc 13330)

```kotlin
SeedlingCardOptions().apply {
    dataSourcePkgName = "com.baidu.BaiduMap"  // 数据真正来源的应用包名
}
```

**典型场景**: 中转应用 (如某聚合打车) 调起百度地图时, 流体云自动隐藏; 退出百度地图时自动显示.
**Sleepy 课程表场景**: 不需要中转数据 → dataSourcePkgName 留 null.

## 四、入口可见性控制 (doc 13330)

### 4.1 showHostMap

```kotlin
SeedlingCardOptions().apply {
    showHostMap = mapOf(
        SeedlingHostEnum.StatusBar to true,    // 状态栏可见
        SeedlingHostEnum.Notification to false // 通知中心不可见
    )
}
```

- 仅 SystemUI 入口生效
- **里程碑机制不能控制单个入口**, showHostMap 是更精细控制
- 锁屏态/非锁屏态都受其控制

### 4.2 lockScreenShowHostMap

```kotlin
SeedlingCardOptions().apply {
    lockScreenShowHostMap = mapOf(
        SeedlingHostEnum.StatusBar to false  // 锁屏下状态栏不可见
    )
}
```

- 受 showHostMap 控制: showHostMap 关闭某入口 → lockScreenShowHostMap 再开该入口不生效
- 仅状态栏和下拉通知两个入口有锁屏/非锁屏区分

### 4.3 课程表场景配置

```kotlin
// 上课时: 锁屏态不可见 (避免老师看到), 解锁态状态栏可见 (自己看)
SeedlingCardOptions().apply {
    isMilestone = true
    grade = SeedlingCardOptions.GRADE_2
    showHostMap = mapOf(
        SeedlingHostEnum.StatusBar to true,
        SeedlingHostEnum.Notification to true,
        SeedlingHostEnum.LockScreen to false
    )
    lockScreenShowHostMap = mapOf(
        SeedlingHostEnum.StatusBar to false
    )
}
```

## 五、面板交互配置 (doc 13330 panelActionConfigMap)

### 5.1 枚举值

| PanelActionEnum (key) | 含义 |
|---|---|
| `Unknown` | 无效 |
| `PANEL_SLIDE` | 面板内滑动 |
| `OUTSIDE_CLICK` | 面板外点空白 |

| CancelPanelActionConfigEnum (value) | 含义 |
|---|---|
| `Unknown` | 无效 |
| `Retract` | 收起为胶囊 |
| `Disappear` | 消失 |
| `NoAction` | 不响应 (新增) |

### 5.2 课程表推荐配置

```kotlin
panelActionConfigMap = mapOf(
    PanelActionEnum.PANEL_SLIDE to CancelPanelActionConfigEnum.Retract,
    PanelActionEnum.OUTSIDE_CLICK to CancelPanelActionConfigEnum.NoAction
)
```

理由: 上课时滑动收起为胶囊 (符合用户预期), 点击空白不响应 (避免误触收起).

## 六、通知去重 (doc 13330 notificationIdList)

```kotlin
notificationIdList = listOf(1001, 1002, 1003)
```

当应用同时用泛在卡 + 普通通知时, 把通知 ID 同步给 SystemUI 做去重, **优先展示泛在卡**.

## 七、行为事实矩阵

| 事实 | 来源 | 等级 |
|---|---|---|
| 流体云支持三形态: notification_sm (气泡/胶囊) / notification_md (大胶囊) / notification_lg (面板) | doc 12965 §support | A |
| 三态升降级通过 `onSizeChanged` 回调, 值为 sm/lg | doc 12719 | A |
| requestShowPanel 需 OPPO 加白名单, 建议改用 remindType | doc 13330 §requestShowPanel | A |
| 模板四类: modular / general / symmetry / media | doc 12658 §模板分类 | A |
| modular 模板支持 9 个入口 (含 AOD/外屏/手表/耳机) | doc 12658 §组合模板入口表 | A |
| 对称模板 OS 14.0 暂不支持车机 | doc 12658 §对称模板 | A |
| general/symmetry 模板 D3 与 E1/E2/E3 不能共存 | doc 12658 §通用模板 | A |
| 按钮图形/文本二选一, 图形按钮最多 2 个 | doc 12658 §通用模板 | A |
| G1 标签组合 (A1*+C1) 必须同时存在才显示 | doc 12658 §通用模板 | A |
| 数据更新必须落在 OML 中 uiData 声明过的 key 集合 | doc 12639 §seedling.js setData | A |
| isMilestone=true 影响卡片可见性 | doc 13330 §isMilestone | A |
| showHostMap 仅 SystemUI 入口生效 | doc 13330 §showHostMap | A |
| lockScreenShowHostMap 受 showHostMap 控制 (反向无效) | doc 13330 §lockScreenShowHostMap | A |
| panelActionConfigMap 生命周期内持久生效, 变更通过更新数据 | doc 13330 §panelActionConfigMap | A |
| dataSourcePkgName 用于中转应用, 进入真实应用时自动隐藏流体云 | doc 13330 §dataSourcePkgName | A |

## 八、对 Sleepy 的落点

1. **首选 modular 模板**: 课程节次天然契合 "顶部信息 (节次) + 核心信息 (课程名/教师/教室) + 底部信息 (下课时间)" 三段式拆分.
2. **三形态必配**: `support: "notification_sm|notification_md|notification_lg"`, 缺一形态审核可能被打回.
3. **`isMilestone=true` 必修**: 课程节次切换就是里程碑, 否则卡片可见性错乱.
4. **`grade` 走 2~3**: 与 OPPO 商务沟通时申请.
5. **`lockScreenShowHostMap` 锁屏关闭**: 课程表是"自己看" 的信息, 不应让老师/同学在锁屏看到.
6. **`panelActionConfigMap` 滑动收起 + 点击空白不响应**: 上课时避免误触.
7. **音频播报 (Z 字段) 可选**: 上课铃响时用 TTS 播报节次, 走 Z 字段触发耳机播报入口.
