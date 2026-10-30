package com.lingion.sleepy.ui.glass

import com.lingion.sleepy.ui.glass.ui.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.kyant.shapes.RoundedRectangle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil
import kotlin.math.floor

internal const val GlassGroupMaximumMembers = 8

@Immutable
data class GlassGroupCandidate(
    val id: String,
    val domain: GlassBackdropDomain,
    val materialKey: String,
    val boundsInViewport: Rect,
    val cornerRadiusPx: Float
)

@Immutable
data class GlassGroupPlan(
    val domain: GlassBackdropDomain,
    val materialKey: String,
    val viewport: Rect,
    val members: List<GlassGroupCandidate>
)

/** A tightly bounded layer plus member geometry translated into that layer's local pixels. */
@Immutable
data class GlassGroupLayerPlan(
    val offsetInViewport: IntOffset,
    val size: IntSize,
    val localPlan: GlassGroupPlan
)

fun GlassGroupPlan.toTightLayerPlan(): GlassGroupLayerPlan {
    require(members.isNotEmpty()) { "A GlassGroup layer requires at least one member." }
    val left = floor(members.minOf { it.boundsInViewport.left }).toInt()
    val top = floor(members.minOf { it.boundsInViewport.top }).toInt()
    val right = ceil(members.maxOf { it.boundsInViewport.right }).toInt()
    val bottom = ceil(members.maxOf { it.boundsInViewport.bottom }).toInt()
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)
    val localMembers = members.map { member ->
        val bounds = member.boundsInViewport
        member.copy(
            boundsInViewport = Rect(
                left = bounds.left - left,
                top = bounds.top - top,
                right = bounds.right - left,
                bottom = bounds.bottom - top
            )
        )
    }
    return GlassGroupLayerPlan(
        offsetInViewport = IntOffset(left, top),
        size = IntSize(width, height),
        localPlan = copy(
            viewport = Rect(0f, 0f, width.toFloat(), height.toFloat()),
            members = localMembers
        )
    )
}

/** Greedy interval coloring: overlapping cards never share one effect layer. */
object GlassGroupPlanner {
    private data class SpatialGroup(
        val members: MutableList<GlassGroupCandidate>,
        var bounds: Rect,
        var coveredArea: Float
    )

    private data class SpatialJoin(
        val groupIndex: Int,
        val bounds: Rect,
        val coveredArea: Float,
        val fillRatio: Float,
        val incrementalArea: Float
    )

    fun plan(
        viewport: Rect,
        candidates: List<GlassGroupCandidate>,
        maxMembersPerPlan: Int = Int.MAX_VALUE
    ): List<GlassGroupPlan> = candidates
        .also { require(maxMembersPerPlan > 0) { "GlassGroup plan size must be positive." } }
        .asSequence()
        .filter { it.boundsInViewport.overlaps(viewport) }
        .groupBy { it.domain to it.materialKey }
        .flatMap { (key, sameMaterial) ->
            val groups = mutableListOf<MutableList<GlassGroupCandidate>>()
            sameMaterial.sortedWith(
                compareBy<GlassGroupCandidate> { it.boundsInViewport.top }
                    .thenBy { it.boundsInViewport.left }
            ).forEach { candidate ->
                val target = groups.firstOrNull { group ->
                    group.none { existing -> existing.boundsInViewport.overlaps(candidate.boundsInViewport) }
                } ?: mutableListOf<GlassGroupCandidate>().also(groups::add)
                target += candidate
            }
            groups.flatMap { members ->
                members.chunked(maxMembersPerPlan).map { chunk ->
                    GlassGroupPlan(
                        domain = key.first,
                        materialKey = key.second,
                        viewport = viewport,
                        members = chunk
                    )
                }
            }
        }

    /**
     * Builds tight, spatially dense chunks across the whole viewport. Unlike [plan], this avoids
     * putting distant cards into one mostly-empty RenderTarget merely because they do not overlap.
     * Single-member fallbacks are retained so callers can make one all-or-nothing renderer choice.
     */
    fun planSpatialChunks(
        viewport: Rect,
        candidates: List<GlassGroupCandidate>,
        maxMembersPerPlan: Int = GlassGroupMaximumMembers,
        minimumFillRatio: Float = 0.34f,
        maximumLayerAreaFraction: Float = 0.58f
    ): List<GlassGroupPlan> {
        require(maxMembersPerPlan > 0) { "GlassGroup plan size must be positive." }
        require(minimumFillRatio in 0f..1f) { "Spatial fill ratio must be between 0 and 1." }
        require(maximumLayerAreaFraction > 0f) {
            "Spatial layer-area fraction must be positive."
        }
        val viewportArea = viewport.area()
        if (viewportArea <= 0f) return emptyList()

        return candidates
            .asSequence()
            .filter { it.boundsInViewport.overlaps(viewport) }
            .groupBy { it.domain to it.materialKey }
            .flatMap { (key, sameMaterial) ->
                val groups = mutableListOf<SpatialGroup>()
                sameMaterial.sortedWith(
                    compareBy<GlassGroupCandidate> { it.boundsInViewport.top }
                        .thenBy { it.boundsInViewport.left }
                        .thenBy { it.id }
                ).forEach { candidate ->
                    val candidateArea = candidate.boundsInViewport.area()
                    val target = groups.mapIndexedNotNull { index, group ->
                        if (
                            group.members.size >= maxMembersPerPlan ||
                            group.members.any { existing ->
                                existing.boundsInViewport.overlaps(candidate.boundsInViewport)
                            }
                        ) {
                            null
                        } else {
                            val joinedBounds = group.bounds.union(candidate.boundsInViewport)
                            val joinedArea = joinedBounds.area()
                            val coveredArea = group.coveredArea + candidateArea
                            val fillRatio = if (joinedArea > 0f) coveredArea / joinedArea else 0f
                            val layerAreaFraction = joinedArea / viewportArea
                            if (
                                fillRatio + 0.0001f < minimumFillRatio ||
                                layerAreaFraction - 0.0001f > maximumLayerAreaFraction
                            ) {
                                null
                            } else {
                                SpatialJoin(
                                    groupIndex = index,
                                    bounds = joinedBounds,
                                    coveredArea = coveredArea,
                                    fillRatio = fillRatio,
                                    incrementalArea = joinedArea - group.bounds.area()
                                )
                            }
                        }
                    }.minWithOrNull(
                        compareBy<SpatialJoin> { -it.fillRatio }
                            .thenBy { it.incrementalArea }
                            .thenBy { it.groupIndex }
                    )

                    if (target == null) {
                        groups += SpatialGroup(
                            members = mutableListOf(candidate),
                            bounds = candidate.boundsInViewport,
                            coveredArea = candidateArea
                        )
                    } else {
                        groups[target.groupIndex].apply {
                            members += candidate
                            bounds = target.bounds
                            coveredArea = target.coveredArea
                        }
                    }
                }
                groups.map { group ->
                    GlassGroupPlan(
                        domain = key.first,
                        materialKey = key.second,
                        viewport = viewport,
                        members = group.members.toList()
                    )
                }
            }
    }
}

private fun Rect.area(): Float = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)

private fun Rect.union(other: Rect): Rect = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom)
)

// Backdrop 2.0 validates the host shape before evaluating a lens chain and deliberately rejects
// Outline.Generic. Keep the host on its supported CornerBasedShape path; the real disjoint card
// geometry is still enforced by both the shared SDF and the union clip below.
private val GlassGroupHostShape = RoundedRectangle(0.dp)

private fun glassGroupClipPath(members: List<GlassGroupCandidate>): Path = Path().apply {
    members.forEach { member ->
        val rect = member.boundsInViewport
        val radius = member.cornerRadiusPx.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
        val memberPath = Path().apply {
            addOutline(
                RoundedRectangle(radius.dp).createOutline(
                    androidx.compose.ui.geometry.Size(rect.width, rect.height),
                    androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.unit.Density(1f)
                )
            )
        }
        addPath(memberPath, androidx.compose.ui.geometry.Offset(rect.left, rect.top))
    }
}

fun GlassSceneState.isGlassGroupEnabled(sceneKey: String): Boolean =
    backendPolicy.rendererFor(sceneKey, GlassRendererKind.GroupedExperimental) ==
        GlassRendererKind.GroupedExperimental

enum class GlassGroupRenderEligibility {
    Eligible,
    DisabledByPolicy,
    NoVisibleMembers,
    TooManyMembers
}

/**
 * Group shaders are deliberately capped. Eight cards still collapse eight complete Kyant effect
 * chains into one while keeping the per-pixel SDF loop bounded on mid-range GPUs.
 */
fun GlassSceneState.glassGroupEligibility(
    sceneKey: String,
    plan: GlassGroupPlan,
    effectFrame: GlassEffectFrame
): GlassGroupRenderEligibility {
    if (!isGlassGroupEnabled(sceneKey)) return GlassGroupRenderEligibility.DisabledByPolicy
    if (plan.members.isEmpty()) return GlassGroupRenderEligibility.NoVisibleMembers
    if (plan.members.size > GlassGroupMaximumMembers) {
        return GlassGroupRenderEligibility.TooManyMembers
    }
    return GlassGroupRenderEligibility.Eligible
}

private const val GlassGroupRoundedRectSdf = """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

private data class GlassGroupLensShader(
    val key: String,
    val source: String
)

/** Produces one bounded, unrolled SDF shader; no per-frame source generation occurs. */
private fun glassGroupLensShader(
    memberCount: Int,
    chromaticAberration: Boolean
): GlassGroupLensShader {
    require(memberCount in 1..GlassGroupMaximumMembers)
    val uniforms = buildString {
        repeat(memberCount) { index ->
            append("uniform float4 rect$index;\nuniform float radius$index;\n")
        }
    }
    val selection = buildString {
        repeat(memberCount) { index ->
            append(
                """
    float2 halfSize$index = rect$index.zw * 0.5;
    float2 centered$index = (coord + offset) - (rect$index.xy + halfSize$index);
    float sd$index = sdRoundedRect(centered$index, halfSize$index, radius$index);
    if (sd$index < selectedSd) {
        selectedSd = sd$index;
        selectedCoord = centered$index;
        selectedHalfSize = halfSize$index;
        selectedRadius = radius$index;
    }
"""
            )
        }
    }
    val output = if (!chromaticAberration) {
        """
    return content.eval(coord + d * grad);
"""
    } else {
        """
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration *
        ((selectedCoord.x * selectedCoord.y) /
            (selectedHalfSize.x * selectedHalfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    return color;
"""
    }
    val dispersionUniform = if (chromaticAberration) {
        "uniform float chromaticAberration;"
    } else {
        ""
    }
    return GlassGroupLensShader(
        key = "SleepDownGlassGroupLensV1:$memberCount:${if (chromaticAberration) 1 else 0}",
        source = """
uniform shader content;
uniform float2 offset;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
$dispersionUniform
$uniforms
$GlassGroupRoundedRectSdf

half4 main(float2 coord) {
    float selectedSd = 1000000.0;
    float2 selectedCoord = float2(0.0);
    float2 selectedHalfSize = float2(1.0);
    float selectedRadius = 0.0;
$selection
    if (-selectedSd >= refractionHeight) {
        return content.eval(coord);
    }
    selectedSd = min(selectedSd, 0.0);
    float d = circleMap(1.0 - -selectedSd / refractionHeight) * refractionAmount;
    float gradRadius = min(
        selectedRadius * 1.5,
        min(selectedHalfSize.x, selectedHalfSize.y)
    );
    float2 grad = normalize(
        gradSdRoundedRect(selectedCoord, selectedHalfSize, gradRadius) +
            depthEffect * normalize(selectedCoord)
    );
$output
}
"""
    )
}

private fun BackdropEffectScope.glassGroupLens(
    members: List<GlassGroupCandidate>,
    shader: GlassGroupLensShader,
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean,
    chromaticAberration: Boolean
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return
    require(members.size in 1..GlassGroupMaximumMembers)
    if (padding > 0f) {
        padding = (padding - refractionHeight).coerceAtLeast(0f)
    }
    runtimeShaderEffect(
        key = shader.key,
        shaderString = shader.source,
        uniformShaderName = "content"
    ) {
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (chromaticAberration) setFloatUniform("chromaticAberration", 1f)
        members.forEachIndexed { index, member ->
            val rect = member.boundsInViewport
            val radius = member.cornerRadiusPx.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
            setFloatUniform("rect$index", rect.left, rect.top, rect.width, rect.height)
            setFloatUniform("radius$index", radius)
        }
    }
}

/**
 * Experimental single-effect-chain renderer. Card content, input and semantics remain separate
 * siblings; this modifier draws only the shared material beneath them. Its unrolled shader keeps
 * one rounded-rect SDF per member, so lens and chromatic aberration retain per-card geometry.
 */
@Composable
fun Modifier.sleepDownGlassGroupSurface(
    backdrop: Backdrop,
    plan: GlassGroupPlan,
    material: GlassMaterialSpec,
    effectFrame: GlassEffectFrame,
    sceneState: GlassSceneState,
    sceneKey: String,
    sampleScale: Float = 1f,
    renderEnabled: () -> Boolean = { true }
): Modifier {
    val eligibility = sceneState.glassGroupEligibility(sceneKey, plan, effectFrame)
    check(eligibility == GlassGroupRenderEligibility.Eligible) {
        "GlassGroup '$sceneKey' is not render-equivalent ($eligibility); " +
            "keep the per-card KyantReference backend."
    }
    val currentMembers = rememberUpdatedState(plan.members)
    val memberClipPath = remember(plan.members) { glassGroupClipPath(plan.members) }
    val lensShader = remember(plan.members.size, effectFrame.chromaticAberration) {
        glassGroupLensShader(plan.members.size, effectFrame.chromaticAberration)
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "GlassGroup:$sceneKey",
        domain = plan.domain,
        materialRole = material.role,
        requestedRenderer = GlassRendererKind.GroupedExperimental,
        sceneKey = sceneKey
    )
    val activeSampleScale = sampleScale.coerceIn(0.5f, 1f)
    val backdropOnlyFrame = effectFrame.sampledBackdropOnly(activeSampleScale)
    return sleepDownGlassSurface(
        backdrop = backdrop,
        descriptor = descriptor,
        material = material,
        shape = { GlassGroupHostShape },
        effectFrame = backdropOnlyFrame,
        sceneState = sceneState,
        renderEnabled = renderEnabled,
        effectInputKey = listOf(plan.members, effectFrame, activeSampleScale),
        effectsOverride = {
            if (backdropOnlyFrame.useVibrancy) vibrancy()
            backdropOnlyFrame.blur?.let { blur(it.toPx()) }
            val lensHeight = backdropOnlyFrame.lensHeight
            val lensAmount = backdropOnlyFrame.lensAmount
            if (lensHeight != null && lensAmount != null) {
                glassGroupLens(
                    members = currentMembers.value,
                    shader = lensShader,
                    refractionHeight = lensHeight.toPx(),
                    refractionAmount = lensAmount.toPx(),
                    depthEffect = effectFrame.depthEffect,
                    chromaticAberration = effectFrame.chromaticAberration
                )
            }
        },
        onDrawBackdrop = { drawBackdrop ->
            // A rectangular host is required by Backdrop's lens validator. Clip the resulting
            // blur/refraction back to the exact union so gaps between cards remain untouched.
            clipPath(memberClipPath) { drawBackdrop() }
        },
        additionalLayerBlock = if (activeSampleScale < 0.999f) {
            ({
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = 1f / activeSampleScale
                scaleY = 1f / activeSampleScale
            })
        } else {
            null
        }
    )
}

/** Visibility selects complete plans; member order, host size and shader arity stay unchanged. */
internal fun visibleGlassGroupPlans(
    plans: List<GlassGroupPlan>,
    mountedIds: Set<String>?
): List<GlassGroupPlan> = if (mountedIds == null) plans else {
    plans.filter { plan -> plan.members.any { it.id in mountedIds } }
}
