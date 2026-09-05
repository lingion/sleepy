# Sleepy v1.0.32

### Review release notes before downloading an update
Before, checking for an update either showed a short message or started downloading immediately. Sleepy now shows the full release notes first. Downloads display progress and can be cancelled; retry distinguishes a failed check from a failed download.

### Update state is cleaned up correctly
Cancelled downloads can be retried safely, the no-update state no longer flashes incorrectly, and stale partial APKs are removed at startup.

### Six-language coverage is broader
User-facing strings in the schedule editor, theme screens, period editor, notification scheduler, and Fluid Cloud service now have translations for Simplified Chinese, Traditional Chinese, English, Japanese, Spanish, and the default fallback locale.

### Localized layouts remain readable
The week-view course-count capsule now measures localized text before drawing. The time column uses the same text sizing as course names and wraps long time ranges instead of clipping them.

### All widgets use the same synchronous rendering path
The four existing widget types now share the stable RemoteViews + Canvas rendering path, and the fifth WeekView widget was added as a plain-text seven-day list with wrapping and separators.

### Build
- Release tag: `v1.0.32`
- versionName: `1.0.32`
- versionCode: `33`
- ABI APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`

— Lingion

---

# Sleepy v1.0.32

### 下载更新前可以先看更新说明
之前检查更新只显示简短提示，或者直接开始下载。现在会先展示完整更新说明；下载有进度并且可以取消，重试也会区分检查失败和下载失败。

### 更新状态会正确清理
取消下载后可以安全重试；无更新提示结束后不再短暂闪出错误状态；启动时会清理残留的半成品 APK。

### 六种语言的界面覆盖更完整
课表编辑器、主题页、节次编辑器、通知排程和流体云服务中的用户可见文字，现在提供简体中文、繁体中文、英语、日语、西班牙语和默认兜底资源。

### 多语言布局保持可读
周视图课程数量胶囊现在会先测量本地化文字再绘制。时间栏使用与课程名一致的字号，较长时间范围会折行，不再被截断。

### 小组件统一使用稳定的同步渲染
原有四种小组件现在共用 RemoteViews + Canvas 渲染路径；同时新增第五种“周视图”小组件，以七列纯文本显示每天课程，支持换行和分隔线。

### 构建
- Release 标签：`v1.0.32`
- versionName：`1.0.32`
- versionCode：`33`
- ABI APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`

— Lingion
