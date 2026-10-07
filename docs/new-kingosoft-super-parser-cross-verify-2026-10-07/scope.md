# NewKingosoft Super Parser (新青果 super) — 跨仓验证立项

## 用户原话(2026-10-07)

> "近期完成的每一个都交叉验证之后,交叉验证之后,独立的开一个 pull request。
> 同时同步把没有适配的协议也做出来。
> 其他的协议按你自己电脑架构继续。"

WakeUp 8 个主 parser 中,已开 PR #119-#124 覆盖 6 个(THU/ECUPL 3 套/SCAU/KingoZX/NWPU-Post/NewUrpSuper),剩余 1 个主 parser **NewKingosoftSuperParser**(新青果 super 2026-10 升级版)未独立成 PR。

注: NewKingosoftSuperParser 在 `feat/wakeup-parity-2026-10` 分支(e3449680 + 8be532da)有过 3 链 HTML 降级实现,但该分支是聚合 PR,未独立成 PR。本次按 SOP 把 **3 链降级 + cross-repo 验证** 拆成独立 PR #125。

## 学校/适配目标

- **学校**: 新青果 super 协议族(多所学校共用,Kingo 升级版)
- **适配类型**: 协议升级 (WakeUp NewKingosoftSuperParser 反编译移植到 Sleepy JwKingoParser)
- **涉及现行 parser**: `JwKingoParser`(`TYPE_KINGO_NEW`,`app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt`)
- **目标**: 在 `JwKingoParser` 内部叠加 3 链 HTML 降级 (不新建独立 parser 类,延续 SOP "协议补丁 = 共享 + 宽松 + 跨语言 invariant" 原则)

## 协议特征 (从 smali 反编译)

`tools/reverse/wakeup-fresh/smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/NewKingosoftSuperParser.smali` (691 行):

3 链调度器:
- **链 1 (o0oOOo 老 div)**: `div.xkinfo` + `&nbsp;` 清理 + `<br>` 行分割 (INV-NBSP-CLEANUP)
- **链 2 (o00O0OO 标准表)**: `table` 配 `thead` 中文表头 (课程/学分/教师/地点/周次/节次/单双周) (INV-CN-HEADER-LEXICON)
- **链 3 (oo0oOO0 新版 div)**: `div.kbDiv` 布局 (新青果 super 2026-10 主入口)

8 个 regex_invariants 闸门 (SOP §6):
- INV-HTML-FRAG-SPLIT — `<head></head>` 切片 size ≥ 3
- INV-NBSP-CLEANUP — `&nbsp;` 串联塌缩空格
- INV-CN-HEADER-LEXICON — 课程/学分/教师/地点/周次/节次/单双周 词典命中
- INV-DAY-DICT — 周X / 星期X → 1-7 (含 日→天)
- INV-WEEKS-PARSE — 复用 parseWeekTokens (逗号 + 单/双周)
- INV-SECTIONS-RANGE — 第1-2节 / 3-4节 → [start, end]
- INV-NO-SECTION-TIME-MAP — super 协议族不使用 08:00/10:00 时间字面量, 不调 timeToNode()
- INV-ERROR-AGGREGATE — 错误列表聚合抛出 (全链降级失败时)

## 验收标准 (Sprint Contract)

1. `parseKingoSuperHtml(source, errors)` 函数正确处理 3 链降级 + 8 invariants
2. `JwKingoParser` 在 chain1/chain2/chain3 任一命中即返回, 全失败聚合错误抛出
3. 8 条单元测试全绿 (chain1 xkinfo, chain2 thead, chain3 kbDiv, 3 链降级, 错误聚合)
4. 致谢 5+ 仓进 `school-kingo-zx` PerSchoolEntry (附 N+ 仓 pool)
5. 6 语 `values{,-en,-ja,-es,-zh-rCN,-zh-rTW}/strings.xml` 同步新增
6. `AboutLicenseAttributionTest` 全 6 语绿
7. `./gradlew :app:testDebugUnitTest --tests '*JwKingoParser*' --tests '*AboutLicenseAttribution*'` BUILD SUCCESSFUL
8. commit 尾注无 Co-Authored-By, body 引用 `ref #118` (无 Fixes/Closes/Resolves)

## 下一步

→ Step 2 — GitHub 多源检索 (5+ 候选仓)
