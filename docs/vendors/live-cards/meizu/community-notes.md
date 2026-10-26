[evidence=B/C] 整理 2026-09-17 · 源: GitHub demo 仓库 (已逐行读源码) + IT之家报道链 + searXNG 检索。社区资料少是魅族实时卡片的现状本身 (资料稀缺=事实)。

# 魅族 Flyme — 社区资料与开源先例

## 一、开源 demo (本库最关键的一手资料)

**Ruyue-Kinsenka/Flyme-Live-Notification-Demo**
- https://github.com/Ruyue-Kinsenka/Flyme-Live-Notification-Demo
- Kotlin, 创建 2025-09-13, 最后 push 2025-09-15, 8 star / 1 fork (2026-09-17 查)
- 定性: 全网唯一公开的 Flyme 实况通知接入代码。extras 键方案 (notification.live.*) 全部出自此仓库; README 自述"核心代码说明(Ai润色)", 三个枚举 (operation/type/capsuleType) 作者自己标注 idk。
- 已克隆到 /tmp/flyme-live-demo (LiveNotificationManager.kt 全文 <150 行, 双 RemoteViews 结构)。
- 仓库名 Kinsenka + 内容风格疑似魅族员工/前员工流出 (未证实, 记录为疑点), demo 内含对魅族粗口文案, 非官方仓库。

## 二、媒体报道链 (时间线)

| 时间 | 事件 | 源 |
|---|---|---|
| 2024 (AIOS 预热期) | 官方公布实况通知: 息屏/锁屏/桌面全场景流转; 投屏/倒计时/行程场景"药丸"提醒 | ithome.com/0/766/240, c114 同稿 |
| 2024-07-16 | Flyme AIOS 11.0.0 稳定版推魅族 21 系列, 实况通知落地 | ithome.com/0/782/152 |
| 2025-08-18 | PM 陈家沂宣布 12306 实况通知进最后内测; 实装效果覆盖 Aicy 纵览+实况通知+锁屏 | ithome.com/0/876/301 |
| 2025-10-14 | AIOS 12.3.0.0A 上线 Aicy 铁路 12306 实况通知 (20/21 系列+Lucky 08) | ithome.com/0/889/345 |

- 媒体侧定性词: "药丸"提醒、"实时活动" — 魅族官方避开灵动岛/流体云营销词, 与用户手册「实况通知」命名一致。
- 12306 报道同稿给出五家横向对照: 华为 (购票改签/晚点/检票口变更) / OPPO (流体云+小布建议) / vivo (原子岛+小V建议+原子通知) — 各厂商实时卡片接入门槛的横向锚点。

## 三、社区技术文章现状

- searXNG 双轮检索 (魅族 Flyme 实况通知 胶囊 三方适配 / Flyme AIOS 实况通知 开发者接入): **无 CSDN/掘金/知乎技术实操文章命中** — 社区只有媒体报道与 demo 仓库。
- 对比: 一期小组件抓取时 CSDN 有 requestPinAppWidget 渠道矩阵 (含魅族行缺失), 本次连矩阵类文章都无 — Flyme 实况通知社区生态远小于华为实况窗/小米焦点通知。
- 现状解读: 官方不开放文档 → 无开发者受众 → 无社区文章, 死循环; demo 仓库 8 star 也印证受众极小。

## 四、对 Sleepy 的落点

1. 接入参考 = 唯一 demo 仓库, 已完整留档 (live-notification-api.md 逐键抄录 + /tmp 克隆, 若 /tmp 清理可随时重克隆)。
2. 无社区踩坑资料可用 → 真机验证是唯一路径, 预期成本高于其他厂商。
3. 若 Sleepy 落地 Flyme 实况通知, 即为社区首例 (可反哺开源, 用户名下仓库)。
