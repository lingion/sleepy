# OPPO · 负一屏

> 证据等级: A(开放平台实测,含"无权限查看"取证)—— open.oppomobile.com 官方文档实测(2026-09-17)。关联底稿: [oppo-coloros-fluid-cloud.md](../oppo-coloros-fluid-cloud.md)
> 其他能力: [长按菜单](../long-press-menu/oppo.md) · [桌面形态](../desktop-morph/oppo.md) · [设计规范](../oem-design-specs/oppo.md) · [开源](../oem-open-source/oppo.md)

## 门槛: 商务合作制,非全量开放
- 官方《负一屏简介》明言"服务开发前请与 OPPO 负一屏团队联系"
- 《卡片接入指南》(id=11981)实测**无权限查看** = 白名单实锤
- 简介页(适用 ColorOS 13+): `https://open.oppomobile.com/documentation/page/info?id=10214`
- 免开发仅有"快捷功能"(图标 + DPLink/H5 跳转, id=11584)

## 底层体系: 泛在服务 (Pantanal)
- 服务以 `.upk` 包(JS + CSS + OML 标记语言)发布,UMS 框架治理
- 卡片入口含 桌面 / 负一屏 / AOD / 锁屏 / 状态栏 / 通知中心 / 折叠屏外屏
- 卡片分类: 实时活动类(流体云) / 推荐类 / 订阅类 → `https://open.oppomobile.com/documentation/page/info?id=11593`
- 接入前提: 认证开发者 + **联系商务获取授权码/意图/服务 ID(需 PRD)**;ColorOS ≥13.1;配套 Pantanal DevStudio IDE → `https://open.oppomobile.com/documentation/page/info?id=12639`

## 旁路: 标准 AppWidget
- 负一屏"桌面插件卡规范"自述"插件能力基于安卓系统" = **标准 AppWidget 亦可上架插件位**: `https://open.oppomobile.com/documentation/page/info?id=13335`

## 小布建议
- 无独立公开 SDK,走意图框架服务接入(id=13258): `https://open.oppomobile.com/documentation/page/info?id=13263`

## 缺口
- 智能侧边栏: 开放平台文档树中无接入文档,未找到公开渠道。
- 商务授权的准入标准 / 排期无公开数据。
