package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle

/** A shared, content-sized naming form for timetable and period-table copies. */
@Composable
internal fun CopyNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    nameLabel: String,
    nameTaken: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    tagPrefix: String = "copy_name",
) {
    val nativeActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    val actions: @Composable () -> Unit = {
        DialogActionButtons(
            confirmText = stringResource(R.string.ok),
            onConfirm = onConfirm,
            dismissText = stringResource(R.string.cancel),
            onDismiss = { if (enabled) onDismiss() },
            confirmEnabled = enabled && name.trim().isNotBlank() && !nameTaken,
            dismissEnabled = enabled,
            dbgTagPrefix = tagPrefix,
            growWithContent = true,
        )
    }
    GlassAlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        modifier = Modifier.dbgTag("${tagPrefix}_dialog"),
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { if (nativeActions) actions() },
        dismissButton = {},
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(nameLabel) },
                    singleLine = true,
                    enabled = enabled,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text(stringResource(R.string.period_table_name_taken)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().dbgTag("${tagPrefix}_field"),
                    shape = SleepyTheme.fieldShape,
                    colors = SleepyTheme.fieldColors(),
                )
                if (!nativeActions) actions()
            }
        },
    )
}
