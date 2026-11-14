package com.lingion.sleepy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton
import com.lingion.sleepy.ui.component.liquidglass.GlassTextButton
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/** Business accordions share interaction and geometry, while each style owns its motion. */
@Composable
internal fun <T> accordionSpatialSpec(): FiniteAnimationSpec<T> =
    if (LocalUiStyle.current == UiStyle.LiquidGlass) {
        // Application content transition, not an iOS native-control measurement.
        spring(dampingRatio = 1f, stiffness = 380f)
    } else {
        MaterialTheme.motionScheme.fastSpatialSpec()
    }

@Composable
internal fun accordionEffectsSpec(): FiniteAnimationSpec<Float> =
    if (LocalUiStyle.current == UiStyle.LiquidGlass) tween(180)
    else MaterialTheme.motionScheme.fastEffectsSpec()

@Composable
fun AccordionChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = accordionSpatialSpec(),
        label = "accordion-chevron",
    )
    Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(24.dp).rotate(rotation),
    )
}

@Composable
fun AccordionIconButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val action = stringResource(if (expanded) R.string.collapse else R.string.expand)
    val state = stringResource(if (expanded) R.string.accordion_expanded else R.string.accordion_collapsed)
    GlassIconButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp).semantics {
            stateDescription = state
            contentDescription = action
        },
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        AccordionChevron(expanded)
    }
}

@Composable
fun AccordionTextButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val state = stringResource(if (expanded) R.string.accordion_expanded else R.string.accordion_collapsed)
    GlassTextButton(
        onClick = onToggle,
        modifier = modifier.heightIn(min = 48.dp).semantics { stateDescription = state },
        enabled = enabled,
    ) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        AccordionChevron(expanded)
    }
}

@Composable
fun Modifier.accordionToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val action = stringResource(if (expanded) R.string.collapse else R.string.expand)
    val state = stringResource(if (expanded) R.string.accordion_expanded else R.string.accordion_collapsed)
    val indication = if (LocalUiStyle.current == UiStyle.LiquidGlass) null else LocalIndication.current
    return semantics { stateDescription = state }.clickable(
        interactionSource = source,
        indication = indication,
        enabled = enabled,
        role = Role.Button,
        onClickLabel = action,
        onClick = onToggle,
    )
}

/**
 * The header exposes one accessible toggle; independent actions such as Delete remain children.
 * Content below the header never inherits its click action.
 */
@Composable
fun AccordionHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .accordionToggle(expanded, onToggle, enabled, source)
            .padding(contentPadding)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { content() }
        actions()
        AccordionIconButton(
            expanded = expanded,
            onToggle = onToggle,
            enabled = enabled,
            interactionSource = source,
            modifier = Modifier
                .focusProperties { canFocus = false }
                .clearAndSetSemantics { },
        )
    }
}

@Composable
fun AccordionContent(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val spatial = accordionSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val effects = accordionEffectsSpec()
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(spatial, expandFrom = Alignment.Top) + fadeIn(effects),
        exit = shrinkVertically(spatial, shrinkTowards = Alignment.Top) + fadeOut(effects),
        content = content,
    )
}

/** Partial lists keep their scroll container and animate only the changed measured height. */
@Composable
fun Modifier.accordionContentSize(): Modifier = animateContentSize(accordionSpatialSpec())
