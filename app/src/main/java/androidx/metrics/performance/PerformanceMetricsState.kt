@file:Suppress("unused")

package androidx.metrics.performance

import android.view.View
import android.view.Window

/**
 * Stub for androidx.metrics.performance.PerformanceMetricsState.
 *
 * The real class comes from androidx.metrics:metrics-performance.
 * This stub provides the same API surface so ported glass code compiles
 * without adding the dependency.
 */
class PerformanceMetricsState private constructor() {

    val state: State? = State()

    class State {
        fun putState(key: String, value: String) {
            // No-op stub — metrics are not collected in this build.
        }
    }

    companion object {
        @JvmStatic
        fun getHolderForHierarchy(view: View): PerformanceMetricsState {
            return PerformanceMetricsState()
        }
    }
}
