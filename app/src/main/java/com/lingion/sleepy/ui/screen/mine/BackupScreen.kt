package com.lingion.sleepy.ui.screen.mine

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.R
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.backup.WebDavClient
import com.lingion.sleepy.data.backup.WebDavConfig
import com.lingion.sleepy.data.backup.WebDavCredentialStore
import com.lingion.sleepy.data.backup.WebDavFailure
import com.lingion.sleepy.data.backup.WebDavFailureKind
import com.lingion.sleepy.data.backup.WebDavRemoteBackup
import com.lingion.sleepy.data.migration.MigrationExecutor
import com.lingion.sleepy.data.migration.MigrationModule
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Complete backups preserve database, preferences and per-widget configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val widgetScope = rememberCoroutineScope()
    fun refreshWidgets() {
        widgetScope.launch { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context) }
    }
    // Reuse the existing full-backup format and restore confirmation.
    val migrationScope = rememberCoroutineScope()
    val migrationDb = remember { AppDatabase.get(context) }
    val migrationPrefsStore = remember {
        MigrationExecutor.PrefsStore { name -> context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE) }
    }
    var migrationBusy by remember { mutableStateOf(false) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    val webDavClient = remember { WebDavClient() }
    var webDavSettings by remember { mutableStateOf(WebDavCredentialStore.load(context)) }
    var showWebDavSettings by remember { mutableStateOf(false) }
    var webDavBusy by remember { mutableStateOf(false) }
    var showWebDavUploadConfirm by remember { mutableStateOf(false) }
    var webDavDialogError by remember { mutableStateOf<String?>(null) }
    var remoteBackups by remember { mutableStateOf<List<WebDavRemoteBackup>>(emptyList()) }
    var showRemoteBackups by remember { mutableStateOf(false) }

    fun showWebDavError(error: Throwable) {
        val message = when (error) {
            is WebDavFailure -> when (error.kind) {
                WebDavFailureKind.INVALID_CONFIGURATION -> context.getString(R.string.webdav_error_configuration)
                WebDavFailureKind.HTTPS_REQUIRED -> context.getString(R.string.webdav_error_https)
                WebDavFailureKind.AUTHENTICATION -> context.getString(R.string.webdav_error_authentication)
                WebDavFailureKind.FORBIDDEN -> context.getString(R.string.webdav_error_forbidden)
                WebDavFailureKind.HTTP_STATUS -> context.getString(
                    R.string.webdav_error_http_status,
                    error.statusCode ?: 0,
                )
                WebDavFailureKind.FILE_TOO_LARGE -> context.getString(R.string.webdav_error_too_large)
                WebDavFailureKind.INVALID_RESPONSE -> context.getString(R.string.webdav_error_response)
                WebDavFailureKind.NETWORK -> context.getString(R.string.webdav_error_network)
            }
            else -> context.getString(R.string.webdav_error_generic)
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    suspend fun exportCompleteBackup(): ByteArray = MigrationExecutor.export(
        setOf(MigrationModule.DATABASE, MigrationModule.PREFERENCES, MigrationModule.WIDGETS),
        migrationPrefsStore,
        migrationDb.courseDao(), migrationDb.timeTableDao(),
        migrationDb.periodTableDao(), migrationDb.importDraftDao(),
    ).bytes

    fun runMigrationImport(mode: MigrationExecutor.ImportMode) {
        val bytes = pendingImportBytes ?: return
        if (webDavBusy) return
        migrationBusy = true
        migrationScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    MigrationExecutor.import(
                        bytes, mode, migrationPrefsStore,
                        migrationDb.courseDao(), migrationDb.timeTableDao(),
                        migrationDb.periodTableDao(), migrationDb.importDraftDao(),
                    )
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_migration_import_done, report.counts.timeTables, report.counts.courses),
                    Toast.LENGTH_LONG
                ).show()
                // 偏好与小组件配置可能被替换, 迁移后统一刷新小组件
                refreshWidgets()
            } catch (e: Exception) {
                Log.e("SleepyMigration", "import failed", e)
                Toast.makeText(context, R.string.settings_migration_import_fail, Toast.LENGTH_LONG).show()
            } finally {
                migrationBusy = false
                pendingImportBytes = null
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri == null || bytes == null) {
            migrationBusy = false
            return@rememberLauncherForActivityResult
        }
        migrationBusy = true
        migrationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("output stream unavailable")
                }
                Toast.makeText(context, R.string.settings_migration_export_ok, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SleepyMigration", "export failed", e)
                Toast.makeText(context, R.string.settings_migration_export_fail, Toast.LENGTH_LONG).show()
            } finally {
                migrationBusy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        migrationScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                Toast.makeText(context, R.string.settings_migration_import_fail, Toast.LENGTH_LONG).show()
            } else {
                pendingImportBytes = bytes
                showImportDialog = true
            }
        }
    }

    fun onMigrationExportClick() {
        if (migrationBusy || webDavBusy) return
        migrationBusy = true
        migrationScope.launch {
            try {
                pendingExportBytes = withContext(Dispatchers.IO) { exportCompleteBackup() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                withContext(Dispatchers.Main) { exportLauncher.launch("sleepy-backup-$stamp.sleepybackup") }
            } catch (e: Exception) {
                Log.e("SleepyMigration", "collect failed", e)
                Toast.makeText(context, R.string.settings_migration_export_fail, Toast.LENGTH_LONG).show()
                migrationBusy = false
            }
        }
    }

    fun configureWebDav(config: WebDavConfig, passwordForTest: String, passwordToSave: String?) {
        if (migrationBusy || webDavBusy) return
        webDavBusy = true
        webDavDialogError = null
        migrationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    webDavClient.testConnection(config, passwordForTest)
                    WebDavCredentialStore.save(context, config, passwordToSave)
                }.also { saved -> webDavSettings = saved }
                showWebDavSettings = false
                Toast.makeText(context, R.string.webdav_config_saved, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                webDavDialogError = when (error) {
                    is WebDavFailure -> when (error.kind) {
                        WebDavFailureKind.INVALID_CONFIGURATION -> context.getString(R.string.webdav_error_configuration)
                        WebDavFailureKind.HTTPS_REQUIRED -> context.getString(R.string.webdav_error_https)
                        WebDavFailureKind.AUTHENTICATION -> context.getString(R.string.webdav_error_authentication)
                        WebDavFailureKind.FORBIDDEN -> context.getString(R.string.webdav_error_forbidden)
                        WebDavFailureKind.HTTP_STATUS -> context.getString(R.string.webdav_error_http_status, error.statusCode ?: 0)
                        WebDavFailureKind.FILE_TOO_LARGE -> context.getString(R.string.webdav_error_too_large)
                        WebDavFailureKind.INVALID_RESPONSE -> context.getString(R.string.webdav_error_response)
                        WebDavFailureKind.NETWORK -> context.getString(R.string.webdav_error_network)
                    }
                    else -> context.getString(R.string.webdav_error_generic)
                }
            } finally {
                webDavBusy = false
            }
        }
    }

    fun uploadToWebDav() {
        val settings = webDavSettings ?: return
        if (migrationBusy || webDavBusy) return
        webDavBusy = true
        migrationScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { exportCompleteBackup() }
                val fileName = "sleepy-backup-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())}.sleepybackup"
                withContext(Dispatchers.IO) { webDavClient.upload(settings.config, settings.password, fileName, bytes) }
                Toast.makeText(context, context.getString(R.string.webdav_upload_done, fileName), Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                showWebDavError(error)
            } finally {
                webDavBusy = false
            }
        }
    }

    fun loadRemoteBackups() {
        val settings = webDavSettings ?: return
        if (migrationBusy || webDavBusy) return
        webDavBusy = true
        migrationScope.launch {
            try {
                remoteBackups = withContext(Dispatchers.IO) {
                    webDavClient.listBackups(settings.config, settings.password)
                }
                showRemoteBackups = true
            } catch (error: Exception) {
                showWebDavError(error)
            } finally {
                webDavBusy = false
            }
        }
    }

    fun downloadRemoteBackup(backup: WebDavRemoteBackup) {
        val settings = webDavSettings ?: return
        if (migrationBusy || webDavBusy) return
        webDavBusy = true
        showRemoteBackups = false
        migrationScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    webDavClient.download(settings.config, settings.password, backup.fileName)
                }
                pendingImportBytes = bytes
                showImportDialog = true
            } catch (error: Exception) {
                showWebDavError(error)
            } finally {
                webDavBusy = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_backup)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.dbgTag("backup_back", role = "button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_migration_sub),
                    style = SettingsLayout.descriptionStyle,
                    color = colors.onSurfaceVariant
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .noRippleClickable { onMigrationExportClick() }
                            .dbgTag("general_migration_export", text = stringResource(R.string.settings_backup_create), role = "button")
                            .alpha(if (migrationBusy || webDavBusy) 0.5f else 1f)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
                            Text(
                                text = stringResource(R.string.settings_backup_create),
                                style = SettingsLayout.headingStyle,
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_migration_export_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .noRippleClickable {
                                if (!migrationBusy && !webDavBusy) importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                            .dbgTag("general_migration_import", text = stringResource(R.string.settings_backup_restore), role = "button")
                            .alpha(if (migrationBusy || webDavBusy) 0.5f else 1f)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
                            Text(
                                text = stringResource(R.string.settings_backup_restore),
                                style = SettingsLayout.headingStyle,
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_migration_import_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                WebDavBackupCard(
                    settings = webDavSettings,
                    busy = migrationBusy || webDavBusy,
                    onConfigure = { webDavDialogError = null; showWebDavSettings = true },
                    onUpload = { showWebDavUploadConfirm = true },
                    onRestore = ::loadRemoteBackups,
                )
            }
        }
    }

    if (showWebDavUploadConfirm) {
        val settings = webDavSettings
        if (settings != null) {
            WebDavUploadConfirmationDialog(
                serverUrl = settings.config.serverUrl,
                directory = settings.config.directory,
                onDismiss = { showWebDavUploadConfirm = false },
                onConfirm = {
                    showWebDavUploadConfirm = false
                    uploadToWebDav()
                },
            )
        } else {
            showWebDavUploadConfirm = false
        }
    }

    if (showWebDavSettings) {
        WebDavSettingsDialog(
            initial = webDavSettings,
            busy = webDavBusy,
            errorMessage = webDavDialogError,
            onDismiss = { showWebDavSettings = false },
            onTestAndSave = ::configureWebDav,
            onForget = {
                if (!migrationBusy && !webDavBusy) {
                    webDavBusy = true
                    migrationScope.launch {
                        try {
                            withContext(Dispatchers.IO) { WebDavCredentialStore.clear(context) }
                            webDavSettings = null
                            showWebDavSettings = false
                            Toast.makeText(context, R.string.webdav_config_forgotten, Toast.LENGTH_SHORT).show()
                        } catch (error: Exception) {
                            showWebDavError(error)
                        } finally {
                            webDavBusy = false
                        }
                    }
                }
            },
        )
    }

    if (showRemoteBackups) {
        WebDavRemoteBackupDialog(
            backups = remoteBackups,
            busy = webDavBusy,
            onDismiss = { showRemoteBackups = false },
            onSelect = ::downloadRemoteBackup,
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; pendingImportBytes = null },
            title = { Text(stringResource(R.string.settings_migration_import_dialog_title)) },
            text = { Text(stringResource(R.string.settings_migration_import_dialog_text)) },
            confirmButton = {
                Row {
                    TextButton(onClick = { showImportDialog = false; runMigrationImport(MigrationExecutor.ImportMode.OVERWRITE) },
                        modifier = Modifier.dbgTag("general_import_overwrite", role = "button")) {
                        Text(stringResource(R.string.settings_migration_import_overwrite))
                    }
                    TextButton(onClick = { showImportDialog = false; runMigrationImport(MigrationExecutor.ImportMode.MERGE) },
                        modifier = Modifier.dbgTag("general_import_merge", role = "button")) {
                        Text(stringResource(R.string.settings_migration_import_merge))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; pendingImportBytes = null },
                    modifier = Modifier.dbgTag("general_import_cancel", role = "button")) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
