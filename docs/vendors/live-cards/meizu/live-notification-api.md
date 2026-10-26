[evidence=B] 整理 2026-09-17 · 源: 官方系 demo 仓库 https://github.com/Ruyue-Kinsenka/Flyme-Live-Notification-Demo (Kotlin, 2025-09-13 创建, 8 star, 全文已读) + open.flyme.cn 用户手册 docId 317 (形态佐证, evidence=A)。extras 键名为 demo 反推的隐藏 API, 无官方文档背书 — 等级 B, 全部参数值需真机验证。

# 魅族 Flyme — 实况通知接入 API (extras 隐藏键, 逆向实证)

> 官方开放平台**没有**实况通知开发者文档 (目录树全量核对过)。接入方式 = 普通 Android Notification + extras 特殊键, Flyme 系统通知服务识别这些键后渲染胶囊/横幅。以下键名与结构来自官方系 demo 仓库源码 (逐行读过), 非猜测。

## 一、结构总览

```
Notification
 ├─ channel: 普通 NotificationChannel (IMPORTANCE_HIGH, demo 用 "hello_world_live")
 ├─ contentView: 自定义 RemoteViews (胶囊点击展开后的"主通知卡片"布局)
 └─ extras: liveBundle                    ← 实况开关
     ├─ "is_live"               : Boolean  = true            ← 实况通知总开关
     ├─ "notification.live.operation" : Int = 0              ← 操作类型 (demo 注释: 显示)
     ├─ "notification.live.type"      : Int = 2              ← 实况通知类型 (demo 注释: 未定)
     └─ "notification.live.capsule"   : Bundle               ← 胶囊配置
         ├─ "notification.live.capsuleStatus"        : Int = 1
         ├─ "notification.live.capsuleType"          : Int = 5   (README 早期写 1, 源码用 5)
         ├─ "notification.live.capsuleContent"       : String = "test"
         ├─ "notification.live.capsuleIcon"          : Icon (parcelable)
         ├─ "notification.live.capsuleBgColor"       : Int (color)
         ├─ "notification.live.capsuleContentColor"  : Int (color)
         └─ "notification.live.capsule.content.remote.view" : RemoteViews (parcelable)
```

## 二、demo 源码关键段 (逐行照录)

```kotlin
// 胶囊 RemoteViews (状态栏药丸的布局)
val capsuleRemoteViews = RemoteViews(context.packageName, R.layout.live_notification_capsule)
capsuleRemoteViews.setTextViewText(R.id.capsule_content, "Hello World")

val capsuleBundle = Bundle().apply {
    putInt("notification.live.capsuleStatus", 1)
    putInt("notification.live.capsuleType", 5)
    putString("notification.live.capsuleContent", "test")
    putParcelable("notification.live.capsuleIcon",
        Icon.createWithResource(context, R.drawable.ic_notification))
    putInt("notification.live.capsuleBgColor",
        context.resources.getColor(android.R.color.holo_blue_bright, null))
    putInt("notification.live.capsuleContentColor",
        context.resources.getColor(android.R.color.white, null))
    putParcelable("notification.live.capsule.content.remote.view", capsuleRemoteViews)
}

val liveBundle = Bundle().apply {
    putBoolean("is_live", true)
    putInt("notification.live.operation", 0)
    putInt("notification.live.type", 2)
    putBundle("notification.live.capsule", capsuleBundle)
}

val notification = Notification.Builder(context, channelId)
    .setSmallIcon(R.drawable.ic_notification)
    .setLargeIcon(...)
    .setContentTitle("Hello World 实况通知")
    .setContentIntent(pendingIntent)
    .addExtras(liveBundle)          // ← 实况键全在这里
    .build()
notification.contentView = contentRemoteViews   // 展开后的主卡片布局
notificationManager.notify(1001, notification)
```

## 三、字段语义与未定项 (demo 作者自己也标注 idk 的)

| 键 | demo 值 | 语义置信度 |
|---|---|---|
| `is_live` | true | 高 — 总开关, 去掉则不渲染实况形态 |
| `notification.live.operation` | 0 | 低 — README 写"操作类型(显示)", 枚举值未知 |
| `notification.live.type` | 2 | 低 — 实况类型枚举未知 (可能与倒计时/进度/媒体等场景分类有关) |
| `notification.live.capsuleStatus` | 1 | 中 — 启用胶囊状态 |
| `notification.live.capsuleType` | 5 | 低 — README 写 1, 实际代码 5, 枚举未知 |
| `capsuleIcon` / `capsuleBgColor` / `capsuleContentColor` | — | 高 — 胶囊外观三件套 |
| `capsule.content.remote.view` | RemoteViews | 高 — 胶囊自定义布局 (约束未知, demo 用单 TextView 60dp minWidth) |

**未定项就是接入手册该有的内容**: operation/type/capsuleType 三个枚举无任何公开资料, 只有真机逐值试探或反编译 Flyme 系统通知服务才能定 — 记入 gaps。

## 四、双 RemoteViews 结构 (与一期 widget 结论同构)

- 胶囊布局 `live_notification_capsule.xml`: wrap_content 横向 LinearLayout + 单 TextView (12sp bold, 白字彩底, minWidth 60dp) — 状态栏药丸区域极窄, 内容必须极简。
- 展开布局 `live_notification_hello_world.xml`: match_parent 竖排 LinearLayout (标题 18sp + 正文 14sp + 辅助 12sp) — 就是普通自定义通知卡片, 点击胶囊后展示。
- 与苹果 Live Activities 双 presentation (compact + expanded) 同构; 与 Lock screen = 通知本身进锁屏横幅 (docId 317)。

## 五、对 Sleepy 的落点

1. 接入代码量 = 一个 LiveNotificationManager 级别的封装 (demo 全文 <150 行), 无 SDK 依赖, 普通 Notification API。
2. 三个枚举未定 → 上手路径 = 装 demo APK 到 Flyme AIOS 真机/模拟器逐值试, 或反编译 `com.android.systemui` 找 `notification.live.` 字符串的读取点。
3. 该方案是社区逆向路径, 上架商店审核风险未评估 (无厂商条款禁止/允许的公开依据, 记 gaps)。
