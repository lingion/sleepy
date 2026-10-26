# vivo · 负一屏 —— 六家中唯一全自助

> 证据等级: A —— dev.vivo.com.cn 官方文档实测(2026-09-17),SPA 破口沿用 `dev.vivo.com.cn/webapi/doc/info?id=`。
> 其他能力: [长按菜单](../long-press-menu/vivo.md) · [桌面形态](../desktop-morph/vivo.md) · [设计规范](../oem-design-specs/vivo.md) · [开源](../oem-open-source/vivo.md)

## 体系
- 负一屏 = "智慧桌面";**原子组件平台 = 唯一公开入口**,官方定位"应用信息零层级展示在桌面和负一屏"
- 原子组件"基于 Android 小部件并结合 OriginOS 桌面华容网格",展示于 桌面 / 智慧桌面 / 组件库(2024-11-15 更新): `https://dev.vivo.com.cn/documentCenter/doc/840`

## 自助流程
1. 开发者认证(邮件)
2. 前提: **应用已上架 vivo 商店** + 邮件申请
3. `https://origin-card.vivo.com.cn` 后台上传 APK 整包 → 平台审核 7-10 工作日 → 发布
- 入口: `https://dev.vivo.com.cn/promote/atomicComponent` · 接入细则: `https://dev.vivo.com.cn/documentCenter/doc/850`
- 组件技术写法(3 个 meta-data)见 [桌面形态](../desktop-morph/vivo.md)

## 相邻但不同门槛: 原子通知(实况类)
- **白名单制**(2025-09-01 更新): 须已上架 vivo 商店,邮件 `oosyztz@vivo.com`,7-10 工作日,再经需求定义 / UI 双方评审 / 联调 / 分机型放量;场景限外卖配送、打车进度、演出提醒等**履约类**: `https://dev.vivo.com.cn/documentCenter/doc/894`
- OriginOS 6 新增**原子岛**(2025-10-16): `https://dev.vivo.com.cn/documentCenter/doc/927`
- → 实时卡片域详见 [live-cards/vivo/atomic-notification.md](../live-cards/vivo/atomic-notification.md)

## 相关新协议: IntentsUI
- (2026-08-28) 跨应用"原子化 UI"协议,节点式声明 UI,触点 = 全搜 / 控制中心 / 锁屏,配 vivo 意图共享 SDK: `https://dev.vivo.com.cn/documentCenter/doc/1107`

## 缺口
- 原子组件在负一屏的排序/曝光权重无公开资料。
