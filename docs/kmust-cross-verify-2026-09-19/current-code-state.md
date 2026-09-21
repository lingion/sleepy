# Current Code State

- `JwKustParser` consumes `data.resultsJsonArr` and `data.zs`.
- Repeated grid cells are deduplicated by day, section, and course identity.
- Consecutive section rows are merged into one normalized `JwCourse`.
- `KUST_FETCH_JS` obtains the term list and then requests the weekly schedule.
- The committed fixture is redacted/synthetic; it is not a live capture.
