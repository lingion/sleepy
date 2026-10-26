# 华为 · 实况窗缺口账本

> 证据等级: A/B/C 分类说明;抓取时间 2026-09-18

## 缺口清单

| # | 缺口 | 证据等级 | 建议验证方式 |
|---|---|---|---|
| 1 | **三方 APK（非 HarmonyOS 原生）无接入路径** | A | 官方明确仅 HarmonyOS 5+ ArkTS |
| 2 | **商务定价/付费机制无公开资料** | C | 需商务邮件实测 |
| 3 | **Push Kit 云侧 TIMER/SCORE/WORKOUT/NAVIGATION/CHECK_IN/PROGRESS 不支持** | A | `liveview-api-map.md` 明确 |
| 4 | **云侧仅支持 10 个 EVENT**：FLIGHT/TAXI/TRAIN/DELIVERY/QUEUE/RENT/EXPRESS/CHECK_IN/TRADE/SUBSCRIBE_TIMER | A | API map 明确 |
| 5 | **进度百分比（extensionData.progress）云侧不支持** | A | API map 明确 |
| 6 | **连续服务按钮云侧不支持** | A | API map 明确 |
| 7 | **应用月活 1000 门槛无公开核实渠道** | B | 需社区实测 |
| 8 | **小爱同学/YOYO/小艺跨端联动无公开文档** | C | 需代码实证 |
| 9 | **正式权限申请驳回率/常见驳回原因无公开资料** | C | 需社区收集 |
| 10 | **地理围栏触发实况窗的精度/耗电策略无公开资料** | B | 需实测 |
| 11 | **地区限制港澳台无例外条款** | A | 官方明确 |

## 与课表类业务的相关性评估

Sleepy 课表 app 适配华为实况窗的可行性评估：

| 维度 | 评估 | 说明 |
|---|---|---|
| 课程提醒是否合规 | ⚠️ 存疑 | TIMER 场景仅限"工具类应用"，课表属于什么？需实测 |
| 上下课打卡 CHECK_IN | ✅ 可能 | 但限制"上下班打卡"，课表打卡是否算？ |
| 倒计时 SUBSCRIBE_TIMER | ⚠️ 可能 | 仅限"演唱会售票/车票候补/全球赛事"，课表不在列 |
| 接入门槛 | ❌ 高 | 需上架 AppGallery + 月活 1000 + 商务审核 |
| 三方 APK | ❌ 不可能 | HarmonyOS 原生专用 |
