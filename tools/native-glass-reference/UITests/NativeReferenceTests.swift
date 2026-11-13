import XCTest

/// All gestures use public XCUITest APIs. Requested straight-line gestures and
/// actual delivered touch waveforms are separate records; no synthesized curves.
final class NativeReferenceTests: XCTestCase {
    private var app: XCUIApplication!
    private var requests: [[String: Any]] = []
    private var settleDuration: TimeInterval = 0.65

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        requests = []
        settleDuration = 0.65
    }

    override func tearDown() {
        if let data = try? JSONSerialization.data(withJSONObject: requests, options: [.prettyPrinted, .sortedKeys]) {
            let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
            attachment.name = "requested-gestures"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        super.tearDown()
    }

    func testToggleGrid() { toggle(background: "grid") }
    func testToggleStripes() { toggle(background: "stripes") }
    /// Supplement the Toggle-only input evidence. A scene named "toggle" can
    /// also contain the tab bar; this method exclusively targets real switches.
    func testToggleHeldDirectionAndRapidProbe() {
        settleDuration = 1.1
        for identifier in ["swiftui-toggle", "uikit-toggle"] {
            launch(scene: "toggle", background: "grid", focused: true)
            let control = app.switches[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))

            func setValue(_ desired: Bool, phase: String) {
                let expected = desired ? "1" : "0"
                let observed = control.value as? String
                XCTAssertTrue(observed == "0" || observed == "1", "Expected a native switch value for \(identifier)")
                if observed != expected {
                    tap(control, label: "\(identifier): setup \(phase), value \(expected)")
                }
                XCTAssertEqual(control.value as? String, expected)
            }

            // Each public command holds, moves, holds at its destination, then
            // releases. Reverse is a NEW gesture after UP, not a continuous
            // out-and-back path. Keep that distinction in both labels and data.
            for (velocity, phase) in [(45.0, "slow"), (650.0, "fast")] {
                setValue(false, phase: phase + " horizontal pair")
                drag(control, from: CGVector(dx: 0.15, dy: 0.5), to: CGVector(dx: 0.85, dy: 0.5),
                     velocity: velocity, label: "\(identifier): \(phase), held off thumb then forward / release")
                XCTAssertEqual(control.value as? String, "1")
                drag(control, from: CGVector(dx: 0.85, dy: 0.5), to: CGVector(dx: 0.15, dy: 0.5),
                     velocity: velocity, label: "\(identifier): \(phase), new held on thumb then reverse / release")
                XCTAssertEqual(control.value as? String, "0")
            }

            // This ends in a real UP outside the switch. It probes tracking
            // cancellation semantics, but is not a delivered touch CANCEL.
            for initialOn in [false, true] {
                setValue(initialOn, phase: "vertical outside probe")
                let start = control.coordinate(withNormalizedOffset: CGVector(dx: initialOn ? 0.85 : 0.15, dy: 0.5))
                drag(from: start, to: start.withOffset(CGVector(dx: 0, dy: -100)), velocity: 160,
                     label: "\(identifier): value \(initialOn), hold thumb then leave vertically / release probe",
                     target: control)
                request("\(identifier): observed value after outside UP", kind: "observation", target: control,
                        extra: ["observedValue": control.value as? String ?? "unavailable"])
            }

            setValue(false, phase: "consecutive taps")
            // XCUITest may wait for quiescence between these commands. No
            // explicit pause is added, but actual DOWN gaps, not this source
            // order or the probe's name, decide whether a rapid burst occurred.
            for index in 0..<6 {
                request("\(identifier): consecutive tap probe \(index)", kind: "consecutiveTapProbe", target: control)
                control.tap()
            }
            pause()
            request("\(identifier): public double-tap probe", kind: "doubleTapProbe", target: control)
            control.doubleTap()
            pause()
            captureLanguage(identifier + "-held-direction-and-rapid-result")
            app.terminate()
        }
    }
    func testSegmentedGrid() { segmented(background: "grid") }
    func testSegmentedStripes() { segmented(background: "stripes") }
    func testSliderGrid() { slider(background: "grid") }
    func testSliderStripes() { slider(background: "stripes") }
    func testButtonsGrid() { buttons(background: "grid") }
    func testButtonsStripes() { buttons(background: "stripes") }
    func testTabBarGrid() { tabBar(background: "grid") }
    func testTabBarStripes() { tabBar(background: "stripes") }
    func testPresentationsGrid() { presentations(background: "grid") }
    func testPresentationsStripes() { presentations(background: "stripes") }
    func testLanguageMenuReference() {
        settleDuration = 1.05
        launch(scene: "language", background: "grid")
        let anchor = app.buttons["language-menu"]
        XCTAssertTrue(anchor.waitForExistence(timeout: 5))
        for (index, choice) in ["English", "Español"].enumerated() {
            let phase = index == 0 ? "first-use" : "warmed"
            tap(anchor, label: "language \(phase): tap open")
            let menu = languageMenu()
            XCTAssertTrue(menu.waitForExistence(timeout: 5))
            for label in ["简体中文", "繁體中文", "English", "日本語", "Español"] {
                XCTAssertTrue(menu.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch.exists)
            }
            captureLanguage("language-\(phase)-open")
            tap(menu.buttons.matching(NSPredicate(format: "label == %@", choice)).firstMatch,
                label: "language \(phase): choose \(choice)")
            XCTAssertTrue(anchor.waitForExistence(timeout: 5))
            XCTAssertEqual(app.staticTexts["language-selection"].label, "Selected: \(choice)")
            XCTAssertFalse(menu.exists)
            captureLanguage("language-\(phase)-selected")
        }
        tap(anchor, label: "language: tap open for outside cancellation")
        let menu = languageMenu()
        XCTAssertTrue(menu.waitForExistence(timeout: 5))
        let japanFrame = menu.buttons.matching(NSPredicate(format: "label == %@", "日本語")).firstMatch.frame
        outsideLanguageMenu(menu, label: "language: tap outside cancel")
        XCTAssertEqual(app.staticTexts["language-selection"].label, "Selected: Español")
        captureLanguage("language-outside-cancelled")

        hold(anchor, label: "language: long press anchor then release")
        XCTAssertTrue(languageMenu().waitForExistence(timeout: 5))
        captureLanguage("language-long-press-open")
        outsideLanguageMenu(languageMenu(), label: "language: cancel held-open menu")
        let destination = app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: japanFrame.midX, dy: japanFrame.midY))
        drag(from: anchor.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
             to: destination, velocity: 100,
             label: "language: hold anchor then slide into 日本語 and release", target: anchor)
        XCTAssertEqual(app.staticTexts["language-selection"].label, "Selected: 日本語")
        XCTAssertFalse(languageMenu().exists)
        captureLanguage("language-slide-release-result")
    }

    func testLanguageMenuSameChoiceAndCancellationProbe() {
        settleDuration = 0.8
        launch(scene: "language", background: "grid", focused: true)
        let anchor = app.buttons["language-menu"]
        tap(anchor, label: "language focused first-use: tap open")
        let menu = languageMenu()
        XCTAssertTrue(menu.waitForExistence(timeout: 5))
        tap(menu.buttons.matching(NSPredicate(format: "label == %@", "简体中文")).firstMatch,
            label: "language focused first-use: choose current 简体中文")
        XCTAssertEqual(app.staticTexts["language-selection"].label, "Selected: 简体中文")
        tap(anchor, label: "language focused warmed: tap open")
        XCTAssertTrue(languageMenu().waitForExistence(timeout: 5))
        outsideLanguageMenu(languageMenu(), label: "language focused warmed: outside cancel")

        // Public XCUITest may wait for animation quiescence between commands.
        // Delivered touches determine whether this lands during opening; a pair
        // of adjacent test statements does not prove an early cancellation.
        let outside = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: 20, dy: 120))
        request("language: immediate outside-cancel probe", kind: "sequentialTapProbe", target: anchor)
        anchor.tap()
        outside.tap()
        pause()
        captureLanguage("language-immediate-outside-probe")
        if languageMenu().exists {
            outsideLanguageMenu(languageMenu(), label: "language: dismiss remaining probe menu")
        }
    }

    private func languageMenu() -> XCUIElement {
        app.collectionViews.containing(.button, identifier: "Español").firstMatch
    }

    private func outsideLanguageMenu(_ menu: XCUIElement, label: String) {
        let frame = app.windows.firstMatch.frame
        let candidates = [CGPoint(x: frame.minX + 20, y: frame.minY + 120),
                          CGPoint(x: frame.maxX - 20, y: frame.maxY - 120)]
        guard let point = candidates.first(where: { !menu.frame.insetBy(dx: -12, dy: -12).contains($0) }) else {
            XCTFail("Native menu leaves no safe outside target"); return
        }
        let outside = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: point.x, dy: point.y))
        request(label, kind: "outsideTap", target: menu, extra: ["point": xy(outside.screenPoint)])
        outside.tap()
        pause()
        XCTAssertFalse(menu.exists)
    }

    private func captureLanguage(_ name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = name + "-accessibility"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
    }

    /// Distinguish direct selection preview from committed selection. Each kind
    /// starts in a fresh process with A selected; no horizontal drag is involved.
    func testDirectSelectionPhases() {
        directSelectionPhases(identifiers: ["swiftui-segmented", "uikit-segmented", "uikit-tabbar"])
    }

    func testDockDirectSelectionPhases() {
        directSelectionPhases(identifiers: ["uikit-tabbar"])
    }

    private func directSelectionPhases(identifiers: [String]) {
        settleDuration = 1.65
        for identifier in identifiers {
            let isTabBar = identifier == "uikit-tabbar"
            launch(scene: isTabBar ? "toggle" : "segmented", background: "grid")
            let control = isTabBar ? app.tabBars[identifier] : app.segmentedControls[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            let first = control.buttons[isTabBar ? "Home" : "First"]
            let second = control.buttons[isTabBar ? "Explore" : "Second"]
            XCTAssertTrue(first.exists)
            XCTAssertTrue(second.exists)
            if isTabBar { captureLanguage("dock-three-tab-natural-layout") }
            tap(second, label: identifier + ": A selected, short tap B")
            tap(first, label: identifier + ": reset A after short tap")
            hold(second, label: identifier + ": A selected, hold unselected B without drag / release")
            tap(first, label: identifier + ": reset A after hold B")
            hold(first, label: identifier + ": hold selected A without drag / release")
            let start = second.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            let outside = start.withOffset(CGVector(dx: 0, dy: -100))
            drag(from: start, to: outside, velocity: 160,
                 label: identifier + ": A selected, hold B then leave vertically / release (cancel probe)",
                 target: second)
        }
    }

    func testDragVelocityMatrix() {
        dragVelocityMatrix(identifiers: ["uikit-segmented", "uikit-tabbar"])
    }

    func testDockAdjacentVelocityMatrix() {
        dragVelocityMatrix(identifiers: ["uikit-tabbar"], adjacent: true)
    }

    private func dragVelocityMatrix(identifiers: [String], adjacent: Bool = false) {
        settleDuration = 1.65
        for identifier in identifiers {
            let isTabBar = identifier == "uikit-tabbar"
            launch(scene: isTabBar ? "toggle" : "segmented", background: "grid")
            let control = isTabBar ? app.tabBars[identifier] : app.segmentedControls[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            let first = control.buttons[isTabBar ? "Home" : "First"]
            let last = control.buttons[isTabBar ? (adjacent ? "Explore" : "Settings") : "Third"]
            for repetition in 1...2 {
                for (forward, velocity, phase) in [
                    (true, 100.0, "slow forward"), (false, 100.0, "slow reverse"),
                    (true, 900.0, "fast forward"), (false, 900.0, "fast reverse")
                ] {
                    let source = forward ? first : last
                    let destination = forward ? last : first
                    let label = "\(identifier): matrix \(repetition), \(phase)"
                    tap(source, label: label + ": select source")
                    drag(from: source.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                         to: destination.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                         velocity: velocity, label: label, target: control)
                }
            }
        }
    }

    func testFourTabFirstLastMotion() {
        settleDuration = 1.65
        launch(scene: "toggle", background: "grid", tabCount: 4)
        let control = app.tabBars["uikit-tabbar"]
        XCTAssertTrue(control.waitForExistence(timeout: 5))
        XCTAssertTrue(control.buttons["Courses"].exists)
        let first = control.buttons["Home"]
        let last = control.buttons["Settings"]
        captureLanguage("dock-four-tab-natural-layout")
        tap(last, label: "four-tab: first to last direct tap")
        tap(first, label: "four-tab: last to first direct tap")
        hold(last, label: "four-tab: hold unselected last then release")
        hold(first, label: "four-tab: hold unselected first then release")
        hold(first, label: "four-tab: hold selected first then release")
        let cancelStart = last.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        drag(from: cancelStart, to: cancelStart.withOffset(CGVector(dx: 0, dy: -100)), velocity: 160,
             label: "four-tab: hold unselected last then leave vertically / release (cancel probe)", target: last)
        for repetition in 1...2 {
            for (forward, velocity, phase) in [
                (true, 100.0, "slow forward"), (false, 100.0, "slow reverse"),
                (true, 900.0, "fast forward"), (false, 900.0, "fast reverse")
            ] {
                let source = forward ? first : last
                let destination = forward ? last : first
                let label = "four-tab: matrix \(repetition), \(phase)"
                tap(source, label: label + ": select source")
                drag(from: source.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                     to: destination.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                     velocity: velocity, label: label, target: control)
            }
        }
        // Consecutive public commands do not guarantee a rapid waveform. Keep
        // the delivered timestamps, including any XCUITest quiescence waits.
        for repetition in 1...3 {
            request("four-tab: consecutive taps \(repetition), last", kind: "consecutiveTapProbe", target: last)
            last.tap()
            request("four-tab: consecutive taps \(repetition), first", kind: "consecutiveTapProbe", target: first)
            first.tap()
        }
        pause()
    }

    // Endpoint evidence uses actual window points. These methods intentionally
    // avoid the older standalone tab bar and width-normalized drag helpers.
    func testSwiftUIEndpointDock() { swiftUIEndpointMotion(kind: "dock") }
    func testSwiftUIEndpointSegmented() { swiftUIEndpointMotion(kind: "segmented") }
    func testSwiftUIEndpointToggle() { swiftUIEndpointMotion(kind: "toggle") }
    func testSwiftUIEndpointSlider() { swiftUIEndpointMotion(kind: "slider") }

    func testSwiftUISaturatedSliderSlow() { saturatedSlider(velocity: 90, speed: "slow") }
    func testSwiftUISaturatedSliderFast() { saturatedSlider(velocity: 900, speed: "fast") }

    private func saturatedSlider(velocity: Double, speed: String) {
        executionTimeAllowance = 80
        settleDuration = 1.65
        for forward in [true, false] {
            // Starting near each destination leaves room to exceed the business
            // endpoint within the real window, after native drag slop. The old
            // 25%-to-window-edge run did not establish held saturation.
            let initial = forward ? 0.75 : 0.25
            launch(scene: "slider", background: "grid", initialSlider: initial)
            let control = app.sliders["swiftui-slider"]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            XCTAssertEqual(control.value as? String, forward ? "75%" : "25%")
            let frame = control.frame
            XCTAssertEqual(frame.width, 335, accuracy: 0.5)
            captureLanguage("saturated-slider-\(speed)-\(forward ? "forward" : "reverse")-rest")
            // Fixed native REST geometry: 37pt thumb, 298pt travel. The original
            // measured 25% center was local x=93.5; retain actual anchor offsets.
            let origin = CGPoint(x: frame.minX + 19 + 298 * CGFloat(initial), y: frame.minY + 16)
            let destination = CGPoint(x: forward ? 367 : 8, y: origin.y)
            let label = "saturated-slider-\(speed)-\(forward ? "forward" : "reverse")"
            endpointDrag(control, from: origin, to: destination, velocity: velocity, label: label,
                         extra: ["control": "swiftui-slider", "initialValue": initial,
                                 "direction": forward ? "forward" : "reverse",
                                 "coordinateUnits": "actual window pt; no width normalization",
                                 "initialThumbOriginScope": "fixed 37pt REST thumb / 298pt travel candidate; verify actual pre-DOWN presentation center",
                                 "endpointScope": "candidate beyond business endpoint after native slop; held saturation must be established from actual trace"])
            XCTAssertEqual(control.value as? String, forward ? "100%" : "0%")
            captureLanguage(label + "-tail")
            app.terminate()
        }
    }

    private func swiftUIEndpointMotion(kind: String) {
        executionTimeAllowance = 65
        settleDuration = 1.65
        let isDock = kind == "dock"
        launch(scene: isDock ? "toggle" : kind, background: "grid", style: "light",
               dockParent: isDock ? "swiftui-tabview" : nil,
               dockContent: isDock ? "synthetic-scroll" : nil)
        let identifier = isDock ? "swiftui-tabbar" : "swiftui-\(kind)"
        let control: XCUIElement
        switch kind {
        case "dock": control = app.tabBars[identifier]
        case "segmented": control = app.segmentedControls[identifier]
        case "toggle": control = app.switches[identifier]
        default: control = app.sliders[identifier]
        }
        XCTAssertTrue(control.waitForExistence(timeout: 5))
        XCTAssertEqual(app.windows.firstMatch.frame.width, 375, accuracy: 0.5)
        if isDock {
            XCTAssertEqual(control.buttons.count, 4)
            XCTAssertTrue(control.buttons["Schedule"].isSelected)
            XCTAssertTrue(app.scrollViews["dock-synthetic-scroll-0"].exists)
        } else if kind == "segmented" {
            XCTAssertTrue(control.buttons["First"].isSelected)
        } else if kind == "toggle" {
            XCTAssertEqual(control.value as? String, "0")
            XCTAssertEqual(control.frame.width, 61, accuracy: 0.5)
        } else {
            XCTAssertEqual(control.value as? String, "25%")
            XCTAssertEqual(control.frame.width, 335, accuracy: 0.5)
        }
        captureLanguage("endpoint-\(kind)-initial-rest")
        let initialValue: Any = isDock || kind == "segmented" ? 0 : (kind == "toggle" ? false : 0.25)
        let speeds: [(Double, String)] = kind == "toggle" ? [(45, "slow"), (650, "fast")] :
            (kind == "slider" ? [(90, "slow"), (900, "fast")] : [(100, "slow"), (900, "fast")])
        var gestureIndex = 0
        for (velocity, speed) in speeds {
            for forward in [true, false] {
                let frame = control.frame
                let origin: CGPoint
                let originScope: String
                if isDock || kind == "segmented" {
                    let selected = control.buttons.allElementsBoundByIndex.filter(\.isSelected)
                    XCTAssertEqual(selected.count, 1)
                    guard let thumb = selected.first else { return }
                    origin = CGPoint(x: thumb.frame.midX, y: thumb.frame.midY)
                    originScope = "current selected AX button center; verify against this run's last REST presentation lens"
                } else if kind == "toggle" {
                    let isOn = control.value as? String == "1"
                    // Existing native REST observations: AX hot zone is 61pt,
                    // visual switch 63pt; the 37x24 lens centers are 20.5/42.5
                    // from its minX. Never use .15/.85 of the AX hot zone.
                    origin = CGPoint(x: frame.minX + (isOn ? 42.5 : 20.5), y: frame.midY)
                    originScope = "measured native REST lens offset from AX minX, off=20.5pt/on=42.5pt; verify current trace"
                } else {
                    let text = control.value as? String ?? ""
                    let percentage = Double(text.replacingOccurrences(of: "%", with: "")) ?? -1
                    XCTAssertTrue((0...100).contains(percentage))
                    // First DOWN uses the measured initial 25% lens center,
                    // not normalized .25 of the 335pt accessibility wrapper.
                    // Endpoint follow-up requests use a conservative 18.5pt
                    // half-thumb inset; actual offsets remain in the evidence.
                    let x = gestureIndex == 0 ? frame.minX + 93.5 :
                        frame.minX + 18.5 + (frame.width - 37) * CGFloat(percentage / 100)
                    origin = CGPoint(x: x, y: frame.minY + 16)
                    originScope = gestureIndex == 0 ? "measured 25% native REST lens center local=(93.5,16)pt; verify current trace" :
                        "endpoint candidate from current AX percentage and measured 37pt thumb width; actual center offset must be retained"
                }
                let endpointX: CGFloat = isDock ? (forward ? 362 : 13) :
                    (forward ? min(367, frame.maxX + 12) : max(8, frame.minX - 12))
                let destination = CGPoint(x: endpointX, y: origin.y)
                let label = "endpoint-\(kind)-\(gestureIndex)-\(speed)-\(forward ? "forward" : "reverse")"
                endpointDrag(control, from: origin, to: destination, velocity: velocity, label: label,
                             extra: ["control": identifier, "initialValue": initialValue,
                                     "gestureIndex": gestureIndex, "initialThumbOriginScope": originScope,
                                     "observedValueBefore": control.value as? String ?? "",
                                     "direction": forward ? "forward" : "reverse",
                                     "coordinateUnits": "actual window pt; Android replay 1pt=1dp plus one fixed translation; no width normalization",
                                     "endpointScope": isDock ? "outside measured native platter x=21...354, inside app" : "outside AX control frame, inside app"])
                gestureIndex += 1
            }
        }
        captureLanguage("endpoint-\(kind)-final-tail")
        app.terminate()
    }

    private func endpointDrag(_ target: XCUIElement, from: CGPoint, to: CGPoint,
                              velocity: Double, label: String, extra: [String: Any]) {
        let root = app.coordinate(withNormalizedOffset: .zero)
        let start = root.withOffset(CGVector(dx: from.x, dy: from.y))
        let end = root.withOffset(CGVector(dx: to.x, dy: to.y))
        var metadata = extra
        metadata.merge(["from": xy(from), "to": xy(to), "pressDuration": 0.45,
                        "requestedRawVelocity": velocity, "endHoldDuration": 0.25,
                        "minimumPostCommandTailSeconds": 1.65,
                        "velocityScope": "XCUIGestureVelocity request; actual sampleT waveform determines delivered velocity; not acceleration"]) { _, new in new }
        request(label, kind: "endpointStraightDrag", target: target, extra: metadata)
        XCTContext.runActivity(named: label) { _ in
            start.press(forDuration: 0.45, thenDragTo: end,
                        withVelocity: XCUIGestureVelocity(rawValue: CGFloat(velocity)), thenHoldForDuration: 0.25)
        }
        // XCUITest's quiescence wait is part of the real intergesture gap. The
        // extra tail is a lower bound; exports preserve every actual sampleT.
        Thread.sleep(forTimeInterval: 1.65)
    }

    func testStaticOpticsFineGridLight() { staticOptics(background: "fine_grid", style: "light") }
    func testStaticOpticsFineGridDark() { staticOptics(background: "fine_grid", style: "dark") }
    func testStaticOpticsColorStripesLight() { staticOptics(background: "color_stripes", style: "light") }
    func testStaticOpticsColorStripesDark() { staticOptics(background: "color_stripes", style: "dark") }
    func testStaticOpticsTonalBlocksLight() { staticOptics(background: "tonal_blocks", style: "light") }
    func testStaticOpticsTonalBlocksDark() { staticOptics(background: "tonal_blocks", style: "dark") }
    func testStaticOpticsLandscapeLight() { staticOptics(background: "synthetic_landscape", style: "light") }
    func testStaticOpticsLandscapeDark() { staticOptics(background: "synthetic_landscape", style: "dark") }

    func testDockOpticsFineGridLight() { dockOptics(background: "fine_grid", style: "light") }
    func testDockOpticsFineGridDark() { dockOptics(background: "fine_grid", style: "dark") }
    func testDockOpticsColorStripesLight() { dockOptics(background: "color_stripes", style: "light") }
    func testDockOpticsColorStripesDark() { dockOptics(background: "color_stripes", style: "dark") }
    func testDockOpticsTonalBlocksLight() { dockOptics(background: "tonal_blocks", style: "light") }
    func testDockOpticsTonalBlocksDark() { dockOptics(background: "tonal_blocks", style: "dark") }
    func testDockOpticsLandscapeLight() { dockOptics(background: "synthetic_landscape", style: "light") }
    func testDockOpticsLandscapeDark() { dockOptics(background: "synthetic_landscape", style: "dark") }

    func testSwiftUITabViewDockFineGridLight() { swiftUITabViewDockOptics(background: "fine_grid", style: "light") }
    func testSwiftUITabViewDockFineGridDark() { swiftUITabViewDockOptics(background: "fine_grid") }
    func testSwiftUITabViewDockColorStripesLight() { swiftUITabViewDockOptics(background: "color_stripes", style: "light") }
    func testSwiftUITabViewDockColorStripesDark() { swiftUITabViewDockOptics(background: "color_stripes") }
    func testSwiftUITabViewDockTonalBlocksLight() { swiftUITabViewDockOptics(background: "tonal_blocks", style: "light") }
    func testSwiftUITabViewDockTonalBlocksDark() { swiftUITabViewDockOptics(background: "tonal_blocks") }
    func testSwiftUITabViewDockLandscapeLight() { swiftUITabViewDockOptics(background: "synthetic_landscape", style: "light") }
    func testSwiftUITabViewDockLandscapeDark() { swiftUITabViewDockOptics(background: "synthetic_landscape") }

    /// One process and one native bar; short top-overlay taps only advance the
    /// controlled input. No tab press, drag, held capture or motion suite.
    func testSwiftUITabViewDockAppearanceControls() {
        executionTimeAllowance = 110
        settleDuration = 0.1
        launch(scene: "toggle", background: "gray_26", style: "dark", staticOptics: true,
               dockParent: "swiftui-tabview", dockContent: "synthetic-scroll", appearanceControls: true)
        let control = app.tabBars["swiftui-tabbar"]
        XCTAssertTrue(control.waitForExistence(timeout: 5))
        XCTAssertEqual(control.buttons.count, 4)
        XCTAssertTrue(control.buttons["Schedule"].isSelected)
        XCTAssertTrue(app.scrollViews["dock-synthetic-scroll-0"].exists)
        let state = app.staticTexts["appearance-controls-state"]
        let next = app.buttons["appearance-controls-next"]
        let stages = ["gray_26-dark", "gray_77-dark", "gray_128-dark", "gray_179-dark", "gray_230-dark",
                      "contrast_low-dark", "contrast_high-dark", "contrast_high-light", "contrast_high-dark", "gray_26-dark"]
        var initialIdentities: [String]?
        var captures: [[String: Any]] = []
        for (index, name) in stages.enumerated() {
            let expectedPrefix = "\(index)|ready|"
            let ready = NSPredicate { _, _ in (state.value as? String)?.hasPrefix(expectedPrefix) == true }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: nil)], timeout: 5), .completed)
            let before = state.value as? String ?? ""
            let identities = before.split(separator: "|").map(String.init).filter {
                $0.hasPrefix("barId=") || $0.hasPrefix("controllerId=") || $0.hasPrefix("windowId=")
            }
            XCTAssertEqual(identities.count, 3)
            if let initialIdentities { XCTAssertEqual(identities, initialIdentities) } else { initialIdentities = identities }
            let requested = index == 7 ? 1 : 2
            XCTAssertTrue(before.contains("requested=\(requested)|"))
            XCTAssertTrue(before.contains("window=\(requested)|"))
            let begin = Date().timeIntervalSince1970
            captureLanguage(String(format: "appearance-%02d-", index) + name + "-rest")
            captures.append(["stage": index, "name": name, "captureBeginUnixSeconds": begin,
                             "captureEndUnixSeconds": Date().timeIntervalSince1970,
                             "stateBeforeCapture": before, "stateAfterCapture": state.value as? String ?? ""])
            if index + 1 < stages.count { next.tap() }
        }
        let data = try! JSONSerialization.data(withJSONObject: captures, options: [.prettyPrinted, .sortedKeys])
        let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
        attachment.name = "appearance-rest-capture-brackets"
        attachment.lifetime = .keepAlways
        add(attachment)
        app.terminate()
    }

    func testAlertOpticsFineGridLight() { alertOptics(background: "fine_grid", style: "light") }
    func testAlertOpticsFineGridDark() { alertOptics(background: "fine_grid", style: "dark") }
    func testAlertOpticsColorStripesLight() { alertOptics(background: "color_stripes", style: "light") }
    func testAlertOpticsColorStripesDark() { alertOptics(background: "color_stripes", style: "dark") }
    func testAlertOpticsTonalBlocksLight() { alertOptics(background: "tonal_blocks", style: "light") }
    func testAlertOpticsTonalBlocksDark() { alertOptics(background: "tonal_blocks", style: "dark") }
    func testAlertOpticsLandscapeLight() { alertOptics(background: "synthetic_landscape", style: "light") }
    func testAlertOpticsLandscapeDark() { alertOptics(background: "synthetic_landscape", style: "dark") }

    func testAlertHeldFineGridLight() { alertHeldOptics(style: "light") }
    func testAlertHeldFineGridDark() { alertHeldOptics(style: "dark") }
    func testAlertHeldColorStripesLight() { alertHeldOptics(background: "color_stripes", style: "light") }
    func testAlertHeldColorStripesDark() { alertHeldOptics(background: "color_stripes", style: "dark") }
    func testAlertHeldTonalBlocksLight() { alertHeldOptics(background: "tonal_blocks", style: "light") }
    func testAlertHeldTonalBlocksDark() { alertHeldOptics(background: "tonal_blocks", style: "dark") }
    func testAlertHeldLandscapeLight() { alertHeldOptics(background: "synthetic_landscape", style: "light") }
    func testAlertHeldLandscapeDark() { alertHeldOptics(background: "synthetic_landscape", style: "dark") }
    // The short-tap LandscapeLight probe remains unchanged and its cancelled
    // deliveries remain failed evidence. This uses a distinct public input.
    func testAlertHeldLandscapeLightLongPressProbe() {
        alertHeldOptics(background: "synthetic_landscape", style: "light", triggerWithHold: true, heldDuration: 6)
    }

    // Only the missing dark Confirm sample is recaptured. Its original host
    // screenshot crossed UP; a fixed larger budget still requires verification.
    func testAlertHeldTonalBlocksDarkConfirmRecapture() {
        settleDuration = 0.8
        launch(scene: "presentations", background: "tonal_blocks", style: "dark", staticOptics: true)
        let prefix = "alert-held-tonal_blocks-dark-recapture"
        let trigger = app.buttons["show-alert"]
        let alert = app.alerts["Native Alert"]
        captureLanguage(prefix + "-before")
        tap(trigger, label: prefix + ": present native alert for held confirm")
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        captureLanguage(prefix + "-confirm-rest")
        opticalHold(alert.buttons["Confirm"], name: prefix + "-confirm-held", duration: 6)
        XCTAssertFalse(alert.exists)
        app.terminate()
    }

    // Keep the native display-link recorder running for these separate probes.
    // Static held screenshots establish endpoints, not an animation waveform.
    func testAlertMotionFineGridLight() { alertHeldOptics(style: "light", staticOptics: false) }
    func testAlertMotionFineGridDark() { alertHeldOptics(style: "dark", staticOptics: false) }

    private func alertHeldOptics(background: String = "fine_grid", style: String, staticOptics: Bool = true,
                                 triggerWithHold: Bool = false, heldDuration: TimeInterval = 2.6) {
        settleDuration = 0.8
        launch(scene: "presentations", background: background, style: style, staticOptics: staticOptics)
        let prefix = "alert-\(staticOptics ? "held" : "motion")-\(background)-\(style)" + (triggerWithHold ? "-long-press-trigger" : "")
        let trigger = app.buttons["show-alert"]
        let alert = app.alerts["Native Alert"]
        captureLanguage(prefix + "-before")

        if triggerWithHold {
            hold(trigger, label: prefix + ": publicly hold/release native trigger for held confirm")
        } else { tap(trigger, label: prefix + ": present native alert for held confirm") }
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        XCTAssertTrue(alert.buttons["Confirm"].exists)
        XCTAssertTrue(alert.buttons["Cancel"].exists)
        captureLanguage(prefix + "-confirm-rest")
        opticalHold(alert.buttons["Confirm"], name: prefix + "-confirm-held", duration: heldDuration)
        XCTAssertFalse(alert.exists)
        captureLanguage(prefix + "-after-confirm")

        if triggerWithHold {
            hold(trigger, label: prefix + ": publicly hold/release native trigger for held cancel")
        } else { tap(trigger, label: prefix + ": present native alert for held cancel") }
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        XCTAssertTrue(alert.buttons["Confirm"].exists)
        XCTAssertTrue(alert.buttons["Cancel"].exists)
        captureLanguage(prefix + "-cancel-rest")
        opticalHold(alert.buttons["Cancel"], name: prefix + "-cancel-held", duration: heldDuration)
        XCTAssertFalse(alert.exists)
        captureLanguage(prefix + "-after-cancel")
        // The unmodified native action handlers log confirmed/cancelled in
        // trace.jsonl. Host validation must check one matching callback after
        // each delivered UP, in addition to the visible dismissal above.
        app.terminate()
    }

    private func alertOptics(background: String, style: String) {
        settleDuration = 0.8
        launch(scene: "presentations", background: background, style: style, staticOptics: true)
        let prefix = "alert-optics-\(background)-\(style)"
        let trigger = app.buttons["show-alert"]
        let alert = app.alerts["Native Alert"]
        captureLanguage(prefix + "-before")
        tap(trigger, label: prefix + ": present native alert for confirm")
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        XCTAssertTrue(alert.buttons["Confirm"].exists)
        XCTAssertTrue(alert.buttons["Cancel"].exists)
        captureLanguage(prefix + "-alert-rest")
        tap(alert.buttons["Confirm"], label: prefix + ": native confirm")
        XCTAssertFalse(alert.exists)
        captureLanguage(prefix + "-after-confirm")
        tap(trigger, label: prefix + ": present native alert for cancel")
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        tap(alert.buttons["Cancel"], label: prefix + ": native cancel")
        XCTAssertFalse(alert.exists)
        captureLanguage(prefix + "-after-cancel")
        app.terminate()
    }

    func testStaticOpticsLanguageProbe() {
        launch(scene: "language", background: "fine_grid", staticOptics: true)
        openOpticalLanguageMenu(prefix: "optics-language-probe")
        captureLanguage("optics-language-probe-menu-rest")
        opticalHold(languageMenu().buttons.matching(NSPredicate(format: "label == %@", "English")).firstMatch,
                    name: "optics-language-probe-menu-row-held")
    }
    func testStaticOpticsTonalBlocksLightLanguageRecapture() {
        launch(scene: "language", background: "tonal_blocks", staticOptics: true)
        openOpticalLanguageMenu(prefix: "optics-tonal_blocks-light-language-recapture")
        captureLanguage("optics-tonal_blocks-light-language-recapture-menu-rest")
        opticalHold(languageMenu().buttons.matching(NSPredicate(format: "label == %@", "English")).firstMatch,
                    name: "optics-tonal_blocks-light-language-menu-row-held-recapture", duration: 6)
    }

    private func openOpticalLanguageMenu(prefix: String) {
        let anchor = app.buttons["language-menu"]
        tap(anchor, label: prefix + ": present language menu, first tap")
        if !languageMenu().waitForExistence(timeout: 2) {
            captureLanguage(prefix + "-first-tap-no-open-menu")
            hold(anchor, label: prefix + ": native long-press opening after first tap did not keep menu open")
        }
        XCTAssertTrue(languageMenu().waitForExistence(timeout: 5))
    }

    /// Isolate the initially selected native Dock item on each optical input.
    /// The host still verifies the held PNG against delivered DOWN/UP times.
    private func dockOptics(background: String, style: String) {
        settleDuration = 0.7
        launch(scene: "toggle", background: background, style: style, staticOptics: true)
        let control = app.tabBars["uikit-tabbar"]
        XCTAssertTrue(control.waitForExistence(timeout: 5))
        let first = control.buttons["Home"]
        XCTAssertTrue(first.exists)
        XCTAssertTrue(first.isSelected)
        let prefix = "dock-optics-\(background)-\(style)"
        captureLanguage(prefix + "-rest")
        opticalHold(first, name: prefix + "-dock-selected-first-held")
        XCTAssertTrue(first.isSelected)
        app.terminate()
    }

    /// A separate four-page parent context; its geometry is entirely native.
    /// opticalHold keeps the existing host capture + delivered DOWN/UP contract.
    private func swiftUITabViewDockOptics(background: String, style: String = "dark") {
        settleDuration = 0.8
        launch(scene: "toggle", background: background, style: style, staticOptics: true,
               dockParent: "swiftui-tabview", dockContent: "synthetic-scroll")
        XCTAssertTrue(app.scrollViews["dock-synthetic-scroll-0"].waitForExistence(timeout: 5))
        let control = app.tabBars["swiftui-tabbar"]
        XCTAssertTrue(control.waitForExistence(timeout: 5))
        XCTAssertEqual(control.buttons.count, 4)
        let first = control.buttons["Schedule"]
        XCTAssertTrue(first.exists)
        XCTAssertTrue(first.isSelected)
        let prefix = "swiftui-tabview-dock-\(background)-\(style)"
        captureLanguage(prefix + "-rest")
        opticalHold(first, name: prefix + "-selected-first-held", duration: 6)
        XCTAssertTrue(first.isSelected)
        app.terminate()
    }

    private func staticOptics(background: String, style: String) {
        settleDuration = 0.7
        for scene in ["toggle", "segmented", "slider", "buttons", "presentations", "language"] {
            launch(scene: scene, background: background, style: style, staticOptics: true)
            let prefix = "optics-\(background)-\(style)-\(scene)"
            captureLanguage(prefix + "-rest")
            switch scene {
            case "toggle":
                for id in ["swiftui-toggle", "uikit-toggle"] {
                    let control = app.switches[id]
                    opticalHold(control, name: prefix + "-" + id + "-off-held")
                    captureLanguage(prefix + "-" + id + "-on-rest")
                    opticalHold(control, name: prefix + "-" + id + "-on-held")
                }
                opticalHold(app.tabBars["uikit-tabbar"].buttons["Settings"], name: prefix + "-dock-held")
            case "segmented":
                for id in ["swiftui-segmented", "uikit-segmented"] {
                    opticalHold(app.segmentedControls[id].buttons["Third"], name: prefix + "-" + id + "-held")
                }
            case "slider":
                for id in ["uikit-slider", "swiftui-slider"] {
                    opticalHold(app.sliders[id], point: CGVector(dx: 0.25, dy: 0.5), name: prefix + "-" + id + "-held")
                }
            case "buttons":
                for id in ["glass-button", "glass-prominent-button", "glass-circle-button"] {
                    opticalHold(app.buttons[id], name: prefix + "-" + id + "-held")
                }
            case "presentations":
                tap(app.buttons["show-sheet"], label: prefix + ": present native sheet")
                XCTAssertTrue(app.staticTexts["sheet-title"].waitForExistence(timeout: 5))
                captureLanguage(prefix + "-sheet-rest")
                opticalHold(app.buttons["dismiss-sheet"], name: prefix + "-sheet-dismiss-held")
                tap(app.buttons["show-menu"], label: prefix + ": present native menu")
                let action = app.buttons["First action"]
                XCTAssertTrue(action.waitForExistence(timeout: 5))
                captureLanguage(prefix + "-menu-rest")
                opticalHold(action, name: prefix + "-menu-row-held")
            case "language":
                openOpticalLanguageMenu(prefix: prefix)
                let menu = languageMenu()
                captureLanguage(prefix + "-menu-rest")
                let english = menu.buttons.matching(NSPredicate(format: "label == %@", "English")).firstMatch
                opticalHold(english, name: prefix + "-menu-row-held")
            default: XCTFail("Unknown optics scene")
            }
            app.terminate()
        }
    }

    /// A host observer reads this durable signal and takes a simctl display
    /// screenshot while the synchronous public press is running. Its timestamp
    /// bracket must later fall entirely within an actual delivered DOWN/UP pair.
    /// The XCTAttachment immediately after press would be a released state, so
    /// it is deliberately not named or used as a held-state reference.
    private func opticalHold(_ target: XCUIElement, point: CGVector = CGVector(dx: 0.5, dy: 0.5),
                             name: String, duration: TimeInterval = 2.6) {
        XCTAssertTrue(target.exists)
        let coordinate = target.coordinate(withNormalizedOffset: point)
        request(name, kind: "opticalHeldCapture", target: target,
                extra: ["pressDuration": duration, "point": xy(coordinate.screenPoint)])
        let signal: [String: Any] = ["name": name, "run": app.launchEnvironment["REFERENCE_RUN_ID"] ?? "",
                                     "requestedAtUnixSeconds": Date().timeIntervalSince1970,
                                     "point": xy(coordinate.screenPoint), "pressDuration": duration,
                                     "targetIdentifier": target.identifier, "targetLabel": target.label,
                                     "targetValue": target.value as? String ?? "",
                                     "targetFramePt": ["x": target.frame.minX, "y": target.frame.minY,
                                                       "w": target.frame.width, "h": target.frame.height]]
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("optics-capture-signals.jsonl")
        var data = try! JSONSerialization.data(withJSONObject: signal, options: [.sortedKeys])
        data.append(10)
        if !FileManager.default.fileExists(atPath: url.path) { FileManager.default.createFile(atPath: url.path, contents: nil) }
        let handle = try! FileHandle(forWritingTo: url)
        try! handle.seekToEnd(); try! handle.write(contentsOf: data); try! handle.synchronize(); try! handle.close()
        coordinate.press(forDuration: duration)
        pause()
    }

    private func launch(scene: String, background: String, focused: Bool = false, tabCount: Int = 3,
                        style: String = "light", staticOptics: Bool = false, dockParent: String? = nil,
                        dockContent: String? = nil, appearanceControls: Bool = false, initialSlider: Double? = nil) {
        app = XCUIApplication()
        app.launchArguments = ["-ReferenceScene", scene, "-ReferenceBackground", background,
                               "-ReferenceInterfaceStyle", style]
        if tabCount == 4 { app.launchArguments += ["-ReferenceTabCount", "4"] }
        if focused { app.launchArguments.append("-ReferenceFocusedTrace") }
        if staticOptics { app.launchArguments.append("-ReferenceStaticOptics") }
        if let dockParent { app.launchArguments += ["-ReferenceDockParent", dockParent] }
        if let dockContent { app.launchArguments += ["-ReferenceDockContent", dockContent] }
        if appearanceControls { app.launchArguments.append("-ReferenceDockAppearanceControls") }
        if let initialSlider { app.launchArguments += ["-ReferenceInitialSlider", String(initialSlider)] }
        app.launchEnvironment["REFERENCE_RUN_ID"] = "\(name)-\(UUID().uuidString)"
        app.launch()
        XCTAssertTrue(app.staticTexts["reference-title"].waitForExistence(timeout: 10))
        pause()
    }

    private func toggle(background: String) {
        launch(scene: "toggle", background: background)
        for identifier in ["swiftui-toggle", "uikit-toggle"] {
            let control = app.switches[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            tap(control, label: identifier + ": tap on")
            tap(control, label: identifier + ": tap off")
            hold(control, label: identifier + ": long hold / release")
            drag(control, from: CGVector(dx: 0.15, dy: 0.5), to: CGVector(dx: 0.85, dy: 0.5),
                 velocity: 45, label: identifier + ": slow forward")
            drag(control, from: CGVector(dx: 0.85, dy: 0.5), to: CGVector(dx: 0.15, dy: 0.5),
                 velocity: 650, label: identifier + ": fast reverse")
        }
    }

    private func segmented(background: String) {
        launch(scene: "segmented", background: background)
        for identifier in ["swiftui-segmented", "uikit-segmented"] {
            let control = app.segmentedControls[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            tap(control.buttons["Third"], label: identifier + ": tap third")
            tap(control.buttons["First"], label: identifier + ": tap first")
            hold(control.buttons["First"], label: identifier + ": long hold / release")
            drag(control, from: CGVector(dx: 1.0 / 6, dy: 0.5), to: CGVector(dx: 5.0 / 6, dy: 0.5),
                 velocity: 100, label: identifier + ": slow across three")
            drag(control, from: CGVector(dx: 5.0 / 6, dy: 0.5), to: CGVector(dx: 1.0 / 6, dy: 0.5),
                 velocity: 900, label: identifier + ": fast reverse")
        }
    }

    private func slider(background: String) {
        launch(scene: "slider", background: background)
        for identifier in ["uikit-slider", "swiftui-slider"] {
            let control = app.sliders[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            // Both are initialized at 0.25; this point remains inside the native
            // thumb without substituting a custom track or thumb geometry.
            hold(control, at: CGVector(dx: 0.25, dy: 0.5), label: identifier + ": long hold / release")
            drag(control, from: CGVector(dx: 0.25, dy: 0.5), to: CGVector(dx: 0.8, dy: 0.5),
                 velocity: 90, label: identifier + ": slow forward")
            drag(control, from: CGVector(dx: 0.8, dy: 0.5), to: CGVector(dx: 0.25, dy: 0.5),
                 velocity: 900, label: identifier + ": fast reverse")
        }
    }

    private func buttons(background: String) {
        launch(scene: "buttons", background: background)
        for identifier in ["glass-button", "glass-prominent-button", "glass-circle-button"] {
            let control = app.buttons[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            tap(control, label: identifier + ": short tap")
            hold(control, label: identifier + ": long hold / release")
            drag(control, from: CGVector(dx: 0.5, dy: 0.5), to: CGVector(dx: 0.5, dy: 3),
                 velocity: 80, label: identifier + ": drag outside / release")
        }
    }

    private func tabBar(background: String) {
        settleDuration = 1.65
        launch(scene: "toggle", background: background)
        let control = app.tabBars["uikit-tabbar"]
        XCTAssertTrue(control.waitForExistence(timeout: 5))
        tap(control.buttons["Explore"], label: "tabbar: tap explore")
        tap(control.buttons["Home"], label: "tabbar: tap home")
        hold(control.buttons["Home"], label: "tabbar: long hold / release")
        let first = control.buttons["Home"]
        let last = control.buttons["Settings"]
        drag(from: first.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
             to: last.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
             velocity: 100, label: "tabbar: slow across three", target: control)
        drag(from: last.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
             to: first.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
             velocity: 900, label: "tabbar: fast reverse", target: control)
    }

    private func presentations(background: String) {
        launch(scene: "presentations", background: background)
        tap(app.buttons["show-sheet"], label: "sheet: present")
        let title = app.staticTexts["sheet-title"]
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        let sheetStart = title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: -1.3))
        drag(from: sheetStart,
             to: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)),
             velocity: 160, label: "sheet: slow raise / release", target: title)
        tap(app.buttons["dismiss-sheet"], label: "sheet: native dismiss button")

        tap(app.buttons["show-alert"], label: "alert: present")
        let alert = app.alerts["Native Alert"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        hold(alert.buttons["Cancel"], label: "alert: hold cancel / release")

        tap(app.buttons["show-menu"], label: "menu: tap present")
        let first = app.buttons["First action"]
        XCTAssertTrue(first.waitForExistence(timeout: 5))
        tap(first, label: "menu: first action")
        hold(app.buttons["show-menu"], label: "menu: long press / release")
        let second = app.buttons["Second action"]
        XCTAssertTrue(second.waitForExistence(timeout: 5))
        tap(second, label: "menu: second action")
    }

    private func tap(_ target: XCUIElement, label: String) {
        request(label, kind: "tap", target: target)
        XCTContext.runActivity(named: label) { _ in target.tap() }
        pause()
    }

    private func hold(_ target: XCUIElement, at point: CGVector = CGVector(dx: 0.5, dy: 0.5), label: String) {
        let coordinate = target.coordinate(withNormalizedOffset: point)
        request(label, kind: "hold", target: target,
                extra: ["pressDuration": 0.7, "point": xy(coordinate.screenPoint)])
        XCTContext.runActivity(named: label) { _ in coordinate.press(forDuration: 0.7) }
        pause()
    }

    private func drag(_ target: XCUIElement, from start: CGVector, to end: CGVector,
                      velocity: Double, label: String) {
        drag(from: target.coordinate(withNormalizedOffset: start),
             to: target.coordinate(withNormalizedOffset: end), velocity: velocity, label: label, target: target)
    }

    private func drag(from start: XCUICoordinate, to end: XCUICoordinate,
                      velocity: Double, label: String, target: XCUIElement) {
        request(label, kind: "straightDrag", target: target, extra: [
            "from": xy(start.screenPoint), "to": xy(end.screenPoint),
            "pressDuration": 0.45, "requestedRawVelocity": velocity, "endHoldDuration": 0.25
        ])
        XCTContext.runActivity(named: label) { _ in
            start.press(forDuration: 0.45, thenDragTo: end,
                        withVelocity: XCUIGestureVelocity(rawValue: CGFloat(velocity)), thenHoldForDuration: 0.25)
        }
        pause()
    }

    private func request(_ label: String, kind: String, target: XCUIElement, extra: [String: Any] = [:]) {
        let frame = target.frame
        var row: [String: Any] = [
            "label": label, "kind": kind, "target": target.identifier,
            "targetLabel": target.label,
            "screenFramePt": ["x": frame.minX, "y": frame.minY, "w": frame.width, "h": frame.height],
            "requestedAtUnixSeconds": Date().timeIntervalSince1970,
            "notice": "Requested XCUITest input; actual delivered waveform is in trace.jsonl"
        ]
        row.merge(extra) { _, new in new }
        requests.append(row)
    }

    private func xy(_ point: CGPoint) -> [String: CGFloat] { ["x": point.x, "y": point.y] }
    private func pause() { Thread.sleep(forTimeInterval: settleDuration) }
}
