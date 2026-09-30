# Implementation Plan: Widget Discovery, Schedule Bar, and Grid Headers

## Overview

Address three user-facing issues in the Android app while preserving the existing widget course rendering and grid course-body behavior:

1. Diagnose and repair widget picker preview/discovery regressions, using the existing Wake Up and vendor reverse-engineering records as evidence rather than assuming a single OEM root cause.
2. Add a persisted setting that hides the home schedule view switcher when the user only wants one view.
3. Modify only the grid view's weekday header row and left period header column. The grid course cards, body geometry, colors, and content layout remain out of scope.

## Scope Invariant

The grid change is restricted to these two regions:

```text
        Mon   Tue   Wed ... Sun   <- may change: weekday header row
Period  | course cells ...        |
1       | course cells ...        | <- course body must not change
2       | course cells ...        |
...     | course cells ...        |
```

No course-card or grid-body layout changes are included.

## Architecture Decisions

- Keep the existing Android AppWidget XML fallback (`previewImage`/`previewLayout`) and generated-preview path, but make preview registration and provider discovery observable and resilient. Validate every declared provider, resource, manifest receiver, and OEM-specific capability before changing metadata.
- Store the home switcher visibility as an `AppPrefs` boolean with a default that preserves current behavior. The schedule screen reads it reactively through the existing preference change bus and conditionally omits only `SegmentedSwitcher`.
- Refactor only `DayHeadCell` and `SingleTimeHeadCell` plus their immediate header geometry. The left period cell will render a vertical period label on the left and start/end time at the top/bottom on the right, while the weekday cell gets a smaller adaptive radius and tighter vertical content padding.
- Add contract/unit coverage for the preference default/change path and pure geometry/layout decisions where practical; use emulator visual verification for the picker and header boundaries.

## Task List

### Phase 1: Evidence and Diagnosis

- [ ] Task 1: Audit widget provider discovery and preview paths across manifest, provider XML, preview resources, generated-preview registration, and existing OEM docs/reverse-engineering records.
- [ ] Task 2: Establish a minimal reproducible matrix for Android launcher/provider listing and preview behavior, including Android version and OEM-specific constraints; record findings before editing.

### Checkpoint: Discovery

- [ ] Every declared provider maps to an existing receiver, XML metadata file, preview resource, and label/description.
- [ ] The diagnosis distinguishes picker preview failure from OEM provider filtering or unsupported proprietary widget channels.
- [ ] Wake Up/vendor evidence and any unverified assumptions are explicitly recorded.

### Phase 2: Focused Implementation

- [ ] Task 3: Repair the confirmed common preview/discovery defect without removing existing provider variants or introducing proprietary OEM dependencies.
- [ ] Task 4: Add the persisted setting and settings UI for hiding the home schedule view switcher; preserve current default and view selection behavior.
- [ ] Task 5: Change only weekday header and period header rendering. Reduce header-only vertical padding/radius responsively and implement the requested period layout:
  - left half: vertically arranged period label;
  - right half: start time near the top and end time near the bottom.

### Checkpoint: Focused Behavior

- [ ] The switcher is present by default and disappears only when the new setting is enabled.
- [ ] Toggling the setting does not change the selected view or grid course body.
- [ ] The grid header remains aligned with the existing course columns and rows.
- [ ] No course card or body rendering diff is introduced outside the requested header cells.

### Phase 3: Verification and Review

- [ ] Task 6: Add/update focused contract tests and run the relevant unit-test groups.
- [ ] Task 7: Build and verify on the emulator: app widget picker shows previews, providers remain discoverable, the setting works, and narrow/wide grid screenshots show only the intended header changes.
- [ ] Task 8: Review the final diff for scope leakage and document any OEM behavior that cannot be fixed from an Android APK alone.

### Checkpoint: Complete

- [ ] Focused tests pass.
- [ ] Debug build succeeds and emulator checks pass.
- [ ] Final diff changes only confirmed preview/discovery code, the new visibility preference/settings, and the two grid header components/tests/docs required for verification.
- [ ] Remaining vendor-specific gaps are reported separately from completed fixes.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| OEM launchers ignore or filter standard AppWidget metadata | High | Verify standard Android path first; document proprietary channels separately and do not claim an APK-only fix for HarmonyOS/Form widgets or vendor approval gates. |
| Runtime preview registration races launcher enumeration | High | Make registration idempotent, observable, and testable; retain XML previews as fallback. |
| Header refactor accidentally changes course body geometry | High | Keep changes inside header composables and add screenshot/diff checks around body boundaries. |
| New setting is not reflected until process restart | Medium | Use the existing AppPrefs change bus or equivalent state invalidation already used by schedule display settings. |
| Wake Up reverse-engineering scope is broader than this bug fix | Medium | First produce an evidence matrix and implement only findings applicable to standard Android AppWidget behavior; leave proprietary SDK work as explicit follow-up. |

## Open Questions

- Which specific launcher/OEM and Android version first exhibits the missing preview/provider listing? The first verification pass will identify this from available emulator/device evidence; implementation will not assume all launchers share one defect.
- Whether the user wants the new switcher hidden by default cannot be inferred from the request. Preserve the current visible default unless product direction later changes it.

---

# Implementation Plan: PR #56 Calendar and Grid Lab UX

## Overview
Implement two independent, default-off laboratory switches for grid separators and long class-break spacing, then refactor system-calendar export so permission handling is owned by ExportScreen and the authorized configuration dialog matches existing Sleepy Material 3 settings/import-preview patterns.

## Architecture Decisions
- Store each laboratory behavior as an independent `AppPrefs` Boolean; do not encode the pair as one mode.
- Keep today highlighting independent from separator rendering.
- Gate meal-break detection and geometry behind the long-break preference; gate only extra lines behind the separator preference.
- Make ExportScreen the permission state machine. CalendarImportDialog assumes permission is already granted and contains no authorization button.
- Use existing settings cards, `SettingToggleRow`, preview metric cards, and dialog action conventions; add no UI dependency.

## Task List

### Phase 1: Preferences and settings
- [x] Task 1: Add two persisted AppPrefs flags and localized settings rows.
- [x] Task 2: Wire settings changes to current-screen recomposition and widget refresh.

### Phase 2: Grid rendering
- [x] Task 3: Gate CourseTableView separators and long-break geometry independently.
- [x] Task 4: Gate WeekGridWidgetProvider separators and long-break geometry independently.

### Checkpoint: Grid labs
- [x] Focused AppPrefs contract coverage and debug compilation succeed.

### Phase 3: Calendar permission flow
- [x] Task 5: Move permission state machine into ExportScreen with automatic post-grant transition and denial return.
- [x] Task 6: Remove authorization UI from CalendarImportDialog and preserve authorized configuration states.

### Phase 4: Calendar visual alignment and verification
- [x] Task 7: Restructure CalendarImportDialog around existing settings/import-preview visual conventions and localized strings.
- [x] Task 8: Add focused state/behavior coverage, run regression tests, lint, and build branch APKs.

### Checkpoint: Complete
- [x] Acceptance criteria in the design specification are implemented.
- [x] APKs are built from `feat/pr56-calendar-lab-ui` and SHA-256 verified.
- [x] Branch remains unmerged, unpushed, and untagged.

## Dependencies
- Tasks 1-2 precede Tasks 3-4.
- Tasks 5-6 precede Task 7.
- Tasks 1-7 precede Task 8.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Existing PR rendering paths duplicate geometry logic | High | Preserve baseline branches and test all four preference combinations. |
| Permission callback state differs from actual provider state | High | Re-query both permissions after the activity-result callback. |
| Calendar dialog visual refactor regresses import behavior | High | Keep `SystemCalendarManager` and import/delete paths unchanged; run existing calendar tests. |
| Widget settings do not refresh immediately | Medium | Reuse the existing widget refresh entry point from the settings callback. |
| Locale resources drift | Medium | Add strings to every existing locale file and run resource compilation. |

## Verification Checkpoints
- After Tasks 1-4: focused AppPrefs, detector/widget, and app compilation checks.
- After Tasks 5-7: focused calendar tests and resource compilation.
- After Task 8: full unit test, lint, debug APK build, artifact hash, and branch/status checks.

## Scope Boundaries
No merge to `main`, push, tag, release, database deletion, calendar-event deletion changes, dependency additions, or CI changes without explicit approval.
