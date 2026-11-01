# Spec: 幽灵滑块全应用推广 与 单次课程节次滑杆化

日期 2026-10-29 · 需求方 lingion · 状态: 待批准

## 1. Objective

把"幽灵"统一语义铺到全 app 每一个滑块。用户 2026-10-29 原话定义：

> 用户原来设的是 7dp，改成 8dp 或 6dp 时，原来 7dp 的地方就有一个幽灵提醒他这是他上一次的结果；一直用中间的滑块、不小心拖到旁边，也应该能看到上一次是什么；直到退出这个页面或保存，幽灵才会消失。

两条需求线：

任务 A — 幽灵推广到全部单头滑杆（新做单头变体，形态为原值位置一条浅色细条）。
任务 B — 长按单次课程弹层（OccurrenceEditor）里的"起始节次 + 连续节数"两个数字文本框，合并改造成加课表单"每卡节次"同款 IntRangeSlider 双头滑杆，幽灵一并按同款语义来（用户裁决原话："跟我的编辑节次完全对齐，它没有任何不同的地方，编辑节次什么样的双头滑块，什么样的幽灵就怎么来"）。

### 已获用户裁定的语义要点

1. 色相条要上幽灵（用户答"要"）。
2. 设置类滑杆基线锚"进页时刻"（用户答"认"）：这些滑杆松手即落 AppPrefs，没有离散的"保存"时刻可锚，且"最近一次保存"会被拖动自身追着改；所以基线在每次进入页面（或打开弹窗）的那一刻取当时的值。退出再进，基线重算。
3. 单头幽灵形态是一条细条（单值没有区间段），不做轨道段（用户答"确认"）。
4. OccurrenceEditor 幽灵基线取打开弹层时的节次区间，与加课表单同语义（用户对"完全对齐"的裁定覆盖此条）。

## 2. 覆盖面盘点（全 app 滑块调用点, 无遗漏）

已有幽灵、本 spec 不动（回归保护对象）：AddCourseScreen 三处双头 IntRangeSlider（顶层周次范围 :627、每卡节次 :1193、每卡周次 :1232）。

任务 A 站点清单：

| 站点 | 文件 | 值域 | 基线锚点 |
|---|---|---|---|
| 课表缩放 / 周条缩放 / 圆角比例 / 冲突堆叠内缩 / 冲突导轨内缩 / 冲突折叠尺寸（6 条, 共用 DisplaySlider） | ui/screen/mine/ScheduleDisplayScreen.kt | 连续 float 与整数档 dp | 进入 ScheduleDisplayContent 组合那一刻; AppearanceScreen 内嵌同一份内容自动同获 |
| 三行式抬头悬挂偏移 | ui/screen/mine/PeriodHeaderSettingsScreen.kt | -1.0..1.0, 0.1 步进 | 进入该设置屏那一刻 |
| 色相条 两处 | ui/component/ColorPickerDialog.kt 的 HueSlider, 被 ColorPickerDialog 与 AppearanceScreen 彩虹条复用 | 0..360 | 打开取色弹窗 / 进入该编辑区那一刻 |

任务 B 站点：OccurrenceEditor.kt :127-130 的"起始节次 + 连续节数"一行。

## 3. 行为规格

### 3.1 单头幽灵（新变体）

- 形态：轨道（色相条为整条彩虹带）上基线值对应位置的一条浅色细条。普通滑杆细条仿默认 thumb 竖胶囊形态、primary 低透明度（与双头幽灵端点细条同族观感）；色相条上因底衬色相任意，细条用与全色相都拉开对比的中性描色（实现时定，真机目验闸兜底，不以性能或省事为由预先降级观感——见 memory aesthetics-over-performance-doctrine）。
- 可见性判据（纯函数）：基线非空且当前值不等于基线则显示，拖回基线即消失。连续值用容差比较时容差必须与最小步进对齐（0.1 步进档容差 0.05 以下、色相按 0.5 度），保证"拖回原刻度点"判得住。
- 非交互：不吃手势、不吞点击、不进无障碍语义、不参与朗读。
- 既有行为零回归：onValueChangeFinished 落库节奏、HueSlider 的 onHueChange/onHueCommit 节流分工、页面滚动协调全部原样。

### 3.2 OccurrenceEditor 节次双头滑杆

与 AddCourseScreen 每卡节次 (:1193) 完全同构：

- 域 1..maxStd，maxStd 取 TimeTableUtils.maxStandardNode(该课表 timeJson)；单档域退化静态读数。
- 显示口径夹取（脏数据时滑杆与摘要一致），落库仍走既有校验 require 兜底（:71-74 的 rows 存在性校验保留，不静默吞越界）。
- 存储不动：写回换算 step = end - start + 1，CourseEntity 仍 startNode + step。
- 摘要文案沿用 slot_summary_period_single/range 两条 string resource。
- 幽灵基线：打开弹层时的 startNode..startNode+step-1；拖离显示、拖回消失；保存后弹层关闭重开即初值刷新自然消失。
- 键盘方向键微调、接触即冻结页面滚动（sliderFreezePageScroll）随组件白捡。
- "自定义本次起止时间"勾出的两个 HH:mm 文本框维持文本输入不动（时刻不是整数域，不在射程）。

## 4. 边界裁定（待用户拍板的一条）

OccurrenceEditor 现在允许把单次课程改到边缘槽位（非常规节, startNode 超出 1..maxStd 的 edge 行——现行文本框无域限制, rows 存在性校验放行）。双头滑杆的域是 1..maxStd，标准滑杆表达不了边缘槽位。加课表单的处理原则正是"标准卡走滑杆、非常规节次走槽位选择不渲染滑杆"。

我的建议方案（对齐加课表单原则）：弹层里当该课当前节次落在标准域 1..maxStd 内时显示双头滑杆（绝大多数场景）；落在标准域外（边缘槽位）时退回现有两个数字文本框。滑杆不新增"把课从标准域拖进/拖出边缘槽位"的能力——现行文本框也写不出 rows 里不存在的节次，能力面不缩。
备择方案：永远滑杆、越界值夹进域内显示（会静默改掉边缘课的实际节次语义, 我不推荐）。

## 5. Commands

测试（聚焦）: `./gradlew :app:testDebugUnitTest --tests "*IntRangeSlider*" --tests "*Occurrence*"`
测试（全量）: `./gradlew :app:testDebugUnitTest`
构建: `./gradlew :app:assembleDebug`
提交信息闸门: `npx commitlint --from origin/main --to HEAD --verbose`
真机验证（用户自装）: hw-adb-install, 包名 com.lingion.sleepy.debug

## 6. Project Structure

改动面：
- app/src/main/java/com/lingion/sleepy/ui/component/IntRangeSlider.kt — 新增单头幽灵判据纯函数与轨道细条渲染复用件
- app/src/main/java/com/lingion/sleepy/ui/screen/mine/ScheduleDisplayScreen.kt — DisplaySlider 挂基线与幽灵（6 站点一次覆盖）
- app/src/main/java/com/lingion/sleepy/ui/screen/mine/PeriodHeaderSettingsScreen.kt — 悬挂偏移滑杆挂基线与幽灵
- app/src/main/java/com/lingion/sleepy/ui/component/ColorPickerDialog.kt — HueSlider 增基线参数与细条（两处复用点同获）
- app/src/main/java/com/lingion/sleepy/ui/screen/edit/OccurrenceEditor.kt — 节次行改双头滑杆与幽灵
- 测试: 上述模块对应的 *ContractTest / *PipelineTest / 纯函数测试新文件, 位置沿用 app/src/test/java 同包

## 7. Code Style

沿用 IntRangeSlider.kt 现行惯例：中文 KDoc 写明需求与裁决出处；纯判据函数 internal 与渲染解耦；Robolectric 管线测试存在性断言只用 testTag（locale 文案断言不可靠, 先例 TimeSlotEditorMotionPipelineTest）。

## 8. Testing Strategy

框架 JUnit4 + Robolectric，位置 app/src/test 同包。红绿先行：

1. 纯函数测试：单头幽灵可见性判据（基线 null 不可见、相等不可见、不等可见、容差与步进对齐、脏值夹取后重合不可见）。
2. 源码契约测试：六加一站点全部消费幽灵判据/渲染单点、禁手抄字面量（先例 NavHostMigrationContractTest 的 banned-literals 扫描口径）。
3. Robolectric 管线测试：OccurrenceEditor 节次行渲染出 IntRangeSlider（testTag 存在性）、幽灵出现与排空终态；DisplaySlider 管线至少一条代表站点拖离与拖回。
4. 全量回归：既有 IntRangeSliderGhost*、Occurrence* 系列全绿，全量 0 失败。

## 9. Boundaries

- 总是：commit 前全量测试；一个 commit 一个可独立回滚单元；verify 行只写真实执行；作者 lingion <lingion@hrbeu.edu.cn>；无 Co-Authored-By；引用 issue 只用 ref #N。
- 先问：合并 main、push、真机装机、发版。
- 绝不：自动关闭关键词；以性能为由预先降级观感；改课程数据层/落库校验/教务协议。

## 10. Success Criteria

1. 任务 A 九个渲染站点（6 显示参数 + 1 悬挂 + 2 色相复用点）拖离进页基线即见细条、拖回即消失、退出重进基线重算，且原落库节奏一克未动。
2. 任务 B 弹层节次行为与加课表单每卡节次行为逐项一致（域、夹取、摘要、写回换算、冻结、键盘微调、幽灵判据）。
3. 全量测试 0 失败、assembleDebug exit 0、commitlint 全区间绿。
4. 交付报告含"用户原话 vs 实现行为"逐条对照表（设计规范四铁律之四）。

## 11. Open Questions

1. 第 4 节边缘槽位回退方案，按建议来还是永远滑杆？
2. OccurrenceEditor 改造是否需要把"连续节数"文本框彻底删掉（我的方案是删——它被滑杆右端取代, 与加课表单一致），确认。
