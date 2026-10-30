@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

@Stable
internal class PersonalizationPreviewState {
    var wallpaperBlur by mutableStateOf<Float?>(null)
        private set
    var wallpaperBrightness by mutableStateOf<Float?>(null)
        private set
    var cardAlpha by mutableStateOf<Float?>(null)
        private set
    var cardBlur by mutableStateOf<Float?>(null)
        private set
    var cardRefractionStrength by mutableStateOf<Float?>(null)
        private set
    var cardFontScale by mutableStateOf<Float?>(null)
        private set
    var weekCardCornerProgress by mutableStateOf<Float?>(null)
        private set

    fun update(key: String, candidate: ScheduleConfigEntity) { /* stub */ }
    fun clear(key: String) { /* stub */ }
    fun clearAll() { /* stub */ }
}

internal val LocalPersonalizationPreview =
    staticCompositionLocalOf<PersonalizationPreviewState?> { null }
