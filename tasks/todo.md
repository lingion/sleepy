# Task Checklist

- [ ] Audit all AppWidget providers, metadata XML, preview resources, and runtime preview registration.
- [ ] Compare current behavior with Wake Up/vendor reverse-engineering evidence and identify the common Android-fixable defect.
- [ ] Reproduce picker discovery/preview behavior on the available emulator and record the matrix.
- [ ] Implement the confirmed preview/discovery fix with XML fallback preserved.
- [ ] Add `AppPrefs` setting for hiding the home schedule view switcher, defaulting to current behavior.
- [ ] Add the setting row and wire it to `ScheduleScreen` without changing view selection.
- [ ] Modify only weekday header and left period header cells in the grid.
- [ ] Add focused tests for preference behavior and header geometry/layout contracts.
- [ ] Run focused tests and build.
- [ ] Verify picker previews, provider listing, setting behavior, and narrow/wide grid screenshots on emulator.
- [ ] Review scope and report unresolved proprietary OEM limitations.

---

# PR #56 Calendar and Grid Lab UX Tasks

- [x] Task 1: Add two persisted AppPrefs flags and localized settings rows.
  - Acceptance: Both flags default false, persist independently, and appear as separate lab rows in every supported locale.
  - Verify: `./gradlew :app:testDebugUnitTest --tests com.lingion.sleepy.GridLabPreferencesContractTest` and `./gradlew :app:compileDebugKotlin`.
  - Files: `AppPrefs.kt`, `GeneralSettingsScreen.kt`, `app/src/main/res/values*/strings.xml`.

- [x] Task 2: Wire settings changes to current-screen recomposition and widget refresh.
  - Acceptance: Toggling either row updates the active UI and invokes the existing widget data refresh path.
  - Verify: Static call-site inspection plus debug compilation.
  - Files: `GeneralSettingsScreen.kt`, existing widget refresh helper.

- [x] Task 3: Gate CourseTableView separators and long-break geometry independently.
  - Acceptance: Both flags are read independently; separators do not control today highlight; long-break off preserves baseline geometry.
  - Verify: Debug compilation and source review.
  - Files: `CourseTableView.kt`.

- [x] Task 4: Gate WeekGridWidgetProvider separators and long-break geometry independently.
  - Acceptance: Widget lines and meal-break spacing follow their separate flags; today background remains independent; widget bounds remain valid.
  - Verify: Full debug unit test suite and debug compilation.
  - Files: `WeekGridWidgetProvider.kt`.

- [x] Checkpoint: Grid labs.
  - Verify: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:compileDebugKotlin` passed.

- [x] Task 5: Move permission state machine into ExportScreen.
  - Acceptance: Granted opens configuration directly; missing requests; grant callback automatically opens configuration; denial leaves ExportScreen without a dialog; next explicit tap retries.
  - Verify: Debug compilation and static review.
  - Files: `ExportScreen.kt`.

- [x] Task 6: Remove authorization UI from CalendarImportDialog.
  - Acceptance: Dialog only renders authorized configuration/preview/import/delete states and no longer asks for permission.
  - Verify: Debug compilation and full unit tests.
  - Files: `CalendarImportDialog.kt`.

- [x] Task 7: Align CalendarImportDialog with existing Sleepy UI conventions.
  - Acceptance: Uses existing Material 3 colors/cards/toggle rows/action buttons; all visible strings remain localized; import behavior unchanged.
  - Verify: Resource compilation, static review, focused test, and lint.
  - Files: `CalendarImportDialog.kt`.

- [x] Task 8: Full verification and branch APK.
  - Acceptance: Full relevant tests, lint, and debug APK build pass; artifact hashes recorded; branch is not merged/pushed/tagged.
  - Verify: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, `./gradlew :app:assembleDebug`, SHA-256, `git status`.
  - Files: Tests and implementation files.
