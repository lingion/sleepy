# 华为 · 实况窗 Live View Kit

> 证据等级: A —— GitHub/GitCode 镜像 `liasica/harmonyos-skills` 落盘;原始 URL `developer.huawei.com/consumer/cn/doc/harmonyos-guides/liveview-introduction`;抓取时间 2026-09-18

## 体系定位

华为 Live View Kit（实况窗服务）支持应用将订单或服务的实时状态信息变化在设备的关键界面展示，并对展示信息的生命周期、用户界面 UI 效果进行管理。

**核心特点（三性）**：
- **时段性**：有明确开始和结束，非单点提醒（例：打车/外卖 vs 天气提示）
- **时效性**：特定时间段内对用户有价值（例：航班出发前提示，非提前两天）
- **变化性**：内容需动态更新

**展示位置**：锁屏 / 通知中心 / 状态栏
**展示形态**：胶囊态 + 卡片态（两种独立形态）

## 适用设备与系统

| 维度 | 值 |
|---|---|
| 操作系统 | HarmonyOS 5 及以上（NEXT / 鸿蒙 5） |
| 设备类型 | Phone + Tablet |
| 地区限制 | 仅中国境内（港澳台除外） |
| 硬件依赖 | 完全解耦，与设备硬件无关 |

## 支持场景（EVENT 类型）

| EVENT | 场景 | 不适用 |
|---|---|---|
| TAXI | 网约车/出租车/拼车/顺风车 | — |
| DELIVERY | 外卖/生鲜/同城配送 | 快递物流运输 |
| FLIGHT | 航班出行/主动关注航班 | 模拟飞行 |
| TRAIN | 高铁/火车 | — |
| QUEUE | 办事大厅/医院/银行/餐饮排队 | 无进度排队/在线客服/文件下载 |
| PICK_UP | 餐饮线下取餐/商品取件 | 模拟取餐 |
| SCORE | 游戏/体育赛事比分 | 主播 PK/象棋游戏 |
| RENT | 共享单车/充电宝/停车/快充 | 慢充/家用家电 |
| TIMER | 专注时刻/番茄时钟/抢票倒计时 | 课程提醒/待办/录音/AI 对话 |
| SUBSCRIBE_TIMER | 演唱会售票/车票候补/全球官方赛事 | 电商直播/日常自媒体/优惠券抢购 |
| WORKOUT | 户外/室内跑步/骑行 | — |
| NAVIGATION | 步行/骑行/车辆导航 | 虚拟导航/游戏导航 |
| CHECK_IN | 上下班打卡 | 景区打卡/课程打卡/打卡挑战 |
| EXPRESS | 快递取件 | 未接入地理围栏的快递 |
| PROGRESS | 文件上传下载/资源导入导出 | 后台自动上传下载 |
| TRADE | 股票/基金交易买五至卖五区间 | — |

## 生命周期限制

- **最长 8 小时**：超过 8h 系统判定实况窗结束
- **超过 2 小时未更新**：状态栏胶囊 + 锁屏胶囊隐藏，仅保留通知中心
- **超过 4 小时未更新**：从所有入口清除

## 卡片模板（五种）

| 模板 | layoutType | 适用场景 |
|---|---|---|
| 进度可视化 | LAYOUT_TYPE_PROGRESS | 打车/外卖/配送 |
| 取餐模板 | LAYOUT_TYPE_PICKUP | 取餐 |
| 航班模板 | LAYOUT_TYPE_FLIGHT | 航班 |
| 赛事比分 | LAYOUT_TYPE_SCORE | 比分 |
| 自定义模板 | LAYOUT_TYPE_CUSTOM | 通用 |

卡片分区：固定区（核心信息）+ 辅助区（次要信息）+ 扩展区（详细信息）

## API 总览

| API | 说明 |
|---|---|
| `liveViewManager.startLiveView` | 本地创建实况窗 |
| `liveViewManager.updateLiveView` | 本地更新实况窗 |
| `liveViewManager.stopLiveView` | 结束实况窗 |
| `liveViewManager.startLiveViewByTrigger` | 地理围栏触发创建 |
| `liveViewManager.stopLiveViewByTrigger` | 地理围栏触发结束 |

**前提条件**：校验 `liveViewManager.isLiveViewEnabled()` 返回 true（用户开启实况窗开关）

## 接入前提

1. 开通 Push Kit 推送服务
2. 开通 Live View Kit 实况窗服务权益
3. 应用月活 ≥ 1000 且已上架 AppGallery
4. 联调测试通过后提交正式权限申请

## 缺口

- 商务定价机制无公开资料
- 三方 APK（非鸿蒙原生）无法接入
