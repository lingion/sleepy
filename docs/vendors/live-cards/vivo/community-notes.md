[evidence=B/C] https://dev.vivo.com.cn/documentCenter/doc/927 (OriginOS 6 预览版·机型名单) · https://github.com/imwangwang/vivo-service (反编译源, C) · https://swsdl.vivo.com.cn/appstore/developer/uploadFile/20250901/ib9x04/原子通知接入需求定义文档示例.xlsx (doc 894 内链) 抓取时间 2026-09-18

# vivo · community-notes.md

> 证据等级: B=官方文档中的社区实操相关信息 + 主流媒体 / C=社区逆向项目。本文件收"官方文档没直说、但实战要用"的情报;字段级 API 见 [realtime-capsule-api.md](./realtime-capsule-api.md)。

## 一、唯一公开的逆向资产: imwangwang/vivo-service (C 级)

| 项 | 内容 |
|---|---|
| 仓库 | github.com/imwangwang/vivo-service(vivo 系统框架反编译源) |
| 关键文件 | `VivoSuperXNotificationManager.java`(原子通知系统侧管理器, 内部代号 SuperX) |
| 已产出 | 白名单包名单 / `vivo.opt.notification.superx` feature flag(**VOS 海外机型整体禁用**)/ 模板注册表 {1,2,3,4,5,7}(7=自定义,文档未公开)/ 移除计时 2.5h·8h / 系统级频控 RateLimit(10, 5min)+RateLimit(60, 60min)/ 校验失败码 2207-2255 / 状态持久化路径 `/data/bbkcore/vivo_superx_info_list.xml` / 广播 `vivo.intent.action.SUPERX_STATUS_CHANGED` |
| 实战用法 | "发了通知没出卡" → 对照 2207 OVERSPEED(超频)/ 2208 NOTINLIST(未开通)/ 2211 SCENE_CLOSE(用户关场景)/ 2212 NOT_HAVE_SCENE(scene 未注册) 排查 |
| 风险 | 反编译结论与文档冲突点(如移除计时 2.5h vs 文档 2h)以真机为准 |

## 二、OriginOS 6 内测机型名单 (doc 927, A/B)

2025-10-17 起开发者预览版仅推: **X Fold5 / X200 Pro(含卫星通信版) / X200 / X200 Pro Mini / X200s / X200 Ultra / iQOO 13 / iQOO Neo 10 / Neo 10 Pro / Neo 10 Pro+** —— 仅限报名内测用户。

实战含义:
1. 原子岛新动效(音乐中卡律动/环境光)首发于这批机型, 联调 UI 需覆盖;
2. OriginOS 6 自定义通知改原生渲染(doc 901)——**存量 app 的 RemoteViews 通知在这批机型上最先出现显示异常**, 课表 app 若有自定义通知需提前适配;
3. 回退需官方降级工具(bbs.vivo.com.cn/thread/32215628)。

## 三、官方给的"作弊"资源

| 资源 | 直链 | 用途 |
|---|---|---|
| 原子通知 UI 模板 + 字体包 | doc 895 文内"点此即可下载"(swsdl 附件) | 设计稿直接套版 |
| 需求定义 xlsx 模板 | `https://swsdl.vivo.com.cn/appstore/developer/uploadFile/20250901/ib9x04/原子通知接入需求定义文档示例.xlsx` | 准入邮件第 5 要素 |
| 回执码表 | doc 896(300/301/302 主类型 + 31xxx/32xxx 子类型) | 云端推送联调排错 |
| 反射探测 API | `getSceneStatus(pkg, scene)` / `isSupportCustomFun(pkg, fun)` | 发送前判开关/判兼容, 官方明示使用 |

## 四、社区生态观察 (B/C)

| 观察 | 说明 |
|---|---|
| 三方接入经验帖稀少 | 原子通知为邮件准入制, 公开技术社区(CSDN/掘金)几乎无一手接入复盘; 主要知识源 = 官方文档 894/895/896 + 反编译仓库 |
| 与魅族路径不同 | vivo 走 Notification.extras 约定键(`notification.superx.*`)但**键名公开在官方文档**, 无需逆向即可构造; 魅族 extras 键则是纯社区逆向 —— vivo 是"半开放: 协议公开+准入不公开" |
| 原子岛 vs 灵动岛媒体对比 | 媒体普遍定位: 原子岛 = OriginOS 5 起的状态栏胶囊形态(对标灵动岛), 横屏跟随状态栏而非摄像头(官方 FAQ 口径, 见 atomic-notification.md §三) |
| 跑马灯劝退 | 官方规范明示岛上文本尽量精简, 跑马灯仅初次曝光 1 次且最长 20s —— 社区实测长文本模糊截断 |
| 海外无望 | 反编译: `!"vos".equals(FtBuild.getOsName())` —— VOS(海外)机型整体禁用 SuperX; 海外 vivo 用户不存在此通道 |

## 五、Sleepy 实战要点(按优先级)

1. **先备案再谈岛**: 本地通知渠道"系统消息-日程待办"备案(doc 930, 7 个工作日)是所有通知能力的前置底座;
2. **联调真机至少一台 OS5 原子岛机型 + 一台无岛机型**: island+capsule 必须双发(Flip 外屏回落胶囊);
3. **每活动独立 notifyId + changedRecord 防乱序**: 远程更新只支持 operation 1/2, 创建必须本地先发;
4. **岛左侧计时器文本可能是系统驱动**(反编译见计时器支持)——若真机验证成立, "下课倒计时"可绕过 10s/次刷新限制, 这是课表场景最值得验证的一个点(记 [gaps.md](./gaps.md) #2);
5. **慎用 setAutoCancel(true)**: 官方注意事项 #7, 通知栏点击后原子通知会被连带 cancel。
