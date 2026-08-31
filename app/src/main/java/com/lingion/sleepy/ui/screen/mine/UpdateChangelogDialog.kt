package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import dev.jeziellago.compose.markdowntext.MarkdownText

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class NoUpdate(val version: String) : UpdateUiState()
    data class UpdateAvailable(val version: String, val changelog: String, val url: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    object Installing : UpdateUiState()
    data class Failed(val message: String, val version: String = "", val changelog: String = "", val url: String = "", val isCheckFailure: Boolean = false) : UpdateUiState()
}

@Composable
fun UpdateChangelogDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: (String, String, String) -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: (String, String, String) -> Unit
) {
    val colors = SleepyTheme.colors
    when (state) {
        is UpdateUiState.UpdateAvailable, is UpdateUiState.Downloading,
        is UpdateUiState.Failed, is UpdateUiState.Installing -> {
            val version = when (state) {
                is UpdateUiState.UpdateAvailable -> state.version
                is UpdateUiState.Downloading -> ""
                is UpdateUiState.Failed -> state.version
                is UpdateUiState.Installing -> ""
                else -> ""
            }
            val changelog = when (state) {
                is UpdateUiState.UpdateAvailable -> state.changelog
                is UpdateUiState.Downloading -> ""
                is UpdateUiState.Failed -> state.changelog
                else -> ""
            }
            val url = when (state) {
                is UpdateUiState.UpdateAvailable -> state.url
                is UpdateUiState.Downloading -> ""
                is UpdateUiState.Failed -> state.url
                else -> ""
            }
            val progress = (state as? UpdateUiState.Downloading)?.progress ?: -1
            val isFailed = state is UpdateUiState.Failed
            val failMsg = (state as? UpdateUiState.Failed)?.message ?: ""

            AlertDialog(
                onDismissRequest = {
                    if (state !is UpdateUiState.Downloading) onDismiss()
                },
                titleContentColor = colors.onSurface,
                title = {
                    Text(
                        when (state) {
                            is UpdateUiState.Installing -> stringResource(R.string.update_installing)
                            is UpdateUiState.Downloading -> stringResource(R.string.update_downloading, (state as UpdateUiState.Downloading).progress)
                            else -> stringResource(R.string.update_found_title, version)
                        },
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
                        if (changelog.isNotBlank()) {
                            // MarkdownText 渲染 release body 的 ## 标题 / 列表 / 加粗 / 链接
                            MarkdownText(
                                markdown = changelog,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.onSurfaceVariant
                                ),
                                linkColor = colors.primary,
                            )
                        }
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
                            Button(onClick = { onCancelDownload() }) {
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
