# Implementation Plan: Mine Navigation, Redo, and Course-Count Semantics

## Overview
Implement three related user-facing improvements on branch `feat/mine-navigation-redo-course-list`: make summary cards and the current-table management card navigate predictably, add a single-level redo action paired with undo in the schedule top bar, and define course counts consistently as course groups while exposing a current-table course list page.

## Architecture Decisions
- Keep the existing in-memory single-level `UndoManager` model; add one redo slot containing the state that was replaced by undo. A new write clears redo.
- Use `CourseEntity.courseName` as the user-facing course-group key. Multiple schedule rows for one name remain separate in the schedule data and are labeled as arrangements where the raw-row count is shown.
- Add a typed `SleepyRoute.CourseList` route. The page derives its list from the existing `ScheduleViewModel.state` for the selected table; no database schema or DAO change is needed.
- Preserve the existing route-level view-model sharing and Compose state patterns.

## Task List

### Phase 1: Navigation and count semantics
- [ ] Add `CourseList` route and navigator method.
- [ ] Add course-list screen with empty state and grouped course summaries.
- [ ] Make MineScreen table-count and course-count stat cells clickable; pass the current table's grouped courses to the new route through shared state.
- [ ] Make the current-table summary card in ManagementPage clickable to `AllTables`.
- [ ] Rename the raw-row count in management summary to "上课安排数" (localized resource updates for existing locales as appropriate).

### Phase 2: Undo/redo behavior
- [ ] Extend `UndoManager` with a redo snapshot and explicit `canRedo`, `recordRedo`, `pollRedo`, and clear-on-new-capture semantics.
- [ ] During undo, capture the current state as redo before restoring the undo snapshot; during redo, capture current state as undo before restoring redo.
- [ ] Expose redo in `ScheduleRepository`/`ScheduleViewModel` and refresh observers after either operation.
- [ ] Add unit/contract tests for undo -> redo, redo visibility, and new-write clearing redo.

### Phase 3: Top-bar control
- [ ] Replace separate conditional undo/check controls with one shared stadium-shaped two-half capsule.
- [ ] Keep both halves visible/hidden together based on undo/redo availability; use a faint vertical divider.
- [ ] Keep scale-uncommitted behavior coherent: commit/reset remains the scale-specific state, while data undo/redo labels and callbacks are not conflated.
- [ ] Add contract tests locking paired visibility and callback wiring.

## Verification Checkpoints
- After Phase 1: focused navigation/count tests and `:app:compileDebugKotlin`.
- After Phase 2: focused undo tests and `:app:testDebugUnitTest --tests ...Undo...`.
- Final: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and inspect the branch diff for unrelated changes.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Existing undo snapshot is global and process-local | Medium | Keep one explicit undo/redo pair; clear redo at every new capture and test transitions. |
| Course rows may have blank/different names | Medium | Group by normalized display name with a stable fallback label; retain raw rows for details. |
| Top bar is space constrained | High | Use fixed-size icon halves in one stadium container; avoid text labels in the control and preserve content descriptions. |
| Existing dirty changes on main | High | Branch from current state without resetting; touch only requested files plus tests/resources. |

## Success Criteria
- Tapping the Management page current-table card always opens AllTables.
- The Mine page table stat opens AllTables; the course stat opens the new course list.
- The same table shows the same group count in Mine and course list; management distinguishes arrangement count from course-group count.
- Undo and redo are mutually exclusive in the single-level history and appear/disappear as one capsule.
- Focused tests, full unit tests, and lint complete with no newly introduced lint errors.
