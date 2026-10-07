# New URP Super Parser — Cross-Verify Scope

**日期**: 2026-10-07
**目标**: 适配 WakeUp `NewUrpSuperParser` 的第二变体（obfuscated name `o0O0o`），对应 JSON 数组直返式 URP 系教务
**真源**: WakeUp smali 反编译 (反混淆 /NewUrpClassListItem 数据类)
**协议判定级别**: HIGH (URL/JSON 形态双重锚点)
**协议描述**: 教务平台返回完整 JSON 数组，每项 `NewUrpClassListItem` 包含 `kcm`(课名), `jsm`(教师), `id.skxq`(星期 1-7), `id.skjc`(起始节), `id.skzc`(周次位图字符串), `cxjc`(持续节次), `jxlm`(教学楼), `jasm`(教室)。周次位图按字符位置 1..N 索引。
**WakeUp 子协议顺序**: `NewUrpSuperParser` 串联 `o0` (dateList/selectCourseList/timeAndPlaceList 嵌套结构 — Sleepy 已支持 `JwNewUrpParser`) → `o0O0o` (本任务新增 flat array 变体)。
**Sleepy 现状**: `JwNewUrpParser` (TYPE_URP_NEW) 已覆盖 `o0` 变体；`o0O0o` flat array 变体无对应 parser。
**任务清单**:
1. ☐ GitHub 候选搜索
2. ☐ 文件级交叉验证
3. ☐ 协议矩阵 + 当前代码状态
4. ☐ 实现 JwNewUrpSuperParser (variants: flat-array bitmap)
6. ☐ 单元测试 + 跨仓测试 fixture
7. ☐ LicenseScreen + 6 strings.xml + AttributionTest 同步
8. ☐ 提 PR (NWPU Post 之后第二个)