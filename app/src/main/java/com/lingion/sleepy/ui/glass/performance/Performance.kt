package com.lingion.sleepy.ui.glass.performance

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import com.xiaomanjun.sleepdownschedule.model.DefaultCourseCardPalette

import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.os.Trace
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StartupPhase {
    Loading,
    Prewarm,
    Reveal,
    Entrance,
    Settle,
    FullQuality
}

val LocalStartupPhase = staticCompositionLocalOf { StartupPhase.FullQuality }
val LocalGlassQuality = staticCompositionLocalOf { 1f }

fun animatedGlassQuality(phase: StartupPhase): Float = when (phase) {
    StartupPhase.Loading,
    StartupPhase.Prewarm -> 0.45f
    StartupPhase.Reveal -> 0.55f
    StartupPhase.Entrance -> 0.70f
    StartupPhase.Settle -> 0.85f
    StartupPhase.FullQuality -> 1f
}

val StartupPhase.isAtLeastEntrance: Boolean
    get() = this == StartupPhase.Entrance || this == StartupPhase.Settle || this == StartupPhase.FullQuality

@Composable
fun StartupJankStats(
    phase: StartupPhase,
    screen: String,
    animation: String,
    personalizeMode: String = "Idle",
    personalizeSlider: String = "Idle"
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var courseEditorOpenCount by remember { mutableIntStateOf(0) }
    DisposableEffect(view, activity) {
        var jankStats: JankStats? = null
        fun startTracking() {
            val performanceBuild =
                BuildConfig.BUILD_TYPE.contains("benchmark", ignoreCase = true)
            if (!performanceBuild || activity == null || jankStats != null) return
            val personalizeOpenAggregator = AnimationFrameSummaryAggregator(
                targetAnimation = "PersonalizeOpen",
                onSummary = { summary -> Log.i(PerformanceLogTag, summary.toLogMessage()) }
            )
            jankStats = runCatching {
                JankStats.createAndTrack(activity.window) { frameData ->
                    personalizeOpenAggregator.onFrame(
                        animation = frameData.states
                            .lastOrNull { it.key == "animation" }
                            ?.value,
                        frameDurationUiNanos = frameData.frameDurationUiNanos,
                        isJank = frameData.isJank
                    )
                }
            }.getOrNull()
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = startTracking()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        if (view.isAttachedToWindow) {
            startTracking()
        } else {
            view.addOnAttachStateChangeListener(attachListener)
        }
        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
            runCatching { jankStats?.isTrackingEnabled = false }
        }
    }

    LaunchedEffect(view, phase, screen, animation, personalizeMode, personalizeSlider) {
        runCatching {
            if (animation == "CourseEditorPrepare") {
                courseEditorOpenCount += 1
            }
            val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                    ?: ThermalStatusNone
            } else {
                ThermalStatusNone
            }
            val state = PerformanceMetricsState.getHolderForHierarchy(view).state
            state?.putState("startup_phase", phase.name)
            state?.putState("screen", screen)
            state?.putState("animation", animation)
            state?.putState("personalize_mode", personalizeMode)
            state?.putState("personalize_slider", personalizeSlider)
            state?.putState("thermal_status", thermalStatus.toString())
            if (animation.startsWith("CourseEditor")) {
                state?.putState(
                    "course_editor_open_kind",
                    if (courseEditorOpenCount <= 1) "First" else "Repeat"
                )
            }
        }
    }
}

private const val ThermalStatusNone = 0
internal const val PerformanceLogTag = "SleepDownPerf"

internal enum class RefreshRateBucket(val nominalHz: Int) {
    Hz60(60),
    Hz90(90),
    Hz120(120)
}

/**
 * Median-based cadence detector with confirmation hysteresis. [recordFrameTimestampNanos] returns
 * a value only when the stable 60/90/120 bucket actually changes, so Compose state and Trace never
 * receive per-frame refresh-rate jitter.
 */
internal class RefreshCadenceTracker(
    private val sampleSize: Int = 9,
    private val confirmationsRequired: Int = 3
) {
    private val frameIntervals = LongArray(sampleSize)
    private var intervalCount = 0
    private var writeIndex = 0
    private var previousFrameTimestampNanos = 0L
    private var currentBucket: RefreshRateBucket? = null
    private var pendingBucket: RefreshRateBucket? = null
    private var pendingConfirmations = 0

    init {
        require(sampleSize >= 3 && sampleSize % 2 == 1)
        require(confirmationsRequired >= 1)
    }

    fun recordFrameTimestampNanos(frameTimestampNanos: Long): RefreshRateBucket? {
        if (previousFrameTimestampNanos == 0L) {
            previousFrameTimestampNanos = frameTimestampNanos
            return null
        }
        val interval = frameTimestampNanos - previousFrameTimestampNanos
        previousFrameTimestampNanos = frameTimestampNanos
        if (interval !in 4_000_000L..25_000_000L) {
            intervalCount = 0
            writeIndex = 0
            pendingBucket = null
            pendingConfirmations = 0
            return null
        }
        frameIntervals[writeIndex] = interval
        writeIndex = (writeIndex + 1) % sampleSize
        intervalCount = (intervalCount + 1).coerceAtMost(sampleSize)
        if (intervalCount < sampleSize) return null

        val medianInterval = frameIntervals.copyOf().apply { sort() }[sampleSize / 2]
        val candidate = refreshRateBucketForMedianInterval(medianInterval)
        if (candidate == currentBucket) {
            pendingBucket = null
            pendingConfirmations = 0
            return null
        }
        if (candidate == pendingBucket) {
            pendingConfirmations += 1
        } else {
            pendingBucket = candidate
            pendingConfirmations = 1
        }
        if (pendingConfirmations < confirmationsRequired) return null
        currentBucket = candidate
        pendingBucket = null
        pendingConfirmations = 0
        return candidate
    }
}

internal fun refreshRateBucketForMedianInterval(intervalNanos: Long): RefreshRateBucket {
    val refreshRate = 1_000_000_000.0 / intervalNanos.coerceAtLeast(1L)
    return when {
        refreshRate < 75.0 -> RefreshRateBucket.Hz60
        refreshRate < 105.0 -> RefreshRateBucket.Hz90
        else -> RefreshRateBucket.Hz120
    }
}

@Composable
fun RefreshCadenceDiagnostics(enabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, enabled) {
        if (!enabled) return@LaunchedEffect
        val tracker = RefreshCadenceTracker()
        while (true) {
            val bucket = tracker.recordFrameTimestampNanos(withFrameNanos { it }) ?: continue
            runCatching {
                PerformanceMetricsState.getHolderForHierarchy(view).state
                    ?.putState("refresh_rate_bucket", bucket.nominalHz.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Trace.setCounter(
                        "SleepDown.RefreshRateBucket",
                        bucket.nominalHz.toLong()
                    )
                }
            }
        }
    }
}

internal data class AnimationFrameSummary(
    val animation: String,
    val frameCount: Int,
    val jankFrameCount: Int,
    val frameDurationUiP50Nanos: Long,
    val frameDurationUiP90Nanos: Long,
    val frameDurationUiP95Nanos: Long,
    val frameDurationUiP99Nanos: Long,
    val maxFrameDurationUiNanos: Long
) {
    fun toLogMessage(): String = String.format(
        Locale.US,
        "%s frames=%d jank=%d jankRate=%.2f%% uiP50=%.2fms uiP90=%.2fms " +
            "uiP95=%.2fms uiP99=%.2fms uiMax=%.2fms",
        animation,
        frameCount,
        jankFrameCount,
        if (frameCount == 0) 0.0 else jankFrameCount * 100.0 / frameCount,
        frameDurationUiP50Nanos / 1_000_000.0,
        frameDurationUiP90Nanos / 1_000_000.0,
        frameDurationUiP95Nanos / 1_000_000.0,
        frameDurationUiP99Nanos / 1_000_000.0,
        maxFrameDurationUiNanos / 1_000_000.0
    )
}

internal class AnimationFrameSummaryAggregator(
    private val targetAnimation: String,
    private val onSummary: (AnimationFrameSummary) -> Unit
) {
    private val frameDurationUiNanos = mutableListOf<Long>()
    private var jankFrameCount = 0
    private var collecting = false

    fun onFrame(animation: String?, frameDurationUiNanos: Long, isJank: Boolean) {
        if (animation == targetAnimation) {
            collecting = true
            this.frameDurationUiNanos += frameDurationUiNanos
            if (isJank) jankFrameCount += 1
            return
        }
        if (!collecting) return

        val sortedDurations = this.frameDurationUiNanos.sorted()
        if (sortedDurations.isNotEmpty()) {
            onSummary(
                AnimationFrameSummary(
                    animation = targetAnimation,
                    frameCount = sortedDurations.size,
                    jankFrameCount = jankFrameCount,
                    frameDurationUiP50Nanos = sortedDurations.nearestRankPercentile(50),
                    frameDurationUiP90Nanos = sortedDurations.nearestRankPercentile(90),
                    frameDurationUiP95Nanos = sortedDurations.nearestRankPercentile(95),
                    frameDurationUiP99Nanos = sortedDurations.nearestRankPercentile(99),
                    maxFrameDurationUiNanos = sortedDurations.last()
                )
            )
        }
        this.frameDurationUiNanos.clear()
        jankFrameCount = 0
        collecting = false
    }
}

private fun List<Long>.nearestRankPercentile(percentile: Int): Long {
    val index = ceil(percentile / 100.0 * size).toInt().coerceIn(1, size) - 1
    return this[index]
}

@Composable
fun PerformanceAnimationState(animation: String, active: Boolean) {
    val view = LocalView.current
    val traceName = performanceTraceName(animation)
    DisposableEffect(view, traceName, active) {
        if (active && traceName != null) Trace.beginSection(traceName)
        onDispose {
            if (active && traceName != null) Trace.endSection()
            if (!active) return@onDispose
            runCatching {
                PerformanceMetricsState.getHolderForHierarchy(view).state?.putState("animation", "Idle")
            }
        }
    }
    LaunchedEffect(view, animation, active) {
        runCatching {
            val state = PerformanceMetricsState.getHolderForHierarchy(view).state
            state?.putState("animation", if (active) animation else "Idle")
        }
    }
}

private fun performanceTraceName(animation: String): String? = when (animation) {
    "PersonalizePrepare" -> "SleepDown.Personalize.Prepare"
    "PersonalizeOpen" -> "SleepDown.Personalize.Open"
    "PersonalizeClose" -> "SleepDown.Personalize.Close"
    "PersonalizeSliderDrag" -> "SleepDown.Personalize.SliderDrag"
    "ImportHistoryCaptureSource" -> "SleepDown.ImportHistory.CaptureSource"
    "ImportHistoryCaptureBackground" -> "SleepDown.ImportHistory.CaptureBackground"
    "ImportHistoryLaunch" -> "SleepDown.ImportHistory.Launch"
    "ImportHistoryOpen", "ImportHistoryDetailOpen", "ImportHistoryDetailClose" -> "SleepDown.ImportHistory.Morph"
    "DayAgentPrepare" -> "SleepDown.DayAgent.Prepare"
    "DayAgentOpen" -> "SleepDown.DayAgent.Open"
    "DayAgentClose" -> "SleepDown.DayAgent.Close"
    "HomeMenuMorph" -> "SleepDown.AddDestination.AddCourse"
    "ManualImportMorph" -> "SleepDown.AddDestination.ManualImport"
    "EduImportMorph" -> "SleepDown.AddDestination.EduImport"
    "WeekSwipe", "WeekProgrammaticChange" -> "SleepDown.WeekSwipe"
    else -> null
}

@Composable
fun TopBarEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) { content() }
}

@Composable
fun ContentEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) { content() }
}

@Composable
fun DockEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) { content() }
}

data class HomeWallpaperImages(
    val source: Bitmap?,
    val reducedSource: Bitmap?,
    val blurredSource: Bitmap?,
    val blurBucket: Int,
    val representativeColors: List<Long> = DefaultCourseCardPalette,
    val readabilityBitmap: Bitmap? = null,
    val renderKey: String = ""
)

private data class WallpaperSourceImages(
    val source: Bitmap,
    val reducedSource: Bitmap?,
    val representativeColors: List<Long>,
    val readabilityBitmap: Bitmap?
)

private val wallpaperSourceCache = object : LinkedHashMap<String, WallpaperSourceImages>(6, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WallpaperSourceImages>?): Boolean = size > 3
}

private val wallpaperRenderCache = object : LinkedHashMap<String, HomeWallpaperImages>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HomeWallpaperImages>?): Boolean = size > 4
}

@Suppress("DEPRECATION")
internal fun shouldClearHomeWallpaperCaches(trimLevel: Int): Boolean =
    trimLevel >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW

fun clearHomeWallpaperCaches() {
    synchronized(wallpaperSourceCache) {
        wallpaperSourceCache.clear()
    }
    synchronized(wallpaperRenderCache) {
        wallpaperRenderCache.clear()
    }
}

private fun HomeWallpaperImages.prepareToDraw() = apply {
    source?.prepareToDraw()
    reducedSource?.prepareToDraw()
    blurredSource?.prepareToDraw()
    readabilityBitmap?.prepareToDraw()
}

fun homeWallpaperRenderKey(
    config: ScheduleConfigEntity,
    useDarkDefaultWallpaper: Boolean
): String {
    val sourceKey = "${config.wallpaperUri}|${config.defaultWallpaperStyle}|$useDarkDefaultWallpaper"
    return "$sourceKey|blur=${bucketWallpaperBlur(config.wallpaperBlur)}"
}

@Composable
fun rememberHomeWallpaperImages(config: ScheduleConfigEntity): State<HomeWallpaperImages> {
    val context = LocalContext.current.applicationContext
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val blurBucket = bucketWallpaperBlur(config.wallpaperBlur)
    val sourceKey = remember(config.wallpaperUri, config.defaultWallpaperStyle, useDarkDefaultWallpaper) {
        "${config.wallpaperUri}|${config.defaultWallpaperStyle}|$useDarkDefaultWallpaper"
    }
    val renderKey = remember(sourceKey, blurBucket) {
        homeWallpaperRenderKey(config, useDarkDefaultWallpaper)
    }
    val explicitNoWallpaper = config.wallpaperUri.isNullOrBlank() &&
        config.defaultWallpaperStyle == DefaultWallpaperStyle.NONE
    // Keep the last rendered image while only the blur bucket is changing.  Keying
    // this state by renderKey used to replace it with an empty bitmap for one frame,
    // which was visible as a flash whenever the blur slider crossed an integer.
    val images = remember {
        mutableStateOf(
            if (explicitNoWallpaper) {
                HomeWallpaperImages(null, null, null, blurBucket, renderKey = renderKey)
            } else {
                synchronized(wallpaperRenderCache) {
                    wallpaperRenderCache[renderKey] ?: HomeWallpaperImages(null, null, null, blurBucket)
                }
            }
        )
    }
    LaunchedEffect(renderKey) {
        if (explicitNoWallpaper) {
            val noWallpaper = HomeWallpaperImages(
                source = null,
                reducedSource = null,
                blurredSource = null,
                blurBucket = blurBucket,
                representativeColors = DefaultCourseCardPalette,
                readabilityBitmap = null,
                renderKey = renderKey
            )
            synchronized(wallpaperRenderCache) { wallpaperRenderCache[renderKey] = noWallpaper }
            images.value = noWallpaper
            return@LaunchedEffect
        }
        val cached = synchronized(wallpaperRenderCache) { wallpaperRenderCache[renderKey] }
        if (cached != null) {
            images.value = cached
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val cachedSource = synchronized(wallpaperSourceCache) { wallpaperSourceCache[sourceKey] }
                val sourceEntry = cachedSource ?: loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)?.let { source ->
                    WallpaperSourceImages(
                        source = source,
                        reducedSource = createReducedWallpaperBitmap(source),
                        representativeColors = extractRepresentativeWallpaperColors(source),
                        readabilityBitmap = createWallpaperReadabilityBitmap(source)
                    ).also { entry ->
                        synchronized(wallpaperSourceCache) { wallpaperSourceCache[sourceKey] = entry }
                    }
                }
                val blurredSource = createBlurredWallpaperBitmap(sourceEntry?.source, blurBucket)
                HomeWallpaperImages(
                    source = sourceEntry?.source,
                    reducedSource = sourceEntry?.reducedSource,
                    blurredSource = blurredSource,
                    blurBucket = blurBucket,
                    representativeColors = sourceEntry?.representativeColors ?: DefaultCourseCardPalette,
                    readabilityBitmap = if (blurredSource != null) createWallpaperReadabilityBitmap(blurredSource)
                        else sourceEntry?.readabilityBitmap,
                    renderKey = renderKey
                ).prepareToDraw()
            }.getOrElse {
                // Mark this key as completed even when a persisted URI is no longer
                // readable, so the first-draw gate can reveal the normal fallback.
                HomeWallpaperImages(
                    source = null,
                    reducedSource = null,
                    blurredSource = null,
                    blurBucket = blurBucket,
                    renderKey = renderKey
                )
            }
        }
        synchronized(wallpaperRenderCache) { wallpaperRenderCache[renderKey] = loaded }
        images.value = loaded
    }
    return images
}

fun bucketWallpaperBlur(blur: Float): Int {
    val clampedBlur = blur.coerceIn(0f, WallpaperBlurMaxDp)
    return clampedBlur.roundToInt()
}
