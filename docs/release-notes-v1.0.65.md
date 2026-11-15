# Sleepy v1.0.65

> WebDAV backups, date-based period schedules, original-school updates and independent 4×2 widgets.

Upgrade note: Existing 4×2 grid widgets need to be added again using one of the new vertical or horizontal entries.

## What's New

<a id="en-backup-webdav"></a>

### Back up to your own WebDAV server

Configure an HTTPS WebDAV directory in Backup and restore, test the connection, upload a full .sleepybackup archive, or select a remote archive to download and restore. Restores use the existing merge or replace confirmation. The archive contains application data and settings; WebDAV connection credentials stay on the device.

Evidence: PR #432.

<a id="en-import-ai"></a>

### Convert a timetable screenshot

Copy the screenshot-conversion prompt from the import page, send it with your screenshot to an AI of your choice, then paste the sleepy-v1 reply into Sleepy to preview and import your timetable.

Evidence: PR #430.

<a id="en-jw-school-update"></a>

### Return to the original academic system

Timetables created through the in-app academic import flow can retain their school, protocol and selected entry. Update from academic system opens that timetable's original school login directly, followed by the existing review-and-confirm update flow. The entry is shown only when the timetable has valid import eligibility.

Evidence: PR #434, PR #435, PR #437.

<a id="en-jw-school-roundtrip"></a>

### Keep school identity with supported exports

Sleepy native exports, compatible WakeUP JSON and full backups retain the saved academic school identity. This is separate from source suggestions already supported in the previous release. Restoring metadata does not grant permission to use the academic update entry.

Evidence: PR #434, PR #436, PR #437.

<a id="en-date-periods"></a>

### Use period times for each date

New academic imports can retain a base period table and dated overrides. Week-view course detail cards, Today, timetable widgets, course notifications, daily agendas and class Do Not Disturb select period times for the applicable date. Explicit course times take priority.

Evidence: PR #428 (contributor @Sunrongguo2008).

<a id="en-widget-split"></a>

### Choose either 4×2 grid directly

The widget picker and gallery list the vertical seven-day grid and horizontal three-day grid as separate widgets. Each has its own binding, settings and preview; the horizontal widget retains the left/right weekday-label option. Existing complete-grid sizes remain available. Re-add an existing 4×2 grid using one of the new entries after upgrading.

Evidence: PR #425 (contributor @Kerry1020).

## Fixes

<a id="en-qdu-import"></a>

- Correct Qingdao University course-period mapping and retain its supplied period schedule and dated rules. Qingdao University was already listed as supported in v1.0.64.

  Evidence: PR #428 (contributor @Sunrongguo2008).

<a id="en-native-first"></a>

- Put Sleepy native format first in the supported import formats and the timetable share sheet, before WakeUp JSON and ICS.

  Evidence: PR #431.

<a id="en-settings-state"></a>

- Open timetable settings directly from Me, keep edits bound to the intended timetable, and measure glass segmented controls to fit their text and touch targets.

  Evidence: PR #415 (contributor @Kerry1020).

<a id="en-gesture-owner"></a>

- Keep active drags on glass switches, segmented controls and sliders from scrolling the settings page underneath. Classic sliders retain their gesture protection; Classic switches and segmented choices use the standard Material interaction path.

  Evidence: PR #421.

<a id="en-glass-quick-input"></a>

- Restore quick taps and consecutive horizontal drags on Liquid Glass tabs and switches.

  Evidence: PR #424 (contributor @Kerry1020).

<a id="en-editor-entry"></a>

- Open a new timetable or period-table editor before initializing its draft, avoiding a new card flashing in the departing management list. Keep initialization, save, back and holiday-page returns tied to the correct editing session.

  Evidence: PR #427 (contributor @Kerry1020).

<a id="en-glass-buttons"></a>

- Make glass action buttons respond across their full touch target, keep the lens raised during a held drag, and settle after release or cancellation. Classic controls retain their Material path.

  Evidence: PR #427 (contributor @Kerry1020).

<a id="en-glass-date"></a>

- Display the glass date input, calendar and year selector one at a time. Returning to the calendar releases text-input focus; Classic mode keeps the standard date picker.

  Evidence: PR #427 (contributor @Kerry1020).

<a id="en-glass-panels"></a>

- Correct glass dialog and sheet contours, backdrop sampling and dimming, including the light dialog's narrow frosted rim. The timetable selector keeps a glass backing with a Material list layout. Management cards and navigation rows retain their existing layouts.

  Evidence: PR #427 (contributor @Kerry1020).

<a id="en-glass-slider"></a>

- Use a horizontal white thumb at rest and lift it into a glass lens during a drag. Extend tracks beneath the thumb at both ends and concentrate refraction around the lens edge.

  Evidence: PR #427 (contributor @Kerry1020), PR #442 (contributor @Kerry1020).

<a id="en-bottom-sheet-backing"></a>

- Use the sheet glass material for attached bottom navigation in Liquid Glass mode, with an inset backing, rounded upper corners and a straight lower edge. Retain the standard theme-colored selected indicator and size the backing to the bottom-bar container.

  Evidence: PR #427 (contributor @Kerry1020), PR #441.

<a id="en-nav-icon-repeat"></a>

- Keep navigation-icon animation continuous when it is triggered again quickly, including icons shown through a glass lens.

  Evidence: PR #438 (contributor @Cold577).

<a id="en-navigation-render"></a>

- Compose only the active main tab or landscape pane during navigation, while retaining saved page state. Shorten route transitions and reuse timetable projections and drawing data instead of repeating the same work during rendering.

  Evidence: PR #439.

<a id="en-runtime-size"></a>

- Trim unused release code and resources, use one lossless WebP set for widget previews, and package compact school and city data. Keep migration schemas and full location-audit material in source control rather than the installed APK.

  Evidence: PR #411.

<a id="en-license-link"></a>

- Open the complete open-source notices page on the official website from About. Bundled third-party notices remain available, and restored navigation to the old license screen opens About.

  Evidence: PR #411, PR #427 (contributor @Kerry1020).

<a id="en-website-refresh"></a>

- Refresh the official website after a complete stable Release, so version details, downloads and release notes follow the latest published version.

  Evidence: PR #410.

<a id="en-gitee-distribution"></a>

- Keep Gitee release descriptions separate from APK mirroring. Mirror APKs for the five most recent releases that have APK assets, retain older descriptions, and link oversized descriptions to the complete GitHub notes.

  Evidence: PR #413, PR #414, PR #416.

<a id="en-period-editing"></a>

- Keep a new manual period row in place until both times are filled, then insert it in time order. Validate automatic period times and preserve edge-period course assignments when standard periods have a clear mapping.

  Evidence: PR #415 (contributor @Kerry1020).

<a id="en-week-picker"></a>

- Choose a week in a centered, scrollable dialog instead of a fixed-height selector.

  Evidence: PR #415 (contributor @Kerry1020).

<a id="en-anchored-choices"></a>

- Open language, capsule display, calendar-import target and schedule-adjustment choices in anchored menus over the page.

  Evidence: PR #427 (contributor @Kerry1020).

<a id="en-accordion-actions"></a>

- Give expandable headers a single expansion action and an accessible expanded state, with delete actions kept separate.

  Evidence: PR #427 (contributor @Kerry1020).

<a id="en-reminder-inline"></a>

- Expand “Timetables to notify” directly inside Reminder settings. Select each timetable in place, including while the reminder master switch is off; the section starts collapsed and keeps its original position.

  Evidence: PR #444.

## Known Limitations

- The previous 4×2 grid receiver has been replaced. Remove the old desktop instance and add either the vertical or horizontal widget again.
- WebDAV is manual and HTTPS-only. Backups are ordinary unencrypted ZIP archives; protect access to the server. Uploads and downloads are limited to 100 MiB. Connection settings are excluded from exported backups and Android system backup.
- Screenshot conversion copies a prompt to the clipboard. You choose the AI and send the screenshot yourself; review its reply before importing.
- Old database rows and manual, file or text imports do not receive academic update eligibility. Restoring an exported .sleepybackup does not grant it either. Manual copies and pre-import backup copies clear it. Android system restoration of the raw database is a separate path and has not been verified here.
- Date rules follow the imported ranges and years. Some main timetable grids, time axes and overlays still use the current-day schedule. Updating or appending to an existing timetable does not replace its dated rules; combination imports cannot bind directly to an existing shared period table. Ordinary sleepy-v1, WakeUp, period files and ICS do not preserve the complete scheme. Use a full .sleepybackup to retain it.
- Academic parser and script checks do not establish successful login at every school. This review did not perform a fresh authenticated Qingdao University import.
- Full glass refraction still requires Android 13 or later. Android 12 uses the supported blur path; earlier versions use the fallback. Widget refresh and notification timing remain subject to launcher, permission and background restrictions.

## Verification

- Release baseline: published `v1.0.64` (`77b878fd5c8903a3f201ea938b3b152de4c4fbc0`).
- Focused local checks: 7 button tests and 29 reminder tests, all passing. Classic and Liquid Glass expansion previews were inspected.
- Local full app suite: 4,863 tests, 0 failures, 0 errors, 2 live-service tests skipped.
- Glass-runtime host suite: 56 tests, 0 failures, 0 errors.
- Build: `1.0.65 / 10065`, derived from the release tag. The release pipeline runs the app and runtime suites, builds the APKs and verifies their signing certificate against the project signing key.
- APKs: arm64-v8a, armeabi-v7a, x86_64 and universal. Exact SHA-256 fingerprints are provided in the attached `sha256sums.txt`.

### Build and distribution maintenance

<a id="en-debug-delivery"></a>

- Official Debug builds retain the current official tag history for version calculation, support signing manual branch builds in the same run, and provide separate architecture downloads in the original PR. This prevents fork-only tags from assigning an older application version.

  Evidence: PR #422, PR #423 (contributor @Kerry1020), PR #426 (contributor @Kerry1020).

<a id="en-build-maintenance"></a>

- Update build and collector dependencies, bootstrap the configured JDK on supported desktop platforms, maintain database migration tests and add startup/navigation benchmark infrastructure. Benchmark infrastructure alone does not establish a measured startup or frame-rate improvement.

  Evidence: PR #417, PR #419, PR #425 (contributor @Kerry1020), PR #433, PR #440.

<a id="en-distribution-docs"></a>

- Update installation and product documentation to distinguish Android, App Store distribution and TestFlight availability; each platform keeps its own release scope.

  Evidence: PR #412, PR #429.

### Contributors this release

- @Kerry1020: timetable settings, glass controls and materials, independent 4×2 widgets, official Debug delivery and slider rendering. PR #415, PR #423, PR #424, PR #425, PR #426, PR #427, PR #442.
- @Sunrongguo2008: Qingdao University import and date-based period schedules. PR #428.
- @Cold577: continuity when navigation-icon animation is triggered again. PR #438.

---

# Sleepy v1.0.65

> WebDAV 备份、按日期切换作息、原学校入口更新与独立 4×2 小组件。

升级提醒：旧 4×2 周网格需从新的竖向或横向入口重新添加。

## 新增功能

<a id="zh-backup-webdav"></a>

### 备份到自己的 WebDAV 服务器

在备份恢复页配置 HTTPS WebDAV 目录，测试连接、上传完整 .sleepybackup 归档，或选择远端归档下载恢复。恢复沿用合并或覆盖确认流程。归档包含应用数据与设置，WebDAV 连接凭据只保留在本机。

依据：PR #432.

<a id="zh-import-ai"></a>

### 转换课表截图

在导入页复制截图转换提示词，将提示词和截图发送给自选 AI，再把 sleepy-v1 回复粘贴回 Sleepy，预览并导入课表。

依据：PR #430.

<a id="zh-jw-school-update"></a>

### 返回原来的教务入口更新

通过应用内教务导入流程创建的课表可保存学校、协议和所选入口。「从教务更新」直接打开该课表原来的学校登录页，后续沿用已有的差异审阅与确认更新流程。只有具备有效导入资格的课表才显示此入口。

依据：PR #434, PR #435, PR #437.

<a id="zh-jw-school-roundtrip"></a>

### 支持的导出格式保留学校身份

Sleepy 原生格式、兼容 WakeUP JSON 和全量备份可保留已保存的教务学校身份。它与上一版已有的来源建议分别保存。恢复元数据不会授予教务更新资格。

依据：PR #434, PR #436, PR #437.

<a id="zh-date-periods"></a>

### 按日期使用对应作息

新建教务导入可保存基础作息和日期区间规则。周视图逐日课程详情卡、今日页、小组件、课程通知、每日摘要及上课勿扰按实际日期选择作息，课程自身的明确钟点优先。

依据：PR #428 (contributor @Sunrongguo2008).

<a id="zh-widget-split"></a>

### 直接添加两种 4×2 周网格

桌面选择器与小组件库将「竖向七天」和「横向三天」列为两个独立周网格。两者各自绑定课表、设置并预览，横向组件保留星期标签左右位置选项。原有完整网格尺寸继续保留。升级后，旧 4×2 周网格需从新入口重新添加。

依据：PR #425 (contributor @Kerry1020).

## 修复

<a id="zh-qdu-import"></a>

- 修正青岛大学课程节次映射，保留来源提供的作息与日期规则。青岛大学在 v1.0.64 已列为支持学校。

  依据：PR #428 (contributor @Sunrongguo2008).

<a id="zh-native-first"></a>

- 支持导入的格式列表优先显示 Sleepy 原生格式，课表分享面板中的原生选项排在 WakeUp JSON、ICS 之前。

  依据：PR #431.

<a id="zh-settings-state"></a>

- 课表设置移到“我的”直接打开，读写均绑定正在编辑的课表；玻璃分段控件按文字和触达尺寸布局。

  依据：PR #415 (contributor @Kerry1020).

<a id="zh-gesture-owner"></a>

- 玻璃开关、分段选择器和滑杆持续接收已激活的拖动，避免设置页跟着纵向滚动。经典滑杆保留手势保护，经典开关和分段选择沿用标准 Material 交互。

  依据：PR #421.

<a id="zh-glass-quick-input"></a>

- 恢复液态玻璃页签与开关的快速点击和连续横向拖动。

  依据：PR #424 (contributor @Kerry1020).

<a id="zh-editor-entry"></a>

- 新建课表或作息表先进入编辑页，再初始化草稿，避免离场中的管理列表先闪出新卡片。初始化、保存、返回及调休子页往返按当前编辑会话处理。

  依据：PR #427 (contributor @Kerry1020).

<a id="zh-glass-buttons"></a>

- 玻璃操作按钮的完整触控区均可响应，按住拖动时镜片保持抬起，松手或取消后回落。经典控件保留 Material 路径。

  依据：PR #427 (contributor @Kerry1020).

<a id="zh-glass-date"></a>

- 玻璃日期输入、日历和年份选择互斥显示；切回日历时释放文字输入焦点。经典模式保留标准日期选择器。

  依据：PR #427 (contributor @Kerry1020).

<a id="zh-glass-panels"></a>

- 修正玻璃对话框与底部弹层的轮廓、背景采样和压暗，包含浅色对话框的窄磨砂边缘。课表选择弹窗保留玻璃背板并采用 Material 列表布局；管理卡片与导航行沿用原有布局。

  依据：PR #427 (contributor @Kerry1020).

<a id="zh-glass-slider"></a>

- 玻璃滑杆静止时使用横向白色滑块，拖动时抬起玻璃镜片；轨道在两端延伸到滑块下方，折射集中在镜片边缘。

  依据：PR #427 (contributor @Kerry1020), PR #442 (contributor @Kerry1020).

<a id="zh-bottom-sheet-backing"></a>

- 液态玻璃模式的贴底导航改用底部弹层材质，背板左右内缩，保留顶部圆角，下沿直切。选中指示沿用主题标准颜色，背板高度随底栏容器。

  依据：PR #427 (contributor @Kerry1020), PR #441.

<a id="zh-nav-icon-repeat"></a>

- 导航图标快速重复触发时保持动效连续，玻璃镜片中的图标同步显示。

  依据：PR #438 (contributor @Cold577).

<a id="zh-navigation-render"></a>

- 主标签与横屏窗格切换时只组合当前页，返回后恢复已保存的页面状态。缩短路由转场，复用课表投影与绘制数据，减少渲染中的重复计算。

  依据：PR #439.

<a id="zh-runtime-size"></a>

- 裁剪发行包中的无用代码和资源，小组件预览改为一套无损 WebP，学校与城市数据使用精简的运行格式。迁移 schema 与完整位置审计资料保留在源码中。

  依据：PR #411.

<a id="zh-license-link"></a>

- 关于页的开源声明打开官网完整开源页，随包第三方许可文件继续保留。恢复旧许可页导航时进入关于页。

  依据：PR #411, PR #427 (contributor @Kerry1020).

<a id="zh-website-refresh"></a>

- 完整正式 Release 完成后触发官网更新，使版本信息、下载与更新说明跟随最新正式版本。

  依据：PR #410.

<a id="zh-gitee-distribution"></a>

- Gitee 发布说明与 APK 镜像分别同步。APK 保留最近五个带安装包的版本，较早版本保留说明；过长正文附完整 GitHub 更新说明入口。

  依据：PR #413, PR #414, PR #416.

<a id="zh-period-editing"></a>

- 手动作息的新行在时间填完整前保持原位，填完后按时间插入。自动作息校验时间；标准节次增减时，保留能够明确映射的非常规节次课程。

  依据： PR #415 (contributor @Kerry1020).

<a id="zh-week-picker"></a>

- 选周改用居中的可滚动弹窗，避免固定高度限制可见周次。

  依据： PR #415 (contributor @Kerry1020).

<a id="zh-anchored-choices"></a>

- 语言、胶囊主显示、日历导入目标和调休相关单选改用覆盖页面的锚点菜单。

  依据： PR #427 (contributor @Kerry1020).

<a id="zh-accordion-actions"></a>

- 折叠区标题提供单一展开动作和可访问的展开状态，删除按钮独立操作。

  依据： PR #427 (contributor @Kerry1020).

<a id="zh-reminder-inline"></a>

- 提醒设置中的“需要通知的课表”改为原位展开，直接选择各课表的通知开关。默认收起并保持原位置，总开关关闭时也可预先配置。

  依据：PR #444。

## 已知限制

- 旧 4×2 周网格 receiver 已替换。请移除旧桌面实例，重新添加竖向或横向组件。
- WebDAV 仅手动操作且只支持 HTTPS。备份是普通未加密 ZIP，请保护服务器访问权限。上传与下载上限为 100 MiB；连接配置不进入导出备份和 Android 系统备份。
- 截图转换入口复制提示词到剪贴板。AI 服务和截图发送由你自行选择与操作，导入前请检查回复。
- 旧数据库记录及手动、文件、文本导入不获得教务更新资格，恢复导出的 .sleepybackup 也不授予资格。手动复制及导入前生成的备份副本会清除资格。Android 系统直接恢复原始数据库属于另一条路径，本轮未验证。
- 日期规则按来源提供的区间和年份执行。主课表的部分网格、时间刻度及浮层仍使用当前日作息；更新或追加已有课表不会替换日期规则，组合导入不能直接绑定已有共享作息表。普通 sleepy-v1、WakeUp、作息文件及 ICS 不保留完整方案，保留方案请使用全量 .sleepybackup。
- 教务解析和脚本检查不代表所有学校均已完成真实登录，本轮没有重新认证青岛大学账号导入。
- 完整玻璃折射仍要求 Android 13 或更高版本。Android 12 使用支持的模糊路径，更早版本使用回退效果。小组件刷新与通知时机仍受桌面、权限和后台限制影响。

## 验证

- 发布基线：已发布 `v1.0.64`（`77b878fd5c8903a3f201ea938b3b152de4c4fbc0`）。
- 本机定向检查：按钮 7 项、提醒 29 项，全部通过；经典及液态玻璃的展开预览已核对。
- 本机应用全量测试：4863 项，失败 0，错误 0，2 项真实服务测试跳过。
- 玻璃运行时 host 测试：56 项，失败 0，错误 0。
- 构建版本：由发布标签派生的 `1.0.65 / 10065`。发布流水线运行应用与运行时测试，构建 APK，并将签名证书与项目签名密钥核对。
- APK：arm64-v8a、armeabi-v7a、x86_64 及通用包。准确 SHA-256 摘要见附件 `sha256sums.txt`。

### 构建与分发维护

<a id="zh-debug-delivery"></a>

- 官方 Debug 构建使用完整官方标签计算版本，支持手动分支在同次运行签名，并在原 PR 提供各架构独立下载，避免 fork 独有旧标签使应用版本回退。

  依据：PR #422, PR #423 (contributor @Kerry1020), PR #426 (contributor @Kerry1020).

<a id="zh-build-maintenance"></a>

- 更新构建与采集器依赖，在受支持桌面平台引导配置的 JDK，维护数据库迁移测试，并加入启动与导航基准设施。基准设施本身不构成启动速度或帧率提升的实测结论。

  依据：PR #417, PR #419, PR #425 (contributor @Kerry1020), PR #433, PR #440.

<a id="zh-distribution-docs"></a>

- 更新安装与产品说明，分别说明 Android、App Store 与 TestFlight 分发渠道，各平台按自身发布范围说明功能。

  依据：PR #412, PR #429.

### 本版贡献者

- @Kerry1020：课表设置、玻璃控件与材质、独立 4×2 小组件、官方 Debug 交付及滑杆渲染。PR #415、PR #423、PR #424、PR #425、PR #426、PR #427、PR #442。
- @Sunrongguo2008：青岛大学导入及按日期切换作息。PR #428。
- @Cold577：导航图标重复触发时的动效连续性。PR #438。
