[evidence=A] 抓取 2026-09-18 · 源: 小米澎湃 OS 开发者平台 开发指南(pId=2131, 2026-01-29 更新)/ 接入流程(pId=2132, 2025-12-05 更新)/ 常见 Q&A(pId=2146, 2025-10-23 更新) · 全文已落盘至 _sources-xiaomi-raw/text_2131/2132/2146.txt

# 小米 HyperOS — 焦点通知 / 超级岛 API 全量

> 证据等级: A(澎湃 OS 官方开发者平台原文, 三篇文档完整落盘)。**结论先行**: 小米把"通知"和"岛"两套能力合并在原生 Notification 通道里, 开发者用同一份代码能在 OS1/2/3 三个版本上做焦点通知(状态栏/通知中心/锁屏/息屏), 仅在 OS3(澎湃 3) 起才出现"岛"形态(摘要态/大岛/小岛), 接入路径 = 普通 Notification + extras 键 `miui.focus.param` 传一份 JSON 协议体。MIpush 通道走云侧, 仅支持 regId 单播。

## 一、协议族分层(OS1/2/3, 决定能不能出岛)

| OS | 焦点通知 | 岛 | 模板库 | 推荐度 |
|---|---|---|---|---|
| OS1 | 模板制 | 不支持 | OS1 模板 | **官方不建议接入**(版本不断更新, 维护成本高) |
| OS2 | 模板制 | 不支持 | OS2 模板 | 已适配 OS2 可继续用 |
| OS3(澎湃 3) | 自动兼容 | 支持 | OS2/OS3 通用模板(适配 OS3 自动兼容 OS2) | 主推 |

**版本查询接口**(text_2131 §五, 反射/系统设置):

```java
// 1. 是否支持岛(系统属性反射, 兼容老版本)
boolean supportIsland = isSupportIsland("persist.sys.feature.island", false);
// 返回 true: 支持; false: 不支持

// 2. 焦点通知协议版本
int focusProtocolVersion = Settings.System.getInt(
    context.getContentResolver(),
    "notification_focus_protocol", 0);
// 返回值: 1=OS1, 2=OS2, 3=OS3(支持岛), 0=无焦点通知功能

// 3. 应用是否开焦点通知权限(content provider, 耗时)
public static boolean hasFocusPermission(Context ctx) {
    boolean canShowFocus = false;
    try {
        Uri uri = Uri.parse("content://miui.statusbar.notification.public");
        Bundle extras = new Bundle();
        extras.putString("package", ctx.getPackageName());
        Bundle bundle = ctx.getContentResolver().call(uri, "canShowFocus", null, extras);
        canShowFocus = bundle.getBoolean("canShowFocus", false);
    } catch (Exception e) { }
    return canShowFocus;
}
```

业务侧策略: 查到 OS3 → 走"岛通知"模板; OS2 → 走焦点通知(不带岛); OS1 → 走最简焦点通知或回退普通通知。

## 二、两条接入路径(本地 vs 云侧)

### 2.1 客户端实现(本地)

按发送原生通知的流程, 在通知 extras 塞岛参数 JSON。

```java
// 1. 标准通知
NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
NotificationChannel channel = new NotificationChannel(
    "test_channel_id", "test_change_name", NotificationManager.IMPORTANCE_DEFAULT);
nm.createNotificationChannel(channel);

// 2. 岛参数(核心, param_v2 JSON 字符串)
String islandParams = "{ \"param_v2\": { " +
    "\"param_island\": { \"bigIslandArea\":{}, \"smallIslandArea\":{}, \"shareData\":{} } " +
"} }";

// 3. 发通知 + 塞 extras
Notification notification = new Notification.Builder(context, channelId)
    .setContentTitle(testTitle).setContentText(testText).setSmallIcon(smallIconResId)
    .build();
notification.extras.putString("miui.focus.param", islandParams);
nm.notify(1, notification);
```

### 2.2 MIPUSH 服务实现(云侧 regId 单播)

```java
// 1. Sender 构造(APP_SECRET 在小米开平创建应用时获取)
Sender sender = new Sender(APP_SECRET);

// 2. 构造 Message, 走 extra 传岛参数
Message message = new Message.Builder()
    .title("notification title")
    .description("notification description")
    .notifyId(123456)
    .extra("miui.focus.param", params)  // ← 同客户端, JSON 字符串
    .extra("miui.focus.pic_large1", "https://url/xxxxxxx1.jpg")
    .extra("miui.focus.pic_large2", "https://url/xxxxxxx2.jpg")
    .extra("miui.focus.pic_large3", "https://url/xxxxxxx3.jpg")
    .build();

// 3. 发送(仅支持 regId 单播, 不支持 topic/alias 全推)
Result result = sender.send(message, regId, 3);  // 第三个参数为重试次数
```

**MIPUSH 图片要求**(text_2131 §2.2 / text_2146 §三.7):
- 单张图 ≤ 100KB(超过直接判下载失败)
- 全流程下载超时 180s
- URL 必须是 HTTPS(HTTP 视为失败)
- 宽高比 1:1 ~ 16:9(1.0~1.78)
- 单通知最多 10 张(超过只顺序下前 10 张)
- 系统为通知下载图消耗的流量会计入应用分发配额

## 三、param_v2 全字段表(根级 + 嵌套)

`miui.focus.param` 字符串是 JSON, 根对象 `param_v2` 包含如下字段:

| 字段路径 | 必选 | 类型 | 默认 | 含义 |
|---|---|---|---|---|
| `business` | 是 | String | — | 运营场景(数据统计用), 示例值 `taxi` |
| `protocol` | 否 | Int | 1 | 协议版本, 1=OS2/OS3 通用 |
| `enableFloat` | 否 | Boolean | false | 通知更新时是否自动展开为展开态 |
| `islandFirstFloat` | 否 | Boolean | true | 通知**首次**出现时是否自动展开 |
| `updatable` | 否 | Boolean | false | 是否为持续性通知(可被 sequence 字段更新) |
| `timeout` | 否 | Int(min) | 720 | 通知默认消失时间; **0=用默认 720min; -1=5 秒后消失**(text_2146 §三.4) |
| `cancel` | 否 | Boolean | false | 是否直接结束通知(对 MIPUSH 触发移除) |
| `reopen` | 否 | String | `close` | 同 notification id 取消后是否再次显示, `reopen`/`close` |
| `filterWhenNoPermission` | 否 | Boolean | false | 焦点通知权限被关时, `true`=通知被过滤不显示, `false`=正常显示 |
| `orderId` | 否 | String | — | 订单号, MIPUSH 字段 |
| `sequence` | MIPUSH 实时更新类必传 | Long | — | 本订单第几次更新, 避免乱序 |
| `param_island` | 是 | Object | — | 岛相关数据(下面专列) |
| `extraInfo` | 否 | Object | — | 扩展信息(车类型/颜色等业务自定义) |
| `carType` | 否 | String | — | 汽车类型(原文档示例, 业务字段) |
| `carColor` | 否 | String | — | 汽车颜色(同上) |
| `baseInfo` | 否 | Object | — | 焦点通知数据(标题/内容/颜色/类型), 见下 |
| `hintInfo` | 否 | Object | — | 提示型焦点通知(数量/动作) |
| `ticker` | OS2 状态栏 | String | — | 状态栏焦点通知文案(OS2 必传) |
| `tickerPic` | 否 | String | — | 状态栏图标, 对应 `miui.focus.pic_xxx` |
| `tickerPicDark` | 否 | String | — | 暗黑模式状态栏图标 |
| `aodTitle` | AOD | String | — | 息屏焦点通知文案 |
| `aodPic` | 否 | String | — | 息屏图标, 对应 `miui.focus.pic_xxx`(AOD = OS3 新增字段, 见 text_2146 §四.3) |
| `actions` | 否 | Array | — | 交互按钮列表, 每项 `{"action": "miui.focus.action_xxx"}` |

### `param_island` 子表(岛属性)

| 字段 | 必选 | 类型 | 默认 | 含义 |
|---|---|---|---|---|
| `islandProperty` | 否 | Int | 1 | 1=信息展示为主, 2=操作为主 |
| `islandOrder` | 否 | Boolean | false | 通知更新时摘要态隐藏时, 是否更新岛排序 |
| `islandTimeout` | 否 | Int(s) | 3600 | 岛自动消失时间(单位**秒**), 与 `timeout`(分钟)区分, 见 text_2146 §三.6 |
| `dismissIsland` | 否 | Boolean | false | 摘要态是否消失 |
| `highlightColor` | 否 | String | — | 文字本强调色(hex) |
| `bigIslandArea` | 是 | Object | — | 大岛内容(模板相关, 见模板库) |
| `smallIslandArea` | 是 | Object | — | 小岛内容(模板相关) |
| `shareData` | 否 | Object | — | 拖拽分享参数(标题/图/正文/分享内容) |

### `shareData` 字段(拖拽分享)

| 字段 | 必选 | 类型 | 含义 |
|---|---|---|---|
| `pic` | 是 | String | 拖拽时卡片上的展示图片, 对应 `miui.focus.pic_xxx` |
| `title` | 是 | String | 拖拽时卡片标题 |
| `content` | 是 | String | 拖拽时卡片正文 |
| `shareContent` | 是 | String | 分享内容文本 |

**拖拽分享支持范围**(text_2146 §一.5): 微信、QQ、短信、浏览器, 卡片/文字/链接三种形式; **不支持小程序**。当前官方口径"适合打车、外卖场景分享", 课表场景的"分享到群提醒"可用但需测试落地体验。

## 四、图片/Action 数据怎么挂到通知上(双 Bundle)

岛参数 JSON 里出现的 `miui.focus.pic_xxx` / `miui.focus.action_xxx` 都是**键引用**, 实际数据通过 `notification.extras` 里的两个 Bundle 投递:

```java
// 图片 Bundle
Bundle pics = new Bundle();
pics.putParcelable("miui.focus.pic_start",  Icon.createWithResource(this, R.drawable.start));
pics.putParcelable("miui.focus.pic_end",    Icon.createWithResource(this, R.drawable.end));
bundle.putBundle("miui.focus.pics", pics);

// 交互按钮 Bundle
Bundle actions = new Bundle();
Intent intent1 = new Intent(ACTION_FOCUS_NOTIFICATION);
intent1.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);  // 触发广播的 PendingIntent 必须加
PendingIntent pi1 = PendingIntent.getBroadcast(this, 0, intent1,
    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
Notification.Action action1 = new Notification.Action.Builder(
    Icon.createWithResource(this, R.drawable.pausebutton), "title", pi1).build();
actions.putParcelable("miui.focus.action_1", action1);
// ...更多 action
bundle.putBundle("miui.focus.actions", actions);

builder.addExtras(bundle);  // 挂到通知上
```

**要点**:
- 触发广播的 PendingIntent 必须加 `Intent.FLAG_RECEIVER_FOREGROUND`, 否则后台收不到
- `PendingIntent.FLAG_IMMUTABLE` 在 Android 12+ 强制要求
- 岛 JSON 的 `actions[].action` 字段值要等于 Bundle 里的 key 字符串, 两者配对

## 五、综合示例(text_2131 §四 完整照录)

```java
String islandParams = "{\n" +
"  \"param_v2\": {\n" +
"    \"protocol\": 1,\n" +
"    \"business\": \"taxi\",\n" +
"    \"enableFloat\": true,\n" +
"    \"updatable\": true,\n" +
"    \"ticker\": \"ticker\",\n" +
"    \"tickerPic\": \"miui.focus.pic_ticker\",\n" +
"    \"aodTitle\": \"aodTitle\",\n" +
"    \"aodPic\": \"miui.focus.pic_aod\",\n" +
"    \"param_island\": {\n" +
"      \"islandProperty\": 1,\n" +
"      \"bigIslandArea\": {\n" +
"        \"imageTextInfoLeft\": {\n" +
"          \"type\": 1,\n" +
"          \"picInfo\": { \"type\": 1, \"pic\": \"miui.focus.pic_imageText\" },\n" +
"          \"miui.focus.paramtextInfo\": {\n" +
"            \"frontTitle\": \"充电中\", \"title\": \"24%\",\n" +
"            \"content\": \"剩5分钟\", \"useHighLight\": false\n" +
"          }\n" +
"        },\n" +
"        \"picInfo\": { \"type\": 1, \"pic\": \"miui.focus.pic_imageText\" }\n" +
"      },\n" +
"      \"smallIslandArea\": {\n" +
"        \"picInfo\": { \"type\": 1, \"pic\": \"miui.focus.pic_imageText\" }\n" +
"      },\n" +
"      \"shareData\": { \"title\": \"share_title\" }\n" +
"    },\n" +
"    \"baseInfo\": {\n" +
"      \"title\": \"待取件\",\n" +
"      \"content\": \"安宁华庭2区8号底商店菜鸟驿站\",\n" +
"      \"colorTitle\": \"#006EFF\",\n" +
"      \"type\": 2\n" +
"    },\n" +
"    \"hintInfo\": {\n" +
"      \"type\": 1, \"title\": \"2件包裹\",\n" +
"      \"actionInfo\": { \"action\": \"miui.focus.action_test\" }\n" +
"    },\n" +
"    \"extraInfo\": { \"carType\": \"YU7\", \"carColor\": \"白色\" }\n" +
"  }\n" +
"}";
```

## 六、清除与降级

- **清除**: 调用原生 `NotificationManager.cancel(id)` 即可, 岛同步消失(text_2131 §三.7, 链 Android 官方文档)
- **降级**: `filterWhenNoPermission=false` 时即使焦点通知权限被关, 通知仍正常显示(只是不呈岛形态); `filterWhenNoPermission=true` 时静默丢弃(text_2131 §二.1 字段表)
- **MIPUSH 通道**: `cancel=true` 直接移除焦点通知和岛(text_2146 §三.5)

## 七、媒体通知(text_2131 §六)

音乐/视频/音频类应用接媒体通知能力后**自动上岛**, 具体接入参看独立文档:
- 《Xiaomi HyperOS 媒体通知适配说明》
- 《媒体通知岛上拖拽分享适配说明》(拖拽分享能力**需单独适配**)

非媒体类应用(课表/外卖/打车等)走前文"客户端/MIPUSH"两条路, 不走媒体通知自动上岛逻辑。

## 八、Sleepy 落点

| 判断 | 说明 |
|---|---|
| 走哪条路 | 课表提醒 = 客户端本地发(MIPUSH 通道适合外卖/打车这种有服务端订单流的场景, 课表无服务端订单) |
| 模板选哪个 | 文档列了 OS2/OS3 通用模板, 课表类建议选"信息展示为主"(`islandProperty=1`), 大岛 A/B 区放时间+课程名+教室, 小岛放单图标 |
| 文案限制 | 岛上左右各 4 个汉字, 超长可用图文组件, **最后两字可缩小展示**(text_2146 §二.5) |
| `sequence` 必传 | 课表状态更新(如"下节课: 高数@A301") 每次必须递增, 否则岛排序会乱 |
| `updatable=true` | 课表中途状态变化(下节提醒)要靠这条, 不设 = 单次通知不更新 |
| `timeout` 怎么填 | 课表单节课一般 45min, 默认 720min(12h) 会一直挂着, 推荐 `timeout=60`(1h, 留余量) 或按节次结束时间算 |
| 权限被关 | 优先 `filterWhenNoPermission=false` + 引导用户去开, 避免静默丢消息 |
| 大小折叠适配 | 标准模板**不需要单独适配小折叠**, 小米自行适配(text_2146 §四.2) |
| 抓 284 日志 | 拨号 `*#*#284#*#*`, 2-3 分钟后 `sdcard/MIUI/debug_log` 下生成日期命名的 zip, 配合消息 id 复现时间点一起给官方(text_2146 §四.6) |
| 收费 | 官方明示"焦点通知旨在提升用户体验, 暂不涉及收费"(text_2146 §四.1) |
