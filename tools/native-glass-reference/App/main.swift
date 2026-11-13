import SwiftUI
import UIKit

enum ReferencePalette {
    static let uiTint = UIColor(red: 0, green: 0.65, blue: 0.75, alpha: 1)
    static let tint = Color(uiTint)
    static let stripeColors: [UIColor] = [.systemRed, .systemOrange, .systemYellow, .systemGreen, .systemTeal, .systemBlue, .systemPurple]

    static func resolvedSRGB(_ color: UIColor, traits: UITraitCollection) -> [CGFloat]? {
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let converted = color.resolvedColor(with: traits).cgColor.converted(to: space, intent: .defaultIntent, options: nil) else { return nil }
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        guard UIColor(cgColor: converted).getRed(&red, green: &green, blue: &blue, alpha: &alpha) else { return nil }
        return [red, green, blue, alpha]
    }
}

enum ReferenceScene: String, CaseIterable {
    case toggle, segmented, slider, buttons, presentations, language
    var label: String {
        switch self {
        case .toggle: return "Toggle"
        case .segmented: return "Select"
        case .slider: return "Slider"
        case .buttons: return "Button"
        case .presentations: return "Present"
        case .language: return "Lang"
        }
    }
}

enum ReferenceBackground: String, CaseIterable {
    case grid, stripes, fine_grid, color_stripes, tonal_blocks, synthetic_landscape

    var metadata: [String: Any] {
        switch self {
        case .grid: return ["id": rawValue, "input": "Legacy opaque (0.94,0.94,0.96), 20pt grid, black alpha 0.13, 0.5pt stroke"]
        case .stripes: return ["id": rawValue, "input": "Legacy 28pt system-color stripes at alpha 0.52 over legacy base"]
        case .fine_grid: return ["id": rawValue, "input": "Opaque #F0F0F5, 8pt black/white fine grid, 0.5pt stroke"]
        case .color_stripes: return ["id": rawValue, "input": "Opaque 18pt #F51D39/#FFD51C/#00CB69/#00DCED/#1552F3/#F218DD stripes"]
        case .tonal_blocks: return ["id": rawValue, "input": "Opaque 112pt alternating #101018/#F5F5F0 large blocks"]
        case .synthetic_landscape: return ["id": rawValue, "input": "Procedural 720x1280 landscape v1, seed 0x51EE9; synthetic, not a photograph"]
        }
    }

    static func opaqueRGB(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: 1)
    }

    // The Android fixture uses overflowing signed Int arithmetic followed by
    // unsigned shifts. Wrapping UInt32 preserves those exact bit operations.
    static let landscape: UIImage = {
        let width = 720, height = 1280
        var pixels = [UInt8](repeating: 255, count: width * height * 4)
        func noise(_ x: Int, _ y: Int) -> Double {
            var n = UInt32(truncatingIfNeeded: x) &* 374761393 &+
                UInt32(truncatingIfNeeded: y) &* 668265263 &+ 0x51EE9
            n = (n ^ (n >> 13)) &* 1274126177
            return Double((n ^ (n >> 16)) & 65535) / 65535
        }
        func ridge(_ u: Double, _ phase: Double) -> Double {
            0.36 + 0.035 * sin(u * 15 + phase) + 0.045 * sin(u * 5.7 + phase) + 0.012 * sin(u * 43 - phase)
        }
        func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
        for y in 0..<height { for x in 0..<width {
            let u = Double(x) / Double(width), v = Double(y) / Double(height)
            let grain = (noise(x, y) - 0.5) * 0.035
            let skyY = v < 0.53 ? v : max(0, 1.06 - v + 0.003 * sin(v * 550 + u * 45))
            let cloud = (sin(u * 17 + skyY * 13) + cos(u * 37 - skyY * 19) + sin(u * 71 + skyY * 46)) / 3
            let cloudMask = clamp((cloud + 0.3) * 1.8) * exp(-pow((skyY - 0.22) / 0.14, 2))
            var r = 0.19 + skyY * 0.95 + cloudMask * 0.42
            var g = 0.43 + skyY * 0.63 + cloudMask * 0.31
            var b = 0.72 + skyY * 0.2 + cloudMask * 0.13
            if skyY > ridge(u, 0.8) {
                r = 0.23 + skyY * 0.20; g = 0.32 + skyY * 0.17; b = 0.38 + skyY * 0.15
            }
            if skyY > ridge(u, 3.6) + 0.07 {
                let crag = noise(x / 3, Int(skyY * Double(height)) / 2) * 0.12
                r = 0.11 + crag; g = 0.22 + crag; b = 0.23 + crag * 0.7
            }
            if v >= 0.53 {
                let ripple = sin(v * 900 + u * 38) * 0.025 + noise(x / 5, y) * 0.035
                r = r * 0.62 + ripple; g = g * 0.80 + ripple; b = b * 0.84 + ripple
            }
            let shore = 0.86 + 0.028 * sin(u * 8) + 0.02 * sin(u * 29)
            if v > shore {
                let rock = noise(x / 4, y / 4) * 0.16 + 0.055 * sin(u * 91 + v * 71)
                r = 0.21 + rock; g = 0.19 + rock * 0.9; b = 0.13 + rock * 0.75
            }
            let tree = (u < 0.16 || u > 0.89) && v > 0.50 && v < 0.89 &&
                abs((u * 34).truncatingRemainder(dividingBy: 1) - 0.5) <
                (v * 37).truncatingRemainder(dividingBy: 1) * 0.28 + 0.08
            if tree { r = 0.045; g = 0.09; b = 0.065 }
            let index = (y * width + x) * 4
            pixels[index] = UInt8(clamp(r + grain) * 255)
            pixels[index + 1] = UInt8(clamp(g + grain) * 255)
            pixels[index + 2] = UInt8(clamp(b + grain) * 255)
        } }
        let data = Data(pixels) as CFData
        let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                            bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
                            provider: CGDataProvider(data: data)!, decode: nil,
                            shouldInterpolate: true, intent: .defaultIntent)!
        return UIImage(cgImage: image)
    }()
}

final class ReferenceBackgroundView: UIView {
    var background: ReferenceBackground = .grid { didSet { setNeedsDisplay() } }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        UIColor(red: 0.94, green: 0.94, blue: 0.96, alpha: 1).setFill()
        context.fill(bounds)
        if background == .stripes {
            let colors = ReferencePalette.stripeColors
            for index in 0...Int(ceil(bounds.width / 28)) {
                colors[index % colors.count].withAlphaComponent(0.52).setFill()
                context.fill(CGRect(x: CGFloat(index) * 28, y: 0, width: 28, height: bounds.height))
            }
        } else if background == .grid {
            context.setStrokeColor(UIColor.black.withAlphaComponent(0.13).cgColor)
            context.setLineWidth(0.5)
            for x in stride(from: CGFloat(0), through: bounds.width, by: 20) {
                context.move(to: CGPoint(x: x, y: 0)); context.addLine(to: CGPoint(x: x, y: bounds.height))
            }
            for y in stride(from: CGFloat(0), through: bounds.height, by: 20) {
                context.move(to: CGPoint(x: 0, y: y)); context.addLine(to: CGPoint(x: bounds.width, y: y))
            }
            context.strokePath()
        } else {
            ReferenceBackground.opaqueRGB(0xF0F0F5).setFill()
            context.fill(bounds)
            switch background {
            case .fine_grid:
                context.setLineWidth(0.5)
                for index in 0...Int(ceil(bounds.width / 8)) {
                    context.setStrokeColor((index % 2 == 0 ? UIColor.black.withAlphaComponent(0.55) : .white).cgColor)
                    context.move(to: CGPoint(x: CGFloat(index) * 8, y: 0))
                    context.addLine(to: CGPoint(x: CGFloat(index) * 8, y: bounds.height)); context.strokePath()
                }
                for index in 0...Int(ceil(bounds.height / 8)) {
                    context.setStrokeColor((index % 2 == 0 ? UIColor.black.withAlphaComponent(0.55) : .white).cgColor)
                    context.move(to: CGPoint(x: 0, y: CGFloat(index) * 8))
                    context.addLine(to: CGPoint(x: bounds.width, y: CGFloat(index) * 8)); context.strokePath()
                }
            case .color_stripes:
                let palette: [UInt32] = [0xF51D39, 0xFFD51C, 0x00CB69, 0x00DCED, 0x1552F3, 0xF218DD]
                for index in 0...Int(ceil(bounds.width / 18)) {
                    ReferenceBackground.opaqueRGB(palette[index % palette.count]).setFill()
                    context.fill(CGRect(x: CGFloat(index) * 18, y: 0, width: 18, height: bounds.height))
                }
            case .tonal_blocks:
                for y in 0...Int(ceil(bounds.height / 112)) { for x in 0...Int(ceil(bounds.width / 112)) {
                    ReferenceBackground.opaqueRGB((x + y) % 2 == 0 ? 0x101018 : 0xF5F5F0).setFill()
                    context.fill(CGRect(x: CGFloat(x) * 112, y: CGFloat(y) * 112, width: 112, height: 112))
                } }
            case .synthetic_landscape:
                context.interpolationQuality = .low
                ReferenceBackground.landscape.draw(in: bounds)
            case .grid, .stripes: break
            }
        }
    }
}

/// Public configuration and effective traits, not inferred glass coefficients.
/// In particular, UIBlurEffect has no public getter for its creation style.
private enum ReferenceDockPublicSnapshot {
    static func record(_ tabBar: UITabBar, phase: String, parent: String,
                       controller: UITabBarController? = nil) {
        let traits = tabBar.traitCollection
        func color(_ value: UIColor?) -> Any {
            value.flatMap { ReferencePalette.resolvedSRGB($0, traits: traits) }.map { $0 as Any } ?? NSNull()
        }
        func appearance(_ value: UITabBarAppearance?) -> Any {
            guard let value else { return NSNull() }
            return [
                "backgroundEffectPresent": value.backgroundEffect != nil,
                "backgroundColorSRGB": color(value.backgroundColor),
                "backgroundImagePresent": value.backgroundImage != nil,
                "shadowColorSRGB": color(value.shadowColor),
                "shadowImagePresent": value.shadowImage != nil,
                "selectionIndicatorTintSRGB": color(value.selectionIndicatorTintColor),
                "selectionIndicatorImagePresent": value.selectionIndicatorImage != nil,
                "stackedItemPositioning": value.stackedItemPositioning.rawValue,
                "stackedItemWidth": value.stackedItemWidth,
                "stackedItemSpacing": value.stackedItemSpacing,
                "selectedIconSRGB": color(value.stackedLayoutAppearance.selected.iconColor),
                "normalIconSRGB": color(value.stackedLayoutAppearance.normal.iconColor),
            ] as [String: Any]
        }
        let frame = tabBar.convert(tabBar.bounds, to: tabBar.window)
        var row: [String: Any] = [
            "event": "tab-public-configuration", "phase": phase, "dockParent": parent,
            "attachedToWindow": tabBar.window != nil,
            "barStyle": tabBar.barStyle.rawValue, "isTranslucent": tabBar.isTranslucent,
            "overrideInterfaceStyle": tabBar.overrideUserInterfaceStyle.rawValue,
            "effectiveInterfaceStyle": traits.userInterfaceStyle.rawValue,
            "windowInterfaceStyle": tabBar.window?.traitCollection.userInterfaceStyle.rawValue ?? -1,
            "accessibilityContrast": traits.accessibilityContrast.rawValue,
            "userInterfaceLevel": traits.userInterfaceLevel.rawValue,
            "legibilityWeight": traits.legibilityWeight.rawValue,
            "displayScale": traits.displayScale,
            "horizontalSizeClass": traits.horizontalSizeClass.rawValue,
            "verticalSizeClass": traits.verticalSizeClass.rawValue,
            "preferredContentSizeCategory": traits.preferredContentSizeCategory.rawValue,
            "tintSRGB": color(tabBar.tintColor), "barTintSRGB": color(tabBar.barTintColor),
            "unselectedItemTintSRGB": color(tabBar.unselectedItemTintColor),
            "backgroundColorSRGB": color(tabBar.backgroundColor),
            "alpha": tabBar.alpha, "isOpaque": tabBar.isOpaque,
            "frameInWindow": ["x": frame.minX, "y": frame.minY, "w": frame.width, "h": frame.height],
            "safeAreaBottom": tabBar.safeAreaInsets.bottom,
            "tabCount": tabBar.items?.count ?? 0,
            "standardAppearance": appearance(tabBar.standardAppearance),
            "scrollEdgeAppearance": appearance(tabBar.scrollEdgeAppearance),
        ]
        if let controller {
            row["controllerInterfaceStyle"] = controller.traitCollection.userInterfaceStyle.rawValue
            row["controllerBackgroundColorSRGB"] = color(controller.view.backgroundColor)
            row["controllerSafeAreaBottom"] = controller.view.safeAreaInsets.bottom
            row["children"] = controller.children.map { child in
                ["safeAreaBottom": child.view.safeAreaInsets.bottom,
                 "additionalSafeAreaBottom": child.additionalSafeAreaInsets.bottom]
            }
        }
        if let window = tabBar.window {
            func canvases(in view: UIView) -> [[String: CGFloat]] {
                let own: [[String: CGFloat]]
                if view is ReferenceBackgroundView {
                    let frame = view.convert(view.bounds, to: window)
                    own = [["x": frame.minX, "y": frame.minY, "w": frame.width, "h": frame.height]]
                } else { own = [] }
                return own + view.subviews.flatMap { canvases(in: $0) }
            }
            row["backgroundCanvasesInWindow"] = canvases(in: window)
        }
        ReferenceTrace.shared.add(row)
    }
}

/// Read only public containment, backgrounds and scroll geometry. These values
/// describe the material's context; they do not expose a shader or blur style.
private enum ReferenceDockMaterialContext {
    static func snapshot(_ controller: UITabBarController, probe: UIView?) -> [String: Any] {
        let window = controller.view.window
        func rect(_ value: CGRect) -> [String: CGFloat] {
            ["x": value.minX, "y": value.minY, "w": value.width, "h": value.height]
        }
        func insets(_ value: UIEdgeInsets) -> [String: CGFloat] {
            ["top": value.top, "left": value.left, "bottom": value.bottom, "right": value.right]
        }
        func view(_ value: UIView) -> [String: Any] {
            ["class": String(describing: type(of: value)),
             "frameInWindow": rect(value.convert(value.bounds, to: window)),
             "backgroundColorSRGB": ReferencePalette.resolvedSRGB(value.backgroundColor ?? .clear,
                                                                 traits: value.traitCollection).map { $0 as Any } ?? NSNull(),
             "backgroundColorPresent": value.backgroundColor != nil,
             "effectiveInterfaceStyle": value.traitCollection.userInterfaceStyle.rawValue,
             "overrideInterfaceStyle": value.overrideUserInterfaceStyle.rawValue,
             "safeAreaInsets": insets(value.safeAreaInsets), "alpha": value.alpha,
             "isOpaque": value.isOpaque, "isHidden": value.isHidden,
             "clipsToBounds": value.clipsToBounds]
        }
        func ancestors(_ start: UIView?) -> [[String: Any]] {
            var rows: [[String: Any]] = [], current = start
            while let candidate = current {
                rows.append(view(candidate)); current = candidate.superview
            }
            return rows
        }
        func canvases(_ candidate: UIView) -> [[String: Any]] {
            let own = candidate is ReferenceBackgroundView ? [view(candidate)] : []
            return own + candidate.subviews.flatMap { canvases($0) }
        }
        var parents: [[String: Any]] = [], current: UIViewController? = controller
        while let candidate = current {
            var row: [String: Any] = ["class": String(describing: type(of: candidate)),
                                      "additionalSafeAreaInsets": insets(candidate.additionalSafeAreaInsets)]
            if let loaded = candidate.viewIfLoaded { row["view"] = view(loaded) }
            if let navigation = candidate as? UINavigationController {
                row["navigationBarHidden"] = navigation.isNavigationBarHidden
                row["navigationBarTranslucent"] = navigation.navigationBar.isTranslucent
            }
            parents.append(row); current = candidate.parent
        }
        var scrolls: [[String: Any]] = [], effects: [[String: Any]] = []
        func scan(_ candidate: UIView, path: String, forScrolls: Bool) {
            if forScrolls, let scroll = candidate as? UIScrollView {
                var row = view(scroll)
                row["path"] = path
                row["contentOffset"] = ["x": scroll.contentOffset.x, "y": scroll.contentOffset.y]
                row["contentSize"] = ["w": scroll.contentSize.width, "h": scroll.contentSize.height]
                row["contentInset"] = insets(scroll.contentInset)
                row["adjustedContentInset"] = insets(scroll.adjustedContentInset)
                row["contentInsetAdjustmentBehavior"] = scroll.contentInsetAdjustmentBehavior.rawValue
                row["isTracking"] = scroll.isTracking; row["isDragging"] = scroll.isDragging
                row["isDecelerating"] = scroll.isDecelerating
                scrolls.append(row)
            } else if !forScrolls, let effect = candidate as? UIVisualEffectView {
                var row = view(effect)
                row["path"] = path; row["effectPresent"] = effect.effect != nil
                effects.append(row)
            }
            for (index, child) in candidate.subviews.enumerated() {
                scan(child, path: path + "/\(index)", forScrolls: forScrolls)
            }
        }
        if let selected = controller.selectedViewController?.viewIfLoaded { scan(selected, path: "selected", forScrolls: true) }
        scan(controller.tabBar, path: "tabBar", forScrolls: false)
        return ["event": "tab-material-context", "dockParent": "swiftui-tabview",
                "sampleScope": "public view/controller/scroll state; not material coefficients or active appearance inference",
                "selectedIndex": controller.selectedIndex,
                "probeAncestors": ancestors(probe), "tabBarAncestors": ancestors(controller.tabBar),
                "controllerParents": parents, "selectedPageScrollViews": scrolls,
                "tabBarPublicEffectViews": effects,
                "backgroundCanvasesInWindow": window.map { canvases($0) } ?? [],
                "windowBounds": window.map { rect($0.bounds) } ?? [:],
                "windowSafeAreaInsets": window.map { insets($0.safeAreaInsets) } ?? [:]]
    }
}

/// Opt-in production-parent probe. UIKit owns the bar frame, material and input.
/// The palette is the existing reference palette, not Sleepy's theme database.
private final class ReferenceSwiftUIDockObservation {
    private var observed = Set<ObjectIdentifier>()
    private weak var controller: UITabBarController?
    private weak var probe: UIView?
    private var lastContext: Data?

    func recordContextIfChanged(phase: String) {
        guard let controller, controller.viewIfLoaded?.window != nil else { return }
        var row = ReferenceDockMaterialContext.snapshot(controller, probe: probe)
        guard let data = try? JSONSerialization.data(withJSONObject: row, options: [.sortedKeys]),
              data != lastContext else { return }
        lastContext = data
        row["phase"] = phase
        ReferenceTrace.shared.add(row)
        ReferenceTrace.shared.flush()
    }

    func apply(from probe: UIView) {
        var chain: [UIView] = []
        var current = probe.superview
        var controller: UITabBarController?
        while let view = current, let window = view.window, view !== window {
            chain.append(view)
            if controller == nil { controller = view.next as? UITabBarController }
            current = view.superview
        }
        guard let controller else { return }
        self.controller = controller
        self.probe = probe
        let firstDiscovery = observed.insert(ObjectIdentifier(controller)).inserted
        if firstDiscovery {
            // SwiftUI created and attached this bar. "before" means before our
            // production policy, not an observation of an unparented default.
            ReferenceDockPublicSnapshot.record(controller.tabBar, phase: "before", parent: "swiftui-tabview", controller: controller)
        }
        // SleepyApp.TabBarTransparentProbe's public background/safe-area policy.
        for view in chain where view.backgroundColor != .clear { view.backgroundColor = .clear }
        let tabBar = controller.tabBar
        tabBar.accessibilityIdentifier = "swiftui-tabbar"
        func matches(_ appearance: UITabBarAppearance) -> Bool {
            appearance.selectionIndicatorTintColor == ReferencePalette.uiTint &&
            [appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance,
             appearance.compactInlineLayoutAppearance].allSatisfy { item in
                item.selected.iconColor == .white && item.normal.iconColor == .secondaryLabel &&
                item.selected.titleTextAttributes[.foregroundColor] as? UIColor == .white &&
                item.normal.titleTextAttributes[.foregroundColor] as? UIColor == .secondaryLabel
            }
        }
        func themed(_ source: UITabBarAppearance) -> UITabBarAppearance {
            let appearance = source.copy() as! UITabBarAppearance
            appearance.selectionIndicatorTintColor = ReferencePalette.uiTint
            for item in [appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance,
                         appearance.compactInlineLayoutAppearance] {
                item.selected.iconColor = .white
                var selectedTitle = item.selected.titleTextAttributes
                selectedTitle[.foregroundColor] = UIColor.white
                item.selected.titleTextAttributes = selectedTitle
                item.normal.iconColor = .secondaryLabel
                var normalTitle = item.normal.titleTextAttributes
                normalTitle[.foregroundColor] = UIColor.secondaryLabel
                item.normal.titleTextAttributes = normalTitle
            }
            return appearance
        }
        if !matches(tabBar.standardAppearance) { tabBar.standardAppearance = themed(tabBar.standardAppearance) }
        if tabBar.scrollEdgeAppearance.map(matches) != true {
            tabBar.scrollEdgeAppearance = themed(tabBar.scrollEdgeAppearance ?? tabBar.standardAppearance)
        }
        if tabBar.tintColor != .white { tabBar.tintColor = .white }
        if tabBar.unselectedItemTintColor != .secondaryLabel { tabBar.unselectedItemTintColor = .secondaryLabel }
        for child in controller.children {
            let base = child.view.safeAreaInsets.bottom - child.additionalSafeAreaInsets.bottom
            let target = -max(base - 34, 0)
            if abs(child.additionalSafeAreaInsets.bottom - target) > 0.5 {
                var insets = child.additionalSafeAreaInsets
                insets.bottom = target
                child.additionalSafeAreaInsets = insets
            }
        }
        if firstDiscovery {
            DispatchQueue.main.async { [weak self, weak controller] in
                guard let controller else { return }
                ReferenceDockPublicSnapshot.record(controller.tabBar, phase: "attached", parent: "swiftui-tabview", controller: controller)
                self?.recordContextIfChanged(phase: "attached")
            }
        }
    }
}

private struct ReferenceDockParentProbe: UIViewRepresentable {
    let observation: ReferenceSwiftUIDockObservation
    func makeUIView(context: Context) -> ReferenceDockParentProbeView {
        let view = ReferenceDockParentProbeView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        view.applyToHierarchy = { observation.apply(from: $0) }
        view.scheduleApply()
        return view
    }
    func updateUIView(_ view: ReferenceDockParentProbeView, context: Context) { view.scheduleApply() }
}

private final class ReferenceDockParentProbeView: UIView {
    var applyToHierarchy: ((UIView) -> Void)?
    private var scheduled = false
    override func didMoveToSuperview() { super.didMoveToSuperview(); scheduleApply() }
    override func didMoveToWindow() { super.didMoveToWindow(); scheduleApply() }
    func scheduleApply() {
        guard !scheduled else { return }
        scheduled = true
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.scheduled = false
            guard self.window != nil else { return }
            self.applyToHierarchy?(self)
        }
    }
}

private struct ReferenceDockCanvas: UIViewRepresentable {
    let background: ReferenceBackground
    func makeUIView(context: Context) -> ReferenceBackgroundView {
        let view = ReferenceBackgroundView()
        view.background = background
        view.isOpaque = true
        view.isUserInteractionEnabled = false
        return view
    }
    func updateUIView(_ view: ReferenceBackgroundView, context: Context) { view.background = background }
}

private enum ReferenceDockContent: String {
    case placeholder
    case syntheticScroll = "synthetic-scroll"
}

private struct ReferenceDockTabs: View {
    let background: ReferenceBackground
    let observation: ReferenceSwiftUIDockObservation
    let content: ReferenceDockContent
    @State private var selection = 0
    private let tabs = [("Schedule", "calendar"), ("Today", "sun.max"), ("Manage", "square.grid.2x2"), ("Mine", "person")]

    var body: some View {
        TabView(selection: $selection) {
            ForEach(0..<tabs.count, id: \.self) { index in
                Group {
                    if content == .syntheticScroll {
                        // A real SwiftUI ScrollView supplies native scroll-edge
                        // context. Sparse synthetic text leaves the optical
                        // canvas visible; it is not a production business page.
                        ScrollView {
                            VStack(alignment: .leading, spacing: 16) {
                                title
                                ForEach(0..<24, id: \.self) { row in
                                    Text("Synthetic scroll row \(row + 1)")
                                        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                                }
                            }
                            .padding(16)
                            .padding(.bottom, 92)
                        }
                        .accessibilityIdentifier("dock-synthetic-scroll-\(index)")
                    } else {
                        VStack { title.padding(.top, 10); Spacer() }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .tint(ReferencePalette.tint)
                .ignoresSafeArea(.container, edges: .bottom)
                .overlay(ReferenceDockParentProbe(observation: observation).allowsHitTesting(false).ignoresSafeArea())
                .tabItem { Image(systemName: tabs[index].1); Text(tabs[index].0) }
                .tag(index)
            }
        }
        .tint(.white)
        .ignoresSafeArea(.container, edges: .bottom)
        .background(ReferenceDockCanvas(background: background).ignoresSafeArea())
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .onChange(of: selection) { value in
            ReferenceTrace.shared.add(["event": "value", "control": "swiftui-tabbar", "value": value])
        }
    }

    private var title: some View {
        Text("iOS 26.5 · SwiftUI TabView Reference")
            .font(.system(size: 16, weight: .semibold))
            .accessibilityIdentifier("reference-title")
    }
}

/// Mirrors the production outer hosting -> hidden navigation -> page hosting
/// containment without importing its navigation state or business pages.
private struct ReferenceDockNavigationParent: UIViewControllerRepresentable {
    let background: ReferenceBackground
    let content: ReferenceDockContent

    func makeUIViewController(context: Context) -> UINavigationController {
        let host = ReferenceDockHostingController(background: background, content: content)
        let navigation = UINavigationController(rootViewController: host)
        navigation.setNavigationBarHidden(true, animated: false)
        navigation.view.backgroundColor = .systemBackground
        host.view.backgroundColor = .systemBackground
        return navigation
    }
    func updateUIViewController(_ controller: UINavigationController, context: Context) {}
}

private final class ReferenceDockHostingController: UIHostingController<ReferenceDockTabs> {
    private let background: ReferenceBackground
    private let content: ReferenceDockContent
    private let observation: ReferenceSwiftUIDockObservation
    private let recorder = PresentationRecorder()
    private var recordingStarted = false
    private var contextTimer: Timer?

    init(background: ReferenceBackground, content: ReferenceDockContent) {
        self.background = background
        self.content = content
        let observation = ReferenceSwiftUIDockObservation()
        self.observation = observation
        super.init(rootView: ReferenceDockTabs(background: background, observation: observation, content: content))
    }
    deinit { contextTimer?.invalidate() }
    @MainActor required dynamic init?(coder aDecoder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !recordingStarted, let window = view.window else { return }
        recordingStarted = true
        ReferenceTrace.shared.beginSession(window: window)
        // Read-only, deduplicated sampling also runs for static optical captures.
        // No delegate or gesture recognizer is installed on native controls.
        let timer = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in
            self?.observation.recordContextIfChanged(phase: "sampled")
        }
        RunLoop.main.add(timer, forMode: .common)
        contextTimer = timer
        let staticOptics = ProcessInfo.processInfo.arguments.contains("-ReferenceStaticOptics")
        ReferenceTrace.shared.add(["event": "reference-configuration", "scene": "dock-parent",
                                   "dockParent": "swiftui-tabview", "tabCount": 4,
                                   "background": background.metadata,
                                   "interfaceStyle": window.traitCollection.userInterfaceStyle.rawValue,
                                   "staticOptics": staticOptics,
                                   "safeAreaPolicy": "Sleepy child.additionalSafeAreaInsets.bottom = -max(baseBottom - 34, 0)",
                                   "paletteScope": "reference tint/white/secondaryLabel; production role slots",
                                   "contentMode": content.rawValue,
                                   "publicContextSamplingIntervalSeconds": 0.25,
                                   "backgroundInputScope": "uncomposited full-window optical input; verify actual canvas window rectangles in context samples",
                                   "navigationContext": "outer SwiftUI host -> hidden UINavigationController -> tab page host",
                                   "scrollBottomPaddingPt": content == .syntheticScroll ? 92 : 0,
                                   "contentScope": "four synthetic pages; SF Symbols; optical root canvas; no production business content"])
        recorder.context = { [background] in
            ["scene": "dock-parent", "dockParent": "swiftui-tabview", "background": background.rawValue, "tabCount": 4]
        }
        if staticOptics {
            let canvas = ReferenceBackgroundView(frame: window.bounds)
            canvas.background = background
            let format = UIGraphicsImageRendererFormat()
            format.scale = window.screen.scale
            format.opaque = true
            format.preferredRange = .standard
            let input = UIGraphicsImageRenderer(bounds: canvas.bounds, format: format).image { _ in canvas.draw(canvas.bounds) }
            try? input.pngData()?.write(to: ReferenceTrace.shared.directory.appendingPathComponent("background-input.png"))
        } else { recorder.start(window: window) }
    }
}

final class ReferenceViewController: UIViewController, UITabBarDelegate {
    private let background = ReferenceBackgroundView()
    private let scenePicker = UISegmentedControl(items: ReferenceScene.allCases.map(\.label))
    private let backgroundPicker = UISegmentedControl(items: ["Grid", "Stripes"])
    private let heading = UILabel()
    private let tabBar = UITabBar()
    private let recorder = PresentationRecorder()
    private var host: UIHostingController<NativeStage>?
    private var scene: ReferenceScene = .toggle
    private var backgroundName = "grid"
    private var recordingStarted = false
    private var recordedTabLayout: [CGFloat] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        let arguments = ProcessInfo.processInfo.arguments
        func argument(_ key: String) -> String? {
            guard let index = arguments.firstIndex(of: key), index + 1 < arguments.count else { return nil }
            return arguments[index + 1]
        }
        view.tintColor = ReferencePalette.uiTint
        background.isUserInteractionEnabled = false
        background.isOpaque = true
        view.addSubview(background)
        heading.text = "iOS 26.5 · Native Glass Reference"
        heading.font = .systemFont(ofSize: 16, weight: .semibold)
        heading.accessibilityIdentifier = "reference-title"
        view.addSubview(heading)
        backgroundPicker.accessibilityIdentifier = "background-selector"
        backgroundPicker.addTarget(self, action: #selector(changeBackground), for: .valueChanged)
        view.addSubview(backgroundPicker)
        scenePicker.accessibilityIdentifier = "scene-selector"
        scenePicker.addTarget(self, action: #selector(changeScene), for: .valueChanged)
        view.addSubview(scenePicker)
        tabBar.items = [
            UITabBarItem(title: "Home", image: UIImage(systemName: "house"), tag: 0),
            UITabBarItem(title: "Explore", image: UIImage(systemName: "magnifyingglass"), tag: 1),
            UITabBarItem(title: "Settings", image: UIImage(systemName: "gearshape"), tag: 2)
        ]
        if argument("-ReferenceTabCount") == "4" {
            tabBar.items = [
                UITabBarItem(title: "Home", image: UIImage(systemName: "house"), tag: 0),
                UITabBarItem(title: "Courses", image: UIImage(systemName: "calendar"), tag: 1),
                UITabBarItem(title: "Explore", image: UIImage(systemName: "magnifyingglass"), tag: 2),
                UITabBarItem(title: "Settings", image: UIImage(systemName: "gearshape"), tag: 3)
            ]
        }
        tabBar.selectedItem = tabBar.items?.first
        tabBar.delegate = self
        tabBar.accessibilityIdentifier = "uikit-tabbar"
        ReferenceDockPublicSnapshot.record(tabBar, phase: "before", parent: "standalone")
        // Public appearance tint preserves the system-owned material and lens.
        let appearance = tabBar.standardAppearance.copy() as! UITabBarAppearance
        appearance.selectionIndicatorTintColor = ReferencePalette.uiTint
        tabBar.standardAppearance = appearance
        tabBar.scrollEdgeAppearance = appearance
        tabBar.tintColor = .white
        view.addSubview(tabBar)

        scene = ReferenceScene(rawValue: argument("-ReferenceScene") ?? "toggle") ?? .toggle
        background.background = ReferenceBackground(rawValue: argument("-ReferenceBackground") ?? "grid") ?? .grid
        backgroundName = background.background.rawValue
        backgroundPicker.selectedSegmentIndex = backgroundName == "grid" ? 0 : (backgroundName == "stripes" ? 1 : UISegmentedControl.noSegment)
        scenePicker.selectedSegmentIndex = ReferenceScene.allCases.firstIndex(of: scene) ?? 0
        installScene()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !recordingStarted, let window = view.window else { return }
        recordingStarted = true
        ReferenceTrace.shared.beginSession(window: window)
        ReferenceDockPublicSnapshot.record(tabBar, phase: "attached", parent: "standalone")
        ReferenceTrace.shared.add(["event": "reference-configuration", "scene": scene.rawValue,
                                   "background": background.background.metadata,
                                   "interfaceStyle": window.traitCollection.userInterfaceStyle.rawValue,
                                   "staticOptics": ProcessInfo.processInfo.arguments.contains("-ReferenceStaticOptics")])
        recorder.context = { [weak self] in
            ["scene": self?.scene.rawValue ?? "", "background": self?.backgroundName ?? "",
             "tabCount": self?.tabBar.items?.count ?? 0]
        }
        if ProcessInfo.processInfo.arguments.contains("-ReferenceStaticOptics") {
            let format = UIGraphicsImageRendererFormat()
            format.scale = window.screen.scale
            format.opaque = true
            format.preferredRange = .standard
            let input = UIGraphicsImageRenderer(bounds: background.bounds, format: format).image { _ in
                background.draw(background.bounds)
            }
            try? input.pngData()?.write(to: ReferenceTrace.shared.directory.appendingPathComponent("background-input.png"))
            if background.background == .synthetic_landscape {
                try? ReferenceBackground.landscape.pngData()?.write(
                    to: ReferenceTrace.shared.directory.appendingPathComponent("landscape-source-720x1280.png"))
            }
        } else { recorder.start(window: window) }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        background.frame = view.bounds
        let width = min(420, max(0, view.bounds.width - 40))
        let left = (view.bounds.width - width) / 2
        let top = view.safeAreaInsets.top
        heading.frame = CGRect(x: left, y: top + 10, width: width, height: 24)
        backgroundPicker.frame = CGRect(x: left, y: top + 44, width: width, height: backgroundPicker.intrinsicContentSize.height)
        scenePicker.frame = CGRect(x: left, y: top + 88, width: width, height: scenePicker.intrinsicContentSize.height)
        // UITabBar owns its fitting height. Reassigning 70pt every layout made its
        // platter 13pt too short and overlapped the unchanged icon/title content.
        let proposedTabSize = CGSize(width: width + 16, height: view.bounds.height)
        let fittingTabSize = tabBar.sizeThatFits(proposedTabSize)
        let tabHeight = fittingTabSize.height
        let tabY = view.bounds.height - view.safeAreaInsets.bottom - tabHeight - 12
        tabBar.frame = CGRect(x: max(0, left - 8), y: tabY, width: proposedTabSize.width, height: tabHeight)
        let layout = [tabBar.frame.minX, tabY, tabBar.frame.width, tabHeight,
                      view.safeAreaInsets.top, view.safeAreaInsets.bottom]
        if layout != recordedTabLayout {
            recordedTabLayout = layout
            ReferenceTrace.shared.add([
                "event": "tab-layout", "policy": "public UITabBar.sizeThatFits; 12pt outer bottom gap",
                "proposedWidth": proposedTabSize.width, "proposedHeight": proposedTabSize.height,
                "fittingWidth": fittingTabSize.width, "fittingHeight": fittingTabSize.height,
                "frameX": tabBar.frame.minX, "frameY": tabY,
                "frameWidth": tabBar.frame.width, "frameHeight": tabHeight,
                "safeAreaTop": view.safeAreaInsets.top, "safeAreaBottom": view.safeAreaInsets.bottom,
                "horizontalSizeClass": traitCollection.horizontalSizeClass.rawValue,
                "verticalSizeClass": traitCollection.verticalSizeClass.rawValue,
                "preferredContentSizeCategory": traitCollection.preferredContentSizeCategory.rawValue,
                "tabCount": tabBar.items?.count ?? 0,
            ])
        }
        host?.view.frame = CGRect(x: left, y: top + 146, width: width,
                                 height: max(120, tabY - top - 160))
    }

    @objc private func changeBackground() {
        backgroundName = backgroundPicker.selectedSegmentIndex == 1 ? "stripes" : "grid"
        background.background = backgroundName == "stripes" ? .stripes : .grid
        ReferenceTrace.shared.add(["event": "background", "value": backgroundName])
    }

    @objc private func changeScene() {
        guard ReferenceScene.allCases.indices.contains(scenePicker.selectedSegmentIndex) else { return }
        scene = ReferenceScene.allCases[scenePicker.selectedSegmentIndex]
        installScene()
    }

    private func installScene() {
        if let host {
            host.willMove(toParent: nil)
            host.view.removeFromSuperview()
            host.removeFromParent()
        }
        let controller = UIHostingController(rootView: NativeStage(scene: scene))
        controller.view.backgroundColor = .clear
        controller.view.accessibilityIdentifier = "reference-stage"
        addChild(controller)
        view.insertSubview(controller.view, belowSubview: tabBar)
        controller.didMove(toParent: self)
        host = controller
        view.setNeedsLayout()
        ReferenceTrace.shared.add(["event": "scene", "value": scene.rawValue])
    }

    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        ReferenceTrace.shared.add(["event": "value", "control": "uikit-tabbar", "value": item.tag])
    }
}

final class ReferenceAppDelegate: NSObject, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        let arguments = ProcessInfo.processInfo.arguments
        let styleIndex = arguments.firstIndex(of: "-ReferenceInterfaceStyle")
        let dark = styleIndex.map { $0 + 1 < arguments.count && arguments[$0 + 1] == "dark" } ?? false
        window.overrideUserInterfaceStyle = dark ? .dark : .light
        let parentIndex = arguments.firstIndex(of: "-ReferenceDockParent")
        let swiftUITabView = parentIndex.map { $0 + 1 < arguments.count && arguments[$0 + 1] == "swiftui-tabview" } ?? false
        if swiftUITabView {
            let backgroundIndex = arguments.firstIndex(of: "-ReferenceBackground")
            let backgroundName = backgroundIndex.flatMap { $0 + 1 < arguments.count ? arguments[$0 + 1] : nil } ?? "grid"
            let contentIndex = arguments.firstIndex(of: "-ReferenceDockContent")
            let contentName = contentIndex.flatMap { $0 + 1 < arguments.count ? arguments[$0 + 1] : nil } ?? "placeholder"
            let parent = ReferenceDockNavigationParent(background: ReferenceBackground(rawValue: backgroundName) ?? .grid,
                                                       content: ReferenceDockContent(rawValue: contentName) ?? .placeholder)
            window.rootViewController = UIHostingController(rootView: parent.ignoresSafeArea(.container))
        } else { window.rootViewController = ReferenceViewController() }
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        ReferenceTrace.shared.flush()
    }

    func applicationWillTerminate(_ application: UIApplication) {
        ReferenceTrace.shared.flush()
    }
}

UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv,
                  NSStringFromClass(ReferenceApplication.self), NSStringFromClass(ReferenceAppDelegate.self))
