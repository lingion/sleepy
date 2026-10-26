# BJTU 跨仓验证 — 致谢清单 (Step 5.5, 与 candidates.json 24 候选 1:1)

> 日期: 2026-09-09 · 规则: 检索矩阵触达即致谢, 不论 stars/年代/verdict (含 NULL_EVIDENCE/NEGATIVE/FETCH_FAILED, 宁可错谢不可放过)
> 落地: LicenseScreen.kt perSchoolEntries["北京交通大学 BJTU"] + 6 语 strings.xml + AboutLicenseAttributionTest (1:1 断言)

| id | 候选仓库 | 语言 | License | verdict | 致谢条目 (strings.xml 原文) |
|----|----------|------|---------|---------|------------------------------|
| 1 | wan300/bjtu_mis_Android | Svelte | MIT | POSITIVE | bjtu_mis_Android (wan300, MIT) |
| 2 | Anyes666/BJTU-MIS-HarmonyOS | TypeScript | MIT | POSITIVE | BJTU-MIS-HarmonyOS (Anyes666, MIT) |
| 3 | HFDLYS/BJTUselfService | Kotlin | MIT | POSITIVE | BJTUselfService (HFDLYS, MIT) |
| 4 | fish2lab/bjtu-cli | Swift | - | POSITIVE | bjtu-cli (fish2lab) |
| 5 | fish2lab/BJTUselfService-macOS | Swift | - | POSITIVE | BJTUselfService-macOS (fish2lab) |
| 6 | s1y4x1/BJTU-course-assistant | JavaScript | - | POSITIVE | BJTU-course-assistant (s1y4x1) |
| 7 | ZiuChen/userscript | JavaScript | MIT | POSITIVE | ZiuChen/userscript (MIT) |
| 8 | ymzhang-cs/BJTU-iCalendar-Generator | Python | MIT | INDIRECT | BJTU-iCalendar-Generator (ymzhang-cs, MIT) |
| 9 | Moliseeee/bjtu-timetable | Python | - | NEGATIVE | bjtu-timetable (Moliseeee) |
| 10 | hyskr/BJTU-course-autoget-program | JavaScript | - | POSITIVE | BJTU-course-autoget-program (hyskr) |
| 11 | 57Darling02/BjtuCoursePlatform | JavaScript | - | INDIRECT | BjtuCoursePlatform (57Darling02) |
| 12 | jlytwhx/bjtuDean | Python | MIT | INDIRECT | bjtuDean (jlytwhx, MIT) |
| 13 | jlytwhx/bjtubox_python | Python | - | POSITIVE | bjtubox_python (jlytwhx) |
| 14 | Orien233/Campus-Mate | Kotlin | - | POSITIVE | Campus-Mate (Orien233) |
| 15 | ymzhang-cs/BJTU-STU-MCP | Python | - | POSITIVE | BJTU-STU-MCP (ymzhang-cs) |
| 16 | xschur/CourseRobber | Python | - | POSITIVE | CourseRobber (xschur) |
| 17 | Futuremind-BJTU/Futuremind-BJTU | - | - | NULL_EVIDENCE | Futuremind-BJTU |
| 18 | Yukikasu/BJTU_ezRate | JavaScript | MIT | INDIRECT | BJTU_ezRate (Yukikasu, MIT) |
| 19 | xxxand/bjtu_teaching_assessment | JavaScript | MIT | INDIRECT | bjtu_teaching_assessment (xxxand, MIT) |
| 20 | Coconut00/BJTU-script | Python | - | NULL_EVIDENCE | BJTU-script (Coconut00) |
| 21 | aooxin/BJTU-CC | Python | - | INDIRECT | BJTU-CC (aooxin) |
| 22 | etherealviator/CourseTable | TypeScript | MIT | INDIRECT | CourseTable (etherealviator, MIT) |
| 23 | mcdona1d/ZF-Assistant | Python | - | NEGATIVE | ZF-Assistant (mcdona1d) |
| 24 | greasyfork.org:430918 北交大iCalender课表生成 | JavaScript (userscript) | - | FETCH_FAILED | Greasy Fork 430918 北交大iCalender课表生成。 |

## 1:1 校验

- candidates.json 候选数 = 24 = 本表行数 = strings.xml BJTU 段分号条目数 (生成脚本已断言)
- 每个 GitHub 候选的致谢条目含其 owner/repo token; id24 greasyfork 以来源名致谢
- 复用纪律: 下次触达 BJTU 相关仓库时, 先补本表再动代码
