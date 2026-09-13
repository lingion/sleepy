[evidence=A] 抓取 2026-09-13 · 源URL: https://open.oppomobile.com/wiki/doc/detail?id=10214 / 10216 / 10213 / 10804 / 11410 / 10668 / 10629 (官方 API 直采)

# OPPO 桌面 / 负一屏(Shelf) 行为与卡片接入

## 负一屏(负一屏介绍, doc 10214)

- 定义(原文): "负一屏是基于桌面级应用场景、结合用户位置信息、精准画像和AI智能推荐…的服务分发平台。在桌面首屏右滑后可进入负一屏, 负一屏框架从上到下分别为搜索栏、快捷功能区、卡片区+信息流组合。"
- 数据(官方 2022.4 更新): 负一屏日活 DAU 7000 万, DAU/MAU 50%。
- 形态: 快捷功能(点击直达) + 卡片两种。

## 负一屏卡片三种类型(卡片类型介绍, doc 10804)

| 卡片类型 | 接入方式 | 尺寸 | 特点(原文要点) |
|---|---|---|---|
| 快应用卡片(推荐) | 快应用技术框架, 开放平台更新发布 | 2x2 / 4x2 / 4x4 | 不受 APP 安装限制; 需要快应用/H5 载体; 覆盖 Android O/P/Q/R/S; 可实时刷新 |
| 插件卡片 | OPPO 提供的技术框架(需支持 AndroidX), 跟随自有 APP 版本发布 | 2x2 / 4x2 / 4x4 | 依赖用户安装 APP; 基于 OPPO 框架支持更多动画; 覆盖 Android Q/R/S(O/P 暂不支持); 不支持实时刷新 |
| 安卓原生卡片 | 安卓原生技术框架, 跟随 APP 版本发布 | 2x2 / 4x2 / 4x4 | 按 OPPO 负一屏卡片设计规范适配即可; 仅覆盖 ColorOS 12.1+(依赖桌面版本, 暂不支持向下兼容); 不支持实时刷新 |

对 Sleepy 的含义: Android AppWidget 体系不在负一屏三种卡片通道内 — 负一屏卡片是独立生态(快应用 rpk / OPPO 插件框架 / 定制原生卡)。Sleepy 只能走 Android AppWidget 桌面挂件, 负一屏不可达。

## 快应用卡片开发关键差异(快应用卡片开发指南, doc 11410)

- 卡片唯一标识 `hap://widget//[path][?key=value]`; 同一宿主中多张卡片运行时互相隔离, 卡片间不能通信。
- 卡片 jsbundle 必须独立渲染: 不能用 app.ux 公共能力组件, 不能引用本地图片资源文件(manifest.json 只拉取卡片目录内资源)。
- 外部(快应用或原生 app)不支持路由到卡片; 卡片无页面栈, 只能 router.push / a 跳转到快应用页面; 卡片不支持 http(s) 直跳。
- 生命周期: 支持 onInit/onReady/onShow/onHide/onDestroy; 不支持 onBackPress/onMenuPress/onRefresh。
- 组件限制: 容器支持 div/popup/stack, 不支持 list/list-item/refresh/richtext/swiper/tabs(与卡片滑动宿主冲突); 媒体支持 video(无全屏), 不支持 camera/canvas/map/web; 事件支持 click/longpress/focus/blur, 不支持 appear/disappear/swipe。
- 接口: 支持 system.fetch(数据请求, 不支持文件与 WebSocket)、system.storage、service.exchange、剪切板、getLocation(单次)、日历事件、电量、RSA/AES; 不支持上传下载、通知、分享、弹窗、webview、传感器订阅、桌面图标、录音、短信、震动; 厂商服务仅支持账户(简化模式), 不支持统计/推送/支付/健康/广告。
- manifest.json: `widgets` 字段定义卡片(key=卡片目录名, path 必须唯一且以 / 开头, 禁止 ".", "@#$&+()-\?:|"、空格、"//"、非 ASCII); `themeMode`(-1 跟随系统/0 日间/1 夜间)控制暗色; `textSizeAdjust`(none/auto)控制字体跟随, 快应用与卡片共享该配置。
- 暗色适配(Q5/Q6/Q15): themeMode=-1 只保证系统自动反色(#fff→#000); 需要指定色值时用 configuration.getThemeMode(建议 onShow 中取, 系统切主题触发 onShow) + computed 动态控制; 组件加 `forcedark="false"` 可豁免自动反色。卡片宿主中无法自动反色(仅负一屏反色)。
- 尺寸(Q1): 快应用 px 是相对单位, designWidth 默认 750; 设 360 则设计稿 1:1, 设 1080 需按 `36 * 1080 / 360 = 108` 换算。根组件宽度建议 100% 不用固定 324px。font-weight 只支持 bold/normal。
- 本地调试(Q7): 卡片需先上架测试环境(负一屏拿卡片链接), 之后新 rpk push 到 `/sdcard/rpks`(同包名只留一个), 快应用引擎测试包下拉刷新选本地模式, 负一屏重加卡片看效果。rpk 推送路径: `/sdcard/Android/data/com.nearme.instant.platform/files/rpks`。
- 标题栏(Q19): 旧版由负一屏定义, 现在完全由开发者自定义。

## 快应用卡片订阅接口(卡片订阅功能, doc 10668)

`@system.card` 模块: `card.checkState(OBJECT)`(path 必填; success 返回 status: 1=可订阅未订阅, 2=已订阅, 3=不可订阅); `card.add(OBJECT)`(path/description/illustration 必填; success/cancel/fail/complete 回调)。注意: 官方原文提醒部分 CP 把卡片名写成中文或默认 CardDemo, 应用 path 作标识。

## 快应用系统入口(入口和场景, doc 10629)

OPPO 快应用覆盖设备数 1.6 亿, 20+ 系统入口: 负一屏、全局搜索(精准+泛)、浏览器名站/信息流、URL 跳转(微信/QQ 分享链接)、乐划锁屏、智能短信、软件商店/快应用中心、小布语音助手。

## 负一屏平台合作协议要点(负一屏平台合作协议, doc 10213)

- 接入免费, 分发服务"部分免费", 收费分发另行书面协议。
- 欢太有权提前 30 天通知后随时变更/终止负一屏, 无需对开发者担责; 有权因促销/规则/地区要求修改负一屏(名称、文本、触发逻辑)。
- 开发者义务(原文要点): 数据接口必须 https 且"肯定能返回有效及准确的数据"; 快应用升级而负一屏未升级必须兼容, 否则先下架负一屏再升级快应用; 账号绑定/解绑/退出时必须调用 OPPO 接口执行系统通知; 提交的分类与触发策略与平台分发策略冲突时"必须遵从 OPPO 负一屏平台的调配和修改"。
- 终止: 开发者终止需提前 3 个月书面通知且欢太书面同意; 欢太可在提前 30 天通知后无理由终止。
