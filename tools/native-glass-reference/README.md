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

### Explicit SwiftUI TabView parent probe

`-ReferenceDockParent swiftui-tabview` opts into a separate, four-item SwiftUI
`TabView`. The independent `UITabBar` fixture remains the default. The opt-in
hierarchy is an outer SwiftUI hosting controller, a `UINavigationController`
with its navigation bar hidden, then a page hosting controller containing the
four tabs. This mirrors the relevant containment in production
`Sleepy/ui/nav/SleepyNavHost.swift`; it does not copy its routing coordinator.
UIKit owns the bar frame, material and input. The tool never assigns the bar
frame, fitting height, material, blur or animation in this context.

The probe follows `/Users/kerrylee/sleepy-ios/Sleepy/SleepyApp.swift`'s native
branch: four page roles, per-page and parent bottom `ignoresSafeArea`, keyboard
bottom-safe-area policy, transparent ancestor backgrounds, and the public
child-controller policy
`additionalSafeAreaInsets.bottom = -max(baseBottom - 34, 0)`. The 34 pt constant
is copied from production, not inferred from this device. Native appearances
are copied before changing selection/icon/title color roles. The reference
teal, white selected content and `secondaryLabel` unselected content occupy the
production role slots; they are not Sleepy's theme database values.

`-ReferenceDockContent placeholder|synthetic-scroll` selects the content. The
manual default is the original non-scrolling placeholder. The focused tests
below explicitly use `synthetic-scroll`: a real SwiftUI `ScrollView` on each
page, 24 sparse text rows and 92 pt extra bottom padding, matching the production
scroll-content allowance. The tests remain at the initial scroll position;
they do not establish bottom-edge transitions or all four production pages.
The root `ReferenceBackgroundView` optical canvas stays visible through the
clear synthetic page. Production Schedule/Today/Management/Mine instead paint
their own theme backgrounds and course/cards/text. The fixture also uses SF
Symbols and English labels rather than Android vector templates/localized
content. No production toolbar, business page, pushed route or sheet is copied.
These are explicit remaining differences, not evidence of complete app parity.

Both contexts log `tab-public-configuration`: public `UITabBar` getters,
resolved sRGB colors, effective traits, standard/scroll-edge background-effect
presence, frame, safe area and actual optical-canvas window rectangles. `before`
means before the tool policy, not an unparented SwiftUI default; the separate
`attachedToWindow` field describes actual attachment. `attached` is recorded on
the next main queue turn after policy application.

The SwiftUI context additionally emits deduplicated `tab-material-context`
events at attachment and every 0.25 seconds when public values change. The timer
uses common run-loop modes so held touch tracking can still be observed. Events
contain probe/bar ancestor frames, backgrounds/opacity/clipping/safe areas,
actual optical-canvas window rectangles, controller parents and hidden navigation state, selected-page `UIScrollView`
offsets/sizes/content insets/adjusted insets/tracking state, and public
`UIVisualEffectView` effect presence under the tab bar. They observe the native
hierarchy without installing scroll delegates or recognizers. Class names are
diagnostics obtained from public object types, not private API calls. Absent
public effect views or nullable appearance fields do not prove absent rendered
material, and scroll geometry does not identify an active material/shader.
`reference-configuration` records the chosen content, navigation and palette
scope. Compare these events with the actual REST and held display screenshots.

Apple documents that a standalone tab bar ignores `scrollEdgeAppearance` unless
managed by a `UITabBarController`, so standalone optics cannot stand in for the
production SwiftUI parent. Public API references:

- [UITabBar.scrollEdgeAppearance](https://developer.apple.com/documentation/uikit/uitabbar/scrolledgeappearance)
- [UIView.safeAreaInsets](https://developer.apple.com/documentation/uikit/uiview/safeareainsets)
- [UIViewController.additionalSafeAreaInsets](https://developer.apple.com/documentation/uikit/uiviewcontroller/additionalsafeareainsets)
- [UIScrollView.adjustedContentInset](https://developer.apple.com/documentation/uikit/uiscrollview/adjustedcontentinset)
- [UINavigationController.setNavigationBarHidden](https://developer.apple.com/documentation/uikit/uinavigationcontroller/setnavigationbarhidden(_:animated:))

No private material state is read. The iOS 27-only
`UIBarAppearance.overrideUserInterfaceStyle` and a nonexistent public
`UIBlurEffect.style` getter are not used. Public context fields do not constitute
an optical acceptance result.

#### Controlled appearance REST sequence

`-ReferenceDockAppearanceControls` plus `-ReferenceStaticOptics` opts into a
ten-stage sequence inside the existing SwiftUI four-tab parent. It retains the
same app process, hosting controller, native tab controller, tab bar and initial
Schedule selection. A small top overlay advances stages after each REST
screenshot; its taps do not target the Dock. Existing material and appearance
policies remain system owned.

`testSwiftUITabViewDockAppearanceControls` visits these stages once:

1. Dark window, opaque sRGB grayscale bytes 26, 77, 128, 179 and 230.
2. Dark window, alternating 2.5 pt vertical stripes of bytes 102/154, then 26/230.
3. The same 26/230 input with light window, then dark window.
4. The initial gray 26 input again with dark window.

Every gray byte is supplied as `(byte/255, byte/255, byte/255, 1)` RGBA.
Both stripe inputs start with their darker stripe at canvas x=0, have a 5 pt
period and a mean of 128/255 over complete periods. The 375 pt reference canvas
contains complete periods. This mean describes the uncomposited input canvas;
the native material's sampling support is unknown. Synthetic text, selected
content and the top experiment overlay remain part of the displayed scene.

Each stage waits a fixed minimum one-second observation window before becoming
ready for XCUITest capture. That budget is neither a measured adaptation time
nor a proven settled state. The next stage begins only after the screenshot and
AX attachment complete. `appearance-stage-applied` and `appearance-stage-state`
events retain the same trace clock across the full sequence. Public bar,
controller and window identities/styles are recorded every 0.25 seconds and at
ready/leaving checkpoints; forced context rows include actual canvas input and
window/screen rectangles. The test asserts identity continuity and requested
window style, while preserving the observed bar style without requiring it to
match the request. Capture time brackets and before/after public state are
attached as `appearance-rest-capture-brackets`.

The run retains ten REST PNG/AX pairs, seven unique raw input PNGs, one session
and trace, and a combined contact sheet. Static mode disables presentation-layer
recording. Do not run the held-capture observer for this test. Use existing
DerivedData and the existing iOS 26.5 simulator, run only this test with the
ordinary single-run command (omit retry/repetition flags), and bound the
xcodebuild test command to 120 seconds.
Export and audit the complete xcresult before deleting it. This sequence is an
observation of controlled inputs and public traits, not a classifier threshold,
material coefficient, motion result or parity claim.

The November 13, 2026 run completed once: one test passed in 35.800 seconds,
with 55.633 seconds for the complete test command. All ten stages retained the
same bar/controller/window identities, initial Schedule selection, tab-bar
frame `(0,584,375,83)` and full-window canvas `(0,0,375,667)`. Screenshot
before/after public state matched in every stage. Gray 230 under the dark window
reported a light effective tab-bar style; the other dark stages reported dark.
The high-contrast input followed light and then dark window changes within that
same run. Both contrast input PNGs contained equal pixel counts of their two
colors and exact full-canvas byte mean 128. This ordering does not establish a
threshold, adaptation latency, sampling support or hysteresis. Raw evidence,
complete result exports and the single contact sheet are retained under
`artifacts/glass-optics-20261112/dock-appearance-controls-63/`.

#### Focused build and static parent captures

Use the same `DerivedData` directory as the central build above; no project
regeneration is needed for these Swift-only changes. Run from this directory:

```sh
xcodebuild build-for-testing -project GlassNativeReference.xcodeproj \
  -scheme GlassNativeReference \
  -destination "platform=iOS Simulator,id=$NATIVE_GLASS_SIMULATOR_ID" \
  -derivedDataPath DerivedData -parallel-testing-enabled NO
```

Before the tests, start the existing host observer in a separate terminal. The
observer watches the UI test runner's durable
`Documents/optics-capture-signals.jsonl`, waits for actual app touch DOWN and
requests a display PNG during the six-second native hold:

```sh
python3 capture-static-optics.py --simulator "$NATIVE_GLASS_SIMULATOR_ID" \
  --output exports/swiftui-tabview-dock
```

```sh
xcodebuild test-without-building -project GlassNativeReference.xcodeproj \
  -scheme GlassNativeReference \
  -destination "platform=iOS Simulator,id=$NATIVE_GLASS_SIMULATOR_ID" \
  -derivedDataPath DerivedData -parallel-testing-enabled NO \
  -resultBundlePath SwiftUITabViewDock.xcresult \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockFineGridDark \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockTonalBlocksDark
```

Each method attaches its REST screenshot and accessibility hierarchy, checks a
real four-button native tab bar and scroll view, then holds the initially
selected `Schedule` tab for six seconds. Signal names are
`swiftui-tabview-dock-fine_grid-dark-selected-first-held` and
`swiftui-tabview-dock-tonal_blocks-dark-selected-first-held`. A screenshot after
the public press returns is a released state. A held reference is only valid
when the host capture's entire begin/end bracket lies within a delivered
DOWN/UP pair in that run's `trace.jsonl`; use `verify-static-optics.py` as for the
existing fixture. Source availability does not establish successful builds,
captures, complete production context, or native parity.

The helper also accepts `style: "light"` and names every REST attachment and
held signal with that actual style. The remaining six static parent scenes can
be captured with the same observer and DerivedData directory:

```sh
xcodebuild test-without-building -project GlassNativeReference.xcodeproj \
  -scheme GlassNativeReference \
  -destination "platform=iOS Simulator,id=$NATIVE_GLASS_SIMULATOR_ID" \
  -derivedDataPath DerivedData -parallel-testing-enabled NO \
  -resultBundlePath SwiftUITabViewDockMissingSix.xcresult \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockFineGridLight \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockTonalBlocksLight \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockColorStripesLight \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockColorStripesDark \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockLandscapeLight \
  -only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testSwiftUITabViewDockLandscapeDark
```

These methods cover REST plus a six-second hold on the initially selected
`Schedule` item, with a real four-item tab bar and the initial synthetic scroll
position. They do not run a Dock motion or selection-transition suite. Export
the complete xcresult attachments and test summary/log, copy the six uniquely
named run directories, and validate the held PNGs against delivered DOWN/end
brackets before treating them as held references; inspect whether the end is
UP or CANCEL separately.

The November 13, 2026 six-scene capture passed its native build and all six
focused tests on the iPhone SE (3rd generation), iOS 26.5 / 23F77 simulator.
Artifact-workspace-relative evidence is
`artifacts/glass-optics-20261112/swiftui-dock-parent-62/native-parent-missing-six/`.
Its `scene-index.json` links six REST/AX and verified HELD pairs to six unique
session/trace directories. In these six scenes, the last sampled public
`UITabBar` effective style matched the requested light/dark style. Each REST
hierarchy reports a 375×83 pt outer bar at `(0,584)`, a 333×62 pt platter-sized
AX container at `(21,584)`, and an 88.2×54 pt selected Schedule button at
`(25,588)`. These observations establish this static fixture's captured context,
not its material classifier, motion behavior, or production/native parity.
Five primary scenes end their held touch in delivered UP. ColorStripesDark ends
in delivered CANCEL in both its original run and one focused recapture. Both
PNGs are verified active-touch evidence, but neither supplies a strict DOWN/UP
reference. The additional test passed and its three attachments and separate
session/trace are retained with that explicit scope.

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

The eight `testAlertHeld{FineGrid,ColorStripes,TonalBlocks,Landscape}{Light,Dark}`
tests add held-action probes for that same unmodified system alert across the
four background inputs and both traits. The fine-grid entrypoints retain their
original default background, action sequence and signal names. Each test
captures the alert at rest, holds native Confirm using `opticalHold`, checks
dismissal, then reopens, captures rest, holds native Cancel and checks dismissal
again. Signal names include the actual background and trait. Run the host
`capture-static-optics.py` observer before these tests and validate the sixteen
held screenshots with `verify-static-optics.py`. A test's rest/after-action
attachments are not substitutes for the host screenshots taken during DOWN/UP.
The existing app handlers record `presentation` phases `confirmed` / `cancelled`;
validate exactly one corresponding callback after each actual action UP in the
trace. Dismissal alone does not prove those callbacks. Adding these entrypoints
does not establish a verified capture matrix; only the actual captured and
validated conditions count as evidence. The probes do not establish alert
presentation/dismissal frame parity.

## Input and presentation evidence

### Focused SwiftUI endpoint motion

`testSwiftUIEndpointDock`, `testSwiftUIEndpointSegmented`,
`testSwiftUIEndpointToggle` and `testSwiftUIEndpointSlider` capture four
independent native controls. The Dock uses the real four-item SwiftUI TabView
parent with synthetic scroll content, retaining its production-like native
containment and default material. The other three use the existing SwiftUI
Picker/Toggle/Slider fixture. This suite does not use the older standalone Dock
as the four-item reference and does not modify NativeStage or system animation.

Each method starts from its native default value without a setup touch, then
requests slow forward, slow reverse, fast forward and fast reverse as separate
straight-line drags. DOWN holds for 0.45 seconds, the destination holds for
0.25 seconds, then the public command releases. Every command is followed by at
least 1.65 seconds of additional tail. Actual quiescence/intergesture waits remain
in the trace. Raw velocity requests are 100/900 for Dock and Picker, 45/650 for
Toggle, and 90/900 for Slider; these are velocity requests, not acceleration.
Actual touch sample timestamps determine the requested replay waveform;
`deliveredT` independently records when the native app received those samples.
An outside-end CANCEL is preserved as observed and is not retried.

All drag positions are absolute window points. Dock destinations x=13/362 lie
outside the measured platter x=21...354 while remaining in the app. Other
destinations lie 12 pt beyond the AX frame, capped within x=8...367. No path is
scaled by control width. Android replay uses the raw samples with 1 pt = 1 dp
and a single fixed translation, preserving original sampleT intergesture gaps.

The initial Dock/Picker origin is its selected AX button center. Native REST
observations establish Toggle lens centers 20.5/42.5 pt from the 61 pt AX hot-zone
minX (the visual switch is 63 pt wide), and initial 25% Slider lens center
`(93.5,16)` pt inside its 335 pt AX frame. The suite uses these measured offsets,
not normalized .15/.85/.25 coordinates. Follow-up Slider origins use a
conservative measured-thumb-inset candidate. After capture, verify each actual
DOWN against the last pre-DOWN presentation lens and report actual lens center,
AX geometry and DOWN-minus-center offset. A requested center alone is not proof
of delivered centering. Enriched replay metadata must explicitly supply
`control`, `initialValue`, and `anchorNativePt`; retain the raw exported samples
unchanged, including any nonzero first-DOWN offset from that anchor.

Run only these four methods once with the existing DerivedData and iOS 26.5
simulator, in non-static mode, under one 300-second build/test command budget.
Keep one simulator video covering the four tests, each actual session/trace,
initial/final REST PNG/AX and requested gesture metadata. Export all available
xcresult content before removing the unique temporary result bundle. No UIKit
endpoint matrix or static patch experiment is part of this focused run.

The November 13, 2026 run passed all four tests in one invocation: build 4.799 s,
test command 135.643 s, combined budget use 141.703 s. All 16 delivered gestures
ended with UP (phase 3) beyond the native control/platter boundary. The initial
REST lens anchors were Dock `(69.125,615)`, Picker `(75.5,332)`, Slider
`(113.5,430)` and Toggle `(314.5,329)` pt. Actual first DOWN minus anchor was
`(-0.125,0)` pt for Dock and zero for the other three. Later Slider candidate
origins differed from their observed REST lens centers by `+1`, `-1`, and `0` pt;
the original coordinates remain unchanged. Final UP-to-last-frame tails were
4.213/3.069/3.036/3.069 s respectively. The Dock pre-DOWN traversal was truncated
but retained its platter, lens, selection and four buttons; the other three
pre-DOWN frames were untruncated. Raw evidence and complete exports are under
`artifacts/glass-optics-20261112/native-endpoint-motion-65/`, with per-gesture
geometry and timing in `endpoint-observation-audit.json`. These observations do
not establish acceleration, optical equivalence or native motion parity.

The slow Slider runs finish at `0.9768656492` / `0.0440298505`, not their
directional value endpoints. The observed extrema `0.9804266095` / `0.0402547009`
occur 86.192 / 92.815 ms after UP delivery. Fast Slider first emits `1` / `0`
105.419 / 93.136 ms after UP delivery; held-phase extrema remain
`0.9589552283` / `0.0492537320`. Thus none of these Slider held phases establishes
business-endpoint saturation. Preserve this limitation when comparing overpull.

`testSwiftUISaturatedSliderSlow` and `testSwiftUISaturatedSliderFast` add separate
launches from .75 forward and .25 reverse. `-ReferenceInitialSlider` accepts a
finite value in 0...1 for the reference SwiftUI/UIKit Slider states; its default
remains .25 and it does not alter native control behavior. Each run verifies its
configured initial value, requests a drag from the fixed measured REST anchor to
x=367/8, and retains the original trace for checking actual saturation before UP.
The November 13 batch68 passed both methods with four fresh runs. Every run
reached its business endpoint before actual UP, with no subsequent value callback.

The fixed 10pt-slop business mapping gives unequal raw terminal excesses,
10.75pt forward and11.75pt reverse. Last150ms held center minus own m41 retains
+4.495645/−4.767461pt displacement from final REST; recorded Slider/stage ancestors
remain fixed. Fast whole-window peaks about ±9pt occur at UP+67ms, not while held.
Bounds, own transform and center displacement must remain separate. These two
excess distances do not identify a general endpoint curve. The batch68 recorder
did not capture layer.position/anchorPoint/bounds.origin; batch70 adds them below.
Batch68 and the locked65
profile holdout review are under
`artifacts/glass-optics-20261112/native-slider-saturation-68/`; they do not establish
range-Slider behavior, visible optical contours or millisecond parity.

### Long-held single-Slider overdrive

`testSwiftUISliderOverdriveLongHold` launches ten independent native Slider
sessions, alternating .75 forward / .25 reverse. It requests slow nominal
0/2/5/10 pt excess and a fast nominal 10 pt repeat in both directions, holds the
destination for 1.35 s, then leaves at least 1.2 s of post-command tail. Origins use
the measured quarter-value anchors; the 10 pt startup mapping and 335 pt control
width define nominal business boundaries. Preserve actual delivered coordinates:
a nominal zero request can fall on either side of the fractional boundary.
Only positive-excess requests assert a displayed 100%/0% endpoint in the test.

The November 13 batch70 passed this one method (build 5.773 s, test 152.550 s) on
the same iOS 26.5/23F77 SE 3 simulator. Its ten delivered gestures all ended with
UP. Actual terminal positions were unchanged for at least 1.350 s. The held reference
uses only frames whose complete `captureStartT...captureEndT` bracket falls in
the final 300 ms before actual UP delivery, yielding 17 frames per run. Current
native forcing is `source=delivery`; coalesced samples are retained separately.
Do not fit a timing shift, interpolate observations, or replace actual speed
with the requested XCUITest velocity.

The recorder now emits six public presentation-layer geometry fields:
`positionX`/`positionY` in immediate-superlayer points,
`anchorPointX`/`anchorPointY` in unit bounds coordinates, and `boundsX`/`boundsY`
as local bounds origins. Session metadata declares
`layerGeometryFieldsVersion=1` and `layerGeometryCoordinates`. These fields allow
a public center/position/anchor/transform decomposition; they do not reveal a
private native animation target. Sampled stage, UISlider and glass visual-element
geometry stayed fixed in this batch. Unrecorded wrappers and extra CALayer
ancestors are outside that claim.

Actual saturated excesses were forward .25/2.25/5.25/10.25 pt and reverse
1.75/4.75/9.75 pt. Corresponding final-held public position shifts from each run's
final REST were +.154295/+1.273258/+2.641662/+4.353381 pt and
−1.011321/−2.435056/−4.206609 pt. Same-excess fast/slow terminal baselines were
identical. The nominal zero reverse instead had actual excess −.25 pt and held
fraction .000746268663; its +.222388 pt offset is ordinary remaining business
travel, not endpoint overdrive. Its three post-UP callbacks were 0,
.000114750030 and .000746268663, while final carrier center was 38.5 pt. Retain both
observations without inferring an internal rounding or snapping mechanism.

A retrospectively selected rubber family with gain .62425 and limit 13.62 pt,
`shift=limit*(1-1/(1+gain*positiveExcess/limit))`, derives from forward .25/10.25 pt
and agrees with the remaining eligible batch70/batch68 held baselines to
2.043e−13 pt. Those checks are not blind holdouts. Scope is native 335 pt width and
298 pt resting-thumb travel; other-width behavior needs its own evidence. The
long-held fast runs still peak at +8/−7 pt after UP, near 84.607/48.781 ms bracket
midpoints. The held rule does not explain these release excursions or identify a
release-velocity law.

Evidence lives under `artifacts/glass-optics-20261112/native-slider-overdrive-70/`:
`independent-overdrive-review` retains 232/232 invariant checks, actual deliveries,
carrier frames and final 300 ms rows; `independent-endpoint-family-review` verifies
the retrospective family directly from raw public positions and trace hashes.
Outer carrier bounds/matrices do not establish the visible alpha contour or
optical equivalence. Android batch71 separates business and visual endpoint
targets and uses an affine visual child. Its base-capsule contour remains a
hypothesis, not proven native material clipping.

Batch71 host verification is recorded separately: the old endpoint behavior
produced 3 assertion failures in 10 tests, and a same-bounding-box circular-resize
counterfactual produced a real shoulder-pixel assertion failure in one test.
The first focused run executed 434 tests: 433 passed, one geometry-read failure,
zero errors/skips. That helper used transformed position plus untransformed
layout size. After changing only the geometry reader to actual semantic
`boundsInRoot` normalized by actual density, all three Thumb tests passed with
unchanged expectations/tolerances. Debug and androidTest APKs built in the same
26-second command. This is not a fresh passing 434-test run. Host XML, logs and
restored-source hashes are in `slider-endpoint-affine-71/{endpoint-red,green-first,affine-green}`
and the adjacent `affine-red-receipt.json` / `affine-green-build.log` artifacts.
These native-graphics Robolectric regressions do not establish device GPU optics
or native frame parity.

Android71 subsequently replayed all ten native70 sources on the API36 phone:
ten instrumentation tests passed, and all 50 trace-file hashes plus the single
raw-video hash matched host/device copies. The installed main APK SHA256 is
`6e20a77fcf8c926bda77f960d5b238bad5688c829bf294574e1b04ba33bd6d66`;
the test APK remains `50c6fa1b864d980e7e7c872743b613746822da8ad00d3fd7c8d05d510d39d97c`.
Independent source and Android audits passed 192/192 and 152/152 checks. The ten
sources contain 568 unique actual samples (538 main deliveries, 30 unique
coalesced samples); every injection is recovered in app current/history. Fixed
anchor translation, 1 pt = 1 dp and original sample intervals are preserved.
Actual current coordinates and receipt timing differ across systems; source
integrity is not proof of an identical received waveform.

For the nine saturated cases, stable held-minus-final-REST center displacement
differs by at most .170985 dp/pt. The separate unshifted whole-window real-frame
comparison still has center-X RMSE 1.642–5.498 dp. Neither metric measures native
visible corners or material parity. The nominal-zero reverse case remains a
separate forcing mismatch: native held value is nonzero, while Android's last
pressed CURRENT lies .649414 px (.218290 dp) beyond its mechanically reconstructed
zero threshold; its HISTORY and later UP lie inside. Independent Float
reconstruction gives raw fractions −.0006513595581 versus +.0008404105902 and
matches business callbacks within about 1.77e−8. Internal raw target is not logged.
No later pressed MOVE corrects it during the final 1.356 s; the source releases
the existing target before reevaluating/publishing UP, and Android has no post-UP
business callback. The trace does not prove which platform mechanism produced
the CURRENT/HISTORY difference, and this is not an equal-input regression claim.

See `slider-endpoint-affine-71/independent-input-audit` for the device observations
and its 10/10 zero-reverse input/source reconstruction, and `geometry-review` for
the unshifted raw comparison. Unexplained native 8/7 pt release excursions,
full visible contours/corners, optics, other widths and independent RangeSlider
motion remain open. See the alignment document for implementation scope.

### Batch72 receipt-timed four-item Dock parent translation

Android now has a separate `NativeDockParentMotion` for whole-parent translation.
The frozen native65 candidate is `u=.010287932530511721*(fingerX-downX)` and
`x''+2*zeta*omega*x'+omega^2*x=omega^2*u`, with omega=9.488283178142856/s,
zeta=.6099720458279732 and unit-mass stiffness=90.0275176686287. Only slow-forward
gesture 0 trained the candidate. Slow reverse and both fast gestures are frozen
holdout calculations; they had already been inspected for mechanism witnesses
and are not prospectively blind. No parent scale/anisotropy fit was installed.

| Native65 gesture | Split / observations | Active RMSE pt | Max absolute pt | Last 150 ms RMSE pt |
| --- | --- | ---: | ---: | ---: |
| Slow forward | train / 209 | .044699370 | .156530395 | .150339528 |
| Slow reverse | holdout / 214 | .044642326 | .156119182 | .148561294 |
| Fast forward | holdout / 57 | .291415316 | .586557971 | .212318315 |
| Fast reverse | holdout / 61 | .294340323 | .598668836 | .249824567 |

Evidence is bounded to a 333 x 62 pt SwiftUI four-tab parent, resting starts,
monotonic gestures ending at |DOWN displacement| = 293 pt and about 250 ms endpoint
holds. The linear candidate has no new endpoint clamp. Other Android four-tab
widths explicitly extrapolate the same physical-dp response, rather than a
measured native width law. Existing Android PressSpring return on UP/CANCEL or
external selection and x/v continuity on interrupted DOWN are implementation
policies. Rapid reversals, native interruption/release mechanisms, parent scale,
visible contours and optics remain unestablished by this fit.

Production input is actual window X divided by density, obtained through the
outer pointer owner's `localToWindow` for DOWN and every delivered MOVE,
including moves below the 4 px lens-drag threshold. Physical +X stays +X in RTL.
The parent queue integrates receipt segments, including receipt-to-first-frame
elapsed time, without replacing receipt by hardware sample time or fitting a
delay. The original seven Dock springs retain their first-eligible-frame-end
Press origin. Animation scale is applied once; scale 0 settles the current
transaction and epoch checks reject stale input after cancellation/new DOWN.

Output inverse-maps the desired window vector through the same owner's
`windowToLocal` before assigning the moving layer's translationX/Y. This implements
a static invertible caller affine mapping. Exercised caller cases are horizontal
scales 0.8/1.2; rotation, shear and dynamic ancestor changes have no new native
validation. Density is a
pointer-handler key so anchors cannot survive a metric change. The three-tab
pull translation and original parent scale expressions remain unchanged.
`dock-parent` tags the whole 333 x 86 dp reference carrier, including 12 dp shadow room
above/below its 62 dp base. Its rectangle center measures translation, not visible
alpha/plume support. Painted lens semantics also observe the new parent state.

The bounded `app/src/test/resources/native-glass/dock-parent-{input,reference}-65.csv`
fixtures retain 362 main deliveries and 541 observations with raw source lines,
frames, getter brackets, actual finger coordinates, native translation and frozen
residuals. `dock-parent-65.md` and batch72 `helper-reference/manifest.json` retain
source hashes and derivation. The helper rest=1e-9 and replay tolerance 1e-4 dp are
numerical stopping/Float-and-clock checks, never native parity tolerances.

Host verification is one fresh focused 133-test run, not the complete suite:

- An initial SDK-path infrastructure failure executed no tests and is retained
  separately. After environment setup, API-compatible no-op RED ran 15 tests:
  12 actual assertion failures, 0 errors/skips; compilation/resource discovery passed.
- First focused implementation run: 133 tests, 132 passed, 1 assertion failure,
  0 errors/skips. All 15 helper cases passed; APK assembly had not run yet.
- Single-test diagnostic: the same assertion failed. Correct Explore/root touch
  coordinates and wholly resting press geometry identified a manual-clock wake
  issue. The fixture now drains snapshotFlow, delivers/synchronizes the first
  Press-origin frame, then advances the original 2500 ms hold. Assertions and
  diagnostic data were retained; no runtime change was made for that failure.
- Fresh rerun: 133/133 passed, 0 failures/errors/skips; debug and androidTest APKs
  built in the same approximately 32-second command. Filters were `*Dock*Test`,
  `*GlassSlider*Test`, `*NativeSlider*Test`, `*TimedGlassControl*Test`.

Logs/XML are under batch72 `parent-red`, `parent-green-first`,
`parent-direct-diagnostic`, and `parent-green`; installed package hash receipts
are separate in `installed-apk-receipt.json`. The 15 helper and 7 new Compose
contracts cover timing, lifecycle, production four-tab/three-tab input paths,
RTL and static caller scale. A precommit CRLF-to-LF normalization regenerated
both CSVs with every parsed value unchanged; the exporter now fixes LF. A separate
15-case helper rerun after that format change passed in 10 s, with no runtime or
installed APK change. It is not combined into another 133-case result.

Dock72 actually replayed all four gestures, exit 0, with all five trace-file
and unique video hashes matching the recorded device hashes. On the same 540
native rows whose nearest Android draw is wholly held in both versions, center-X
RMSE for slow forward/reverse and fast forward/reverse changed from Android69
3.579757/3.587388/4.666596/4.245231 dp to .046427/.047706/.198231/.212943 dp.
One native fast-reverse row has a nearest72 draw after UP and remains separate
in the complete541-row report; no farther draw was substituted. Pairing uses
actual DOWN receipts and nearest actual getter midpoints, without interpolation,
delay fitting or parameter refitting. Dispatch histories differ, and real draw
phase gaps reach25.711 ms. These carrier-center improvements do not validate
native optics, parent scale, release/interruption or a general width law. Exact
brackets, forcing differences, repeated matches and independent raw audit are in
batch72 `independent-dock-parent-review/device-parent-comparison/`.

### Measured-center SwiftUI Slider optical captures (batch72)

Eight focused methods retain the native SwiftUI Slider's natural parent and
initial 25% value on four backgrounds in both traits:

| Background | Light method | Dark method |
| --- | --- | --- |
| Fine grid | `testSwiftUISliderOpticsFineGridLight` | `testSwiftUISliderOpticsFineGridDark` |
| Color stripes | `testSwiftUISliderOpticsColorStripesLight` | `testSwiftUISliderOpticsColorStripesDark` |
| Tonal blocks | `testSwiftUISliderOpticsTonalBlocksLight` | `testSwiftUISliderOpticsTonalBlocksDark` |
| Synthetic landscape | `testSwiftUISliderOpticsLandscapeLight` | `testSwiftUISliderOpticsLandscapeDark` |

Each fresh launch asserts 375 x 667 pt and 335 pt control width, captures REST,
requests a 6-second press at the measured AX-local `(93.5,16) pt` lens anchor,
checks the value remains 25%, and captures released REST. Actual source72 target
frames are `(20,414,335,31) pt` and DOWNs are `(113.5,430) pt`. These measured-center captures
are distinct from older generic AX-normalized `.25/.5` Slider holds; do not
silently substitute those older coordinates or images.

The November 13, 2026 native batch built in 5.159 s and ran all eight methods in
170.879 s, both exit 0. Host observer PNGs were verified against actual delivery
DOWN/UP brackets: 8/8 held captures valid, no unverified/missing runs; capture
began 651.945–661.943 ms after actual DOWN. Complete sessions/traces, xcresult
attachments, image hashes, capture signals and held verification are under
`artifacts/glass-optics-20261112/android-dock-parent-response-72/native-slider-optics/`.

The Android companion
`NativeSliderOpticalWorldRenderTest#captureNativeSliderOpticalWorld` has also
actually completed with exit 0: 8 background/theme cases, 16 REST/HELD control
captures plus 8 background baselines, Window/display PNGs stored independently.
All 49 exported files (48 PNGs and result.json) match their device hashes.
The production 335 dp single Slider uses a 375 x 667 dp source world on ordinary
backing, with actual holder/carrier/world corners and unit basis exported.
This fixture uses a synthetic Compose clock to settle optical states, not a
natural-time motion measurement. A single sampling-only translation, bounded
to 1 dp per axis from the first raw REST center, is reused unchanged across all
backgrounds/themes/states; the actual correction was (+.22270203,-.08401489) dp.
It moves neither the backing world nor production dimensions and cannot serve
as a native geometry match. Input is stationary center DOWN, capture while held,
then cleanup CANCEL; value remains .25 with zero value/finish callbacks. Android
records injection times and active held state, not an independent raw platform
receipt trace.

Android capture data and hash receipts are under batch72 `android-slider-optics/`;
installed APK hashes are in `installed-apk-receipt.json`. Independent same-world
comparison found visible excess saturated rainbow bands, a bright lower edge,
and excess dark-theme inner brightness. Source-baseline RGB MAE is.482–1.338/255;
held lower-inner windows excluding the declared rail/title roles still differ
by6.875–23.838/255. At the batch72 capture, Slider aliased the Dock lens factory; that source
coupling is verified, but it is not proved the sole cause. No optical runtime was
changed in this batch. Current31 pt AX bounds do not establish the visible
ellipse/corner radius, and no alpha mask was invented. See batch72
`independent-slider-optical-review/findings.md` for frozen windows, all8 cases,
source hashes, theme response and single-variable followup experiments.
Capture validity remains separate from material/shape acceptance and supplies
no native optical equivalence or release-motion result.

Production73 subsequently separated `sliderLens` from Dock. Production75 adopts
the previously captured74 half-depth F (`refraction=-2.15`) as a bounded
single-Slider correction after source-associated lower-feature review. The
extra inward tonal/grid segment is reduced; the primary red/yellow edge still
differs by one0.5pt sample bracket, and all17 diagnostic regional MAE increases
remain recorded. This is not identification of native depth or a full optical
acceptance. Range and the other control factories remain unchanged. The fresh75
production capture matches all24 F App Window images and all24 original nonbar
display domains. Strict complete-PNG equality remains24/48 because system-bar
pixels differ. See alignment notes75 and this session's
`android-native-range-75/{feature-audit,production-comparison}` evidence.

The [local library inventory](../../libraries/liquid-glass/README.md) and
[asset catalog](../../libraries/liquid-glass/asset-catalog.json) describe the
current candidates for future extraction. They are inventory only, not an
independently buildable or published library. Existing source attribution and
unresolved provenance remain preserved; no license change is implied.

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

`testAlertMotionFineGridLight` / `Dark` repeat real Alert presentation, held
Confirm/Cancel and dismissal with `staticOptics` disabled, so the display-link
recorder retains actual presentation observations. The held screenshot endpoint
alone supplies no animation timing. Inspect capture cost, frame gaps, touch
receipt and presentation geometry before fitting any curve.
`testAlertHeldTonalBlocksDarkConfirmRecapture` supplies a separate fixed 6-second
hold for a host screenshot whose earlier 2.6-second press ended before screenshot
completion. The original rejected capture remains rejected; the new capture
still needs the same complete DOWN-to-UP timestamp-bracket validation.

`testAlertHeldLandscapeLightLongPressProbe` is a separate 0.7-second public
hold/release of the alert trigger, followed by fixed 6-second held action captures.
It does not replace `testAlertHeldLandscapeLight` or turn its two cancelled
short-tap deliveries into a pass. The probe can supply a background/material
reference only if its own actual touch and capture brackets verify.


### Opt-in single-Slider public model-layer probe75

`testSwiftUISliderReleaseLayerProbe` repeats the existing slow90/fast900 forward
nominal10pt-excess pair, with1.35s destination hold and1.2s post-command tail.
Only these launches pass `-ReferenceSliderLayerProbe`. In Slider scenes this
flag records every UIView-backed ancestor, public model-layer geometry under
`modelGeometry`, and existing public CAAnimation fields. Ordinary captures keep
their previous traversal scope. No native layer, animation or tracking state is
changed. Session metadata declares the flag and its sequential-read scope.

The November14 batch75 built in5.854s and completed the selected test with exit0
(29.400s test case;65.081s whole xcodebuild command). Both fresh sessions confirm
iOS26.5/23F77 and375x667pt. Named log activities plus session chronology identify
the requested speeds; actual MOVE sample spans are1166.667/100ms. Both delivered
terminal x366.5, corresponding to10.25pt beyond the nominal356.25 threshold.

Both final-held public model/presentation positions are4.353380581pt beyond
final REST. Fast reaches +8pt in both position channels at the complete getter
bracket UP+83.103..85.307ms; slow never exceeds its held presentation position,
and its post-UP model maximum is+4pt. No post-UP business callback was observed.
The lens and eight recorded view-backed ancestors have empty animation keys,
and those ancestors stay fixed. This supports an empirical changing model-state
position response; it does not identify a private native mechanism, target,
retained velocity law, or the absence of animations in unrecorded CALayer nodes.

All9 manifest file hashes/sizes pass independent checks. Neither run truncates
traversal. Median capture cost is2.018/2.232ms (slow/fast), with early maxima
29.385/26.010ms. Getter brackets, actual input and observer overhead are retained.
The raw video is contextual; no synchronized video contour acceptance is claimed.
Evidence and independent reproduction live in this session's
`android-native-range-75/release-probe`. Android motion was not changed by this
probe, and the release excursions remain an open alignment item.

### Opt-in host-composed paired UISlider reference

`-ReferenceScene range` launches two genuine public `UISlider` controls over
one host-painted rail. The ordinary six-item scene selector stays unchanged;
the range scene has no selected segment. Each slider keeps its system thumb
drawing and native tracking, with its own track tints set clear through public
properties. The host supplies the common rail, endpoint ordering and touch
routing. This composition is an experimental reference, not an Apple range API.

| Launch argument | Behavior |
| --- | --- |
| `-ReferenceScene range` | Enables the paired scene explicitly. |
| `-ReferenceRangeScale 1` or `0.75` | Uniformly transforms the entire UISlider, including its thumb and tracking coordinate space; default `1`. |
| `-ReferenceRangeLower <value>` | Initial lower value; default `0.25`. |
| `-ReferenceRangeUpper <value>` | Initial upper value; default `0.75`. |
| `-ReferenceRangeGeometryProbe` | In range scenes, retains every visited UIView's public model geometry plus cached slider/host layout snapshots, within the existing traversal bounds and capture brackets. |

Initial values are clamped to `0...1` and ordered. Both native slider domains
remain `0...1`; the host clamps value callbacks to preserve `lower <= upper`.
Inside the rail interaction band, host `hitTest` selects the nearest model
thumb center, then returns that slider's own public `hitTest` result, including
nil. The selected slider decides whether to hit itself, a native child or
nothing. At collapsed endpoints, contact left of center selects lower, contact
right selects upper, and an exact-center tie selects lower. Later movement
does not transfer the live touch to the other slider. Range touch rows retain
`hitAncestors` from actual `touch.view` through the public `superview` chain,
plus `rangeControlIdentifier` / `rangeEndpoint` when a slider ancestor exists.
Native child identifiers are unchanged; ownership must be checked through
ancestry rather than requiring the child's own identifier to equal the slider ID.

Initial or resized idle layout performs two fresh endpoint measurements. Public
values are temporarily set to 0 and 1 so UIKit requests native layout; a
read-only `thumbRect` override caches and returns `super` unchanged. One width
compensation occurs between the measurements. The final observed endpoint span,
multiplied by the exact requested scale, defines the shared rail centered on the
desired rail. Desired span is `stageWidth - 40` pt; it is not a convergence
requirement. `calibration.measurements` retains each pass, and `desiredSpanPt`,
`actualSpanPt` and signed `spanDeltaPt` report the realized residual. Missing or
invalid fresh anchors disable the controls. Values are restored before display;
calibration does not run during tracking.

Use the central build/test commands with
`-only-testing:GlassNativeReferenceUITests/NativeReferenceTests/testPairedNativeSliderSeparatedReference`.
This method requests two fresh launches, scale 1 then .75, each with .25/.75
endpoints, lower/upper six-second held-capture signals, a lower slow outward
drag and an upper fast outward drag. The existing host optical observer is
needed for held PNGs; REST and released XCTAttachments do not establish held
capture timing. The separate
`testPairedNativeSliderCollapsedRoutingReference` probes left/right DOWN
selection in fresh collapsed sessions and is not part of that focused run.

The real sliders use `native-range-lower` / `native-range-upper` accessibility
identifiers. Their public screen-coordinate accessibility frames follow the
cached model thumb rectangles. Those rectangles, UIView model/presentation
geometry and stage/window coordinates are public layout observations, not
visible alpha contours or material coefficients. Source availability alone
does not establish calibration, native child ownership, held screenshot
validity, optical parity, cancellation, crossover or release behavior on a
particular OS build.

The November14 batch76 used iOS26.5/23F77, SE3 375x667pt, light/grid.
Build passed in4.697s; the selected test passed in71.247s (77.388s command).
All four complete host screenshot brackets fell inside genuine DOWN-to-UP
intervals. Actual hits were native lens children with the correct slider
ancestor; target tracking and peer idle held throughout all complete bracket
frames. Independent saved-raw and source/file hash audits passed. Observed
shared spans were295pt at scale1 and294.75pt at scale.75, retaining the latter's
-.25pt residual. Stationary holds had zero value changes and one unchanged
callback after UP each. Drag releases continued value/tracking observations
after UP, including fast upper values reaching1; no general release law or
Android range calibration is inferred. See the batch76 section in
../../docs/native-glass-control-alignment.md for values, failed attempts and limits.


### Paired native range optical matrix

The eight explicit `testPairedNativeSliderOptics{FineGrid,ColorStripes,TonalBlocks,Landscape}{Light,Dark}` tests each launch a fresh range scene at whole-control scale `.75` with `.25/.75` endpoints. Each endpoint receives one six-second center hold. Run the host optical observer together with these selected tests; only complete host PNG brackets inside delivered DOWN/UP establish held images. Normal presentation traversal and the range geometry probe remain enabled. The ordinary six-item scene selector and existing test bodies are unchanged. These eight methods are XCTest-discovered: an unfiltered reference suite includes all eight; use `-only-testing` for a bounded capture run.

Record actual pre-touch REST and held model/presentation centers separately: UIKit may move the native center slightly when tracking begins. Do not replace either with ideal rail fractions or use these carrier rectangles as visible alpha contours. This non-static capture path does not export a fresh `background-input.png`; retain the source/world metadata and limit background comparisons to independently justified regions.

The Android counterpart is the opt-in `NativeRangeOpticalWorldRenderTest#captureNativeRangeOpticalWorld`. After building/installing the debug and androidTest APKs on an API33+ hardware device, run:

```sh
adb shell am instrument -w -r \
  -e class com.lingion.sleepy.ui.component.NativeRangeOpticalWorldRenderTest#captureNativeRangeOpticalWorld \
  -e glassRangeOpticalWorld true \
  -e glassRangeOpticalRunId range-optics \
  com.lingion.sleepy.debug.test/com.lingion.sleepy.SleepyRenderTestRunner
```

It exports `range-optical-world/<runId>` under the debug app's external files directory: four backgrounds × light/dark, plain background pairs and REST/lower-held/upper-held Window/display pairs, actual world/endpoint/rail geometry, callbacks and capture readiness. It uses the production Range/input path with a synthetic settled Compose clock; cleanup CANCEL differs from native UP. It is stable optical evidence, not a natural timing or release test. The fixed375×667dp world and holder placement are fixture context; no per-endpoint translation, shape or material override is applied.

The default `glassRangeOpticalPalette=product` retains Sleepy's theme. Optional `-e glassRangeOpticalPalette native` changes only the fixture's primary role to the reference tint `(0,.65,.75,1)` and records the consumer's actual RGBA. This separate diagnostic reduces active-rail tint differences; inactive roles and the Android8dp/native-host6pt rail can still differ. Preserve both arms and the unmatched context when reporting errors. Neither the host-composed range nor a passing capture matrix establishes full native parity.
