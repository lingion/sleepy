# Sleepy v1.0.1

### Android 12+ no longer crashes while scheduling reminders
Before, Android 12 and later could throw a `SecurityException` during startup because exact-alarm access is not automatically granted. Sleepy now checks `canScheduleExactAlarms()` and falls back to an inexact daily alarm when access is unavailable. The reminder still runs daily; Android controls the exact delivery time.

### Daily reminders survive a reboot
Before, the manifest pointed to the wrong `BootReceiver` package, so the system could not restore scheduled reminders after boot. The receiver path now points to the implementation under `widget.notification`.

### Startup continues even if reminder scheduling fails
Before, an unexpected scheduling error could happen before the first screen appeared. Reminder scheduling is now guarded so a failure is logged without preventing the main activity from loading.

### Build
- Release tag: `v1.0.1` hotfix for the v1.0.0 build
- versionName in the tagged build: `1.0.0`
- versionCode: `1`
- APK: 13.2 MB, v2 and v3 signed

— Lingion

---

# Sleepy v1.0.1

### Android 12 及以上不再因提醒调度崩溃
之前，Android 12 及以上系统在应用启动时可能因为精确闹钟权限未自动授予而抛出 `SecurityException`。现在 Sleepy 会先检查 `canScheduleExactAlarms()`；没有权限时改用不精确的每日闹钟。提醒仍会每天触发，具体到达时间由 Android 调整。

### 重启后可以恢复每日提醒
之前，Manifest 指向了错误的 `BootReceiver` 包路径，系统启动后无法恢复已安排的提醒。现在已改为指向 `widget.notification` 下的实际实现。

### 提醒调度异常不会挡住启动
之前，调度过程中的意外异常可能发生在首屏显示之前。现在调度过程受到保护，失败只记录日志，不会阻止主页面加载。

### 构建
- Release 标签：`v1.0.1`，用于修复 v1.0.0 的热修复版本
- 标签对应构建的 versionName：`1.0.0`
- versionCode：`1`
- APK：13.2 MB，使用 v2 和 v3 签名

— Lingion
