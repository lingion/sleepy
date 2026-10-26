# SEU gateway protocol matrix (2026-10-13)

| Claim | Pinned evidence | Implementation | Confidence |
|---|---|---|---|
| Student timetable gateway | Haa1024/SEU-Timetable@d69ae1d EhallClient.kt; HeraldStudio@29180c4 curriculum.js:93-111; 131AIClub@c933e81 provider.js:4-35; classduck@4f2b370 csf_dongnandaxue_provider.js:4-75 | ehall.seu.edu.cn/jwapp/sys/wdkb/modules/xskcb/xskcb.do | Protocol supported by four pinned sources, one updated September 2026 |
| Term code | 131AIClub provider.js:8-15 | #dqxnxq2, including accessible same-origin iframe | Source-backed plus local contract |
| Data shape | All three direct xskcb sources access datas.xskcb.rows | Require array, allow empty array, reject HTML/malformed JSON | Source-backed plus local contract |
| Week bitmap | HeraldStudio and classduck use SKZC; classduck also ZCMC | Valid bitmap first; malformed bitmap falls back to text | Source-backed plus local parser tests |
| OFRabby/seu-routine-maker | OFRabby@2f2f0ec seu_routine_maker.py converts pasted UMS text to PDF | Excluded from jwapp protocol claim | Negative |
| Alternative jwgl.seu.edu.cn/xsglapp route | Uncommitted research note without raw capture | Not assumed; requires captured request/response | Unverified |
| Related alternate bykb route | WheretoSleepinNJU/NJU-Class-Shedule-Flutter@25125ef api/tools/seubksjw.js:4-9,53-59 | Different cxxszhxqkb shape; not substituted into xskcb parser | Corroborates portal family only |

No authenticated live school session was run. These sources establish a documented gateway, including a September 2026 codebase, but not whether every student account still reaches it in October 2026.
