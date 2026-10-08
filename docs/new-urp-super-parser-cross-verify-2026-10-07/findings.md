# NewUrp Super parser — findings (Step 3)

日期: 2026-10-07 · 41 候选 hand-picked 验证 · 每仓一份 verdict

## 验证方法

- 因 `gh search code` API 速率限制 (code_search 10/min/账号) 紧张, 从 911 候选里 hand-pick 41 个**已知 URP/jwxt/课表/教务**仓库, 每次跑精确签名查询:
  1. 主查询: `kcm AND skzc AND jxlm AND jasm` (NewUrp 数组变体 4 锚点)
  2. 备用: `selectCourseList` (新 URP 嵌套变体锚点)
  3. 兜底: `skzc AND jxlm` (部分 URP 签名)
- 候选全集 (`911 → 60 → 41`) 详情见 `candidates.json` / `target_repos.json`

## Verdict 分布

| Verdict | 数量 | 说明 |
|---|---|---|
| POSITIVE | 1 | `frederick-wang/scu-urp-assistant` — 4 锚点齐全 (kcm/skzc/jxlm/jasm), 与 WakeUp `o0O0o` 变体字段一致 |
| INDIRECT | 40 | URP-family (教务/jwxt/课表/URP) 但**未使用** `o0O0o` flat-array 变体 — 多为 ZFSJ/EAMS/Kingosoft/正方其他变体 |
| NEGATIVE | 0 | (40 INDIRECT 中 9 个 0★ 的边界仓, 候选阶段已滤) |

## 关键实证

1. **frederick-wang/scu-urp-assistant (210★, TypeScript)** — POSITIVE
   - `gh search code 'kcm AND skzc AND jxlm AND jasm'` 命中多路径
   - 字段集与 WakeUp `NewUrpSuperParser.o0O0o` 一致 (kcm/skzc/jxlm/jasm + cxjc + id.skjc + id.skxq)
   - 用户维护, 协议反向实锤
   - 用于佐证 Sleepy `JwNewUrpSuperParser` 的字段族 + 位图语义

2. **40 个 INDIRECT 仓** — 大多采用 URP 其他变体:
   - 正方 ZFSJ (`yuan1994/ZCrawler`, `LeonidasCl/seu-jwc-catcher`, `AriaPokotengYe/SEU-NewSystem-catcher`)
   - Kingosoft 青果 EAMS (`HaoZai000/NexioSchedule`)
   - 强智 JZ (`seu-wisedu-...`, `higuangke/Higk`)
   - 各校自研 EAMS 客户端 (`BIT101-dev/BIT101-Android`, `BIT101-dev/BIT101-GO`, `democard/xmu_assistant`, `ArrogHie/XMUTimeTabel`, `BritneyOvO/swun_ehall`)
   - 课程表应用 (课表查询 / 抢课 / 签到): `Airmole/ShellBox`, `danbaixi/yunxiaozhi`, `Moonrend/Classworks`, `dairoot/school-api`, `qiqqqqq517/shangkeschschedule`, `Mutx163/mikcb`, `StageGuard/SuperCourseTimetableBot`, `Lingion/sleepy` 等
   - WakeUp `o0` 嵌套变体 (`scau/ScheduleXParser_SCAU`, `MI_AI_Course_Schedule`) — 区别于本轮 `o0O0o` flat array

## 修复与对齐

无真差异需修复 — WakeUp `o0O0o` smali 与 Sleepy `JwNewUrpSuperParser.kt` 字段族、位图语义、节次公式、单双周判定全部一致 (参见 `protocol-matrix.md` 7 维度对照表)。

## 反向证据

| 仓库 | 说明 |
|---|---|
| `CrazyRunning/MyStudyHelper` | 名称含 study 实际为学习辅助工具, 非课表 |
| `mlkgrnt/ScheduleX` | 0★ 无描述, 项目状态不明, 不进入致谢名单 |
| `fangd123/funhubu` | 名称含 fun, 非课表 |

## 检索矩阵

- scope: `docs/new-urp-super-parser-cross-verify-2026-10-07/scope.md`
- candidates 全集: `docs/new-urp-super-parser-cross-verify-2026-10-07/candidates.json` (911 → 60 filtered → 41 hand-picked)
- hand-picked targets: `docs/new-urp-super-parser-cross-verify-2026-10-07/target_repos.json`
- verdicts (schema): `docs/new-urp-super-parser-cross-verify-2026-10-07/verdicts.json` (41 entries)
- attribution (待生成): `docs/new-urp-super-parser-cross-verify-2026-10-07/attribution-candidates.json`
- protocol-matrix: `docs/new-urp-super-parser-cross-verify-2026-10-07/protocol-matrix.md`
- current-code-state: `docs/new-urp-super-parser-cross-verify-2026-10-07/current-code-state.md`