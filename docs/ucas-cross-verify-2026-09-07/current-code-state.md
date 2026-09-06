# Current Code State — UCAS

- `TYPE_UCAS` is declared in `JwProtocol` and registered in `JwParserRegistry`.
- `xkgo.ucas.ac.cn/.../course/personSchedule` selects `ucas` before generic detection.
- `JwUcasParser` selects the schedule grid by header and course detail href, emits all occupied cells, then merges adjacent nodes with matching course/day/week attributes.
- The sanitized fixture comes from #18's captured HTML table. No raw user data is committed.
- Parser currently uses `startWeek=1`, `endWeek=16`, `type=0`: the supplied page contains no course-week data. This is a provisional rendering fallback, not an exact UCAS-week implementation.
- `sleepy-collector v1.2` now sends cross-origin course-detail links through controlled same-profile tab navigation and stores successful DOM in `4-detail-nav/`; it no longer uses page-context fetch or synthetic POST for those links.
- Completion is blocked on a fresh authenticated capture containing a successful detail response. Once available, the parser must decode exact week data and replace the provisional fallback.
