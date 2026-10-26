# 荣耀 · 桌面形态

> 证据等级: A —— developer.honor.com 官方文档实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/honor.md) · [长按菜单](../long-press-menu/honor.md) · [设计规范](../oem-design-specs/honor.md) · [开源](../oem-open-source/honor.md)

## 安卓应用卡片(标准 AppWidget + 1 个 meta-data)
- meta-data: `com.hihonor.widget.type=honorcard` 即入荣耀卡片库
- 《安卓应用卡片接入流程》V14(2024-04-10): `https://developer.honor.com/cn/doc/guides/100169`
- 官方 CSDN 教程(2022-10): `https://blog.csdn.net/HONOR_Developer/article/details/126829344`
- 配套: 快应用 JS 卡片 g/100145、模板卡片 g/101182

## 任意门(跨应用拖拽)
- 三件事: **数据捐赠实体词**(`magicportal@honor.com`)+ deeplink / activity 接口 + 悬浮窗适配
- 业务介绍 / 开发指南(均 2024-10-23): `https://developer.honor.com/cn/doc/guides/101459` / `101465`
- 拖拽对象 = 系统侧,第三方只暴露捐赠词与目标接口

## 灵动胶囊(实时形态)
- 媒体类免开发(MediaSession 自动渲染),《开发指南》2026-07-08: `https://developer.honor.com/cn/doc/guides/101771`
- → 实时卡片域详见 [live-cards](../live-cards/INDEX.md)

## 锁屏小组件
- `https://developer.honor.com/cn/doc/guides/20036`

## 缺口
- 拖拽连续变形: **未找到公开资料**。
- 卡片尺寸档位: 未公开限定(官方仅说"卡片尺寸由 widget provider 自定,系统按 launcher 网格约束")。
