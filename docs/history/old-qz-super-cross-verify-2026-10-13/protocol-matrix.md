# Protocol matrix

| Form | Evidence | Container | Fields | Time / day | Failure behavior |
|---|---|---|---|---|---|
| base / compressed nodes | BUPT/ThaiCao/TableAPK/Java-Demo OldQzParser; WakeUp old_qz OooO0OO | kbtable | name, teacher, week[node], room | header day, otherwise 8-cell row; 0102 becomes 1-2 | invalid range skips record |
| CCUT | WakeUp_SHU dex and local old_qz OooO00o | kbtable, group-day div id | name at week-3, teacher week-1, room week+1 | group id maps two nodes; sparse weeks split | no valid id means unsupported |
| JLICT | OpenWakeUp JlictQzOldParser; local old_qz OooO0O0 | kb > table, a title | lines 3/4/6/7 = name/teacher/weeks/room | explicit documented eight-column table; data row two-node group | short title rejected |
| CCSU | OpenWakeUp CcsuQzOldParser | id group-day-2 | five lines: name/class/teacher/weeks/room | id directly establishes day and node group | short records rejected |

All use already-rendered DOM. No new network endpoint, credential, student-id extraction, captcha bypass or login algorithm is asserted. Existing frame traversal and login/session diagnostics remain in place. JLICT/fixed IDs have a structural frame anchor validated by parser output; arbitrary id=kb is insufficient. No duplicated JS regex: time parsing only exists in Kotlin.

Hardening: CCSU requires all 35 documented fixed IDs before treating arbitrary ID-bearing divs as course data. CCUT id weekday must agree with table column/header. Parity is scoped to each comma-delimited range; unmarked ranges are every week (no suffix leakage).
