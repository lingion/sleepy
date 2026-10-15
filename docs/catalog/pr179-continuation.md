# PR 179 continuation — 2026-10-15

The recovered request requires sourced school-to-academic-system ownership and accurate participation in the existing city location system. The original local session is rollout-2026-10-14T11-30-02-01a13875-bc82-7b21-9217-03ac71daddbc.jsonl: user request at line 3237, followed by further tool work after its line-3322 summary. This continuation builds on that work; the PR is not ready to claim complete nationwide location coverage.

## Current scope

- Catalog: 4,140 entries, including graduate, campus, historical and import variants; not 4,140 independent institutions.
- City mappings: 3,763; 377 unresolved. This adds 599 reviewed exact-entity address records to the prior 3,164 mappings.
- Unresolved: 166 campus/import variants, 44 graduate entries, 7 historical/alias entries, 47 legacy names and 113 adult institutions with no accepted current city evidence.
- A mapped graduate office or teaching unit does not prove all campuses or student locations. Six 2015 recruitment-office addresses remain research evidence only.
- Offline boundaries: 22 Taiwan counties/cities added; offshore islands and small enclaves tested. Xinxing remains explicitly unsupported pending a verifiable compatible-coordinate boundary source.
- Initial configured academic endpoints: 348 publicly verified, 170 unknown, one wrong-school configuration identified. Strict endpoint review withdrew 97 earlier overbroad verdicts; 27 now have exact official links or directed HTTP evidence, while 70 remain unresolved. Unknown does not mean proven wrong.
- Unknown new candidates remain pending with no operational URL. Existing baseline configurations retain their prior import evidence, with public ownership gaps explicitly recorded. Public verification is not an authenticated timetable import test.

## Evidence and continuation

school_url_evidence.json records identity, exact endpoint, observations, source URLs, verdict history and research traces. The nursing-college correction requires the official notice plus an archived continuous meta/HTTP chain to the exact named endpoint.

school_location_verified.json is the sole reviewed address input. school_location_research.json preserves actual search queries, result URLs, probe outcomes, hashes and reviewed rejection reasons for unresolved names; search hits alone are not proof. school_location_evidence.json is the generated full-catalog audit.

Remaining research should prioritize unresolved exact graduate/campus entities, then failed or ambiguous official pages. Fresh exact-contact queries cover the remaining graduate records. County-level addresses such as Yanji and Jishou require official administrative linkage to the prefecture used by the polygon index. Do not infer from school names, parent universities, third-party recruiting pages, or unrelated source footers.

The research archive lives under /Users/lingion_k/research/school-expansion-20261014/. Completed temporary batches were archived in locations/frozen-batches; only canonical reviewed inputs drive generation.

## Reproduction

Run the URL safety, ownership and location Python tests, followed by generate_school_catalog.py, enrich_school_cities.py, build_school_location_evidence.py and generate_schools_list.py. Then run generate_school_catalog.py --check, verify repeated-output hashes and git diff --check. Run one serialized Gradle invocation for testDebugUnitTest, lintDebug and assembleDebug; concurrent Gradle processes in this worktree previously caused a KSP output collision.

✅ 来源证据先于归属/定位 · ✅ 精确实体与endpoint · ✗同host/根路径/SSO推同租户 · ✗研究生办公室=全部校区 · ✗旧报名地址=现校址 · ✅ 公开快照脱敏后入库 · 🚫原始cookie/CSRF入库 · ✗待证项包装成完成
