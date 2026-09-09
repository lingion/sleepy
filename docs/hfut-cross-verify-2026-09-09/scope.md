# scope - HFUT issue #25 diagnosis (2026-09-09)

## Task type
- Issue #25 is a diagnosis/fix of an EXISTING adaptation (not a new school).
- HFUT is already supported (eams5, jxglstu.hfut.edu.cn), attributed since the EAMS5 adaptation.
- Collection package: sleepy-adapt-0907-122346.zip (issue #25 attachment, 11.9MB).
- Goal: reproduce the reporter failure mode at unit-test level with the real captured data,
  find root cause, apply minimal fix, add desensitized regression fixtures.
- Non-goals: no protocol upgrade (EAMS5 parser for schedule-table/datum is already locked
  by fixtures and green); no new schools; no widget or other protocol changes.

## Reproduction environment
- Worktree: sleepy-worktrees/hfut25 (branch adapt/hfut-issue25, base a6e2cac).
- Method: fixtures synthesized from 1-dom/top_page.html and 4-net-live/1_2026-09-7.json,
  fed to detectProtocolFromHtmlForTest / tryAllParsersForTestWithAttempts / JwEams5Parser.
- Red-green evidence: 27 new tests, 7 red (all root-cause patterns) before fix;
  1297 total green after fix.
