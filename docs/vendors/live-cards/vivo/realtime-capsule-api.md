[evidence=A] https://dev.vivo.com.cn/documentCenter/doc/896 (技术规范全文) 抓取时间 2026-09-17 · 系统侧常量补充自反编译源 [evidence=C] https://github.com/imwangwang/vivo-service 抓取时间 2026-09-17

# vivo 原子通知技术协议全量（本地 Bundle + 云端 VPush + 反射 API + 系统侧常量）

> 一句话: vivo 的实时卡片**没有独立 Kit/SDK**——本地链路 = 标准 `Notification.extras` 塞约定 Bundle key；云端链路 = VPush `/livemessage/send`；能力探测 = 反射 `NotificationManager` 隐藏接口。内部代号 SuperX。

## 一、架构（doc 896 一）

```
应用本地发通知(带 superx extras) ──► 系统创建原子通知
        │
        └─► 客户端集成 VPush SDK 拿 regId ──► regId+notifyId+scene 上传应用服务器
                                                    │
应用服务器 ── VPush 服务器 /livemessage/send (liveMessage JSON) ──► 远程更新/结束
```

- 整体复用**系统通知方案**，非独立服务；VPush SDK 接入文档: dev.vivo.com.cn/documentCenter/doc/365。

## 二、本地数据协议 — `notification.superx.*` Bundle（doc 896 二）

### 2.1 核心字段

| key | 必填 | 类型 | 说明 |
|---|---|---|---|
| `notification.superx.operation` | Y | int | 0 创建 / 1 更新 / 2 结束 |
| `notification.superx.showNotify` | N | boolean | 展示失败是否回落普通通知，默认 true |
| `notification.superx.template` | Y | int | 1 强调信息 / 2 进度可视化 / 3 左右对称 / 4 基础 / 5 导航 |
| `notification.superx.baseInfos` | Y | Bundle | 基础信息区（2.4 模板数据） |
| `notification.superx.infos` | Y | Bundle | template 对应扩展区（2.5-2.7/2.9） |
| `notification.superx.shortInfos` | Y | Bundle | 小卡 + OriginB 锁屏（2.8） |
| `notification.superx.capsule` | N | Bundle | 胶囊（**不支持岛的设备默认取此字段做状态栏触点**） |
| `notification.superx.island` | N | Bundle | OS5.0 原子岛（**支持岛的设备默认取此字段**） |
| `notification.superx.clickResp` | Y | PendingIntent | 大卡/小卡/胶囊点击；B 锁屏为拖动后触发 |
| `notification.superx.scene` | Y | String | 场景（见 2.1.1） |
| `notification.superx.keepDuration` | N | int | 秒；结束存档 ≤3600s，默认不存档 |
| `notification.superx.changedRecord` | N | int | 更新序号，后续更新此值小于当前值则丢弃（防乱序） |
| `notification.superx.newNode` | N | int | 传递新节点（如接单=1 送外卖=2），胶囊处展示重要节点 |
| `notification.superx.displays` | N | int | 触点位掩码: 0x001 通知 / 0x010 锁屏 / 0x100 状态栏 / 0x1000 桌面组件 / 0x10000 熄屏 / 0x100000 魔盒 / 0x1000000 小v建议；不传默认 4*2 卡全触点、4*1 卡通知+状态栏 |
| `notification.superx.sound` | N | boolean | 创建时是否响铃，默认 true |
| `notification.superx.dismissWhenKill` | N | boolean | 杀进程是否清除，默认 false |
| `notification.superx.customSuperx` | N | String(json) | 扩展信息（如接小v建议，需线下对协议） |

#### 2.1.1 scene 场景值

官方文档值（含原文拼写）: `MOVIE` 电影 · `HEALTH_REGISTER` 挂号 · `TAXI` 打车 · `TAKEOUT` 外卖 · `DELIEVERY` 快递（原文如此，非 DELIVERY）· `NAVIGATION` 导航 · `CAR_STATE` 车机状态 · `METTING` 会议日程（原文如此）· `TRAIN` 火车 · `FLIGHT` 航班 等。

反编译源核对（C 级）: 系统内默认场景注册表为 `HEALTH_REGISTER, TRAIN, DELIVERY`(此处系统侧拼写是 DELIVERY), `FLIGHT, NAVIGATION, MOVIE, TAXI, TAKEOUT, FOCUSMODE`；**未出现** `CAR_STATE`/`METTING`——即文档多列了 2 个、系统另有 `FOCUSMODE`(专注模式) 未列入文档。以 `getSceneStatus(pkg, scene)` 反射实测为准。

### 2.2 状态栏胶囊 capsule（* 必发，Flip 外屏用）

`capsule.state`(Y, 0 不展示/1 展示) · `capsule.icon`(Y, Icon) · `capsule.content`(N) · `capsule.contentColor`(N, 默认黑) · `capsule.bgColor`(N, 默认白带描边) · `capsule.showTime`(N, 秒) · `capsule.clickResp`(N, 默认取核心 clickResp)。

### 2.3 原子岛 island（OS5.0+）

外层: `island.superx.leftTemplate`(Y, 目前仅 1=图片+文本) · `island.superx.leftInfo`(Y, Bundle) · `island.superx.rightTemplate`(Y) · `island.superx.rightInfo`(Y, Bundle) · `island.superx.islandClick`(N, 0 出卡/1 跳落地页/2 不可跳仅反馈) · `island.superx.clickResp`(N) · `island.superx.showTime`(N, 秒) · `island.superx.template`(N, 岛大卡模板 1-6[6=自定义]，不配表示不支持大卡) · `island.superx.baseInfos`/`infos`(N, 不设置时复用通知中心数据)。

rightTemplate 6 种: **1 律动动画（无需传资源）· 2 进度动画 · 3 加载中动画 · 4 文本+图片（至少一个）· 5 图片+文本（至少一个）· 6 胶囊文本**。

左岛: `leftInfo.icon`(Y) · `leftInfo.content`(N, CharSequence, 支持 SpannableString 颜色、**支持计时器文本**)。
右岛: `waveColor`(ArrayList\<int\>，[主色,辅色]) · `waveState`(0 暂停/1 播放) · `icon` · `content` · `capsuleContent` · `capsuleBgColor` · `clickResp`(动画类不支持点击) · `progressValue` · `progressColor` · `progressBgColor` · `progressState`(-1 失败/0 更新中/1 成功，先判状态再读进度) · `loadingColor`。
反编译补充（C 级）: 系统侧另有 `island.superx.eventInfo` key；缺岛 bundle 报 2218/2249-2253。

### 2.4-2.9 大卡模板字段（进 baseInfos / infos / shortInfos）

- **baseInfos**: `icon` · `title`(Y) · `content`(Y) · `subInfo`(0 不展示/1 文本/2 胶囊文本/3 图片/4 多图片*仅模板4/5 进度/6 加载中；模板4时必选) · `subText` · `subTextColor` · `subCapsuleBgColor` · `subImage` · `subInfoClickResp` · `subImageList`(ArrayList\<Icon\>，最多显示 3 个；>1 个必须每个都设点击) · `subInfoClickRespList` · `subProgress` · `subProgressColor` · `subProgressBgColor` · `progressState` · `progressContent`(进度环内文案，默认进度值) · `loadingColor`。
- **模板1 强调 infos**: `describe`(Y) · `coreInfo`(Y) · `image`(Y) · `imageClickResp` · `subReverse`(与辅助信息区调换位置；旧版本不生效) · `notification.vcard.infos.mainText/subText`(小v建议组件卡片主/辅信息，不传默认拼接 describe+coreInfo，**注意语序**)。
- **模板2 进度 infos**: `nodeIcon`(Y, 2~5 个，其他数量异常；4-5 个时强制无指示器) · `progress`(Y, ≤100) · `indicatorIcon` · `indicatorLoc`(1 覆盖线上[默认]/2 线上方) · `progressColor` · `BgColor`。
- **模板3 对称 infos**: `leftMain/leftSub/rightMain/rightSub`(Y) · `rightMainExtra`(右上角跨天角标，系统强制红) · `midTopMsg` · `midMainType`(1 图片/2 文本)+`midMainIcon`/`midMainMsg`(Y) · `midSubType`+`midSubIcon`/`midSubMsg`。
- **模板5 导航 infos**: `navIcon`(Y) · `navMsg`(Y, 最多两行，两行以 `#` 分隔)。
- **shortInfos（小卡+OriginB锁屏）**: `icon` · `image`(Y) · `imageClickResp`(Y) · `OriginBImage`(N, Origin 锁屏图标，缺省用应用图标) · `describeShort`(Y) · `coreInfoShort`(Y)。

### 2.10 本地代码骨架（doc 896 三）

```java
Bundle b = new Bundle();
b.putInt("notification.superx.operation", 0);        // 0创建 1更新 2结束
b.putBoolean("notification.superx.showNotify", true);
b.putInt("notification.superx.template", 1);
b.putParcelable("notification.superx.clickResp", mPendingIntent);
b.putString("notification.superx.scene", "HEALTH_REGISTER");
Bundle baseInfo = new Bundle();
baseInfo.putParcelable("notification.superx.baseInfos.icon", icon);
baseInfo.putCharSequence("notification.superx.baseInfos.title", title);
baseInfo.putCharSequence("notification.superx.baseInfos.content", content);
b.putBundle("notification.superx.baseInfos", baseInfo);
Notification n = new NotificationCompat.Builder(ctx, CHANNEL_NORMAL)
        .setContentTitle(...).setContentText(...).setSmallIcon(R.mipmap.ic_launcher)
        .setExtras(b).build();
mManager.notify(notificationId, n);
```
- 更新 = 同 notifyId 重发，operation 改 1；
- 结束 = operation 改 2（可配 keepDuration ≤1h 存档）；
- **彻底取消** = `mManager.cancel(tag, id)`，tag 固定字符串 `"VIVO_SUPERX_TAG"`。

## 三、云端更新 — VPush（doc 896 四）

### 3.1 鉴权 `/message/auth`
POST https://api-push.vivo.com.cn/message/auth，body `{appId, appKey, timestamp, sign}`；sign = MD5(appId+appKey+timestamp+appSecret) 小写。authToken 24h 过期，业务方中心缓存 1-2h 换一次；单 app **10 次/s**，超频返回 10093。

### 3.2 单推 `/livemessage/send`
POST https://api-push.vivo.com.cn/livemessage/send，Header 带 authToken。公共字段: `appId/regId/alias`(二选一，都传取 regId) · `notifyType`(1 无/2 响铃/3 振动/4 响铃+振动) · `title`(≤20 汉字) · `content`(≤50 汉字) · `timeToLive`(≥60s 最长 7 天，建议默认 24h) · `skipType`(1 首页/2 链接/3 自定义/4 指定页) · `skipContent` · `networkType` · `extra`(回执) · `requestId`(≤64 字符) · `pushMode`(0 正式/1 测试) · **`notifyId`(Y, 必须等于本地创建时指定的 notifyId)** · `profileId` · **`liveMessage`(Y)**。

`liveMessage` 对象:
- `operation`(Y, 远程只支持 **1 更新 / 2 结束**——远程不能创建，创建必须本地先发)；
- `scene`(Y) · `keepDuration` · `showNotify` · `changeRecord`（乱序丢弃）；
- `templateType`(Y, 远程仅支持 **1/2/4** 三种模板——3 对称、5 导航不支持远程)；
- `templateData`(Y): `baseInfo`(icon + mainText/subText 富文本数组 [{text,textColor}]) · `priorityInfo`(模板1: image/mainText/subText/skipType/skipContent/subReverse/vcardMainText/vcardMainTextColor/vcardSubText/vcardSubTextColor) · `progressInfo`(模板2: nodeIcon[2-5]/indicatorIcon/indicatorLoc/progress 0-100/progressColor/bgColor) · `shortInfo`(小卡+锁屏: lockIcon/icon/image/mainText/subText/skip) · `subInfo`(type 0-4/text/textColor/bgColor/image/skip/imageSkipInfos 多图跳转)；
- `capsuleData`(state/content/contentColor/bgColor/icon/newNode)；
- `islandData`(leftTemplate/rightTemplate/leftInfo{icon,text,textColor}/rightInfo{waveColor[],waveState,icon,text,textColor,bgColor,skipType,skipContent,progress,progressColor,progressBgColor,progressState,loadingColor}/forceShowCard(直接出卡,如行情 loading 完成直接出卡)/**showTime 最长 180s**/clickType)；
- `customSuperx` · `displays`(触点掩码同本地)。
- image 对象: `path`(必填，应用本地资源路径如 `res/drawable-hdpi/xx.jpg`) + `imgUri`(网络图，**需申请权限**，无权限回落 path)。

### 3.3 回执（extra: `callback.id`(Y 新回执,原子通知仅支持新回执) + `callback.param`）

| 主类型 | 含义 | 子类型 |
|---|---|---|
| 300 | 原子通知送达 | / |
| 301 | 下发失败 | 31000 其他 · 31001 参数校验不合法 · 31002 内容审核不通过 · 31003 设备未订阅 · 31004 regId 不存在 · 31005 设备超 14 天不活跃 · **31006 设备不支持原子通知远程更新** |
| 302 | 客户端展示失败 | 32000 其他 · 32001 用户关闭该应用原子通知 · 32002 原子通知不存在(已删/未创建) · **32003 系统版本不支持原子通知** · 32004 图片资源不存在 · 32005 应用被卸载 · 32006 跳转页面不合法 · 32007 消息超频控限制 · **32008 当前应用或场景未开通原子通知权限** · 32009 展示失败转普通通知 |

32008 直接印证: **白名单/开通制**，未开通的应用/场景云端更新会被拒（见 access-gate.md）。

## 四、反射 API（doc 896 五.6/五.9，系统隐藏接口）

```java
// 场景开关状态（用户设置页里该场景原子通知开关；可用它决定是否发原子通知）
Class clz = Class.forName("android.app.NotificationManager");
Method m = clz.getDeclaredMethod("getSceneStatus", String.class, String.class);
boolean open = (boolean) m.invoke(nm, "com.vivo.ele", "TRAIN");   // pkg, scene

// 各模块是否已做兼容（版本能力探测）
Method m2 = clz.getDeclaredMethod("isSupportCustomFun", String.class, String.class);
boolean support = (boolean) m2.invoke(nm, "com.sdu.didi.psnger", "TAXI");
```
系统广播（C 级反编译补充）: `vivo.intent.action.SUPERX_STATUS_CHANGED`（场景状态变化）。

## 五、17 条官方注意事项（doc 896 五，浓缩）

1. island + capsule 双发（Flip 外屏）；
2. Icon 无更新可不传，复用资源；
3. 多语言: 本地语言上传服务器，至少中英文；
4. 结束且无存档时只需传 operation=2；
5. 颜色异常默认值写 `0x0`（alpha 0 视为异常值）；
6. 用 `getSceneStatus` 判场景开关再发；
7. **慎用 `setAutoCancel(true)`**——通知栏点击后原子通知会被系统 cancel 掉；
8. **NotificationChannel 必须先建后发**；无渠道发送失败不能出卡；**删除渠道会连带清除原子通知**；
9. 用 `isSupportCustomFun` 判版本兼容；
10. （7 的重复强调）；
11. （8 的重复强调）；
12. 管理 regId 注册/解注册时机，保证映射最新；
13. 一键换机: Push SDK 升 500 级以上 + 冷启动 turnOnPush 后 getRegId 同步服务端（业务数据迁移会带旧 regId 导致推错机）；
14. timeToLive 建议 24h 默认，自定义 ≥30 分钟；
15. 端侧图片 Icon 宽高 ≤1000*1000；
16. **不要判断通知开关状态**（原话）；
17. 小v建议组件卡片形态可复用原子通知数据，关注 `customSuperx` 字段并联系 vivo 商务。

## 六、系统侧实况（C 级: 反编译 vivo-service `VivoSuperXNotificationManager.java`）

- 总开关: `FtFeature.isFeatureSupport("vivo.opt.notification.superx") && !"vos".equals(FtBuild.getOsName())` —— **VOS（海外）机型整体禁用**。
- 白名单包: `{com.vivo.assistant, com.eg.android.AlipayGphone(支付宝), com.vivo.pushservice}` —— 对应"已开通"应用；三方需走准入（见 access-gate.md）。
- 模板注册表: `{1, 2, 3, 4, 5, 7}`（7=自定义模板，文档未公开）。
- 移除计时: 结束后 `SUPERX_NEXT_REMOVE_TIME = 2.5h`、`SUPERX_LONGEST_REMOVE_TIME = 8h`（与文档"2h 不更新清除/8h 上限"同一量级，2.5h 为反编译值）。
- 频控（文档未写，系统级 MultiRateLimiter）: `RateLimit(10, 5min)` + `RateLimit(60, 60min)` —— 每 userId+pkg+key 维度 5 分钟 10 条、60 分钟 60 条。
- 状态持久化: `/data/bbkcore/vivo_superx_info_list.xml`。
- 校验失败原因码 2207-2255（可用于联调排查"为什么没出卡"）: 2207 OVERSPEED(超频) · 2208 NOTINLIST(不在白名单) · 2209 USERREMOVE · 2210 INVALIDAPP · 2211 SCENE_CLOSE · 2212 NOT_HAVE_SCENE · 2213 NOT_HAVE_TEMPLATE · 2214 NOT_HAVE_OPERATION · 2215 HAVE_END_PHASE · 2216 CHANGERECORD_ERROR · 2218 缺 island bundle · 2220-2223 baseInfos 缺失(title/content/subInfo) · 2230-2235 infos 缺失(describe/coreInfo/image/nodeIcon 数量/progress) · 2239-2243 shortInfos 缺失 · 2244-2248 对称模板缺字段 · 2249-2253 island 缺字段 · 2254-2255 导航缺 navIcon/navMsg。

## 七、Sleepy 落点速判

- 课表/上课提醒属"日程待办"语义，与原子通知目标场景（进行中事件实时进度）不完全重合；vivo 明确**强及时性高刷新场景**禁入的是小V建议（导航/倒计时），原子通知侧禁止的是营销/隐私。
- 若仅做"下一节课倒计时胶囊"：倒计时文本在岛上受 10s/次刷新限制（岛 leftInfo 支持计时器文本，可能由系统驱动——反编译见计时器支持，待真机验证）；FOCUSMODE 场景存在系统注册表内，但三方申请 scene 需 vivo 分配，自定义 scene 走准入邮件沟通。
