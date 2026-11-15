import SwiftUI
import UIKit

/// Host policy for a pair of scalar controls, not an Apple range-slider API.
struct ReferenceRangeConfiguration {
    let scale: CGFloat
    let lower: Float
    let upper: Float

    static var current: ReferenceRangeConfiguration {
        let arguments = ProcessInfo.processInfo.arguments
        func number(_ key: String) -> Double? {
            guard let index = arguments.firstIndex(of: key), index + 1 < arguments.count,
                  let value = Double(arguments[index + 1]), value.isFinite else { return nil }
            return value
        }
        let requestedScale = number("-ReferenceRangeScale") ?? 1
        let lower = Float(min(1, max(0, number("-ReferenceRangeLower") ?? 0.25)))
        let upper = Float(min(1, max(0, number("-ReferenceRangeUpper") ?? 0.75)))
        return ReferenceRangeConfiguration(scale: requestedScale == 0.75 ? 0.75 : 1,
                                           lower: min(lower, upper), upper: max(lower, upper))
    }

    var metadata: [String: Any] {
        ["composition": "host-composed range with two genuine public UISlider controls",
         "wholeControlScale": scale, "initialLower": lower, "initialUpper": upper,
         "domain": [0, 1], "constraint": "host clamps lower <= upper; both UISlider domains stay 0...1",
         "drawing": "system thumb drawing unchanged; native tracks clear via public tint properties; host paints one shared rail",
         "scaleScope": "uniform transform of the entire UISlider, including its native thumb and tracking coordinate space",
         "routing": "nearest model thumb center at DOWN; collapsed: left selects lower, right selects upper, exact-center tie selects lower",
         "nativeHitPolicy": "host chooses one UISlider; that slider's public hitTest chooses the actual native child; a nil native hit is not replaced with the slider",
         "routingLimit": "recipient remains fixed for that genuine UIKit touch; later drag direction cannot retarget it",
         "railPolicy": "two fresh native endpoint measurements with one width compensation; shared rail uses the final observed endpoint span at the exact whole-control scale, centered on the desired rail; desired/actual span difference is reported",
         "geometryScope": "cached super results from read-only UISlider layout overrides; model geometry is not the alpha contour or pressed presentation geometry"]
    }
}

struct NativeRangeReference: UIViewRepresentable {
    func makeUIView(context: Context) -> NativeRangeHostView {
        NativeRangeHostView(configuration: .current)
    }
    func updateUIView(_ view: NativeRangeHostView, context: Context) {}
}

/// Overrides only observe super's public layout and genuine UIControl lifecycle.
/// No private thumb is extracted, drawn, or assigned an accessibility proxy.
final class NativeRangeSlider: UISlider {
    struct ThumbSample {
        let bounds: CGRect
        let track: CGRect
        let value: Float
        let rect: CGRect
    }
    let endpoint: String
    private var samples: [ThumbSample] = []
    var geometryDidChange: (() -> Void)?
    var trackingObserved: ((String, UITouch?, Bool?) -> Void)?
    var routingDecision: [String: Any] = [:]

    init(endpoint: String) {
        self.endpoint = endpoint
        super.init(frame: .zero)
        minimumValue = 0
        maximumValue = 1
        minimumTrackTintColor = .clear
        maximumTrackTintColor = .clear
        tintColor = ReferencePalette.uiTint
        isContinuous = true
        isMultipleTouchEnabled = false
        isAccessibilityElement = true
        accessibilityIdentifier = "native-range-\(endpoint)"
        accessibilityLabel = "Native UISlider \(endpoint) endpoint"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func thumbRect(forBounds bounds: CGRect, trackRect rect: CGRect, value: Float) -> CGRect {
        let result = super.thumbRect(forBounds: bounds, trackRect: rect, value: value)
        samples.append(ThumbSample(bounds: bounds, track: rect, value: value, rect: result))
        if samples.count > 24 { samples.removeFirst(samples.count - 24) }
        return result
    }

    func sample(for value: Float) -> ThumbSample? {
        samples.last { abs($0.value - value) < 0.000_01 && $0.bounds == bounds }
    }

    func clearLayoutSamples() { samples.removeAll(keepingCapacity: true) }

    override func layoutSubviews() {
        super.layoutSubviews()
        geometryDidChange?()
    }

    override func beginTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        let accepted = super.beginTracking(touch, with: event)
        trackingObserved?("begin", touch, accepted)
        return accepted
    }
    override func continueTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        let accepted = super.continueTracking(touch, with: event)
        trackingObserved?("continue", touch, accepted)
        return accepted
    }
    override func endTracking(_ touch: UITouch?, with event: UIEvent?) {
        super.endTracking(touch, with: event)
        trackingObserved?("end", touch, nil)
    }
    override func cancelTracking(with event: UIEvent?) {
        super.cancelTracking(with: event)
        trackingObserved?("cancel", nil, nil)
    }

    func geometrySnapshot() -> [String: Any] {
        var row: [String: Any] = ["endpoint": endpoint, "value": value,
                                  "minimumValue": minimumValue, "maximumValue": maximumValue,
                                  "isTracking": isTracking, "boundsPt": rangeRect(bounds),
                                  "controlRectInWindowPt": rangeRect(convert(bounds, to: window)),
                                  "controlTransform": ["a": transform.a, "b": transform.b,
                                                       "c": transform.c, "d": transform.d,
                                                       "tx": transform.tx, "ty": transform.ty],
                                  "thumbSampleAvailable": false]
        if let sample = sample(for: value) {
            row["thumbSampleAvailable"] = true
            row["thumbModelRectLocalPt"] = rangeRect(sample.rect)
            row["thumbModelRectInWindowPt"] = rangeRect(convert(sample.rect, to: window))
            row["trackModelRectLocalPt"] = rangeRect(sample.track)
            row["thumbSampleValue"] = sample.value
        }
        return row
    }
}

final class NativeRangeHostView: UIView {
    private let configuration: ReferenceRangeConfiguration
    private let lower = NativeRangeSlider(endpoint: "lower")
    private let upper = NativeRangeSlider(endpoint: "upper")
    private let rail = UIView()
    private let selection = UIView()
    private let title = UILabel()
    private let detail = UILabel()
    private let status = UILabel()
    private var desiredRailRect = CGRect.zero
    private var railRect = CGRect.zero
    private var calibratedSize = CGSize.zero
    private var calibrating = false
    private var calibrationAvailable = false
    private var lastLayoutSignature = ""
    private var calibration: [String: Any] = [:]

    init(configuration: ReferenceRangeConfiguration) {
        self.configuration = configuration
        super.init(frame: .zero)
        accessibilityIdentifier = "native-range-host"
        isAccessibilityElement = false
        isMultipleTouchEnabled = false
        clipsToBounds = false
        title.text = "Host-composed range · native UISlider thumbs"
        title.font = .systemFont(ofSize: 15, weight: .semibold)
        title.numberOfLines = 2
        detail.text = "Whole UISlider scale \(configuration.scale) · host rail / ordering\nOverlap DOWN: left lower · right upper · center lower\nLater drag direction keeps the selected endpoint."
        detail.font = .systemFont(ofSize: 11)
        detail.numberOfLines = 0
        status.font = .monospacedDigitSystemFont(ofSize: 12, weight: .regular)
        status.accessibilityIdentifier = "native-range-status"
        rail.backgroundColor = UIColor.secondaryLabel.withAlphaComponent(0.25)
        selection.backgroundColor = ReferencePalette.uiTint
        for view in [title, detail, status, rail, selection] {
            view.isUserInteractionEnabled = false
            addSubview(view)
        }
        lower.value = configuration.lower
        upper.value = configuration.upper
        for slider in [lower, upper] {
            addSubview(slider)
            slider.addTarget(self, action: #selector(valueChanged(_:)), for: .valueChanged)
            slider.geometryDidChange = { [weak self] in
                guard let self, !self.calibrating else { return }
                self.refreshObservedGeometry()
            }
            slider.trackingObserved = { [weak self, weak slider] phase, touch, accepted in
                guard let self, let slider else { return }
                if phase == "begin", accepted == true { self.bringSubviewToFront(slider) }
                self.refreshObservedGeometry()
                var row: [String: Any] = ["event": "range-tracking", "scene": "range", "phase": phase,
                                          "control": slider.accessibilityIdentifier ?? "", "endpoint": slider.endpoint,
                                          "routingDecision": slider.routingDecision,
                                          "geometry": self.geometrySnapshot()]
                if let touch {
                    let point = touch.location(in: self.window)
                    row["touchInWindowPt"] = ["x": point.x, "y": point.y]
                }
                if let accepted { row["superAccepted"] = accepted }
                ReferenceTrace.shared.add(row)
            }
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func layoutSubviews() {
        super.layoutSubviews()
        title.frame = CGRect(x: 0, y: 8, width: bounds.width, height: 40)
        detail.frame = CGRect(x: 0, y: 52, width: bounds.width, height: 62)
        // The desired host span guides one width compensation. The final rail
        // uses observed native endpoints, including any native layout rounding.
        desiredRailRect = CGRect(x: 20, y: max(144, bounds.height * 0.58), width: max(1, bounds.width - 40), height: 6)
        rail.layer.cornerRadius = 3
        selection.layer.cornerRadius = 3
        if calibratedSize != bounds.size, !lower.isTracking, !upper.isTracking { calibrateControls() }
        rail.frame = railRect
        status.frame = CGRect(x: 0, y: railRect.maxY + 28, width: bounds.width, height: 42)
        refreshObservedGeometry()
        let signature = "\(bounds)-\(convert(bounds, to: window))-\(calibrationAvailable)"
        if signature != lastLayoutSignature {
            lastLayoutSignature = signature
            ReferenceTrace.shared.add(["event": "range-layout", "scene": "range", "configuration": configuration.metadata,
                                       "geometry": geometrySnapshot()])
        }
    }

    private func calibrateControls() {
        calibrating = true
        defer { calibrating = false }
        let values = (lower.value, upper.value)
        let scale = configuration.scale
        railRect = desiredRailRect
        var width = max(1, bounds.width / scale)
        let height = max(1, lower.intrinsicContentSize.height)
        var anchors: (NativeRangeSlider.ThumbSample, NativeRangeSlider.ThumbSample)?
        var measurements: [[String: Any]] = []
        var failureReason = "Fresh native endpoint samples unavailable"
        // UIKit, rather than a direct thumbRect call, requests the two endpoint
        // layouts. Only startup / resized idle layouts use these public values;
        // no calibration is performed while either slider is tracking.
        for pass in 1...2 {
            for slider in [lower, upper] {
                slider.transform = .identity
                slider.bounds = CGRect(x: 0, y: 0, width: width, height: height)
                slider.center = CGPoint(x: bounds.midX, y: desiredRailRect.midY)
                slider.clearLayoutSamples()
            }
            lower.setValue(0, animated: false)
            upper.setValue(1, animated: false)
            for slider in [lower, upper] { slider.setNeedsLayout(); slider.layoutIfNeeded() }
            guard let start = lower.sample(for: 0), let end = upper.sample(for: 1) else {
                measurements.append(["pass": pass, "nativeBoundsPt": rangeRect(lower.bounds),
                                     "freshEndpointSamplesAvailable": false])
                break
            }
            let travel = end.rect.midX - start.rect.midX
            let correction = desiredRailRect.width / scale - travel
            measurements.append(["pass": pass, "nativeBoundsPt": rangeRect(lower.bounds),
                                 "freshEndpointSamplesAvailable": true,
                                 "minimumThumbModelRectLocalPt": rangeRect(start.rect),
                                 "maximumThumbModelRectLocalPt": rangeRect(end.rect),
                                 "nativeTravelPt": travel, "transformedTravelPt": travel * scale,
                                 "desiredSpanPt": desiredRailRect.width,
                                 "spanDeltaPt": travel * scale - desiredRailRect.width,
                                 "nativeWidthCompensationPt": pass == 1 ? correction : 0])
            guard travel.isFinite, travel > 0 else {
                failureReason = "Fresh native endpoint span is non-finite or non-positive"
                break
            }
            if pass == 1 {
                let correctedWidth = width + correction
                guard correctedWidth.isFinite, correctedWidth > 0 else {
                    failureReason = "Observed native endpoint span produced invalid compensated bounds"
                    break
                }
                width = correctedWidth
            } else { anchors = (start, end) }
        }
        calibrationAvailable = anchors != nil
        if let (start, end) = anchors {
            let observedSpan = (end.rect.midX - start.rect.midX) * scale
            railRect = CGRect(x: desiredRailRect.midX - observedSpan / 2, y: desiredRailRect.minY,
                              width: observedSpan, height: desiredRailRect.height)
            let nativeMidpoint = (start.rect.midX + end.rect.midX) / 2
            let center = CGPoint(x: desiredRailRect.midX - scale * (nativeMidpoint - width / 2),
                                 y: desiredRailRect.midY - scale * (start.rect.midY - height / 2))
            for slider in [lower, upper] {
                slider.center = center
                slider.transform = CGAffineTransform(scaleX: scale, y: scale)
            }
            calibration = ["available": true, "passes": measurements.count, "measurements": measurements,
                           "nativeBoundsPt": rangeRect(lower.bounds),
                           "minimumThumbModelRectLocalPt": rangeRect(start.rect),
                           "maximumThumbModelRectLocalPt": rangeRect(end.rect),
                           "nativeTravelPt": end.rect.midX - start.rect.midX,
                           "transformedTravelPt": (end.rect.midX - start.rect.midX) * scale,
                           "desiredSpanPt": desiredRailRect.width, "actualSpanPt": observedSpan,
                           "spanDeltaPt": observedSpan - desiredRailRect.width,
                           "railPolicy": "exact whole-control scale; final observed endpoints define the shared rail centered on the desired rail; no span-convergence threshold",
                           "source": "UIKit layout invokes read-only thumbRect override; override returns super unchanged",
                           "samplingPolicy": "public setValue(0/1) during initial or resized idle layout; restore both current values before display; never calibrate during tracking"]
        } else {
            calibration = ["available": false, "passes": measurements.count, "measurements": measurements,
                           "desiredSpanPt": desiredRailRect.width, "reason": failureReason,
                           "samplingPolicy": "two fresh endpoint layouts required; no guessed anchors"]
        }
        lower.setValue(values.0, animated: false)
        upper.setValue(values.1, animated: false)
        for slider in [lower, upper] { slider.setNeedsLayout(); slider.layoutIfNeeded() }
        lower.isEnabled = calibrationAvailable
        upper.isEnabled = calibrationAvailable
        calibratedSize = bounds.size
    }

    private func thumbRect(_ slider: NativeRangeSlider) -> CGRect? {
        slider.sample(for: slider.value).map { slider.convert($0.rect, to: self) }
    }

    private func refreshObservedGeometry() {
        guard !calibrating else { return }
        let lowerRect = thumbRect(lower)
        let upperRect = thumbRect(upper)
        for (slider, rect) in [(lower, lowerRect), (upper, upperRect)] {
            if let rect {
                // These are the real UISlider accessibility nodes. Only their
                // public accessibility frames follow the observed model thumbs.
                slider.accessibilityFrame = UIAccessibility.convertToScreenCoordinates(rect, in: self)
            }
        }
        selection.isHidden = !calibrationAvailable || lowerRect == nil || upperRect == nil
        if let lowerRect, let upperRect {
            selection.frame = CGRect(x: lowerRect.midX, y: railRect.minY,
                                     width: max(0, upperRect.midX - lowerRect.midX), height: railRect.height)
        }
        status.text = calibrationAvailable
            ? String(format: "lower %.3f  upper %.3f", Double(lower.value), Double(upper.value))
            : "Native endpoint geometry unavailable"
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard !isHidden, alpha > 0.01, isUserInteractionEnabled, self.point(inside: point, with: event),
              calibrationAvailable, let lowerRect = thumbRect(lower), let upperRect = thumbRect(upper) else {
            return super.hitTest(point, with: event)
        }
        let band = lower.convert(lower.bounds, to: self).union(upper.convert(upper.bounds, to: self))
        guard band.contains(point) else { return super.hitTest(point, with: event) }
        let collapsed = abs(lower.value - upper.value) < 0.000_01
        let lowerDistance = abs(point.x - lowerRect.midX)
        let upperDistance = abs(point.x - upperRect.midX)
        let chosen = collapsed ? (point.x <= lowerRect.midX ? lower : upper)
            : (lowerDistance <= upperDistance ? lower : upper)
        let nativePoint = chosen.convert(point, from: self)
        let nativeHit = chosen.hitTest(nativePoint, with: event)
        chosen.routingDecision = ["phase": "DOWN hitTest", "hostPointPt": ["x": point.x, "y": point.y],
                                  "collapsed": collapsed, "selectedEndpoint": chosen.endpoint,
                                  "selectedControlIdentifier": chosen.accessibilityIdentifier ?? "",
                                  "nativePointPt": ["x": nativePoint.x, "y": nativePoint.y],
                                  "nativeHitClass": nativeHit.map { String(describing: type(of: $0)) } ?? "",
                                  "nativeHitIdentifier": nativeHit?.accessibilityIdentifier ?? "",
                                  "nativeHitAvailable": nativeHit != nil,
                                  "lowerThumbCenterXPt": lowerRect.midX, "upperThumbCenterXPt": upperRect.midX,
                                  "policy": collapsed ? "left lower; right upper; exact-center lower" : "nearest model thumb center; tie lower",
                                  "laterDirectionRetargeting": false]
        return nativeHit
    }

    @objc private func valueChanged(_ slider: NativeRangeSlider) {
        let proposed = slider.value
        let accepted = slider === lower ? min(proposed, upper.value) : max(proposed, lower.value)
        if accepted != proposed { slider.setValue(accepted, animated: false) }
        refreshObservedGeometry()
        ReferenceTrace.shared.add(["event": "range-value", "scene": "range",
                                   "control": slider.accessibilityIdentifier ?? "", "endpoint": slider.endpoint,
                                   "proposedValue": proposed, "acceptedValue": accepted,
                                   "lower": lower.value, "upper": upper.value, "hostClamped": proposed != accepted])
    }

    func geometrySnapshot() -> [String: Any] {
        ["hostBoundsPt": rangeRect(bounds), "hostRectInWindowPt": rangeRect(convert(bounds, to: window)),
         "windowBoundsPt": window.map { rangeRect($0.bounds) } ?? [:],
         "desiredRailRectInHostPt": rangeRect(desiredRailRect),
         "railRectInHostPt": rangeRect(railRect), "railRectInWindowPt": rangeRect(convert(railRect, to: window)),
         "calibration": calibration, "wholeControlScale": configuration.scale,
         "lower": lower.geometrySnapshot(), "upper": upper.geometrySnapshot(),
         "accessibilityFrameScope": "real UISlider nodes use public screen-coordinate accessibilityFrame converted from cached model thumb rect in host coordinates; no proxy thumb nodes",
         "geometryCoordinates": "all rect suffixes state their coordinate space; points, not pixels"]
    }
}

private func rangeRect(_ rect: CGRect) -> [String: CGFloat] {
    ["x": rect.minX, "y": rect.minY, "w": rect.width, "h": rect.height]
}
