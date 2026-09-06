# 安徽大学 (Anhui University, AHU) 教务适配跨仓验证

> 立项日: 2026-09-06
> 适配版本基线: v1.0.50 (Release, versionCode 51)
> 触发来源: issue #17 (OTUT9, 2026-09-06 08:46 UTC)

## 用户原话

> 安徽大学的课表抓取失败
> 显示：抓取失败：Error: POST schedule-table/datum 失败 HTTP500

## 适配类型

**初次适配 + 协议族路径扩展**。schools.json 已有 `ahu` 条目, 协议族归类为
`JwProtocol.TYPE_EAMS5`, URL `https://jw.ahu.edu.cn/student/for-std/course-table`
(短前缀 supwisdom 形态, 与矿大北京同型, 由 `Eams5PathPrefix.fromSchoolUrl`
推断)。

**问题域**: 用户报 `POST schedule-table/datum` 返回 HTTP500。
现有 v1.0.50 协议补丁 (commit 3a3993d) 已涵盖:

- studentId 6 形态 HTML 正则 (Eams5StudentIdExtractionTest 全覆盖)
- Eams5PathPrefix 学校 → 前缀推断 (Eams5PathPrefixTest 全覆盖)
- /login 302 登录态检测 (isEams5LoginRedirect)
- 跨语言 invariant (JS regex literal ≡ Kotlin EAMS5_STUDENT_ID_REGEX)

**但 v1 简化** (JwEams5Parser.kt:23 + EAMS5_FETCH_JS:861): 直接 POST schedule-table/datum
时 `body={"lessonIds":[], "studentId":..., "weekIndex":""}` —— lessonIds 数组置空,
依赖上游对空数组回退返全量。**HFUT (合工大) 形态接受该简化, 安大可能不接受**,
导致 500。

## 涉及现行 parser / 文件清单(待修范围候选)

| 路径 | 当前形态 | 待评估 |
|------|----------|--------|
| `app/src/main/java/com/lingion/sleepy/data/jw/Eams5PathPrefix.kt` | `/student` 已推断 ahu | 无需改 |
| `app/src/main/java/com/lingion/sleepy/data/jw/JwEams5Parser.kt` | schedule-table/datum JSON 解析器, 字段映射稳定 | 必要时扩展 |
| `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt:814-883` | `EAMS5_FETCH_JS` 三段 fetch (course-table → schedule-table/datum), **lessonIds=[] 是 v1 简化** | **可能需要扩为 4 段 (含 get-data?bizTypeId=23)** |
| `app/src/test/java/com/lingion/sleepy/data/jw/Eams5StudentIdExtractionTest.kt` | 6 形态学号 + invariant 测试 | 无需改 |
| `app/src/test/java/com/lingion/sleepy/data/jw/Eams5PathPrefixTest.kt` | hfut/cumtb/ahu URL 推断测试 | 无需改 |
| `app/src/main/assets/schools.json` | 已有 ahu 条目 | **待验证是否需要新加 marker** |
| `app/src/test/resources/jw/schools.json` | 同上, 测试副本 | 同上 |

## 历史 commit (本次适配前)

```
3a3993d hfut EAMS5 studentId regex + 登录态检测 (跨仓验证)
<待查: schools.json 收录 ahu 的原始 commit>
```

## 期望产物

1. `candidates.json` —— Step 2 GitHub 全量检索结果
2. `findings.json` —— Step 3 派单 schema 化 verdict 数组
3. `protocol-matrix.md` —— Step 4 横向对比表
4. `current-code-state.md` —— Step 5 现有代码状态(本文文件基础上扩展)
5. `attribution-candidates.json` —— Step 5.5 致谢清单

待 Step 6 修复设计 / Step 7 fixture / Step 8 全测试 / Step 9 commit 落地。
