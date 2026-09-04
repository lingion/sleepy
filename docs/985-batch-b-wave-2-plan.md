# 985 批量收录 B 档第二波 — Plan

## Context

Sleepy (Android, Kotlin) 用户授权收录 985 全部 22 所。A 档(零代码,4 校)与合工大 EAMS5 已完成;B 档第一波(宽松/无 license 5 校:东南/浙大/中科大/川大/东北大)亦已落地,7 个 commit 已合 main(`d34b89d`)。

本计划覆盖 B 档剩余 7 校,按用户原话"宽松 license 先覆盖了,然后 License 有冲突的话,我这个是 GPL 3.0,你看,那能从头写就从头写,只复用它的逻辑"原则处理。

## Global Constraints (硬约束)

- **协议可用优先**: 必须存在开源 upstream 仓能用 JSON 协议解析课表;HTML scraping 无 clean JSON → 跳过
- **license 排序** (优先覆盖): 无 license / Apache-2.0 / MIT / BSD > LGPL-2.1 > GPL-2.0 > GPL-3.0
  - 冲突项目: 只复用其**逻辑思路**, 不得直接复制代码行(用户明确: "能从头写就从头写")
- **parser 形态**: 沿用第一波的两类 — 单/双周端点修正型 vs 协议丢失型(后者 type 强制 0,KDoc 标注)
- **TDD**: 失败测试先红 → 最小实现 → 绿
- **commit**: 每个逻辑单元一个 commit,小步前进
- **致谢归 about_license_body strings 三语**(values + zh-rCN + zh-rTW)
- **不可越权**: push/tag/publish 须用户明示; 🚫 Co-Authored-By: Claude; 🚫 emoji (✓✗⚠★ 保留)
- **不可逆操作**: schools.json 禁全量重排(只按字典序插入新条目)
- **worktree**: 本次实现不在 worktree,继续在 main(已有 7 commit 在 main 上)
- **测试必须过**: `./gradlew :app:testDebugUnitTest` 全绿 0 fail

## 任务清单

### Task 1 — 调研 7 校 (Phase 1: Research)
对以下 7 校逐一调研,产出 PER-SCHOOL 调研报告:
- 华东师范大学 (East China Normal University)
- 哈尔滨工业大学 (Harbin Institute of Technology)
- 西北工业大学 (Northwestern Polytechnical University)
- 北京航空航天大学 (Beihang University)
- 南开大学 (Nankai University)
- 厦门大学 (Xiamen University)
- 大连理工大学 (Dalian University of Technology)

每校产出:
1. GitHub 上游仓列表(搜 "学校名 + 课表/教务/schedule/timetable")
2. 协议族识别(qz / zf / urp / cas / html / 其他)
3. license 识别(精确到 LICENSE 文件内容)
4. 协议是否 JSON 可用(yes / no / partial)
5. 推荐结论:采纳 / 跳过(原因)
6. 若采纳,parser 形态(端点修正型 / 协议丢失型 / 全周型)

### Task 2 — 落地 (Phase 2: Implement, 取决于 Task 1 结论)
对 Task 1 标记"采纳"的校,每校独立 subagent 实施:
- 新 parser + fixture JSON + Jw<School>ParserTest
- JwProtocol TYPE_* + ALL_TYPES 追加
- JwParserRegistry FACTORIES 注册
- SchoolsJsonConsistencyTest declared set 同步
- schools.json 字典序插入
- strings 三语致谢
- AboutLicenseAttributionTest 增 BATCH_C/BATCH_D 等批次项
- 独立 commit

### Task 3 — 最终验证 (Phase 3: Verify)
- 全测试回归: `./gradlew :app:testDebugUnitTest --rerun-tasks` 0 fail
- 独立 agent 多维度交叉验证:
  - 7 commit diff 复核
  - parser/test/fixture 三件套齐
  - schools.json 字典序
  - strings 三语同步
  - JwProtocol TYPE_* + Registry FACTORIES 一致
  - KDoc license 标注
  - emoji 禁
  - Co-Authored-By 禁
- 输出最终验证报告

## 排期
- Task 1: 必先完成(Task 2 强依赖)
- Task 2: 7 校(部分可能跳过)逐校 commit,可串行或小批并行(每批 ≤2 校避免冲突)
- Task 3: Task 2 全部 commit 完再触发

## 成功标准
1. 7 commit + B 档所有可采纳校落地
2. 全测试 0 fail
3. 独立交叉验证报告 PASS
4. memory 更新(状态总览表 + 各 commit 链接)
