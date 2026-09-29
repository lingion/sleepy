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
