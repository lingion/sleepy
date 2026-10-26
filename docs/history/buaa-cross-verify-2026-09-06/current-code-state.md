# Current Code State — BUAA

- Entry: `JwWebViewLoginScreen` captures the authenticated WebView page; `JwImportViewModel` performs URL/HTML protocol detection.
- Protocol: `qz_ieas` is declared, registered, and selected before generic `/kbcx/` zf markers.
- Fetch: iEAS is server-rendered HTML and uses outerHTML capture, not zf JSON fetch.
- Parser: `JwQzIeasParser` parses `table#queryGrkb` rows, data attributes or five text columns, weekdays, nodes, ranges, discrete weeks, and parity.
- Fixture: `app/src/test/resources/jw/fixtures/qz_ieas/query-grkb.sample.html` is synthetic and redistributable because upstream exposes the endpoint but no real timetable HTML.
- Tests: parser, fetch, detection, registry, school-list, and attribution contracts are covered.
