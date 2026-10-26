# Protocol matrix

| Shape | Container | Course fields | Day and periods | Week evidence |
| --- | --- | --- | --- | --- |
| Element UI SCAU | `.el-table__body-wrapper table.el-table__body` | nested `div` indexes 3 name, 4 teacher, 6 week text, 7 room | first `td` period; next seven cells weekdays; six row defaults 1–2, 3–5, 6–7, 8–9, 10–12, 13–15 | greyovo pinned HTML and `QZParser.java` |
| WakeUp border grid | `table[border=1]` without bordercolor | `<br>` lines name, teacher, weeks, room | original `td` position and course-row count | local `o00OOO00.smali` |
| WakeUp old border grid | `table[border=1][bordercolor=#000000]` | first line `name：teacher`, then weeks and room | section/data row pair | local `SCAUParser.smali` |

A table with `bordercolor` also satisfies `border=1`; the old grid is excluded from the primary branch. Filtered cells retain their original column indices. Week lists produce distinct runs, so `1-4,6-8周` never claims week 5. Invalid or absent weeks produce no course.
