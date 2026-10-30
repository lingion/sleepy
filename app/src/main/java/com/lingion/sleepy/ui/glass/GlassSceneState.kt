package com.lingion.sleepy.ui.glass

import com.lingion.sleepy.ui.glass.ui.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntSize
import com.xiaomanjun.sleepdownschedule.BuildConfig

@Stable
class GlassSceneState internal constructor(
    val sceneId: String,
    val diagnosticsEnabled: Boolean,
    val backendPolicy: GlassBackendPolicy
) {
    var renderPhase by mutableStateOf(GlassRenderPhase.Preparing)
        private set

    var cacheGeneration by mutableIntStateOf(0)
        private set

    private val domainMetrics = linkedMapOf<GlassBackdropDomain, MutableGlassDomainMetrics>()
    private val transforms = linkedMapOf<GlassBackdropDomain, GlassCoordinateTransform>()
    private val temporaryResources = linkedSetOf<String>()

    fun synchronizePhase(next: GlassRenderPhase): Boolean {
        if (!renderPhase.canTransitionTo(next)) return false
        renderPhase = next
        if (next == GlassRenderPhase.Released) {
            transforms.clear()
        }
        return true
    }

    fun beginCacheGeneration(): Int {
        cacheGeneration += 1
        return cacheGeneration
    }

    fun updateCoordinateTransform(
        domain: GlassBackdropDomain,
        transform: GlassCoordinateTransform
    ) {
        transforms[domain] = transform
    }

    fun coordinateTransform(domain: GlassBackdropDomain): GlassCoordinateTransform =
        transforms[domain] ?: GlassCoordinateTransform()

    fun acquireTemporaryResource(resourceId: String) {
        temporaryResources += resourceId
    }

    fun releaseTemporaryResource(resourceId: String) {
        temporaryResources -= resourceId
    }

    fun recordProvider(domain: GlassBackdropDomain, providerId: String) {
        if (!diagnosticsEnabled) return
        val metrics = metrics(domain)
        metrics.providerRecordCount += 1
        metrics.providerIds += providerId
    }

    fun recordConsumerDraw(
        descriptor: GlassSurfaceDescriptor,
        size: IntSize
    ) {
        if (!diagnosticsEnabled) return
        val metrics = metrics(descriptor.domain)
        metrics.consumerDrawCount += 1
        metrics.consumerIds += descriptor.id
        val pixelArea = size.width.coerceAtLeast(0).toLong() * size.height.coerceAtLeast(0).toLong()
        metrics.offscreenPixelArea += pixelArea
        metrics.maxConsumerPixelArea = maxOf(metrics.maxConsumerPixelArea, pixelArea)
        val previousSize = metrics.consumerSizes.put(descriptor.id, size)
        if (previousSize != null && previousSize != size) {
            metrics.graphicsLayerSizeChangeCount += 1
        }
    }

    fun recordEffectChainEvaluation(descriptor: GlassSurfaceDescriptor) {
        if (!diagnosticsEnabled) return
        metrics(descriptor.domain).effectChainEvaluationCount += 1
    }

    fun recordEffectChainRebuild(descriptor: GlassSurfaceDescriptor) {
        if (!diagnosticsEnabled) return
        metrics(descriptor.domain).effectChainRebuildCount += 1
    }

    fun recordPrewarmHit(domain: GlassBackdropDomain) {
        if (!diagnosticsEnabled) return
        metrics(domain).prewarmHitCount += 1
    }

    fun rendererFor(descriptor: GlassSurfaceDescriptor): GlassRendererKind =
        backendPolicy.rendererFor(descriptor.sceneKey, descriptor.requestedRenderer)

    /** Fails fast only in diagnostic builds; release rendering never pays for graph validation. */
    fun requireValidTopology(
        nodes: List<GlassTopologyNode>,
        links: List<GlassSamplingLink>
    ) {
        if (!diagnosticsEnabled) return
        val violations = GlassSceneTopology.validate(nodes, links)
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "Invalid glass topology for '$sceneId': ",
                separator = "; "
            ) { violation -> "${violation.kind}: ${violation.message}" }
        }
    }

    fun snapshot(): GlassSceneSnapshot = GlassSceneSnapshot(
        sceneId = sceneId,
        renderPhase = renderPhase,
        cacheGeneration = cacheGeneration,
        domains = domainMetrics.mapValues { (_, value) -> value.snapshot() },
        activeTemporaryResources = temporaryResources.toSet(),
        stableResourceLeakCount = if (renderPhase.keepsTemporaryLayers) 0 else temporaryResources.size
    )

    fun snapshotAndResetDiagnostics(): GlassSceneSnapshot {
        val snapshot = snapshot()
        domainMetrics.values.forEach(MutableGlassDomainMetrics::resetInterval)
        return snapshot
    }

    private fun metrics(domain: GlassBackdropDomain): MutableGlassDomainMetrics =
        domainMetrics.getOrPut(domain) { MutableGlassDomainMetrics() }
}

private class MutableGlassDomainMetrics {
    var providerRecordCount: Long = 0
    var consumerDrawCount: Long = 0
    var offscreenPixelArea: Long = 0
    var maxConsumerPixelArea: Long = 0
    var effectChainEvaluationCount: Long = 0
    var effectChainRebuildCount: Long = 0
    var graphicsLayerSizeChangeCount: Long = 0
    var prewarmHitCount: Long = 0
    val providerIds = linkedSetOf<String>()
    val consumerIds = linkedSetOf<String>()
    val consumerSizes = linkedMapOf<String, IntSize>()

    fun snapshot() = GlassDomainSnapshot(
        providerRecordCount = providerRecordCount,
        distinctProviderCount = providerIds.size,
        consumerDrawCount = consumerDrawCount,
        distinctConsumerCount = consumerIds.size,
        offscreenPixelArea = offscreenPixelArea,
        maxConsumerPixelArea = maxConsumerPixelArea,
        effectChainEvaluationCount = effectChainEvaluationCount,
        effectChainRebuildCount = effectChainRebuildCount,
        graphicsLayerSizeChangeCount = graphicsLayerSizeChangeCount,
        prewarmHitCount = prewarmHitCount
    )

    fun resetInterval() {
        providerRecordCount = 0
        consumerDrawCount = 0
        offscreenPixelArea = 0
        maxConsumerPixelArea = 0
        effectChainEvaluationCount = 0
        effectChainRebuildCount = 0
        graphicsLayerSizeChangeCount = 0
        prewarmHitCount = 0
        providerIds.clear()
        consumerIds.clear()
        // Keep the last known sizes so the next interval can still detect an allocation change.
    }
}

data class GlassDomainSnapshot(
    val providerRecordCount: Long,
    val distinctProviderCount: Int,
    val consumerDrawCount: Long,
    val distinctConsumerCount: Int,
    val offscreenPixelArea: Long,
    val maxConsumerPixelArea: Long,
    val effectChainEvaluationCount: Long,
    val effectChainRebuildCount: Long,
    val graphicsLayerSizeChangeCount: Long,
    val prewarmHitCount: Long
)

data class GlassSceneSnapshot(
    val sceneId: String,
    val renderPhase: GlassRenderPhase,
    val cacheGeneration: Int,
    val domains: Map<GlassBackdropDomain, GlassDomainSnapshot>,
    val activeTemporaryResources: Set<String>,
    val stableResourceLeakCount: Int
) {
    val providerRecordCount: Long get() = domains.values.sumOf { it.providerRecordCount }
    val distinctProviderCount: Int get() = domains.values.sumOf { it.distinctProviderCount }
    val consumerDrawCount: Long get() = domains.values.sumOf { it.consumerDrawCount }
    val distinctConsumerCount: Int get() = domains.values.sumOf { it.distinctConsumerCount }
    val offscreenPixelArea: Long get() = domains.values.sumOf { it.offscreenPixelArea }
    val effectChainEvaluationCount: Long get() = domains.values.sumOf { it.effectChainEvaluationCount }
    val effectChainRebuildCount: Long get() = domains.values.sumOf { it.effectChainRebuildCount }
    val graphicsLayerSizeChangeCount: Long get() = domains.values.sumOf { it.graphicsLayerSizeChangeCount }
    val prewarmHitCount: Long get() = domains.values.sumOf { it.prewarmHitCount }
}

val LocalGlassSceneState = staticCompositionLocalOf<GlassSceneState?> { null }

@Composable
fun rememberGlassSceneState(
    sceneId: String,
    diagnosticsEnabled: Boolean = BuildConfig.DEBUG ||
        BuildConfig.BUILD_TYPE.contains("benchmark", ignoreCase = true),
    backendPolicy: GlassBackendPolicy = GlassBackendPolicy.ReferenceOnly
): GlassSceneState {
    androidx.compose.runtime.DisposableEffect(diagnosticsEnabled) {
        if (diagnosticsEnabled) GlassBackendTrace.acquire()
        onDispose { if (diagnosticsEnabled) GlassBackendTrace.release() }
    }
    return remember(sceneId, diagnosticsEnabled, backendPolicy) { GlassSceneState(
        sceneId = sceneId,
        diagnosticsEnabled = diagnosticsEnabled,
        backendPolicy = backendPolicy
    ) }
}

@Composable
fun GlassSceneHost(
    state: GlassSceneState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalGlassSceneState provides state, content = content)
}
