package com.lingion.sleepy.ui.glass

import android.os.Build
import android.os.Trace
import com.kyant.backdrop.BackdropDiagnostics

/** One process-wide diagnostic sink, leased by live scenes; never retains scene or UI objects. */
internal object GlassBackendTrace {
    fun durationSince(stage: String, startedNanos: Long) {
        if (com.xiaomanjun.sleepdownschedule.BuildConfig.DEBUG ||
            com.xiaomanjun.sleepdownschedule.BuildConfig.BUILD_TYPE == "benchmark") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.setCounter("GlassMotion.$stage.Microseconds", (System.nanoTime() - startedNanos) / 1000L)
            }
        }
    }
    private var owners = 0
    private val counters = mutableMapOf<String, Long>()
    @Synchronized fun acquire() {
        if (owners++ == 0) {
            counters.clear()
            BackdropDiagnostics.observer = ::record
        }
    }
    @Synchronized fun release() {
        check(owners > 0)
        if (--owners == 0) {
            BackdropDiagnostics.observer = null
            counters.clear()
        }
    }
    @Synchronized private fun record(event: String, delta: Long) {
        val total = (counters[event] ?: 0L) + delta
        counters[event] = total
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.setCounter("Kyant.$event", total)
    }
}
