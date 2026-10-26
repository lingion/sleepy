# 小米 · 负一屏

> 证据等级: A —— dev.mi.com 澎湃OS开发者平台逐页实测(JS 渲染站已实际渲染读正文,2026-09-17)。
> 其他能力: [长按菜单](../long-press-menu/xiaomi.md) · [桌面形态](../desktop-morph/xiaomi.md) · [设计规范](../oem-design-specs/xiaomi.md) · [开源](../oem-open-source/xiaomi.md)

## 体系与官方入口
- 官方入口「负一屏」: `https://dev.mi.com/xiaomihyperos/appvault`
- 官方定义: "千万级日活流量覆盖的系统级服务分发入口,基于小部件的多样化展现形式"
- **两种接入模式**:
  1. **开发者自主接入** —— 自行研发小部件 → 小部件平台上传审核 → 用户自行添加
  2. **非标资源合作** —— 白名单式深度合作
- 《负一屏服务介绍》(2024-09-25): `https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1923` —— 智能推荐/服务直达由小爱端侧 AI 驱动,覆盖 20+ 场景;第三方只能以小部件形态进入

## 小爱建议 = 付费商业资源,无免费 SDK
- 《负一屏商业化合作引导》(2024-10-18): `https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1926`
- 推荐位(小爱建议版 2×2 卡片 widget/maml / 固定 icon / 快捷功能 icon)= **CPC/CPM 采买**,客户分级年框 **10W~1000W/年**
- 4×2 强插卡片为 maml 模板卡片
- 小爱开放平台(`https://developers.xiaoai.mi.com/`)仅语音技能 / IoT,**不含负一屏推荐位**

## 载体写法
- MIUI 小部件技术规范(独立进程 `:widgetProvider`、`miuiWidget=true`、尺寸 2×2/4×2/4×4、邮件报备 + widget.xiaomi.com 审核)→ 详见 [桌面形态](../desktop-morph/xiaomi.md)
- 纯原生 AppWidget 无需小米审核,但需用户手动添加,不自动进负一屏

## 缺口
- 自主接入小部件在负一屏的推荐露出条件无量化标准。
- "超级小卡"无官方资料(疑与其他厂商混淆)。
