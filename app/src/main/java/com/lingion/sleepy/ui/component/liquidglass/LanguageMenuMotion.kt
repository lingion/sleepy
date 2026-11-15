package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import kotlin.math.exp
import kotlin.math.abs

/** Actual drawing parameters, observable by explicitly enabled device captures. */
internal data class LanguageMenuVisualState(
    val scaleX: Float,
    val scaleY: Float,
    val alpha: Float,
    val contentAlpha: Float,
    val translationXDp: Float,
    val translationYDp: Float,
)

internal val LanguageMenuVisualStateKey = SemanticsPropertyKey<LanguageMenuVisualState>("LanguageMenuVisualState")
internal var SemanticsPropertyReceiver.languageMenuVisualState by LanguageMenuVisualStateKey

internal data class LanguageMenuHandoffState(val sessionId: Int, val phase: String, val sourceCardAlpha: Float)
internal val LanguageMenuHandoffStateKey = SemanticsPropertyKey<LanguageMenuHandoffState>("LanguageMenuHandoffState")
internal var SemanticsPropertyReceiver.languageMenuHandoffState by LanguageMenuHandoffStateKey

/** Window submission is a handoff fence, not proof of compositor presentation. */
internal class LanguageMenuHandoff {
    private enum class Phase { Popup, Source, Motion, RestoringSource, Restored, Disposed }
    private var phase by mutableStateOf(Phase.Popup)
    val sessionId = System.identityHashCode(this)
    val sourceCardAlpha: Float get() = if (phase == Phase.Popup || phase == Phase.RestoringSource || phase == Phase.Restored || phase == Phase.Disposed) 1f else 0f
    val awaitingSourceFrame: Boolean get() = phase == Phase.Source
    val awaitingRestoredSourceFrame: Boolean get() = phase == Phase.RestoringSource
    val canAnimate: Boolean get() = phase == Phase.Motion || phase == Phase.RestoringSource || phase == Phase.Restored
    val returningSource: Boolean get() = phase == Phase.RestoringSource || phase == Phase.Restored
    val isDisposed: Boolean get() = phase == Phase.Disposed
    val state: LanguageMenuHandoffState get() = LanguageMenuHandoffState(sessionId, when (phase) {
        Phase.Popup -> "waiting-popup-frame"
        Phase.Source -> "waiting-source-hidden-frame"
        Phase.Motion -> "motion"
        Phase.RestoringSource -> "waiting-source-restored-frame"
        Phase.Restored -> "source-restored"
        Phase.Disposed -> "disposed"
    }, sourceCardAlpha)

    fun popupFrameSubmitted(): Boolean {
        if (phase != Phase.Popup) return false
        phase = Phase.Source
        return true
    }

    fun sourceHiddenFrameSubmitted(): Boolean {
        if (phase != Phase.Source) return false
        phase = Phase.Motion
        return true
    }

    /** Keep the matching popup card until the original window has drawn its replacement. */
    fun restoreSource(): Boolean {
        if (phase != Phase.Motion) return false
        phase = Phase.RestoringSource
        return true
    }

    fun sourceRestoredFrameSubmitted(): Boolean {
        if (phase != Phase.RestoringSource) return false
        phase = Phase.Restored
        return true
    }

    fun dispose() { phase = Phase.Disposed }
}

/** Opt-in capture observer; no logging or frame traversal when unset. */
internal object LanguageMenuPresentationDiagnostics {
    var observer: ((sessionId: Int, event: String, frameTimeNanos: Long?, windowIdentity: Int?, windowClass: String?) -> Unit)? = null
    fun event(handoff: LanguageMenuHandoff, event: String, frameTimeNanos: Long? = null,
        windowIdentity: Int? = null, windowClass: String? = null) {
        observer?.invoke(handoff.sessionId, event, frameTimeNanos, windowIdentity, windowClass)
    }
}

/**
 * Fit to the iOS 26.5 Menu + inline Picker presentation samples, not Apple internals.
 * Geometry uses one continuous spring; material and content have distinct silhouettes.
 * The observed vertical path is retained separately from the size spring. Different
 * native invocations have vertical residuals; this baseline is not a pixel-parity claim.
 */
internal class LanguageMenuMotion {
    val progress = SpringState(0f, rest = .0001)
    var closing = false
        private set
    private var elapsed by mutableDoubleStateOf(0.0)
    private var menuCorrection = 0.0
    private var menuVelocityCorrection = 0.0
    private var anchorCorrection = 0.0
    private var anchorVelocityCorrection = 0.0

    init { progress.springTo(1f, OpenSpring) }

    private val pathEnd: Double
        get() = if (closing) maxOf(CloseMenuY[CloseMenuY.size - 2], CloseAnchorY[CloseAnchorY.size - 2])
        else OpenMenuY[OpenMenuY.size - 2]
    val isRunning: Boolean get() = progress.isRunning || elapsed < pathEnd
    // Full mathematical rest remains available to fixtures. Production uses the
    // painted-source tolerance below instead of retaining an invisible tail.
    val canRemove: Boolean get() = closing && !isRunning
    val alpha: Float get() = progress.value.coerceIn(0f, 1f)
    val anchorContentAlpha: Float get() = ((1f - progress.value) / .65f).coerceIn(0f, 1f)
    // Once the opaque source card has returned, its rim must not retain a second
    // glass rectangle until the hidden spring's tiny residual reaches rest.
    val sourceMaterialAlpha: Float get() = if (closing) 1f - anchorContentAlpha else 1f
    private fun correction(value: Double, velocity: Double): Double =
        (value * (1.0 + 20.0 * elapsed) + velocity * elapsed) * exp(-20.0 * elapsed)
    private fun residual(menu: Boolean): Double {
        val curve = if (!closing) OpenMenuY else if (menu) CloseMenuY else CloseAnchorY
        if (!closing && !menu) return 0.0
        return samplePath(curve, elapsed) + if (!closing) 0.0 else correction(
            if (menu) menuCorrection else anchorCorrection,
            if (menu) menuVelocityCorrection else anchorVelocityCorrection,
        )
    }
    private fun residualVelocity(menu: Boolean): Double {
        val curve = if (!closing) OpenMenuY else if (menu) CloseMenuY else CloseAnchorY
        return if (!closing && !menu) 0.0 else samplePathVelocity(curve, elapsed)
    }

    fun close() {
        if (closing) return
        val currentMenu = residual(true)
        val currentAnchor = residual(false)
        val menuVelocity = residualVelocity(true)
        val anchorVelocity = residualVelocity(false)
        closing = true
        elapsed = 0.0
        menuCorrection = currentMenu - samplePath(CloseMenuY, 0.0)
        anchorCorrection = currentAnchor - samplePath(CloseAnchorY, 0.0)
        menuVelocityCorrection = menuVelocity - samplePathVelocity(CloseMenuY, 0.0)
        anchorVelocityCorrection = anchorVelocity - samplePathVelocity(CloseAnchorY, 0.0)
        progress.springTo(0f, CloseSpring)
    }

    fun tick(seconds: Double) {
        if (!seconds.isFinite() || seconds <= 0.0) return
        elapsed += seconds
        progress.tickDelta(seconds)
    }

    fun finish() {
        progress.jumpTo(if (closing) 0f else 1f)
        elapsed = pathEnd
        menuCorrection = 0.0
        menuVelocityCorrection = 0.0
        anchorCorrection = 0.0
        anchorVelocityCorrection = 0.0
    }

    fun geometry(anchor: Rect, menu: Rect): LanguageMenuGeometry {
        val p = progress.value
        val smallMenu = anchor.height / 2f
        val width = (smallMenu + (menu.width - smallMenu) * p).coerceAtLeast(1f)
        val materialHeight = (width * (1f + (menu.height / menu.width - 1f) * p)).coerceAtLeast(1f)
        val contentScale = width / menu.width
        val contentHeight = menu.height * contentScale
        val cx = anchor.center.x + (menu.center.x - anchor.center.x) * p
        val smallTop = anchor.center.y - smallMenu / 2f
        val y = smallTop + (menu.top - smallTop) * p + (residual(true) * anchor.height).toFloat()
        val anchorHeight = (anchor.height * (1f - .75f * p)).coerceAtLeast(1f)
        val anchorAspect = anchor.width / anchor.height + (1f - anchor.width / anchor.height) * p
        val anchorWidth = (anchorHeight * anchorAspect).coerceAtLeast(1f)
        val anchorY = anchor.top + anchor.height * (41.25f / (154f / 3f)) * p +
            (residual(false) * anchor.height).toFloat()
        val anchorRect = Rect(anchor.center.x - anchorWidth / 2f, anchorY,
            anchor.center.x + anchorWidth / 2f, anchorY + anchorHeight)
        return LanguageMenuGeometry(
            material = Rect(cx - width / 2f, y, cx + width / 2f, y + materialHeight),
            content = Rect(cx - width / 2f, y, cx + width / 2f, y + contentHeight),
            anchor = anchorRect,
            anchorContentScale = anchorWidth / anchor.width,
        )
    }

    /**
     * A window handoff should follow visible output, not an invisible spring tail.
     * The opaque replacement card is uniformly scaled around the optical anchor's
     * center; its painted height differs from that optical contour while moving.
     */
    fun sourceReadyForHandoff(anchor: Rect, menu: Rect, onePixelInCoordinates: Float): Boolean {
        if (!closing || !onePixelInCoordinates.isFinite() || onePixelInCoordinates <= 0f ||
            anchorContentAlpha < 254f / 255f || sourceMaterialAlpha > 1f / 255f
        ) return false
        val geometry = geometry(anchor, menu)
        val halfWidth = anchor.width * geometry.anchorContentScale / 2f
        val halfHeight = anchor.height * geometry.anchorContentScale / 2f
        val painted = Rect(geometry.anchor.center.x - halfWidth, geometry.anchor.center.y - halfHeight,
            geometry.anchor.center.x + halfWidth, geometry.anchor.center.y + halfHeight)
        return listOf(abs(painted.left - anchor.left), abs(painted.top - anchor.top),
            abs(painted.right - anchor.right), abs(painted.bottom - anchor.bottom)).all { it <= onePixelInCoordinates }
    }

    companion object {
        val OpenSpring = SpringDescription.withDampingRatio(1.0, 323.0, .75)
        val CloseSpring = SpringDescription.withDampingRatio(1.0, 164.0, .8)

        /**
         * C1 interpolation of nonuniform native samples. Zero endpoint slopes
         * join the held states; a first secant is not a measured initial velocity.
         */
        internal fun samplePath(points: DoubleArray, time: Double): Double = interpolatePath(points, time, false)

        internal fun samplePathVelocity(points: DoubleArray, time: Double): Double = interpolatePath(points, time, true)

        private fun interpolatePath(points: DoubleArray, time: Double, velocity: Boolean): Double {
            if (time <= points[0]) return if (velocity) 0.0 else points[1]
            val last = points.size - 2
            if (time >= points[last]) return if (velocity) 0.0 else points[last + 1]
            var i = 0
            while (i + 2 < last && points[i + 2] < time) i += 2
            val t0 = points[i]; val t1 = points[i + 2]
            val y0 = points[i + 1]; val y1 = points[i + 3]
            val before = (i - 2).coerceAtLeast(0)
            val after = (i + 4).coerceAtMost(last)
            val m0 = if (i == 0) 0.0 else (points[i + 3] - points[before + 1]) / (points[i + 2] - points[before])
            val m1 = if (i + 2 == last) 0.0 else (points[after + 1] - points[i + 1]) / (points[after] - points[i])
            val u = (time - t0) / (t1 - t0)
            val u2 = u * u; val u3 = u2 * u
            if (velocity) return (6*u2 - 6*u)*y0/(t1-t0) + (3*u2 - 4*u + 1)*m0 +
                (-6*u2 + 6*u)*y1/(t1-t0) + (3*u2 - 2*u)*m1
            return (2*u3 - 3*u2 + 1)*y0 + (u3 - 2*u2 + u)*(t1-t0)*m0 +
                (-2*u3 + 3*u2)*y1 + (u3-u2)*(t1-t0)*m1
        }

        // Cold first opening (run 1) and outside cancellation (run 3), recorded
        // at 402 x 874 pt on iOS 26.5. Time is captureStart minus the fitted
        // spring origin (k=323/164), not the capture completion timestamp.
        // Y residuals use the same source-center baseline in both directions.
        // The terminal zero at 850 ms is a smooth settling approximation, not
        // an extra native frame. Other invocations have different vertical paths.
        private val OpenMenuY = doubleArrayOf(
            0.000000000, 0.006493507,
            0.031298004, 0.122028066,
            0.047928204, 0.269918720,
            0.064658704, 0.422381112,
            0.081388004, 0.541254812,
            0.098184804, 0.605767464,
            0.114692204, 0.613391184,
            0.147950604, 0.502503299,
            0.164708004, 0.414810019,
            0.181298804, 0.323736906,
            0.197970004, 0.238429025,
            0.214634904, 0.164305762,
            0.231281004, 0.103735957,
            0.247953004, 0.055858869,
            0.264607304, 0.022662991,
            0.281296604, -0.000868851,
            0.297928604, -0.015779281,
            0.314583204, -0.024096767,
            0.331287604, -0.027621448,
            0.347943004, -0.027855073,
            0.364606704, -0.025990054,
            0.381237104, -0.022932138,
            0.397952304, -0.019339252,
            0.414640004, -0.015665349,
            0.431255104, -0.012203319,
            0.447905304, -0.009123719,
            0.464588404, -0.006507585,
            0.481257904, -0.004373534,
            0.497979404, -0.002699142,
            0.514585504, -0.002801277,
            0.531315104, -0.001945907,
            0.547900404, -0.001259947,
            0.564643604, -0.000731832,
            0.581269904, -0.000342290,
            0.597914804, -0.000069105,
            0.614580504, 0.000110244,
            0.631269704, 0.000216845,
            0.647911804, 0.000269375,
            0.664610004, 0.000283625,
            0.681249304, 0.000272416,
            0.698107304, 0.000245756,
            0.850000000, 0.000000000,
        )
        private val CloseMenuY = doubleArrayOf(
            0.000000000, 0.000000000,
            0.007361226, 0.067330354,
            0.024005726, 0.433287987,
            0.040689026, 0.754730780,
            0.057595526, 0.922927265,
            0.074038326, 0.962994718,
            0.090951726, 0.911774501,
            0.107759426, 0.802722373,
            0.124008226, 0.679082403,
            0.140706026, 0.537709563,
            0.157661426, 0.417289882,
            0.174036626, 0.315254237,
            0.191136126, 0.233032119,
            0.207333726, 0.169355140,
            0.224010326, 0.121615167,
            0.240711926, 0.086764851,
            0.257383226, 0.061857013,
            0.274051726, 0.044323537,
            0.290733426, 0.032080121,
            0.307329626, 0.023525825,
            0.323998726, 0.017486721,
            0.340673226, 0.013136832,
            0.357361126, 0.009916701,
            0.373996426, 0.007460394,
            0.390645626, 0.005535862,
            0.407317626, 0.003999088,
            0.424031226, 0.002761163,
            0.440676826, 0.001765778,
            0.457318326, 0.000974536,
            0.474038926, 0.000358493,
            0.490638926, -0.000106814,
            0.507307426, -0.000443658,
            0.523965626, -0.000672813,
            0.540747526, -0.000813812,
            0.557332726, -0.000884658,
            0.573992826, -0.000901497,
            0.590769926, -0.000878411,
            0.607291026, -0.000827445,
            0.623951126, -0.000758418,
            0.640716826, -0.000689854,
            0.657315426, -0.000596038,
            0.673937426, -0.000513293,
            0.690614726, -0.000434227,
            0.850000000, 0.000000000,
        )
        private val CloseAnchorY = doubleArrayOf(
            0.000000000, 0.000000000,
            0.007361226, -0.000833996,
            0.024005726, -0.008777476,
            0.040689026, -0.026187755,
            0.057595526, -0.054716926,
            0.074038326, -0.094907931,
            0.090951726, -0.145778908,
            0.107759426, -0.205113114,
            0.124008226, -0.259675995,
            0.140706026, -0.297237256,
            0.157661426, -0.317913143,
            0.174036626, -0.323307269,
            0.191136126, -0.315935664,
            0.207333726, -0.298714527,
            0.224010326, -0.274567373,
            0.240711926, -0.246162689,
            0.257383226, -0.215764772,
            0.274051726, -0.185175235,
            0.290733426, -0.155736004,
            0.307329626, -0.128371926,
            0.323998726, -0.103653546,
            0.340673226, -0.081866783,
            0.357361126, -0.063080859,
            0.373996426, -0.047208927,
            0.390645626, -0.034059734,
            0.407317626, -0.023378471,
            0.424031226, -0.014878769,
            0.440676826, -0.008266053,
            0.457318326, -0.003253186,
            0.474038926, 0.000428070,
            0.490638926, 0.003021399,
            0.507307426, 0.004742802,
            0.523965626, 0.005779346,
            0.540747526, 0.006290460,
            0.557332726, 0.006409576,
            0.573992826, 0.006246267,
            0.590769926, 0.005889007,
            0.607291026, 0.005408197,
            0.623951126, 0.004858181,
            0.640716826, 0.004280408,
            0.657315426, 0.003705133,
            0.673937426, 0.003153869,
            0.690614726, 0.002640883,
            0.850000000, 0.000000000,
        )
    }
}

internal data class LanguageMenuGeometry(
    val material: Rect,
    val content: Rect,
    val anchor: Rect,
    val anchorContentScale: Float,
)
