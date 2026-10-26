# OPPO · 桌面形态 —— "拖长变形"唯一有官方记载

> 证据等级: A(开放平台实测) + B(生产环境反编译佐证)—— open.oppomobile.com / coloros.com 实测(2026-09-17)。关联底稿: [oppo-coloros-fluid-cloud.md](../oppo-coloros-fluid-cloud.md)
> 其他能力: [负一屏](../negative-one-screen/oppo.md) · [长按菜单](../long-press-menu/oppo.md) · [设计规范](../oem-design-specs/oppo.md) · [开源](../oem-open-source/oppo.md)

## 图标"拖长"变形(Sleepy 核心关注)
- 官方《自定义桌面》(2025-12-12)原文: "**无缝拖拽桌面图标大小,自由选择 1×2、2×1、2×2 样式,还可以在图标里添加应用快捷方式**"
- 方法 = 桌面长按图标拖动扩展角标
- 同页另含: 文件夹变形 / 无字桌面 5×7 / 卡片堆叠(长按卡片拖至相同尺寸合并)
- URL: `https://www.coloros.com/article/A00000124/`
- **关键事实**:
  - **1×3 档位无官方记载**
  - **无第三方声明 API** —— 变形由系统按 AppWidget 尺寸档自动赋予,未验证推断,建议开放平台工单确认

## Seedling / UPK 卡片(独立体系,非 AppWidget)
- 服务以 `.upk` 包(JS + CSS + OML 标记语言)发布
- **尺寸档位**(SeedlingCardSizeEnum,桌面/负一屏): 1×2 / 2×2 / 2×4 / 4×4,另有 widget_1×1
- 文档: `https://open.oppomobile.com/documentation/page/info?id=12691`
- 模板 id: 12657 / 12658
- 接入: 商务授权码 + Pantanal DevStudio IDE(见 [负一屏](../negative-one-screen/oppo.md))

## 流体云(实时形态)
- UPK 体系,`seedling-type: live|immediate`,三形态 notification_sm(胶囊)/ md(大胶囊)/ lg(展开面板);模板 capsule/general/graphic/text/symmetry;API 3.0.0 起多页面;ColorOS 14.0(API 2.0)起: `https://open.oppomobile.com/documentation/page/info?id=12965`
- 产品页: `https://www.coloros.com/article/A00000075/`
- → 实时卡片域详见 [live-cards](../live-cards/INDEX.md)

## 双轨: 标准 AppWidget 也可上架
- 负一屏"桌面插件卡规范"自述"插件能力基于安卓系统": `https://open.oppomobile.com/documentation/page/info?id=13335`
- 即 Seedling/UPK 与 AppWidget 在负一屏并存,前者须商务、后者免商务

## ColorOS 16 / realme UI 7.0
- "灵感桌面"图标/组件/卡片堆叠拉伸: `https://ask.zol.com.cn/x/35871893.html`、`https://zhidao.baidu.com/question/277321598416185525.html`
- realme UI 7.0 同源: `https://realmebbs.com/post-details/1991174981971066880`
