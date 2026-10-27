# 立项 Brief：星链（StarLink）课表分享码导入

> 2026-10-27 立项 · 来源：拾光课程表竞品调研 · 状态：待排期

## 1. 目标

Sleepy 增加一种课表导入格式：**粘贴星链分享文案 → 提取分享码 → 拉取云端课表 → 导入**。竞品拾光已在 GLOBAL_TOOLS 提供该能力（`shiguang_warehouse/resources/GLOBAL_TOOLS/starlink.js`，MIT，触达必致谢），Sleepy 当前全仓 grep「星链 / starlink」零命中，是空白面。与已支持的 WAKEUP_SHARE / WAKEUP_JSON（ImportSheet.kt）同族形态，用户无需教务账号凭据。

## 2. 协议规格（从 starlink.js 逆向，含实物证据）

- 端点：`GET https://api.starlinkkb.cn/share/curriculum/<shareCode>`，无鉴权、无 cookie。
- 分享码提取（拾光实现，双分支宽松 regex）：
  1. 结构化命中：`/输入：\s*([^（\s]+)/`（分享文案带「输入：<code>」提示语）；
  2. 兜底命中：`/([a-zA-Z0-9-]{5,20})/`；
  3. 全失败则整串 trim 当码。
- 响应 JSON：`{ data: { courses: [ { name, teacher, location, weekday, startSection, endSection, weeks: [int] } ], startDate, totalWeeks, sectionMinutes: { "<节次号>": [startMin, endMin] } } }`。
- 映射规则（拾光语义，Sleepy 侧等价实现）：
  - `location` 去前导 `@` 与空白，空则「未排地点」；`teacher` 为「无」或空则「未知教师」；
  - 合并去重两阶段：同名同师同地同天同周次且节次连续 → 延长 endSection；完全重复 → 丢弃；再按节次合并周次；
  - 学期配置：`semesterStartDate = startDate` 前 10 字符、`totalWeeks` 缺省 20；
  - 作息表：`sectionMinutes` 分钟数转 `HH:mm`，按节次号升序。

## 3. 未决项（排期时先做侦察）

1. **api.starlinkkb.cn 可达性与存活**：大陆直连实测（GET 一个样例码），确认无 WAF/UA 分流；服务方身份与稳定性无公开信息，属未知。
2. **样例分享码**：需要至少一个真实分享文案做 fixture 与活体回归；拾光脚本无 fixture 仓库可借。
3. **数据形态差异**：星链 `weekday` 1..7 起点、`weeks` 是否含 0/负数、跨学期码过期语义——拿到样例后定 fixture 反例。
4. **产品形态**：入口放 ImportSheet「从分享文本导入」通路（与 WakeUp share 并列）还是独立格式 chip，实现时按现有 ImportPlanner 流程定。

## 4. 实现骨架（预估）

- 新增格式常量与嗅探（星链分享文案特征 = 「输入：」提示语或 api.starlinkkb.cn 链接）；
- fetch 走既有导入网络层，解码唯一落点 Kotlin parser（与 HEBZYHJ 跨语言 invariant 纪律一致：regex 与嗅探特征如落 JS 需逐字符锁）；
- fixture：N form（结构化文案 / 裸码 / 失效码 / 空 courses）× N 反例；
- 致谢：触达 starlink.js 按 SOP Step 5.5 记 shiguang_warehouse（MIT）。

## 5. 判定依据与本仓不做的事

不复用拾光的远程 JS 执行架构；本协议为无凭据 GET，风险面仅数据可信度（导入前走既有课表预览确认流）。
