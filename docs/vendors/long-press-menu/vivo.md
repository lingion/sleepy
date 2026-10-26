# vivo · 长按菜单 —— 六家中开放度最高

> 证据等级: A —— dev.vivo.com.cn 官方文档实测(2026-09-17),SPA 破口 `dev.vivo.com.cn/webapi/doc/info?id=`。
> 其他能力: [负一屏](../negative-one-screen/vivo.md) · [桌面形态](../desktop-morph/vivo.md) · [设计规范](../oem-design-specs/vivo.md) · [开源](../oem-open-source/vivo.md)

## 1. 图标长按快捷方式
- 《桌面图标长按快捷方式接入指导》(2023-01-31,桌面 v9.3.0+ 开放): `https://dev.vivo.com.cn/documentCenter/doc/460`
- 三方自定义 shortcuts **最多 4 个**(静态 / 动态,标准 Android shortcuts)
- 系统固定项 卸载 / 应用信息 / 编辑桌面 **不可自定义**

## 2. 组件长按气泡(六家唯一的组件级 OEM 菜单扩展)
- 原子组件长按弹出气泡快捷菜单,**每组件最多 4 项**
- 声明方式: meta-data `shortcutinfo0` ~ `shortcutinfo3`
- 值格式: `action/包名/类名/extravalue`;标题与图标取自目标 activity 的 `label` / `icon`
- 文档: `https://dev.vivo.com.cn/documentCenter/doc/845`(2024-11-15)
- 前提: 组件已按原子组件规范接入(见 [桌面形态](../desktop-morph/vivo.md))
