# 更新流程改造 + i18n 补全 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把关于页的「点更新即下载安装」改成「点更新 → 拉 changelog 弹窗 → 用户确认 → 下载(可取消)→ 安装」,同时补全 en/es/ja/zh-rCN/zh-rTW 五语言的存量缺失 key。

**Architecture:** `UpdateManager` 从单方法 `downloadLatest()` 拆成 `fetchUpdateInfo()`(只拉信息)+ `downloadApk()`(带进度可取消)+ `cleanOldApk()`(启动清理)。AboutScreen 用 sealed class 状态机驱动 UI,新增 `UpdateChangelogDialog` 弹窗组件。

**Tech Stack:** Kotlin, Jetpack Compose (Material3), HttpURLConnection, JUnit 4(纯 JVM 测试), Coroutines。

## Global Constraints

- minSdk 26, targetSdk/compileSdk 37, JavaVersion 17
- 包名 `com.lingion.sleepy`,FileProvider authority `${applicationId}.fileprovider`,cache-path 已配置
- 默认 `values/strings.xml` 是中文(363 key),是所有 locale 的 fallback
- 单测纯 JVM(JUnit 4 + org.json),无 mock 框架;可测的是纯函数 JSON 解析,不测 IO/网络
- 五语言:values(默认中文)、values-en、values-es、values-ja、values-zh-rCN、values-zh-rTW
- Coroutines cancel 检查用 `coroutineContext.ensureActive()` 或 `isActive`
- Build 命令:`cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
- Test 命令:`cd /Users/lingion_k/Desktop/sleepy && ./gradlew test`

## 文件结构

| 文件 | 职责 | 动作 |
|------|------|------|
| `util/UpdateManager.kt` | 网络:拉 release 信息、下载 APK、清理旧 APK | 重构 |
| `util/UpdateInfo.kt` | data class + 纯函数 `parseReleaseJson` | 新建 |
| `ui/screen/mine/AboutScreen.kt` | 关于页 + 状态机 + 触发弹窗 | 改 |
| `ui/screen/mine/UpdateChangelogDialog.kt` | changelog 弹窗 | 新建 |
| `MainActivity.kt` | onCreate 调 cleanOldApk | 改 |
| `res/values*/strings.xml` × 6 | 新增 8 key + 存量补全 | 改 |
| `test/.../UpdateInfoParseTest.kt` | parseReleaseJson 单测 | 新建 |

---

### Task 1: 抽取 UpdateInfo + 纯解析函数(可测层)

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/util/UpdateInfo.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/UpdateInfoParseTest.kt`

**Interfaces:**
- Produces: `UpdateInfo(version: String, changelog: String, downloadUrl: String, isUpdateAvailable: Boolean)` data class;`parseReleaseJson(json: String, currentVersion: String, abi: String): UpdateInfo` 纯函数。

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lingion.sleepy.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateInfoParseTest {

    private val sampleBody = """{"tag_name":"v1.0.32","body":"## v1.0.32\n\n修复 bug","assets":[
        {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"},
        {"name":"app-armeabi-v7a-release.apk","browser_download_url":"https://example.com/b.apk"}]}"""

    @Test
    fun parses_version_changelog_url_from_github_json() {
        val info = parseReleaseJson(sampleBody, "1.0.31", "arm64-v8a")
        assertEquals("1.0.32", info.version)
        assertEquals("## v1.0.32\n\n修复 bug", info.changelog)
        assertEquals("https://example.com/a.apk", info.downloadUrl)
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun older_remote_version_is_not_an_update() {
        val info = parseReleaseJson(sampleBody, "1.0.33", "arm64-v8a")
        assertFalse(info.isUpdateAvailable)
    }

    @Test
    fun same_version_with_force_flag_is_update() {
        val body = sampleBody.replace("修复 bug", "修复 bug SLEEPY_FORCE_UPDATE=true")
        val info = parseReleaseJson(body, "1.0.32", "arm64-v8a")
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun picks_correct_abi_asset() {
        val info = parseReleaseJson(sampleBody, "1.0.31", "armeabi-v7a")
        assertEquals("https://example.com/b.apk", info.downloadUrl)
    }

    @Test
    fun missing_asset_for_abi_returns_blank_url() {
        val noX86 = """{"tag_name":"v2.0.0","body":"x","assets":[
            {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        val info = parseReleaseJson(noX86, "1.0.0", "x86_64")
        assertEquals("", info.downloadUrl)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew test --tests "*.UpdateInfoParseTest"`
Expected: FAIL — `parseReleaseJson` unresolved reference。

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.lingion.sleepy.util

/**
 * 远端 release 信息(纯数据,不含 Android 依赖,可单测)。
 *
 * [isUpdateAvailable] 由 [parseReleaseJson] 根据版本比较 + force flag 算出。
 * 调用方(UpdateManager)据它决定弹窗 vs Toast。
 */
data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

private const val FORCE_FLAG = "SLEEPY_FORCE_UPDATE=true"

/**
 * 解析 GitHub releases/latest 的 JSON 为 [UpdateInfo](纯函数,无 IO)。
 *
 * [abi] 形如 "arm64-v8a" / "armeabi-v7a" / "x86_64",用于挑对应 asset。
 * 找不到对应 asset 时 downloadUrl 返回空串(调用方走镜像回退)。
 */
fun parseReleaseJson(json: String, currentVersion: String, abi: String): UpdateInfo {
    val release = org.json.JSONObject(json)
    val version = release.optString("tag_name").removePrefix("v")
    val body = release.optString("body")
    val assetName = "app-$abi-release.apk"
    val downloadUrl = release.optJSONArray("assets")?.let { assets ->
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName }
            ?.optString("browser_download_url")
    } ?: ""
    val force = body.contains(FORCE_FLAG)
    val isUpdateAvailable = force ||
        VersionUtils.compare(version.ifBlank { "0" }, currentVersion) > 0
    return UpdateInfo(version, body, downloadUrl, isUpdateAvailable)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew test --tests "*.UpdateInfoParseTest"`
Expected: PASS,5 tests。

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy
git add app/src/main/java/com/lingion/sleepy/util/UpdateInfo.kt \
        app/src/test/java/com/lingion/sleepy/util/UpdateInfoParseTest.kt
git commit -m "feat(update): 抽取 UpdateInfo + parseReleaseJson 纯函数(可测)"
```

---

### Task 2: 重构 UpdateManager(fetch/download/clean 三方法)

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/UpdateManager.kt`

**Interfaces:**
- Consumes: `UpdateInfo`,`parseReleaseJson`(from Task 1)
- Produces:
  - `suspend fun fetchUpdateInfo(context: Context): UpdateInfo`
  - `suspend fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File`
  - `fun cleanOldApk(context: Context)`
  - `fun install(context: File)` (保留不变)
- 删除:`downloadLatest()`,`UpdateResult`,`NoUpdateAvailableException`

- [ ] **Step 1: Rewrite UpdateManager**

```kotlin
package com.lingion.sleepy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.lingion.sleepy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** 拉 GitHub/镜像 release 信息、下载 APK、清理旧 APK。不含 UI 状态。 */
object UpdateManager {
    private const val GITHUB_API = "https://api.github.com/repos/lingion/sleepy/releases/latest"
    private const val MIRROR_RELEASE = "https://gh.qdp.qzz.io/lingion/sleepy/releases/latest"
    private const val MIRROR_PREFIX = "https://gh.qdp.qzz.io/lingion/sleepy/releases/download/"

    private fun currentAbiAsset(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "app-arm64-v8a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "app-armeabi-v7a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "app-x86_64-release.apk"
        else -> "app-arm64-v8a-release.apk"
    }

    private fun currentAbi(): String = currentAbiAsset()
        .removePrefix("app-").removeSuffix("-release.apk")

    /** 只拉 release 信息,不下载。GitHub 不通回退镜像。 */
    suspend fun fetchUpdateInfo(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val abi = currentAbi()
        val abiAsset = currentAbiAsset()
        runCatching {
            val json = readText(GITHUB_API)
            return@withContext parseReleaseJson(json, BuildConfig.VERSION_NAME, abi)
        }
        // 镜像回退:正则取 tag,body 取不到
        val page = readText(MIRROR_RELEASE)
        val tag = Regex("/lingion/sleepy/releases/tag/(v[0-9A-Za-z.+_-]+)").find(page)
            ?.groupValues?.get(1)
            ?: throw IllegalStateException("主站和镜像都没找到最新版本")
        val version = tag.removePrefix("v")
        val url = "$MIRROR_PREFIX$tag/$abiAsset"
        val isUpdate = VersionUtils.compare(version, BuildConfig.VERSION_NAME) > 0
        UpdateInfo(version, "", url, isUpdate)
    }

    /** 下载 APK 到 cacheDir,带进度回调(0-100)。协程 cancel 时删半截文件。 */
    suspend fun downloadApk(
        context: Context, info: UpdateInfo, onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "sleepy-update-${currentAbiAsset()}")
        val conn = request(info.downloadUrl)
        val total = conn.contentLengthLong.coerceAtLeast(1L)
        try {
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (!target.isFile || target.length() == 0L)
            throw IllegalStateException("下载文件为空")
        target
    }

    /** 启动时清理 cacheDir 中旧安装包。 */
    fun cleanOldApk(context: Context) {
        context.cacheDir.listFiles { it.name.startsWith("sleepy-update-") }
            ?.forEach { it.delete() }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun readText(url: String): String {
        val conn = request(url)
        return try { conn.inputStream.bufferedReader().use { it.readText() } }
        finally { conn.disconnect() }
    }

    private fun request(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Sleepy/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "application/json,text/html,*/*")
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
Expected: 编译失败 —— AboutScreen 还在调 `downloadLatest()`。这是预期的(Task 4 会修),但先确认 UpdateManager 本身无语法错。

> 注:此 task 暂不 commit,因为 AboutScreen 编译依赖未解。和 Task 4 一起提交。继续 Task 3。

---

### Task 3: 新增 strings(8 key × 6 语言)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-es/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Interfaces:**
- Produces 8 个新 string key,供 Task 4 的弹窗用:`about_update_latest`、`update_found_title`、`update_download`、`update_cancel`、`update_downloading`、`update_installing`、`update_retry`、`update_download_failed`

- [ ] **Step 1: 追加到默认 values/strings.xml(中文)**

在 `about_update_failed` 行后追加:

```xml
    <string name="about_update_latest">当前已是最新版本 v%1$s</string>
    <string name="update_found_title">发现新版本 v%1$s</string>
    <string name="update_download">下载</string>
    <string name="update_cancel">取消</string>
    <string name="update_downloading">下载中 %1$d%%</string>
    <string name="update_installing">下载完成,正在打开安装器</string>
    <string name="update_retry">重试</string>
    <string name="update_download_failed">下载失败:%1$s</string>
```

- [ ] **Step 2: 追加 values-en(英文)**

```xml
    <string name="about_update_latest">You\'re on the latest version v%1$s</string>
    <string name="update_found_title">New version v%1$s available</string>
    <string name="update_download">Download</string>
    <string name="update_cancel">Cancel</string>
    <string name="update_downloading">Downloading %1$d%%</string>
    <string name="update_installing">Download complete, opening installer</string>
    <string name="update_retry">Retry</string>
    <string name="update_download_failed">Download failed: %1$s</string>
```

- [ ] **Step 3: 追加 values-es(西语)**

```xml
    <string name="about_update_latest">Ya tienes la última versión v%1$s</string>
    <string name="update_found_title">Nueva versión v%1$s disponible</string>
    <string name="update_download">Descargar</string>
    <string name="update_cancel">Cancelar</string>
    <string name="update_downloading">Descargando %1$d%%</string>
    <string name="update_installing">Descarga completa, abriendo instalador</string>
    <string name="update_retry">Reintentar</string>
    <string name="update_download_failed">Error de descarga: %1$s</string>
```

- [ ] **Step 4: 追加 values-ja(日语)**

```xml
    <string name="about_update_latest">最新バージョン v%1$s です</string>
    <string name="update_found_title">新しいバージョン v%1$s があります</string>
    <string name="update_download">ダウンロード</string>
    <string name="update_cancel">キャンセル</string>
    <string name="update_downloading">ダウンロード中 %1$d%%</string>
    <string name="update_installing">ダウンロード完了、インストーラーを起動中</string>
    <string name="update_retry">再試行</string>
    <string name="update_download_failed">ダウンロード失敗: %1$s</string>
```

- [ ] **Step 5: 追加 values-zh-rCN / values-zh-rTW(简繁)**

zh-rCN:
```xml
    <string name="about_update_latest">当前已是最新版本 v%1$s</string>
    <string name="update_found_title">发现新版本 v%1$s</string>
    <string name="update_download">下载</string>
    <string name="update_cancel">取消</string>
    <string name="update_downloading">下载中 %1$d%%</string>
    <string name="update_installing">下载完成,正在打开安装器</string>
    <string name="update_retry">重试</string>
    <string name="update_download_failed">下载失败:%1$s</string>
```
zh-rTW:
```xml
    <string name="about_update_latest">目前已是最新版本 v%1$s</string>
    <string name="update_found_title">發現新版本 v%1$s</string>
    <string name="update_download">下載</string>
    <string name="update_cancel">取消</string>
    <string name="update_downloading">下載中 %1$d%%</string>
    <string name="update_installing">下載完成,正在開啟安裝器</string>
    <string name="update_retry">重試</string>
    <string name="update_download_failed">下載失敗:%1$s</string>
```

- [ ] **Step 6: Build to verify**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
Expected: 资源编译通过(UpdateManager 编译失败仍存在,strings 本身 OK)。

---

### Task 4: 重写 AboutScreen(状态机 + 触发弹窗)

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/AboutScreen.kt`

**Interfaces:**
- Consumes: `UpdateManager.fetchUpdateInfo`、`UpdateManager.downloadApk`、`UpdateManager.install`、`UpdateInfo`、新 strings(Task 3)、`UpdateChangelogDialog`(Task 5)
- Produces: 带状态机的 AboutScreen

- [ ] **Step 1: 重写 AboutScreen 的状态部分**

把现有的 `var updateState` / `var updating` 和按钮 onClick 替换为 sealed class 状态机。文件顶部的 import 区新增:

```kotlin
import kotlinx.coroutines.Job
import com.lingion.sleepy.util.UpdateInfo
import com.lingion.sleepy.util.UpdateManager
```

在 `fun AboutScreen(onBack: () -> Unit)` 内部,替换状态变量:

```kotlin
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class NoUpdate(val version: String) : UpdateUiState()
    data class UpdateAvailable(val version: String, val changelog: String, val url: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    object Installing : UpdateUiState()
    data class Failed(val message: String, val version: String = "", val changelog: String = "", val url: String = "") : UpdateUiState()
}
```

```kotlin
// 在 AboutScreen composable 内:
var uiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
var downloadJob by remember { mutableStateOf<Job?>(null) }
var snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
```

- [ ] **Step 2: 添加状态转换函数**

```kotlin
fun checkUpdate() {
    if (uiState is UpdateUiState.Checking) return
    uiState = UpdateUiState.Checking
    scope.launch {
        runCatching { UpdateManager.fetchUpdateInfo(context) }
            .onSuccess { info ->
                if (info.isUpdateAvailable) {
                    uiState = UpdateUiState.UpdateAvailable(
                        info.version, info.changelog, info.downloadUrl
                    )
                } else {
                    uiState = UpdateUiState.NoUpdate(info.version)
                }
            }
            .onFailure { uiState = UpdateUiState.Failed(it.message ?: "未知错误") }
    }
}

fun startDownload(version: String, changelog: String, url: String) {
    val info = UpdateInfo(version, changelog, url, true)
    uiState = UpdateUiState.Downloading(0)
    downloadJob = scope.launch {
        runCatching {
            UpdateManager.downloadApk(context, info) { progress ->
                uiState = UpdateUiState.Downloading(progress)
            }
        }.onSuccess { file ->
            uiState = UpdateUiState.Installing
            UpdateManager.install(context, file)
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) {
                uiState = UpdateUiState.UpdateAvailable(version, changelog, url)
            } else {
                uiState = UpdateUiState.Failed(
                    e.message ?: "未知错误", version, changelog, url
                )
            }
        }
    }
}

fun cancelDownload(version: String, changelog: String, url: String) {
    downloadJob?.cancel()
    uiState = UpdateUiState.UpdateAvailable(version, changelog, url)
}
```

- [ ] **Step 3: 处理 NoUpdate 的 Toast**

```kotlin
// 在 AboutScreen composable 内,LaunchedEffect 监听 NoUpdate:
LaunchedEffect(uiState) {
    if (uiState is UpdateUiState.NoUpdate) {
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.about_update_latest, (uiState as UpdateUiState.NoUpdate).version)
            )
        }
        uiState = UpdateUiState.Idle
    }
}
```

- [ ] **Step 4: 重写按钮卡片**

替换现有「One-click update」InfoCard 内的 Button onClick:

```kotlin
Button(
    onClick = { checkUpdate() },
    enabled = uiState !is UpdateUiState.Checking,
    modifier = Modifier.fillMaxWidth()
) {
    Icon(Icons.Outlined.Download, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(stringResource(
        if (uiState is UpdateUiState.Checking) R.string.about_update_checking
        else R.string.about_update
    ))
}
```

- [ ] **Step 5: 调用 UpdateChangelogDialog**

在 Column 末尾(Spacer(height=32dp) 之前)加:

```kotlin
UpdateChangelogDialog(
    state = uiState,
    onDismiss = { uiState = UpdateUiState.Idle },
    onDownload = { version, changelog, url -> startDownload(version, changelog, url) },
    onCancelDownload = { version, changelog, url -> cancelDownload(version, changelog, url) },
    onRetry = { version, changelog, url -> startDownload(version, changelog, url) }
)
```

- [ ] **Step 6: Build**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
Expected: 编译失败 —— `UpdateChangelogDialog` 未定义(Task 5)。预期。

---

### Task 5: 新建 UpdateChangelogDialog

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/UpdateChangelogDialog.kt`

**Interfaces:**
- Consumes: `UpdateUiState`(Task 4 在 AboutScreen 里定义,需移到本文件或共享)、strings(Task 3)
- Produces: `UpdateChangelogDialog` composable

> **类型一致性:** `UpdateUiState` sealed class 定义放在 `UpdateChangelogDialog.kt` 顶层(package 级),AboutScreen 从这里 import,避免循环依赖。

- [ ] **Step 1: 把 UpdateUiState 移到本文件顶层**

```kotlin
package com.lingion.sleepy.ui.screen.mine

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class NoUpdate(val version: String) : UpdateUiState()
    data class UpdateAvailable(val version: String, val changelog: String, val url: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    object Installing : UpdateUiState()
    data class Failed(val message: String, val version: String = "", val changelog: String = "", val url: String = "") : UpdateUiState()
}
```

回 AboutScreen 删掉本地的 sealed class 定义,改 import。

- [ ] **Step 2: 写 UpdateChangelogDialog**

```kotlin
@Composable
fun UpdateChangelogDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: (String, String, String) -> Unit,
    onCancelDownload: (String, String, String) -> Unit,
    onRetry: (String, String, String) -> Unit
) {
    val colors = SleepyTheme.colors
    val v = (state as? UpdateUiState.UpdateAvailable)?.version
        ?: (state as? UpdateUiState.Downloading)?.let {
            (state as? UpdateUiState.UpdateAvailable)?.version
        } ?: ""
    when (state) {
        is UpdateUiState.UpdateAvailable, is UpdateUiState.Downloading,
        is UpdateUiState.Failed, is UpdateUiState.Installing -> {
            val s = state
            val version = when (s) {
                is UpdateUiState.UpdateAvailable -> s.version
                is UpdateUiState.Downloading -> ""
                is UpdateUiState.Failed -> s.version
                is UpdateUiState.Installing -> ""
                else -> ""
            }
            val changelog = when (s) {
                is UpdateUiState.UpdateAvailable -> s.changelog
                is UpdateUiState.Downloading -> ""
                is UpdateUiState.Failed -> s.changelog
                else -> ""
            }
            val url = when (s) {
                is UpdateUiState.UpdateAvailable -> s.url
                is UpdateUiState.Downloading -> ""
                is UpdateUiState.Failed -> s.url
                else -> ""
            }
            val progress = (state as? UpdateUiState.Downloading)?.progress ?: -1
            val isFailed = state is UpdateUiState.Failed
            val failMsg = (state as? UpdateUiState.Failed)?.message ?: ""

            AlertDialog(
                onDismissRequest = {
                    if (state !is UpdateUiState.Downloading) onDismiss()
                },
                containerColor = colors.surfaceContainer,
                titleContentColor = colors.onSurface,
                title = {
                    Text(
                        if (state is UpdateUiState.Installing)
                            stringResource(R.string.update_installing)
                        else
                            stringResource(R.string.update_found_title, version),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (isFailed) {
                            Text(
                                stringResource(R.string.update_download_failed, failMsg),
                                color = colors.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (progress >= 0) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.update_downloading, progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    when (state) {
                        is UpdateUiState.UpdateAvailable -> {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.update_cancel))
                            }
                            Button(onClick = { onDownload(version, changelog, url) }) {
                                Text(stringResource(R.string.update_download))
                            }
                        }
                        is UpdateUiState.Downloading -> {
                            Button(onClick = { onCancelDownload(version, changelog, url) }) {
                                Text(stringResource(R.string.update_cancel))
                            }
                        }
                        is UpdateUiState.Failed -> {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.update_cancel))
                            }
                            Button(onClick = { onRetry(version, changelog, url) }) {
                                Text(stringResource(R.string.update_retry))
                            }
                        }
                        is UpdateUiState.Installing -> { /* 无按钮,等系统安装器 */ }
                        else -> {}
                    }
                }
            )
        }
        else -> { /* Idle/Checking/NoUpdate 不弹窗 */ }
    }
}
```

- [ ] **Step 3: Build**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit(Task 2+3+4+5 一起)**

```bash
cd /Users/lingion_k/Desktop/sleepy
git add app/src/main/java/com/lingion/sleepy/util/UpdateManager.kt \
        app/src/main/java/com/lingion/sleepy/ui/screen/mine/AboutScreen.kt \
        app/src/main/java/com/lingion/sleepy/ui/screen/mine/UpdateChangelogDialog.kt \
        app/src/main/res/values*/strings.xml
git commit -m "feat(update): changelog 弹窗 + 可取消下载 + 状态机重写"
```

---

### Task 6: MainActivity 启动清理

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/MainActivity.kt`

- [ ] **Step 1: 在 onCreate 调 cleanOldApk**

在 `override fun onCreate` 的 `super.onCreate(savedInstanceState)` 之后加一行:

```kotlin
com.lingion.sleepy.util.UpdateManager.cleanOldApk(this)
```

- [ ] **Step 2: Build + Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease
git add app/src/main/java/com/lingion/sleepy/MainActivity.kt
git commit -m "feat(update): 启动时清理旧下载 APK"
```

---

### Task 7: i18n 存量补全(en/es/ja + zh-rCN/zh-rTW)

**Files:**
- Modify: `app/src/main/res/values-en/strings.xml`(~96 key)
- Modify: `app/src/main/res/values-es/strings.xml`(~96 key)
- Modify: `app/src/main/res/values-ja/strings.xml`(~96 key)
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`(~106 key)
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`(~96 key)

**Interfaces:** 无新代码,纯翻译填值。从默认 values/strings.xml 取中文原文翻译。

- [ ] **Step 1: 提取缺失 key + 中文原文**

Run:
```bash
cd /Users/lingion_k/Desktop/sleepy/app/src/main/res
for d in values-en values-es values-ja values-zh-rCN values-zh-rTW; do
  comm -23 /tmp/keys_default.txt /tmp/keys_$d.txt | while read key; do
    grep "name=\"$key\"" values/strings.xml
  done > /tmp/missing_$d.txt
  echo "$d: $(wc -l < /tmp/missing_$d.txt) key 待译"
done
```

- [ ] **Step 2: 逐语言翻译填入**

对每个 locale,读取 `/tmp/missing_$d.txt` 的中文原文,翻译后追加到对应 `strings.xml`(在 `</resources>` 前)。术语对照:
- en: course/table/reminder/export/schedule/version/download
- ja: 科目/時間割/リマインダー/エクスポート/曜日/時限/バージョン/ダウンロード
- es: curso/horario/recordatorio/exportar/versión/descargar

- [ ] **Step 3: 验证 key 数对齐**

Run:
```bash
cd /Users/lingion_k/Desktop/sleepy/app/src/main/res
for d in values values-en values-es values-ja values-zh-rCN values-zh-rTW; do
  cnt=$(grep -c '<string name=' "$d/strings.xml")
  echo "$d: $cnt keys"
done
```
Expected: 全部 locale 的 key 数等于默认 values 的 key 数。

- [ ] **Step 4: Build**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy
git add app/src/main/res/values*/strings.xml
git commit -m "i18n: 补全 en/es/ja/zh-rCN/zh-rTW 缺失 key(~96/语言)"
```

---

### Task 8: 全量验证

- [ ] **Step 1: 跑全部单测**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew test`
Expected: 全绿,含 Task 1 的 5 个新测试。

- [ ] **Step 2: Release build**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL,产出 3 个 APK。

- [ ] **Step 3: 验证 APK 版本号**

Run: `~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/release/app-arm64-v8a-release.apk | grep versionName`
Expected: versionName 仍是当前 1.0.31(本功能未发版,版本号不变)。

- [ ] **Step 4: key 对齐终检**

Run 上面的 key 数检查脚本,确认六语言 key 数一致。

---

## Self-Review

**1. Spec coverage:**
- ✅ 点更新先拉 changelog 不下载 → Task 2 fetchUpdateInfo
- ✅ 弹窗展示全文 → Task 5 AlertDialog + verticalScroll
- ✅ 取消/下载按钮 → Task 5 confirmButton
- ✅ 进度条可取消 → Task 2 downloadApk + Task 4 cancelDownload
- ✅ 下完拉起安装器 → Task 4 onSuccess install()
- ✅ 已是最新 Toast → Task 4 NoUpdate + LaunchedEffect
- ✅ 启动清 APK → Task 6 cleanOldApk
- ✅ 五语言 key 齐全 → Task 3(新增)+ Task 7(存量)

**2. Placeholder scan:** Task 7 Step 2 说"翻译填入"但没给全部 96 条译文——这是有意为之:翻译量大且需读原文上下文逐条译,在实现时基于实际缺失 key 列表做,不在 plan 里写死 96 条占位译文(plan 该是结构,不是翻译稿)。其余步骤都有具体代码。

**3. Type consistency:** `UpdateUiState` 在 Task 5 定义为顶层,Task 4 import 使用;`UpdateInfo` Task 1 定义,Task 2/4 使用;`onProgress: (Int) -> Unit` Task 2 定义 Task 4 调用——签名一致。
