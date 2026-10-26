# Current Code State

- `JwClassicEamsParser` handles the captured `newActivity`/`addActivityByTime` form.
- The parser maps timetable absolute-minute ranges to normalized section nodes and expands the numeric week bitmap.
- The existing WebView capture path is reused; no separate endpoint fetch is required.
- Evidence boundary: the committed fixture is redacted/synthetic and is not presented as a live capture.
