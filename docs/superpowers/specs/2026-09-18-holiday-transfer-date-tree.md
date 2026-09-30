# Spec: 节假日调休映射 — 放假日视角的日期→日期树状 UI

## Objective

issue#44 第二轮重构。把现有“补班日视角 / 星期几”调休映射,重做为**“放假日视角 / 补班日具体日期”**的树状 UI。每个法定节假日独占一卡,卡内每个放假日一行,行结构 `[放假日] → [补班日]`,右格为补班日选择器;按课表独立存储;跨节跨源日不互斥,仅按**目标日**互斥(一天只能上一次课,后选覆盖前选)。

成功标准:
- 用户打开设置页,在某节卡(如元旦)任意一行点右格,看到 `[全部官方补班日(按日期排序全集) | 无 | 其他日期]` 三段下拉
- 选择官方补班日 → 立即落盘 + 课表/今日/widget/闹钟全部按新映射刷
- 同节另一行选同一目标日 → 后选行变占位,前选行右格自动清空(回灰)
- 跨节同理(任意其他节卡的行再选同一目标日,旧选择被覆盖)
- 选“无” → 该放假日回到自然星期(不映射)
- 选“其他日期” → 弹出系统 DatePicker,用户自选任意日期,落入同一互斥规则

## Tech Stack

- Kotlin 2.0 / Compose / Material3 / Gradle (与现有 sleepy 安卓基线一致)
- 现有 AppPrefs / HolidayManager / HolidayRangeOps 全部沿用,只扩展
- 测试: JUnit4 + Robolectric(沿用项目既有单测基线)
- 端点未变: `https://unpkg.com/holiday-calendar@1.3.3/data/CN/<year>.json`,字段 `{year, region, dates:[{date, name, name_cn, name_en, type}]}`,type ∈ {public_holiday, transfer_workday}

## 关键数据决策(用户拍板)

- 存储层: `HolidayTransferEntry(sourceDate, targetDate, segmentId)` 三字段,按 `(tableId, sourceDate)` 唯一索引(一天只能映射一次,符合“后选覆盖前选”语义)。
- 旧 `MakeupDay(date, sourceDayOfWeek)` 字段废弃不迁移(分支未发布,无用户存量)。
- 渲染层: `effectiveDayOfWeek(date, transfers)` = `targetDate` 的星期几;无映射 → 自然星期。
- 互斥: 写入前先按 `targetDate` 查找全表已存在的 entry,有则移除,再写入新 entry(同事务内)。这是“后选覆盖前选”的代码层表达。
- 跨节补班日全集平铺: 下拉里**所有 transfer_workday 条目(按日期排序)全集**显示,不做节归属过滤、不做 UI disable、不分组。归属信息用户从卡头标题已经能识别。
- 失效映射处理: 加载时若 entry.sourceDate 对应的日期不在当年 public_holiday 集合内,该 entry 在 UI 渲染为灰行 + 提示“对应放假日已不存在(可能学校另行通知),点按清除”;数据保留,用户手动清。

## UI 与交互(用户拍板)

- 节卡布局: 卡片,卡头 = 节日名(沿用现有 `HolidayRangeListCard` 标题渲染机制, 直接复用聚合段)+ 当前课表下拉(卡头右侧 SegmentedSwitcher 风格, 默认显示当前课表名, 展开全部表, 选中切刷新)
- 卡内每行: 三段式 — `[日期(放假日) + 星期(灰色小字)]  [箭头 →]  [右格: 灰空 / 填日期 / 占用徽章?]`, 行间用现有 hairline divider
- 右格: 圆角小矩形,默认灰空(描边灰 + 内部一条斜杠 + 居中文字 “无”),点击展开下拉
- 下拉结构(固定顺序):
  1. **官方补班日** — 当年所有 `transfer_workday` 条目全集,按日期升序,每项显示 `M月d日 (周X)`
  2. **无** — 选该项清除该 sourceDate 的映射(回灰空)
  3. **其他日期…** — 选中走系统 DatePicker,用户自选任意日期
- 提交时机: 每次点下拉项即落盘(无保存按钮)
- 删除映射走下拉里的“无”,不再设额外 × 按钮
- 卡头副标题: 一行小字“未设置的放假日按当天星期取课,不会替你猜”
- 替换原“节假日调休设置”页面里的: ④说明卡、⑧独立选择卡、列表卡⑦里没有取课入口 — 全部并入新卡结构;原⑦的段列表点击编辑弹窗仍保留(允许用户覆盖网络段)

## 边界

- 删表时清该表所有 HolidayTransferEntry (沿用 deleteTable 已有的 key 清理)
- 跨年: 视图按年切换;每张表的所有 entry 都随视图刷新;失效 entry 灰显(不删)
- shouldGrey 联动: entry 命中的放假日永不灰显(有课的列不可能灰)
- 用词统一: UI 文案统一用 “补班日” / “按哪天上课”; 标题一律 “调休日按哪天上课”
- 字符串 i18n: 6 locale 必齐 (en, zh-CN, zh-TW, ja); StringsKeyParityTest 锁 key 存在性

## 成功标准 (验收)

- 单测: 新模型 `HolidayTransferEntry` 编解码 + 互斥语义(`setEntry` 写 targetDate=D 时自动清该表其他 targetDate=D 的 entry) + 失效判定 (`isOrphanFor(holidays)`) 全绿
- 端到端 contract 测试: 周网格渲染期 / 今日页 / 8 widget 变体 / 闹钟 / 摘要 共 11 取课点全部走新 resolver (HolidayTransferOps.effectiveDayOfWeek)
- UI contract 测试: 锁下拉项顺序、卡头课表下拉、失效行渲染
- assembleDebug 绿, lint 与基线 a453b5f7 保持一致(仅允许新文案键引发的 MissingTranslation 增项)
- 课表页 widget 8 变体在补班日按映射取课, 不出现“灰色的列里排满课”
- feature-baseline.md §5.4 / §11 / §12 同步更新

## 风险

- API 多源/换源预留: 取数适配层 (`HolidayManager.parseEntries`) 不变, 调休映射 UI 不感知数据源
- 旧 `MakeupDay` 字段全删可能让“废弃不迁移”测试假阳性 — 用 git 删除 + grep 全仓零结果确认
- widget 11 取课点全替换为新 resolver 是大改动面, 必须契约测试锁全 11 点位都被改