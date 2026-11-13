package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.ui.component.sleepyExpandSpecs
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlin.math.max

/** Material keeps its inline card; only Liquid Glass presents a native-style menu. */
@Composable
fun GlassLanguageMenu(
    title: String,
    selectedCode: String,
    languages: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismissed: (String?) -> Unit = {},
) {
    if (LocalUiStyle.current != UiStyle.LiquidGlass) {
        MaterialLanguageCard(title, selectedCode, languages, onSelect, modifier, onDismissed)
        return
    }
    var session by remember { mutableStateOf<LanguageMenuSession?>(null) }
    var completing by remember { mutableStateOf(false) }
    val completionScope = rememberCoroutineScope()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val select by rememberUpdatedState(onSelect)
    val dismissed by rememberUpdatedState(onDismissed)
    val dragSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val currentLabel = languages.firstOrNull { it.first == selectedCode }?.second ?: selectedCode
    fun open() {
        if (session == null && !completing && coordinates?.isAttached == true) session = LanguageMenuSession(dragSlop)
    }
    fun screenPoint(point: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToScreen(point)
    fun hover(point: Offset) {
        val active = session ?: return
        val screen = screenPoint(point) ?: return
        active.holdingAnchor = true
        if (active.pointerOrigin == null) active.pointerOrigin = screen
        if ((screen - active.pointerOrigin!!).getDistance() > active.dragSlopPx) active.pointerMoved = true
        val viewport = active.viewport?.takeIf { it.isAttached }?.let { coords ->
            // Row coordinates retain their unscrolled layout size even when
            // clipped. Intersect hit testing with the actual transformed and
            // window-clipped scroll viewport before considering any row.
            val screenOrigin = coords.localToScreen(Offset.Zero) - coords.localToWindow(Offset.Zero)
            coords.boundsInWindow(clipBounds = true).translate(screenOrigin)
        }
        active.hoveredCode = if (active.pointerMoved && viewport?.contains(screen) == true) active.rows.entries.firstOrNull { (_, coords) ->
            coords.takeIf { it.isAttached }?.let {
                Rect(it.localToScreen(Offset.Zero), it.localToScreen(Offset(it.size.width.toFloat(), it.size.height.toFloat()))).contains(screen)
            } == true
        }?.key else null
        if (active.hoveredCode != null) active.enteredMenu = true
    }
    Box(modifier.fillMaxWidth().onGloballyPositioned { coordinates = it }
        .languageMenuAnchorGesture(
            enabled = session == null && !completing,
            onOpen = ::open,
            onPosition = { point, _ -> hover(point) },
            onRelease = { point, _ ->
                val active = session
                if (active != null) {
                    hover(point)
                    active.holdingAnchor = false
                    if (active.enteredMenu) active.close(active.hoveredCode, select)
                    active.hoveredCode = null
                }
            },
            onCancel = { session?.let { it.holdingAnchor = false; it.hoveredCode = null } },
        )
        .semantics(mergeDescendants = true) { role = Role.Button; onClick { open(); true } }
        .dbgTag("general_language_card", text = currentLabel, role = "button"),
    ) {
        LanguageCardContent(title, currentLabel,
            Modifier.graphicsLayer { alpha = if (session == null) 1f else 0f }, tagged = true)
    }
    session?.let { active ->
        LanguagePopup(title, selectedCode, languages, coordinates, active,
            onSelect = select,
            onClosed = { code ->
                completing = true
                session = null
                completionScope.launch {
                    withFrameNanos { }
                    completing = false
                    dismissed(code)
                }
            })
    }
}

private class LanguageMenuSession(val dragSlopPx: Float) {
    var pointerOrigin: Offset? = null
    var pointerMoved = false
    val motion = LanguageMenuMotion()
    val rows = mutableMapOf<String, LayoutCoordinates>()
    var viewport: LayoutCoordinates? = null
    var holdingAnchor by mutableStateOf(false)
    var hoveredCode by mutableStateOf<String?>(null)
    var closing by mutableStateOf(false)
    var pickedCode by mutableStateOf<String?>(null)
    var enteredMenu = false
    fun close(code: String?, onSelect: (String) -> Unit) {
        if (closing) return
        pickedCode = code
        closing = true
        motion.close()
        // Native Picker commits the binding before its context menu finishes closing.
        if (code != null) onSelect(code)
    }
}

@Composable
private fun LanguageCardContent(title: String, label: String, modifier: Modifier = Modifier, tagged: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    FlowRow(modifier.fillMaxWidth().heightIn(min = (154f / 3f).dp).clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleSmall.copy(lineHeight = (50f / 3f).sp),
            color = colors.onSurface, maxLines = 1)
        Row(Modifier.widthIn(max = 180.dp).then(if (tagged)
            Modifier.dbgTag("general_language_value", text = label, clickable = false) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = (58f / 3f).sp),
                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun MaterialLanguageCard(
    title: String, selectedCode: String, languages: List<Pair<String, String>>, onSelect: (String) -> Unit,
    modifier: Modifier, onDismissed: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val label = languages.firstOrNull { it.first == selectedCode }?.second ?: selectedCode
    Column(modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .dbgTag("general_language_card", text = label, role = "button")
            .heightIn(min = SettingsLayout.minRowHeight).padding(horizontal = SettingsLayout.cardInset, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = SettingsLayout.headingStyle, color = colors.onSurface)
            Icon(Icons.Outlined.ExpandMore, title, tint = colors.onSurfaceVariant)
        }
        val transition = sleepyExpandSpecs()
        AnimatedVisibility(expanded, enter = transition.enter, exit = transition.exit) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).selectableGroup()) {
                languages.forEachIndexed { index, (code, text) ->
                    val selected = code == selectedCode
                    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = {
                        expanded = false
                        onSelect(code)
                        onDismissed(code)
                    }).dbgTag("general_language#$code", text = text, role = "radio")
                        .heightIn(min = SettingsLayout.minRowHeight).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f), style = SettingsLayout.itemStyle,
                            color = if (selected) colors.primary else colors.onSurface)
                        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), colors.primary)
                    }
                    if (index < languages.lastIndex) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                }
            }
        }
    }
}

@Composable
private fun LanguagePopup(
    title: String,
    selectedCode: String,
    languages: List<Pair<String, String>>,
    anchorCoordinates: LayoutCoordinates?,
    session: LanguageMenuSession,
    onSelect: (String) -> Unit,
    onClosed: (String?) -> Unit,
) {
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val glass = LocalGlassHost.current != null
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safe = WindowInsets.safeDrawing
    val margin = with(density) { 16.dp.toPx() }
    val left = safe.getLeft(density, LayoutDirection.Ltr) + margin
    val right = windowSize.width - safe.getRight(density, LayoutDirection.Ltr) - margin
    val top = safe.getTop(density) + margin
    val bottom = windowSize.height - safe.getBottom(density) - margin
    val width = with(density) { minOf(250.dp, (right - left).coerceAtLeast(1f).toDp()) }
    val maxHeight = with(density) { (bottom - top).coerceAtLeast(1f).toDp() }
    var popupOrigin by remember { mutableStateOf<Offset?>(null) }
    var menuSize by remember { mutableStateOf(IntSize(with(density) { width.roundToPx() },
        with(density) { (20.dp + 42.dp * languages.size).roundToPx() })) }
    val anchorScreen = anchorCoordinates?.takeIf { it.isAttached }?.let {
        val origin = it.localToScreen(Offset.Zero)
        Rect(origin, Size(it.size.width.toFloat(), it.size.height.toFloat()))
    } ?: Rect.Zero
    val anchor = anchorScreen.translate(-(popupOrigin ?: Offset.Zero))
    val menuX = (anchor.center.x - menuSize.width / 2f).coerceIn(left, max(left, right - menuSize.width))
    val menuY = anchor.top.coerceIn(top, max(top, bottom - menuSize.height))
    val menu = Rect(menuX, menuY, menuX + menuSize.width, menuY + menuSize.height)
    val motion = session.motion
    val geometry = motion.geometry(anchor, menu)
    val closed by rememberUpdatedState(onClosed)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(session, session.closing) {
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (durationScale <= 0f) motion.finish()
        else {
            var previous = withFrameNanos { it }
            while (motion.isRunning) {
                val now = withFrameNanos { it }
                // Use actual elapsed time: a missed frame must not slow the spring down.
                motion.tick((now - previous) / 1_000_000_000.0 / durationScale)
                previous = now
            }
        }
        if (session.closing) {
            closed(session.pickedCode)
        }
    }
    // The full window preserves overshoot and the source-card morph outside final menu bounds.
    Popup(
        popupPositionProvider = remember { object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
        } },
        onDismissRequest = { session.close(null, select) },
        properties = PopupProperties(focusable = !session.holdingAnchor,
            dismissOnBackPress = true, dismissOnClickOutside = true, clippingEnabled = false),
    ) {
        CompositionLocalProvider(LocalGlassOverlay provides glass, LocalGlassOverlaySamplingHost provides parentScene,
            LocalGlassPopupScene provides popupScene, LocalGlassMaterialBackdrop provides null) {
            Box(Modifier.size(with(density) { windowSize.width.toDp() }, with(density) { windowSize.height.toDp() })
                .onGloballyPositioned { popupOrigin = it.localToScreen(Offset.Zero) }
                .recordGlassPopupScene(popupScene)
                .pointerInput(session, menu) { detectTapGestures { point ->
                    if (!menu.contains(point)) session.close(null, select)
                } }) {
                if (popupOrigin != null && anchor.width > 0f) {
                    // Bound the optical pass to the card/menu region rather than shading the whole window.
                    val carrier = Rect(minOf(geometry.anchor.left, geometry.material.left), minOf(geometry.anchor.top, geometry.material.top),
                        maxOf(geometry.anchor.right, geometry.material.right), maxOf(geometry.anchor.bottom, geometry.material.bottom))
                    val shape = LanguageMenuMorphShape(geometry.anchor.translate(-carrier.topLeft),
                        geometry.material.translate(-carrier.topLeft), motion.alpha, density.density)
                    // A retained placement clip keeps the source card's first-frame height
                    // during natural playback. Clip the current contours directly instead.
                    Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(carrier.left.toInt(), carrier.top.toInt()) }
                        .requiredSize(with(density) { carrier.width.toDp() }, with(density) { carrier.height.toDp() })
                        .glassControl(shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu,
                            placementLayer = false)
                        .clip(shape).then(if (glass) Modifier else Modifier.background(colors.surfaceContainerHigh)))
                    Box(Modifier.align(AbsoluteAlignment.TopLeft).requiredSize(with(density) { anchor.width.toDp() }, with(density) { anchor.height.toDp() })
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = geometry.anchor.center.x - anchor.width / 2f
                            translationY = geometry.anchor.center.y - anchor.height / 2f
                            scaleX = geometry.anchorContentScale
                            scaleY = geometry.anchorContentScale
                            alpha = motion.anchorContentAlpha
                        }.clearAndSetSemantics {}) {
                        LanguageCardContent(title, languages.firstOrNull { it.first == (session.pickedCode ?: selectedCode) }?.second ?: selectedCode,
                            Modifier.background(androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
                Column(Modifier.align(AbsoluteAlignment.TopLeft).width(width).heightIn(max = maxHeight)
                    .onSizeChanged { menuSize = it }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = geometry.content.left
                        translationY = geometry.content.top
                        scaleX = geometry.content.width / menu.width.coerceAtLeast(1f)
                        scaleY = geometry.content.height / menu.height.coerceAtLeast(1f)
                        alpha = if (popupOrigin == null) 0f else motion.alpha
                    }
                    .clip(FlClashGlassShape(26f))
                    .dbgTag("general_language_menu", clickable = false)
                    .semantics {
                        paneTitle = title
                        dismiss { session.close(null, select); true }
                        languageMenuVisualState = LanguageMenuVisualState(
                            geometry.content.width / menu.width.coerceAtLeast(1f),
                            geometry.content.height / menu.height.coerceAtLeast(1f), motion.alpha, motion.alpha,
                            with(density) { geometry.content.left.toDp().value }, with(density) { geometry.content.top.toDp().value })
                    }
                    .onGloballyPositioned { session.viewport = it }
                    .verticalScroll(rememberScrollState()).selectableGroup().padding(10.dp)) {
                    languages.forEach { (code, label) ->
                        val interaction = remember(code) { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp)
                            .background(
                                if (session.hoveredCode == code || pressed) colors.onSurface.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent,
                                CircleShape,
                            )
                            .selectable(code == (session.pickedCode ?: selectedCode), enabled = !session.closing,
                                interactionSource = interaction, indication = null,
                                role = Role.RadioButton, onClick = { session.close(code, select) })
                            .dbgTag("general_language#$code", text = label, role = "radio", clickable = !session.closing)
                            .onGloballyPositioned { coords ->
                                session.rows[code] = coords
                            }.padding(start = 3.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(30.dp)) {
                                if (code == (session.pickedCode ?: selectedCode)) Icon(Icons.Outlined.Check, null,
                                    Modifier.size(17.dp), tint = colors.onSurface)
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 20.sp), color = colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/** Two measured contours; optical smoothing/radius remain an approximation, documented with the reference. */
private data class LanguageMenuMorphShape(val anchor: Rect, val menu: Rect, val progress: Float, val scale: Float) : LiquidGlassDualContourShape {
    private val contours: Pair<Path, Path> by lazy {
        fun path(rect: Rect, radius: Float): Path {
            val native = android.graphics.Path()
            LiquidGlassSuperellipse.buildPath(LiquidGlassSuperellipse.Rect2(rect.left, rect.top, rect.right, rect.bottom),
                LiquidGlassSuperellipse.Radii.makeRadius(radius.coerceIn(0f, minOf(rect.width, rect.height) / 2f)))
                .toAndroidPath(native)
            return native.asComposePath()
        }
        path(anchor, 8f * scale) to
            path(menu, minOf(menu.width, menu.height) / 2f * (1f - progress) + 26f * scale * progress)
    }
    private val outline: Outline by lazy {
        Outline.Generic(Path.combine(PathOperation.Union, contours.first, contours.second))
    }

    override fun createOpticalContours(size: Size, layoutDirection: LayoutDirection, density: Density): Pair<Path, Path> = contours
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
}
