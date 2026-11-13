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
