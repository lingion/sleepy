# WebView Environment & Failure Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Sleepy's existing JW diagnostic session and dump with thread-safe WebView environment, navigation, failure, and SSL-decision evidence so Edge/Via/native-WebView differences can be diagnosed from one export.

**Architecture:** Keep Android-only collection at WebView factory and callback boundaries, where main-thread-only WebView properties can be safely read. Store the collected values in a pure Kotlin `JwWebViewEnvironment`/diagnostic state model, and format them through pure string exporters. Preserve the existing raw diagnostic session and dump behavior; add new files rather than changing existing sensitive-content semantics implicitly.

**Tech Stack:** Kotlin, Android WebView, AndroidX WebKit, JUnit4, Gradle Android plugin, `ConcurrentLinkedDeque`.

## Global Constraints

- Work only on branch `feat/jw-webview-diagnostics`, based on the current `main` baseline.
- Do not overwrite or stage unrelated pre-existing working-tree changes.
- Do not change WebView loading behavior, SSL policy, mixed-content policy, interceptors, frame capture, or parser behavior.
- Never read `WebView.settings`, `WebView.url`, or other `view.*` state from `shouldInterceptRequest` or another Chromium background callback.
- Preserve `JwDiagnosticSession`'s existing 500-entry bounded behavior and existing dump files.
- New exported diagnostic text must redact Cookie, Set-Cookie, Authorization, and Proxy-Authorization values and sensitive URL query parameters (`token`, `key`, `session`, `secret`, `jwt`, and equivalent case-insensitive names).
- Do not export passwords, full Cookie values, Authorization values, or unredacted HTML through the new environment/navigation/failure files.
- Use commit author email `lingion@hrbeu.edu.cn`; do not add a Claude/Anthropic trailer.
- Before merge, run `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and the existing parser/attribution contract selectors required by `BRANCHING.md`.

## File Map

- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironment.kt` — pure data model, bounded recording state, URL/header redaction, and deterministic text exporters.
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentProbe.kt` — Android-only provider/version/device probe, called only during WebView factory setup.
- Create: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentTest.kt` — pure JVM model, redaction, bounded-recording, and formatting tests.
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt` — own/reset/export the new environment state while preserving current request/console/download APIs.
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewClientBuilder.kt` — record page navigation, resource failures, and SSL decisions without introducing background-thread `view.*` access.
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt` — capture actual UA/provider at factory time, record initial URL and page completion, and inject storage capability probing after page completion.
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCaptureDump.kt` — add `environment.txt`, `navigation.txt`, and `failures.txt`; retain existing `env/device.txt` and existing raw files.
- Modify: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt` — verify reset/export integration and no regression in existing logs.

---

### Task 1: Add the pure WebView diagnostic model and exporter

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironment.kt`
- Create: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentTest.kt`

**Interfaces:**
- `data class JwWebViewEnvironment(...)` exposes the fields from the approved spec, with nullable unknown values and empty-list defaults.
- `class JwWebViewEnvironmentState` provides thread-safe `setEnvironment`, `recordRedirect`, `recordFailure`, `recordWindowOpen`, `recordSslDecision`, `recordStorageProbe`, `snapshot`, `exportEnvironment`, `exportNavigation`, and `exportFailures`.
- `sanitizeUrl(String): String` and `redactHeaders(String?): String?` are pure functions used by exporters.

- [ ] **Step 1: Write failing JVM tests for defaults and deterministic export**

```kotlin
@Test
fun emptyStateExportsStableEnvironment() {
    val state = JwWebViewEnvironmentState()
    assertEquals(null, state.snapshot().providerPackage)
    assertTrue(state.snapshot().redirectChain.isEmpty())
    assertTrue(state.exportEnvironment().contains("providerPackage=<unknown>"))
}

@Test
fun exportPreservesFieldOrder() {
    val state = JwWebViewEnvironmentState()
    state.setEnvironment(JwWebViewEnvironment(actualUserAgent = "UA"))
    val text = state.exportEnvironment()
    assertTrue(text.indexOf("providerPackage") < text.indexOf("actualUserAgent"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwWebViewEnvironmentTest"`

Expected: FAIL because the model/state/exporter does not exist.

- [ ] **Step 3: Implement the pure model and bounded state**

Use a `ConcurrentLinkedDeque` for redirect, failure, window, and SSL records. Every record method must sanitize at insertion or export and trim to 500 entries. `snapshot()` must return immutable copies. The data class must include:

```kotlin
val providerPackage: String? = null
val providerVersionName: String? = null
val chromiumMajor: Int? = null
val androidSdkInt: Int? = null
val androidRelease: String? = null
val manufacturer: String? = null
val model: String? = null
val configuredUserAgent: String? = null
val actualUserAgent: String? = null
val initialUrl: String? = null
val finalUrl: String? = null
val redirectChain: List<String> = emptyList()
val storageEnabled: Boolean = false
val storageEntryCount: Int? = null
val sessionStorageEntryCount: Int? = null
val indexedDbAvailable: Boolean? = null
val serviceWorkerState: String? = null
val windowOpenRequests: List<String> = emptyList()
val sslDecisions: List<String> = emptyList()
val failedResources: List<String> = emptyList()
```

- [ ] **Step 4: Implement URL/header redaction tests and code**

```kotlin
@Test
fun sensitiveQueryValuesAreRemoved() {
    assertEquals(
        "https://jw.example.edu.cn/path?term=2026&token=%5BREDACTED%5D",
        sanitizeUrl("https://jw.example.edu.cn/path?term=2026&token=secret")
    )
}

@Test
fun sensitiveHeadersNeverExportOriginalValue() {
    val text = redactHeaders("Cookie: sid=secret\nAuthorization: Bearer abc\nAccept: text/html")!!
    assertFalse(text.contains("sid=secret"))
    assertFalse(text.contains("Bearer abc"))
    assertTrue(text.contains("***REDACTED***"))
}
```

Implement case-insensitive header names and query keys. Preserve host/path and non-sensitive parameters. Do not remove the parameter name itself; replace only its value with `[REDACTED]` so investigators can identify which credential class was present.

- [ ] **Step 5: Run the focused test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwWebViewEnvironmentTest"`

Expected: PASS.

- [ ] **Step 6: Commit the pure model**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironment.kt app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentTest.kt
git commit -m "feat(jw): add WebView diagnostic environment model"
```

---

### Task 2: Integrate the model into the diagnostic session

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt`
- Modify: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt`

**Interfaces:**
- `JwDiagnosticSession.resetSession()` clears the new state in addition to existing buffers.
- `JwDiagnosticSession.recordWebViewEnvironment(...)` sets the factory-time snapshot.
- `JwDiagnosticSession.recordRedirect`, `recordFailure`, `recordWindowOpen`, `recordSslDecision`, and `recordStorageProbe` delegate to the new state.
- `JwDiagnosticSession.exportEnvironment()`, `exportNavigation()`, and `exportFailures()` return deterministic text without altering existing `exportNetlog()` or `exportConsole()` output.

- [ ] **Step 1: Add contract tests for reset and delegation**

```kotlin
@Test
fun resetClearsWebViewDiagnosticState() {
    JwDiagnosticSession.recordRedirect("https://example.test/one")
    assertTrue(JwDiagnosticSession.exportNavigation().contains("one"))
    JwDiagnosticSession.resetSession()
    assertFalse(JwDiagnosticSession.exportNavigation().contains("one"))
}
```

- [ ] **Step 2: Run the contract test and verify the new assertions fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwDiagnosticSessionContractTest"`

Expected: FAIL until delegation exists.

- [ ] **Step 3: Add the state field, reset call, and delegation methods**

Keep all methods small and thread-safe. Do not modify `recordRequest`, `recordConsole`, `recordDownload`, or their existing output formats. `exportEnvironment()` must include the session id header; navigation/failure exports must include the same session id and stable section headers.

- [ ] **Step 4: Run focused integration tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwDiagnosticSessionContractTest" --tests "*JwWebViewEnvironmentTest"`

Expected: PASS.

- [ ] **Step 5: Commit the session integration**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt
git commit -m "feat(jw): integrate WebView diagnostics into session"
```

---

### Task 3: Add the Android factory-time environment probe

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentProbe.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt`

**Interfaces:**
- `JwWebViewEnvironmentProbe.capture(webView: WebView, configuredUserAgent: String?, initialUrl: String): JwWebViewEnvironment` reads provider/version, Android build fields, and actual `webView.settings.userAgentString` only on the factory/main thread.
- `JwWebViewLoginScreen` calls the probe before `loadUrl`, then records the initial URL.

- [ ] **Step 1: Add the probe with safe unknown fallbacks**

Use `WebView.getCurrentWebViewPackage()` where available. Parse the leading numeric component of `versionName` into `chromiumMajor`; if absent or unparsable, use null. Catch provider lookup failures and keep diagnostic collection non-fatal.

- [ ] **Step 2: Wire probe invocation into the existing `WebView(context).apply { ... }` factory**

After `settings` configuration and before `loadUrl(lastUrl)`, call:

```kotlin
val configuredUa = if (desktopUa) DESKTOP_USER_AGENT else null
JwDiagnosticSession.recordWebViewEnvironment(
    JwWebViewEnvironmentProbe.capture(this, configuredUa, lastUrl)
)
JwDiagnosticSession.recordRedirect(lastUrl)
```

Do not read these properties later from `shouldInterceptRequest`.

- [ ] **Step 3: Record final URL and navigation chain in the existing page-finished callback**

Have `JwWebViewClientBuilder` invoke the existing callback with the URL, and record the final URL/redirect entry in the session before the existing network bridge reinjection. Deduplicate adjacent identical URLs.

- [ ] **Step 4: Run focused tests and compile the app module**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwDiagnostic*" :app:compileDebugKotlin`

Expected: PASS and successful Kotlin compilation.

- [ ] **Step 5: Commit the Android probe and wiring**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentProbe.kt app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt
git commit -m "feat(jw): capture WebView provider and effective user agent"
```

---

### Task 4: Record resource failures and SSL decisions without violating thread affinity

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewClientBuilder.kt`
- Modify: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt`

**Interfaces:**
- `JwWebViewClientImpl.onReceivedError(...)` records both main-frame and subresource failures using `WebResourceRequest.url`, error code, and response MIME when available.
- `onReceivedSslError(...)` records `proceed|host` or `cancel|host` before invoking the existing handler decision.
- No new access to `view.settings`, `view.url`, or `view.cookieManager` occurs in callbacks.

- [ ] **Step 1: Add tests for failure/SSL export shape**

```kotlin
@Test
fun failuresContainCodeAndSanitizedUrl() {
    JwDiagnosticSession.recordFailure(-6, "https://example.test/app.js?token=secret", "script")
    assertTrue(JwDiagnosticSession.exportFailures().contains("ERROR_-6"))
    assertFalse(JwDiagnosticSession.exportFailures().contains("token=secret"))
}
```

- [ ] **Step 2: Implement `onReceivedError` recording**

Record `ERROR_<errorCode>|<sanitized-url>|<mime-or-unknown>` and preserve the existing WebView default behavior by not overriding the error handler beyond recording.

- [ ] **Step 3: Implement SSL decision recording**

Resolve host from `error.url`, check the existing `SslBypassRegistry`, record the decision, and then call the same `proceed()`/`cancel()` branch as before.

- [ ] **Step 4: Verify the thread-affinity test remains green**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwWebViewCallbackThreadAffinenessTest" --tests "*JwDiagnostic*"`

Expected: PASS; the existing test must continue to prove no callback reads `view.*` state.

- [ ] **Step 5: Commit failure and SSL diagnostics**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewClientBuilder.kt app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt
git commit -m "feat(jw): record WebView resource and SSL failures"
```

---

### Task 5: Probe storage capabilities and capture window-open requests

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt`
- Modify: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentTest.kt`

**Interfaces:**
- The page-finished callback evaluates a bounded probe that returns only counts/capabilities, never key/value contents.
- `WebChromeClient.onCreateWindow` records the requested URL if available, then returns `false` to preserve current behavior; actual popup support remains sub-project 2.
- `JwDiagnosticSession.recordStorageProbe(...)` stores local/session counts, IndexedDB availability, and service-worker state.

- [ ] **Step 1: Add pure parsing tests for storage probe JSON**

```kotlin
@Test
fun storageProbeParsesCountsWithoutValues() {
    val parsed = parseStorageProbe("{\"localCount\":2,\"sessionCount\":1,\"indexedDb\":true,\"serviceWorker\":\"unknown\"}")
    assertEquals(2, parsed.localCount)
    assertEquals(1, parsed.sessionCount)
    assertTrue(parsed.indexedDb)
}
```

- [ ] **Step 2: Add the bounded JavaScript probe**

Use `Object.keys(localStorage).length`, `Object.keys(sessionStorage).length`, an IndexedDB feature check, and a service-worker feature/registration-state check. Return JSON only. Do not enumerate values or send cookies.

- [ ] **Step 3: Parse the callback and record the result**

Strip the JSON string wrapper safely, parse with `org.json.JSONObject`, and ignore malformed results. Invoke after `onPageFinished` and after bridge/network recorder installation.

- [ ] **Step 4: Add `onCreateWindow` recording only**

Read the target URL from the supplied `Message`/temporary WebView only if available on the main thread; record a sanitized URL. Do not create a popup WebView in this task.

- [ ] **Step 5: Run focused tests and lint the changed Kotlin**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwWebViewEnvironmentTest" --tests "*JwDiagnostic*" :app:lintDebug`

Expected: PASS and zero lint warnings attributable to the changes.

- [ ] **Step 6: Commit storage/window diagnostics**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwWebViewEnvironmentTest.kt
git commit -m "feat(jw): capture storage and popup navigation evidence"
```

---

### Task 6: Export the three new diagnostic files

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCaptureDump.kt`
- Modify: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt`

**Interfaces:**
- `buildZip()` adds `environment.txt`, `navigation.txt`, and `failures.txt` using the new session exporters.
- Existing `env/device.txt`, `netlog.txt`, cookies, storage, and frame files remain present and unchanged.
- The index and manifest describe the three additions without claiming that raw cookies are redacted; the new files are the redacted diagnostic channel.

- [ ] **Step 1: Add ZIP contract assertions**

Build a dump with the existing pure `buildZip` path and assert that ZIP entry names include `environment.txt`, `navigation.txt`, and `failures.txt`, while `netlog.txt` and `env/device.txt` still exist.

- [ ] **Step 2: Add the three `writeText` calls**

Insert them beside `netlog.txt`/`console.txt` so the diagnostic package contains both the existing raw evidence and the new redacted summary evidence.

- [ ] **Step 3: Update `INDEX.txt` descriptions**

Describe `environment.txt` as provider/UA/device/storage capability evidence, `navigation.txt` as redirect/window-open evidence, and `failures.txt` as failed resources/SSL decisions. Keep the existing explicit description of `cookies-full.txt` as sensitive raw evidence.

- [ ] **Step 4: Run ZIP and focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwCaptureDump*" --tests "*JwDiagnostic*"`

Expected: PASS.

- [ ] **Step 5: Commit dump export integration**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCaptureDump.kt app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSessionContractTest.kt
git commit -m "feat(jw): export redacted WebView diagnostic files"
```

---

### Task 7: Full verification and branch handoff

**Files:**
- Modify only files required by failing tests or lint; do not absorb unrelated working-tree changes.

- [ ] **Step 1: Run all targeted diagnostics tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*JwWebViewEnvironment*" --tests "*JwDiagnostic*" --tests "*JwCaptureDump*" --tests "*JwWebViewCallbackThreadAffinenessTest*"`

Expected: PASS.

- [ ] **Step 2: Run the full required unit and lint gates**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:lintDebug`

Expected: all tests pass and lint reports zero warnings/errors for the module.

- [ ] **Step 3: Run the mandated contract selectors**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*AboutLicenseAttributionTest*" --tests "*Parser*"
```

Expected: PASS.

- [ ] **Step 4: Review the diff and verify scope**

Run: `git diff main...HEAD --stat && git diff main...HEAD --check`

Confirm that only the approved diagnostics files, tests, and plan/spec documentation changed; unrelated existing working-tree modifications remain unstaged and untouched.

- [ ] **Step 5: Report branch readiness**

Report the branch name, commit list, test/lint commands and results, and any device-only verification still needed. Do not merge, push, tag, or release without explicit user approval.

## Self-Review

- **Spec coverage:** Provider/version, Chromium major, SDK/device, configured/effective UA, initial/final URL, redirect chain, storage capabilities, service worker, window-open requests, failures, SSL decisions, redaction, three output files, JVM tests, and thread-affinity constraints are covered by Tasks 1–6.
- **Placeholder scan:** No TBD/TODO or undefined implementation step is used. Every task names concrete files, interfaces, tests, commands, and commit content.
- **Type consistency:** All later tasks consume the `JwWebViewEnvironmentState` delegation methods defined in Task 1/2; exporter names remain `exportEnvironment`, `exportNavigation`, and `exportFailures` throughout.
- **Scope check:** The plan does not implement popup support, profile switching, interceptor governance, frame capture changes, or parser changes; those remain later sub-projects.
