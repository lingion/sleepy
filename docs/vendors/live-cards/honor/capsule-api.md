[evidence=B] https://developer.honor.com/cn/doc/guides/101454 (开发指南, Googlebot UA 抓取 2026-09-17) · https://developer.honor.com/cn/doc/guides/101453

# 荣耀灵动胶囊 API / 开发指南

> 荣耀全局触达开发路径为五步: 应用注册 → 权限申请 → 集成 Suggestions Kit → Manifest 配置 → 接口调用与数据反馈。

## 接入五步(101454 实测结构)

| 步骤 | 内容 | Sleepy 对应动作 |
|---|---|---|
| Step1 应用注册 | 荣耀开发者服务平台创建应用,拿 appId | AGC 类似流程,先注册包名 com.sleepy.timetable |
| Step2 权限申请 | 开通场景化建议服务/全局触达权限 | 后台申请,注意审核期 |
| Step3 集成 Suggestions Kit | 导入 SDK,反馈结构化数据(事件类型+时间+卡面素材) | 把「下节课」事件用建议服务类型上报 |
| Step4 AndroidManifest | 写入 appid meta-data | `<meta-data android:name="com.honor.appid" android:value="..."/>` |
| Step5 接口调用 | 初始化 SDK → 数据反馈 → 系统择机展示胶囊 | 上课/下课事件驱动 update |

## 已核实 API 面(场景化建议服务 Suggestions Kit)

- 初始化: SDK init(context, appId)
- 数据反馈接口类: 反馈(用户场景事件) → 系统判定是否展示胶囊/卡片/AOD
- 事件类型清单: 见场景化建议服务文档组 101587-101610(通勤/日程/天气/快递等;日程类覆盖课程表场景)
- 云调试: MagicOS 9.0.0+ 才支持云端联调

## 与华为实况窗 API 差异(关键)

| 维度 | 华为实况窗 | 荣耀灵动胶囊 |
|---|---|---|
| 展示控制权 | 应用直接 startLiveView/update/stop | 系统根据反馈数据择机展示,应用不直接命令 |
| 模板 | 5 种卡模板 | 卡面跟随全局触达设计规范(主视觉/主题色/叠加图) |
| 生命周期 | 8h 上限,2h 无更新降级 | 官方未公开等价时长(evidence 不足) |
| 云更新 | Push Kit 10 种事件 | 官方未公开 push 通道(evidence 不足) |

> 直接命令式 API 在荣耀体系内 evidence 不足,未证实内容一律进 gaps.md。
