package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

@Composable
private fun overlayGlass() = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null

/** Material owns window lifetime, focus, back and dismissal; optics are painted in that window. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val glass = overlayGlass()
    CompositionLocalProvider(LocalGlassOverlay provides glass) {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier.glassControl(shape = shape, enabled = false, registerPane = false, role = GlassMaterialRole.Alert),
            dismissButton = dismissButton, icon = icon, title = title, text = text, shape = shape,
            containerColor = if (glass) Color.Transparent else containerColor,
            iconContentColor = iconContentColor, titleContentColor = titleContentColor,
            textContentColor = textContentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
            properties = properties,
        )
    }
}

@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = overlayGlass()
    CompositionLocalProvider(LocalGlassOverlay provides glass) {
        MaterialDropdownMenu(
            expanded, onDismissRequest,
            modifier.glassControl(shape = shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu),
            offset, scrollState, properties, shape,
            if (glass) Color.Transparent else containerColor,
            if (glass) 0.dp else tonalElevation,
            shadowElevation, border, content,
        )
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val glass = overlayGlass()
    CompositionLocalProvider(LocalGlassOverlay provides glass) {
        androidx.compose.ui.window.Dialog(onDismissRequest, properties, content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = overlayGlass()
    CompositionLocalProvider(LocalGlassOverlay provides glass) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest, confirmButton,
            modifier.glassControl(enabled = false, registerPane = false, role = GlassMaterialRole.Alert),
            dismissButton = dismissButton,
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = if (glass) Color.Transparent else androidx.compose.material3.DatePickerDefaults.colors().containerColor,
            ),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> androidx.compose.foundation.layout.WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = overlayGlass()
    CompositionLocalProvider(LocalGlassOverlay provides glass) {
        MaterialBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier.glassControl(shape = shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu),
            sheetState = sheetState, sheetMaxWidth = sheetMaxWidth, shape = shape,
            containerColor = if (glass) Color.Transparent else containerColor,
            contentColor = contentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
            scrimColor = scrimColor, dragHandle = dragHandle, contentWindowInsets = contentWindowInsets,
            properties = properties, content = content,
        )
    }
}
