# Sleepy v1.0.68

Choose Today as your startup page, import timetables from Qingdao City University, and resize quick-add courses in Liquid Glass mode.

## What's New

### Open Sleepy on Today

Choose **Today** in **Settings > Schedule Display > Default schedule view** to open the app on the Today page after launch. Grid remains the default for new installations, and the Schedule tab keeps its grid layout when Today is selected as the startup page (PR #478).

### Two more verified school entries

- **Tongji University (Graduate)** is now listed as supported. A successful import report covered 19 courses (PR #477).
- **Qingdao City University** is now listed as supported. A successful import report covered 53 courses (PR #480).

## Fixes

- **Qingdao City University timetable import:** Sleepy now uses the timetable response already received by the school page after its verification step. This avoids sending the same request again when the portal rejects it. The same fallback is available for other newer Zhengfang portals when direct fetching is blocked, including WebVPN and `/kbcx/` entry paths. Open the timetable page and wait for the courses to load before tapping Import. Contributor @z2bguoguo (PR #479).
- **Quick-add resizing in Liquid Glass:** The top and bottom handles of a quick-add course capsule now resize the selected period range instead of triggering the glass drag effect. Tapping the middle of the capsule still confirms the course (PR #481).
- **Timetable script parsing:** Sleepy now reads course names assembled from static strings and variable aliases, ignores timetable-like text inside regular-expression examples, and locates the course list in UCAS responses even when unrelated data appears first (PR #464).

## Known Limitations

- For newer Zhengfang portals that use the page-response fallback, complete the school's verification and wait for the timetable to appear before tapping Import. If you change the semester or filters, query the timetable again before importing.
- The script-parsing changes passed automated regression tests, but PR #464 did not include a live authenticated-account or Android WebView import test.
- Full Liquid Glass refraction requires Android 13 or later. Earlier Android versions use the supported fallback appearance.

## Verification

- User-facing changes: PR #464, #477, #478, #479, #480 and #481.
- CI: main-branch run `45951633890` passed all 8 jobs for commit `f69402e4d163774dd4c01d22e8a416c4ec6b1108`: commit lint, release-notes consumer checks, school catalog check, collector tests, academic fetch-script tests, Android lint, Android assemble and Android unit tests. The Android unit-test job passed `:app:testDebugUnitTest`, `:liquid-glass-runtime:testAndroidHostTest` and the release-console lifecycle test; the assemble job passed `:app:assembleRelease`. The Actions log does not expose a single aggregate test count.
- School import reports: Tongji University (Graduate), 19 courses; Qingdao City University, 53 courses.
- Release version: `1.0.68 / 10068`.
- The tag-triggered Release workflow reruns unit tests, builds production-signed APKs, verifies their signing certificates against the configured release keystore, and attaches `sha256sums.txt` with the final APK fingerprints.
- Contributors this release: @z2bguoguo (Qingdao City University timetable import fix, PR #479).

---

# Sleepy v1.0.68

启动时可直接进入「今日」页，青岛城市学院课表现已支持导入，液态玻璃模式下也能正常调整快速加课的节次。

## 新增功能

### 启动时进入「今日」页

在**设置 > 课表显示 > 默认课表视图**中选择「今日」，下次启动 Sleepy 时就会直接进入今日页。新安装仍默认使用网格视图；选择今日页作为启动页面后，课表页内部也继续保持网格布局（PR #478）。

### 新增两所已验证支持的学校

- **同济大学（研究生）**现已标记为已支持。成功导入报告包含 19 门课程（PR #477）。
- **青岛城市学院**现已标记为已支持。成功导入报告包含 53 门课程（PR #480）。

## 修复

- **修复青岛城市学院课表导入：**教务页面完成校方验证并显示课表后，Sleepy 会直接使用页面已经收到的课表响应，避免再次请求被校方拒绝的接口。新版正方门户在主动抓取受阻时也会使用这一回退方式，兼容 WebVPN 和裸 `/kbcx/` 入口。导入前请先打开课表页面，完成验证并等待课程加载。贡献者 @z2bguoguo（PR #479）。
- **修复液态玻璃下快速加课无法调整节次：**现在拖动课程胶囊顶部或底部的把手即可调整起止节次，不会再被玻璃拖拽效果抢占；点击胶囊中间区域仍可确认加课（PR #481）。
- **改进教务脚本解析：**现在可以识别由静态字符串拼接或变量别名组成的课程名称，不会把正则表达式示例文本误识别成课程；解析中国科学院大学（UCAS）课表时，也能跳过前置无关数据并找到实际课程列表（PR #464）。

## 已知限制

- 对使用页面响应回退方式的新版正方门户，需要先完成校方验证并等待课表显示，再点击导入。切换学期或筛选条件后，请重新查询课表。
- 本版教务脚本解析改动已通过自动化回归测试；PR #464 未进行真实账号登录或 Android WebView 在线导入验证。
- 完整液态玻璃折射效果需要 Android 13 或更新版本。更早版本的 Android 继续使用兼容外观。

## 验证

- 用户可感知变更对应 PR：#464、#477、#478、#479、#480、#481。
- CI：主线 CI `45951633890` 在提交 `f69402e4d163774dd4c01d22e8a416c4ec6b1108` 上的 8 项任务全部通过：提交规范检查、官网更新日志消费检查、学校目录检查、采集器测试、教务抓取脚本测试、Android Lint、Android 构建和 Android 单元测试。Android 单元测试任务通过了 `:app:testDebugUnitTest`、`:liquid-glass-runtime:testAndroidHostTest` 和 Release 控制台生命周期测试；构建任务通过了 `:app:assembleRelease`。Actions 日志没有提供统一的测试总数。
- 学校导入报告：同济大学（研究生）19 门课；青岛城市学院 53 门课。
- 发布版本：`1.0.68 / 10068`。
- 标签触发的 Release 工作流会重新运行单元测试、构建正式签名 APK、校验 APK 签名证书与项目配置的 Release 密钥一致，并将包含最终 APK 指纹的 `sha256sums.txt` 附加到 GitHub Release。
- 本版贡献者：@z2bguoguo（修复青岛城市学院课表导入，PR #479）。
