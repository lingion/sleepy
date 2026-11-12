import UIKit

/// This recorder observes public touch delivery and presentation layers. It never
/// changes a control's layer, animation, material, timing, or tracking behavior.
final class ReferenceTrace {
    static let shared = ReferenceTrace()
    let origin = CACurrentMediaTime()
    let originUnixSeconds = Date().timeIntervalSince1970
    let runID: String
    let directory: URL
    private let handle: FileHandle
    private let queue = DispatchQueue(label: "glass.reference.trace", qos: .utility)
    private var queuedRows = 0
    private var touchIDs: [ObjectIdentifier: Int] = [:]
    private var nextTouchID = 0

    private init() {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let requestedID = ProcessInfo.processInfo.environment["REFERENCE_RUN_ID"] ?? UUID().uuidString
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_."))
        let safeID = requestedID.unicodeScalars.map { allowed.contains($0) ? String($0) : "_" }.joined()
        let cleanedID = String(safeID.prefix(180)).trimmingCharacters(in: CharacterSet(charactersIn: "."))
        runID = cleanedID.isEmpty ? UUID().uuidString : cleanedID
        directory = documents.appendingPathComponent("reference", isDirectory: true)
            .appendingPathComponent(runID, isDirectory: true)
        try! FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("trace.jsonl")
        FileManager.default.createFile(atPath: url.path, contents: nil)
        handle = try! FileHandle(forWritingTo: url)
    }

    func add(_ fields: [String: Any]) {
        var row = fields
        row["t"] = CACurrentMediaTime() - origin
        queue.async {
            guard var data = try? JSONSerialization.data(withJSONObject: Self.sanitize(row)) else { return }
            data.append(10)
            try? self.handle.write(contentsOf: data)
            self.queuedRows += 1
            if self.queuedRows >= 60 {
                try? self.handle.synchronize()
                self.queuedRows = 0
            }
        }
    }

    func flush() {
        queue.async { try? self.handle.synchronize() }
    }

    func beginSession(window: UIWindow) {
        let bounds = window.bounds
        let metadata: [String: Any] = [
            "schema": 2,
            "run": runID,
            "os": ProcessInfo.processInfo.operatingSystemVersionString,
            "device": UIDevice.current.model,
            "screenWidthPt": bounds.width,
            "screenHeightPt": bounds.height,
            "screenScale": window.screen.scale,
            "maximumFramesPerSecond": window.screen.maximumFramesPerSecond,
            "captureFramesPerSecond": 60,
            "focusedMenuObservation": ProcessInfo.processInfo.arguments.contains("-ReferenceFocusedTrace"),
            "originUptimeSeconds": origin,
            "originUnixSeconds": originUnixSeconds,
            "frameClock": "captureStartT...captureEndT bracket sequential presentation reads; t is row enqueue time",
            "effectiveOpacityScope": "product of presentation opacity along UIView ancestors; excludes extra CALayer ancestors and material shader coverage",
            "coordinates": "window points; normalized coordinates divide by window bounds",
            "tintSRGB": [0.0, 0.65, 0.75, 1.0],
            "backgrounds": [
                "colorSpace": "sRGB (resolved UIColor, converted CGColor, then UIColor.getRed)",
                "baseRGBA": [0.94, 0.94, 0.96, 1.0],
                "gridSpacingPt": 20.0, "gridLineWidthPt": 0.5,
                "gridLineRGBA": [0.0, 0.0, 0.0, 0.13],
                "stripeWidthPt": 28.0, "stripeAlpha": 0.52,
                "stripeNames": ["systemRed", "systemOrange", "systemYellow", "systemGreen", "systemTeal", "systemBlue", "systemPurple"],
                "stripeResolvedRGBA": ReferencePalette.stripeColors.map {
                    ReferencePalette.resolvedSRGB($0, traits: window.traitCollection).map { $0 as Any } ?? NSNull()
                },
                "stripePaintRGBA": ReferencePalette.stripeColors.map {
                    ReferencePalette.resolvedSRGB($0.withAlphaComponent(0.52), traits: window.traitCollection).map { $0 as Any } ?? NSNull()
                }
            ],
            "reduceMotion": UIAccessibility.isReduceMotionEnabled,
            "reduceTransparency": UIAccessibility.isReduceTransparencyEnabled,
            "darkerSystemColors": UIAccessibility.isDarkerSystemColorsEnabled,
            "interfaceStyle": window.traitCollection.userInterfaceStyle.rawValue,
            "startedAt": ISO8601DateFormatter().string(from: Date())
        ]
        if let data = try? JSONSerialization.data(withJSONObject: Self.sanitize(metadata), options: [.prettyPrinted, .sortedKeys]) {
            try? data.write(to: directory.appendingPathComponent("session.json"), options: .atomic)
        }
        add(["event": "session", "metadata": metadata])
    }

    func touch(_ touch: UITouch, event: UIEvent, in window: UIWindow) {
        let objectID = ObjectIdentifier(touch)
        if touchIDs[objectID] == nil {
            nextTouchID += 1
            touchIDs[objectID] = nextTouchID
        }
        let identifier = touchIDs[objectID]!
        let deliveredAt = CACurrentMediaTime() - origin
        // Coalesced samples are delivered observations, not predicted touches.
        // The main delivery is separately marked so replay can deduplicate it.
        let samples = event.coalescedTouches(for: touch) ?? []
        for sample in samples {
            add(touchRow(sample, identifier: identifier, deliveredAt: deliveredAt,
                         source: "coalesced", window: window))
        }
        add(touchRow(touch, identifier: identifier, deliveredAt: deliveredAt,
                     source: "delivery", window: window))
        if touch.phase == .ended || touch.phase == .cancelled {
            touchIDs.removeValue(forKey: objectID)
            flush()
        }
    }

    private func touchRow(_ touch: UITouch, identifier: Int, deliveredAt: Double,
                          source: String, window: UIWindow) -> [String: Any] {
        let point = touch.location(in: window)
        let hit = touch.view
        return [
            "event": "touch", "touchID": identifier, "source": source,
            "phase": touch.phase.rawValue, "touchType": touch.type.rawValue,
            "timestampUptimeSeconds": touch.timestamp,
            "sampleT": touch.timestamp - origin, "deliveredT": deliveredAt,
            "x": point.x, "y": point.y,
            "nx": point.x / max(1, window.bounds.width),
            "ny": point.y / max(1, window.bounds.height),
            "force": touch.force, "maximumPossibleForce": touch.maximumPossibleForce,
            "majorRadius": touch.majorRadius, "tapCount": touch.tapCount,
            "hitClass": hit.map { String(describing: type(of: $0)) } ?? "",
            "hitIdentifier": hit?.accessibilityIdentifier ?? "",
            "windowClass": String(describing: type(of: window)),
            "windowWidthPt": window.bounds.width, "windowHeightPt": window.bounds.height,
            "windowFrameX": window.frame.minX, "windowFrameY": window.frame.minY
        ]
    }

    private static func sanitize(_ value: Any) -> Any {
        if let dictionary = value as? [String: Any] { return dictionary.mapValues(sanitize) }
        if let array = value as? [Any] { return array.map(sanitize) }
        if let number = value as? NSNumber, !number.doubleValue.isFinite { return NSNull() }
        return value
    }
}

/// UIApplication's public event interception also sees separate UIKit menu / alert
/// windows. Each observation retains its actual window geometry.
final class ReferenceApplication: UIApplication {
    override func sendEvent(_ event: UIEvent) {
        for touch in event.allTouches ?? [] {
            if let window = touch.window { ReferenceTrace.shared.touch(touch, event: event, in: window) }
        }
        super.sendEvent(event)
    }
}

final class PresentationRecorder: NSObject {
    weak var window: UIWindow?
    var context: () -> [String: Any] = { [:] }
    private var displayLink: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private var frame = 0

    // Read only public CALayer / CAAnimation properties. Private view class names
    // merely select which native materials to observe; no private KVC is used.
    private func animation(_ value: CAAnimation) -> [String: Any] {
        var row: [String: Any] = [
            "class": String(describing: type(of: value)), "duration": value.duration,
            "beginTime": value.beginTime, "timeOffset": value.timeOffset, "speed": value.speed,
            "repeatCount": value.repeatCount, "autoreverses": value.autoreverses,
            "fillMode": value.fillMode.rawValue, "removedOnCompletion": value.isRemovedOnCompletion
        ]
        if let function = value.timingFunction {
            var first: [Float] = [0, 0]
            var second: [Float] = [0, 0]
            function.getControlPoint(at: 1, values: &first)
            function.getControlPoint(at: 2, values: &second)
            row["timingControlPoints"] = [first, second]
        }
        if let basic = value as? CABasicAnimation {
            row["keyPath"] = basic.keyPath ?? ""
            row["from"] = basic.fromValue.map { String(describing: $0) } ?? ""
            row["to"] = basic.toValue.map { String(describing: $0) } ?? ""
            row["by"] = basic.byValue.map { String(describing: $0) } ?? ""
        }
        if let spring = value as? CASpringAnimation {
            row["mass"] = spring.mass
            row["stiffness"] = spring.stiffness
            row["damping"] = spring.damping
            row["initialVelocity"] = spring.initialVelocity
            row["settlingDuration"] = spring.settlingDuration
        }
        if let keys = value as? CAKeyframeAnimation {
            row["keyPath"] = keys.keyPath ?? ""
            row["calculationMode"] = keys.calculationMode.rawValue
            row["valueCount"] = keys.values?.count ?? 0
            row["valuesPrefix"] = keys.values?.prefix(16).map { String(describing: $0) } ?? []
            row["keyTimes"] = keys.keyTimes ?? []
        }
        if let group = value as? CAAnimationGroup {
            row["animations"] = group.animations?.prefix(12).map(animation) ?? []
        }
        return row
    }

    private func layerAnimations(_ layer: CALayer) -> [[String: Any]] {
        (layer.animationKeys() ?? []).compactMap { key in
            guard let value = layer.animation(forKey: key) else { return nil }
            var row = animation(value)
            row["animationKey"] = key
            return row
        }
    }

    private func animatedSublayers(_ layer: CALayer) -> (rows: [[String: Any]], visited: Int, truncated: Bool) {
        var rows: [[String: Any]] = []
        var visited = 0
        var truncated = false
        func walk(_ candidate: CALayer, path: String, depth: Int) {
            guard depth < 5, visited < 150 else { truncated = true; return }
            visited += 1
            let animations = layerAnimations(candidate)
            if !animations.isEmpty {
                let presented = candidate.presentation() ?? candidate
                let rect = presented.convert(presented.bounds, to: nil)
                rows.append(["path": path, "class": String(describing: type(of: candidate)),
                             "x": rect.minX, "y": rect.minY, "w": rect.width, "h": rect.height,
                             "opacity": presented.opacity, "animations": animations])
            }
            for (index, child) in (candidate.sublayers ?? []).enumerated() {
                walk(child, path: path + "/\(index)", depth: depth + 1)
            }
        }
        for (index, child) in (layer.sublayers ?? []).enumerated() {
            walk(child, path: "layer/\(index)", depth: 0)
        }
        return (rows, visited, truncated)
    }

    func start(window: UIWindow) {
        guard displayLink == nil else { return }
        self.window = window
        let link = CADisplayLink(target: self, selector: #selector(capture(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        ReferenceTrace.shared.flush()
    }

    @objc private func capture(_ link: CADisplayLink) {
        guard let window else { return }
        let startedAt = CACurrentMediaTime()
        var views: [[String: Any]] = []
        var visited = 0
        var truncated = false
        let languageScene = (context()["scene"] as? String) == "language"
        let focused = ProcessInfo.processInfo.arguments.contains("-ReferenceFocusedTrace")
        func walk(_ view: UIView, path: String, depth: Int, inheritedOpacity: Float = 1, inheritedHidden: Bool = false) {
            guard depth <= 18, visited < 900 else { truncated = true; return }
            visited += 1
            let name = String(describing: type(of: view))
            let identifier = view.accessibilityIdentifier ?? ""
            let isControl = view is UIControl || view is UITabBar || view is UILabel || name == "Label"
            // Class-name inspection is observational only; no private methods,
            // properties, selectors, or animation keys are invoked or modified.
            let isMaterial = ["LiquidLens", "TabSelection", "TabBarPlatter", "SegmentLabel", "VisualEffect", "Glass"].contains { name.contains($0) }
            let isMenuMotion = ["Portal", "SDF", "Transform", "ContextMenu"].contains { name.contains($0) }
            let layer = view.layer.presentation() ?? view.layer
            let effectiveOpacity = inheritedOpacity * layer.opacity
            let effectiveHidden = inheritedHidden || view.isHidden
            // Language runs retain every ancestor and menu descendant so an animated
            // content child is not mistaken for its final, static outer container.
            if (languageScene && !focused) || isControl || isMaterial || isMenuMotion || !identifier.isEmpty {
                var rect = layer.convert(layer.bounds, to: nil)
                if !rect.minX.isFinite || !rect.minY.isFinite || !rect.width.isFinite || !rect.height.isFinite {
                    rect = view.convert(view.bounds, to: window)
                }
                let transform = layer.transform
                let sublayers = isMenuMotion ? animatedSublayers(view.layer) : (rows: [], visited: 0, truncated: false)
                views.append([
                    "path": path, "class": name, "identifier": identifier,
                    "x": rect.minX, "y": rect.minY, "w": rect.width, "h": rect.height,
                    "boundsW": layer.bounds.width, "boundsH": layer.bounds.height,
                    "opacity": layer.opacity, "hidden": view.isHidden,
                    "effectiveOpacity": effectiveOpacity, "effectiveHidden": effectiveHidden,
                    "presentationAvailable": view.layer.presentation() != nil,
                    "text": (view as? UILabel)?.text ?? "",
                    "accessibilityLabel": view.accessibilityLabel ?? "",
                    "cornerRadius": layer.cornerRadius, "m11": transform.m11, "m22": transform.m22,
                    "m12": transform.m12, "m21": transform.m21,
                    "m41": transform.m41, "m42": transform.m42,
                    "background": layer.backgroundColor.map { String(describing: $0) } ?? "",
                    "animationKeys": view.layer.animationKeys() ?? [],
                    "animations": isMenuMotion ? layerAnimations(view.layer) : [],
                    "animatedSublayers": sublayers.rows,
                    "animatedSublayerVisits": sublayers.visited,
                    "animatedSublayersTruncated": sublayers.truncated
                ])
            }
            for (index, child) in view.subviews.enumerated() {
                walk(child, path: path + "/\(index)", depth: depth + 1,
                     inheritedOpacity: effectiveOpacity, inheritedHidden: effectiveHidden)
            }
        }
        // UIKit may host a native menu in another window; observe visible windows
        // without changing their levels, roots, or event delivery.
        var windows = [window]
        for candidate in UIApplication.shared.windows where candidate !== window && !candidate.isHidden && candidate.alpha > 0 {
            windows.append(candidate)
        }
        for (index, candidate) in windows.enumerated() {
            walk(candidate, path: "window\(index)", depth: 0)
        }
        frame += 1
        var row = context()
        row["event"] = "frame"
        row["frame"] = frame
        row["displayTimestamp"] = link.timestamp
        row["targetTimestamp"] = link.targetTimestamp
        row["displayIntervalMs"] = previousTimestamp.map { (link.timestamp - $0) * 1_000 } ?? 0
        let endedAt = CACurrentMediaTime()
        row["captureStartT"] = startedAt - ReferenceTrace.shared.origin
        row["captureEndT"] = endedAt - ReferenceTrace.shared.origin
        row["captureCostMs"] = (endedAt - startedAt) * 1_000
        row["visitedViews"] = visited
        row["truncated"] = truncated
        row["views"] = views
        previousTimestamp = link.timestamp
        ReferenceTrace.shared.add(row)
    }
}
