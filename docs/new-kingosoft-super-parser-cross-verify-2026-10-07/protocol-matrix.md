# New Kingosoft HTML layout matrix

| Layout | Upstream OpenWakeUp@bc4f13e (KingoNewParser.kt) | Sleepy route | Negative guard |
|---|---|---|---|
| #mytable | lines 50-142: weekday by cell column, normal four-line or div1 xkinfo/NBSP three-line block | TYPE_KINGO_NEW, JwKingoParser | Missing timing skips block; subsequent block still imports |
| Format two | lines 147-219: thead 课程 + 节次; day and section share a 周X[3-4节] cell | TYPE_KINGO_NEW | Malformed row does not turn into default section |
| #kbDiv | lines 222-265: tbody row index = section, last seven td = weekdays | TYPE_KINGO_NEW | Fewer than seven cells rejected |
| TaskActivity | Existing Sleepy parser, independent historical Kingosoft references | TYPE_KINGO_NEW fallback | HTML parsing does not remove JS path |
| Positive selection report | Existing Sleepy #122 JwKingoZxParser, table#pageRpt and td.td grid | TYPE_KINGO_ZX | Explicit type remains intact |

The upstream helper splits concatenated documents by head boundaries as a tolerance mechanism. A head-less fragment is valid. No section-time literals are inferred from this HTML family.
