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
                        style: String = "light", staticOptics: Bool = false) {
        app = XCUIApplication()
        app.launchArguments = ["-ReferenceScene", scene, "-ReferenceBackground", background,
                               "-ReferenceInterfaceStyle", style]
        if tabCount == 4 { app.launchArguments += ["-ReferenceTabCount", "4"] }
        if focused { app.launchArguments.append("-ReferenceFocusedTrace") }
        if staticOptics { app.launchArguments.append("-ReferenceStaticOptics") }
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
