# Protocol Matrix

| Dimension | NWUPL capture | Current implementation |
|---|---|---|
| Entry | `/eams/courseTableForStd.action` | classic EAMS route |
| Activity | `new TaskActivity(...)` | `JwClassicEamsParser` |
| Placement | `index = day * unitCount + period` | page-derived `unitCount` |
| Weeks | binary week bitmap | one normalized course per active week |
| Evidence | captured package hash in scope.md | fixture is redacted/synthetic |
