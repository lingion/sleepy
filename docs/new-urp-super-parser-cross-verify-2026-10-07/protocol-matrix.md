# WakeUp `NewUrpSuperParser` 第二变体 (o0O0o) — protocol-matrix

日期: 2026-10-07 · 分支: feat/adapt-new-urp-super-parser · SOP: jw-cross-verify-sop v1.1+

## 协议族

WakeUp `NewUrpSuperParser` 包含两个变体 (同 SUPER 串行调用, 任一成功即用):
1. `o0` (dateList/selectCourseList/timeAndPlaceList 嵌套) — 已由 `JwNewUrpParser` 覆盖 (PR 此前已合并)
2. `o0O0o` (flat JSON 数组 + skzc 位图) — **本轮新增 `JwNewUrpSuperParser`**

## WakeUp 真源

- `tools/reverse/wakeup-fresh/smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/o0O0o.smali`
- `NewUrpClassListItem` 字段族 (`kcm`, `jsm`, `jxlm`, `jasm`, `cxjc`, `id: {skxq, skjc, skzc, ...}`)
- 触发: `NewUrpSuperParser.OooO0o0()`: 先试 `o0`, 失败试 `o0O0o`

## JSON 形态

```json
[
  {
    "kcm": "高等数学",
    "jsm": "张老师",
    "jxlm": "教学楼A",
    "jasm": "301",
    "cxjc": 2,
    "id": {
      "skxq": 3,
      "skjc": 5,
      "skzc": "1111111111111111111100000000",
      "..."
    }
  }
]
```

### 字段语义

| 字段 | 含义 | 类型 | 备注 |
|---|---|---|---|
| `kcm` | 课名 | string | 锚点之一 |
| `jsm` | 教师 | string | 可空 |
| `jxlm` | 教学楼/教学区 | string | 锚点之一; 与 `jasm` 拼接成 room |
| `jasm` | 教室 | string | 与 `jxlm` 拼接成 room |
| `cxjc` | 持续节次 | int | `endSection = skjc + cxjc - 1` |
| `id.skxq` | 星期 1..7 | int | |
| `id.skjc` | 起始节 | int | 1-based |
| `id.skzc` | 周次位图 | string | `'0'/'1'` 字符序列, 长度 = 学期周数; 第 i 位 (i=1..N) = 是否第 i 周上课 |

### 位图 → 周次范围

- `1` 字符位置 i (1-based) 表示第 i 周上课
- 连续段 + 步长判定:
  - step=1 → `type=0` (每周)
  - step=2 且 start%2!=0 → `type=1` (单周)
  - step=2 且 start%2==0 → `type=2` (双周)
  - 步长异常 → 退化为每周单点

## 7 维度对照 (本轮 NewUrp Super)

| 维度 | WakeUp smali (jadx) | 开源仓证据 | Sleepy 实现 | 判定 |
|---|---|---|---|---|
| 端点 | (由 fetch 层承担, 见 `NewUrpApi` 同源) | code-search `kcm AND jxlm AND cxjc` triple 多仓命中 | 由 fetch 层承担 | 一致 |
| 顶层形态 | JSON **数组** (`o0O0o` smali) | 跨仓见 o0O0o 类同款字段 | 抠取最大合法 JSON 数组 (深度平衡扫描) | 一致 |
| 字段锚点 | `kcm + jxlm + cxjc + id{skxq, skjc, skzc}` | (本轮 verifiers 待补) | confidence() = 100 三件套 / 70 双件 / 30 单件 | 一致 |
| 周次位图 | `'0'/'1'` 字符串, 1-based | 同左 | `parseWeekBits` 提取 `1` 字符 1-based 索引 → `weekBitsToRanges` 段合并 | 一致 |
| 节次坐标 | `cxjc` 持续节次 | 同左 | `endSection = skjc + cxjc - 1` | 一致 |
| 单双周 | 位图奇偶判定 | 同左 | 段端点步长 1/2 + 起始奇偶判定 type | 一致 |
| 嵌套策略 | 顶层 flat array, 区别于 `o0` 嵌套 dateList/selectCourseList | 与 Sleepy 现有 `JwNewUrpParser` (处理 `o0` 嵌套) 互补不冲突 | 新增 parser 仅处理 flat array, 不重复处理 `o0` 嵌套 | 一致 |

## 与现有 `JwNewUrpParser` 的边界

| 维度 | `JwNewUrpParser` (PR 此前已合并) | `JwNewUrpSuperParser` (本轮) |
|---|---|---|
| 顶层 | `dateList[]` (按周) → `selectCourseList[]` (按日) → `timeAndPlaceList[]` | flat array `[]` (每个 item 一条流) |
| 锚点 | `dateList` + `selectCourseList` + `timeAndPlaceList` 嵌套 | `kcm` + `id` + `skzc` 三件套 (任一齐) |
| 周次表达 | 字符串 (例 "1-15单") | bitmap `'0'/'1'` |
| 共存 | 同时注册 TYPE_NEW_URP + TYPE_NEW_URP_SUPER, 自动按 confidence 优先 | 同左 |

## 检索矩阵

- scope: `docs/new-urp-super-parser-cross-verify-2026-10-07/scope.md`
- candidates: `docs/new-urp-super-parser-cross-verify-2026-10-07/candidates.json` (60)
- findings (待 verifiers 完成): `docs/new-urp-super-parser-cross-verify-2026-10-07/findings.json`
- attribution (待 findings 收齐): `docs/new-urp-super-parser-cross-verify-2026-10-07/attribution-candidates.json`