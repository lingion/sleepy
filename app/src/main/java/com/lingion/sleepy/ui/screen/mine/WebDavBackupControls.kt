package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.backup.SavedWebDavSettings
import com.lingion.sleepy.data.backup.WebDavConfig
import com.lingion.sleepy.data.backup.WebDavRemoteBackup
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import java.util.Locale

@Composable
internal fun WebDavBackupCard(
    settings: SavedWebDavSettings?,
    busy: Boolean,
    onConfigure: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .noRippleClickable(onConfigure)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)
            ) {
                Text(
                    text = stringResource(R.string.webdav_section_title),
                    style = SettingsLayout.headingStyle,
                    color = colors.onSurface,
                )
                Text(
                    text = if (settings == null) stringResource(R.string.webdav_section_sub)
                    else stringResource(R.string.webdav_configured_sub, settings.config.serverUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        if (settings != null) {
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (settings.passwordNeedsReentry) {
                    Text(
                        stringResource(R.string.webdav_password_reentry_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
                Button(onClick = onUpload, enabled = !busy && !settings.passwordNeedsReentry, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.webdav_upload))
                    if (busy) {
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.webdav_working))
                    }
                }
                TextButton(onClick = onRestore, enabled = !busy && !settings.passwordNeedsReentry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.webdav_restore))
                }
            }
        }
    }
}

@Composable
internal fun WebDavUploadConfirmationDialog(
    serverUrl: String,
    directory: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webdav_upload_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.webdav_upload_confirm_target, serverUrl, directory))
                Text(stringResource(R.string.webdav_privacy_notice), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.webdav_confirm_upload)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun WebDavSettingsDialog(
    initial: SavedWebDavSettings?,
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onTestAndSave: (WebDavConfig, passwordForTest: String, passwordToSave: String?) -> Unit,
    onForget: () -> Unit,
) {
    var serverUrl by remember(initial?.config?.serverUrl) { mutableStateOf(initial?.config?.serverUrl.orEmpty()) }
    var directory by remember(initial?.config?.directory) { mutableStateOf(initial?.config?.directory ?: "Sleepy") }
    var username by remember(initial?.config?.username) { mutableStateOf(initial?.config?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgetConfirmation by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.webdav_config_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.webdav_server_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.webdav_directory)) },
                    supportingText = { Text(stringResource(R.string.webdav_directory_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.webdav_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.webdav_password)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (initial?.hasStoredPassword == true) R.string.webdav_password_keep_hint
                                else R.string.webdav_password_optional_hint
                            )
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.webdav_password_hide else R.string.webdav_password_show
                                ),
                            )
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.webdav_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.webdav_directory_create_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                if (initial?.passwordNeedsReentry == true) {
                    Text(
                        text = stringResource(R.string.webdav_password_reentry_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
                if (initial != null) {
                    TextButton(onClick = { showForgetConfirmation = true }, enabled = !busy) {
                        Text(stringResource(R.string.webdav_forget_configuration), color = colors.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = WebDavConfig(serverUrl, directory, username)
                    val passwordForTest = when {
                        password.isNotEmpty() -> password
                        initial?.hasStoredPassword == true -> initial.password
                        else -> ""
                    }
                    val passwordToSave = when {
                        password.isNotEmpty() -> password
                        initial?.hasStoredPassword == true -> null
                        else -> ""
                    }
                    onTestAndSave(config, passwordForTest, passwordToSave)
                },
                enabled = !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.webdav_testing))
                } else {
                    Text(stringResource(R.string.webdav_test_and_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showForgetConfirmation) {
        AlertDialog(
            onDismissRequest = { showForgetConfirmation = false },
            title = { Text(stringResource(R.string.webdav_forget_title)) },
            text = { Text(stringResource(R.string.webdav_forget_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showForgetConfirmation = false
                    onForget()
                }) { Text(stringResource(R.string.webdav_forget_configuration), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showForgetConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
internal fun WebDavRemoteBackupDialog(
    backups: List<WebDavRemoteBackup>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WebDavRemoteBackup) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.webdav_remote_title)) },
        text = {
            if (backups.isEmpty()) {
                Text(stringResource(R.string.webdav_remote_empty))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(backups, key = WebDavRemoteBackup::fileName) { backup ->
                        TextButton(
                            onClick = { onSelect(backup) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(backup.fileName, color = colors.onSurface)
                                val modified = backup.lastModified.ifBlank {
                                    stringResource(R.string.webdav_date_unknown)
                                }
                                Text(
                                    stringResource(
                                        R.string.webdav_remote_file_detail,
                                        modified,
                                        formatBackupSize(backup.sizeBytes),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun formatBackupSize(sizeBytes: Long): String = when {
    sizeBytes < 1024 -> "$sizeBytes B"
    sizeBytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
}
