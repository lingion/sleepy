package com.lingion.sleepy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalNavigationEntryActive = staticCompositionLocalOf { true }

/** Outgoing pages retain their last visible data throughout the navigation animation. */
@Composable
internal fun <T> rememberNavigationEntrySnapshot(value: T): T {
    val active = LocalNavigationEntryActive.current
    var lastVisible by remember { mutableStateOf(value) }
    SideEffect { if (active) lastVisible = value }
    return if (active) value else lastVisible
}
