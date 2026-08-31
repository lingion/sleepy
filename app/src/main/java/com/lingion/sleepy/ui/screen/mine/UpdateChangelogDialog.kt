package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.MarkdownBlocks

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
                            // 确定性 Markdown 渲染: MarkdownBlocks 解析出块结构,
                            // 这里显式排版 — 标题层级/列表缩进/粗体/链接都有确定的视觉结果。
                            // 之前用 MarkdownText(Markwon) 黑盒, 真机上标题列表与正文无视觉差异。
                            MarkdownChangelog(
                                markdown = changelog,
                                textColor = colors.onSurfaceVariant,
                                accentColor = colors.primary
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

/**
 * 更新日志的确定性 Markdown 排版。
 * 块级: ## 标题(层级字号) / - 列表(圆点+缩进) / 普通段落。
 * 行内: **粗体** / `代码` / [链接]。
 * 排版完全由本函数决定, 无第三方渲染依赖。
 */
@Composable
private fun MarkdownChangelog(markdown: String, textColor: androidx.compose.ui.graphics.Color, accentColor: androidx.compose.ui.graphics.Color) {
    val body = MaterialTheme.typography.bodySmall
    Column {
        for (block in MarkdownBlocks.parse(markdown)) {
            when (block) {
                is MarkdownBlocks.Block.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(fontWeight = FontWeight.Bold, color = textColor),
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                is MarkdownBlocks.Block.Bullet -> Column {
                    for (item in block.items) {
                        Row(modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)) {
                            Text("•", style = body.copy(color = accentColor))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                inlineAnnotated(item, textColor, accentColor),
                                style = body.copy(color = textColor),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is MarkdownBlocks.Block.Paragraph -> Text(
                    text = inlineAnnotated(block.text, textColor, accentColor),
                    style = body.copy(color = textColor),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/** 行内粗体/代码/链接 → AnnotatedString span */
private fun inlineAnnotated(text: String, textColor: androidx.compose.ui.graphics.Color, accentColor: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        for (span in MarkdownBlocks.parseInline(text)) {
            when (span) {
                is MarkdownBlocks.Inline.Text -> append(span.text)
                is MarkdownBlocks.Inline.Bold -> {
                    val start = length
                    append(span.text)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor), start, length)
                }
                is MarkdownBlocks.Inline.Code -> {
                    val start = length
                    append(span.text)
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = textColor), start, length)
                }
                is MarkdownBlocks.Inline.Link -> {
                    val start = length
                    append(span.text)
                    addStyle(SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline), start, length)
                }
            }
        }
    }
