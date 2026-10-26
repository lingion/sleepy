# 小米 · 长按菜单

> 证据等级: A(否定性结论)—— dev.mi.com 桌面适配目录逐页实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/xiaomi.md) · [桌面形态](../desktop-morph/xiaomi.md) · [设计规范](../oem-design-specs/xiaomi.md) · [开源](../oem-open-source/xiaomi.md)

## 结论: 无 OEM 长按菜单扩展文档
- dev.mi.com 桌面适配目录(pId=1511)仅含: 图标上传规范 / 角标适配(pId=1510)/ 角标 Q&A
- 长按菜单项(应用信息 / 卸载 / 分享 / 小组件子菜单 / 分屏)均为 `com.miui.home` 系统内置
- 第三方唯一可自定义项 = **标准 Android shortcuts**

## 相关
- HyperOS 2 长按交互改进(应用长按弹小组件预览等)仅媒体/社区描述,无开发者文档;知乎实测: `https://zhuanlan.zhihu.com/p/6573730489`
- 逆向佐证: `qqlittleice/MiuiHome`(Xposed Hook)→ [开源](../oem-open-source/xiaomi.md)
- **角标**是图标层面唯一官方开放项: MIUI 12+ 用 `Notification.number`(pId=1510)

## 缺口
- 小组件子菜单 / 分屏项能否由第三方影响: 无公开资料。
