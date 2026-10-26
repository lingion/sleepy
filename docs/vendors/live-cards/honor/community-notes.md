[evidence=B] https://developer.honor.com/cn/doc/guides/101538 · https://blog.csdn.net/HONOR_Developer/article/details/126829344 · 抓取时间 2026-09-17/18

# 荣耀灵动胶囊社区情报与缺口

> 官方文档正文 JS 渲染抓不全,以下是目录级事实 + 社区信息 + 实机可验证项。

## 已确认事实(官方目录级)

- 101538 设计指南含「灵动胶囊规范」独立章节,与 AOD/动态通知并列
- 101453/101454 走 Suggestions Kit 五步接入(见 capsule-api.md)
- MagicOS 9.0.0+ 支持云调试

## 社区信息(CSDN 荣耀官方号)

- 官方运营 CSDN 账号 HONOR_Developer,持续发安卓卡片接入教程
- 有百亿曝光扶持计划,卡面质量为筛选条件

## 真机验证清单(Sleepy 工程侧)

| # | 验证项 | 方法 |
|---|---|---|
| 1 | 未上架包能否 init Suggestions Kit 成功 | 侧载+平台注册同包名实测 |
| 2 | 课程事件上报后胶囊是否出现 | 打课表事件,观察状态栏 |
| 3 | 胶囊展开/压缩两态尺寸 | 截屏量像素,补设计文档 |
| 4 | AOD 是否展示课程 | 熄屏观察 |
| 5 | 纯通知路径(不接 Kit)在 MagicOS 的样式 | 发 Notification 对比官方卡面 |
