package com.lingion.sleepy.ui.glass

import com.lingion.sleepy.ui.glass.ui.*

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.LiquidCourseCardBlurMax

/**
 * A backdrop sampling domain is an ownership boundary, not merely a visual label.
 * Providers from different domains must remain independent even when their pixels look similar.
 */
enum class GlassBackdropDomain {
    Background,
    Content,
    PickerScene,
    ChromeCombined,
    DialogBridge,
    ActivityBackground;

    val sourceDomains: Set<GlassBackdropDomain>
        get() = when (this) {
            ChromeCombined,
            DialogBridge -> setOf(Background, Content)

            else -> setOf(this)
        }
}

enum class GlassMaterialRole {
    Pill,
    Dialog,
    CourseCard,
    Popup,
    Editor,
    MorphShell,
    Control,
    Lens,
    SimpleBlur
}

enum class GlassRenderPhase {
    Preparing,
    Moving,
    Live,
    Closing,
    Released;

    val keepsTemporaryLayers: Boolean
        get() = this == Preparing || this == Moving || this == Closing

    val isInteractive: Boolean
        get() = this == Live

    fun canTransitionTo(next: GlassRenderPhase): Boolean = when (this) {
        Preparing -> next == Preparing || next == Moving || next == Live || next == Released
        Moving -> next == Moving || next == Preparing || next == Live || next == Closing || next == Released
        Live -> next == Live || next == Preparing || next == Moving || next == Closing || next == Released
        Closing -> next == Closing || next == Preparing || next == Moving || next == Live || next == Released
        Released -> next == Released || next == Preparing
    }
}

enum class GlassRendererKind {
    KyantReference,
    GroupedExperimental,
    StableEnvelopeExperimental
}

/** Stable identifiers shared by renderer policy, diagnostics and business-route adapters. */
object GlassSceneKeys {
    const val HomeThreeDotMenuMotion = "home-three-dot-menu"
    const val HomeMenuDestinationAddCourse = "home-menu-destination:add-course"
    const val HomeMenuDestinationManualImport = "home-menu-destination:manual-import"
    const val HomeMenuDestinationEduImport = "home-menu-destination:edu-import"
    const val HomePersonalizationProgressiveSurface = "home-personalization:progressive-surface"
    const val HomePersonalizationBackdropAura = "home-personalization:backdrop-aura"
    const val WeekCourseCards = "week-course-cards"

    val Phase2LargeSurfaceEnvelopeRoutes: Set<String> = setOf(
        HomeMenuDestinationAddCourse,
        HomeMenuDestinationManualImport,
        HomeMenuDestinationEduImport,
        HomePersonalizationProgressiveSurface,
        HomePersonalizationBackdropAura
    )

    val Phase2GroupedRoutes: Set<String> = setOf(WeekCourseCards)
}

/**
 * Feature switches are deliberately allow-list based. The reference renderer is always the
 * fallback, so adding framework plumbing cannot silently change an existing surface.
 */
@Immutable
data class GlassBackendPolicy(
    val groupedSceneAllowlist: Set<String> = emptySet(),
    val stableEnvelopeRouteAllowlist: Set<String> = emptySet()
) {
    fun rendererFor(sceneKey: String, requested: GlassRendererKind): GlassRendererKind = when (requested) {
        GlassRendererKind.GroupedExperimental -> if (sceneKey in groupedSceneAllowlist) {
            GlassRendererKind.GroupedExperimental
        } else {
            GlassRendererKind.KyantReference
        }

        GlassRendererKind.StableEnvelopeExperimental -> if (sceneKey in stableEnvelopeRouteAllowlist) {
            GlassRendererKind.StableEnvelopeExperimental
        } else {
            GlassRendererKind.KyantReference
        }

        GlassRendererKind.KyantReference -> GlassRendererKind.KyantReference
    }

    companion object {
        val ReferenceOnly = GlassBackendPolicy()

        /** Production policy for large glass surfaces and grouped week-course rendering. */
        val LargeGlass = GlassBackendPolicy(
            groupedSceneAllowlist = GlassSceneKeys.Phase2GroupedRoutes,
            stableEnvelopeRouteAllowlist = GlassSceneKeys.Phase2LargeSurfaceEnvelopeRoutes
        )
    }
}

/** Complete, immutable material tokens. Dynamic animation values live in [GlassEffectFrame]. */
@Immutable
data class GlassMaterialSpec(
    val role: GlassMaterialRole,
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float = 0.06f,
    val shadowAlpha: Float = 0.16f,
    val innerShadowAlpha: Float = 0.12f,
    val chromaticAberration: Boolean = false,
    val depthEffect: Boolean = true,
    val useVibrancy: Boolean = true
) {
    companion object {
        fun pill(intensity: Float = 1f, reduceTransparency: Boolean = false) = GlassMaterialSpec(
            role = GlassMaterialRole.Pill,
            blur = if (reduceTransparency) 0.dp else (2.5f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensHeight = if (reduceTransparency) 0.dp else (12f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensAmount = if (reduceTransparency) 0.dp else (24f * intensity.coerceIn(0.4f, 1.5f)).dp,
            surfaceAlpha = if (reduceTransparency) 0.86f else 0.18f,
            borderAlpha = if (reduceTransparency) 0.18f else 0.32f,
            highlightAlpha = if (reduceTransparency) 0.04f else 0.055f,
            shadowAlpha = if (reduceTransparency) 0.08f else 0.14f,
            innerShadowAlpha = if (reduceTransparency) 0.05f else 0.09f
        )

        fun dialog(intensity: Float = 1f, reduceTransparency: Boolean = false) = GlassMaterialSpec(
            role = GlassMaterialRole.Dialog,
            blur = if (reduceTransparency) 0.dp else (4f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensHeight = if (reduceTransparency) 0.dp else (16f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensAmount = if (reduceTransparency) 0.dp else (32f * intensity.coerceIn(0.4f, 1.5f)).dp,
            surfaceAlpha = if (reduceTransparency) 0.92f else 0.40f,
            borderAlpha = if (reduceTransparency) 0.16f else 0.28f,
            highlightAlpha = if (reduceTransparency) 0.04f else 0.06f,
            shadowAlpha = if (reduceTransparency) 0.08f else 0.18f,
            innerShadowAlpha = if (reduceTransparency) 0.05f else 0.11f
        )

        fun courseCard(blur: Float, reduceTransparency: Boolean = false) = GlassMaterialSpec(
            role = GlassMaterialRole.CourseCard,
            blur = if (reduceTransparency) 0.dp else blur.coerceIn(0f, LiquidCourseCardBlurMax).dp,
            lensHeight = if (reduceTransparency) 0.dp else 10.dp,
            lensAmount = if (reduceTransparency) 0.dp else 20.dp,
            surfaceAlpha = if (reduceTransparency) 0.92f else 0.52f,
            borderAlpha = if (reduceTransparency) 0.14f else 0.24f,
            highlightAlpha = if (reduceTransparency) 0.035f else 0.045f,
            shadowAlpha = if (reduceTransparency) 0.08f else 0.14f,
            innerShadowAlpha = if (reduceTransparency) 0.05f else 0.10f
        )

        fun lens() = GlassMaterialSpec(
            role = GlassMaterialRole.Lens,
            blur = 3.dp,
            lensHeight = 24.dp,
            lensAmount = 34.dp,
            surfaceAlpha = 0.08f,
            borderAlpha = 0f,
            highlightAlpha = 0.18f,
            shadowAlpha = 0.34f,
            innerShadowAlpha = 0.34f,
            depthEffect = false
        )

        fun popup(blur: Dp) = GlassMaterialSpec(
            role = GlassMaterialRole.Popup,
            blur = blur,
            lensHeight = 12.dp,
            lensAmount = 24.dp,
            surfaceAlpha = 0.34f,
            borderAlpha = 0.24f,
            highlightAlpha = 0.06f,
            shadowAlpha = 0.16f,
            innerShadowAlpha = 0.10f,
            depthEffect = true,
            useVibrancy = true
        )

        fun simpleBlur(blur: Dp) = GlassMaterialSpec(
            role = GlassMaterialRole.SimpleBlur,
            blur = blur,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceAlpha = 0f,
            borderAlpha = 0f,
            highlightAlpha = 0.10f,
            shadowAlpha = 0.12f,
            innerShadowAlpha = 0.08f,
            depthEffect = false,
            useVibrancy = false
        )
    }
}

enum class GlassHighlightStyle {
    Default,
    Plain
}

@Immutable
data class GlassHighlightFrame(
    val style: GlassHighlightStyle,
    val alpha: Float
)

@Immutable
data class GlassInnerShadowFrame(
    val radius: Dp,
    val alpha: Float
)

/** Exact values consumed by one draw pass; no layout size is encoded here. */
@Immutable
data class GlassEffectFrame(
    val blur: Dp?,
    val lensHeight: Dp? = null,
    val lensAmount: Dp? = null,
    val useVibrancy: Boolean = false,
    // Matches Backdrop 2.0 lens() default. Token-backed surfaces opt in explicitly.
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
    val highlight: GlassHighlightFrame? = null,
    val shadowAlpha: Float? = null,
    val innerShadow: GlassInnerShadowFrame? = null,
    /** Null means no layerBlock at all; 1f keeps an existing identity transform node. */
    val layerScale: Float? = null
)

@Immutable
data class GlassSurfaceDescriptor(
    val id: String,
    val domain: GlassBackdropDomain,
    val materialRole: GlassMaterialRole,
    val requestedRenderer: GlassRendererKind = GlassRendererKind.KyantReference,
    val sceneKey: String = id
)

@Immutable
data class GlassCoordinateTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f
)
