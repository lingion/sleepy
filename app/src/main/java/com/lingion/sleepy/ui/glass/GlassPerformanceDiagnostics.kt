package com.lingion.sleepy.ui.glass

import android.os.Build
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState
import com.lingion.sleepy.ui.glass.performance.PerformanceLogTag

@Composable
fun GlassPerformanceDiagnostics(
    sceneState: GlassSceneState,
    animation: String
) {
    val view = LocalView.current
    val currentAnimation by rememberUpdatedState(animation)
    LaunchedEffect(view, sceneState) {
        if (!sceneState.diagnosticsEnabled) return@LaunchedEffect
        var intervalAnimation = currentAnimation
        while (true) {
            // Resume at the next frame boundary so the snapshot represents one completed frame,
            // rather than the whole time an animation label happened to stay unchanged.
            withFrameNanos { }
            val snapshot = sceneState.snapshotAndResetDiagnostics()
            val nextAnimation = currentAnimation
            if (nextAnimation != intervalAnimation && snapshot.hasRecordedGlassWork()) {
                Log.i(PerformanceLogTag, snapshot.toPerformanceLogMessage(intervalAnimation))
            }
            runCatching {
                PerformanceMetricsState.getHolderForHierarchy(view).state?.apply {
                    putState("glass_scene", snapshot.sceneId)
                    putState("glass_phase", snapshot.renderPhase.name)
                    putState("glass_provider_records", snapshot.providerRecordCount.toString())
                    putState("glass_provider_instances", snapshot.distinctProviderCount.toString())
                    putState("glass_consumer_draws", snapshot.consumerDrawCount.toString())
                    putState("glass_consumer_layers", snapshot.distinctConsumerCount.toString())
                    putState("glass_offscreen_pixels", snapshot.offscreenPixelArea.toString())
                    putState("glass_effect_evaluations", snapshot.effectChainEvaluationCount.toString())
                    putState("glass_effect_chain_rebuilds", snapshot.effectChainRebuildCount.toString())
                    putState("glass_layer_size_changes", snapshot.graphicsLayerSizeChangeCount.toString())
                    putState("glass_prewarm_hits", snapshot.prewarmHitCount.toString())
                    putState("glass_stable_resource_leaks", snapshot.stableResourceLeakCount.toString())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Trace.setCounter("SleepDown.Glass.ProviderRecords", snapshot.providerRecordCount)
                    Trace.setCounter("SleepDown.Glass.ProviderInstances", snapshot.distinctProviderCount.toLong())
                    Trace.setCounter("SleepDown.Glass.ConsumerDraws", snapshot.consumerDrawCount)
                    Trace.setCounter("SleepDown.Glass.ConsumerLayers", snapshot.distinctConsumerCount.toLong())
                    Trace.setCounter("SleepDown.Glass.OffscreenPixels", snapshot.offscreenPixelArea)
                    Trace.setCounter("SleepDown.Glass.EffectEvaluations", snapshot.effectChainEvaluationCount)
                    Trace.setCounter("SleepDown.Glass.EffectChainRebuilds", snapshot.effectChainRebuildCount)
                    Trace.setCounter("SleepDown.Glass.LayerSizeChanges", snapshot.graphicsLayerSizeChangeCount)
                    Trace.setCounter("SleepDown.Glass.PrewarmHits", snapshot.prewarmHitCount)
                    Trace.setCounter("SleepDown.Glass.StableResourceLeaks", snapshot.stableResourceLeakCount.toLong())
                }
            }
            intervalAnimation = nextAnimation
        }
    }
}

private fun GlassSceneSnapshot.hasRecordedGlassWork(): Boolean =
    providerRecordCount > 0 ||
        consumerDrawCount > 0 ||
        effectChainEvaluationCount > 0 ||
        effectChainRebuildCount > 0 ||
        graphicsLayerSizeChangeCount > 0 ||
        prewarmHitCount > 0 ||
        stableResourceLeakCount > 0

private fun GlassSceneSnapshot.toPerformanceLogMessage(animation: String): String =
    "Glass scene=$sceneId animation=$animation phase=$renderPhase " +
        "providerRecords=$providerRecordCount providerInstances=$distinctProviderCount " +
        "consumerDraws=$consumerDrawCount consumerLayers=$distinctConsumerCount " +
        "offscreenPixels=$offscreenPixelArea effectEvaluations=$effectChainEvaluationCount " +
        "effectChainRebuilds=$effectChainRebuildCount " +
        "layerSizeChanges=$graphicsLayerSizeChangeCount prewarmHits=$prewarmHitCount " +
        "stableResourceLeaks=$stableResourceLeakCount"
