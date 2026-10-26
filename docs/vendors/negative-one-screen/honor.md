# 荣耀 · 负一屏

> 证据等级: A —— developer.honor.com 官方文档实测(2026-09-17)。
> 其他能力: [长按菜单](../long-press-menu/honor.md) · [桌面形态](../desktop-morph/honor.md) · [设计规范](../oem-design-specs/honor.md) · [开源](../oem-open-source/honor.md)

## 体系
- 负一屏 = YOYO 建议卡片集 + 智慧服务。《YOYO建议介绍》V2(2025-02-13): `https://developer.honor.com/cn/doc/guides/101368` —— 原文"浅表入口-精准触达:桌面/负一屏 YOYO建议卡片集;全新入口:灵动胶囊、AOD、锁屏通知"

## 接入门槛: 无自助平台,商务白名单
- 《智慧服务业务介绍》V32(2025-09-10)列 5 类服务: 快捷服务图标 / 快应用 JS 卡片 / 快应用模板卡片 / 安卓 widget 卡片 / 内容接口服务
- 商务: `Developers_BD@honor.com` · 技术: `HISP@honor.com`
- 文档: `https://developer.honor.com/cn/doc/guides/100118`
- **内容接口服务**模式特殊: "卡片由荣耀侧提供,开发者按荣耀接口规范开发内容接口"(V12,2024-12): `https://developer.honor.com/cn/doc/guides/100161`

## 可自助的旁路
- 推荐数据有公开 SDK:场景化建议服务 **Suggestions Kit**(V2,2026-09-01;SDK 集成 101595、意图映射 101605): `https://developer.honor.com/cn/doc/guides/101586` —— 可自助集成,但露出与否仍由荣耀侧算法决定。
- 安卓应用卡片(可入荣耀卡片库,桌面为主):见 [桌面形态](../desktop-morph/honor.md)

## 缺口
- 商务准入通过率 / 周期 / 合作量级门槛无公开数据。
- 媒体报道佐证"负一屏即渠道"逻辑: `https://rich.online.sh.cn/content/2025-11/21/content_10401555.htm`、`https://www.geekpark.net/news/365938`
