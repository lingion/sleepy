package com.lingion.sleepy.ui.component

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Material selection roles shared by segmented tabs and both floating Dock materials. */
internal data class SelectionColors(
    val selectedContainer: Color,
    val selectedContent: Color,
    val unselectedContent: Color,
    val accent: Color,
)

/** Optical carriers consume the same tokens without tinting or deriving a second palette. */
internal fun ColorScheme.selectionColors() = SelectionColors(
    selectedContainer = secondaryContainer,
    selectedContent = onSecondaryContainer,
    unselectedContent = onSurfaceVariant,
    accent = primary,
)
