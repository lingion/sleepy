# Protocol Matrix

| Dimension | LIXIN capture | Current implementation |
|---|---|---|
| Entry | `/edu/lesson/std/timetable!courseTable.action` | classic EAMS route |
| Activity creation | `table0.newActivity(8 args)` | `JwClassicEamsParser.timeActivities()` |
| Placement | `table0.addActivityByTime(activity, day, startMinutes, endMinutes)` | time-to-node mapping |
| Weeks | numeric long bitmap | decoded via bit shifts |
| Framework | beangle 0.2.0 | no DOM parsing |
