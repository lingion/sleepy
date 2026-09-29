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
