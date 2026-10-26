[evidence=B/A] 抓取 2026-09-18 · A 级事实来自小米官方开发指南/接入流程/FAQ(pId=2131/2132/2146); 社区侧邮件和日志实践仅作 B 级补充

# 小米 HyperOS — 社区接入与排查笔记

> 结论先行: 社区最常踩的是“参数写对但权限/版本没开”。先查协议版本和焦点权限, 再查 `miui.focus.param` 大小、图片下载和 `sequence`。别先改模板。

## 公开入口

| 用途 | 入口 |
|---|---|
| OS2 焦点通知开发包/权限 | `mipush-permission@xiaomi.com` |
| 云侧接口 | `vip.api.xmpush.xiaomi.com` |
| 参数扩展 | `miui.focus.param`、`miui.focus.pic_xxx` |
| 权限查询 | `content://miui.statusbar.notification.public` + `canShowFocus` |
| 284 调试日志 | `*#*#284#*#*`，生成于 `sdcard/MIUI/debug_log` |

以上端点和键名都有官方原文依据。社区文章里出现的其他域名、未公开模板名不纳入实现依据。

## 排查顺序

1. `persist.sys.feature.island` 是否为 true。
2. `notification_focus_protocol` 是否为 2/3。
3. `canShowFocus` 是否返回 true。
4. `miui.focus.param` 是否为合法 JSON, 且整体不超过 3072 字节。
5. `business`、`param_island`、`bigIslandArea`、`smallIslandArea` 是否齐全。
6. 图片是否 HTTPS、≤100KB、数量≤10、宽高比在 1:1 到 16:9。
7. MIPUSH 更新的 `sequence` 是否递增。
8. 最后才看设备模板和 UI。

## 邮件申请模板骨架

官方 FAQ 要求的内容:

```text
收件人: mipush-permission@xiaomi.com
主题: 【申请开通小米焦点通知使用权限】-yymmdd-「APP名称」

【应用信息】：应用名称、包名、appid
【channel信息】：channel_id、channel名称、channel描述
【公司主体】：
【push负责人/对接人】：
【联系方式】：
【所申请的通知权限】：焦点通知
【申请内容】：接入场景、展示时机、消失时机、刷新节点、变更频次、预计持续时长
【产品方案】：触发场景截图、焦点通知效果图、交互设计
【使用期限】：长期使用至当年12月31日
```

申请声明必须明确不用于广告、恶意导流或其他影响体验的用途。长期权限到期前 2 周重发申请。

## 284 日志包

官方流程要求问题复现后记录消息 id 和点击时间点, 拨号触发抓取, 2-3 分钟后把日期命名 zip 连同消息 id、复现时间点发给官方。社区习惯是先保存 APK 构建签名、协议版本和 `param_v2` 原文, 方便把日志与一次发送对应起来; 这是排查建议, 非官方字段要求。

## 未证实的社区说法

- 某个具体模板名是否对所有 HyperOS 3 机型有效: N/A。
- 未公开的灰度比例和按机型放量算法: N/A。
- `miui.focus.*` 之外的隐藏参数: N/A。
- 个人开发者一定能通过审核的概率: N/A。

## Sleepy 落点

项目内保存每次发送的协议版本、权限查询结果和 `param_v2` 摘要, 但不要把通知完整内容写进普通日志。用户报告“没上岛”时, 先让其导出 284 zip 和消息 id, 走同一排查顺序。
