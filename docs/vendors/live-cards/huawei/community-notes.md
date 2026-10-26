# 华为 · 实况窗社区实战要点

> 证据等级: B —— 社区博客二次核实;原始官方文档 `liveview-create-locally.md` + `liveview-design-formula.md` + `liveview-faq-*.md`;抓取时间 2026-09-18

## 核心实战要点

### 本地创建 vs 云端推送

| 方式 | 说明 | 适用 |
|---|---|---|
| 本地创建 | `liveViewManager.startLiveView()` | 应用在前台，用户实际使用并产生服务合约 |
| 云端推送 | Push Kit 推送 | 后台更新 / 用户离开应用后继续更新 |

**推荐**：本地创建 → Push Kit 更新/结束（不依赖应用进程）

### 实况窗开关

用户需手动开启：设置 → 应用和元服务 → 应用名 → 实况窗
创建前必须校验：`liveViewManager.isLiveViewEnabled()`

### 卡片模板选择

5 种扩展区模板（按 layoutType）：

| layoutType | 枚举值 | 适用场景 |
|---|---|---|
| LAYOUT_TYPE_DEFAULT | -1 | 默认通用 |
| LAYOUT_TYPE_PROGRESS | 3 | 打车/配送进度 |
| LAYOUT_TYPE_PICKUP | 4 | 取餐码展示 |
| LAYOUT_TYPE_FLIGHT | 5 | 航班信息 |
| LAYOUT_TYPE_SCORE | 7 | 赛事比分 |
| LAYOUT_TYPE_CUSTOM | 100 | 自定义布局 |

### 计时器场景

仅支持**端侧创建与更新**（TIMER / SUBSCRIBE_TIMER）
倒计时结束监听：创建时记录开始时间 + 计时器时长，循环判断是否到达

### 地理围栏触发（6.1.0+/23）

支持提前注册地理围栏条件触发创建或结束实况窗
适用场景：打卡、快递取件

### 通知频率限制（打车场景示例）

- 每设备每 **5 分钟最多更新 30 次**
- 每小时最多更新 **180 次**

### 提醒方式

- **强提醒**：铃声 + 振动（`isMute: false`）
- **胶囊动态效果**：仅云更新支持（`remind` 参数）

### 消失机制示例

| 场景 | 消失时机 |
|---|---|
| 打车到达目的地 | 待支付状态展示 5 分钟；支付成功后立即消失 |
| 外卖已送达 | 待取餐展示 5 分钟；当面送达立即消失 |
| 取餐完成 | 待取餐展示 15 分钟；用户取餐完成立即消失 |
| 已过号 | 已过号状态展示 5 分钟 |

### 预约场景时机

| 场景 | 创建时机 |
|---|---|
| 打车预约出行 | 计划出发前 20 分钟通过云创建实况窗 |
| 银行排队预约 | 办理前 30 分钟开始展示 |

### 错误码

| 错误码 | 含义 | 处理 |
|---|---|---|
| 1003500001 | 系统内部错误 | 重试或提单 |
| 1003500002 | 序列化/反序列化失败 | 重试 |
| 1003500003 | 连接服务失败 | 重试 |
| 1003500004 | 实况窗开关关闭 | 提示用户开启 |

### SampleCode

`https://gitcode.com/HarmonyOS_Samples/live-view-kit_-sample-code_-clientdemo_-arkts`
ArkTS 端侧示例，覆盖 13 个场景

## 缺口

- Push Kit REST API 对 TIMER/SCORE/WORKOUT/NAVIGATION/CHECK_IN/PROGRESS 场景不支持
- 云侧仅支持 FLIGHT/TAXI/TRAIN/DELIVERY/QUEUE/RENT/EXPRESS/CHECK_IN/TRADE/SUBSCRIBE_TIMER 场景
- 进度百分比（`extensionData.progress`）云侧不支持
- 连续服务按钮（`serviceButtons`）云侧不支持
