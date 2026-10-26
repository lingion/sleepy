# protocol-matrix - HFUT issue #25 (2026-09-09)

## Where the reporter was vs where the app expected

| Aspect | Portal one.hfut.edu.cn (reporter reality) | EAMS5 jxglstu.hfut.edu.cn (app expectation) |
|---|---|---|
| Data source | Portal slice API /api/operation/course-timetable/search/1/<date> | /eams5-student/for-std/course-table + /ws/schedule-table/datum |
| Scope | current week + next week only (2-week slice) | full semester |
| Response shape | {code, msg, data.nextWeek[], data.currentWeek[]} | {result.lessonList[], result.scheduleList[], result.scheduleGroupList[]} |
| Fields | cxjc/skrq/skjc/jsxm/xh/jxbdm/kch/dayOfWeek/skbm/kclx/kxh/jsgh/kcmc/jxdd | lessonId/weekday/weekIndex/startTime/periods/personName/room.nameZh |
| Auth | portal CAS session only | EAMS5 session on jxglstu domain |
| Usable as import source | NO - two-week display slice, no semester/week-index semantics | YES - already locked by fixtures |

## Protocol conclusion
The EAMS5 protocol itself has NO divergence: the reporter never reached it.
The portal slice API must NOT be phantom-parsed as EAMS5 (locked by
HfutIssue25UrlEntryTest.portal course-timetable json test: JwEams5Parser yields 0 courses on it).

## WebView layer conclusion
EAMS5_FETCH_JS host guard used weak containment: indexOf('hfut.edu.cn') >= 0 lets
one.hfut.edu.cn through. Fixed with an explicit portal-host branch that guides the user
to the jxglstu entry via the catalog.
