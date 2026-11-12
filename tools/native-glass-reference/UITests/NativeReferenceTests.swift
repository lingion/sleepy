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

    /// Distinguish direct selection preview from committed selection. Each kind
    /// starts in a fresh process with A selected; no horizontal drag is involved.
    func testDirectSelectionPhases() {
        settleDuration = 1.05
        for identifier in ["swiftui-segmented", "uikit-segmented", "uikit-tabbar"] {
            let isTabBar = identifier == "uikit-tabbar"
            launch(scene: isTabBar ? "toggle" : "segmented", background: "grid")
            let control = isTabBar ? app.tabBars[identifier] : app.segmentedControls[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            let first = control.buttons[isTabBar ? "Home" : "First"]
            let second = control.buttons[isTabBar ? "Explore" : "Second"]
            XCTAssertTrue(first.exists)
            XCTAssertTrue(second.exists)
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
        settleDuration = 1.05
        for identifier in ["uikit-segmented", "uikit-tabbar"] {
            let isTabBar = identifier == "uikit-tabbar"
            launch(scene: isTabBar ? "toggle" : "segmented", background: "grid")
            let control = isTabBar ? app.tabBars[identifier] : app.segmentedControls[identifier]
            XCTAssertTrue(control.waitForExistence(timeout: 5))
            let first = control.buttons[isTabBar ? "Home" : "First"]
            let last = control.buttons[isTabBar ? "Settings" : "Third"]
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

    private func launch(scene: String, background: String) {
        app = XCUIApplication()
        app.launchArguments = ["-ReferenceScene", scene, "-ReferenceBackground", background]
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
