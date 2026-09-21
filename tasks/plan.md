# 调休映射·放假日视角日期树 UI — 实施计划

基线: 分支 `feat/holiday-makeup-mapping` @ 76eb9093 (worktree /tmp/sleepy-makeup-wt), 上一轮 6 commits 已合入旧 `MakeupDay(sourceDayOfWeek)` 模型。

## 架构决策

1. **存储模型完全替换**: 新 `HolidayTransferEntry(sourceDate, targetDate, segmentId)` 三字段, 按 `(tableId, sourceDate)` 单值语义(一表一放假日最多一条映射)。旧 `MakeupDay(date, sourceDayOfWeek)` 字段全删, prefs key 名换 `holiday_transfer_<tableId>`。分支未发布, 无用户存量, 不写迁移。
2. **互斥按目标日(`targetDate`)层做, 不做 UI disable**: `AppPrefs.setHolidayTransfer(tableId, sourceDate, targetDate, segmentId)` 写入前先同事务移除该表所有 `targetDate` 相同的 entry, 再写入新 entry。下拉里候选全集平铺, UI 不做归属过滤/禁用/分组, “后选覆盖前选”的反馈由源日行的视觉回灰体现。
3. **渲染期 `effectiveDayOfWeek` 公式变**: 旧 = `resolveCourseDay(date, mappings)` (按 sourceDate 命中, 取星期几)。新 = `transferFor(date).targetDate.dayOfWeek.value`, 命中即取 targetDate 的星期几, 未命中走自然星期。`MakeupCourseDayHelper` 重写为 `HolidayTransferOps.effectiveDayOfWeek(tableId, date, transfers, segmentByDate)`, 11 取课点(ScheduleScreen 网格 / TodayScreen / 8 widget 变体 / 闹钟 / 摘要)全替换。
4. **节卡 = HolidayRange 节点**: 复用现有 `mergeSegments` 产出的 `active public_holiday` 段, 每段一张卡, 卡内逐放假日一行。段点击行为**取消**——段不再编辑弹窗(原⑦段编辑移到一个独立紧凑的“可编辑假期段”折叠卡, 默认收起)。这是简化: 用户主要诉求是调休, 段编辑是次要路径, 折叠到角落。
5. **课表切换走已有 `mainVm.state.value.tables` + `selectTable(id)`**, 卡头下拉直接绑。VM 切换表时连带 reload 该表 transfers + 触发 widget/闹钟刷新(沿用 `refreshMakeup` 改名 `refreshTransfer`)。
6. **shouldGrey 联动**: 设了映射的 sourceDate 永不灰显(有课不在灰列)。`HolidayManager.shouldGrey` 增入 `transfers: List<HolidayTransferEntry>`, 命中映射即返回 false。`sourceKey` 不参与此判定(只在段编辑生效)。
7. **失效映射**: 加载时若 entry.sourceDate 不在当年 `public_holiday` 集合内, 渲染为灰行 + “对应放假日已不存在(可能学校另行通知), 点按清除”, 不删数据, 用户手动清。

## 任务序列(依赖序)

```
T1 数据层(新模型 + 编解码 + 互斥写) → T2 解析层(transferFor / effectiveDayOfWeek / isOrphanFor)
T2 → T4 渲染层(ScheduleScreen 网格 / TodayScreen)
T2 → T5 widget 11 取课点全替换
T2 → T6 闹钟 + 摘要
T4 + T5 + T6 → T3 VM (refreshTransfer + makeupDays→transfers + courseDayFor→transferDayFor)
T1 + T3 → T7 UI(节卡 + 右格下拉 + 失效行 + 课表下拉 + 段编辑折叠)
T7 → T8 删旧字段 + 改 strings + 改 i18n 全套 + feature-baseline 同步
T8 → T9 测试全量 + lint + 契约锁补全
```

## 任务列表

见 `tasks/todo.md`。