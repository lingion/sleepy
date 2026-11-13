package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.FilledTonalButton as MaterialTonalButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.IconButton as MaterialIconButton
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

@Composable
private fun hasGlass() = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null

/** Shared dimensions keep the optical disc separate from its accessible touch target. */
object GlassControlLayout {
    val touchTarget = 48.dp
    val iconDisc = 36.dp
    val iconClearance = 6.dp
    val controlGap = 8.dp
    val denseClearance = 4.dp
}

/** The resting disc leaves 6dp around it; a held pane may float beyond the slot. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (hasGlass()) {
        val interaction = interactionSource ?: remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .defaultMinSize(GlassControlLayout.touchTarget, GlassControlLayout.touchTarget)
                .clickable(
                    interactionSource = interaction, indication = null,
                    enabled = enabled, role = Role.Button, onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor) {
                Box(
                    Modifier.size(GlassControlLayout.iconDisc).glassControl(
                        enabled = enabled, interactionSource = interaction, pressedContentAlpha = .5f,
                    ),
                    contentAlignment = Alignment.Center,
                ) { content() }
            }
        }
        return
    }
    MaterialIconButton(
        onClick = onClick,
        modifier = modifier.glassControl(enabled = enabled),
        enabled = enabled,
        colors = if (hasGlass()) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val resolvedShape = shape ?: ButtonDefaults.shape
    val resolvedColors = colors ?: ButtonDefaults.buttonColors()
    val resolvedPadding = contentPadding ?: ButtonDefaults.ContentPadding
    MaterialButton(
        onClick, modifier.glassControl(resolvedShape, resolvedColors.containerColor, enabled, pressedContentAlpha = .8f), enabled, resolvedShape,
        if (hasGlass()) resolvedColors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else resolvedColors,
        if (hasGlass()) null else elevation, border, resolvedPadding, interactionSource, content,
    )
}

@Composable
fun GlassTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val resolvedShape = shape ?: ButtonDefaults.filledTonalShape
    val resolvedColors = colors ?: ButtonDefaults.filledTonalButtonColors()
    val resolvedPadding = contentPadding ?: ButtonDefaults.ContentPadding
    MaterialTonalButton(
        onClick, modifier.glassControl(resolvedShape, resolvedColors.containerColor, enabled, pressedContentAlpha = .8f), enabled, resolvedShape,
        if (hasGlass()) resolvedColors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else resolvedColors,
        if (hasGlass()) null else elevation, border, resolvedPadding, interactionSource, content,
    )
}

@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val resolvedShape = shape ?: ButtonDefaults.textShape
    val resolvedColors = colors ?: ButtonDefaults.textButtonColors()
    val resolvedPadding = contentPadding ?: ButtonDefaults.TextButtonContentPadding
    MaterialTextButton(
        onClick, modifier.glassControl(resolvedShape, enabled = enabled, pressedContentAlpha = .5f), enabled, resolvedShape,
        if (hasGlass()) resolvedColors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else resolvedColors,
        elevation, border, resolvedPadding, interactionSource, content,
    )
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val resolvedShape = shape ?: ButtonDefaults.outlinedShape
    val resolvedColors = colors ?: ButtonDefaults.outlinedButtonColors()
    val resolvedPadding = contentPadding ?: ButtonDefaults.ContentPadding
    MaterialOutlinedButton(
        onClick, modifier.glassControl(resolvedShape, enabled = enabled, pressedContentAlpha = .5f), enabled, resolvedShape,
        if (hasGlass()) resolvedColors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else resolvedColors,
        elevation, if (hasGlass()) null else border, resolvedPadding, interactionSource, content,
    )
}
