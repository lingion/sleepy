package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.UiStyle
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem

/** Scoped to the action slots of a glass alert, never to its arbitrary body content. */
internal val LocalGlassAlertAction = staticCompositionLocalOf { false }

private object NativeAlertActionData : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = NativeAlertActionData
}

/**
 * Dimensions come from the recorded iOS 26.5 alert's accessibility frames. The 17/16sp
 * type sizes and weights are visual fits: accessibility does not expose UIFont sizes.
 * Caller Text styles, content colors and custom slots continue to take precedence.
 */
@Composable
internal fun NativeGlassAlertContent(
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    iconContentColor: Color,
    titleContentColor: Color,
    textContentColor: Color,
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
) {
    Column(modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        if (icon != null || title != null || text != null) {
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .padding(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (icon != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) { icon() }
                    }
                    if (title != null || text != null) Spacer(Modifier.height(7.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp, lineHeight = 21.sp,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start,
                        )) { title() }
                    }
                    if (text != null) Spacer(Modifier.height(7.dp))
                }
                if (text != null) {
                    // Bound the slot without introducing a second scroll owner. Existing
                    // scrollable forms/changelogs must receive a finite viewport here.
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp, lineHeight = 19.sp, textAlign = TextAlign.Start,
                            )) { text() }
                        }
                    }
                }
            }
        }
        NativeGlassAlertActions(dismissButton, confirmButton)
    }
}

private enum class AlertActionSlot { Dismiss, Confirm }

/** Equal native cells for our buttons; custom multi-node slots retain their own sizes. */
@Composable
private fun NativeGlassAlertActions(
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { constraints ->
        val dismiss = subcompose(AlertActionSlot.Dismiss) {
            CompositionLocalProvider(LocalGlassAlertAction provides true) { dismissButton?.invoke() }
        }
        val confirm = subcompose(AlertActionSlot.Confirm) {
            CompositionLocalProvider(LocalGlassAlertAction provides true) { confirmButton() }
        }
        val nodes = dismiss + confirm
        val width = constraints.maxWidth
        if (nodes.isEmpty()) return@SubcomposeLayout layout(width, 0) {}
        val inset = 16.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val available = (width - inset * 2).coerceAtLeast(0)
        val equalCells = dismiss.size <= 1 && confirm.size <= 1 &&
            nodes.all { it.parentData === NativeAlertActionData }
        if (equalCells) {
            val cellWidth = ((available - gap * (nodes.size - 1)) / nodes.size).coerceAtLeast(0)
            val children = nodes.map { it.measure(Constraints(maxWidth = cellWidth)) }
            val rowHeight = children.maxOf { it.height }
            layout(width, inset * 2 + rowHeight) {
                children.forEachIndexed { index, child ->
                    child.placeRelative(inset + index * (cellWidth + gap) + (cellWidth - child.width) / 2, inset + (rowHeight - child.height) / 2)
                }
            }
        } else {
            // Measure once, then wrap complete custom nodes. The surrounding scroll
            // viewport also keeps very tall custom actions accessible on small displays.
            val children = nodes.map { it.measure(Constraints(maxWidth = available)) }
            val rows = mutableListOf<MutableList<Int>>()
            var occupied = 0
            children.forEachIndexed { index, child ->
                if (rows.isEmpty() || (occupied > 0 && occupied + gap + child.width > available)) {
                    rows += mutableListOf<Int>()
                    occupied = 0
                }
                rows.last() += index
                occupied += (if (occupied > 0) gap else 0) + child.width
            }
            val rowHeights = rows.map { row -> row.maxOf { children[it].height } }
            layout(width, inset * 2 + rowHeights.sum() + gap * (rows.size - 1)) {
                var y = inset
                rows.forEachIndexed { rowIndex, row ->
                    val rowWidth = row.sumOf { children[it].width } + gap * (row.size - 1)
                    var x = width - inset - rowWidth
                    row.forEach { index ->
                        val child = children[index]
                        child.placeRelative(x, y + (rowHeights[rowIndex] - child.height) / 2)
                        x += child.width + gap
                    }
                    y += rowHeights[rowIndex] + gap
                }
            }
        }
    }
}

/** Material still owns activation, focus, disabled semantics and press interactions. */
@Composable
internal fun NativeGlassAlertActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    colors: ButtonColors?,
    elevation: ButtonElevation?,
    border: BorderStroke?,
    contentPadding: PaddingValues?,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    MaterialButton(
        onClick = onClick,
        modifier = modifier.then(NativeAlertActionData).fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = shape ?: CircleShape,
        colors = colors ?: ButtonDefaults.buttonColors(
            containerColor = primary.copy(alpha = .12f), contentColor = primary,
        ),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding ?: PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        interactionSource = interactionSource,
    ) {
        // Empirical visual fit, not an extracted native UIFont measurement.
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(
            fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )) { content() }
    }
}

/** Source-compatible with the non-selectable Material 3 menu item overload. */
@Composable
fun GlassDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    if (!glass) {
        MaterialDropdownMenuItem(
            text, onClick, modifier, leadingIcon, trailingIcon, enabled, colors,
            contentPadding ?: MenuDefaults.DropdownMenuItemContentPadding, interactionSource,
        )
        return
    }
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Recorded native rows use a 10pt horizontal inset and a capsule. These
    // onSurface alphas are visual fits, not Apple constants. Dark held captures
    // also change the whole carrier; this local fill does not reproduce that.
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalDarkTheme.current) .20f else .08f)
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 42.dp)
            .background(if (container == Color.Unspecified) Color.Transparent else container)
            .drawBehind {
                if (enabled && pressed) {
                    val inset = 10.dp.toPx().coerceAtMost(size.width / 2)
                    drawRoundRect(
                        color = highlight, topLeft = Offset(inset, 0f),
                        size = Size((size.width - inset * 2).coerceAtLeast(0f), size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(contentPadding ?: PaddingValues(horizontal = 28.dp, vertical = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.leadingIconColor else colors.disabledLeadingIconColor) {
                leadingIcon()
            }
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.textColor else colors.disabledTextColor) {
                // 17sp regular is a visual fit; 42dp row height is from native AX frames.
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
                )) { text() }
            }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(12.dp))
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor) {
                trailingIcon()
            }
        }
    }
}
