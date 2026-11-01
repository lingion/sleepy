# Sleepy v1.0.63

> Liquid Glass navigation bar engine with physical lens stretch, drag-to-paging schedule rearrangement, ghost slider time editing, MD3 Expressive motion system, multi-step undo history, Kingo parser fixes with Jiamusi University promotion, and an expanded national school catalog.

## What's New

### Bit-exact Liquid Glass navigation bar engine with physical lens stretch

The navigation bar and bottom sheet surfaces now feature a bit-exact superellipse curve, dynamic refraction shader, and a 1.25f physical lens stretch modeled after FLClash's Liquid Glass implementation. The outline calculation uses a compiled -O2 FMA golden algorithm, while the rendering layer evaluates lens distortion and translucent surface blur in real time with cached path geometry, seamlessly coexisting with Material Theme surface tinting.

Evidence: PR #266, PR #272 (contributed by @Kerry1020).

### Drag-to-paging timetable rearrangement

Dragging a course capsule near the left or right edge of the screen now triggers continuous week paging every 500 ms. The viewport advances smoothly across semester weeks, with magnetic landing point snapping and boundary clamps preventing drops outside valid academic weeks.

Evidence: PR #260.

### Ghost slider across course time editing

When editing class section intervals or time ranges, dragging a thumb away from its initial interval reveals a ghost indicator marking the original start and end boundaries. This ghost reference pattern is deployed across 9 time-editing touchpoints throughout the app, including single session adjustments and custom period settings.

Evidence: PR #253, PR #261.

### Material 3 Expressive motion system

All primary screen transitions, bottom sheets, dialogs, and timetable interactions now follow Material 3 Expressive motion specifications. Transitions utilize shared axis and container transform primitives driven by physical spring curves, replacing legacy linear interpolations with responsive, interruptible motion.

Evidence: PR #255.

### Multi-step undo and redo history

Schedule modifications—including adding, deleting, editing courses, and timetable-level adjustments—are recorded on an in-memory history stack. Users can step backwards and forwards through recent edits. The maximum undo depth can be configured or disabled in the Experimental Lab settings (default depth: 5 steps).

Evidence: PR #240.

### Single session course adjustment and quick capsule add

Courses can now be adjusted for a specific session date without modifying the parent recurring course rule. In addition, tapping on an empty slot in the schedule grid immediately presents a quick add capsule pre-filled with the selected day and period.

Evidence: PR #225 (contributed by @jim139129).

### Academic catalog, online token import, and Kingo parser expansion

- Promoted Jiamusi University (`moe:4123010222`) to officially supported status following Kingo parser fixes (implemented configurations: 351 to 352 schools).
- Fixed Kingo (`kingo_new`) schedule weekday column shifting on `#mytable` tables caused by leading period guide columns, preventing Saturday and Sunday courses from being dropped.
- Added fault-tolerant parsing in Kingo for courses without room locations (such as PE classes or lectures) via dynamic timing row matching.
- Integrated WakeUP 6.6.2 catalog entries, promoting 43 institutions to operational status with verified route configurations.
- Added WakeUP v6.1.70 share token online import pathway, resolving course schedules directly from shared token strings.
- Absorbed 264 Shiguang academic entries into the national catalog via the generic sg_asset pipeline with 282 vendor scripts.
- Adapted Nankai University classic_eams Type B gateway with live-verified parsing rules.
- Adapted Ningbo City College (NBCC) webvpn gateway with user-agent routing fixes.
- Supported Chinese full-width parentheses `（单）` / `（双）` in week range compatibility regex.

Evidence: PR #272 (contributed by @Kerry1020), PR #254, PR #248, PR #251, PR #247, PR #243, PR #232.

### Android 13+ themed dynamic icons and modern geometry

The launcher icon now includes a monochrome vector layer for full compatibility with Android 13+ Material You dynamic theming. The core symbol geometry has been adjusted to a 55% x 60% centered proportion matching modern squircle standards.

Evidence: PR #238, PR #237.

### Section period header alignment options

The timetable header now supports custom alignment configurations for period start and end timestamps, accommodating varied timetable schedules and compact column layouts.

Evidence: PR #259 (contributed by @jim139129).

### Tablet portrait single-pane fallback

When a tablet or foldable device is oriented in portrait mode, the interface automatically falls back to the phone-optimized single-pane layout. Dual-pane layout is gated by landscape geometric aspect ratio checks.

Evidence: PR #235.

### Agent Debug Console

A local loopback semantic debug console is integrated for automated diagnostic inspection and coding agent interaction, exposing read-only schedule topology, preferences, and state trees over a secured localhost port.

Evidence: PR #265, PR #267 (contributed by @Kerry1020).

## Fixes

- Fixed school selection back navigation routing: pressing back in the school picker no longer prematurely drops the user onto timetable management, preserving the add table flow until import succeeds. Evidence: PR #272 (contributed by @Kerry1020).
- Removed protocol unverified label suffixes ("（未实测）") from school listings, unifying confirmation warnings into the click-to-import dialog. Evidence: PR #272 (contributed by @Kerry1020).
- Removed unverified OriginOS atomic widget declarations (`vivo_widget`), restoring desktop widgets to the standard Android AppWidget provider flow so widgets can be freely placed and managed on OriginOS. Evidence: PR #234.
- Repaired Vivo origin island expansion card protocol compliance and isolated reminder sessions to eliminate crosstalk. Evidence: PR #226.
- Stripped trailing carriage returns in schedule HTML parser and normalized Shiguang asset hashes across line endings. Evidence: PR #257 (contributed by @jim139129).
- Cleaned up duplicate fixtures across academic parser test suites. Evidence: PR #263 (contributed by @jim139129).
- Resolved localized string typos and comment formatting across supported languages. Evidence: PR #262 (contributed by @jim139129).
- Equalized desktop widget gallery card row heights and centered preview bitmaps. Evidence: PR #231, PR #245.
- Replaced course editor start and end number inputs with Material 3 dual-thumb sliders dynamically bounded by timetable maxWeek. Evidence: PR #244.
- Updated overview preview banner graphics and website documentation links. Evidence: PR #239, PR #242 (contributed by @Kerry1020), PR #268, PR #269, PR #270, PR #271.

## Known Limitations

- Shiguang catalog imports: 264 newly added Shiguang catalog entries are marked as unverified and will undergo progressive live network testing.
- Agent Debug Console: Intended strictly for local development and diagnostic automation; the console listener only binds to loopback interfaces.

## Verification

- Scope: v1.0.62..e4f38f45 (= origin/main, 42 commits).
- Unit tests: `:app:testDebugUnitTest` executed 3,543+ tests, 0 failures, 100% successful.
- Build target: versionName 1.0.63 / versionCode 10063 derived dynamically from git tag upon release build.
- CI pipeline: Release APK compilation, R8 optimization, asset signing, and sha256sums generation are executed automatically via GitHub Actions upon tag push.
- Contributors this release:
  - @Kerry1020: FLClash bit-exact liquid glass navigation bar engine & 1.25f lens stretch (PR #266, PR #272), Kingo parser fixes & Jiamusi University promotion (PR #272), school picker back route fix (PR #272), loopback console CI & test memory optimization (PR #267), website link & header cleanup (PR #239, PR #242).
  - @jim139129: Single session course adjustments and quick add capsule (PR #225), section period header alignment options (PR #259), parser fixture deduplication & typo fixes (PR #263, PR #262), HTML trailing CR parser fix (PR #257), Fluid Cloud documentation (PR #256), vendor documentation maintenance (PR #229).

---

# Sleepy v1.0.63

> 液态玻璃底栏引擎与物理透镜拉伸、拖课压周缘连翻页、幽灵滑杆时间编辑、Material 3 表现行动效系统、多步撤回历史栈、新青果教务解析修复与佳木斯大学转正，以及全量扩充的全国高校教务目录。

## 新增功能

### 液态玻璃底栏超椭圆路径复刻与物理透镜拉伸

底栏与底部弹层现已集成基于 FLClash 液态玻璃算法的超椭圆曲率、1.25f 物理透镜拉伸与实时折射着色器。轮廓计算采用 -O2 FMA golden 算法实现点对点还原，渲染层结合缓存路径几何，在保持流畅帧率的前提下动态计算透镜折射与半透明表面模糊，并与 Material Theme 动态主题色无缝共存。

依据: PR #266、PR #272（贡献者 @Kerry1020）。

### 拖课浮起压周缘连翻页

在课程表内长按拖拽课程胶囊接近屏幕左侧或右侧边缘时，系统每满 500 毫秒自动向上一周或向下一周连续翻页。视图在学期各周之间平滑切换，释放时具备磁吸落点判断与超出周次边界钳制。

依据: PR #260。

### 幽灵滑杆贯穿课程时间编辑

在课程编辑或单次时间调整中，拖动滑杆滑块离开初始区间时，界面会同步保留半透明的幽灵指示条标明原始时段范围。该幽灵参考设计已完整铺设至全应用 9 处时间微调触点，包含单次课程时间调整与自定义节次设置。

依据: PR #253、PR #261。

### Material 3 Expressive 动效系统

全应用主页面切换、底部抽屉、弹窗及课表交互全面接入 Material 3 Expressive 动效规范。采用共享轴（Shared Axis）与容器转换（Container Transform）原语，配合物理弹簧曲线取代传统线性过渡，保证转场动画自然且支持手势即时打断。

依据: PR #255。

### 课程表多步撤回与重做历史栈

对课表的操作（添加、删除、编辑课程以及课表维度的调整）现已支持内存历史栈多步撤回与重做。用户可在课表界面逐步回退误操作。最大撤回步数可在实验室深度设置中自定义调节或关闭（默认深度为 5 步）。

依据: PR #240。

### 单次课程调整与胶囊快速加课

现已支持仅针对某一特定日期的课程实例进行调课，不影响原周期循环课程设置。同时，在课表格子空白处轻点即可呼出预填对应星期与节次的快速加课胶囊。

依据: PR #225（贡献者 @jim139129）。

### 教务目录扩充、新青果解析修复与分享口令导入

- 伴随新青果解析器修复，佳木斯大学（`moe:4123010222`）正式转正至已实现配置基准（已实现配置院校：351 增至 352 所）。
- 修复新青果/金智（`kingo_new`）课表因前置导轨列导致的星期计算偏移，解决周六周日课程静默丢失的问题。
- 优化新青果无教室课程（如体育课、讲座）容错解析，避免因缺少地点行导致解析被丢弃。
- 全量吸收 WakeUP 6.6.2 教务目录，将 43 所高校晋升为 operational 状态并附带经检验的路由配置。
- 新增 WakeUP v6.1.70 分享口令在线导入通路，支持直接解析口令字符串拉取课表数据。
- 经由 sg_asset 通用通路及 282 个厂商脚本，吸收 264 所拾光高校条目至全国目录。
- 依据真机回传日志，完成南开大学 classic_eams 形态 B 开闸适配。
- 适配宁波城市职业技术学院（NBCC）webvpn 形态 B 网关并修复 UA 入口改判。
- 周次兼容正则支持中文全角括号 `（单）` / `（双）`。

依据: PR #272（贡献者 @Kerry1020）、PR #254、PR #248、PR #251、PR #247、PR #243、PR #232。

### Android 13+ 主题动态图标与现代几何比例

桌面启动图标补齐 monochrome 矢量图层，完整支持 Android 13+ Material You 动态拾色主题图标。主图形几何比例对齐 55% × 60% 居中现代平滑圆角标准。

依据: PR #238、PR #237。

### 节次表头时间对齐选项

课表节次表头新增起止时间对齐方式设置项，可根据个人排课习惯及屏幕列宽灵活调整时间显示位置。

依据: PR #259（贡献者 @jim139129）。

### 平板竖屏单栏回落保护

当平板或折叠屏设备处于竖屏方向时，界面自动回落为手机单栏导航形态；双栏分屏布局严格由横屏几何比例判定门闸控制，避免竖屏下内容过窄挤压。

依据: PR #235。

### Agent 本机回环调试控制台

内置面向自动化排查与编码代理的本机语义调试控制台，通过本地回环端口以 JSON 协议提供只读的课表拓扑结构、配置快照与状态树查询。

依据: PR #265、PR #267（贡献者 @Kerry1020）。

## 修复

- 修复选校返回路由：在教务选校页按返回键或手势不再意外退回课表管理页，仅在导入成功后关闭添加课表层。依据: PR #272（贡献者 @Kerry1020）。
- 移除选校列表中协议名称的「（未实测）」后缀，统一由点击后的二次确认弹窗承载提示。依据: PR #272（贡献者 @Kerry1020）。
- 摘除未审核的 vivo 原子组件声明（`vivo_widget`），所有桌面小组件回归标准 Android AppWidget 通路，彻底解决 OriginOS 智慧桌面上小组件无法拖放与编辑的问题。依据: PR #234。
- 补全 vivo 原子岛展开卡协议并隔离提醒会话，修复多表通知串扰问题。依据: PR #226。
- 修复教务 HTML 解析器尾部回车换行符引起的解析异常，并对齐 Shiguang 资源文件在跨平台换行符下的校验哈希。依据: PR #257（贡献者 @jim139129）。
- 清理教务解析器单元测试套件中的重复夹具文件。依据: PR #263（贡献者 @jim139129）。
- 修正多语言翻译字符串与注释中的错别字。依据: PR #262（贡献者 @jim139129）。
- 优化小组件画廊卡片排版，行内以大卡为准等高，小卡等比缩放居中。依据: PR #231、PR #245。
- 将加课表单节次数字输入框替换为 Material 3 双头滑杆，周次范围动态跟随课表最大周数。依据: PR #244。
- 重绘并更新 README 00-overview 预览图，同步最新小组件风格与透明圆角规范，前置官网入口。依据: PR #239、PR #242（贡献者 @Kerry1020）、PR #268、PR #269、PR #270、PR #271。

## 已知限制

- 拾光目录导入状态：新录入的 264 所拾光高校目前标记为 unverified，待后续实机接入逐波验证。
- 调试控制台：仅供本地排查与测试自动化使用，仅监听 loopback 回环网卡。

## 验证

- 审计范围: v1.0.62..e4f38f45（= origin/main，共 42 个提交）。
- 单元测试: `:app:testDebugUnitTest` 实测 3,543+ 项测试，0 失败，100% 通过。
- 版本参数: versionName 1.0.63 / versionCode 10063（由 git tag 在构建期派生）。
- CI 流程: 发布 tag 推送后由 GitHub Actions 自动构建 Release APK、执行 R8 优化、签名并生成 sha256sums 资产。
- 本版贡献者:
  - @Kerry1020: FLClash 液态玻璃底栏引擎与 1.25f 物理透镜拉伸（PR #266、PR #272）、新青果教务解析修复与佳木斯大学转正（PR #272）、选校返回路由修复（PR #272）、控制台 CI 与测试内存优化（PR #267）、README 官网链接与顶栏精简（PR #239、PR #242）。
  - @jim139129: 单次课程时间调整与快速加课胶囊（PR #225）、节次表头时间对齐设置（PR #259）、解析器测试夹具去重与错别字修正（PR #263、PR #262）、教务 HTML 换行符剥离（PR #257）、流体云文档补充（PR #256）、厂商资料整理（PR #229）。
