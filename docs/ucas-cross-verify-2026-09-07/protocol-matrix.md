# Protocol Matrix — UCAS

| Dimension | Evidence |
|---|---|
| Schedule page | `GET xkgo.ucas.ac.cn:3000/course/personSchedule` returns HTTP 200 and server-rendered HTML |
| Grid | table header `节次/星期` plus Monday–Sunday columns and rows 1–13 |
| Course identity | `a[href*=/course/coursetime/]` text is the course name |
| Detail endpoint | `xkcts.ucas.ac.cn:8443/course/coursetime/<id>` |
| Existing detail capture | page-context cross-origin GET/POST failed; synthetic replay generated OPTIONS 403 and did not provide details |
| Revised collection route | collector v1.2 opens each discovered cross-origin course-detail link as a same-profile new-tab navigation and stores successful DOM under `4-detail-nav/` |
| Exact-week source | upstream `selectedCourse.json` exposes `courseTimeList[].courseWeek` bitmap; a fresh authenticated capture must verify the live field/detail representation |
| Current parser contract | grid determines name/day/node; adjacent cells merge; weekly range defaults to 1–16 only until exact detail evidence is captured |

The issue attachment is the primary protocol evidence. `ldiex/UCAS_Course_Schedule_Convertor` independently confirms a 13-section schedule model and individual course-time data.
