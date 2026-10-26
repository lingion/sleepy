# HBSM 跨仓验证 — Step 3 派单结果

- 派单数 = 2 = candidates.json 数 (全量, 无手削)
- 采集日期 2026-10-10, 两 agent 均返回 schema 化 verdict

## 候选 1: qiqqqqq517/shangkeschedule (28★, Apache-2.0)

- **verdict: INDIRECT (同判定印证)**
- timetable_schools.json 独立收录 湖北商贸学院 = `zhengfang_new`, url `https://jwxt.hbc.edu.cn/`
- 与 sleepy 预分析判定 (zf_new) 一致 — 两个独立维护方对该校得出同一协议判定
- zhengfang_new.js 字段映射: kcmc/xm/cdmc/xqj/jcs/zcd, parseWeeks 处理 `9-16周` 与 `11-17周(单)` 单周形态
- 无该校特殊分支 — 走通用 zf_new 路径

## 候选 2: LonelyMarch/OpenWakeUp (6★, AGPL-3.0)

- **verdict: NEGATIVE (无新正方逻辑)**
- schools.json 收录 HBSM 为 `type=zf` (WakeUp 谱系旧版伞型: ASP.NET Table1/kbgrid_table HTML 网格)
- 其 `ZfNewParser` / `Zf1Parser` 存在但**未接入 ParserFactory**, 无学校使用 — 对该校实际处理走旧版 `ZfParser`
- 代码中无 kbList JSON / xskbcx_cxXsgrkb 端点逻辑
- 判定差异说明: OpenWakeUp 的 zf 伞型未区分新旧正方, 归类粒度粗于 shangkeschedule/sleepy; 其根路径 URL 收录与 jwxt.hbc.edu.cn 域一致

## 综合结论

- 有效正证据 = 1 (shangkeschedule, 独立同判定)
- 负/无关 = 1 (OpenWakeUp, 伞型归类无协议细节, 不构成反证)
- **无任何仓库提供与 sleepy 预分析冲突的协议证据**
- HBSM 走通用 zf_new 路径 (kbList JSON), 无需特殊分支 — 与 scope.md 预分析一致
