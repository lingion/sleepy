package com.lingion.sleepy.console

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lingion.sleepy.R
import com.lingion.sleepy.console.handlers.EnableFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 密钥只保存在弹窗内存中，关闭即丢弃，不进入可保存状态。 */
@Composable
fun DebugUnlockDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    prepare: suspend (Application) -> Boolean = { ConsoleBootstrap.armForUnlock(it) },
    unlock: suspend (String) -> EnableFlow.Result = { ConsoleBootstrap.unlock(it) },
) {
    val app = LocalContext.current.applicationContext as Application
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val status by DebugGate.status.collectAsState()
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    // HTTP 也能完成解锁；本地提交期间等待装配返回，避免提前移除并取消提交协程。
    LaunchedEffect(status, busy) {
        if (status.active && status.releaseUnlocked && !busy) currentOnUnlocked()
    }
    LaunchedEffect(Unit) {
        ready = withContext(Dispatchers.IO) { prepare(app) }
        if (!ready) error = R.string.debug_unlock_service_failed
    }
    AlertDialog(
        // 由 Compose 约束宽度，避免平台 Dialog 和输入框最小宽度互相触发重新测量。
        modifier = Modifier.testTag("debug_unlock_dialog").widthIn(max = 560.dp).padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.debug_unlock_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.debug_unlock_description))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().testTag("debug_unlock_key"),
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text(stringResource(R.string.debug_unlock_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                )
                error?.let { Text(stringResource(it), Modifier.testTag("debug_unlock_error")) }
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.testTag("debug_unlock_confirm"), enabled = ready && !busy && passphrase.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    try {
                        when (withContext(Dispatchers.IO) { unlock(passphrase) }) {
                            EnableFlow.Result.OK, EnableFlow.Result.ALREADY_ACTIVE -> {
                                passphrase = ""
                                onUnlocked()
                            }
                            EnableFlow.Result.WRONG_PASSPHRASE -> error = R.string.debug_unlock_wrong_key
                            EnableFlow.Result.BACKOFF -> error = R.string.debug_unlock_backoff
                            EnableFlow.Result.NOT_ARMED -> error = R.string.debug_unlock_expired
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = R.string.debug_unlock_service_failed
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.debug_unlock_action)) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.testTag("debug_unlock_cancel"), enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
