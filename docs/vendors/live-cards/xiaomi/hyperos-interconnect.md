[evidence=A] 抓取 2026-09-18 · 源: 小米澎湃 OS 开发者平台开发指南(pId=2131, 2026-01-29 更新) · 全文: ../_sources-xiaomi-raw/text_2131.txt

# 小米 HyperOS — 岛通知与系统触点

> 结论先行: 小米超级岛不是单独的 View, 而是原生 Notification 的一组扩展参数。`param_v2` 同时描述岛、AOD、OS2 状态栏和拖拽分享; 系统根据协议版本及权限, 把同一通知投到不同触点。

## 触点表

| 触点 | 参数/入口 | 说明 |
|---|---|---|
| 岛摘要态/大岛/小岛 | `param_v2.param_island` | OS3 支持; `bigIslandArea`、`smallIslandArea` 按模板填充 |
| 息屏 AOD | `aodTitle`、`aodPic` | `aodPic` 引用 `miui.focus.pic_*`; 不传图标则用应用图标 |
| OS2 状态栏 | `ticker`、`tickerPic`、`tickerPicDark` | 已适配 OS2 时可不传 ticker; 未适配时不传则状态栏不展示 |
| OS2 自定义状态栏 | `miui.focus.rvBar`、`miui.focus.rvBarNight` | Light/Dark 两套 `RemoteViews`, 官方建议优先用具体 ticker 参数 |
| 媒体通知 | 独立媒体通知适配 | 音乐、视频、音频场景接入后自动上岛; 拖拽分享另有适配文档 |
| 拖拽分享 | `param_island.shareData` | 图片、标题、正文、分享内容; 微信/QQ/短信/浏览器, 暂不支持小程序 |

## 三个查询接口

```java
// 岛能力
boolean supportIsland = isSupportIsland("persist.sys.feature.island", false);

// 协议: 0=无, 1=OS1, 2=OS2, 3=OS3
int version = Settings.System.getInt(
    context.getContentResolver(), "notification_focus_protocol", 0);

// 权限: content://miui.statusbar.notification.public
Bundle result = context.getContentResolver().call(
    Uri.parse("content://miui.statusbar.notification.public"),
    "canShowFocus", null, extras);
```

`supportIsland` 与 `notification_focus_protocol` 是两件事: 前者回答设备是否有岛能力, 后者回答焦点通知协议版本。业务代码不要只查其中一个。

## 状态栏 RemoteViews

如果采用自定义状态栏, 通知 extras 使用:

```java
notification.extras.putParcelable("miui.focus.rvBar", lightView);
notification.extras.putParcelable("miui.focus.rvBarNight", darkView);
```

这两个键分别对应 Light/Dark 模式。官方同时给了 `ticker`/`tickerPic` 方案, 课表类优先使用文本参数, 避免自定义 RemoteViews 带来的版本差异。

## 系统互联边界

官方开发指南只确认以上通知触点、媒体通知和拖拽分享。跨设备同步、平板接力、PC 状态同步的协议未在这三份材料中公开, 不能把“HyperOS 互联”泛化成 Sleepy 可以直接调用的跨设备 API。

## Sleepy 落点

- 课程卡的主通道是岛摘要态/大岛/小岛; 状态栏和 AOD 作为同一通知的补充表现。
- 先查协议版本, 再选择 OS2 焦点通知或 OS3 岛模板。
- 课表无需媒体通知适配; 拖拽分享可以留作课程卡分享的后续实验, 不把它当首版依赖。
- Android 16 Live Updates 与小米超级岛是否互通, 本档官方材料未说明, 记为 N/A。
