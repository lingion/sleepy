# Sleepy v1.0.67

Recover timetables with an invalid semester length, restore startup on older Android devices, and fix Liquid Glass navigation and course colors.

## What's New

This maintenance release focuses on the fixes below.

## Fixes

- **Recover existing zero-week timetables on startup.** Updating automatically repairs their semester length while keeping courses, timetable identities and the default selection. You do not need to clear app data. Restoring a backup also repairs invalid lengths. New timetables must contain at least one week.
- **Restore startup on older Android devices, including the affected Huawei Android 10 device.** The bottom navigation uses the supported rendering path when Android 13 shader APIs are unavailable.
- Keep the attached Liquid Glass bottom navigation at its intended height after a cold start, including while system navigation insets are first applied.
- Use the course editor's color controls when adding a single course occurrence. Choose group, automatic or custom colors in the same layout, with the same custom color picker in Liquid Glass mode.

## Known Limitations

- Full glass refraction requires Android 13 or later. Older Android versions continue to use their supported fallback appearance.

## Verification

- Fixes: PR #470. Baseline: published `v1.0.66`.
- CI run `44046838317` passed all seven checks: commit lint, school catalog, collector tests, academic fetch scripts, Android unit tests, Android lint and APK assembly.
- Targeted startup and navigation checks passed on the affected Huawei device running Android 10. Single-course color controls were also checked on that device.
- Planned release version: `1.0.67 / 10067`, derived from the release tag. Final APK fingerprints are provided in the release's `sha256sums.txt`.

# Sleepy v1.0.67

修复无效学期周数导致的持续闪退、旧版 Android 启动兼容问题，以及液态玻璃底栏和单次课程配色。

## 新增功能

本版集中修复已有功能，具体变化如下。

## 修复

- **已有 0 周课表会在启动时自动修复。** 更新后自动恢复有效学期周数，保留课程、课表身份和默认选择，无需清除应用数据。恢复备份时同样会修复无效周数。新建课表至少需要 1 周。
- **修复旧版 Android 启动闪退，包括本次受影响的华为 Android 10 设备。** 系统不支持 Android 13 着色器接口时，底部导航使用兼容的绘制方式。
- 修复冷启动时贴底液态玻璃导航栏高度异常，首次应用系统导航栏边距时也能保持正确高度。
- 添加单次课程时，配色使用与编辑课程相同的控件。分组、自动、自定义颜色采用一致布局，液态玻璃模式下使用相同的自定义颜色选择器。

## 已知限制

- 完整玻璃折射需要 Android 13 或更新版本。旧版 Android 继续使用兼容的替代外观。

## 验证

- 修复对应 PR #470，基线为已发布的 `v1.0.66`。
- CI 运行 `44046838317` 的七项检查全部通过：提交规范、学校目录、采集器测试、教务抓取脚本、Android 单元测试、Android Lint 和 APK 构建。
- 已在本次受影响的华为 Android 10 设备上通过启动与导航专项检查，并检查单次课程的配色控件。
- 计划发布版本为 `1.0.67 / 10067`，由发布标签生成。最终 APK 指纹见 Release 附带的 `sha256sums.txt`。
