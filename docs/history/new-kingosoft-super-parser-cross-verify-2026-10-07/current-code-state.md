# New Kingosoft parser state (2026-10-13)

- Production type `TYPE_KINGO_NEW` is registered to `JwKingoParser` by `JwParserRegistry`; the existing TaskActivity path stays available after the HTML layouts fail.
- The HTML parser attempts `#mytable` weekday-cell blocks, then a format-two table whose `节次` cell includes both day and section, then `#kbDiv` table rows where row index is section and the final seven columns are weekdays.
- A plain HTML fragment with no `<head>` is valid. A malformed course block is skipped while later blocks remain eligible.
- `JwKingoZxParser`, introduced by #122, also handles positive-selection `#mytable` grids. It retains its explicit type and registry route; source-shaped super layouts have stronger structural confidence on generic selection.
- No authenticated school session was used. The layout contract comes from pinned OpenWakeUp source and local fixtures.
