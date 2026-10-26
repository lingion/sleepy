# 上海立信会计金融学院跨仓验证

- 用户原话：把 4 所学校按照 SOP 流程全部做完，然后并且最终交叉验证。
- 学校：上海立信会计金融学院（LIXIN）
- 类型：新增学校适配；经典 EAMS 时间 API 变体
- 入口：`https://lxjw.lixin.edu.cn/edu/lesson/std/timetable!courseTable.action`
- 采集包 SHA-256：`4781729c5c916e9c5ad51696ef5354984f65d99052eaa153ae8c2dc44e31bf7c`
- 证据：真实页面包含 `table0.newActivity(...)` 与 `table0.addActivityByTime(activity, day, startTime, endTime)`；Beangle 0.2.0。
- 当前代码结论：需扩展 `JwClassicEamsParser`，支持活动对象引用、绝对分钟时间转节次、数字周次位图。
- 证据边界：fixture 仅保留脱敏 JS 结构，不提交任何 Cookie、身份信息或页面原始敏感字段。
