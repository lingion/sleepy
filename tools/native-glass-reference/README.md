# iOS 26.5 native glass reference

Standalone observation app. Controls retain system geometry, drawing, tracking,
materials, springs, and presentation transitions. No private input API or altered
animation speed. `GlassButtonStyle`, `GlassProminentButtonStyle`, `Toggle(.switch)`,
`Picker(.segmented)`, `Slider`, `UISwitch`, `UISegmentedControl`, `UISlider`,
`UITabBar`, `.sheet`, `.alert`, and `Menu` supply the native references.

## Build and record centrally

Run from this directory. Select an iOS 26.5 simulator with `xcrun simctl list devices`,
then set `NATIVE_GLASS_SIMULATOR_ID` to its UUID. A generated project is included;
XcodeGen is only needed when changing project.yml. Run tests sequentially on one device.

```sh
xcodegen generate --spec project.yml
xcodebuild build-for-testing -project GlassNativeReference.xcodeproj \
  -scheme GlassNativeReference \
  -destination "platform=iOS Simulator,id=$NATIVE_GLASS_SIMULATOR_ID" \
  -derivedDataPath DerivedData
```

Start / stop the recording in the controlling terminal around each test batch:

```sh
xcrun simctl io $NATIVE_GLASS_SIMULATOR_ID recordVideo --codec=h264 native-grid.mp4
```

```sh
xcodebuild test-without-building -project GlassNativeReference.xcodeproj \
  -scheme GlassNativeReference \
  -destination "platform=iOS Simulator,id=$NATIVE_GLASS_SIMULATOR_ID" \
  -derivedDataPath DerivedData -parallel-testing-enabled NO \
  -resultBundlePath NativeReference.xcresult \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testToggleGrid
```

Replace the final method with `testSegmentedGrid`, `testSliderGrid`,
`testButtonsGrid`, `testTabBarGrid`, or `testPresentationsGrid`. Every case also has
the `Stripes` suffix alternative. Each test launches one isolated run and stores
the requested actions as a `requested-gestures` JSON xcresult attachment. Actual
input delivery is in the app trace, not inferred from XCUITest's requested speed.

## Runs, coordinates, and background

Each launch writes `Documents/reference/<sanitized REFERENCE_RUN_ID>/session.json`
and `trace.jsonl`. UITests supply a method-specific ID plus UUID, so subsequent
launches keep earlier traces. Manual launches generate a UUID. Copy the complete
`Documents/reference` directory from the app's data container after the batch:

```sh
xcrun simctl get_app_container $NATIVE_GLASS_SIMULATOR_ID \
  com.codex.reference.GlassNativeReference data
python3 export-touches.py /returned/container/Documents/reference exports
```

The app is portrait and uses light traits. Each background is drawn in root-window
points from `(0,0)`, with an opaque sRGB `(0.94,0.94,0.96)` base. Grid lines are
20 pt apart, 0.5 pt wide, black at 0.13 alpha. Stripes are 28 pt wide at 0.52
alpha, in system red/orange/yellow/green/teal/blue/purple order. `session.json`
exports both opaque and painted stripe RGBA resolved for current traits,
converted to sRGB, then extracted with `UIColor.getRed`. Use those values on
Android. Reference tint is fixed sRGB `(0,0.65,0.75,1)`.

On the 375 pt wide SE, stage width is 335 pt, x=20. For any screen, width is
`min(420, windowWidth - 40)` and left is `(windowWidth - stageWidth)/2`.
Heading y=`safeTop+10`; background selector y=`safeTop+44`; scene selector
y=`safeTop+88`; stage y=`safeTop+146`.
Tab frame is x=`left-8`, y=`windowHeight-safeBottom-82`, w=`stageWidth+16`, h=70.
Stage height is `max(120, tabY-safeTop-160)`. Native controls remain intrinsically
sized inside the stage. These are layout input formulas, not measured output
geometry. Actual control rectangles are in the frame trace and test attachment.

Manual launch arguments: `-ReferenceScene toggle|segmented|slider|buttons|presentations`
and `-ReferenceBackground grid|stripes`. Selector buttons also switch these live.

## Input and presentation evidence

Touch observations include UIKit phase, touch ID, `UITouch.timestamp`, main-clock
delivery time, window geometry, point and normalized coordinates, hit view, and
coalesced samples. No predicted sample is used. Public `UIApplication.sendEvent`
observes native presentation windows too. The export script deduplicates delivery
and coalesced observations without smoothing or interpolating; it preserves the
actual `dtMs` waveform per gesture for Android replay.

Tests request short taps, 0.7 s hold/release, and straight slow/fast drags. Drag
press is 0.45 s and endpoint hold is 0.25 s. There is no public arbitrary
multi-waypoint XCUITest drag used here; slow and reverse drags are separate
gestures. Native activation / value changes and scene / background changes are
logged beside touches.

Presentation sampling requests 60 Hz and records actual display timestamps,
intervals, sampling cost, traversal truncation, native control / label frames,
glass / visual-effect frames, opacity, transforms, and bounds. Private class
names are inspected as strings only; no private properties/methods are called.
The hierarchy walker is bounded to 900 views and depth 18. The logger serializes
and writes off-main; frames retain observer cost so expensive capture or initial
system shader warmup can be identified. Use video plus logged samples; a layer's
alpha does not directly represent internal material opacity. Keep first-use and
warmed-up evidence separate.

The November 12, 2026 run used iOS 26.5 (23F77), iPhone SE 3 simulator, 375 × 667 pt.
Seven initial cases, direct selection, and a bidirectional drag matrix passed.
See ../../docs/native-glass-control-alignment.md for measured values and limits.
Rebuild and capture on the selected OS before treating a new run as a reference.
