# 荣耀 · 长按菜单

> 证据等级: A(否定性结论)—— developer.honor.com 文档树实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/honor.md) · [桌面形态](../desktop-morph/honor.md) · [设计规范](../oem-design-specs/honor.md) · [开源](../oem-open-source/honor.md)

## 结论: 未找到公开资料
- 开发者平台无"长按菜单第三方扩展"任何文档
- YOYO 建议卡片 / 智慧服务卡片入口均为**系统侧生成**,第三方无法注入菜单项
- 第三方唯一可自定义项 = **标准 Android shortcuts**(AOSP 行为)

## 相关但非菜单
- 安卓应用卡片入卡片库靠 meta-data `com.hihonor.widget.type=honorcard`(见 [桌面形态](../desktop-morph/honor.md)),与长按菜单无关
- 任意门(跨应用拖拽)靠数据捐赠实体词 + deeplink(见 [桌面形态](../desktop-morph/honor.md))

## 缺口
- 是否存在未公开菜单注入通道: 未找到;Gitee/XDA 亦无系统性逆向。
