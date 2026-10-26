# vivo · 桌面形态 —— 原子组件(标准 AppWidget + 3 meta-data)

> 证据等级: A —— dev.vivo.com.cn 官方文档实测(2026-09-17)。
> 其他能力: [负一屏](../negative-one-screen/vivo.md) · [长按菜单](../long-press-menu/vivo.md) · [设计规范](../oem-design-specs/vivo.md) · [开源](../oem-open-source/vivo.md)

## 原子组件 = 标准 AppWidget + 3 个 meta-data
- 原文: "方式和原生 AppWidget 相同,只需增加 meta-data 标识"
- 三件套: `vivo_widget=true`、`vivoWidgetVersion`(正整数、只升不降)、`vivo.widget.description`
- 文档: `https://dev.vivo.com.cn/documentCenter/doc/845`(2024-11-15)

## 尺寸档位
- **2×2 / 4×2 / 4×4**(targetCellWidth/Height + minWidth 120 / 280 dp)
- 《视觉规范》(2025-11-03,OriginOS 6 时期)原文: "**未来新的组件仅支持 2×2、4×2、4×4 三个尺寸**"
- 圆角 20dp 桌面统一裁切、安全边距 14dp
- 文档: `https://dev.vivo.com.cn/documentCenter/doc/835`

## 用户变形: 华容网格拖拽
- 用户拖拽组件变形(社区实证: `https://bbs.vivo.com.cn/newbbs/thread/38782885`)
- 图标拉长成条 = 原子组件考据: `https://wenku.baidu.com/view/176c25f5f90a79563c1ec5da50e2524de518d0af.html`

## 组件长按气泡(菜单扩展)
- 见 [长按菜单](../long-press-menu/vivo.md)

## 限制
- 刷新间隔 ≥12 小时
- 动效仅补间动画(layoutAnimation, 200-800 ms)
- 深色模式双套资源
- **接入前提**: 应用已上架 vivo 商店 + 邮件申请,APK 整包上传审核 7-10 工作日,组件版本号改动须重新审核
- 细则: `https://dev.vivo.com.cn/documentCenter/doc/850`
