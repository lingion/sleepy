# NEU Protocol Matrix

| Dimension | NEU observed form | Sleepy implementation | Evidence |
|---|---|---|---|
| WebView endpoint | `GET /jwapp/sys/home/student/getMyScheduleDetail.do` | Dedicated `NEU_FETCH_JS`; cookies sent with `credentials:'include'` | `JwWebViewLoginScreen.kt`, `JwNeuWebViewContractTest` |
| Response envelope | Raw JSON response | `{ok:true,data:<raw>,periods:[]}` through `__sleepyBridge.onWiseduResult` | `JwNeuWebViewContractTest` |
| Course array | `datas.arrangedList[]` | `JwNeuParser` reads `courseName`, `dayOfWeek`, `beginSection`, `endSection` | `JwNeuParser.kt`, `JwNeuParserTest.kt` |
| Teacher | `weeksAndTeachers` slash-delimited suffix, optional `[主讲]` | Last segment, marker removed | `JwNeuParser.extractTeacher` |
| Room | `titleDetail[1..]` entries containing weeks and room | Last token of the first usable detail entry; fallback is empty | `JwNeuParser.extractWeeksAndRoom` |
| Weeks/parity | Range/list with Chinese single/double-week markers | Parsed to ranges; `type=1/2` retained and endpoints normalized | `JwNeuParser.extractParity`, `parseWeeks` |
| ICS section mapping | NEU export uses custom PRODID and may omit explicit section description | NEU-specific time inference maps actual time blocks; explicit `第X-Y节` remains authoritative | `ScheduleParser.parseIcs`, `NeuRealFixtureImportTest` |
| Round-trip | NEU ICS -> Sleepy ICS -> parser | Course count, day/node/step, week span, and node window are regression checked | `NeuRoundTripIcsTest` |

The six reference repositories are recorded in `candidates.json` and were already acknowledged in the app attribution surface.
