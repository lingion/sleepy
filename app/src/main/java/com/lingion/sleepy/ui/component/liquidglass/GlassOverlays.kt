package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlin.math.roundToInt

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
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val carrierShape = if (glass && shape == AlertDialogDefaults.shape) FlClashGlassShape(34f) else shape
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
    ) {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier.recordGlassPopupScene(popupScene).glassControl(shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Alert),
            dismissButton = dismissButton, icon = icon, title = title, text = text, shape = carrierShape,
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
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val carrierShape = if (glass && shape == MenuDefaults.shape) FlClashGlassShape(24f) else shape
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassPlacementLayer provides false,
    ) {
        MaterialDropdownMenu(
            expanded, onDismissRequest,
            // Material's dropdown owns the content layer. A second backdrop placement layer
            // drops its foreground on API 33; the backdrop's draw-time outline clip remains.
            modifier.recordGlassPopupScene(popupScene).glassControl(
                shape = carrierShape, enabled = false, registerPane = false,
                role = GlassMaterialRole.Menu, placementLayer = false,
            ),
            offset, scrollState, properties, carrierShape,
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
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
    ) {
        androidx.compose.ui.window.Dialog(onDismissRequest, properties) {
            androidx.compose.foundation.layout.Box(Modifier.recordGlassPopupScene(popupScene)) { content() }
        }
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
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val carrierShape = if (glass) FlClashGlassShape(34f) else androidx.compose.material3.DatePickerDefaults.shape
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
    ) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest, confirmButton,
            modifier.recordGlassPopupScene(popupScene).glassControl(shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Alert),
            shape = carrierShape,
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
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
    ) {
        MaterialBottomSheet(
            onDismissRequest = onDismissRequest,
            // Material 在传入的 modifier 之后放置 draggableAnchors；将录制和玻璃裁切
            // 移至 sheet 的实际位置，再抵消内部位移，避免半高 sheet 被原点处的 clip 裁掉。
            // 两次 offset 不改变测量尺寸，Material 仍拥有高度、手柄、拖动与输入法 insets。
            modifier = modifier
                .offset { IntOffset(0, sheetState.requireOffset().roundToInt()) }
                .recordGlassPopupScene(popupScene)
                .glassControl(shape = shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu)
                .offset { IntOffset(0, -sheetState.requireOffset().roundToInt()) },
            sheetState = sheetState, sheetMaxWidth = sheetMaxWidth, shape = shape,
            containerColor = if (glass) Color.Transparent else containerColor,
            contentColor = contentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
            scrimColor = scrimColor, dragHandle = dragHandle, contentWindowInsets = contentWindowInsets,
            properties = properties, content = content,
        )
    }
}
