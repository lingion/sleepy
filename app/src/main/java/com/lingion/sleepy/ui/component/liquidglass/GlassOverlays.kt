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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.UiStyle
import kotlin.math.roundToInt

@Composable
private fun overlayGlass() = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null

// Visual fit to the recorded iOS 26.5 light alert/sheet backdrop: RGB 240 -> 192.
// This only dims the surrounding scene; it is not an optical pane tint or blur value.
internal const val NativeLightModalDimAmount = 0.2f

/** Must run inside the actual dialog composition, whose parent owns its window. */
@Composable
internal fun GlassDialogScrim(enabled: Boolean) {
    if (!enabled) return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val originalDimAmount = window.attributes.dimAmount
        window.setDimAmount(NativeLightModalDimAmount)
        onDispose { window.setDimAmount(originalDimAmount) }
    }
}

internal fun glassModalScrimColor(requested: Color, glass: Boolean, dark: Boolean, materialDefault: Color): Color =
    if (requested != Color.Unspecified) requested
    else if (glass && !dark) Color.Black.copy(alpha = NativeLightModalDimAmount)
    else materialDefault

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
    val nativeLightScrim = glass && !LocalDarkTheme.current
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val carrierShape = if (glass && shape == AlertDialogDefaults.shape) FlClashGlassShape(34f) else shape
    val materialScope = rememberGlassMaterialScope(carrierShape, parentScene?.overlayBackdrop)
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassMaterialBackdrop provides null,
    ) {
        val opticalModifier = modifier.recordGlassPopupScene(popupScene).trackGlassMaterial(materialScope).glassControl(
            shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Alert,
            exportedBackdrop = materialScope?.exported,
        )
        GlassMaterialContent(materialScope) {
            MaterialAlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = { GlassDialogScrim(nativeLightScrim); confirmButton() },
                modifier = opticalModifier,
                dismissButton = dismissButton, icon = icon, title = title, text = text, shape = carrierShape,
                containerColor = if (glass) Color.Transparent else containerColor,
                iconContentColor = iconContentColor, titleContentColor = titleContentColor,
                textContentColor = textContentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
                properties = properties,
            )
        }
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
    val materialScope = rememberGlassMaterialScope(carrierShape, parentScene?.overlayBackdrop)
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassPlacementLayer provides false,
        LocalGlassMaterialBackdrop provides null,
    ) {
        // Capture the shell's prefix before publishing its material to children.
        val opticalModifier = modifier.recordGlassPopupScene(popupScene).trackGlassMaterial(materialScope).glassControl(
            shape = carrierShape, enabled = false, registerPane = false,
            role = GlassMaterialRole.Menu, placementLayer = false,
            exportedBackdrop = materialScope?.exported,
        )
        GlassMaterialContent(materialScope) {
            MaterialDropdownMenu(
                expanded, onDismissRequest,
                // Material's dropdown owns the content layer. A second backdrop placement layer
                // drops its foreground on API 33; the backdrop's draw-time outline clip remains.
                opticalModifier,
                offset, scrollState, properties, carrierShape,
                if (glass) Color.Transparent else containerColor,
                if (glass) 0.dp else tonalElevation,
                shadowElevation, border, content,
            )
        }
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val glass = overlayGlass()
    val nativeLightScrim = glass && !LocalDarkTheme.current
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassMaterialBackdrop provides null,
    ) {
        androidx.compose.ui.window.Dialog(onDismissRequest, properties) {
            GlassDialogScrim(nativeLightScrim)
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
    val nativeLightScrim = glass && !LocalDarkTheme.current
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val carrierShape = if (glass) FlClashGlassShape(34f) else androidx.compose.material3.DatePickerDefaults.shape
    val materialScope = rememberGlassMaterialScope(carrierShape, parentScene?.overlayBackdrop)
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassMaterialBackdrop provides null,
    ) {
        val opticalModifier = modifier.recordGlassPopupScene(popupScene).trackGlassMaterial(materialScope).glassControl(
            shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Alert,
            exportedBackdrop = materialScope?.exported,
        )
        GlassMaterialContent(materialScope) {
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest, { GlassDialogScrim(nativeLightScrim); confirmButton() },
                opticalModifier,
                shape = carrierShape,
                dismissButton = dismissButton,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = if (glass) Color.Transparent else androidx.compose.material3.DatePickerDefaults.colors().containerColor,
                ),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    // Unspecified selects this appearance's default; explicit caller colors always win.
    scrimColor: Color = Color.Unspecified,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> androidx.compose.foundation.layout.WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = overlayGlass()
    val effectiveScrimColor = glassModalScrimColor(
        scrimColor, glass, LocalDarkTheme.current, BottomSheetDefaults.ScrimColor,
    )
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val materialScope = rememberGlassMaterialScope(shape, parentScene?.overlayBackdrop)
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassMaterialBackdrop provides null,
    ) {
        val opticalModifier = modifier
            .offset { IntOffset(0, sheetState.requireOffset().roundToInt()) }
            .recordGlassPopupScene(popupScene)
            .trackGlassMaterial(materialScope)
            .glassControl(shape = shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu,
                exportedBackdrop = materialScope?.exported)
            .offset { IntOffset(0, -sheetState.requireOffset().roundToInt()) }
        GlassMaterialContent(materialScope) {
            MaterialBottomSheet(
                onDismissRequest = onDismissRequest,
                // Material 在传入的 modifier 之后放置 draggableAnchors；将录制和玻璃裁切
                // 移至 sheet 的实际位置，再抵消内部位移，避免半高 sheet 被原点处的 clip 裁掉。
                // 两次 offset 不改变测量尺寸，Material 仍拥有高度、手柄、拖动与输入法 insets。
                modifier = opticalModifier,
                sheetState = sheetState, sheetMaxWidth = sheetMaxWidth, shape = shape,
                containerColor = if (glass) Color.Transparent else containerColor,
                contentColor = contentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
                scrimColor = effectiveScrimColor, dragHandle = dragHandle, contentWindowInsets = contentWindowInsets,
                properties = properties, content = content,
            )
        }
    }
}
