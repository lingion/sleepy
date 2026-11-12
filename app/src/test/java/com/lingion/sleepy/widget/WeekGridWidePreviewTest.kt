package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-xhdpi", application = SleepyApp::class)
class WeekGridWidePreviewTest {
    private val app get() = ApplicationProvider.getApplicationContext<SleepyApp>()
    private val now = LocalDateTime.of(2026, 10, 4, 7, 0) // Sunday, next two days cross the week
    private lateinit var repo: ScheduleRepository

    @Before fun setup() {
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("database\$delegate").apply { isAccessible = true }.set(app, lazyOf(db))
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        val manager = shadowOf(AppWidgetManager.getInstance(app))
        manager.addBoundWidget(51, AppWidgetProviderInfo().apply { provider = ComponentName(app, WeekGridVerticalWidgetProvider::class.java) })
        manager.addBoundWidget(52, AppWidgetProviderInfo().apply { provider = ComponentName(app, WeekGridHorizontalWidgetProvider::class.java) })
    }

    private suspend fun table(name: String, isDefault: Boolean = false, empty: Boolean = false): Long {
        val id = repo.insertTable(TimeTableEntity(name = name, startDate = "2026-09-28", isDefault = isDefault))
        if (!empty) repo.insertCourses(listOf(1, 2, 7).map { day -> CourseEntity(
            tableId = id, groupId = "$name-$day", courseName = name, room = "Room $name", day = day,
            startNode = 1, step = 4, startWeek = 1, endWeek = 16, color = "#FF6750A4") })
        return id
    }

    @Test fun eachFixedPreviewUsesActualBindingAndProductionDispatcherAtRealDensity() = runBlocking {
        val a = table("Table A", isDefault = true)
        val b = table("Table B")
        WidgetBindingStore.put(app, 51, a)
        WidgetBindingStore.put(app, 52, b)
        for ((id, name) in listOf(51 to "Table A", 52 to "Table B")) {
            val result = WeekGridWidePreviewLoader.load(app, id, WeekGridWideSettings(), now)
            assertEquals(WideGridPreviewStatus.READY, result.status)
            assertEquals(listOf(if (id == 51) WeekGridWideMode.VERTICAL else WeekGridWideMode.HORIZONTAL), result.frames.map { it.mode })
            for (frame in result.frames) {
                assertTrue(frame.data.days.flatMap { it.courses }.isNotEmpty())
                assertEquals(setOf(name), frame.data.days.flatMap { it.courses }.map { it.courseName }.toSet())
                assertEquals((300 * app.resources.displayMetrics.density).toInt(), frame.bitmap.width)
                assertEquals((160 * app.resources.displayMetrics.density).toInt(), frame.bitmap.height)
                val direct = WeekGridWideRenderer.renderBitmap(app, frame.data, WeekGridWideSettings(frame.mode), now, frame.bitmap.width, frame.bitmap.height)
                assertTrue("preview and provider must use the same dispatcher", direct.sameAs(frame.bitmap))
                if (id == 51) export(frame.bitmap, "three-form-${frame.mode.name.lowercase()}.png")
            }
            if (id == 52) assertEquals(listOf(now.toLocalDate(), now.toLocalDate().plusDays(1), now.toLocalDate().plusDays(2)), result.frames.single().data.days.map { it.date })
        }
    }

    @Test fun followingDefaultIsResolvedAgainAndEmptyNeverInjectsSampleCourses() = runBlocking {
        val a = table("Default A", isDefault = true)
        val b = table("Default B")
        assertEquals(setOf("Default A"), WeekGridWidePreviewLoader.load(app, 51, WeekGridWideSettings(), now).frames.flatMap { it.data.days }.flatMap { it.courses }.map { it.courseName }.toSet())
        repo.setDefault(b)
        assertEquals(setOf("Default B"), WeekGridWidePreviewLoader.load(app, 51, WeekGridWideSettings(), now).frames.flatMap { it.data.days }.flatMap { it.courses }.map { it.courseName }.toSet())
        val empty = table("Empty", empty = true)
        WidgetBindingStore.put(app, 51, empty)
        val result = WeekGridWidePreviewLoader.load(app, 51, WeekGridWideSettings(), now)
        assertEquals(WideGridPreviewStatus.EMPTY, result.status)
        assertEquals(1, result.frames.size)
        assertTrue(result.frames.all { it.data.hasTable && it.data.days.all { day -> day.courses.isEmpty() } })
        assertTrue(a != b)
    }

    @Test fun missingTableHasOneHonestEmptyImageButUnknownReceiverHasNone() = runBlocking {
        val missing = WeekGridWidePreviewLoader.load(app, 51, WeekGridWideSettings(), now)
        assertEquals(WideGridPreviewStatus.NO_TABLE, missing.status)
        assertEquals(1, missing.frames.size)
        assertTrue(missing.frames.all { !it.data.hasTable && it.data.days.isEmpty() })
        val unknown = WeekGridWidePreviewLoader.load(app, 999, WeekGridWideSettings(), now)
        assertEquals(WideGridPreviewStatus.UNAVAILABLE, unknown.status)
        assertTrue(unknown.frames.isEmpty())
    }

    @Test fun changingHorizontalSideChangesPixelsButNotDatesAndCancellationDoesNotPublish() = runBlocking {
        table("Bound course", isDefault = true)
        val left = WeekGridWidePreviewLoader.load(app, 52, WeekGridWideSettings(), now)
        val right = WeekGridWidePreviewLoader.load(app, 52, WeekGridWideSettings(labelSide = WeekGridWideLabelSide.RIGHT), now)
        assertEquals(left.frames.last().data.days.map { it.date }, right.frames.last().data.days.map { it.date })
        assertFalse(left.frames.last().bitmap.sameAs(right.frames.last().bitmap))
        export(right.frames.last().bitmap, "three-form-horizontal-right.png")
        val published = java.util.concurrent.atomic.AtomicInteger()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val first = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = scope.launch {
            WeekGridWidePreviewLoader.observe(app, 51).collect {
                published.incrementAndGet()
                first.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        kotlinx.coroutines.withTimeout(10_000) { first.await() }
        job.cancel()
        job.join()
        val before = published.get()
        WidgetBindingStore.put(app, 51, 999)
        assertTrue(job.isCancelled)
        assertEquals("closed preview collection must not publish after leaving", before, published.get())
    }

    @Test fun fixedPreviewRetainsActualSemesterStateBeforeAndAfterTerm() = runBlocking {
        table("Semester", isDefault = true)
        for ((date, expected) in listOf(
            LocalDateTime.of(2026, 9, 20, 7, 0) to com.lingion.sleepy.util.DateUtils.SemesterStatus.BEFORE_START,
            LocalDateTime.of(2027, 9, 20, 7, 0) to com.lingion.sleepy.util.DateUtils.SemesterStatus.AFTER_END,
        )) {
            val result = WeekGridWidePreviewLoader.load(app, 51, WeekGridWideSettings(), date)
            assertEquals(expected, result.frames.first().data.semesterStatus)
        }
    }

    @Test fun productionSchedulerOnlyArmsPeriodBoundaryForVertical() = runBlocking {
        table("Schedule", isDefault = true)
        com.lingion.sleepy.util.AppClock.setAbsolute(now.atZone(com.lingion.sleepy.util.AppClock.zone).toInstant().toEpochMilli())
        try {
            val alarms = shadowOf(app.getSystemService(android.app.AlarmManager::class.java))
            WidgetBoundaryScheduler.armNext(app)
            fun boundaries() = alarms.scheduledAlarms.filter {
                shadowOf(it.operation).savedIntent.component?.className == WidgetBoundaryReceiver::class.java.name
            }
            assertTrue("vertical component contributes individual period boundaries", boundaries().isNotEmpty())
            shadowOf(AppWidgetManager.getInstance(app)).addBoundWidget(51, AppWidgetProviderInfo().apply {
                provider = ComponentName(app, WeekGridHorizontalWidgetProvider::class.java)
            })
            WidgetBoundaryScheduler.armNext(app)
            assertTrue("horizontal components do not contribute rolling period boundaries", boundaries().isEmpty())
        } finally { com.lingion.sleepy.util.AppClock.reset() }
    }

    private fun export(bitmap: Bitmap, name: String) {
        val file = File("build/reports/week-grid-wide/$name")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
