@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.ui.interaction

internal class MorphCurveContinuity {
    private var sampler: ((Float, Boolean) -> FloatArray)? = null
    private var rebase: ((FloatArray, Boolean) -> (Float) -> FloatArray)? = null
    private var closing = false
    private var flight: Flight? = null

    val isActive: Boolean get() = flight != null
    val durationMillis: Int? get() = flight?.durationMillis

    fun bind(
        sampler: (Float, Boolean) -> FloatArray,
        rebase: (FloatArray, Boolean) -> (Float) -> FloatArray
    ) {
        this.sampler = sampler
        this.rebase = rebase
    }

    fun unbind(sampler: (Float, Boolean) -> FloatArray) {
        if (this.sampler === sampler) {
            this.sampler = null
            this.rebase = null
        }
    }

    fun reset() {
        flight = null
        closing = false
    }

    fun redirect(progress: Float, closing: Boolean, fullDurationMillis: Int) {
        this.closing = closing
        // Stub: no-op
    }

    fun value(channel: Int, progress: Float, original: Float): Float = original

    private class Flight(
        private val startProgress: Float,
        private val closing: Boolean,
        private val current: FloatArray,
        private val route: (Float) -> FloatArray,
        val durationMillis: Int
    ) {
        fun values(p: Float): FloatArray = route(p)
    }
}
