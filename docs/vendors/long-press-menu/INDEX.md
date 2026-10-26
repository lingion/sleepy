# 长按图标菜单 (Long-Press Menu) 六厂商扩展能力速查

> 目录目的: 只回答一件事 —— **长按 app 图标弹出的菜单,第三方到底能改什么**。标准 shortcuts 之外有无 OEM 官方扩展位、数量上限、声明方式。
> 抓取时间: 2026-09-17 (3 research agent 并行实测官方开发者平台)
> 抓取原则: 厂商官方页优先 → 社区二次核实;每文件首行标注证据等级;查无公开资料的显式记录,不编造 URL。
> 相邻域: [负一屏](../negative-one-screen/INDEX.md) · [桌面形态与变形](../desktop-morph/INDEX.md) · [设计规范](../oem-design-specs/INDEX.md) · [开源与社区](../oem-open-source/INDEX.md)

## 一页矩阵

| 厂商 | 图标长按第三方可定义项 | OEM 官方扩展位 | 数量上限 |
|---|---|---|---|
| 华为 | 标准 shortcuts +「服务卡片」按钮 | ✅ **服务卡片长按菜单**(应用自定义:编辑/排序/更改内容) | shortcuts 上限未公开 |
| 荣耀 | 标准 shortcuts | ❌ 未找到公开资料 | — |
| OPPO | 标准 shortcuts(另有长按拖角标=变形,非菜单) | ❌ 未找到公开资料 | — |
| **vivo** | 标准 shortcuts(≤4) | ✅ **组件长按气泡** `shortcutinfo0~3`(六家唯一组件级菜单扩展) | 图标 4 + 每组件 4 |
| 小米 | 标准 shortcuts | ❌ 菜单项全为 `com.miui.home` 内置 | — |
| 魅族 | 标准 shortcuts | ❌ 无公开文档 | — |

**结论**: 六家中只有 **vivo(图标 + 组件双处)** 与 **华为(卡片长按区)** 给了第三方官方扩展位;其余一律只能靠标准 Android shortcuts。

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
- 华为/荣耀: shortcuts 数量上限与菜单分组规则无公开资料。
- OPPO/小米/魅族: 是否存在未公开的 OEM 菜单注入通道 —— 只能靠逆向或工单确认。
