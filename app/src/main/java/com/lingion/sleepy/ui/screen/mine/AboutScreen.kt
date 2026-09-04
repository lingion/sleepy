package com.lingion.sleepy.ui.screen.mine

import android.content.Intent
import android.net.Uri
import android.os.Build as AndroidBuild
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.FeedbackComposer
import com.lingion.sleepy.util.UpdateInfo
import com.lingion.sleepy.util.UpdateManager
import com.lingion.sleepy.util.UpdateNotifier
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicense: () -> Unit = {}) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val updateAvailable by UpdateNotifier.updateAvailable.collectAsState()
    var updateCheckEnabled by remember { mutableStateOf(AppPrefs.isUpdateCheckEnabled(context)) }

    fun diagnostic() = FeedbackComposer.Diagnostic(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        androidVersion = AndroidBuild.VERSION.RELEASE ?: AndroidBuild.VERSION.SDK_INT.toString(),
        brand = AndroidBuild.BRAND,
        model = AndroidBuild.MODEL,
        resolution = "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}",
        locale = context.resources.configuration.locales[0].toLanguageTag(),
        isDebug = BuildConfig.DEBUG,
    )

    fun openGitHubFeedback() {
        val uri = FeedbackComposer.githubIssueUrl(
            title = "[Sleepy] ",
            body = "请描述你遇到的问题或建议：",
            diag = diagnostic(),
            template = "bug_report.yml",
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    fun openEmailFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(FeedbackComposer.mailtoUri(
            subject = context.getString(R.string.about_feedback_email_subject),
            body = context.getString(R.string.about_feedback_email_body),
            diag = diagnostic(),
        )))
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.about_feedback_no_mail_app)) }
        }
    }

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
                .onFailure { uiState = UpdateUiState.Failed(it.message ?: context.getString(R.string.error_unknown), isCheckFailure = true) }
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
                        e.message ?: context.getString(R.string.error_unknown), version, changelog, url
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    LaunchedEffect(uiState) {
        val current = uiState
        if (current is UpdateUiState.NoUpdate) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.about_update_latest, current.version)
                )
            }
            uiState = UpdateUiState.Idle
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    // 对齐其余 mine 子页（AllTables/Export/Reminder 等统一 onBackground）
                    titleContentColor = colors.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = colors.background
    ) { innerPadding ->
        // 远端有新版可用时: 整页底色轻刷主题色(alpha 5%) + 顶部 banner 提示, 关 Toggle 后两者一起消失
        val highlightColor = colors.primary.copy(alpha = 0.05f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (updateAvailable != null) highlightColor else colors.background)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部 banner: 仅 updateAvailable != null 时渲染, 点击跳 Releases tag 页
            if (updateAvailable != null) {
                Spacer(modifier = Modifier.height(12.dp))
                UpdateBanner(
                    version = updateAvailable!!.version,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://gh.qdp.qzz.io/lingion/sleepy/releases/tag/v${updateAvailable!!.version}"))
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))

            // App name + icon
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Version info card
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.about_version),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_version_detail, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // One-click update
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.about_update),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(R.string.about_update_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
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
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Author card
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_author),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_author_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                    // IconButton 而非裸 Icon+clickable — 裸 20dp 图标的涟漪半径过小且无 48dp 最小触达区
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lingion")))
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Source code card
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_source),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_source_url),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lingion/sleepy")))
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feedback card
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_feedback),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_feedback_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { openGitHubFeedback() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.about_feedback_github),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { openEmailFeedback() }) {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = stringResource(R.string.about_feedback_email),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // License 入口行(v1.0.46 用户令): 长卡拆独立二级页, 这里只留入口
            InfoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable(onOpenLicense)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_license_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_license_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 自动检查更新 Toggle(issue#无编号): 关闭后冷启动不拉远端, 高亮也立即消失
            InfoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_update_check),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_update_check_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = updateCheckEnabled,
                        onCheckedChange = { v ->
                            updateCheckEnabled = v
                            AppPrefs.setUpdateCheckEnabled(context, v)
                            if (!v) UpdateNotifier.clearCache()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.outline,
                            uncheckedTrackColor = colors.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    UpdateChangelogDialog(
        state = uiState,
        onDismiss = { uiState = UpdateUiState.Idle },
        onDownload = { version, changelog, url -> startDownload(version, changelog, url) },
        onCancelDownload = { cancelDownload() },
        onRetry = { version, changelog, url ->
            val failed = uiState as? UpdateUiState.Failed
            if (failed?.isCheckFailure == true) checkUpdate()
            else startDownload(version, changelog, url)
        }
    )
}


@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(20.dp)
    ) {
        content()
    }
}

/** 冷启动检查到新版可用时在「关于」顶部展示的横幅, 点击跳 Releases tag 页 */
@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.primary.copy(alpha = 0.12f))
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.NewReleases,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.about_update_available, "v$version"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}
