package com.lingion.sleepy.widget.notification

/** Owns one notification session. All callbacks run on the service's main thread. */
internal class FluidNotificationSession(
    private val now: () -> Long,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val publish: (Int) -> Unit,
    private val finished: () -> Unit,
) {
    private var generation = 0L
    private var active = false
    private var sequence = 0
    private val pending = mutableSetOf<Runnable>()

    fun start(endEpoch: Long, reinforce: Boolean) {
        stop()
        if (now() >= endEpoch) {
            finished()
            return
        }
        active = true
        sequence = 0
        val session = generation
        fun post() {
            sequence = (sequence + 1).coerceAtLeast(1)
            publish(sequence)
        }
        fun tick() {
            if (now() >= endEpoch) {
                stop()
                finished()
            } else {
                post()
                enqueue(session, minOf(15_000L, endEpoch - now()).coerceAtLeast(1), ::tick)
            }
        }
        post()
        if (reinforce) enqueue(session, 600L) {
            if (now() < endEpoch) post()
        }
        enqueue(session, minOf(15_000L, endEpoch - now()).coerceAtLeast(1), ::tick)
    }

    fun stop() {
        active = false
        generation++
        pending.forEach(cancel)
        pending.clear()
    }

    private fun enqueue(session: Long, delay: Long, block: () -> Unit) {
        val callback = object : Runnable {
            override fun run() {
                pending.remove(this)
                if (active && generation == session) block()
            }
        }
        pending.add(callback)
        schedule(callback, delay)
    }
}

/** Only ColorOS has the observed first-post race; vivo requires >=10s updates. */
internal fun needsFluidFirstPostReinforcement(vendor: LiveCardVendor): Boolean =
    vendor == LiveCardVendor.OPPO || vendor == LiveCardVendor.ONEPLUS || vendor == LiveCardVendor.REALME


/** Display-only room/teacher/time edits do not replace the active course session. */
internal data class FluidNotificationIdentity(
    val notifyEpoch: Long,
    val classEpoch: Long,
    val startNode: Int,
    val courseName: String,
)

internal fun canReuseFluidSession(
    current: FluidNotificationIdentity?,
    next: FluidNotificationIdentity,
    casting: Boolean,
    explicitTest: Boolean,
    now: Long,
): Boolean = casting && !explicitTest && current == next && next.classEpoch > now
