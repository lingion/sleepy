# 桌面形态与变形 (Desktop Forms & Morphing) 六厂商速查

> 目录目的: 只回答一件事 —— **app 在桌面上能以什么形态存在、能被用户怎么变形**。卡片框架与 AppWidget 的关系 / 尺寸档位 / 图标拖长变形 / 第三方声明 API 有无。
> 抓取时间: 2026-09-17 (3 research agent 并行实测官方开发者平台)
> 抓取原则: 厂商官方页优先 → 社区/反编译二次核实;每文件首行标注证据等级;查无公开资料的显式记录,不编造 URL。
> 相邻域: [负一屏](../negative-one-screen/INDEX.md) · [长按菜单](../long-press-menu/INDEX.md) · [设计规范](../oem-design-specs/INDEX.md) · [开源与社区](../oem-open-source/INDEX.md) · [AppWidget 启动器兼容坑](../widget-vendor-specs/INDEX.md)

## 一页矩阵

| 厂商 | 卡片框架 | 官方尺寸档位 | 用户拖拽变形 | 第三方声明 API |
|---|---|---|---|---|
| 华为 | ArkTS 服务卡片(Form Kit)/元服务万能卡片,**NEXT 不兼容 APK** | 微/小/中/大 + 圆形;**上架必须含 2×2** | ❌ 加桌时选规格,无连续拖拽 | 卡片侧有;图标变形无 |
| 荣耀 | 标准 AppWidget + meta-data `com.hihonor.widget.type=honorcard` | 未公开限定 | ❌ 未找到公开资料 | ❌ |
| **OPPO** | 双轨: Seedling/UPK(JS+OML) 独立 + 标准 AppWidget 可上 | Seedling 1×2/2×2/2×4/4×4(+widget_1×1) | ✅ **图标拖成 1×2/2×1/2×2** +文件夹变形+卡片堆叠 | ❌ 变形是系统行为,无声明 API |
| **vivo** | 标准 AppWidget + 3 个 meta-data(原子组件) | **2×2 / 4×2 / 4×4**(原文"未来仅这三个尺寸") | ✅ 华容网格拖拽变形 | ✅ 组件即 AppWidget 声明 |
| 小米 | 标准 AppWidget 超集(MIUI 小部件)+ 审核制 | 2×2 / 4×2 / 4×4 | ❌ 三档切换,无任意拖拽 | ✅ `miuiWidget=true` 等 meta |
| 魅族 | 仅标准 AppWidget | 无官方档位 | ❌ 无公开资料 | ❌ |

**"图标拖长"(Sleepy 核心关注)结论**: 只有 **OPPO** 有官方记载的图标尺寸变形(1×2/2×1/2×2,**1×3 无官方档位**),且属系统行为、无第三方声明 API;**vivo** 是组件级拖拽变形。详见 [oppo.md](./oppo.md) / [vivo.md](./vivo.md)。

## 文件索引

| 厂商 | 文件 |
|---|---|
| 华为 | [huawei.md](./huawei.md) |
| 荣耀 | [honor.md](./honor.md) |
| OPPO | [oppo.md](./oppo.md) |
| vivo | [vivo.md](./vivo.md) |
| 小米 | [xiaomi.md](./xiaomi.md) |
| 魅族 | [meizu.md](./meizu.md) |

## 缺口账本(本域)
- OPPO: 图标变形能否由第三方声明额外档位(如 1×3)—— 未验证推断"系统按 AppWidget 尺寸档自动赋予",**建议开放平台工单确认**。
- 华为: 连续拖拽变形无资料;荣耀/魅族: 拖拽变形无资料。
- 小米: MIUI 大图标(MIUI 14)无第三方适配文档;"超级小卡"无官方资料。
