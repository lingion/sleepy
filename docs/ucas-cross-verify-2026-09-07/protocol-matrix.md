# Protocol Matrix — UCAS

| Dimension | Evidence |
|---|---|
| Schedule page | `GET xkgo.ucas.ac.cn:3000/course/personSchedule` returns HTTP 200 and server-rendered HTML |
| Grid | table header `节次/星期` plus Monday–Sunday columns and rows 1–13 |
| Course identity | `a[href*=/course/coursetime/]` text is the course name |
| Detail endpoint | `xkcts.ucas.ac.cn:8443/course/coursetime/<id>` |
| Detail capture | cross-origin GET/POST failed; replay OPTIONS returned 403 |
| Parser contract | grid determines name/day/node; adjacent cells merge; weekly range defaults to 1–16 pending a detail capture |

The issue attachment is the primary protocol evidence. `ldiex/UCAS_Course_Schedule_Convertor` independently confirms a 13-section schedule model and individual course-time data.
