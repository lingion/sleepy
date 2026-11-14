package com.lingion.sleepy.ui.component.liquidglass

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lingion.sleepy.console.ui.dbgTag
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.math.max

/** Language labels and diagnostics stay in the Sleepy adapter. */
@Composable
fun GlassLanguageMenu(
    title: String,
    selectedCode: String,
    languages: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismissed: (String?) -> Unit = {},
) = SingleChoiceDropdown(
    title = title, selectedId = selectedCode, options = languages,
    onSelect = onSelect, modifier = modifier, tagPrefix = "general_language", onDismissed = onDismissed,
)

/** Shared source-card morph and submitted-frame handoff for single-choice fields. */
@Composable
internal fun GlassChoiceMenu(
    title: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedLabel: String? = null,
    tagPrefix: String = "single_choice",
    onDismissed: (String?) -> Unit = {},
) {
    var session by remember { mutableStateOf<LanguageMenuSession?>(null) }
    var completing by remember { mutableStateOf(false) }
    val completionScope = rememberCoroutineScope()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val select by rememberUpdatedState(onSelect)
    val dismissed by rememberUpdatedState(onDismissed)
    val dragSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val currentLabel = selectedLabel ?: options.firstOrNull { it.first == selectedId }?.second ?: selectedId
    val acceptsChoice by rememberUpdatedState<(String) -> Boolean>({ id -> enabled && options.any { it.first == id } })
    fun complete(active: LanguageMenuSession, code: String?) {
        if (session !== active) return
        active.handoff.dispose()
        completing = true
        session = null
        completionScope.launch {
            withFrameNanos { }
            completing = false
            dismissed(code)
        }
    }
    val sourceFrame = session?.let { active ->
        rememberLanguageMenuSubmittedFrame(active, LocalView.current, "source_hidden") {
            active.handoff.sourceHiddenFrameSubmitted().also { submitted ->
                if (submitted) active.motionReady.complete(Unit)
            }
        }
    }
    val restoredFrame = session?.let { active ->
        rememberLanguageMenuSubmittedFrame(active, LocalView.current, "source_restored", allowClosing = true) {
            active.handoff.sourceRestoredFrameSubmitted().also { submitted ->
                if (submitted) complete(active, active.pickedCode)
            }
        }
    }
    fun open() {
        if (enabled && options.isNotEmpty() && session == null && !completing && coordinates?.isAttached == true) session = LanguageMenuSession(dragSlop)
    }
    LaunchedEffect(enabled, options.isEmpty()) {
        if (!enabled || options.isEmpty()) session?.close(null, select)
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
            enabled = enabled && options.isNotEmpty() && session == null && !completing,
            onOpen = ::open,
            onPosition = { point, _ -> hover(point) },
            onRelease = { point, _ ->
                val active = session
                if (active != null) {
                    hover(point)
                    active.holdingAnchor = false
                    if (active.enteredMenu) active.close(active.hoveredCode, select, acceptsChoice)
                    active.hoveredCode = null
                }
            },
            onCancel = { session?.let { it.holdingAnchor = false; it.hoveredCode = null } },
        )
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (!enabled || options.isEmpty()) disabled()
            onClick { open(); enabled && options.isNotEmpty() }
            languageMenuHandoffState = session?.handoff?.state ?: LanguageMenuHandoffState(0, "idle", 1f)
        }
        .dbgTag("${tagPrefix}_card", text = currentLabel, role = "button", clickable = enabled && options.isNotEmpty()),
    ) {
        ChoiceCardContent(title, currentLabel,
            Modifier.drawWithContent {
                drawContent()
                if (session?.handoff?.awaitingSourceFrame == true) sourceFrame?.drawn()
                if (session?.handoff?.awaitingRestoredSourceFrame == true) restoredFrame?.drawn()
            }.graphicsLayer { alpha = session?.handoff?.sourceCardAlpha ?: 1f }, tagPrefix = tagPrefix)
    }
    session?.let { active ->
        DisposableEffect(active) {
            onDispose {
                active.handoff.dispose()
                active.motionReady.cancel()
                LanguageMenuPresentationDiagnostics.event(active.handoff, "disposed")
            }
        }
        LanguagePopup(title, selectedId, options, currentLabel, tagPrefix, enabled, acceptsChoice, coordinates, active,
            onSelect = select,
            onClosed = { code -> complete(active, code) })
    }
}

private class LanguageMenuSession(val dragSlopPx: Float) {
    var pointerOrigin: Offset? = null
    var pointerMoved = false
    val motion = LanguageMenuMotion()
    val handoff = LanguageMenuHandoff()
    val motionReady = CompletableDeferred<Unit>()
    var popupPositioned = false
    val rows = mutableMapOf<String, LayoutCoordinates>()
    var viewport: LayoutCoordinates? = null
    var holdingAnchor by mutableStateOf(false)
    var hoveredCode by mutableStateOf<String?>(null)
    var closing by mutableStateOf(false)
    var pickedCode by mutableStateOf<String?>(null)
    var enteredMenu = false
    init { LanguageMenuPresentationDiagnostics.event(handoff, "prepared") }
    fun close(code: String?, onSelect: (String) -> Unit, acceptsChoice: (String) -> Boolean = { true }) {
        if (closing) return
        val accepted = code?.takeIf(acceptsChoice)
        pickedCode = accepted
        closing = true
        motion.close()
        // Native Picker commits the binding before its context menu finishes closing.
        if (accepted != null) onSelect(accepted)
    }
}

/** Applied layer inputs, kept out of snapshot state to avoid writes during layer updates. */
private class LanguageMenuReplacementLayer {
    var origin: Offset? = null
    var alpha = 0f
    fun isVisibleAt(position: Offset?) = position != null && origin == position && alpha > 0f
}

/** Registered by the actual content draw in its owning window, never by a host-window proxy. */
private class LanguageMenuSubmittedFrame(
    private val session: LanguageMenuSession,
    private val view: View,
    private val label: String,
    private val allowClosing: Boolean,
    private val onSubmitted: () -> Boolean,
) {
    private var requested = false
    private var disposed = false
    private var hardwareSubmission = false
    private var registeredObserver: ViewTreeObserver? = null
    private val submitted = Runnable {
        if (!disposed && (allowClosing || !session.closing) && !session.handoff.isDisposed && view.isAttachedToWindow) {
            event(if (hardwareSubmission) "${label}_frame_committed" else "${label}_draw_posted_legacy_or_software")
            if (onSubmitted()) event("handoff_${session.handoff.state.phase}")
        }
    }

    private fun event(name: String) = LanguageMenuPresentationDiagnostics.event(session.handoff, name,
        windowIdentity = System.identityHashCode(view.rootView), windowClass = view.rootView.javaClass.name)

    fun drawn() {
        if (requested || disposed || (session.closing && !allowClosing) || session.handoff.isDisposed) return
        requested = true
        event("${label}_first_content_draw")
        if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
            hardwareSubmission = true
            registeredObserver = view.viewTreeObserver.also { it.registerFrameCommitCallback(submitted) }
            // Registration during recording can miss this traversal's callback capture.
            // Request another real draw of this same window, rather than a timed delay.
            view.postInvalidateOnAnimation()
        } else {
            // API 26–28/software rendering has no HWUI submission callback. Keep this
            // explicitly distinct in diagnostic traces; it is not a display fence.
            view.post(submitted)
        }
    }

    fun dispose() {
        disposed = true
        view.removeCallbacks(submitted)
        if (Build.VERSION.SDK_INT >= 29) registeredObserver?.takeIf { it.isAlive }?.unregisterFrameCommitCallback(submitted)
        registeredObserver = null
    }
}

@Composable
private fun rememberLanguageMenuSubmittedFrame(
    session: LanguageMenuSession,
    view: View,
    label: String,
    allowClosing: Boolean = false,
    onSubmitted: () -> Boolean,
): LanguageMenuSubmittedFrame {
    val frame = remember(session, view, label, allowClosing) { LanguageMenuSubmittedFrame(session, view, label, allowClosing, onSubmitted) }
    DisposableEffect(frame) { onDispose { frame.dispose() } }
    return frame
}

@Composable
private fun ChoiceCardContent(title: String, label: String, modifier: Modifier = Modifier, tagPrefix: String? = null) {
    val colors = MaterialTheme.colorScheme
    FlowRow(modifier.fillMaxWidth().heightIn(min = (154f / 3f).dp).clip(LanguageCardShape).background(colors.surfaceContainer).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotEmpty()) Text(title, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleSmall.copy(lineHeight = (50f / 3f).sp),
            color = colors.onSurface, maxLines = 1)
        Row(Modifier.widthIn(max = 180.dp).then(if (tagPrefix != null)
            Modifier.dbgTag("${tagPrefix}_value", text = label, clickable = false) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = (58f / 3f).sp),
                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LanguagePopup(
    title: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    selectedLabel: String,
    tagPrefix: String,
    enabled: Boolean,
    acceptsChoice: (String) -> Boolean,
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
        with(density) { (20.dp + 42.dp * options.size).roundToPx() })) }
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
    val currentAnchor by rememberUpdatedState(anchor)
    val currentMenu by rememberUpdatedState(menu)
    val closed by rememberUpdatedState(onClosed)
    val select by rememberUpdatedState(onSelect)
    val canPick by rememberUpdatedState(acceptsChoice)
    LaunchedEffect(session, session.closing) {
        if (!session.handoff.canAnimate) {
            // A popup cancelled before its handoff never started a visible morph.
            if (session.closing) {
                closed(session.pickedCode)
                return@LaunchedEffect
            }
            // Subscribe before the source-hidden submission. Restarting this effect
            // through composition after that callback costs an extra frame.
            session.motionReady.await()
        }
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (durationScale <= 0f) motion.finish()
        else {
            var previous = withFrameNanos { it }
            LanguageMenuPresentationDiagnostics.event(session.handoff,
                if (session.closing) "close_motion_origin" else "open_motion_origin", previous)
            while (motion.isRunning) {
                val now = withFrameNanos { it }
                // Use actual elapsed time: a missed frame must not slow the spring down.
                motion.tick((now - previous) / 1_000_000_000.0 / durationScale)
                previous = now
                // bounds/localToScreen and both morph rectangles are in physical pixels.
                if (session.closing && motion.sourceReadyForHandoff(currentAnchor, currentMenu, 1f)) break
            }
        }
        if (session.closing) {
            // Reverse the opening window handoff: reveal the source first and
            // remove the popup only after that actual host-window frame submits.
            if (!session.handoff.restoreSource()) closed(session.pickedCode)
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
        val replacementLayer = remember(session) { LanguageMenuReplacementLayer() }
        val popupFrame = rememberLanguageMenuSubmittedFrame(session, LocalView.current, "popup") {
            session.handoff.popupFrameSubmitted()
        }
        CompositionLocalProvider(LocalGlassOverlay provides glass, LocalGlassOverlaySamplingHost provides parentScene,
            LocalGlassPopupScene provides popupScene, LocalGlassMaterialBackdrop provides null) {
            Box(Modifier.size(with(density) { windowSize.width.toDp() }, with(density) { windowSize.height.toDp() })
                .onGloballyPositioned {
                    popupOrigin = it.localToScreen(Offset.Zero)
                    if (!session.popupPositioned) {
                        session.popupPositioned = true
                        LanguageMenuPresentationDiagnostics.event(session.handoff, "popup_positioned")
                    }
                }
                .recordGlassPopupScene(popupScene)
                .pointerInput(session, menu) { detectTapGestures { point ->
                    if (!menu.contains(point)) session.close(null, select)
                } }) {
                if (anchor.width > 0f) {
                    // Bound the optical pass to the card/menu region rather than shading the whole window.
                    val carrier = Rect(minOf(geometry.anchor.left, geometry.material.left), minOf(geometry.anchor.top, geometry.material.top),
                        maxOf(geometry.anchor.right, geometry.material.right), maxOf(geometry.anchor.bottom, geometry.material.bottom))
                    val shape = LanguageMenuMorphShape(geometry.anchor.translate(-carrier.topLeft),
                        geometry.material.translate(-carrier.topLeft), motion.alpha, density.density)
                    // A retained placement clip keeps the source card's first-frame height
                    // during natural playback. Clip the current contours directly instead.
                    // While the two windows overlap, draw only the opaque replacement card.
                    // The parent scene still contains its original card until the second
                    // fence; sampling it here would refract the card back into itself.
                    Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(carrier.left.toInt(), carrier.top.toInt()) }
                        .requiredSize(with(density) { carrier.width.toDp() }, with(density) { carrier.height.toDp() })
                        // Allocate the optical node during the first popup layout,
                        // but never sample the source while its card is still present.
                        .drawWithContent { if (session.handoff.canAnimate) drawContent() }
                        .graphicsLayer { alpha = if (session.handoff.returningSource) 0f else motion.sourceMaterialAlpha }
                        .glassControl(shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu,
                            placementLayer = false)
                        .clip(shape).then(if (glass) Modifier else Modifier.background(colors.surfaceContainerHigh)))
                    Box(Modifier.align(AbsoluteAlignment.TopLeft).requiredSize(with(density) { anchor.width.toDp() }, with(density) { anchor.height.toDp() })
                        .drawWithContent {
                            drawContent()
                            if (replacementLayer.isVisibleAt(popupOrigin)) popupFrame.drawn()
                        }
                        .graphicsLayer {
                            // Position is known by the first draw after layout. Read
                            // it here rather than waiting to compose a replacement card.
                            val card = if (session.handoff.canAnimate) geometry.anchor
                                else anchorScreen.translate(-(popupOrigin ?: Offset.Zero))
                            transformOrigin = TransformOrigin.Center
                            translationX = card.center.x - anchor.width / 2f
                            translationY = card.center.y - anchor.height / 2f
                            scaleX = geometry.anchorContentScale
                            scaleY = geometry.anchorContentScale
                            alpha = if (popupOrigin == null) 0f else motion.anchorContentAlpha
                            replacementLayer.origin = popupOrigin
                            replacementLayer.alpha = alpha
                        }.clearAndSetSemantics {}) {
                        ChoiceCardContent(title, selectedLabel,
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
                    .dbgTag("${tagPrefix}_menu", clickable = false)
                    .semantics {
                        languageMenuHandoffState = session.handoff.state
                        paneTitle = title.ifEmpty { selectedLabel }
                        dismiss { session.close(null, select); true }
                        languageMenuVisualState = LanguageMenuVisualState(
                            geometry.content.width / menu.width.coerceAtLeast(1f),
                            geometry.content.height / menu.height.coerceAtLeast(1f), motion.alpha, motion.alpha,
                            with(density) { geometry.content.left.toDp().value }, with(density) { geometry.content.top.toDp().value })
                    }
                    .onGloballyPositioned { session.viewport = it }
                    .verticalScroll(rememberScrollState()).selectableGroup().padding(10.dp)) {
                    options.forEach { (code, label) ->
                        key(code) {
                            DisposableEffect(session, code) { onDispose { session.rows.remove(code) } }
                            val interaction = remember(code) { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            Row(Modifier.fillMaxWidth().heightIn(min = 42.dp)
                                .background(
                                    if (session.hoveredCode == code || pressed) colors.onSurface.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent,
                                    CircleShape,
                                )
                                .selectable(code == (session.pickedCode ?: selectedId), enabled = enabled && !session.closing,
                                    interactionSource = interaction, indication = null,
                                    role = Role.RadioButton, onClick = { session.close(code, select, canPick) })
                                .dbgTag("$tagPrefix#$code", text = label, role = "radio", clickable = enabled && !session.closing)
                                .onGloballyPositioned { coords ->
                                    session.rows[code] = coords
                                }.padding(start = 3.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(30.dp)) {
                                    if (code == (session.pickedCode ?: selectedId)) Icon(Icons.Outlined.Check, null,
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
}

/** Same two actual CPU contours; inward material depth remains a radial projection approximation. */
internal const val LanguageCardCornerDp = 16f
private val LanguageCardShape = FlClashGlassShape(LanguageCardCornerDp)

internal data class LanguageMenuMorphShape(val anchor: Rect, val menu: Rect, val progress: Float, val scale: Float) :
    LiquidGlassDualContourShape, LiquidGlassExactContourShape {
    private val sources: Pair<LiquidGlassContourOutline, LiquidGlassContourOutline> by lazy {
        fun path(rect: Rect, radius: Float): LiquidGlassContourOutline =
            LiquidGlassContourOutline(LiquidGlassSuperellipse.buildPath(LiquidGlassSuperellipse.Rect2(rect.left, rect.top, rect.right, rect.bottom),
                LiquidGlassSuperellipse.Radii.makeRadius(radius.coerceIn(0f, minOf(rect.width, rect.height) / 2f)))
            )
        path(anchor, LanguageCardCornerDp * scale) to
            path(menu, minOf(menu.width, menu.height) / 2f * (1f - progress) + 26f * scale * progress)
    }
    private val contours: Pair<Path, Path> by lazy { sources.first.path to sources.second.path }
    private val outline: Outline by lazy {
        Outline.Generic(Path.combine(PathOperation.Union, contours.first, contours.second))
    }

    override fun createOpticalContours(size: Size, layoutDirection: LayoutDirection, density: Density): Pair<Path, Path> = contours
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
    override fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>? {
        if (this.outline !== outline) return null
        val first = sources.first.exact ?: return null
        val second = sources.second.exact ?: return null
        return listOf(first, second)
    }
}
