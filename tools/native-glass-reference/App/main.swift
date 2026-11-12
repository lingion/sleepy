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
    case toggle, segmented, slider, buttons, presentations
    var label: String {
        switch self {
        case .toggle: return "Toggle"
        case .segmented: return "Select"
        case .slider: return "Slider"
        case .buttons: return "Button"
        case .presentations: return "Present"
        }
    }
}

final class ReferenceBackgroundView: UIView {
    var stripes = false { didSet { setNeedsDisplay() } }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        UIColor(red: 0.94, green: 0.94, blue: 0.96, alpha: 1).setFill()
        context.fill(bounds)
        if stripes {
            let colors = ReferencePalette.stripeColors
            for index in 0...Int(ceil(bounds.width / 28)) {
                colors[index % colors.count].withAlphaComponent(0.52).setFill()
                context.fill(CGRect(x: CGFloat(index) * 28, y: 0, width: 28, height: bounds.height))
            }
        } else {
            context.setStrokeColor(UIColor.black.withAlphaComponent(0.13).cgColor)
            context.setLineWidth(0.5)
            for x in stride(from: CGFloat(0), through: bounds.width, by: 20) {
                context.move(to: CGPoint(x: x, y: 0)); context.addLine(to: CGPoint(x: x, y: bounds.height))
            }
            for y in stride(from: CGFloat(0), through: bounds.height, by: 20) {
                context.move(to: CGPoint(x: 0, y: y)); context.addLine(to: CGPoint(x: bounds.width, y: y))
            }
            context.strokePath()
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

    override func viewDidLoad() {
        super.viewDidLoad()
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

        let arguments = ProcessInfo.processInfo.arguments
        func argument(_ key: String) -> String? {
            guard let index = arguments.firstIndex(of: key), index + 1 < arguments.count else { return nil }
            return arguments[index + 1]
        }
        scene = ReferenceScene(rawValue: argument("-ReferenceScene") ?? "toggle") ?? .toggle
        backgroundName = argument("-ReferenceBackground") == "stripes" ? "stripes" : "grid"
        backgroundPicker.selectedSegmentIndex = backgroundName == "stripes" ? 1 : 0
        background.stripes = backgroundName == "stripes"
        scenePicker.selectedSegmentIndex = ReferenceScene.allCases.firstIndex(of: scene) ?? 0
        installScene()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !recordingStarted, let window = view.window else { return }
        recordingStarted = true
        ReferenceTrace.shared.beginSession(window: window)
        recorder.context = { [weak self] in
            ["scene": self?.scene.rawValue ?? "", "background": self?.backgroundName ?? ""]
        }
        recorder.start(window: window)
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
        let tabY = view.bounds.height - view.safeAreaInsets.bottom - 82
        tabBar.frame = CGRect(x: max(0, left - 8), y: tabY, width: width + 16, height: 70)
        host?.view.frame = CGRect(x: left, y: top + 146, width: width,
                                 height: max(120, tabY - top - 160))
    }

    @objc private func changeBackground() {
        backgroundName = backgroundPicker.selectedSegmentIndex == 1 ? "stripes" : "grid"
        background.stripes = backgroundName == "stripes"
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
        window.overrideUserInterfaceStyle = .light
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
