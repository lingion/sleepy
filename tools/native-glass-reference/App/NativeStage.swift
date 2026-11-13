import SwiftUI
import UIKit

struct NativeStage: View {
    let scene: ReferenceScene
    @State private var swiftUIOn = false
    @State private var uiKitOn = false
    @State private var swiftUISelection = 0
    @State private var uiKitSelection = 0
    @State private var swiftUISlider = 0.25
    @State private var uiKitSlider: Float = 0.25
    @State private var showSheet = false
    @State private var showAlert = false
    @State private var buttonCount = 0
    @State private var language = "zh-CN"
    private let languages = [
        ("zh-CN", "简体中文"), ("zh-TW", "繁體中文"), ("en", "English"),
        ("ja", "日本語"), ("es", "Español")
    ]

    init(scene: ReferenceScene) {
        self.scene = scene
        let arguments = ProcessInfo.processInfo.arguments
        let supplied = arguments.firstIndex(of: "-ReferenceInitialSlider").flatMap { index in
            index + 1 < arguments.count ? Double(arguments[index + 1]) : nil
        }
        let initial = supplied.flatMap { $0.isFinite && (0...1).contains($0) ? $0 : nil } ?? 0.25
        _swiftUISlider = State(initialValue: initial)
        _uiKitSlider = State(initialValue: Float(initial))
    }

    var body: some View {
        Group {
            switch scene {
            case .toggle:
                VStack(spacing: 38) {
                    HStack {
                        Text("SwiftUI Toggle")
                        Spacer()
                        Toggle("SwiftUI Toggle", isOn: $swiftUIOn)
                            .toggleStyle(.switch).labelsHidden()
                            .accessibilityIdentifier("swiftui-toggle")
                    }
                    HStack {
                        Text("UIKit UISwitch")
                        Spacer()
                        UIKitSwitch(value: $uiKitOn)
                            .fixedSize()
                    }
                }
            case .segmented:
                VStack(alignment: .leading, spacing: 30) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("SwiftUI Picker (.segmented)")
                        Picker("SwiftUI Picker", selection: $swiftUISelection) {
                            Text("First").tag(0)
                            Text("Second").tag(1)
                            Text("Third").tag(2)
                        }
                        .pickerStyle(.segmented)
                        .accessibilityIdentifier("swiftui-segmented")
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("UIKit UISegmentedControl")
                        UIKitSegments(selection: $uiKitSelection)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            case .slider:
                VStack(alignment: .leading, spacing: 38) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("UIKit UISlider")
                        UIKitSlider(value: $uiKitSlider)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("SwiftUI Slider")
                        Slider(value: $swiftUISlider, in: 0...1)
                            .accessibilityIdentifier("swiftui-slider")
                    }
                }
            case .buttons:
                VStack(spacing: 24) {
                    Button("Glass") { didPress("glass-button") }
                        .buttonStyle(GlassButtonStyle())
                        .accessibilityIdentifier("glass-button")
                    Button("Glass prominent") { didPress("glass-prominent-button") }
                        .buttonStyle(GlassProminentButtonStyle())
                        .accessibilityIdentifier("glass-prominent-button")
                    Button { didPress("glass-circle-button") } label: {
                        Image(systemName: "plus")
                    }
                    .buttonStyle(GlassButtonStyle())
                    .buttonBorderShape(.circle)
                    .accessibilityLabel("Add")
                    .accessibilityIdentifier("glass-circle-button")
                    Text("Activations: \(buttonCount)")
                        .font(.system(size: 12, design: .monospaced))
                        .accessibilityIdentifier("activation-count")
                }
            case .presentations:
                VStack(spacing: 26) {
                    Button("System sheet") {
                        ReferenceTrace.shared.add(["event": "presentation", "kind": "sheet", "phase": "requested"])
                        showSheet = true
                    }
                    .buttonStyle(GlassButtonStyle())
                    .accessibilityIdentifier("show-sheet")
                    Button("System alert") {
                        ReferenceTrace.shared.add(["event": "presentation", "kind": "alert", "phase": "requested"])
                        showAlert = true
                    }
                    .buttonStyle(GlassButtonStyle())
                    .accessibilityIdentifier("show-alert")
                    Menu {
                        Button("First action") { menuAction("first") }
                        Button("Second action") { menuAction("second") }
                        Button("Delete", role: .destructive) { menuAction("delete") }
                    } label: { Text("System menu") }
                    .buttonStyle(GlassButtonStyle())
                    .accessibilityIdentifier("show-menu")
                }
            case .language:
                VStack(alignment: .leading, spacing: 16) {
                    nativeLanguageMenu
                    Text("Selected: \(languageLabel)")
                        .font(.system(size: 12, design: .monospaced))
                        .accessibilityIdentifier("language-selection")
                    Spacer()
                }
            }
        }
        .font(.system(size: 16))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .tint(ReferencePalette.tint)
        .onChange(of: swiftUIOn) { _, value in valueChanged("swiftui-toggle", value) }
        .onChange(of: swiftUISelection) { _, value in valueChanged("swiftui-segmented", value) }
        .onChange(of: swiftUISlider) { _, value in valueChanged("swiftui-slider", value) }
        .sheet(isPresented: $showSheet, onDismiss: {
            ReferenceTrace.shared.add(["event": "presentation", "kind": "sheet", "phase": "dismissed"])
        }) {
            NativeReferenceSheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .alert("Native Alert", isPresented: $showAlert) {
            Button("Cancel", role: .cancel) {
                ReferenceTrace.shared.add(["event": "presentation", "kind": "alert", "phase": "cancelled"])
            }
            Button("Confirm") {
                ReferenceTrace.shared.add(["event": "presentation", "kind": "alert", "phase": "confirmed"])
            }
        } message: {
            Text("System-provided alert, controls, material, and transition.")
        }
    }

    private var languageLabel: String { languages.first { $0.0 == language }?.1 ?? language }

    // Same native composition and update scheduling as Sleepy's LanguageSection.
    // The reference does not rebuild an app locale or replace system menu transitions.
    private var nativeLanguageMenu: some View {
        Menu {
            Picker("语言", selection: Binding(
                get: { language },
                set: { code in
                    ReferenceTrace.shared.add(["event": "language-selection", "phase": "binding-setter",
                                               "previous": language, "value": code])
                    guard code != language else { return }
                    DispatchQueue.main.async {
                        language = code
                        ReferenceTrace.shared.add(["event": "language-selection", "phase": "async-state-commit",
                                                   "value": code])
                    }
                }
            )) {
                ForEach(languages, id: \.0) { code, label in Text(label).tag(code) }
            }
            .pickerStyle(.inline)
        } label: {
            HStack(spacing: 12) {
                Text("语言").font(.system(size: 14, weight: .medium))
                    .foregroundColor(Color(uiColor: .label))
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    Text(languageLabel).font(.system(size: 16)).lineLimit(1)
                    Image(systemName: "chevron.down").font(.system(size: 12))
                }
                .foregroundColor(Color(uiColor: .secondaryLabel))
                .frame(maxWidth: 180, alignment: .trailing)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .contentShape(Rectangle())
        }
        .menuStyle(.borderlessButton)
        .buttonStyle(.plain)
        .accessibilityIdentifier("language-menu")
        .accessibilityValue(languageLabel)
    }

    private func valueChanged(_ control: String, _ value: Any) {
        ReferenceTrace.shared.add(["event": "value", "control": control, "value": value])
    }

    private func didPress(_ control: String) {
        buttonCount += 1
        ReferenceTrace.shared.add(["event": "activation", "control": control, "count": buttonCount])
    }

    private func menuAction(_ value: String) {
        ReferenceTrace.shared.add(["event": "menu", "value": value])
    }
}

private struct NativeReferenceSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var isOn = false
    @State private var value = 0.4

    var body: some View {
        VStack(spacing: 24) {
            Text("Native Sheet")
                .font(.title2)
                .accessibilityIdentifier("sheet-title")
            Text("System medium / large detents")
            Toggle("Native switch", isOn: $isOn).toggleStyle(.switch)
            Slider(value: $value, in: 0...1)
            Button("Done") { dismiss() }
                .buttonStyle(GlassButtonStyle())
                .accessibilityIdentifier("dismiss-sheet")
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .tint(ReferencePalette.tint)
        .onAppear {
            ReferenceTrace.shared.add(["event": "presentation", "kind": "sheet", "phase": "content-appeared"])
        }
    }
}

private struct UIKitSwitch: UIViewRepresentable {
    @Binding var value: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UISwitch {
        let control = UISwitch()
        control.onTintColor = ReferencePalette.uiTint
        control.accessibilityIdentifier = "uikit-toggle"
        control.accessibilityLabel = "UIKit UISwitch"
        control.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .valueChanged)
        return control
    }

    func updateUIView(_ control: UISwitch, context: Context) {
        context.coordinator.parent = self
        if control.isOn != value { control.setOn(value, animated: false) }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UISwitch, context: Context) -> CGSize? {
        uiView.intrinsicContentSize
    }

    final class Coordinator: NSObject {
        var parent: UIKitSwitch
        init(_ parent: UIKitSwitch) { self.parent = parent }
        @objc func changed(_ control: UISwitch) {
            parent.value = control.isOn
            ReferenceTrace.shared.add(["event": "value", "control": "uikit-toggle", "value": control.isOn])
        }
    }
}

private struct UIKitSegments: UIViewRepresentable {
    @Binding var selection: Int

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UISegmentedControl {
        let control = UISegmentedControl(items: ["First", "Second", "Third"])
        control.selectedSegmentTintColor = ReferencePalette.uiTint
        control.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
        control.accessibilityIdentifier = "uikit-segmented"
        control.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .valueChanged)
        return control
    }

    func updateUIView(_ control: UISegmentedControl, context: Context) {
        context.coordinator.parent = self
        if control.selectedSegmentIndex != selection { control.selectedSegmentIndex = selection }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UISegmentedControl, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 335, height: uiView.intrinsicContentSize.height)
    }

    final class Coordinator: NSObject {
        var parent: UIKitSegments
        init(_ parent: UIKitSegments) { self.parent = parent }
        @objc func changed(_ control: UISegmentedControl) {
            parent.selection = control.selectedSegmentIndex
            ReferenceTrace.shared.add(["event": "value", "control": "uikit-segmented", "value": control.selectedSegmentIndex])
        }
    }
}

private struct UIKitSlider: UIViewRepresentable {
    @Binding var value: Float

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UISlider {
        let control = UISlider()
        control.minimumValue = 0
        control.maximumValue = 1
        control.tintColor = ReferencePalette.uiTint
        control.accessibilityIdentifier = "uikit-slider"
        control.accessibilityLabel = "UIKit UISlider"
        control.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .valueChanged)
        return control
    }

    func updateUIView(_ control: UISlider, context: Context) {
        context.coordinator.parent = self
        if !control.isTracking, abs(control.value - value) > 0.000_01 { control.value = value }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UISlider, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 335, height: uiView.intrinsicContentSize.height)
    }

    final class Coordinator: NSObject {
        var parent: UIKitSlider
        init(_ parent: UIKitSlider) { self.parent = parent }
        @objc func changed(_ control: UISlider) {
            parent.value = control.value
            ReferenceTrace.shared.add(["event": "value", "control": "uikit-slider", "value": control.value])
        }
    }
}
