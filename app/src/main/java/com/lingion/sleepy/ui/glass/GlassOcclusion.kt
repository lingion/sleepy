package com.lingion.sleepy.ui.glass

import android.os.Build
import android.os.Trace
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Lifecycle of expensive course-card material nodes while an exact cached Home frame covers them.
 * Suspended removes only sampled glass/decoration nodes. The real card layout, flat translucent
 * fallback, text, input and semantics stay composed. Revealing crossfades the restored glass over
 * that flat fallback after Closing.
 */
enum class CourseGlassOcclusionPhase {
    Live,
    Preparing,
    Suspended,
    PostCloseRestore,
    Revealing;

    val mountsMaterialNodes: Boolean
        get() = this != Suspended

    /** A complete page plan is frozen before Opening and kept through the final cached handoff. */
    val usesFrozenGroupPlan: Boolean
        get() = this != Live
}

/**
 * Post-close work is paced against an equivalent 60 Hz cadence. At 120 Hz this naturally advances
 * at most every second display frame instead of doubling material initialization throughput.
 */
internal const val CourseGlassRestoreCadenceNanos = 16_666_667L
internal const val CourseGlassRestoreGroupsPerBatch = 2

internal fun courseGlassRestoreFrameDue(previous: Long, current: Long): Boolean =
    current - previous >= CourseGlassRestoreCadenceNanos * 9 / 10
internal const val CourseGlassMaterialRevealDurationMillis = 280

internal fun courseGlassFlatFallbackAlpha(baseAlpha: Float, materialProgress: Float): Float =
    baseAlpha.coerceIn(0f, 1f) * (1f - materialProgress.coerceIn(0f, 1f))

/** One stable group from the complete pre-occlusion page plan. */
data class CourseGlassRestoreGroup(
    val key: String,
    val pageWeek: Int,
    val normalizedCenterX: Float
)

/**
 * Week pages publish their complete group plans here while remaining fully composed. The
 * coordinator takes one immutable, centre-out snapshot before suspending course materials.
 */
class CourseGlassRestoreRegistry {
    private val pageGroups = linkedMapOf<Int, List<CourseGlassRestoreGroup>>()

    @Synchronized
    fun replacePage(
        pageWeek: Int,
        groups: List<CourseGlassRestoreGroup>,
        topologyFrozen: Boolean
    ) {
        val normalized = groups.distinctBy { it.key }
        val previous = pageGroups[pageWeek]
        if (previous?.map { it.key } == normalized.map { it.key }) return
        pageGroups[pageWeek] = normalized
        if (topologyFrozen && previous != null) {
            CourseGlassOcclusionTrace.recordGroupTopologyChange()
        }
    }

    @Synchronized
    fun removePage(pageWeek: Int) {
        pageGroups.remove(pageWeek)
    }

    @Synchronized
    fun pageKeys(pageWeek: Int): Set<String> = pageGroups[pageWeek].orEmpty().map { it.key }.toSet()

    @Synchronized
    fun orderedGroupKeys(targetWeek: Int): List<String> = pageGroups.values
        .flatten()
        .distinctBy { it.key }
        .sortedWith(
            compareBy<CourseGlassRestoreGroup> {
                if (it.pageWeek == targetWeek) 0 else 1
            }.thenBy { abs(it.pageWeek - targetWeek) }
                .thenBy { if (it.pageWeek < targetWeek) 0 else 1 }
                .thenBy { abs(it.normalizedCenterX - 0.5f) }
                .thenBy { it.normalizedCenterX }
                .thenBy { it.key }
        )
        .map { it.key }
}

data class CourseGlassRestorePlan(
    val phase: CourseGlassOcclusionPhase = CourseGlassOcclusionPhase.Live,
    val restoredGroupKeys: Set<String> = emptySet()
) {
    fun mountsGroup(groupKey: String?): Boolean = when (phase) {
        CourseGlassOcclusionPhase.Live,
        CourseGlassOcclusionPhase.Preparing -> true
        CourseGlassOcclusionPhase.Suspended -> false
        CourseGlassOcclusionPhase.PostCloseRestore,
        CourseGlassOcclusionPhase.Revealing ->
            groupKey != null && groupKey in restoredGroupKeys
    }
}

val LocalCourseGlassOcclusionPhase = staticCompositionLocalOf {
    CourseGlassOcclusionPhase.Live
}

val LocalCourseGlassRestorePlan = staticCompositionLocalOf {
    CourseGlassRestorePlan()
}

val LocalCourseGlassRestoreRegistry = staticCompositionLocalOf<CourseGlassRestoreRegistry?> {
    null
}

/** Read from draw/layer blocks so the reveal updates pixels, not card composition. */
val LocalCourseGlassMaterialRevealProgress = staticCompositionLocalOf<() -> Float> {
    { 1f }
}

internal fun shouldSuspendCourseGlassMaterials(
    experimentEnabled: Boolean,
    weekMode: Boolean,
    exactCacheCoverActive: Boolean,
    substantialOverlayActive: Boolean
): Boolean = !GlassMotionExperiments.continuousMaterialDrawing &&
    experimentEnabled && weekMode && exactCacheCoverActive && substantialOverlayActive

/** Stable key: restore progress never changes planner membership or positional Compose keys. */
internal fun courseGlassRestoreGroupKey(
    pageWeek: Int,
    memberIds: List<String>
): String = buildString {
    append("week:").append(pageWeek).append('|')
    memberIds.sorted().forEach { id -> append(id.length).append(':').append(id).append('|') }
}

/** Trace counters used by Perfetto structural acceptance, also present in signed Release builds. */
internal object CourseGlassOcclusionTrace {
    private val liveDrawsDuringMorph = AtomicLong(0L)
    private val fullTreeRecordsDuringMorph = AtomicLong(0L)
    private val topologyChanges = AtomicLong(0L)
    private val postCloseRestoreFrames = AtomicLong(0L)

    fun beginMorph(generation: Int) {
        liveDrawsDuringMorph.set(0L)
        fullTreeRecordsDuringMorph.set(0L)
        topologyChanges.set(0L)
        postCloseRestoreFrames.set(0L)
        setCounter("CourseGlass.LiveDrawsDuringMorph", 0L)
        setCounter("CourseGlass.FullTreeRecordsDuringMorph", 0L)
        setCounter("CourseGlass.GroupTopologyChanges", 0L)
        setCounter("CourseGlass.PostCloseRestoreFrames", 0L)
        setCounter("CourseGlass.OcclusionGeneration", generation.toLong())
    }

    fun recordLiveDrawDuringMorph() {
        setCounter(
            "CourseGlass.LiveDrawsDuringMorph",
            liveDrawsDuringMorph.incrementAndGet()
        )
    }

    fun recordFullTreeRecordDuringMorph() {
        setCounter(
            "CourseGlass.FullTreeRecordsDuringMorph",
            fullTreeRecordsDuringMorph.incrementAndGet()
        )
    }

    fun recordGroupTopologyChange() {
        setCounter(
            "CourseGlass.GroupTopologyChanges",
            topologyChanges.incrementAndGet()
        )
    }

    fun recordPostCloseRestoreFrame() {
        setCounter(
            "CourseGlass.PostCloseRestoreFrames",
            postCloseRestoreFrames.incrementAndGet()
        )
    }

    private fun setCounter(name: String, value: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.setCounter(name, value)
        }
    }
}
