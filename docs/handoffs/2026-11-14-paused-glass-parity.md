# UI parity work paused for device transfer — 2026-11-14

The user explicitly paused the goal and requested all work and assets uploaded for continuation on another computer. This is a **work-in-progress checkpoint**, not a release or native-parity acceptance.

## Source and assets

- App branch: `Kerry1020/sleepy`, `fix/android-native-glass-parity`; existing upstream PR `lingion/sleepy#427`.
- Public standalone runtime: `Kerry1020/sleepy-liquid-glass`, `main`.
- Private full handoff: `Kerry1020/sleepy-ui-parity-handoff`; release `handoff-20261114` contains split session assets, source Git bundles, installable previous APKs, file manifests, SHA-256 checksums and restore instructions.
- The authoritative app worktree on the original Mac was `/Users/kerrylee/.codex/worktrees/android-settings-glass-fixes/sleepy`. Do not substitute the dirty `~/sleepy` or `~/sleepy-ios` workspaces.

## Verification boundary

Before the final dialog-geometry edits, `all-dialog-native-actions-99/app-final-summary.json` recorded 30 classes / 192 tests, zero failures, errors or skips. That same run assembled the app and Android test APKs. Its source manifest is `app-final-source.json`; it is **not** the final paused source snapshot.

Installed previous app SHA-256: `5f2098625a7bc3b311cdf15b922794f9abf1c6a227e790524af44dbb57c907d9`. Previous test APK: `16b67e0b9293c7538753058ffcb1c11fda91345bb5cc985d258622d2d713bfac`. These are retained as build products in the private handoff and do not contain the latest geometry edits.

**Latest edits have not been rebuilt, tested or installed.** They move full Liquid Glass action groups out of the padded alert body into the pane footer, preserve the Material layout, unify the holiday editor's three actions, remove an extra occurrence-date inset, adapt update/import footers and add raw-action intrinsic preflight with a narrow SubcomposeLayout fallback. Review and run the targeted checks before installing or merging this checkpoint. No further UI implementation or device input was performed after the pause request.

## Immediate continuation

1. Read the private handoff README and `goal-paused.json`; resume the goal only when the user asks.
2. Review the final geometry patch, especially `GlassOverlayContent.kt` intrinsic measurement/fallback, fixed import options, copy form spacing, holiday delete/save semantics and existing action debug tags.
3. Run the saved targeted Gradle argv against the paused sources when authorized to resume; include the new raw long-label / 2× font tests. Preserve the original failure logs.
4. Install the newly built APK and verify actual copy, week, date, destructive and three-action dialogs in both styles. The images ending in `before-geometry` / `before-layout` are old installed UI; they are not final-fix evidence. `copy-table-before-layout.png` is a school selector, not a copy dialog; `week-current-geometry.png` is the My screen. Inspect image contents, not names.
5. Continue the open motion, optical, corner, layering and multi-background work listed in `docs/native-glass-control-alignment.md`. No 1:1, every-frame, every-millisecond or 99.99% completion has been established.

## Existing native evidence

The iOS 26.5 two-short-action alert reference has a 320-point pane, body horizontal inset 30, top inset 22, title/message gap 7, action inset 16, action gap 8 and two 140×48 action frames. Its actual title/message glyphs are left aligned. The reference is under `workspace/outputs/glass-native-parity-20261112/native-alert-held-20261113/review/named-rest/` in the asset archive. It does not independently establish the single-action width, three-action policy or internal label padding.

The parent-owned Toggle evidence in `parent-toggle-expressive-97` concerns actual Compose semantics carrier bounds and a controlled echo fixture, not a native optical contour, presentation timestamps or real database-latency equivalence.

## Runtime publication

The public standalone library already passed 38 host tests, sample/runtime builds and a clean remote-consumer build before this checkpoint. Its 34 imported entries, including 26 production Kotlin files and the AGSL file, remain byte-identical to the app runtime and the import manifest. The public backend is pinned official Kyant AndroidLiquidGlass 2.0.0 with documented minimal adaptations; the app still consumes its existing backend. Do not copy the app's SleepDown-derived backend into the public library. Complete app controls and theme/business adapters remain app-side; the library sample is not a complete production control set.

## Workflow constraints

- Maximum four active agents including root. Root owns builds, ADB, commits and pushes.
- The user waived the unrelated commit SOP. Use Kerry1020 / likerry22@gmail.com; no AI trailers.
- The user authorized pushes and PR updates, but previous upstream merge was denied by protected-branch permissions. Do not bypass protection or claim the PR merged.
- Native reference is iOS 26.5. Do not use the user's iPhone. Ordinary control drags are agent-verifiable; the special course long-press/drag acceptance remains separately scoped.
- Old Android device: `d3efcd6a`, API 36; new device identifiers must be discovered. Set `ANDROID_HOME` for builds. Avoid unrelated caches, APK duplicates and giant assets in source Git history.

The private handoff preserves all surviving session `artifacts`, `outputs` and `work`, including failed evidence, plus the previous installable APKs. It omits only `.DS_Store` metadata and machine-global caches/credentials.
