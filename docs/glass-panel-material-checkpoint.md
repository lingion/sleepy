# Glass material checkpoint — 2026-11-15

This checkpoint updates PR #427. It publishes implementation and calibration evidence, not a native-equivalence verdict. iOS 26.5 (23F77), 375×667 pt at 2×, remains the native reference; the paired Android captures use a physical API 36 device and the explicit 1 pt = 1 dp comparison convention.

## Implementation

- Add explicit, default-off Dock tone/rim response parameters, positive refraction profile controls, and reset-safe shader uniforms. Production factories do not enable the experimental Dock tone responses. The navigation calibration override defaults to null and is supplied only by instrumentation.
- Separate Android blur radius from Gaussian sigma on the opt-in input path. Account for the full retained raster, actual source origin, and allocation padding instead of deriving input bounds from the panel size. Preserve inherited material sampling and restore the original graph on fallback.
- Apply light/dark modal dim amounts .20/.48 and restore the prior Window dim on theme changes and disposal. Explicit sheet scrim requests retain precedence. Material Design keeps its separate route.
- Use light Alert blur 8 and light Sheet blur 10 in the application wrapper. Alert uses a small bounded angular shine floor. Only the added floor receives an exponential, remaining-headroom response; legacy shine is preserved. This empirical encoded-RGB response is not a physical radiance or Apple implementation claim.
- Add registered external-input, actual geometry, clean-backing panel, and native-size reference fixtures. The paired-slope test entry is preparation only; it has not been executed.
- Repair stale accessibility/test selectors while retaining one semantic accordion action. Keep strict official Material Expressive button bitmap comparison and add failure diagnostics.

## Measured effects and remaining differences

The frozen S02 light Alert response width changes from 24.14 to 13.91 pt (native 12.53); contrast changes from 42.72 to 53.61 (native 49.88). Response position still differs. Only one of fourteen fixed Sheet response tracks meets the original identifiability gates, and changing Sheet 12 to 10 also changes shared-filter versus local-prefilter routing.

The rejected additive floor saturated 92 of 124 fixed colored edge points in red. The rejected plain-screen floor removed that saturation but reduced neutral side highlights below the visibility gate. The current budget-normalized floor has no 0/255 channel endpoints in those 124 points and restores left/right shoulder-relative peaks to 10.854/13.082 counts (native 16.002/19.995). FWHM spans only two pixels and remains unresolved under the original three-pixel rule.

These local gains do not imply every background improves. New-probe corner raw errors improve/regress/tie 22/22/4 versus additive, while first- and second-difference errors regress on 39/48 and 40/48 tracks. Historical landscape corners regress on 23/24 tracks versus additive. Historical inward tracks add no endpoint samples relative to additive, but add one blue 255 sample relative to plain screen on the right edge of light color stripes at 2.25 pt. These counts contain correlated and repeated observations, not independent trials.

Fixed top/body, unchanged Sheet10, and dark Window/app controls are byte-identical to their specified references. Native clean-backing REST/repeat screenshots are byte-identical within four sessions and preserve measured panel geometry. The original ICC-only gate remains failed; a separate PNG chunk/CRC audit identifies valid explicit sRGB. These observations do not recover a unique 2D refraction field, physical thickness, other sizes, or dynamics.

## Evidence and continuation

Publication validation ran against unchanged recorded sources: app 4,810 tests in 649 classes (zero failures/errors, three skips), runtime 56 tests, and backdrop 8 tests. Debug and instrumentation APK assembly succeeded. Swift frontend parsing passed for the three changed native source/test files; the new paired-slope entry remains unexecuted. CI commit lint now uses Git's non-merge set and continues checking every ordinary commit, so a true merge does not fail conventional-message parsing.

The public companion repository, `Kerry1020/sleepy-liquid-glass`, contains the research atlas, motion/slider/menu figures and the panel material study with source hashes, fixed curves, complete regressions, rejected candidates and reproducible data. Application captures do not validate that repository's independent backend automatically.

Local authoritative receipts are under `artifacts/glass-panel-thickness-20261115` in the session workspace: `panel-alert-floor-budget-build-result.json`, `publication-validation-input.json`, `publication-validation-result.json`, `analysis-rim-evidence/budget-decision.md`, and the native clean-backing analysis. No APK, build cache, DerivedData or user data is included in this source commit.

Next work remains static Alert/Sheet edge mapping and material refinement. Different sizes and complete-action dynamic validation remain pending; this checkpoint does not close the overall parity goal.
