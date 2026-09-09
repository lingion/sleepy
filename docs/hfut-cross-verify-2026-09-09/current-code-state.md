# current-code-state - HFUT issue #25 (2026-09-09)

## Before fix
- detectProtocolFromUrlImpl: ZERO eams5 anchors. Even the correct jxglstu course-table
  URL returned null and fell to the generic capture path (would yield 0 courses).
- SchoolSelectScreen UrlDirectRow: any unmatched URL became a custom entry with type=null;
  WebView opened the typed URL directly (e.g. portal) and generic capture found nothing.
- EAMS5_FETCH_JS: host guard = indexOf('hfut.edu.cn') containment, which the portal host
  one.hfut.edu.cn passes; staying on the portal and tapping import led to a cross-domain
  fetch failure with a confusing error message.

## After fix
- Fix A (JwImportViewModel.detectProtocolFromUrlImpl): added the eams5 anchor block after
  the WISEDU branch: jxglstu / /eams5-student / /for-std/ (slash-delimited, boundary regex
  .*/for-std(/|$) so forum-standard cannot hit) / jw.ahu.edu.cn / jwxt.cumtb.edu.cn.
  WebVPN branch sits earlier, so rewritten URLs still cannot be fingerprinted by host.
- Fix B (new SchoolDomainMatch object + SchoolSelectScreen wiring): typed URL whose
  registrable domain equals a supported catalog entry's domain resolves to that entry
  (authoritative jxglstu URL + eams5 type). WebVPN hosts excluded. UrlDirectRow now shows
  the matched school name (new string url_match_school in all 6 locales) and onClick uses
  the catalog entry instead of creating a type=null custom entry.
- registrableDomain single-source: SchoolDomainMatch.registrableDomain carries the same
  heuristic as SslBypassRegistry (edu.cn-style multi-suffix -> last 3 segments).
- Fix C (EAMS5_FETCH_JS): explicit one.hfut.edu.cn branch before the weak guard, returning
  a bridge error that names the portal and tells the user to open the HFUT entry
  (jxglstu) from the school catalog.

## Regression coverage added (27 tests, all green)
- HfutIssue25UrlEntryTest (12): eams5 URL anchors (jxglstu host, bare host, AHU/CUMTB
  short prefixes, for-std path forms), negatives (portal URL is not an eams5 anchor,
  forum-standard path, WebVPN rewrites stay invisible), portal home fixture (0 protocol
  fingerprints, 0 courses via all parsers), portal course-timetable JSON (not parseable
  as EAMS5, real field names preserved).
- SchoolDomainMatchTest (10): portal->catalog mapping for HFUT and AHU, subdomain forms,
  WebVPN exclusion, pending/no-URL entries excluded, unknown domain, blank host,
  registrableDomain heuristic equivalence with the SSL registry semantics.
- HfutPortalEams5WebViewContractTest (5): EAMS5_FETCH_JS explicitly rejects
  one.hfut.edu.cn; rejection message mentions jxglstu; existing guards (hfut/ahu/cumtb/
  jxglstu) intact; JVM EAMS5_STUDENT_ID_REGEX and the JS literal remain character-identical.

## Fixtures (desensitized)
- hfut-portal-home-issue25.sample.html: synthesized SPA shell, zero fingerprint literals
  anywhere (an earlier draft listed them in an HTML comment and the BNUZ fingerprint fired
  on the comment itself - caught by the red phase).
- hfut-portal-course-timetable-issue25.sample.json: real field names kept (14 fields),
  student id -> 2024xxxxxx, teacher names/ids replaced.

## Verification
- ./gradlew :app:testDebugUnitTest: 1297 tests, 0 failures, 0 errors, 0 skipped
  (baseline 1270 + 27 new).
- Red phase evidence: 27 new tests ran with 7 failures, all root-cause patterns
  (6 URL-anchor + 1 portal-host rejection), before any production change was applied.
- ./gradlew :app:lintDebug: build aborts on 35 pre-existing errors
  (32 MissingTranslation on keys not touched here, 1 MissingPrefix widget_scroll_clip.xml,
  1 NewApi HighRefreshRate.kt, 1 ByteOrderMark ScheduleParser.kt).
  None reference any file or key changed in this issue (url_match_school: 0 lint mentions);
  zero new lint findings.
