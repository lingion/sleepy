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

`testToggleHeldDirectionAndRapidProbe` exclusively targets `swiftui-toggle` and
`uikit-toggle`. It adds held forward/reverse pairs at requested 45 / 650 pt/s,
vertical movement out of each switch from both values, six consecutive tap
commands without an added pause, and a public double-tap probe. Each horizontal
reverse begins a new touch after the prior UP. The outside probe also ends in UP;
it does not synthesize a touch CANCEL. Requested commands and the actual trace
must remain separate: XCUITest can insert quiescence waits, so the consecutive
commands are only rapid-input evidence if the delivered DOWN gaps establish it.
The checked public `XCUICoordinate` interface supplies one drag destination and no
explicit cancel operation. This test therefore does not cover a continuously
held multi-segment out-and-back touch or a system-delivered CANCEL. Those remain
native evidence gaps; two separately released drags must not stand in for them.

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

The app is portrait and defaults to light traits. Each legacy background is drawn in root-window
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
Tab frame is x=`left-8`, w=`stageWidth+16`; height comes from the public
`UITabBar.sizeThatFits` result. Its bottom is `windowHeight-safeBottom-12`.
Each changed layout emits a `tab-layout` trace event containing the proposed and
fitted sizes, actual frame, safe areas, size classes, content-size category and
tab count. This is an independent `UITabBar` fixture; Sleepy's production native
navigation uses a four-item SwiftUI `TabView` / `UITabBarController` and must be
compared with that parent context before declaring product geometry identical.
Stage height is `max(120, tabY-safeTop-160)`. Native controls remain intrinsically
sized inside the stage. These are layout input formulas, not measured output
geometry. Actual control rectangles are in the frame trace and test attachment.

Manual launch arguments: `-ReferenceScene toggle|segmented|slider|buttons|presentations|language`
and `-ReferenceBackground grid|stripes`. Selector buttons also switch these live.
`-ReferenceTabCount 4` adds Courses between Home and Explore; the default remains
three tabs so existing reference runs keep their original geometry.

### Dock geometry provenance

The November 10 `work/native-motion-reference` observation allowed UIKit to
settle its bar to 351×83 pt, giving a 274×62 pt three-item platter and 94×54 pt
resting lens. The initial November 12 tool reassigned a 70 pt bar frame on every
layout. That constrained run produced a 274×49 pt platter / 94×41 pt lens (309×49
pt platter with four items). These are different layout inputs on the same
iPhone SE (3rd generation), iOS 26.5 / 23F77 simulator, not two system defaults.
In the constrained run Home's image frame y=595,h=28 and label y=611,h=12 overlap;
the earlier naturally fitted run's image y=590,h=28 and label y=619,h=12 do not.
Retain old recordings as explicitly constrained evidence. Do not use the forced
49/41 geometry as an unconstrained native navigation specification.

Focused Dock tests are `testDockDirectSelectionPhases`,
`testDockAdjacentVelocityMatrix` (three-item adjacent 86 pt scene),
`testFourTabFirstLastMotion`, and `testTabBarGrid`. They leave at least 1.65 s
after individual gestures; the consecutive-tap probe leaves its complete final
tail but intentionally records the actual inter-tap waits. Requested gestures
remain separate xcresult attachments; actual delivery remains in `trace.jsonl`
and `export-touches.py` output. No unrelated control matrix needs to be rerun.

The November 13 natural-size replacement is stored at artifact-workspace-relative
`outputs/glass-native-parity-20261112/dock-natural-fitting-20261113/`.
`DockNaturalFitting.xcresult` and `test-summary.json` record four passing focused
tests; `capture-manifest.json` identifies the simulator and frozen source hashes.
`traces/`, `exports/`, `attachments/`, `delivery-validation.json` and
`dock-natural-fitting.mp4` retain 55 complete gestures and 1,109 actual exported
touch samples, with no malformed trace lines. The 351×83 pt outer fixture yields
274×62 pt / 309×62 pt three-/four-item platters, a 94×54 pt resting lens, and
86 pt / 69 pt item-center pitch. This directory is capture evidence kept outside
the tool source, and is not regenerated by checking out the repository.
These measurements apply to the independent SE 3 / iOS 26.5 fixture, not the
production SwiftUI navigation parent or other devices. Consecutive tap DOWN gaps
were 1.534–1.615 s, so this run does not establish rapid-tap behavior. The older
static optical matrix's compressed Dock samples retain their original scope.

## Static optical background matrix

Additional `-ReferenceBackground` values mirror the Android optical fixture's
input formulas: `fine_grid` (opaque #F0F0F5, alternating black alpha .55 / white
lines every 8 pt, .5 pt stroke), `color_stripes` (18 pt opaque
#F51D39/#FFD51C/#00CB69/#00DCED/#1552F3/#F218DD), `tonal_blocks` (112 pt alternating
#101018/#F5F5F0), and `synthetic_landscape` (the same 720×1280 deterministic scene
and overflowing 32-bit noise algorithm, seed 0x51EE9). The landscape is synthetic,
not a photograph. Point and dp dimensions are corresponding logical inputs;
platform rasterization and image scaling are not assumed pixel-identical.
Legacy `grid` and `stripes` painting is unchanged.

`-ReferenceInterfaceStyle light|dark` changes native traits while these four
background inputs remain fixed. `-ReferenceStaticOptics` skips the expensive
presentation-layer sampler, preserving real touch/value/session logging and
exporting `background-input.png` (background drawing only, not a screen capture).
Landscape runs also export their 720×1280 source PNG. The
`reference-configuration` trace event identifies the chosen scene, background,
traits, and static mode; the session's legacy-background palette stays available.

The eight `testStaticOptics{FineGrid,ColorStripes,TonalBlocks,Landscape}{Light,Dark}`
tests each visit all six native scenes. Rest references are XCUITest display
screenshots plus accessibility hierarchies. Held references require a host
observer because a synchronous XCUITest press cannot itself take a screenshot
mid-press. Before starting tests, run this in another terminal:

```sh
python3 capture-static-optics.py --simulator "$NATIVE_GLASS_SIMULATOR_ID" \
  --output /absolute/path/native-optics/held
```

The observer watches durable UITest signals, waits for the actual native DOWN
record, and invokes `simctl io screenshot` during a 2.6 s public press. Stop it
after the tests, copy all app reference traces, and verify:

```sh
python3 verify-static-optics.py --reference /copied/reference \
  --captures /absolute/path/native-optics/held/capture-events.jsonl \
  --output /absolute/path/native-optics
```

Only captures whose entire screenshot subprocess timestamp bracket lies inside
one actual delivered DOWN/end interval are marked `verifiedHeld`. Failed or
early captures remain in the report. PNG validation checks complete chunk CRCs,
the zlib stream, IHDR dimensions against the session screen and filtered scanline
lengths/filter bytes; failures retain an explicit `pngValidationError` and cannot
pass. The image SHA remains the hash of the actual captured file.
This proves an active touch at capture;
it does not prove an optical material match. Static language-menu sampling keeps
an explicit first-tap-no-open attachment if the initial native tap is cancelled,
then requests a separate native long press to open it; that recovery does not
erase the initial observation or assert a product defect.

### System alert rest matrix

The separate `testAlertOptics{FineGrid,ColorStripes,TonalBlocks,Landscape}{Light,Dark}`
tests capture the existing SwiftUI `.alert("Native Alert")` in all eight input /
trait conditions. Each isolated launch records a background-only PNG, a before
display screenshot, the actual system alert at rest with its accessibility
hierarchy, public Confirm, a second presentation, and public Cancel. The trace
keeps actual delivered touches and requested/confirmed/cancelled callbacks;
the requested test actions remain separate attachments. Static mode intentionally
omits presentation-layer traversal, so these runs establish rest optics and
actions, not animation timing or held-state behavior. Record the display video
around the test batch with the command above.

The November 13 evidence is at artifact-workspace-relative
`outputs/glass-native-parity-20261112/native-alert-optics-20261113/`; its preflight
and formal matrix remain separate. The alert uses the system's real material and
layout. Menu and sheet screenshots are not substitutes for this reference.

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

Schema 2 brackets sequential presentation reads with `captureStartT` and
`captureEndT`, relative to the session uptime origin. The generic `t` is later row
enqueue time. Individual views are read at different instants inside that range;
`displayTimestamp` may predate the callback. UIView traversal and the separately
bounded animated-sublayer traversal both report truncation. `effectiveOpacity`
multiplies UIView ancestor presentation opacity only; it excludes extra CALayer
ancestors, masks, and internal material coverage.

The November 12, 2026 run used iOS 26.5 (23F77), iPhone SE 3 simulator, 375 × 667 pt.
Seven initial cases, direct selection, and a bidirectional drag matrix passed.
See ../../docs/native-glass-control-alignment.md for measured values and limits.
Rebuild and capture on the selected OS before treating a new run as a reference.

## Five-language native menu

`language` uses Sleepy's actual native composition: `Menu` containing an inline
`Picker`, five tagged language options, a full-width card anchor, borderless menu
style and plain button style. The selection setter is logged before dispatching
the state update to the main queue. A current-language selection is logged but
does not schedule a state change. No custom menu transition or animation is added.

Run `testLanguageMenuReference` for first-use and warmed selection, outside
cancellation, long-press opening and hold/slide/release selection. Run
`testLanguageMenuSameChoiceAndCancellationProbe` for current-language selection and a
consecutive-tap cancellation probe. Public XCUITest can wait for animation
quiescence between those taps: inspect delivered input before calling the latter
an early-opening cancellation. The November 12 probe tapped outside 1.50 seconds
after anchor release, so it did **not** establish early-opening cancellation.

Language traces retain every ancestor and child by default. `-ReferenceFocusedTrace`
filters serialized views while preserving menu morphs, SDF contours, moving
parents, controls and materials. Both modes retain actual callback gaps and
observer cost. Public `CALayer.animation(forKey:)` reads public animation types
and properties only. The observed native morph uses `CAMatchPropertyAnimation`
and `CAMatchMoveAnimation`, which did not expose `CASpringAnimation` parameters;
non-finite duration is stored as JSON null. No private KVC or selector is used.

Export a copied trace directory with:

```sh
python3 export-language.py /path/to/reference /path/to/export
swift -suppress-warnings inspect-video.swift /path/to/native.mp4 /path/to/video-export 12.3 12.35
```

The CSV distinguishes target containers, real content portals, morph parents,
SDF contours and internal collections. `capture_start_t`/`capture_end_t` bracket
presentation reads; `display_t` is the display-link timestamp relative to the
same session origin. For schema 1, the exporter estimates the interval from `t`
minus `captureCostMs` and marks it `legacy-approximation`; enqueue overhead cannot
be recovered. The export discovers menu bounds and morph ancestry in each run,
rather than assuming a fixed width or hierarchy index. The video
tool records decoded PTS, exports nearest actual frames and labels their actual
times; it never creates intermediate images. Variable video PTS or a timestamp
with millisecond precision is not evidence of millisecond screen refresh. Internal
SDF optical coverage and shader refraction remain visual-only evidence.
The contact-sheet crop assumes the 402 pt reference layout; full frames retain
the complete image. See ../../docs/glass-language-menu-reference.md for the
November 12 measurements and invocation-dependent vertical motion.
