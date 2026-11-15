package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.FilledTonalButton as MaterialTonalButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.IconButton as MaterialIconButton
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

@Composable
private fun hasGlass() = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null

/**
 * Keeps a Sleepy design-system Material preset from overriding the glass button's
 * default silhouette. Use only for those shared presets; pass deliberate custom
 * shapes directly to the button so their meaning is retained in both styles.
 */
@Composable
fun glassButtonShape(materialShape: Shape): Shape? = if (hasGlass()) null else materialShape

/** Shared dimensions keep the optical disc separate from its accessible touch target. */
object GlassControlLayout {
    val touchTarget = 48.dp
    val iconDisc = 36.dp
    val iconClearance = 6.dp
    val controlGap = 8.dp
    val denseClearance = 4.dp
}

private enum class StandaloneGlassButtonRole { Prominent, Tonal, Regular }

private object StandaloneGlassButtonDefaults {
    // The eight iOS 26.5 rest AX captures report 33.5pt for both text buttons.
    // AX is not an optical-contour measurement; this is the initial body-height
    // target, separate from Android's 48dp accessible touch target.
    val minimumBodyHeight = 33.5.dp

    // Engineering candidates, not measured Apple contentInsets. Width remains
    // content-driven and explicit padding / large text may increase body height.
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

    // The reference requests .system(size:16). Android's default font is not SF;
    // do not inherit Material's label line-height, tracking or weight here.
    val textStyle = TextStyle.Default.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal)
}

/** A standalone lens owns its body; the surrounding hit target owns activation. */
@Composable
private fun StandaloneGlassButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    role: StandaloneGlassButtonRole,
    shape: Shape?,
    colors: ButtonColors?,
    border: BorderStroke?,
    contentPadding: PaddingValues?,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val palette = MaterialTheme.colorScheme
    val bodyShape = shape ?: CircleShape
    val containerColor = colors?.let {
        if (enabled) it.containerColor else it.disabledContainerColor
    } ?: when (role) {
        StandaloneGlassButtonRole.Prominent -> palette.primary
        StandaloneGlassButtonRole.Tonal -> palette.primaryContainer
        StandaloneGlassButtonRole.Regular -> Color.Transparent
    }
    val contentColor = colors?.let {
        if (enabled) it.contentColor else it.disabledContentColor
    } ?: if (!enabled) {
        // Android disabled contrast policy; this is not a captured iOS constant.
        palette.onSurface.copy(alpha = .38f)
    } else when (role) {
        StandaloneGlassButtonRole.Prominent -> palette.onPrimary
        StandaloneGlassButtonRole.Tonal -> palette.onPrimaryContainer
        StandaloneGlassButtonRole.Regular -> palette.primary
    }
    val tint = containerColor.takeUnless { it == Color.Unspecified || it.alpha == 0f }
    val glassInteraction = remember(tint != null) { GlassControlInteraction(tint != null) }
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides StandaloneGlassButtonDefaults.textStyle,
    ) {
        Layout(
            modifier = modifier.observeGlassControlPointers(
                interaction = glassInteraction,
                enabled = enabled,
                interactionSource = interaction,
            ).clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
            content = {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = StandaloneGlassButtonDefaults.minimumBodyHeight)
                        .glassControl(
                            shape = bodyShape,
                            tint = tint,
                            enabled = enabled,
                            interactionSource = interaction,
                            sharedInteraction = glassInteraction,
                            pressedContentAlpha = if (role == StandaloneGlassButtonRole.Regular) .5f else .8f,
                        )
                        .then(if (border == null) Modifier else Modifier.border(border, bodyShape))
                        .padding(contentPadding ?: StandaloneGlassButtonDefaults.contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            },
        ) { measurables, constraints ->
            // Preserve the caller's width constraints, including fillMaxWidth.
            // A fixed / minimum outer height must not become the lens height.
            val body = measurables.single().measure(constraints.copy(minHeight = 0))
            val target = GlassControlLayout.touchTarget.roundToPx()
            val width = constraints.constrainWidth(maxOf(target, body.width))
            val height = constraints.constrainHeight(maxOf(target, body.height))
            val bodyX = (width - body.width) / 2
            val bodyY = (height - body.height) / 2
            glassInteraction.bodySize = IntSize(body.width, body.height)
            glassInteraction.bodyOffset = androidx.compose.ui.geometry.Offset(
                (if (layoutDirection == LayoutDirection.Rtl) width - body.width - bodyX else bodyX).toFloat(),
                bodyY.toFloat(),
            )
            layout(width, height) {
                body.placeRelative(bodyX, bodyY)
            }
        }
    }
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
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
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
    elevation: ButtonElevation? = if (hasGlass()) null
        else ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!hasGlass()) {
        MaterialButton(
            onClick = onClick,
            shapes = if (shape == null) ButtonDefaults.shapes() else ButtonDefaults.shapes(shape, shape),
            modifier = modifier,
            enabled = enabled,
            colors = colors ?: ButtonDefaults.buttonColors(),
            elevation = elevation,
            border = border,
            contentPadding = contentPadding ?: ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    StandaloneGlassButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        role = StandaloneGlassButtonRole.Prominent,
        shape = shape, colors = colors, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content,
    )
}

@Composable
fun GlassTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = if (hasGlass()) null
        else ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!hasGlass()) {
        MaterialTonalButton(
            onClick = onClick,
            shapes = if (shape == null) ButtonDefaults.shapes() else ButtonDefaults.shapes(shape, shape),
            modifier = modifier,
            enabled = enabled,
            colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
            elevation = elevation,
            border = border,
            contentPadding = contentPadding ?: ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    StandaloneGlassButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        role = StandaloneGlassButtonRole.Tonal,
        shape = shape, colors = colors, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content,
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
    if (!hasGlass()) {
        MaterialTextButton(
            onClick = onClick,
            shapes = if (shape == null) ButtonDefaults.shapes() else ButtonDefaults.shapes(shape, shape),
            modifier = modifier,
            enabled = enabled,
            colors = colors ?: ButtonDefaults.textButtonColors(),
            elevation = elevation,
            border = border,
            contentPadding = contentPadding ?: ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    StandaloneGlassButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        role = StandaloneGlassButtonRole.Regular,
        shape = shape, colors = colors, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content,
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
    border: BorderStroke? = if (hasGlass()) null
        else ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!hasGlass()) {
        MaterialOutlinedButton(
            onClick = onClick,
            shapes = if (shape == null) ButtonDefaults.shapes() else ButtonDefaults.shapes(shape, shape),
            modifier = modifier,
            enabled = enabled,
            colors = colors ?: ButtonDefaults.outlinedButtonColors(),
            elevation = elevation,
            border = border,
            contentPadding = contentPadding ?: ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    if (hasGlass() && LocalGlassAlertAction.current) {
        NativeGlassAlertActionButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    StandaloneGlassButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        role = StandaloneGlassButtonRole.Regular,
        shape = shape, colors = colors, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content,
    )
}
