[evidence=A] 抓取 2026-09-13 · 源URL: https://developers.oppomobile.com/ + https://open.oppomobile.com/menu/menu/get-menu-list (官方 API 直采)

# OPPO 开放平台门户总览

## 两个门户

| 门户 | 面向 | 服务清单(页面原文) |
|---|---|---|
| open.oppomobile.com | 中国大陆开发者 | 应用分发(软件商店/游戏中心/主题商店/快应用/小游戏)、智慧服务(移动服务/账号/推送/游戏/钱包/安全/CameraUnit/Hyper Boost/ARUnit/MediaUnit/GalleryUnit)、开发支持(云测/适配)、推广服务、AI & IoT(HeyThings IoT/小布开发者平台)、企业业务 |
| developers.oppomobile.com | 国际开发者 | Application service: App Market / Game Center / Theme Store / HeyFun; Development service: Remote Phone / Mobile services(Push) / Game Services / Hyper Boost / CameraUnit / PhysRay Engine; Promotion service: App Review / Customized Promotion; AI & IoT: HeyThings IoT / Breeno 语音技能平台 |

两门户账号体系独立(国际站登录表单要求 username 4-12 位数字字母)。国际站联系邮箱 devservice@oppo.com。

## 门户数据(开放平台首页公布, 2026-09 抓取)

- OPPO 开发者数量 185 万+, 创作者数量 5.6 亿+
- 软件商店月分发量 6300 万+
- PUSH 定位(国际站原文): "OPPO PUSH is a system level channel on ColorOS, providing developers with stable and efficient message push services."

## 文档中心结构(官方 menu API `POST /menu/menu/get-menu-list` 返回的文档树)

开发 > 移动服务: 应用服务(账号服务 doc=10847, 推送服务 doc=11207, 游戏SDK doc=11871, 广告服务 doc=11495, 钱包服务 doc=10772), 智慧服务(负一屏 doc=10214, 流体云 doc=13270, 设计规范 doc=13271, 服务接入指南 doc=13258, 小布建议 doc=13263), 大模型(安第斯 doc=12811), 安全服务, 系统能力(动效能力 doc=13771, Hyper Boost doc=10749, 网络能力 doc=11422, 窗口能力 doc=12510, 系统资源服务 doc=13210, 手写笔服务 doc=13305), 图形处理(物光引擎 doc=11377, AR doc=10268), 多媒体, 端侧AI, 互联互通(CarLinkUnit doc=10978, 穿戴 doc=11282, 实时通讯 doc=11587, 视频接续 doc=11318, 健康研究 doc=11612), 开发工具(远程真机 doc=11472, 自动化测试 doc=11473, 设备开放 doc=11878, 服务编排工具 doc=11600), 接口鉴权(doc=12168)。

分发 > 应用分发: 软件商店 doc=10035, 游戏中心 doc=11389, 小游戏 doc=10498, 游戏服务 doc=10941; 发布应用(应用更新 doc=11523, 分阶段发布 doc=11546, 多包上传 doc=10942, 边下边玩 doc=11524); 运营应用(素材ABtest doc=11525, 组件化活动 doc=11527, 统一链接 doc=13203, 下载赋能 doc=13256); 开发者支持(应用审核规范 doc=12129, 升级64位架构 doc=10948, **Android 17 适配 doc=11814**, 折叠屏适配 doc=11815, 平板适配 doc=11819, 手机适配 doc=10797, 日历服务 doc=12059, 大屏适配服务 doc=11053)。内容分发: 主题商店 doc=10970, 内容号 doc=11289, 乐划锁屏 doc=11554。服务分发: 快应用 doc=10628, 快应用模板 doc=13105。

## 抓取通道(对后续归档有用)

- 文档树 API `POST https://open.oppomobile.com/menu/menu/get-menu-list`(Content-Type: application/json, body `{}`)免登录返回全量菜单, 含每个条目的 `document_id`。
- 旧版文档正文 API `GET https://open.oppomobile.com/wiki/doc/detail?id=<doc_id>`: 对 2022 年前后入库的文档直接返回 JSON(id/content/title), 免登录。本文档系列(shelf/darkmode/appstore/quickapp 各档案)正文全部由此通道取得。
- 2023 年后新增文档(SeedlingSupportSDK、流体云、Android 17 适配等)走新版 SPA `https://open.oppomobile.com/documentation/page/info?id=<id>`, 数据接口 `POST /oneoppoapi/doc/detail` 需要登录态(未登录返回 `{"code":300005,"message":"登录失效"}`)。旧 API 对这些 id 返回跳转壳页(无正文)。
- 页面路由: `/new/developmentDoc/info?id=` 与 `/documentation/page/info?id=` 等价(新版 SPA 有 basePath 重写表); `/wiki/doc?id=` 旧版跳新版。
