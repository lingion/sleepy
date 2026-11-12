package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.AppClock
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** Covers the real update broadcast -> provider -> RemoteViews image path used by a launcher. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-mdpi", application = SleepyApp::class)
class WeekGridFixedReceiverTest {
    private val app get() = ApplicationProvider.getApplicationContext<SleepyApp>()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val now = LocalDateTime.of(2026, 9, 28, 7, 0)
    private var previousWorkManager: Any? = null
    private var previousWorkDelegate: Any? = null

    @Before fun setup() = runBlocking {
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("database\$delegate").apply { isAccessible = true }.set(app, lazyOf(db))
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        val workClass = androidx.work.impl.WorkManagerImpl::class.java
        previousWorkManager = workClass.getDeclaredField("sDefaultInstance").apply { isAccessible = true }.get(null)
        previousWorkDelegate = workClass.getDeclaredField("sDelegatedInstance").apply { isAccessible = true }.get(null)
        if (!androidx.work.impl.WorkManagerImpl.isInitialized()) WorkManager.initialize(app, Configuration.Builder().build())
        AppClock.setAbsolute(now.atZone(AppClock.zone).toInstant().toEpochMilli())
        val table = repo.insertTable(TimeTableEntity(name = "Desktop", startDate = "2026-09-28", isDefault = true))
        repo.insertCourses(listOf(CourseEntity(tableId = table, groupId = "desktop-fill", courseName = "", room = "",
            day = 1, startNode = 1, step = 4, startWeek = 1, endWeek = 16, color = "#FF6750A4")))
        AppPrefs.setWidgetColorless(app, false)
        AppPrefs.setThemeKey(app, "default")
        AppPrefs.setThemeMode(app, AppPrefs.THEME_MODE_LIGHT)
    }

    @After fun resetTestServices() {
        AppClock.reset()
        WorkManager.getInstance(app).cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
        val workClass = androidx.work.impl.WorkManagerImpl::class.java
        workClass.getDeclaredField("sDefaultInstance").apply { isAccessible = true }.set(null, previousWorkManager)
        workClass.getDeclaredField("sDelegatedInstance").apply { isAccessible = true }.set(null, previousWorkDelegate)
    }

    @Test fun bothDesktopComponentsRefreshToUniformColorAndIgnoreStaleMode() = runBlocking {
        val shadow = shadowOf(manager)
        val application = shadowOf(app)
        // Robolectric 4.17 dispatches explicit app-widget broadcasts to every
        // manifest provider sharing the action. Keep its host/RemoteViews support,
        // and route the recorded explicit intents as Android does below.
        application.registeredReceivers.filter { it.broadcastReceiver is android.appwidget.AppWidgetProvider }
            .forEach { it.context.unregisterReceiver(it.broadcastReceiver) }
        val ids = listOf(WeekGridVerticalWidgetProvider::class.java, WeekGridHorizontalWidgetProvider::class.java).map { cls ->
            val id = shadow.createWidget(cls, R.layout.widget_bitmap_container)
            app.getSharedPreferences("widget_wide_grid", android.content.Context.MODE_PRIVATE).edit()
                .putString("mode_$id", "ORIGINAL").apply()
            id
        }
        suspend fun image(id: Int, previous: Bitmap? = null, expected: Bitmap? = null): Bitmap = withTimeout(10_000) {
            var result: Bitmap? = null
            while (result == null) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val drawable = shadow.getViewFor(id)?.findViewById<ImageView>(R.id.widget_bitmap)?.drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null && bitmap !== previous && (expected == null || bitmap.sameAs(expected))) result = bitmap else delay(10)
            }
            result
        }
        val before = ids.associateWith { image(it) }
        AppPrefs.setWidgetColorless(app, true)
        application.clearBroadcastIntents()
        WidgetUpdater.notifyDataChanged(app)
        val updates = application.broadcastIntents.filter { it.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE }
        assertEquals(setOf(WeekGridVerticalWidgetProvider::class.java.name, WeekGridHorizontalWidgetProvider::class.java.name),
            updates.map { it.component?.className }.toSet())
        for (intent in updates) {
            val id = requireNotNull(intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)).single()
            val receiver = shadow.getAppWidgetProviderFor(id)
            assertEquals(receiver.javaClass.name, intent.component?.className)
            receiver.onReceive(app, intent)
        }
        for (id in ids) {
            val provider = shadow.getAppWidgetProviderFor(id) as WeekGridWideWidgetProvider
            val settings = WeekGridWideSettings(mode = provider.layoutMode)
            val data = WeekGridWideWidgetProvider.loadData(app, id, settings, now)
            val oldImage = before.getValue(id)
            val direct = WeekGridWideRenderer.renderBitmap(app, data, settings, now, oldImage.width, oldImage.height)
            // A previously queued update can finish first. Await the complete new image,
            // rather than treating the first bitmap delivered after the toggle as final.
            val after = image(id, oldImage, direct)
            val layout = WeekGridWideRenderer.layout(app, data, settings, now, after.width, after.height)
            val card = layout.cards.single()
            val x = card.bounds.centerX().toInt()
            val y = card.bounds.centerY().toInt()
            val expected = resolveSchemePublic(app, "default", false).secondaryContainer.toArgb()
            for ((name, bitmap) in listOf("before" to before.getValue(id), "after" to after, "direct" to direct)) {
                val output = java.io.File("build/reports/week-grid-wide/desktop-${settings.mode}-$name.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            assertEquals("desktop RemoteViews must receive the neutral fill: ${settings.mode} ${after.width}x${after.height} at $x/$y direct=${direct.getPixel(x, y)}", expected, after.getPixel(x, y))
            assertNotEquals("toggling must replace the old colored desktop fill", before.getValue(id).getPixel(x, y), after.getPixel(x, y))
            assertEquals(if (settings.mode == WeekGridWideMode.VERTICAL) 7 else 3, layout.dates.size)
        }
    }
}
