# 逐卡非常规节次/时间 Implementation Plan

> For agentic workers: 按任务逐个执行, 小步 commit。

**Goal:** 课程级总开关模型推翻 → 每张节次卡片两个独立可选项（非常规节次 / 非常规时间）。

**Architecture:** CourseEntity 行级加两列布尔（v4→v5 迁移）；边缘槽位存 timeJson（新增槽位默认时间编辑函数）；AddCourseScreen 删除课程级 IrregularSection，卡片内嵌两个 Switch + 候选弹层 + B 规则三输入；冲突/渲染/widget 走现有链路。

**Spec:** docs/superpowers/specs/2026-09-08-per-card-irregular-design.md

## Global Constraints

- 分支 feat/edit-irregular-per-card；合并前全量单测 + lint 全绿。
- commit 邮箱 lingion@hrbeu.edu.cn；尾注禁 Co-Authored-By: Claude。
- 验证仅 build/单测/静态检查；禁 emulator。
- UI 纯色块无描边；6 locale 文案同步（StringsKeyParityTest 兜底）。
- 全量单测唯一允许失败 = JwProtocolFixtureMatrixTest（pre-existing）。

## Task 1: 数据层 — CourseEntity 两列 + v5 迁移

- Modify: data/entity/CourseEntity.kt, data/Migrations.kt, data/AppDatabase.kt (version 5)
- Test: app/src/test/java/com/lingion/sleepy/data/CourseEntityFlagsTest.kt
- CourseEntity 加 isIrregularNode / isIrregularTime 两列（default "0"）。
- MIGRATION_4_5: ALTER TABLE courses ADD COLUMN isIrregularNode INTEGER NOT NULL DEFAULT 0;
  ALTER TABLE courses ADD COLUMN isIrregularTime INTEGER NOT NULL DEFAULT 0;
  UPDATE courses SET isIrregularTime = 1 WHERE ownTime = 1;
- 测试: 默认值 + copy 保真（CourseEntityFlagsTest）。
- Commit: feat(db): courses 行级 irregular 标志 + v5 迁移

## Task 2: TimeTableUtils — 候选集合 + 槽位时间编辑

- Modify: util/TimeTableUtils.kt
- Test: app/src/test/java/com/lingion/sleepy/util/EdgeCandidatesTest.kt
- 新增 EdgeCandidate(node, start, end, exists) + edgeCandidates(timeJson):
  Before 组 = 现有 Before 槽位降序 + 新建(min-1 或 0); After 组 = 现有 After 槽位升序 + 新建(maxStd+1)。
  复用槽位带时间, 新建候选 exists=false 时间空串。
- 新增 updateEdgeNodeTimes(timeJson, node, start, end): 仅边缘行可改, 标准行原样返回。
- 测试: §2.2 候选表逐行对表; updateEdgeNodeTimes 边缘行生效/标准行无效。
- 收尾: resolveIrregularCourseTime + IrregularTimeResolution 在 Task 3 消费方迁走后删除。
- Commit: feat(edit): edgeCandidates 候选集合 + updateEdgeNodeTimes 槽位时间编辑

## Task 3: AddCourseScreen 逐卡重构

- Modify: ui/screen/edit/AddCourseScreen.kt, res/values*/strings.xml (6 locale)
- MeetingBlockDraft 加 isIrregularNode / isIrregularTime / selectedEdgeNode 三个状态字段。
- 删除 IrregularSection + 课程级 irregularEnabled/irregularNodeStartTime/irregularNodeEndTime。
- 卡片内: 非常规节次 Switch → 候选弹层(edgeCandidates 喂数据); 已有槽位 = 置灰时间 + 编辑图标 → 弹窗改默认时间(pendingEdgeEdits); 新建候选 = 起止输入 → pendingEdgeInserts。
- 卡片内: 非常规时间 Switch → 开始/结束/时长三输入 B 规则联动(改起止算时长, 改时长算结束); 预填 = 所选节次默认时间。
- validateCourseDraft / buildCourseEntity / blockRangeMinutes 去

全局参数, per-block effective range 单一函数化。
- 编辑回填: ownTime=true → isIrregularTime=true 预填起止; 边缘 startNode → isIrregularNode=true 选回槽位。
- 新 key 6 locale: irregular_node_switch/_sub, irregular_time_switch/_sub, irregular_node_pick, irregular_node_new, irregular_node_required, 槽位编辑弹窗三 key。
- 删除 resolveIrregularCourseTime + IrregularTimeResolution(消费方已迁走)。
- Commit: feat(edit): 逐卡非常规节次/时间 UI 重构

## Task 4: 全量验证

- ./gradlew :app:testDebugUnitTest 全绿(除 JwProtocolFixtureMatrixTest)
- ./gradlew :app:lintDebug 0 warning
- ./gradlew :app:assembleDebug 通过

## Risks

- Room schema 校验失败 → defaultValue "0" 精确对齐, 预置 v4 db 人工验升级。
- validate/build/range effective 解析不一致 → 单一 blockEffectiveRange 函数, 四处全部调用。
- 旧 ownTime 课编辑丢配置 → 迁移 + 回填链路。
- edge 旧课 step>1 → 编辑回填 step 显示 1, 渲染不动。
