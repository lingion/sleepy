package com.lingion.sleepy.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Material choice is independent of color palette, light/dark mode and navigation geometry. */
enum class UiStyle(val key: String) {
    Classic("classic"),
    LiquidGlass("liquid_glass");

    companion object {
        fun fromKey(key: String?): UiStyle = entries.firstOrNull { it.key == key } ?: Classic
    }
}

val LocalUiStyle = compositionLocalOf { UiStyle.Classic }
