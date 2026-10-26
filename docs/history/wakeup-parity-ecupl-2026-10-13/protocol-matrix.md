# Protocol matrix

| Shape | Schedule coordinate | Week source | Route | Reject when |
| --- | --- | --- | --- | --- |
| listTable alone | none | metadata note only | ECUPL main | always no courses |
| TaskActivity/index + marshalTable + listTable | D*unitCount+P | TaskActivity bitmap | classic EAMS delegate | no index or invalid bitmap |
| CourseTable + eight-argument newActivity + addActivityByTime | exact minute matrix | decimal Long, offset from date | ECUPL script | unmatched minutes, invalid date/bitmap |
| Ordinary classic EAMS | TaskActivity/index | ordinary bitmap | classic EAMS | unchanged |

Synthetic fixtures in app/src/test/resources/jw encode these observed shapes; they are not authenticated captures.
