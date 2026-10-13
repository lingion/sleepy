# Code state

`JwScauParser` routes Element UI before the two WakeUp border-grid shapes. It keeps original cell positions, skips header rows when numbering border-grid course rows, parses parity only from week text, and preserves gaps in week lists. `JwParserRegistry` registers `TYPE_SCAU` for import. Tests exercise the pinned Element UI layout, positive border-grid fixtures, and negative cases for missing weeks, skipped columns, headers, and the word `单片机`.

Production directory currently declares SCAU as `qz` at `http://jwxtxs.scau.edu.cn/default2.aspx`; this PR does not change the advertised login URL without a live account. The pinned Element UI test goes through `JwImportViewModel.parseHtmlStatic(html, TYPE_QZ)` to check actual fallback routing rather than registration alone. Generic WebView frame capture already recognizes `el-table__body`; these tests verify captured HTML import, not live login or portal availability.
