package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
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

/**
 * The platform's fixed dialog width limits the whole Window, including transparent
 * drawing room. Give this optical Alert the available viewport instead; the pane
 * still owns its 320dp default and caller size modifiers, inside its measured halo.
 * All other caller window behavior remains intact, including an explicit opt-out.
 */
internal fun nativeGlassAlertDialogProperties(properties: DialogProperties): DialogProperties =
    if (!properties.usePlatformDefaultWidth) properties else DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = properties.decorFitsSystemWindows,
        windowTitle = properties.windowTitle,
        windowType = properties.windowType,
        windowToken = properties.windowToken,
    )

/** The platform dialog owns window lifetime, focus, back and dismissal in both styles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    // Sentinels distinguish appearance defaults from explicit caller overrides.
    shape: Shape? = null,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val resolvedIconColor = if (iconContentColor != Color.Unspecified) iconContentColor else AlertDialogDefaults.iconContentColor
    val resolvedTitleColor = if (titleContentColor != Color.Unspecified) titleContentColor else AlertDialogDefaults.titleContentColor
    val resolvedTextColor = if (textContentColor != Color.Unspecified) textContentColor else AlertDialogDefaults.textContentColor
    if (!overlayGlass()) {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
            dismissButton = dismissButton, icon = icon, title = title, text = text,
            shape = shape ?: AlertDialogDefaults.shape,
            containerColor = if (containerColor != Color.Unspecified) containerColor else AlertDialogDefaults.containerColor,
            iconContentColor = resolvedIconColor, titleContentColor = resolvedTitleColor,
            textContentColor = resolvedTextColor, tonalElevation = tonalElevation, properties = properties,
        )
        return
    }
    val nativeLightScrim = !LocalDarkTheme.current
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val carrierShape = shape ?: FlClashGlassShape(34f)
    val materialScope = rememberGlassMaterialScope(carrierShape, parentScene?.overlayBackdrop)
    val panePress = remember { NativeGlassAlertPressState() }
    // Native DOWN traces support this small whole-pane lift. This zero-delay
    // spring is an empirical fit; its release is not claimed as a native match.
    // animateFloatAsState retains velocity when interrupted and never gates clicks.
    val pressScale by animateFloatAsState(
        targetValue = if (panePress.isPressed) 1.0125f else 1f,
        animationSpec = spring(dampingRatio = .565f, stiffness = 256f, visibilityThreshold = .00001f),
        label = "native-alert-press-scale",
    )
    CompositionLocalProvider(
        LocalGlassOverlay provides true,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassMaterialBackdrop provides null,
        LocalGlassAlertPressState provides panePress,
    ) {
        // BasicAlertDialog applies its width bounds to the pane plus drawing room.
        // A platform fixed-width theme can then consume the halo by shrinking the
        // pane. Dialog provides the same platform lifetime/focus/dismissal while
        // our content measures the pane and its real outer drawing room separately.
        Dialog(onDismissRequest = onDismissRequest, properties = nativeGlassAlertDialogProperties(properties)) {
            GlassDialogScrim(nativeLightScrim)
            val opticalModifier = modifier.graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }.recordGlassPopupScene(popupScene).trackGlassMaterial(materialScope).glassControl(
                shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Alert,
                exportedBackdrop = materialScope?.exported,
            ).then(if (containerColor != Color.Unspecified) Modifier.background(containerColor, carrierShape) else Modifier)
            NativeGlassAlertDrawingSpace {
                GlassMaterialContent(materialScope) {
                    NativeGlassAlertContent(
                        modifier = opticalModifier, icon = icon, title = title, text = text,
                        iconContentColor = resolvedIconColor, titleContentColor = resolvedTitleColor,
                        textContentColor = resolvedTextColor, dismissButton = dismissButton, confirmButton = confirmButton,
                    )
                }
            }
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
    shape: Shape? = null,
    containerColor: Color = Color.Unspecified,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = overlayGlass()
    if (!glass) {
        MaterialDropdownMenu(
            expanded, onDismissRequest, modifier, offset, scrollState, properties,
            shape ?: MenuDefaults.shape,
            if (containerColor != Color.Unspecified) containerColor else MenuDefaults.containerColor,
            tonalElevation, shadowElevation, border, content,
        )
        return
    }
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    // Menu-only visible-corner fit against iOS 26.5. Alert, language and
    // sheet carriers have their own independently measured contours.
    val carrierShape = shape ?: FlClashGlassShape(32f)
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
                if (containerColor != Color.Unspecified) containerColor else Color.Transparent,
                if (glass) 0.dp else tonalElevation,
                shadowElevation, border,
                // Material supplies 8dp at each edge. Native menu AX frames show 10dp.
                content = {
                    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 2.dp), content = content)
                },
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
    // Null selects the appearance's default; even an explicit Material default is respected.
    shape: Shape? = null,
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
    val carrierShape = shape ?: if (glass) NativeGlassSheetShape else BottomSheetDefaults.ExpandedShape
    val materialScope = rememberGlassMaterialScope(carrierShape, parentScene?.overlayBackdrop)
    CompositionLocalProvider(
        LocalGlassOverlay provides glass,
        LocalGlassOverlaySamplingHost provides parentScene,
        LocalGlassPopupScene provides popupScene,
        LocalGlassMaterialBackdrop provides null,
    ) {
        val opticalModifier = modifier
            // Keep the gap outside the optical carrier and Material's Surface, so the
            // exported backing, foreground clip and touch bounds all share the inset.
            .then(if (glass) Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp) else Modifier)
            .offset { IntOffset(0, sheetState.requireOffset().roundToInt()) }
            .recordGlassPopupScene(popupScene)
            .trackGlassMaterial(materialScope)
            .glassControl(shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Sheet,
                exportedBackdrop = materialScope?.exported)
            .offset { IntOffset(0, -sheetState.requireOffset().roundToInt()) }
        GlassMaterialContent(materialScope) {
            MaterialBottomSheet(
                onDismissRequest = onDismissRequest,
                // Material 在传入的 modifier 之后放置 draggableAnchors；将录制和玻璃裁切
                // 移至 sheet 的实际位置，再抵消内部位移，避免半高 sheet 被原点处的 clip 裁掉。
                // 两次 offset 不改变测量尺寸，Material 仍拥有高度、手柄、拖动与输入法 insets。
                modifier = opticalModifier,
                sheetState = sheetState, sheetMaxWidth = sheetMaxWidth, shape = carrierShape,
                containerColor = if (glass) Color.Transparent else containerColor,
                contentColor = contentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
                scrimColor = effectiveScrimColor, dragHandle = dragHandle, contentWindowInsets = contentWindowInsets,
                properties = properties, content = content,
            )
        }
    }
}

/**
 * Contour fit, not an Apple API constant: the 750 x 1334 native sheet reference has
 * bounds x=16..734, y=635.4..1318. At y=656 its left edge is x=37 (top radius ~36pt);
 * at y=1314 it is x=38 (bottom radius ~20pt). Reference:
 * native-static-optics/rest/optics-fine_grid-light-presentations-sheet-rest.png.
 */
private val NativeGlassSheetShape: CornerBasedShape = NativeGlassSheetContour(
    topStart = CornerSize(36.dp), topEnd = CornerSize(36.dp),
    bottomEnd = CornerSize(20.dp), bottomStart = CornerSize(20.dp),
)

/** Publish the same contour's radii to the optical shader's directional rim. */
private class NativeGlassSheetContour(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val leftToRight = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (leftToRight) topStart else topEnd
        val topRight = if (leftToRight) topEnd else topStart
        val bottomRight = if (leftToRight) bottomEnd else bottomStart
        val bottomLeft = if (leftToRight) bottomStart else bottomEnd
        val path = android.graphics.Path()
        LiquidGlassSuperellipse.buildPath(
            LiquidGlassSuperellipse.Rect2(0f, 0f, size.width, size.height),
            LiquidGlassSuperellipse.Radii(
                topLeft = LiquidGlassSuperellipse.Size2(topLeft, topLeft),
                topRight = LiquidGlassSuperellipse.Size2(topRight, topRight),
                bottomRight = LiquidGlassSuperellipse.Size2(bottomRight, bottomRight),
                bottomLeft = LiquidGlassSuperellipse.Size2(bottomLeft, bottomLeft),
            ),
        ).toAndroidPath(path)
        return Outline.Generic(path.asComposePath())
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = NativeGlassSheetContour(topStart, topEnd, bottomEnd, bottomStart)
}
