@file:Suppress("unused")

package androidx.metrics.performance

import android.view.Window

/**
 * Stub for androidx.metrics.performance.JankStats.
 *
 * The real class comes from androidx.metrics:metrics-performance.
 * This stub provides the same API surface so ported glass code compiles
 * without adding the dependency.
 */
class JankStats private constructor() {

    var isTrackingEnabled: Boolean = false

    /**
     * Frame data reported by the jank tracker.
     */
    data class FrameData(
        val frameDurationUiNanos: Long,
        val isJank: Boolean,
        val states: List<StateEntry>
    )

    data class StateEntry(
        val key: String,
        val value: String
    )

    companion object {
        @JvmStatic
        fun createAndTrack(
            window: Window,
            listener: (FrameData) -> Unit
        ): JankStats {
            return JankStats()
        }
    }
}
