# Academic-system URL evidence audit

Frozen review date: 2026-10-15. Scope: 519 initially operational catalog records (350 baseline plus 169 imported records), representing 517 distinct URLs.

## Current result

- **311 verified**: archived evidence proves this school's configured endpoint under the strict rules below.
- **207 unknown**: exact configured endpoint ownership is not proven. This includes 151 baseline and 56 imported records. All 207 retain machine-readable research traces.
- **1 contradicted**: 潍坊护理职业学院's configured sdwfvc.cn entry belongs to 潍坊职业学院. The incorrect entry must never be used; its separately proven nursing-college correction is admitted as unverified with fetch disabled.

The machine-readable source is [`scripts/data/school_url_evidence.json`](../../scripts/data/school_url_evidence.json). A verified public ownership result does not prove an authenticated timetable import, current availability, or long-term uptime. No credentials or authenticated student data were used.

## Exact endpoint policy

The configured and cited URLs must match scheme, hostname, effective port, path, query, and fragment. An absent root slash and an explicit default port are equivalent. Other trailing-slash, scheme, path, query, and fragment differences require evidence. Same hostname, generic login/SSO, parent directory, academic application prefix, and root links no longer imply tenant ownership.

Official published-address quotations must contain the full endpoint, not merely a hostname/IP substring. Whitespace is not silently removed, and shared-host tenant/query values are not discarded. Official-link and published-address sources apply the same strict endpoint comparison. Source identity remains exact for campuses and graduate entries; recorded institutional renames must be explicit.

A differing cited URL is accepted only when an archived directed HTTP redirect chain reaches the exact configured endpoint. Each capture records its archive reference, date, requested URL, body SHA-256, and continuous 301/302/303/307/308 hops. Two tenants ending on the same generic SSO page are not equivalent. Current retained redirect exceptions are 中国科学技术大学 - 研究生 and 广西城市职业大学.

## Strict-review correction

The earlier 408-verified checkpoint was historical and is superseded. Strict comparison rejected 99 records; two were restored by archived directed redirects and **97 were downgraded to unknown**. Their prior evidence, confidence, research trace, and reason are retained in `ownershipReviewHistory`; exact affected IDs are under `strictEndpointReview.downgradedCatalogIds`. These are missing-proof decisions, not 97 proven wrong-school mappings.

Thirty-two Python unit tests cover the seven final-review counterexamples, different scheme/query/fragment/trailing slash, published-address tenant mismatches, invalid ports, missing or discontinuous redirect captures, generic-SSO convergence, hash tampering, literal adjacent HTML text, continuous raw meta/HTTP targets, wrong published ports/paths, and wrong final identity. No generator, Gradle invocation, or commit was performed by this evidence review.

## Confirmed mismatch and proven correction

潍坊护理职业学院's original sdwfvc.cn target identifies 潍坊职业学院 and remains contradicted. The official wfnc.edu.cn notice at `https://www.wfnc.edu.cn/info/1051/1172.htm` (2021-08-13) publishes `120.224.233.70:8189`: its adjacent HTML spans contain `1` and `20.224.233.70:8189`. The earlier text extractor inserted a separator; concatenating raw text nodes preserves actual text without deleting arbitrary whitespace.

A 2026-10-15 raw capture proves the complete directed chain: `http://120.224.233.70:8189/` returns one HTML meta refresh to `/jwglxt`, which returns HTTP 302 to `/jwglxt/`, then HTTP 302 to `/jwglxt/xtgl/login_slogin.html`. The exact final HTTP 200 page names 潍坊护理职业学院. Saved response snapshots, including the official notice, are embedded as base64 with checked SHA-256 and capture date; the sanitized archive is `weifang-sanitized-chain.json` in the research directory below. Headers retain only the captured HTTP status, Location and Content-Type. Anonymous cookies are omitted; body scripts are removed and input values blanked. Original hashes remain for traceability, while validation uses hashes of the saved sanitized bytes. Original publication text, refresh tags and visible institutional identity are retained.

The separate `official-school-meta-chain` validator parses the actual refresh attribute and raw Location headers; hand-written destination fields cannot override them. It requires the literal publication quote/root authority, exact original source URL, same-origin continuous responses, exact final target and institutional identity. Missing/multiple meta tags, discontinuity, hash mismatch, wrong IP/port/path and wrong final identity fail closed. Non-allowlisted headers, script elements and populated input values are rejected to prevent session or token material entering evidence. Existing endpoint equivalence rules stay strict.

The proposed correction is **verified for public URL ownership**, preserving the earlier unknown decision in review history. It replaces the proven wrong-school entry only as `unverified`, with `enableFetch=false` and `authenticatedImportTested=false`; no authenticated timetable import was tested. The initial configured-URL totals remain 311 verified / 207 unknown / 1 contradicted.

## Research coverage and limitations

The initial 2026-10-14 pass fetched 517 distinct configured URLs, 488 root candidates and 545 source-page candidates, with 287 targeted searches. Later historical batches added 12 targeted searches/40 pages and 129 searches/88 pages. The 2026-10-15 continuation added 119 targeted school-name searches, 284 candidate page fetches and 119 configured-URL refetches. These are historical batch counts, not current verified totals.

Research captures live under `/Users/lingion_k/research/school-expansion-20261014/url-verification/`. Quotations and hashes are embedded in machine-readable evidence; search results are discovery aids, never standalone proof. The 207 unknown entries include generic pages, restricted hosts, unproven tenant/path distinctions, campus/graduate scope gaps, and the 97 strict-rule downgrades. Pending catalog entries outside the initial 519-record URL audit are not URL-verified.

四川城市技师学院 remains unknown because the current tenant names 四川城市职业学院; attachment to that institution does not establish a shared tenant. 山东大学（威海） remains unknown because parent-university identity and incidental campus mentions do not establish its academic endpoint. 云南农业大学's official integration page links the tenant root, but the root capture does not prove the configured `/jsxsd/` path.

✅ 精确scheme·authority·path·query·fragment → ✗同host/SSO/根路径推租户 · ✅实际有向跳转链·归档hash · 缺证据→unknown · 已证伪错校→阻断
