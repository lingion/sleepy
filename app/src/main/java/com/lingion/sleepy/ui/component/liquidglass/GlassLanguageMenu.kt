package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** 整行打开，当前值提供锚点；选择回调只在浮层退出组合后执行。 */
@Composable
fun GlassLanguageMenu(
    title: String,
    selectedCode: String,
    languages: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentLabel = languages.firstOrNull { it.first == selectedCode }?.second ?: selectedCode
    var expanded by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    Row(
        modifier = modifier.fillMaxWidth().clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .clickable(enabled = !expanded && !completing, role = Role.Button) { expanded = true }
            .dbgTag("general_language_card", text = currentLabel, role = "button")
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Box(Modifier.widthIn(max = 180.dp).dbgTag("general_language_value", text = currentLabel, clickable = false)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    currentLabel, Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            if (expanded) {
                LanguagePopup(title, selectedCode, languages) { code ->
                    completing = true
                    expanded = false
                    scope.launch {
                        // 留一帧移除 Popup window，再允许 prefs/recreate 改变父场景。
                        withFrameNanos { }
                        completing = false
                        if (code != null) select(code)
                    }
                }
            }
        }
    }
}

private data class LanguagePopupGeometry(
    val anchor: IntRect = IntRect.Zero,
    val origin: IntOffset = IntOffset.Zero,
)

@Composable
private fun LanguagePopup(
    title: String,
    selectedCode: String,
    languages: List<Pair<String, String>>,
    onClosed: (String?) -> Unit,
) {
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    // 先捕获父场景，再发布本层录制：本层永远不采样自身。
    val parentScene = LocalGlassPopupScene.current ?: LocalGlassHost.current
    val popupScene = rememberGlassHostState()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safe = WindowInsets.safeDrawing
    val margin = with(density) { 16.dp.roundToPx() }
    val safeLeft = safe.getLeft(density, LayoutDirection.Ltr)
    val safeRight = safe.getRight(density, LayoutDirection.Ltr)
    val safeTop = safe.getTop(density)
    val safeBottom = safe.getBottom(density)
    val maxWidth = with(density) { (windowSize.width - safeLeft - safeRight - margin * 2).coerceAtLeast(1).toDp() }
    val maxHeight = with(density) { (windowSize.height - safeTop - safeBottom - margin * 2).coerceAtLeast(1).toDp() }
    var geometry by remember { mutableStateOf(LanguagePopupGeometry()) }
    val positionProvider = remember(margin, safeLeft, safeRight, safeTop, safeBottom) {
        LanguageMenuPositionProvider(margin, safeLeft, safeTop, safeRight, safeBottom) { anchor, origin ->
            geometry = LanguagePopupGeometry(anchor, origin)
        }
    }
    var closing by remember { mutableStateOf(false) }
    var pickedCode by remember { mutableStateOf<String?>(null) }
    val closed by rememberUpdatedState(onClosed)
    fun close(code: String? = null) {
        if (closing) return
        pickedCode = code
        closing = true
    }
    val progress = remember { Animatable(0f) }
    val corners = remember { Animatable(1f) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(200))
            closed(pickedCode)
        } else {
            launch {
                // popup.dart: 圆角比展开晚 70ms，324/.75 弹簧从胶囊过渡至菜单。
                delay(70)
                corners.animateTo(0f, spring(dampingRatio = .75f, stiffness = 324f))
            }
            progress.animateTo(1f, spring(dampingRatio = .754f, stiffness = 400f))
        }
    }
    val width = minOf(280.dp, maxWidth)
    val carrierShape = FlClashGlassShape(24f + width.value * .48f * corners.value.coerceAtLeast(0f))
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { close() },
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        CompositionLocalProvider(
            LocalGlassOverlay provides glass,
            LocalGlassOverlaySamplingHost provides parentScene,
            LocalGlassPopupScene provides popupScene,
        ) {
            Column(
                Modifier.width(width).heightIn(max = minOf(420.dp, maxHeight))
                    .graphicsLayer {
                        val p = progress.value
                        val anchor = geometry.anchor
                        val anchorCenter = Offset(
                            (anchor.left + anchor.right) / 2f - geometry.origin.x,
                            (anchor.top + anchor.bottom) / 2f - geometry.origin.y,
                        )
                        val startScale = (.41f * min(anchor.width, anchor.height) / size.width.coerceAtLeast(1f)).coerceIn(.01f, 1f)
                        val scale = startScale + (1f - startScale) * p
                        scaleX = scale
                        scaleY = scale * (size.width + (size.height - size.width) * p) / size.height.coerceAtLeast(1f)
                        transformOrigin = TransformOrigin.Center
                        translationX = (anchorCenter.x - size.width / 2f) * (1f - p)
                        translationY = (anchorCenter.y - size.height / 2f) * (1f - p)
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .recordGlassPopupScene(popupScene)
                    .glassControl(shape = carrierShape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu)
                    .clip(carrierShape)
                    .then(if (glass) Modifier else Modifier.background(colors.surfaceContainerHigh))
                    .dbgTag("general_language_menu", clickable = false)
                    .semantics {
                        paneTitle = title
                        dismiss { close(); true }
                    }
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
                    .padding(8.dp),
            ) {
                languages.forEach { (code, label) ->
                    val selected = code == selectedCode
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clip(SleepyTheme.shapes.medium)
                            .selectable(selected = selected, enabled = !closing, role = Role.RadioButton, onClick = { close(code) })
                            .dbgTag("general_language#$code", text = label, role = "radio", clickable = !closing)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** 与参考 overAnchorEnd 相同的 8dp 重叠；逻辑 end 随 RTL 翻转，并夹在窗口安全边界内。 */
private class LanguageMenuPositionProvider(
    private val margin: Int,
    private val safeLeft: Int,
    private val safeTop: Int,
    private val safeRight: Int,
    private val safeBottom: Int,
    private val onPosition: (IntRect, IntOffset) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left = safeLeft + margin
        val top = safeTop + margin
        val maxX = (windowSize.width - safeRight - margin - popupContentSize.width).coerceAtLeast(left)
        val maxY = (windowSize.height - safeBottom - margin - popupContentSize.height).coerceAtLeast(top)
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val position = IntOffset(x.coerceIn(left, maxX), (anchorBounds.top - margin / 2).coerceIn(top, maxY))
        onPosition(anchorBounds, position)
        return position
    }
}
