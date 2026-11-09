# Timetable import contracts

An imported course must preserve its name, teacher, room, weekday, teaching weeks and periods. A parser returning the expected number of rows is insufficient: a single source course can contain several arrangements, and two rows can describe the same physical occurrence.

The regression tests expand each result into individual teaching weeks and periods. They compare all arrangement fields and preserve separate teachers, rooms and explicit clock times.

## Existing school routes

| Route | Contract | Regression tests |
| --- | --- | --- |
| Kingosoft | Keep compact grid positions, teaching arrangement fields, parenthesized course names and independent teachers and rooms. | `JwKingoGridOccurrenceTest`, `JwKingoCompactOccurrenceTest`, `JwKingoTdRoutingTest` |
| Classic EAMS | Decode JavaScript strings, retain all day and period coordinates, and preserve sparse week bitmaps. | `JwClassicEamsOccurrenceTest`, `JwClassicEamsInputIntegrityTest`, `JwNbccOccurrenceParityTest`, `JwProtocolOccurrenceParityTest` |
| QZ | Treat titled fields as authoritative, preserve explicit sparse periods and reject contradictory coordinates. | `JwQzOccurrenceParityTest`, `JwQzSchoolRowMappingTest` |
| URP | Recognize both short and long headers, follow physical rowspans, decode explicit Chinese period labels and reject unknown weeks. | `JwLegacyUrpXjuOccurrenceTest`, `JwNewUrpOccurrenceTest` |
| XJU postgraduate | Read each bracketed arrangement independently, retain its parity and reject invalid spans without generating a default semester. | `JwLegacyUrpXjuOccurrenceTest`, `XjuPostgraduateAdmissionTest` |
| UCAS | Match detail pages by course identity; fail an incomplete detail capture instead of importing placeholder weeks. | `JwUcasOccurrenceTest`, `UcasDetailFetchIntegrityTest` |

The NBCC and Nankai regression fixtures exercise their documented EAMS layouts. Their expected occurrences are independent of the parser implementation. These offline tests do not establish that an authenticated import was performed during this change.

## Explicit school layouts

Declared parsers use their own week and row conventions. An empty result from an explicit layout must not fall through to an unrelated parser that can manufacture a plausible schedule.

The additional declared layouts include SIMC, RUC, SDU, SEIG, Kingosoft variants, BFA, FDU, ZTVTIT, HUST, AIC, HBMZU, NUA, STUM, AHMU, GDEI, JLU postgraduate, CCIBE, CPPU, SHCC, FSTVC, GDBH, GLIET, CNU, Xbellsoft, CDUT, GZHU postgraduate, DHU, ECJTU, ZPTC, CTGU, NYIST and JXAU.

Important layout boundaries are covered explicitly:

- CTGU takes the final field as the teacher, even when it is empty. Explanatory fields do not establish a second course. Ambiguous mixed containers are rejected.
- JXAU preserves empty period cells and skips only its two explicit separator columns. Each `dl` keeps its own fields.
- NYIST preserves explicit clock times and independent week parities. Missing time does not create a course in week nineteen.
- Xbellsoft combines overlapping weeks only for otherwise identical arrangements. A different teacher or room remains separate.
- UMOOC excludes weeks through set subtraction. Share payloads combine duplicate occurrences without filling missing weeks.

Parser registration establishes that Sleepy can interpret that layout. It does not prove the current school's login flow uses it. Existing school entries retain their verification status, and private, redacted or unknown URLs are not promoted based on parser tests.

## Capture and persistence

CF captures use the page's explicit week options, including sparse and late weeks. Every requested week must have exactly one response bucket; an empty final week still counts. Missing, duplicate, unexpected or malformed buckets reject the capture. Historical envelopes without an explicit request list retain their compatibility path.

Paged school captures require all pages and validate completion before returning results. UCAS detail failures and collector failures remain visible to the import flow.

Source clock times, calendar dates and term length survive the editable import draft and entity conversion. Captured period times take precedence over source defaults. School-specific defaults remain scoped to the appropriate school and protocol.

Confirmation rows cover both the highest course period and the highest source period. Unused source periods remain available in the draft and imported timetable; missing clock values stay empty for manual confirmation.

NUIST row labels with either `~` or `～` declare two periods per row. When a course omits its packed period code, later rows keep their corresponding period pairs.

NWPU graduate tables retain the selected semester name and thirteen school periods. Campus defaults count each course row's time cell once, with Chang'an taking ties. Youyi uses the May through September clock in those months and the winter clock otherwise. A missing semester selection does not create a calendar origin or a placeholder semester name.

## Verification

Run the Android regression suite and lint:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug --max-workers=1 --console=plain
```

The WebView capture contracts run the production JavaScript against offline DOM and HTTP responses:

```sh
for contract in tools/test-*.js; do node "$contract"; done
```

These tests cover parsing, request parameters, pagination, response validation, timeout and error propagation. They do not replace authenticated account tests or execution in a device WebView. School verification labels must reflect that distinction.
