# Protocol Matrix

| Dimension | NUIT capture | Current implementation |
|---|---|---|
| Entry | `/jwapp/sys/homeapp/home/index.html` | `nuit` URL route |
| Term | `currentUser.do` -> `welcomeInfo.xnxqdm` | `NUIT_FETCH_JS` |
| Schedule | `student/courses.do?termCode=...` | `NUIT_FETCH_JS` |
| Payload | `datas[].courseName` and `classDateAndPlace` | `JwNuitParser` |
| Schedule text | week range, Chinese weekday, section range, teacher, room | split and normalize |
| Evidence | captured package hash in scope.md | fixture is redacted/synthetic |
