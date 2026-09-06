# Current Code State — UCAS

- `TYPE_UCAS` is declared in `JwProtocol` and registered in `JwParserRegistry`.
- `xkgo.ucas.ac.cn/.../course/personSchedule` selects `ucas` before generic detection.
- `JwUcasParser` selects the schedule grid by header and course detail href, emits all occupied cells, then merges adjacent nodes with matching course/day/week attributes.
- The sanitized fixture comes from #18's captured HTML table. No raw user data is committed.
- Parser uses `startWeek=1`, `endWeek=16`, `type=0`: the supplied page contains no course-week data and its detail requests were blocked by CORS.
