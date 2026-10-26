# Protocol Matrix

| Dimension | KMUST capture | Current implementation |
|---|---|---|
| Entry | `https://i.kust.edu.cn/` | `kust` URL route |
| Term | `/api/uppcard/kbsz/queryAllTerm` | fetches term list before schedule |
| Schedule | `/api/uppcard/kbsz/queryAWeekSchedule` | `KUST_FETCH_JS` |
| Payload | `data.resultsJsonArr`, `data.zs`, `data.weekcount` | `JwKustParser` |
| Grid | repeated cells / section rows | deduplicate, then merge consecutive sections |
| Evidence | captured package hash in scope.md | fixture is redacted/synthetic |
