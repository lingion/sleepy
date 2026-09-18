# 调休映射·放假日视角日期树 UI — Todo

分支 feat/holiday-makeup-mapping · worktree /tmp/sleepy-makeup-wt · 主仓不动

## Phase 1 数据层 + 解析层

- [ ] T1 新模型 HolidayTransferEntry + 编解码 + 互斥写
  - Accept: `data class HolidayTransferEntry(val sourceDate: LocalDate, val targetDate: LocalDate, val segmentId: String)`;`HolidayTransferOps.encode/decodeList` (纯函数, 坏行跳过); `AppPrefs.getHolidayTransfers(ctx, tableId)` / `setHolidayTransfers`; `AppPrefs.setHolidayTransfer(ctx, tableId, source, target, seg)` 自动按 targetDate 互斥清同表其他条目; `clearHolidayTransfers(ctx, tableId)` 删表清; prefs key `holiday_transfer_<tableId>`
  - Verify: `HolidayTransferOpsTest` 8 用例(编解码坏行/同日期重复/顺序/空表/互斥/越界)全红→绿; `AppPrefsHolidayTransferTest` 5 用例(读写/同事务互斥/clear 全清)全红→绿; 旧 `MakeupDay` 全仓 grep = 0
  - Files: HolidayRange.kt(新增 HolidayTransferOps + HolidayTransferEntry), AppPrefs.kt(替换 setHolidayMakeupDay 等), HolidayTransferOpsTest.kt, AppPrefsHolidayTransferTest.kt
- [ ] T2 渲染期解析: transferFor + effectiveDayOfWeek + isOrphanFor
  - Accept: `transferFor(date, transfers, holidays)` 命中=该 entry, 未命中=null; `effectiveDayOfWeek(date, transfers, holidays, segmentByDate)` 命中取 targetDate.dayOfWeek, 未命中取自然星期; `isOrphanFor(entry, holidays)` = sourceDate ∉ holidays
  - Verify: `HolidayTransferResolverTest` 10 用例 (含跨节/同 target 互斥覆盖/失效行/同源日多条冲突) 全红→绿
  - Files: HolidayRange.kt (扩展 HolidayTransferOps), HolidayTransferResolverTest.kt
- [ ] T3 shouldGrey 联动: 命中映射的放假日永不灰
  - Accept: `HolidayManager.shouldGrey(ctx, date, transfers)` 命中映射→false; `decideGrey` 入参加 `transfers` 走 false 短路; 默认旧行为(transfers=空)不变
  - Verify: `HolidayGreyTransferTest` 6 用例 (有映射/无映射/周末补班/忽略补班日开关) 全红→绿
  - Files: HolidayManager.kt (shouldGrey + decideGrey), HolidayGreyTransferTest.kt

## Checkpoint A (T1–T3): 全测试绿 · assembleDebug 绿

## Phase 2 VM + 渲染 + widget 全替换

- [ ] T4 ScheduleScreen / TodayScreen 走新解析器
  - Accept: `ScheduleViewModel.state.transfers` 替 `makeupDays`; `transferDayFor(date)` 替 `courseDayFor(date)`; ScheduleScreen 网格 daySwap 用新 `effectiveDayOfWeek`; TodayScreen 今日日 = `state.transferDayFor(today)`; 渲染期替身语义保持(行 day 字段改写, 不写库)
  - Verify: `HolidayTransferScheduleContractTest` 锁 ScheduleScreen `daySwap` 仍存在 + TodayScreen 仍走 `transferDayFor`; 旧 `resolveCourseDay`/`courseDayFor` 全仓 grep = 0; 手动模拟器: 元旦卡设 1/4 → 1/4 列显示 1/1 (周四) 课
  - Files: ScheduleViewModel.kt, ScheduleScreen.kt, TodayScreen.kt, HolidayTransferScheduleContractTest.kt
- [ ] T5 widget 8 变体全替换 + MakeupCourseDayHelper 重写
  - Accept: `HolidayTransferHelper.effectiveDayOfWeek(ctx, tableId, date, holidays, segmentByDate)` 替 `MakeupCourseDayHelper`; 8 变体 (Today/TwoDay/WeekGrid/WeekList/WeekView/WidgetCompactWindow/WidgetRenderActivity/WeekGridWidgetProvider) 全部走新 helper; `MakeupCourseDayHelper.kt` 删除
  - Verify: `HolidayTransferWidgetContractTest` 锁 8 变体 + CompactWindow + RenderActivity 全调新 helper; 旧 `MakeupCourseDayHelper` grep = 0
  - Files: MakeupCourseDayHelper.kt (改名 + 重写), TodayWidget.kt, TwoDayWidget.kt, WeekGridWidgetProvider.kt, WeekListWidget.kt, WeekViewWidget.kt, WidgetCompactWindow.kt, WidgetRenderActivity.kt, HolidayTransferWidgetContractTest.kt
- [ ] T6 闹钟 + 每日摘要走新解析器
  - Accept: `CourseNotificationScheduler.scheduleAll/scheduleCourseAlarm` 取课用新 helper; 摘要节点构建用 `effectiveDayOfWeek(date, transfers, holidays)`
  - Verify: 闹钟 contract 测试锁 scheduler 走新 helper; 旧 `resolveCourseDay` 全仓 grep = 0
  - Files: CourseNotificationScheduler.kt

## Checkpoint B (T4–T6): assembleDebug 绿 · 8 widget + 今日 + 闹钟语义自洽

## Phase 3 UI 重建

- [ ] T7 节卡 + 右格下拉 + 失效行 + 课表下拉 + 段编辑折叠
  - Accept:
    - 每张 public_holiday 段一张卡;卡头=节日名+课表下拉(SegmentedSwitcher 风格)
    - 卡内每行 `[M月d日(周X)] [→] [右格:灰空 / 填日期]`
    - 点右格下拉:`[全部补班日(按日期)] [无] [其他日期…]`,固定三项,无 disable
    - 选"其他日期…"弹 DatePickerDialog → 确认 → 当 targetDate 落库
    - 失效行灰底 + 提示文案 + 可点清除
    - 段编辑折叠到 "可编辑假期段" 折叠卡(默认收起), 保留原弹窗逻辑
    - 无课表提示卡保留原 `holiday_makeup_no_table`
  - Verify: `HolidayTransferSettingsContractTest` 锁下拉项顺序、卡头课表下拉、失效行渲染、段编辑折叠默认收起; UI 模拟器三段(元旦/春节/国庆)各设一映射 + 跨节互斥 + 失效映射呈现
  - Files: HolidaySettingsScreen.kt (大幅重写), HolidayTransferSettingsContractTest.kt
- [ ] T8 删旧字段 + strings 替换 + i18n 全套 + feature-baseline 同步
  - Accept:
    - 旧 prefs key `holiday_makeup_days_<id>` 全删
    - `MakeupDay` 类 / `holiday_makeup_days_<tableId>` 相关 prefs 函数全删
    - 旧 strings `holiday_makeup_*` 改名 `holiday_transfer_*`(4 键×6 locale = 24 项 + 新增 5 键 `holiday_transfer_no_workday_option / holiday_transfer_orphan_hint / holiday_transfer_other_date / holiday_transfer_edit_segments / holiday_transfer_no_table_segment_card`)
    - feature-baseline.md §5.4.b/§11/§12/§J 同步(调休映射章节)
    - `StringsKeyParityTest` 把新 key 加进 parity 清单
  - Verify: `grep -rn 'holiday_makeup\|MakeupDay' app/` = 0; `assembleDebug` 绿; lint 与基线对照只允许新增 5 键引发的 MissingTranslation; `feature-baseline.md` 调休章节与代码一致
  - Files: 6×strings.xml, AppPrefs.kt, HolidayRange.kt, HolidaySettingsScreen.kt, feature-baseline.md, StringsKeyParityTest.kt

## Checkpoint C (T7–T8): UI 全链绿 · 全测试绿 · 模拟器三场景实测

## Phase 4 收口

- [ ] T9 全量测试 + lint + 契约锁补全 + feature-baseline 校
  - Accept: 单测全跑 (基线 1980 + 新增 ≥35 = ≥2015) 绿; lint 与基线 a453b5f7 仅允许 i18n 新增项; 11 取课点契约锁 (ScheduleScreen/TodayScreen/8 widget/闹钟) 全检; README/feature-baseline 一致
  - Verify: `./gradlew :app:testDebugUnitTest :app:lintDebug` 全绿; 11 取课点 grep 全调新 helper
  - Files: test 全套

## 验收(用户原话 vs 实现行为)

- "默认不填,但是给用户,用户要是点开了右边这个映射的话,可以自动有一个这个默认的一个选项" → 右格灰空,下拉首段"全部补班日"(全集)
- "不能把多天都绑到同一天上面,如果他这一天选了的话,那就得其他两个就没得选了,你得让用户去选其他日期" → 互斥按目标日,后选覆盖前选,前选行视觉回灰
- "点开这个灰色的小方块,小圆角矩形,然后展开来先是 1 月 4 号,然后第二行是无,然后第三行是其他日期" → 下拉三段固定顺序: ①官方补班日(全集,按日期) ②无 ③其他日期
- "用户点其他日期的话,然后就给他弹出来系统的那个选其他日期的那个窗口" → 系统 DatePickerDialog
- "多个补课的也是让用户自己填,然后有哪些官方补课日,就是放到这个默认这个默认的下拉菜单里" → 多个官方补班日全列在下拉首段
- "如果没有的话,也允许用户去选其他的日期" → "其他日期"项始终存在
- "跨课程表,那也是每一个课程表有自己的一个调休安排,每一个课程表都要自己进行设置" → 卡头课表下拉切课表, 按 (tableId) 隔离存储
- "互斥肯定是跨天互斥的,是按天算的" → 互斥按 targetDate,跨节也生效
- "节卡标题就是原来怎么做,现在就怎么做" → 复用现有 HolidayRangeListCard 标题渲染逻辑, 段编辑折叠保留
- "把多天都绑到同一天上面" 误解纠正: 用户最终确认是 "后选覆盖前选,前选行回灰";允许源日不同但目标日相同(只要时间序上晚选覆盖)