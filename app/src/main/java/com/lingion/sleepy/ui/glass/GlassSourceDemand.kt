package com.lingion.sleepy.ui.glass

import androidx.compose.runtime.*
import com.kyant.backdrop.Backdrop
import java.util.WeakHashMap

/** Explicit dependencies allow an exception (scene blur) to retain only its own producers. */
internal object GlassSourceDemand {
    private val inputs = WeakHashMap<Backdrop, List<Backdrop>>()
    private val consumers = mutableStateMapOf<Backdrop, Int>()

    fun link(output: Backdrop, vararg sources: Backdrop) { inputs[output] = sources.toList() }

    fun sources(backdrop: Backdrop): Set<Backdrop> = buildSet {
        fun visit(value: Backdrop) {
            if (add(value)) inputs[value].orEmpty().forEach(::visit)
        }
        visit(backdrop)
    }

    fun required(backdrop: Backdrop): Boolean = (consumers[backdrop] ?: 0) > 0
    fun acquire(sources: Set<Backdrop>) = sources.forEach { consumers[it] = (consumers[it] ?: 0) + 1 }
    fun release(sources: Set<Backdrop>) = sources.forEach {
        val remaining = (consumers[it] ?: 0) - 1
        if (remaining <= 0) consumers.remove(it) else consumers[it] = remaining
    }
}

@Composable
internal fun retainGlassSources(backdrop: Backdrop) {
    DisposableEffect(backdrop) {
        val sources = GlassSourceDemand.sources(backdrop)
        GlassSourceDemand.acquire(sources)
        onDispose { GlassSourceDemand.release(sources) }
    }
}

/** Opt-in counters for instrumented verification; no recording or logging in normal operation. */
internal object MaterialSamplingDiagnostics {
    var enabled = false
    var denseDraws = 0L
    var sceneDraws = 0L
    var producerDraws = 0L
    var dedicatedRefreshes = 0L
    fun reset() { denseDraws = 0; sceneDraws = 0; producerDraws = 0; dedicatedRefreshes = 0 }
}
