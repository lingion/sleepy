# Protocol matrix / provenance

| Form | Source repo and pinned SHA | DOM | Course fields | Schedule/room | Sleepy landing |
|---|---|---|---|---|---|
| 正选结果 report | `icepie/aischedule-lit-kingosoft` `e685fc6a77c6f9cd2e881a2f30119b89828acfe4` `src/parser.ts` | `table#pageRpt` second nested table, each `tr` one course | `td[1]` title after `]`, `td[4] a` teacher | `td[10]` text nodes: `[1-16周]星期一[5-6节]/XB309` or actual alternate code order `星期一 5-6节 1-16周/XB309` (source comment contradicts its executable indexing) | `JwKingoZxParser.parseReport` |
| Legacy weekday grid | `cqwu-ehall/AIShedule_cqwu` `497456ca88a8c7f9768de0f8f2600007e303e0aa` `scheduleHtmlParser.js` and `scheduleHtmlProvider.js` | `#mytable` in three nested same-origin iframes, `td.td` weekday cell, child `div` each course | 4 `<br>` fields: name, teacher, week/section, room | `5,7,9,13-16[5-6]` (source uses numeric split; no 节 suffix), `傍晚` maps node 9 | `parseGrid`; `FrameTraversalTree.ANCHORS` |
| Second grid confirmation | `web1n/XiaoAISchedule_hebust` `f5ae47b0ae980e27701db29c44ed5548541b6d52` `scheduleHtmlProvider.js`,`scheduleHtmlParser.js` | iframe → `#mytable` | 4 fields | weeks/section bracket grammar | `parseGrid` |

Historical schema verdicts and license/attribution are in `docs/history/wakeup-protocols-cross-verify-2026-09-15/findings/kingo-2.json`, `kingo-3.json`, `kingo2-4.json`, and `attribution-candidates.json`. All three source projects already appear in `LicenseScreen.kt`, six `strings.xml`, and `AboutLicenseAttributionTest.kt`; this repair did not touch new repositories. Fixture is a minimal synthetic projection of the pinned source grammar, not a real login capture.

Cross-language invariant: no matching Kingo regex exists in WebView JS; JS only captures DOM frames. Kotlin parser is sole grammar decoder, so a Kotlin/JS regex equality test would be artificial.
