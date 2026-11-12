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
        window.rootViewController = ReferenceViewController()
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
