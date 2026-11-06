package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.room.InvalidationTracker
import com.lingion.sleepy.SleepyApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import android.content.res.Configuration
import android.graphics.Bitmap
import com.lingion.sleepy.util.AppClock
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

internal enum class WideGridPreviewStatus { LOADING, READY, EMPTY, NO_TABLE, UNAVAILABLE, STALE, ERROR }
internal data class WideGridPreview(val mode: WeekGridWideMode, val data: WeekData, val bitmap: Bitmap)
internal data class WideGridPreviews(val status: WideGridPreviewStatus, val frames: List<WideGridPreview> = emptyList())

/** Reads the binding once for one coherent three-image batch. No stock/example courses. */
internal object WeekGridWidePreviewLoader {
    val displayOrder = listOf(WeekGridWideMode.ORIGINAL, WeekGridWideMode.VERTICAL, WeekGridWideMode.HORIZONTAL)

    /** Cold, screen-owned stream: leaving/stopping cancels IO and unregisters all listeners. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(context: Context, widgetId: Int): Flow<WideGridPreviews> = callbackFlow {
        val prefs = listOf("sleepy_prefs", WidgetBindingCore.PREFS_NAME, "widget_wide_grid").map {
            context.getSharedPreferences(it, Context.MODE_PRIVATE)
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        val tracker = SleepyApp.get().database.invalidationTracker
        val observer = object : InvalidationTracker.Observer("time_tables", "courses", "period_tables", "course_colors") {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        tracker.addObserver(observer)
        val ticker = launch {
            while (true) { delay(60_000); trySend(Unit) }
        }
        trySend(Unit)
        awaitClose {
            ticker.cancel()
            tracker.removeObserver(observer)
            prefs.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }.conflate().transformLatest {
        emit(WideGridPreviews(WideGridPreviewStatus.LOADING))
        try {
            val result = load(context, widgetId, WeekGridWideSettingsStore.read(context, widgetId))
            currentCoroutineContext().ensureActive()
            // A newer invalidation cancels this block, including a synchronous bitmap render.
            if (result.status != WideGridPreviewStatus.STALE) emit(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("WeekGridWidePreview", "preview failed: $widgetId", error)
            emit(WideGridPreviews(WideGridPreviewStatus.ERROR))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun load(context: Context, widgetId: Int, settings: WeekGridWideSettings,
        now: LocalDateTime = LocalDateTime.now(AppClock)): WideGridPreviews = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val receiver = ComponentName(context, WeekGridWideWidgetProvider::class.java)
        fun isAvailable() = manager.getAppWidgetInfo(widgetId)?.provider == receiver
        if (!isAvailable()) return@withContext WideGridPreviews(WideGridPreviewStatus.UNAVAILABLE)
        val binding = WidgetBindingStore.get(context, widgetId)
        val storedSettings = WeekGridWideSettingsStore.read(context, widgetId)
        val source = WidgetWeekDataLoader.resolve(widgetId, now)
        val dark = AppPrefs.isDarkMode(context,
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        val theme = AppPrefs.getThemeKey(context)
        val alias = AppPrefs.isWidgetUseAlias(context)
        val density = context.resources.displayMetrics.density
        val width = (300 * density).toInt().coerceAtLeast(1)
        val height = (160 * density).toInt().coerceAtLeast(1)
        val frames = displayOrder.map { mode ->
            currentCoroutineContext().ensureActive()
            val data = WeekGridWideWidgetProvider.dataForSource(context, source, mode, now, dark, theme)
            WideGridPreview(mode, data, WeekGridWideRenderer.renderBitmap(context, data,
                settings.copy(mode = mode), now, width, height))
        }
        currentCoroutineContext().ensureActive()
        if (!isAvailable()) return@withContext WideGridPreviews(WideGridPreviewStatus.UNAVAILABLE)
        if (binding != WidgetBindingStore.get(context, widgetId) ||
            storedSettings != WeekGridWideSettingsStore.read(context, widgetId) ||
            theme != AppPrefs.getThemeKey(context) || alias != AppPrefs.isWidgetUseAlias(context)) {
            return@withContext WideGridPreviews(WideGridPreviewStatus.STALE)
        }
        val status = when {
            source == null -> WideGridPreviewStatus.NO_TABLE
            frames.all { frame -> frame.data.days.all { it.courses.isEmpty() } } -> WideGridPreviewStatus.EMPTY
            else -> WideGridPreviewStatus.READY
        }
        // Compose and RemoteViews may still draw a published bitmap. Never eagerly recycle.
        WideGridPreviews(status, frames)
    }
}
