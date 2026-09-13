[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.samsung.com/samsung-dex/how-it-works.html (+ .../overview.html / modify-optimizing.html / testing.html / one-ui/overview.html · developer.samsung.com Samsung DeX 官方原文)

# 三星 Samsung DeX / 大屏适配官方规范

> DeX 文档树 4 页: Overview / How Samsung DeX works / Modifying your app (Optimizing) / App testing guide。全部为 developer.samsung.com 原文 (evidence=A)。

## 一、定性: 无私有启动 API (how-it-works.html 原文)

- **Samsung DeX 是 Android N Multi-Window 模式的扩展** ("Samsung DeX is an extension of Android N's Multi-Window mode"), 三星加入的额外代码增加特性并整合三星硬件。
- **无需任何三星私有 API**: "There are no proprietary Samsung APIs needed to launch apps in Samsung DeX, as it is enabled by default." — DeX 默认启用, 应用遵循 Android 最佳实践即可运行。
- DeX 模式形态: DeX Mode (投 PC 体验到大屏, 同一套手机 App); 支持显示器/电视/PC (Windows/macOS); 平板上 Quick panel 一键切 DeX。

## 二、窗口类型 (Window types, how-it-works.html)

应用在 DeX 中启动时采用 3 种窗口类型之一 (原文分类):

| 类型 | 条件 | 表现 |
|---|---|---|
| Resizable Window | 符合 Android N Multi-Window 标准 | Free-Form 多窗口, 可按需 resize |
| Fixed-size Window | 不符合 Android N Multi-Window 标准 | 固定手机尺寸, 不可 resize |
| Not supported | **仅 widget 基 (widget based only) 的应用**、或 DeX 中不实用的应用 (如 Car mode); 需手指触控且限制鼠标使用的应用 | 在 DeX 中不工作 |

- Sleepy 关联: Sleepy 是普通 Activity 应用 (非 widget-only), 属 Resizable Window 通道; 前提 = manifest 声明 Multi-Window 支持。

## 三、App 优化要求 (modify-optimizing.html 原文)

官方列举 DeX 适配四要素 (很少的特殊实现要求):
1. Enabling Multi-Window support
2. Enabling keyboard and mouse support
3. Handling runtime configuration changes
4. Implementing a responsive UI

### 3.1 Multi-Window 支持
- `<application android:resizeableActivity="true">` — 不声明则打开为 fixed-size window。

### 3.2 键盘鼠标
- **禁止显式声明触屏独占**: `<uses-configuration android:reqTouchScreen="finger" />` 或 `<uses-feature android:name="android.hardware.touchscreen" android:required="true" />` 会导致应用**不在 Desktop mode 启动**并弹提示。

### 3.3 运行时配置变化 (DeX 8 项, 原文清单)
切换手机 ↔ DeX 引发的 config change (类似横竖屏):
1. Density change (xxxhdpi ↔ mdpi)
2. Resolution change (WQHD ↔ FHD)
3. Orientation change (portrait ↔ landscape)
4. Screen layout change
5. Screen size change
6. Smallest screen size change
7. UI mode change (mobile ↔ desktop)
8. Color mode change (*仅 DeX Dual Mode 需要)

- 资源限定符: UI mode `desk` / Screen layout `xlarge` / Density `mdpi` / Resolution FHD (SEP v9.0 起 HD+/WQHD)。
- keepalive meta-data (防切换时进程被杀): `< v3.0` 用 `com.samsung.android.keepalive.density`; `≥ v3.0` 用 `com.samsung.android.multidisplay.keep_process_alive`; 配合 `android:configChanges="orientation|screenSize|smallestScreenSize|density|screenLayout|uiMode|keyboard|keyboardHidden|navigation"`。

### 3.4 DeX 检测 (反射私有字段, 官方原文代码)
- `Configuration.SEM_DESKTOP_MODE_ENABLED` / `semDesktopModeEnabled` 反射比较判断 DeX 模式; Dual Mode 用 `desktopmode` system service 的 `getDesktopModeState` (isEnabled / isDualMode / isStandaloneMode 三态)。
- 广播: `android.app.action.ENTER_KNOX_DESKTOP_MODE` / `EXIT_KNOX_DESKTOP_MODE`, extra `DISPLAY_TYPE` (STANDALONE=101, DUAL=102)。

## 四、DeX 分辨率与 DPI (how-it-works.html)

| 阶段 | Resolution | DPI | Orientation | Screen size |
|---|---|---|---|---|
| SEP v8.x (Phone Mode) | WQHD | 640dpi (xxhdpi) | Portrait | - |
| SEP v9.x | FHD, HD+, WQHD | 160 dpi (mdpi) | Landscape | xLarge |

- 外接显示器: FHD (16:9) 起, HDMI; SEP v9.0 支持 1920x1080 / 1600x900 / 2560x1440。
- OS 要求: Android N (API 24) 以上。
- Wireless DeX: SEP 11.5 (OneUI 2.5) 起; DeX for PC: SEP 10.0 起; Wireless DeX for PC: SEP 12.5 (OneUI 3.1) 起。
- Sleepy 关联: DeX 模式下 DPI 从 xxhdpi 降到 mdpi + 分辨率横置 — widget 若被摆上 DeX 桌面 (DeX 桌面支持 widget), cell dp 随 density 变化, `OPTION_APPWIDGET_SIZES` 读真实 dp 的机制覆盖。

## 五、大屏 / 平板上的 widget 行为 (One UI 大屏规范交叉引用)

- One UI 大屏规范 (largescreen-and-foldable) 明示 One UI 为「外接显示器 (Samsung DeX)、单屏分屏视图」提供多套尺寸 — widget cell 在 DeX 桌面/大屏上有独立网格, minWidth/minHeight 只是占格下界。
- 大屏多任务: 最多 3 分屏 + 5 pop-up; widget 不参与分屏, 但 widget 所在宿主 (DeX 桌面) 的网格密度与手机不同。
- One UI 9 消费者文档 (samsung.com.cn/one-ui): 平板/折叠屏外屏均可拖动、新增、删除小组件; 大屏 widget 行为 = 网格自适应, 无额外厂商协议。

## 六、调试 (testing.html 原文)

- **DeX station 内不能 USB 调试** (插拔会退出 DeX 模式); 改用以太网或 Wi-Fi ADB。
- 模拟器复现 DeX 环境: AVD (Nexus 6P + Nougat) + `settings put global enable_freeform_support 1` + freeform 权限绑定 + `wm density 160; wm size 1080x1920` (即 mdpi/FHD, DeX Desktop Mode 同参数)。
- 恢复: `wm density reset; wm size reset`。

## 对 Sleepy 的落点 (仅事实归纳)

1. DeX 无私有启动 API, 默认启用; Sleepy 无需 DeX 专属代码。
2. 硬约束: 不声明 `reqTouchScreen` / 不强制 touchscreen feature, 否则 DeX 不启动 — Sleepy manifest 需保持不声明 (如有声明需检查)。
3. `android:resizeableActivity="true"` 决定 resizable window vs fixed-size window — 普通应用建议声明。
4. widget-only 应用在 DeX "Not supported" — 不影响 Sleepy (Sleepy 是应用 + widget, 非 widget-only)。
5. DeX 桌面 widget cell 随 density (mdpi) 与分辨率 (FHD 横置) 变化, 与「渲染按真实 dp」公共层规则同构, 无专属动作。
