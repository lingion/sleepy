package com.lingion.sleepy.ui.glass.performance

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class AppMaterialLevel { QUALITY, PERFORMANCE, SUPER_PERFORMANCE }

/** Full-screen scene blur is intentionally independent of dense control materials. */
enum class MaterialUsage { CONTROL, SCENE_BLUR }

/** A restore can import appearance values, but cannot lift the device's active material guard. */
internal fun restoredMaterialLevel(current: AppMaterialLevel, archived: AppMaterialLevel): AppMaterialLevel =
    if (current == AppMaterialLevel.SUPER_PERFORMANCE) current else archived

@androidx.compose.runtime.Composable
internal fun effectiveAppMaterialPolicy(): EffectiveMaterialPolicy = AppMaterialPreferences.policy

data class EffectiveMaterialPolicy(val level: AppMaterialLevel) {
    val denseMaterials: Boolean get() = level != AppMaterialLevel.SUPER_PERFORMANCE
    val simplifiedProgressiveBlur: Boolean get() = level != AppMaterialLevel.QUALITY
    fun samples(usage: MaterialUsage): Boolean = denseMaterials || usage == MaterialUsage.SCENE_BLUR
    fun courseSamples(glass: Boolean, gaussian: Boolean, hasWallpaper: Boolean): Boolean =
        denseMaterials && hasWallpaper && (glass || gaussian)
    fun opacity(blur: Float, maximum: Float): Float =
        0.15f + 0.85f * (if (blur.isFinite()) blur / maximum.coerceAtLeast(1f) else 1f).coerceIn(0f, 1f)
}

/** Device-wide rendering preference; schedule appearance values remain intact. */
object AppMaterialPreferences {
    private const val Prefs = "app_material_preferences"
    var level by mutableStateOf(AppMaterialLevel.QUALITY)
        private set
    val isPerformance: Boolean get() = level == AppMaterialLevel.PERFORMANCE
    val isSuperPerformance: Boolean get() = level == AppMaterialLevel.SUPER_PERFORMANCE
    val policy: EffectiveMaterialPolicy get() = EffectiveMaterialPolicy(level)

    fun load(context: Context) {
        val saved = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString("level", null)
        level = AppMaterialLevel.entries.firstOrNull { it.name == saved } ?: AppMaterialLevel.QUALITY
    }

    fun setLevel(context: Context, value: AppMaterialLevel) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit { putString("level", value.name) }
        level = value
    }
}
