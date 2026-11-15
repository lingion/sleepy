# Navigation and startup performance

The `baselineprofile` module collects a startup profile and a navigation profile,
and contains Macrobenchmark cases for cold startup and tab-switch frame timing.
The app consumes generated Baseline Profiles through the Android Gradle Plugin.

## Run on a measurement device

Use a physical device running Android 10 (API 29) or newer. Run the startup
profile and timing benchmark first, then run the navigation profile and frame
benchmark. Keep the device refresh rate fixed across before/after runs; record
the selected rate and repeat each run to account for device variance.

```powershell
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
./gradlew :app:generateReleaseBaselineProfile
```

The startup benchmark reports `timeToInitialDisplayMs`. The navigation benchmark
reports frame duration and frame overrun distributions for repeated tab changes,
including the calendar icon's return to the Schedule tab. Compare P95 frame time
and over-budget frames under the same device, refresh rate, app data, and thermal
state. A 50% target means reducing the measured P95 frame time by at least 50%;
it is not considered met until the same benchmark shows that reduction.

The previous 120 Hz `gfxinfo` capture mixed full-page rendering with navigation
and is not a comparable animation-only baseline. Do not use it to claim a 50%
animation improvement.
