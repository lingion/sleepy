[evidence=A] https://dev.vivo.com.cn/documentCenter/doc/894 (原子通知接入指导·全文) · https://dev.vivo.com.cn/documentCenter/doc/952 (小V建议接入指导) · https://dev.vivo.com.cn/documentCenter/doc/916 (智能体专区) · 回执码 32008 见 doc 896(技术规范) 抓取时间 2026-09-18 (SPA 破口 `webapi/doc/info?id=`)

# vivo · access-gate.md

> 证据等级: A(vivo developer 官方原文落盘; doc 894 全文已抓)。结论先行: vivo 原子通知是**邮件申请 + 白名单 + scene 分配制**, 明面周期 7-10 个工作日, 暗面是"双方评审需求/UI → 逐步放量"的共建模式; 无自助 API 开通入口。

## 一、原子通知准入六步流程 (doc 894 §一 原文)

| 步骤 | 内容 | 周期 |
|---|---|---|
| 前置 | **应用已在 vivo 应用商店上架** | — |
| 1 | 准备接入资料, 发邮件至 **oosyztz@vivo.com** 申请接入权限, 等 vivo 原子通知团队审核准入 | **7-10 个工作日** |
| 2 | 获准入后, 完成**需求定义**及 **UI 设计**(红色强调, 官方标注) | 双方 |
| 3 | 双方评审需求定义及 UI 设计内容, **定稿通过** | 双方 |
| 4 | vivo 开通相关权限 → 开发者开发 → **双方联调与测试** | 双方 |
| 5 | 上线 —— 官方注: "vivo 功能上线为**机型逐步放量**" | 渐进 |
| 6 | 定期体验巡检, 重大更新相互公告 | 长期 |

## 二、申请邮件模板 (doc 894 §二 原文, "缺一无法通过准入")

> 固定开场白: "我方已阅读原子通知产品设计规范与技术规范,准备按照要求适配原子通知,希望进一步沟通后续流程和相关规范。"

| # | 字段 | 备注 |
|---|---|---|
| 1 | 应用名称 | |
| 2 | 应用包名 | |
| 3 | 开发者联系电话/微信 | |
| 4 | 接入服务场景 | 官方示例: "如外卖配送、打车进度、演出提醒等" |
| 5 | 接入需求定义文档 | **xlsx 模板直链**: `https://swsdl.vivo.com.cn/appstore/developer/uploadFile/20250901/ib9x04/原子通知接入需求定义文档示例.xlsx` |
| 6 | 服务示意图 | |

## 三、隐性门槛(明文之外)

| 门槛 | 证据 | 等级 |
|---|---|---|
| **白名单/开通制** | VPush 回执码 **32008 = "当前应用或场景未开通原子通知权限"**(doc 896); 即云端更新对未开通应用直接拒绝 | A |
| scene 由 vivo 分配 | 反射接口 `getSceneStatus(pkg, scene)` 按包名+场景查询; 文档场景值(MOVIE/TAXI/TAKEOUT/TRAIN/FLIGHT/DELIEVERY/NAVIGATION/HEALTH_REGISTER/CAR_STATE/METTING)均需 vivo 侧注册生效 | A(字段) + C(注册表反编译核对) |
| 系统内置白名单包 | 反编译: `com.vivo.assistant` / `com.eg.android.AlipayGphone`(支付宝) / `com.vivo.pushservice`; 校验失败码 2208 NOTINLIST | C |
| UI/需求双方定稿 | 流程第 2-3 步: 不是"接 API"而是"共建方案", 开发者无最终裁量权 | A |
| 机型逐步放量 | 上线后仍可能仅部分机型生效, 需按回执 31006/32003 做降级 | A |

## 四、vivo 三条"卡片类"通道门槛横评

| 通道 | 申请入口 | 前置 | 周期 | 出卡控制权 |
|---|---|---|---|---|
| **原子通知**(实时胶囊/岛) | oosyztz@vivo.com | 上架 vivo 商店 | 7-10 工作日(准入) + 双方评审 | 开发者驱动(本地 Bundle / VPush) |
| **小V建议**(智慧建议卡) | aiadviser@vivo.com | 含应用则须上架 | 无公开数字, 商务答复 | **vivo 推理引擎**(开发者只供数据) |
| **蓝心智能体平台**(AI 入口) | agents.vivo.com(扣子一键分发) | 智能体审核 | 约 5 工作日 | 平台分发 |

## 五、Sleepy 落点: 申请策略

| 判断 | 说明 |
|---|---|
| 场景申报口径 | 建议申报"课程日程提醒"(类比官方示例"演出提醒"——同为有明确开始/结束时间的日程型场景); doc 895 适用三条件(用户主动预期/特定时段关注/明确起止)课表均满足 |
| 硬伤预警 | 8h 单活动上限 + 2h 无更新清除 → "全天常驻课表卡"不可行, 只能按节次/半天拆活动(详见 _cross-vendor/china-live-cards-comparison.md §课表评估) |
| 双通道并行 | 原子通知(oosyztz@)+ 小V建议(aiadviser@)分属两个团队, 可同期发信降低总等待; 两者数据可复用(displays 掩码 0x1000000) |
| 降级链 | 未准入/未放量机型 → showNotify 回落普通通知(须先完成渠道备案, 见 [notification-behavior.md](./notification-behavior.md) doc 930) |
| 邮件六要素 | 名称/包名/联系方式/场景/**xlsx 需求定义**/示意图 —— xlsx 直链已存, 按节次-教室-教师字段填充即可 |
