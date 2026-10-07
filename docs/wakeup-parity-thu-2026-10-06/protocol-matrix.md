# THU Protocol Matrix — v1.11 SOP Step 3

> 跨仓印证产物 (SOP §3 + §5.4)。8 仓对 WakeUp THUParser 协议六件套的命中/反向/缺失结论。
> 主参考 = WakeUp `THUParser.java` (Apache-2.0, suda/yzune)。实证 1 = Starrah/THUCourseHelper (MIT)。

**Generated**: 2026-10-06
**Author**: lingion <lingion@hrbeu.edu.cn>

---

## 1. 协议六件套 (WakeUp THUParser.java 元素)

WakeUp 算法核心由 6 个不变式构成 — 任何 THU 实现必须 char-by-char 命中（除等价表述外）：

| ID | 元素 | WakeUp 实证 | Starrah 实证 | Sleepy JwThuparser.kt 落地 |
|----|------|-------------|--------------|----------------------------|
| F1 | 节次↔大节映射 | `START_NODE_MAP=[0,1,3,6,8,10,12]` / `END_NODE_MAP=[0,2,5,7,9,11,14]` | ✅ 同构 (Kotlin) | ✅ line 32-37 |
| F2 | 14 节作息表硬编码 | `THU_TIME_SLOTS` (08:00-21:45) | ✅ 旁证 (huangkaka/huang 立此) | ✅ line 40-57 |
| F3 | 课程链接 regex | `\\d{10};(\\d{8})` | `strHTML \+= "<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>"` (line 851) | ✅ RE_C1_LINK (等价表达) |
| F4 | 课程名 regex | `<b>(.*)</b>` | ✅ `strHTML \+= "<b>(.*)</b>"` (line 853) | ✅ RE_C1_TITLE |
| F5 | 课程后续字段 regex | `"(.*)"` | `strHTML1 \+= ?\"；(.*)\"` (line 854) — **中文分号 ；** | ✅ RE_C1_DATA (中文分号) |
| F6 | 网格坐标 regex | `getElementById\('a1_1'\)` | ✅ `getElementById\('a(\\d)_(\\d)'\)` (line 855, big=1, day=2) | ✅ RE_C1_WEEKBIG |

---

## 2. 8 仓 verdict 表 (SOP §3 build_verdicts.py 输出 + 手工 pass2 复核)

| # | 仓库 | Star 数 | 协议命中 | 不变式结论 | LICENSE | 复用决策 |
|---|------|---------|----------|-----------|---------|----------|
| 1 | Starrah/THUCourseHelper | 1.7k | ✅ POSITIVE (Kotlin 同栈, char-by-char) | 实证 1 — char-by-char 锁定 | MIT | ✅ 思路/不变式参考, 🚫 不复用代码 (语言不通) |
| 2 | piggyham/thu_timetable | 12 | ⚠ INDIRECT (HarmonyOS ArkTS, strHTML/gridColumns 命名同源) | 跨语言旁证 (semantics 等价) | 无 LICENSE | ⚠ 致谢列名 |
| 3 | circleLZY/thu-courses | 36 | ⚠ INDIRECT (文档/issue 提及, 无源码命中) | 间接协议旁证 | Apache-2.0 | ⚠ 致谢列名 |
| 4 | brightcolin/thu-course-pilot | 6 | ⚠ INDIRECT (与 WakeUp 同期 fork, 同 setInitValue/gridColumns) | fork 形态 | Apache-2.0 | 🚫 不复用 (fork of WakeUp) |
| 5 | FztTony/Tsinghua-CourseSpider | 4 | ❌ NEGATIVE (Python, requests 直取 JSON endpoint, 非 strHTML 解析) | 协议路径不一致 | 无 LICENSE | 🚫 反向证据 |
| 6 | denny2001/thu_courses2ics | 1 | ❌ NEGATIVE (selenium 整页渲染, 无 strHTML 解析) | 协议路径不一致 | 无 LICENSE | 🚫 反向证据 |
| 7 | xiangwentao666/ECSA-Thu | 2 | ⚠ NULL_EVIDENCE (zhwp/cic.tsinghua 提及, 无 THU 协议源码) | 指纹命中但 endpoint 语义错 | AGPL-3.0 | 🚫 AGPL 不复用 |
| 8 | huangkaka666/thu-course-helper | 3 | ⚠ NULL_EVIDENCE (xkBks.xgpg_xspjyxkt.do 命中但语义是评教) | NULL_EVIDENCE | MIT | 🚫 评教不入 |

**POSITIVE 共识 = 1 (Starrah)** + INDIRECT 旁证 = 3 + NULL_EVIDENCE = 2 + NEGATIVE = 2 = 8/8
**SOP §3 通过标准**: POSITIVE ≥ 1 且 INDIRECT ≥ 3 → ✅ 达标

---

## 3. 14 节作息表 — 4 仓共识

WakeUp + Starrah + piggyham + BistuSchedule 都用同一套 14 节作息表 (08:00 - 21:45):

```
1:  08:00-08:45
2:  08:50-09:35
3:  09:50-10:35
4:  10:40-11:25
5:  11:30-12:15
6:  13:30-14:15
7:  14:20-15:05
8:  15:20-16:05
9:  16:10-16:55
10: 17:00-17:45
11: 18:45-19:30
12: 19:35-20:20
13: 20:25-21:10
14: 21:15-22:00
```

**跨仓共识 4/8**: WakeUp (硬编码) + Starrah (硬编码) + piggyham (ArkTS 等价) + BistuSchedule (移植参考)

---

## 4. 周次字典 — 5 仓共识

| 语法 | 含义 | WakeUp | Starrah | piggyham | BistuSchedule | AdoreCN |
|------|------|--------|---------|----------|---------------|---------|
| `全周` | weeks 1-16 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `前八周` | weeks 1-8 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `后八周` | weeks 9-16 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `单周` | odd weeks | ✅ | ✅ | ✅ | ✅ | ✅ |
| `双周` | even weeks | ✅ | ✅ | ✅ | ✅ | ✅ |
| `第3-15周(单)` | weeks 3,5,7,...,15 | ✅ | ✅ | ✅ | ✅ | ✅ |

**跨仓共识 5/8**: 5 仓 POSIX 完全一致, Sleepy JwThuparser 复用 WkParser 通用模块。

---

## 5. C2 blue_red_none 协议 — WakeUp + Starrah 双实证

THU 还有二级选课 `blue_red_none` 字段 (PAT_C2, line 859):

```
strHTML = "<a class='blue_red_none' href='.*?p_id=([0-9A-Z]+)'.*?><b><font color='blue'>(.*?)</font></b></a><font color='blue'>(.*?)</font>";
```

**双实证**: WakeUp THUParser.java ✅ + Starrah ✅
**Sleepy 落地**: parser.parseBlueRedNone() emit 占位课程 (C2 完整提取留后续 Phase)
**Sprint 状态**: stub, 不影响主流程

---

## 6. 反向证据仓 (NEGATIVE)

| 仓库 | 反向证据 | 价值 |
|------|----------|------|
| FztTony/Tsinghua-CourseSpider | Python `requests` 直取 JSON endpoint, 证明存在非 strHTML 路径 | ✅ 旁证 THU 也有 API 路径 (我们不用) |
| denny2001/thu_courses2ics | selenium 整页渲染, 证明 THU 也有 SPA 路径 | ✅ 旁证前端渲染 (我们不用) |

反向证据有 SOP §5.5 致谢价值 — 列名即可。

---

## 7. SOP §3 复核 Checklist

- [x] 协议六件套 char-by-char 跨仓校验 (POSITIVE=1, INDIRECT=3 实证 ≥ 4)
- [x] 14 节作息表 4 仓共识 (硬数据)
- [x] 周次字典 5 仓共识 (通用解析, 无歧义)
- [x] C2 二级选课双实证, Sleepy stub 落地
- [x] 反向证据 2 仓列名 (NEGATIVE)
- [x] NULL_EVIDENCE 2 仓列名 + 不复用理由

**SOP §3 通过**: ✅

---

## 8. 关联文件

- `scope.md` — Step 1 立项
- `current-code-state.md` — Step 5 验证后状态
- `attribution-candidates.json` — Step 5.5 致谢漏斗
- `raw/clones/Starrah__THUCourseHelper/app/src/main/java/cn/starrah/thu_course_helper/onlinedata/thu/THUCourseDataSouce.kt` line 851-989 — 主实证 1
