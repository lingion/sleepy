# DOM and guard matrix

| Shape | Source evidence | Sleepy handling |
|---|---|---|
| .el-table__body-wrapper tbody | QZParser.java parse; sample2021-2.html | TYPE_JZ via WakeUpCompat.parse -> parseJzSuper |
| First td section label + seven weekday td + optional gutter td | sample2021-2.html | Direct row children, columns 1..7; gutter ignored |
| position: relative course wrapper with eight direct div fields | sample2021-2.html | Select style-normalized wrapper, direct child offsets 3/4/6/7 |
| Explicit section or row label | QZParser.java getSectionStart/getSectionContinue; sample | Validate explicit ranges; use row-label range for omitted section |
| Week list in parentheses | QZParser.java getWeeksList | Strict local range/list/parity tokens; malformed course skipped |

Source bugs corrected deliberately: original Java uses nine-element grouping against an eight-div sample, strips two characters after any star, assumes range has two numbers, and parses integers without row isolation. The port does not repeat these failure modes.
