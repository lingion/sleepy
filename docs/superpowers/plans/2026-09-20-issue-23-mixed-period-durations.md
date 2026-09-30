# Issue #23 Mixed Period Durations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Support multiple lesson durations in automatic period mode and preserve manual schedule edits when switching modes.

**Architecture:** Extend `SmartPeriodConfig` with duration groups and per-period assignments, mirroring the existing break-group model. Add a pure `TimeTableUtils.inferSmartPeriodConfig` function and use it at mode/import boundaries. Keep standard grid rows equal-height in this change.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, JUnit 4, Android Gradle.

## Global Constraints

- Repository root: `~/sleepy`; run Gradle commands from there.
- Author commits as `lingion <lingion@hrbeu.edu.cn>`; no Claude/Anthropic trailer; no `Fixes`, `Closes`, or `Resolves` issue keywords.
- Focused tests: `./gradlew :app:testDebugUnitTest --tests '<FQCN>'`.
- Final verification: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug`.
- Add strings to `values`, `values-en`, `values-es`, `values-ja`, `values-zh-rCN`, and `values-zh-rTW`.
- Do not change `timeToFractionalRows` or `buildRenderSlotPlan`; standard rows remain equal-height.
- Exclude rows with `edgeClass != null` from inference.
- Old smart-config JSON without new fields must decode with empty-list defaults; no database migration.

## File Map

- Modify `app/src/main/java/com/lingion/sleepy/data/entity/SmartPeriodConfig.kt`: duration model, assignment helpers, derivation.
- Modify `app/src/main/java/com/lingion/sleepy/util/TimeTableUtils.kt`: pure inference function and validation.
- Modify `app/src/main/java/com/lingion/sleepy/ui/component/SmartPeriodEditor.kt`: duration groups mirroring break groups.
- Modify `app/src/main/java/com/lingion/sleepy/ui/component/TimeSlotEditor.kt`: infer on manual-to-auto and derive on auto changes.
- Modify `EditTableScreen.kt`, `PeriodTableEditScreen.kt`, `ImportSheet.kt`, and `JwImportActivity.kt`: shared inference seeding.
- Modify six locale `strings.xml` files: duration labels and assignment copy.
- Test `SmartPeriodConfigTest.kt` and create `TimeTableUtilsInferenceTest.kt`.

---

### Task 1: Extend the smart configuration model

**Files:**
- Modify `app/src/main/java/com/lingion/sleepy/data/entity/SmartPeriodConfig.kt`
- Test `app/src/test/java/com/lingion/sleepy/data/entity/SmartPeriodConfigTest.kt`

**Interfaces:**
- Add serializable `DurationOption(minutes: Int, isLong: Boolean = false, label: String? = null)` with `displayLabel(index)`.
- Add `durations: List<DurationOption> = emptyList()` and `periodAssignments: List<Int?> = emptyList()`.
- Add effective assignment and per-period duration helpers.

- [ ] Write failing tests for old JSON defaults, mixed 45/30 derivation, invalid assignment fallback, and duration-group deletion index remapping.
- [ ] Run the focused test and verify RED.
- [ ] Implement the smallest model change; keep break behavior unchanged.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.lingion.sleepy.data.entity.SmartPeriodConfigTest'` and verify GREEN.
- [ ] Commit only this model/test unit with a real verify line.

### Task 2: Add deterministic inference from manual rows

**Files:**
- Modify `app/src/main/java/com/lingion/sleepy/util/TimeTableUtils.kt`
- Create `app/src/test/java/com/lingion/sleepy/util/TimeTableUtilsInferenceTest.kt`

**Interface:**
`inferSmartPeriodConfig(rows: List<TimeSlotRow>, previous: SmartPeriodConfig? = null): SmartPeriodConfig?`.

Rules: filter edge rows, validate standard rows, choose a deterministic duration mode, infer duration and break groups, preserve prior labels/classification by minutes where possible, and return a config whose `derive()` reproduces valid rows.

- [ ] Write tests for 45-majority plus 30-minority, multiple duration/break groups, deterministic ties, edge exclusion, invalid rows returning null, and infer-then-derive round trip.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.lingion.sleepy.util.TimeTableUtilsInferenceTest'` and verify RED.
- [ ] Implement pure inference with no database or UI access.
- [ ] Run the focused test and verify GREEN.
- [ ] Commit the inference unit and tests.

### Task 3: Add duration groups to automatic-mode UI

**Files:**
- Modify `app/src/main/java/com/lingion/sleepy/ui/component/SmartPeriodEditor.kt`
- Modify the six locale `strings.xml` files.

- [ ] Add duration heading, assignment hint, short/long duration labels, and no-period guidance in all locales.
- [ ] Reuse the break assignment-group semantics: minute editor, delete/remap, numbered period cards, and short/long colors.
- [ ] Keep existing break UI behavior unchanged.
- [ ] Run model tests and compile the debug app.
- [ ] Commit this UI/i18n unit.

### Task 4: Make manual and automatic modes round-trip

**Files:**
- Modify `app/src/main/java/com/lingion/sleepy/ui/component/TimeSlotEditor.kt`
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt`
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/mine/PeriodTableEditScreen.kt`

- [ ] Add a contract test proving manual 45/30 rows become primary 45 plus a 30-minute group at the correct node.
- [ ] Add a round-trip assertion preserving every valid standard start/end pair.
- [ ] Infer from current rows when entering automatic mode instead of constructing a fresh default config.
- [ ] Preserve valid stored config when it still derives current rows; otherwise re-infer.
- [ ] Run focused tests and compile.
- [ ] Commit this mode synchronization unit.

### Task 5: Use inference at import boundaries and verify

**Files:**
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt`
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt`
- Modify import tests only where they assert fresh 45-minute defaults.

- [ ] Add a regression test for imported mixed durations.
- [ ] Seed automatic config from parsed `TimeSlotRow` values through the shared inference function.
- [ ] Keep incomplete rows in manual mode and use existing validation instead of writing defaults.
- [ ] Do not change native export format.
- [ ] Run focused tests, then `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:lintDebug`; separate pre-existing baseline findings from new errors.
- [ ] Confirm render-height functions and unrelated dirty files are untouched.
- [ ] Commit import wiring only after both full verification commands have real output.

## Verification Checklist

- [ ] 45-minute majority plus one 30-minute period is recognized correctly.
- [ ] Manual -> automatic -> manual preserves every valid standard time pair.
- [ ] Edge nodes are excluded from inference.
- [ ] Old JSON decodes and derives as before.
- [ ] Duration groups use the same short/long assignment behavior as breaks.
- [ ] Standard rows remain equal-height; no render-weight code changed.
- [ ] Full unit tests pass and lint has no new errors.
