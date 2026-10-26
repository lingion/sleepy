# WakeUp 协议族跨仓验证 — findings 汇总 (Step 3)

日期: 2026-09-16 · 38 候选全量, 每仓一份 schema verdict JSON 见 findings/ 目录

## Verdict 分布

| Verdict | 数量 | 仓库 |
|---|---|---|
| POSITIVE | 25 | coursehelper, shiguang-warehouse, dawn-course, kingo-1/2/3, kingo2-2/4/5, qz-1/2/3/4/5/6/8, ref-4, ref-5, adapter-2/3, wakeup-shu, suot-1, luke-1 |
| INDIRECT | 9 | kingo-4, kingo2-1, qz-7, ref-2, ref-3, adapter-1, ics-1, wit-1, tester-1 |
| NEGATIVE | 1 | adapter-4 (ZKJJaker 实为商丘 EAMS 非数维 — 反向证据) |
| NULL_EVIDENCE | 1 | kingo2-3 |
| 非 verdict 佐证 | 3 | suda-1 (partial), suda-2 (inconclusive), suda-3 (supports) |
| PRIMARY 文档 | 1 | ref-1 zfman/pub-docs (青果 wapController.jsp + encrypt key 'ju8opt' + getKb/kbdetail_bz 协议文档) |

## 关键实证 (驱动修复)

1. **kingo day 坐标 4 源一致** (vs Sleepy 旧实现 day=D):
   - WakeUp o0000OO0.java:117 `i10 = parseInt(group(1)) + 1`
   - WakeUp o000O0Oo.java:2085 (shuwei 族 mode16 共用) `get(0)+1`
   - CourseHelper Swift `dayOfWeek = match[1]` (0-based, 0=周一)
   - shiguang HUNNU `day = floor(idx/unitCount)+1` / UESTC `day = parseInt(match[1]) + 1`
   → 修复 commit c624363c

2. **suda rowspan** — WakeUp o0OO00O dex L00e9 `attr("rowspan")` + shiguang UPC class-C `endSection=start+rowspan-1`; Sleepy 旧实现无 rowspan → 修复 commit c624363c

3. **cumtb 未知课名** — WakeUp oo000o.java:45 `str == null → "未知"` 不丢行; Sleepy 旧实现 skip → 修复 commit c624363c

## 判定等价不修的差异

- suda `<br>{2,}` 容忍连续 br vs Sleepy 单 br split + 空行过滤 — 效果等价
- 表头跳过: WakeUp align!=center 过滤 vs Sleepy 星期X 文本过滤 — 效果等价
- 周次 token 形态差异 (区间+单双后缀 vs 位图) — 各通路内部自洽
- chaoxing 端点族 queryKbForGrdb/kckbData 在开源仓 0 命中 — WakeUp 内嵌 WebView 形态自洽, 开源 adapter 走 /admin/pkgl/xskb/* 同字段族
- south_soft: 开源界无专属仓 (ref-5 NUIST 实为正方), WakeUp o00000O0 mode10 HTML 形态与 Sleepy JSON 形态为同源数据异构, 互补不冲突

## 反向证据

- adapter-4 NEGATIVE: ZKJJaker/xiaoai-shuwei-course 名含 shuwei 实为商丘学院 EAMS courseTableForStd — 数维协议判定不能靠仓名
- ref-5: zyc-816/NUISTTable 实为正方 jwapp — 南方软件判定不能靠校名
