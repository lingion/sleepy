package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.FilterChip as MaterialFilterChip
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/** Same chip semantics and geometry, with a selected optical surface instead of a painted fill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    MaterialFilterChip(
        selected, onClick, label, modifier.glassControl(shape, enabled = enabled, selected = selected),
        enabled, leadingIcon, trailingIcon, shape,
        if (glass) colors.copy(
            containerColor = Color.Transparent, selectedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent, disabledSelectedContainerColor = Color.Transparent,
        ) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        interactionSource = interactionSource,
    )
}
