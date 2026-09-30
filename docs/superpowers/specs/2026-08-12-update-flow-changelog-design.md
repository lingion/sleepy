# Spec: 关于页更新流程改造 + i18n 补全

## Objective

改造 Sleepy Android App「关于」页的获取更新流程:从现在的「点击即检查+下载+安装一步到底」,改成「点击 → 拉取 changelog → 弹窗展示最新版本更新记录 → 用户选下载或取消 → 下载(可取消)→ 安装 → 下次启动清安装包」。

同时补全 App 存量的 i18n 缺口:values-en / values-es / values-ja 各缺约 95-96 个 key,导致这三个语言的用户在 about / export / reminder / notif / jw / school 等界面看到中文 fallback。

## 用户故事

1. 用户点「获取更新」→ 看到新版本的完整更新日志(中英双语全文),决定值不值得更新
2. 点「下载」→ 弹窗内进度条,可随时取消
3. 下完自动拉起系统安装器
4. 已是最新版本 → Toast 提示,不弹窗
5. 下次启动 App 自动清理下载缓存的 APK
6. 英语/西语/日语用户不再在任何界面看到中文残留

## 成功标准

- [ ] 点「获取更新」先请求 API 获取 changelog,**不下载 APK**
- [ ] 有新版本时弹窗展示 release body 全文(可滚动)
- [ ] 弹窗底部「取消」+「下载」两按钮
- [ ] 点下载 → 弹窗内进度条 + 百分比,点取消中断并回到弹窗
- [ ] 下完 → 自动调系统安装器 → 关弹窗
- [ ] 已是最新 → Toast「当前已是最新版本 vX」,不弹窗
- [ ] App 启动时清掉 cacheDir 中 `sleepy-update-*.apk`
- [ ] values-en / values-es / values-ja 的 key 数与默认 values 一致(363 + 本次新增)
- [ ] 五语言 build 通过

## Commands

```
Build: cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleRelease
Test:  cd /Users/lingion_k/Desktop/sleepy && ./gradlew test
```

## 技术方案:方案 A

### UpdateManager 改造

拆 `downloadLatest()` 为三个公开方法 + 保留 `install()`:

```kotlin
data class UpdateInfo(
    val version: String,          // 远端版本号 e.g. "1.0.31"
    val changelog: String,        // release body 全文(可能空)
    val downloadUrl: String,      // APK 直链
    val isUpdateAvailable: Boolean // compare(remote, current) > 0 || forceUpdate
)

// 1. 只检查,不下载
suspend fun fetchUpdateInfo(context: Context): UpdateInfo

// 2. 只下载,带进度回调(0-100),协程可取消
suspend fun downloadApk(
    context: Context, info: UpdateInfo,
    onProgress: (Int) -> Unit
): File

// 3. 启动时清理旧 APK
fun cleanOldApk(context: Context)
```

**fetchUpdateInfo 逻辑**:
- 请求 GitHub API `releases/latest`,取 `tag_name`(去 v 前缀)、`body`(changelog)、assets 里对应 ABI 的 `browser_download_url`
- GitHub 不通 → 回退镜像 `MIRROR_RELEASE`,正则取 tag,拼 `MIRROR_PREFIX + tag + assetName`;镜像下 body 取不到则置空串
- `isUpdateAvailable`: `VersionUtils.compare(remote, BuildConfig.VERSION_NAME) > 0`,或 body 含 `SLEEPY_FORCE_UPDATE=true` 时恒 true
- 删除旧的 `NoUpdateAvailableException` 机制(fetch 只返回信息,不抛异常;是否更新由调用方根据 isUpdateAvailable 判断)

**downloadApk 逻辑**:
- target = `File(context.cacheDir, "sleepy-update-$assetName")`
- HTTP `Content-Length` 头取总字节数;循环读 buffer(8KB),累计 downloaded,`onProgress(downloaded * 100 / total)`
- `withContext(Dispatchers.IO)` + 协程 `isActive` 检查;cancel 时 finally 删半截文件,抛 CancellationException

**cleanOldApk 逻辑**:
- `cacheDir.listFiles { it.name.startsWith("sleepy-update-") }?.forEach { it.delete() }`
- 在 `MainActivity.onCreate` 调一次

### AboutScreen 状态机 + 交互

```kotlin
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class NoUpdate(val version: String) : UpdateUiState()
    data class UpdateAvailable(val version: String, val changelog: String, val url: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    object Installing : UpdateUiState()
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateUiState()
}
```

**按钮点击** (Idle → Checking): 调 `fetchUpdateInfo` →
- `isUpdateAvailable=false` → `NoUpdate` → Toast → Idle
- `isUpdateAvailable=true` → `UpdateAvailable` → 弹窗

**UpdateChangelogDialog** (新 Composable):
- UpdateAvailable / Downloading / Failed 时显示
- 标题:`发现新版本 vX` (update_found_title)
- 正文:changelog,`verticalScroll`(中英双语全文可能很长)
- 底部分状态:
  - UpdateAvailable: 「取消」(文字) | 「下载」(填充)
  - Downloading: `LinearProgressIndicator(progress)` + `下载中 N%` + 「取消」
  - Failed: 错误信息 + 「取消」| 「重试」
- 取消(UpdateAvailable): 关弹窗 → Idle
- 下载: 启动协程 `job = scope.launch { downloadApk(...) }`,DownloadAvailable → Downloading
- 取消(Downloading): `job.cancel()` → 回 UpdateAvailable(changelog 保留)
- 下完: Installing → `install()` → 关弹窗 → Idle

**协程管理**: 用 `remember { mutableStateOf<Job?>(null) }` 持有下载协程,取消时 `job?.cancel()`。

### 新增 strings (5 语言)

| key | zh (默认) | en | es | ja | zh-rTW |
|-----|-----------|----|----|----|--------|
| about_update_latest | 当前已是最新版本 v%1$s | You're on the latest version v%1$s | Ya tienes la última versión v%1$s | 最新バージョン v%1$s です | 目前已是最新版本 v%1$s |
| update_found_title | 发现新版本 v%1$s | New version v%1$s available | Nueva versión v%1$s disponible | 新しいバージョン v%1$s があります | 發現新版本 v%1$s |
| update_download | 下载 | Download | Descargar | ダウンロード | 下載 |
| update_cancel | 取消 | Cancel | Cancelar | キャンセル | 取消 |
| update_downloading | 下载中 %1$d%% | Downloading %1$d%% | Descargando %1$d%% | ダウンロード中 %1$d%% | 下載中 %1$d%% |
| update_installing | 下载完成,正在打开安装器 | Download complete, opening installer | Descarga completa, abriendo instalador | ダウンロード完了、インストーラーを起動中 | 下載完成,正在開啟安裝器 |
| update_retry | 重试 | Retry | Reintentar | 再試行 | 重試 |
| update_download_failed | 下载失败:%1$s | Download failed: %1$s | Error de descarga: %1$s | ダウンロード失敗: %1$s | 下載失敗:%1$s |

复用已有:`about_update_checking`。

### i18n 存量补全

补 values-en / values-es / values-ja 各 ~96 个缺失 key(about/export/reminder/notif/jw/school/action 等区)。默认 values 是中文,这三个语言缺 key 时会 fallback 到中文——用户会看到中英混杂。zh-rCN / zh-rTW 缺 key 时 fallback 到默认中文,用户无感,本次一并补齐保持 key 完整性。

翻译由我直接做,匹配各语言现有术语:
- en: course/table/reminder/export 一致用词
- ja: 「曜日/時限/科目/時間割/リマインダー」
- es: curso/horario/recordatorio/exportar

## 文件清单

| 文件 | 改动 |
|------|------|
| util/UpdateManager.kt | 重构:拆 fetch/download/clean 三方法 |
| ui/screen/mine/AboutScreen.kt | 状态机 + 弹窗触发 |
| ui/screen/mine/UpdateChangelogDialog.kt | 新文件:弹窗 Composable |
| MainActivity.kt | onCreate 调 cleanOldApk |
| res/values/strings.xml | +8 新 key |
| res/values-en/strings.xml | +8 新 key + ~96 存量补全 |
| res/values-es/strings.xml | +8 新 key + ~96 存量补全 |
| res/values-ja/strings.xml | +8 新 key + ~96 存量补全 |
| res/values-zh-rCN/strings.xml | +8 新 key + 存量补全 |
| res/values-zh-rTW/strings.xml | +8 新 key + ~96 存量补全 |

## 边界

- **Always**: build 通过、五语言 key 齐全、不破坏现有 install() 逻辑
- **Ask first**: 新增依赖(本次不需要)
- **Never**: 删 ForceUpdate 机制、改 signingConfig
