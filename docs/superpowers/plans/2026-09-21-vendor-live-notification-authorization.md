# 全厂商实时通知授权引导 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在「设置 → 通知」原有位置增强“流体云 / 超级岛”实验开关，为全部登记厂商提供可验证的通知/增强能力状态、设置跳转、返回复检和标准通知兜底。

**Architecture:** 新增纯 Kotlin 的厂商能力适配层：运行时厂商识别进入统一 registry，registry 返回可验证的 `VendorLiveNotificationCapability`，UI 只消费状态和设置动作，不直接读取厂商属性或拼 Intent。各适配器只使用公开 Android API、通知渠道状态和可解析的系统设置入口；无法验证时返回 `UNKNOWN`。`ReminderScreen` 保持通知二级目录原位置，在标题右侧渲染常驻 `[实验]` Tag，开启后显示状态/去设置入口，并在生命周期恢复时重新检查。

**Tech Stack:** Kotlin/JVM, Android SDK, Jetpack Compose Material 3, AndroidX Core NotificationCompat, JUnit 4, 现有 Gradle Android 工程。

## Global Constraints

- 功能必须继续位于「设置 → 通知」二级目录原位置，不移动到通用设置或独立实验室页。
- 标题旁显示小胶囊 Tag「实验」，不把 Tag 文本拼进功能标题。
- 覆盖 OPPO/一加/realme、vivo/iQOO、小米/Redmi/POCO、魅族、荣耀、华为/HarmonyOS、三星及 AOSP/其他 Android。
- `ENABLED` 只能来自可验证系统状态；不能由品牌匹配、Intent 可创建或曾打开设置推导。
- 无法验证时返回 `UNKNOWN`，不能误报为已授权。
- 厂商增强能力失败、权限被拒或设置页不存在时，标准 Android 通知必须继续工作。
- 不新增第三方依赖，不使用反射或隐藏权限 API，不自动打开系统设置。
- 厂商 Intent 必须按专属入口、应用通知详情/渠道、系统通知设置顺序 fallback，并检查可解析性或捕获启动异常。
- Android 13+ 仍使用现有 `POST_NOTIFICATIONS` 运行时权限流程。
- 所有新增用户文案同步 `values`, `values-en`, `values-es`, `values-ja`, `values-zh-rCN`, `values-zh-rTW`。
- 每个任务完成后先运行其验证命令，再产生一个可独立回滚的 commit；不得 push。

---

## Task 1: 定义统一能力状态与厂商适配器接口

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationCapability.kt`
- Create: `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdapter.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt:16-42`（复用或迁移现有厂商枚举，避免第二套厂商识别）
- Test: `app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationCapabilityTest.kt`

**Interfaces:**
- Produces `enum class VendorCapabilityState { ENABLED, DISABLED, NOTIFICATION_PERMISSION_REQUIRED, SETTINGS_REQUIRED, NOT_SUPPORTED, UNKNOWN }`。
- Produces `data class VendorLiveNotificationCapability(val vendor: LiveCardVendor, val state: VendorCapabilityState, val summaryRes: Int, val settingsIntents: List<IntentSpec>, val fallbackToAppNotificationSettings: Boolean)`。
- Produces `data class IntentSpec(val action: String, val extras: Map<String, String> = emptyMap())`，只作为可测试的 intent 描述，不在纯 JVM 层启动 Activity。
- Produces `interface VendorLiveNotificationAdapter { fun inspect(context: Context): VendorLiveNotificationCapability; fun settingsIntents(context: Context): List<Intent> }`。

- [ ] **Step 1: Write failing pure JVM tests for state semantics.**

```kotlin
@Test fun `unknown is not enabled`() {
    assertNotEquals(VendorCapabilityState.ENABLED, VendorCapabilityState.UNKNOWN)
}

@Test fun `capability preserves vendor and fallback contract`() {
    val result = VendorLiveNotificationCapability(
        vendor = LiveCardVendor.OPPO,
        state = VendorCapabilityState.UNKNOWN,
        summaryRes = R.string.reminder_fluid_status_unknown,
        settingsIntents = listOf(IntentSpec("android.settings.APP_NOTIFICATION_SETTINGS")),
        fallbackToAppNotificationSettings = true
    )
    assertTrue(result.fallbackToAppNotificationSettings)
    assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", result.settingsIntents.single().action)
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*VendorLiveNotificationCapabilityTest*'`
Expected: FAIL because the new state and contract types do not exist.

- [ ] **Step 2: Implement the minimal contract and keep `LiveCardVendor` as the single vendor enum.**

Add KDoc stating that `UNKNOWN` is a legitimate terminal state and that `ENABLED` requires a verifiable system signal. Do not add vendor-specific behavior in this task.

- [ ] **Step 3: Run the focused tests.**

Run: `./gradlew :app:testDebugUnitTest --tests '*VendorLiveNotificationCapabilityTest*'`
Expected: PASS.

- [ ] **Step 4: Commit the contract.**

```bash
git add app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationCapability.kt app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdapter.kt app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationCapabilityTest.kt app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt
git commit -m "feat(notification): define vendor capability contract"
```

---

## Task 2: Implement vendor registry, detection, and setting Intent fallback

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdapters.kt`
- Create: `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationSettings.kt`
- Test: `app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdaptersTest.kt`
- Test: `app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationSettingsTest.kt`

**Interfaces:**
- Produces `fun detectLiveCardVendor(manufacturer: String, brand: String = ""): LiveCardVendor` with normalized aliases for OPPO/OnePlus/realme, vivo/iQOO, Xiaomi/Redmi/POCO, Huawei, HONOR, Meizu, Samsung and generic Android.
- Produces `fun vendorAdapterFor(vendor: LiveCardVendor): VendorLiveNotificationAdapter`.
- Produces `fun buildVendorSettingsIntents(context: Context, vendor: LiveCardVendor): List<Intent>` in strict priority order.
- Consumes the Task 1 state contract and existing `CourseNotificationScheduler` channel id.

- [ ] **Step 1: Write the failing vendor matrix tests.**

```kotlin
@Test fun `manufacturer aliases map to one vendor family`() {
    assertEquals(LiveCardVendor.OPPO, detectLiveCardVendor("OnePlus"))
    assertEquals(LiveCardVendor.REALME, detectLiveCardVendor("realme"))
    assertEquals(LiveCardVendor.IQOO, detectLiveCardVendor("iQOO"))
    assertEquals(LiveCardVendor.XIAOMI, detectLiveCardVendor("Redmi"))
    assertEquals(LiveCardVendor.HONOR, detectLiveCardVendor("HONOR"))
}

@Test fun `unknown vendor uses generic adapter`() {
    assertEquals(LiveCardVendor.GENERIC, detectLiveCardVendor("Acme"))
}
```

For settings, assert that the first candidate is vendor-specific, application notification settings is later, and system notification settings is last. Assert that no vendor candidate is required to exist on the device before fallback is attempted.

Run: `./gradlew :app:testDebugUnitTest --tests '*VendorLiveNotificationAdaptersTest*' --tests '*VendorLiveNotificationSettingsTest*'`
Expected: FAIL because registry and fallback functions do not exist.

- [ ] **Step 2: Implement normalized vendor detection and one adapter per vendor family.**

Adapters must at minimum inspect `POST_NOTIFICATIONS` on API 33+, the existing before-class notification channel where available, and return `UNKNOWN` for the private enhancement state unless a public, testable signal exists. Generic Android returns `ENABLED` only for standard notification readiness, never for a vendor enhancement.

- [ ] **Step 3: Implement safe Intent construction.**

Use `Settings.ACTION_APP_NOTIFICATION_SETTINGS` with `Settings.EXTRA_APP_PACKAGE` for the application fallback and `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` with the existing channel id for the channel fallback. Add vendor-specific Actions only as candidates. The caller must filter with `resolveActivity` and continue on `ActivityNotFoundException`.

- [ ] **Step 4: Run focused adapter tests and compile.**

Run: `./gradlew :app:testDebugUnitTest --tests '*VendorLiveNotificationAdaptersTest*' --tests '*VendorLiveNotificationSettingsTest*' :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 5: Commit the registry and fallback layer.**

```bash
git add app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdapters.kt app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationSettings.kt app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationAdaptersTest.kt app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveNotificationSettingsTest.kt
git commit -m "feat(notification): add vendor capability adapters and settings fallback"
```

---

## Task 3: Wire capability inspection and safe settings launch into ReminderScreen

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt:75-460`
- Modify: `app/src/main/java/com/lingion/sleepy/app/src/main/AndroidManifest.xml` only if an existing declaration is insufficient (do not add new permissions without approval)
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/mine/ReminderCapabilityFlowTest.kt`

**Interfaces:**
- Consumes `vendorAdapterFor`, `VendorLiveNotificationCapability`, `VendorCapabilityState`, and `buildVendorSettingsIntents`.
- Produces a UI flow: toggle on → notification permission → inspect → status dialog; “去设置” → safe fallback launch; lifecycle resume → inspect again.

- [ ] **Step 1: Add pure flow tests before UI edits.**

```kotlin
@Test fun `notification permission is checked before vendor settings`() {
    assertEquals(listOf("notification", "vendor"), inspectOrderForPermissionMissing())
}

@Test fun `settings failure falls through to application notification settings`() {
    assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", lastResolvableActionFor(emptySet()))
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderCapabilityFlowTest*'`
Expected: FAIL until the flow helper exists.

- [ ] **Step 2: Extract a small, pure `ReminderCapabilityFlow` helper if needed.**

Keep Compose state and Activity launching out of the pure helper. The helper must choose `NOTIFICATION_PERMISSION_REQUIRED` before vendor inspection and must not persist vendor `ENABLED` as a boolean.

- [ ] **Step 3: Add lifecycle-aware reinspection.**

Use the existing Compose lifecycle pattern (`LifecycleEventObserver` or `LaunchedEffect` tied to lifecycle) to refresh state on `ON_RESUME`. The refresh must not launch settings automatically.

- [ ] **Step 4: Add the status dialog and safe “去设置” action.**

Display distinct copy for enabled, disabled/settings required, unknown, unsupported, and notification permission required. Try the first resolvable Intent; if launching fails, continue to the next candidate. Keep the fluid notification preference enabled after user confirmation, while showing the actual capability status separately.

- [ ] **Step 5: Run flow tests and compile.**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderCapabilityFlowTest*' :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit the UI flow.**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt app/src/test/java/com/lingion/sleepy/ui/screen/mine/ReminderCapabilityFlowTest.kt
git commit -m "feat(notification): guide vendor live notification authorization"
```

---

## Task 4: Add the permanent experimental Tag in the existing notification row

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt:380-460`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-es/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/mine/ReminderExperimentalTagContractTest.kt`

**Interfaces:**
- Consumes existing `ReminderToggleRow` conventions and `reminder_fluid_title`.
- Produces a title composable/row that always renders `reminder_experimental_tag` beside the fluid notification title without moving the row.

- [ ] **Step 1: Write the resource and placement contract test.**

```kotlin
@Test fun `all locales define experimental tag and capability status copy`() {
    assertResourceKeyExists("reminder_experimental_tag")
    assertResourceKeyExists("reminder_fluid_status_unknown")
    assertResourceKeyExists("reminder_fluid_go_to_settings")
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderExperimentalTagContractTest*'`
Expected: FAIL because the keys do not exist.

- [ ] **Step 2: Add six-locale strings.**

Add the Tag, status, settings action, and fallback copy. Keep the feature title as “流体云 / 超级岛” (translated per locale); do not append “实验” to the title string itself.

- [ ] **Step 3: Render the Tag in the existing notification row.**

Use a compact Material 3 surface/chip-like composable consistent with existing settings styles, small enough not to change row height unexpectedly. Do not use emoji or a large button for the Tag.

- [ ] **Step 4: Run resource and placement tests.**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderExperimentalTagContractTest*' :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 5: Commit the Tag and copy.**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt app/src/main/res/values*/strings.xml app/src/test/java/com/lingion/sleepy/ui/screen/mine/ReminderExperimentalTagContractTest.kt
git commit -m "feat(notification): label live card setting as experimental"
```

---

## Task 5: Preserve notification fallback and add integration contracts

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt:240-330`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/notification/FluidCloudService.kt:85-128`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt:45-100`
- Test: `app/src/test/java/com/lingion/sleepy/widget/notification/StandardNotificationFallbackTest.kt`
- Test: `app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveCardRendererTest.kt`

**Interfaces:**
- Consumes the Task 2 capability state only for diagnostics/UI; notification scheduling remains usable when state is `UNKNOWN`, `DISABLED`, or `NOT_SUPPORTED`.
- Produces a stable generic `NotificationCompat` path for every `LiveCardVendor`.

- [ ] **Step 1: Write failing fallback tests.**

```kotlin
@Test fun `unknown vendor still selects generic notification path`() {
    val notification = renderFor(LiveCardVendor.GENERIC)
    assertEquals(NotificationCompat.CATEGORY_PROGRESS, notification.category)
}

@Test fun `enhanced capability failure does not cancel standard scheduling`() {
    assertTrue(scheduleDecision(VendorCapabilityState.UNKNOWN).standardNotificationAllowed)
    assertTrue(scheduleDecision(VendorCapabilityState.NOT_SUPPORTED).standardNotificationAllowed)
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*StandardNotificationFallbackTest*' --tests '*VendorLiveCardRendererTest*'`
Expected: FAIL until the fallback contract is explicit.

- [ ] **Step 2: Add the smallest explicit fallback decision helper or assertions.**

Do not alter existing vendor extras except to ensure exceptions in enhancement construction do not prevent `builder.build()` or standard notification scheduling. Keep all private Bundle extras best-effort.

- [ ] **Step 3: Run notification integration tests.**

Run: `./gradlew :app:testDebugUnitTest --tests '*StandardNotificationFallbackTest*' --tests '*VendorLiveCardRendererTest*'`
Expected: PASS.

- [ ] **Step 4: Commit the fallback contracts.**

```bash
git add app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt app/src/main/java/com/lingion/sleepy/widget/notification/FluidCloudService.kt app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt app/src/test/java/com/lingion/sleepy/widget/notification/StandardNotificationFallbackTest.kt app/src/test/java/com/lingion/sleepy/widget/notification/VendorLiveCardRendererTest.kt
git commit -m "test(notification): lock standard fallback for vendor live cards"
```

---

## Task 6: Full verification, vendor matrix audit, and documentation update

**Files:**
- Modify: `docs/superpowers/specs/2026-09-21-vendor-live-notification-authorization-design.md` only if implementation decisions differ from the accepted design.
- Modify: `docs/sop/feature-baseline.md` only if the existing ignored local baseline requires the new user-visible notification behavior recorded.
- Test: all existing tests and all new notification tests.

- [ ] **Step 1: Run focused vendor and notification tests.**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*Vendor*' --tests '*Notification*' --tests '*Reminder*'
```

Expected: PASS; no new failures.

- [ ] **Step 2: Run the full unit suite from a clean task graph.**

Run:

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks
```

Expected: BUILD SUCCESSFUL; report total tests, failures, errors and skipped from `app/build/test-results/testDebugUnitTest/*.xml`.

- [ ] **Step 3: Build the debug APK.**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run lint and compare against the existing baseline.**

Run: `./gradlew :app:lintDebug`
Expected: no new lint errors/warnings in changed files; pre-existing baseline must be reported separately, not silently fixed as part of this feature.

- [ ] **Step 5: Perform static contract audit.**

Run:

```bash
rg -n 'UNKNOWN|ENABLED|POST_NOTIFICATIONS|ACTION_APP_NOTIFICATION_SETTINGS|ACTION_CHANNEL_NOTIFICATION_SETTINGS|reminder_experimental_tag' app/src/main app/src/test
rg -n '<<<<<<<|=======|>>>>>>>' app/src/main app/src/test docs/superpowers/specs/2026-09-21-vendor-live-notification-authorization-design.md
```

Expected: every vendor path has an UNKNOWN/fallback route; no conflict markers; no hidden API or reflection calls.

- [ ] **Step 6: Commit only documentation/baseline changes if needed.**

```bash
git add docs/superpowers/specs/2026-09-21-vendor-live-notification-authorization-design.md docs/sop/feature-baseline.md
git commit -m "docs(notification): record vendor authorization behavior"
```

Do not push, tag, close issues, or publish a release as part of this plan.

---

## Dependency Order and Review Gates

1. Task 1 defines the contract and must pass before adapters are added.
2. Task 2 implements all vendor families and fallback ordering.
3. Task 3 wires permission/settings flow without changing visual placement.
4. Task 4 adds the permanent experimental Tag and all locale copy.
5. Task 5 locks standard notification behavior against vendor failures.
6. Task 6 performs full regression verification and documents deviations.

After each task, review the diff for scope and run that task's focused tests. Before claiming completion, compare the implementation line-by-line against the user requirement: the row remains under Notifications, the Tag is visible beside the title, all vendor families are covered, authorization is never fabricated, and standard notifications remain functional.
