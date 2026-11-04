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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.R
import com.lingion.sleepy.data.AppDatabase
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

    fun runMigrationImport(mode: MigrationExecutor.ImportMode) {
        val bytes = pendingImportBytes ?: return
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
        if (migrationBusy) return
        migrationBusy = true
        migrationScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    MigrationExecutor.export(
                        setOf(MigrationModule.DATABASE, MigrationModule.PREFERENCES, MigrationModule.WIDGETS),
                        migrationPrefsStore,
                        migrationDb.courseDao(), migrationDb.timeTableDao(),
                        migrationDb.periodTableDao(), migrationDb.importDraftDao(),
                    )
                }
                pendingExportBytes = result.bytes
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                withContext(Dispatchers.Main) { exportLauncher.launch("sleepy-backup-$stamp.sleepybackup") }
            } catch (e: Exception) {
                Log.e("SleepyMigration", "collect failed", e)
                Toast.makeText(context, R.string.settings_migration_export_fail, Toast.LENGTH_LONG).show()
                migrationBusy = false
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_migration_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
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
                            .alpha(if (migrationBusy) 0.5f else 1f)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_backup_create),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                                if (!migrationBusy) importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                            .dbgTag("general_migration_import", text = stringResource(R.string.settings_backup_restore), role = "button")
                            .alpha(if (migrationBusy) 0.5f else 1f)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_backup_restore),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
        }
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
