[evidence=A] 抓取 2026-09-18 · 源: 小米澎湃 OS 开发者平台开发指南(pId=2131)/常见 Q&A(pId=2146) · 全文: ../_sources-xiaomi-raw/text_2131.txt / text_2146.txt

# 小米 HyperOS — 焦点通知行为与限制

> 结论先行: `timeout` 管焦点通知, 单位分钟; `islandTimeout` 管岛, 单位秒。两者互不替代。`cancel=true` 直接清理焦点通知和超级岛, `updatable=true` 才适合持续更新。

## 生命周期字段

| 字段 | 单位 | 行为 |
|---|---:|---|
| `timeout` | min | 焦点通知默认消失时间; 默认 720。传 `0` 使用默认值, 传 `-1` 约 5 秒后消失 |
| `islandTimeout` | s | 岛自动消失时间; 默认 `60*60` |
| `cancel` | — | true 时直接结束, MIPUSH 下发时移除焦点通知和岛 |
| `dismissIsland` | — | true 时摘要态消失 |
| `updatable` | — | 是否持续性通知 |
| `reopen` | — | 同 notification id 被 cancel 后, `reopen` 再次显示, `close` 不显示 |
| `sequence` | — | MIPUSH 实时更新必传, 递增避免更新乱序 |

官方 FAQ 对 `timeout=0` 的解释是“使用默认值”,不是永不消失。课表卡不要把 0 当常驻开关。

## 展开和排序

- `islandFirstFloat=true`: 首次出现自动展开, 默认 true。
- `enableFloat=true`: 更新时自动展开, 默认 false。
- `islandProperty=1`: 信息展示为主; `2`: 操作为主。
- `islandOrder=true`: 更新时即使摘要态隐藏, 也更新岛排序。
- `filterWhenNoPermission=false`: 用户关闭焦点通知权限时仍按普通通知显示。
- `filterWhenNoPermission=true`: 权限关闭时过滤, 不显示。

## 展示约束

- 摘要态点击展开的卡片背景只能为深色。
- 通知栏可浅色或深色。
- 普通岛文案左右各约 4 个汉字; 长文案可用图文组件, 最后两个汉字可缩小。
- 标准模板不需要为小折叠单独适配, 小米会自行适配。
- 自定义图片: 每张 ≤100KB, HTTPS, 宽高比 1:1 到 16:9, 最多 10 张; 下载最长等待 180s。
- `miui.focus.param` payload 不超过 3072 字节。

## 推送与进程

MIPUSH 下发与普通消息相同: 应用进程被杀后, 设备联网仍可收到消息。客户端本地实现则要求应用客户端保持活跃, 这是两条路径的实际差别。

## 清除与调试

```java
notificationManager.cancel(notificationId);
```

调用原生 cancel 即可清除通知和岛。故障复现后记录消息 id 和点击时间, 拨号 `*#*#284#*#*`; 等 2-3 分钟, 日志在 `sdcard/MIUI/debug_log` 下生成日期命名 zip。

## Sleepy 落点

一节课按一次性提醒发出, `timeout` 按课程结束时间留余量。需要更新“下节课”时用同一业务通知并递增 `sequence`; 长空档不要依赖默认 720 分钟, 应在真实节点重建或更新。岛文案固定短句, 课程名和教室放大岛字段, 超长内容回普通通知详情页。
