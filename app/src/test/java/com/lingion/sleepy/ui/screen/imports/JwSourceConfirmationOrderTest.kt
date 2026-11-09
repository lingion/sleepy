package com.lingion.sleepy.ui.screen.imports

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.jw.JwCourse
import com.lingion.sleepy.data.jw.JwImportDraftPeriod
import com.lingion.sleepy.data.jw.JwImportDraftPhase
import com.lingion.sleepy.data.jw.JwImportDraftSnapshot
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.repository.ImportDraftRepository
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import java.io.File
import java.lang.reflect.Field
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** The first method is a source contract; the other methods exercise the real Activity and Room. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-xhdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class JwSourceConfirmationOrderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var context: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository
    private lateinit var drafts: ImportDraftRepository
    private var activity: ActivityScenario<JwImportActivity>? = null
    private val replacedFields = mutableListOf<RestoredField>()

    private val initialTableName = "P15 内存恢复课表"
    private val editedTableName = "P15 周数确认后的课表"
    private val metadata = """{"schemaVersion":1,"adapter":{"schoolId":"UJS","adapterId":"UJS_02","name":"研究生","category":"研究生","importUrl":"https://example.edu.cn/yjs"},"config":{"semesterStartDate":null,"semesterTotalWeeks":24,"defaultClassDuration":50,"defaultBreakDuration":0,"firstDayOfWeek":7}}"""

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(database)
        drafts = ImportDraftRepository(database)

        // Do not run SleepyApp.onCreate: it starts unrelated background database readers.
        // The real Activity gets its context from Robolectric's isolated Application;
        // this attached SleepyApp holds only the real dependencies installed below.
        val app = SleepyApp()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        replaceField(SleepyApp::class.java, app, "database\$delegate", lazyOf(database))
        replaceField(SleepyApp::class.java, app, "repository\$delegate", lazyOf(repository))
        replaceField(SleepyApp::class.java, app, "importDraftRepository\$delegate", lazyOf(drafts))
        // Some production readers call AppDatabase.get directly rather than the app delegate.
        replaceField(AppDatabase::class.java, null, "instance", database)
        replaceField(SleepyApp::class.java, null, "instance", app)
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
    }

    @After fun cleanup() {
        try {
            // Closing the real Activity clears its ViewModelStore and disposes its Compose tree.
            activity?.close()
        } finally {
            try {
                ReminderRescheduler.resetForTest()
                shadowOf(Looper.getMainLooper()).idle()
                if (::database.isInitialized) database.close()
            } finally {
                replacedFields.asReversed().forEach { it.field.set(it.owner, it.originalValue) }
                replacedFields.clear()
                UndoManager.clear()
            }
        }
    }

    // Original source placement contract, retained without changing its assertions.
    @Test fun `source suggestions follow the editable calendar and precede period editing`() {
        val root = System.getProperty("sleepy.test.root") ?: error("sleepy.test.root is required")
        val source = File(root, "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt").readText()
        val confirm = source.substringAfter("stage is Stage.ConfigureConfirm && parsedCourses.isNotEmpty() ->")
            .substringBefore("stage is Stage.WebViewLogin")
        val positions = listOf("\"jw_date_field\"", "\"jw_weeks_field\"", "\"jw_name_field\"",
            "JwSourceSuggestions(importSourceJson)", "TimeSlotEditor(").map(confirm::indexOf)
        assertTrue("Date, total weeks, name, source suggestions and periods must all be visible", positions.all { it >= 0 })
        assertTrue("Confirmation fields must keep their review order", positions.zipWithNext().all { (a, b) -> a < b })
    }

    /** Scenario P15-UI-1: restored values and actual layout order, without reading production source. */
    @Test fun restoredDraftShowsConfirmationFieldsInReviewOrder() {
        openConfirmationDraft()
        val date = compose.onNodeWithTag("jw_date_field")
        val weeks = compose.onNodeWithTag("jw_weeks_field")
        val name = compose.onNodeWithTag("jw_name_field")
        val sourceEntry = compose.onNodeWithText(context.getString(R.string.jw_source_entry, "研究生"))
        val suggestions = compose.onNodeWithText(context.getString(R.string.jw_source_suggestions))
        val classMinutes = compose.onNodeWithText(context.getString(R.string.jw_source_class_minutes, "50"))
        val breakMinutes = compose.onNodeWithText(context.getString(R.string.jw_source_break_minutes, "0"))
        val firstWeekday = compose.onNodeWithText(context.getString(
            R.string.jw_source_first_weekday, context.resources.getStringArray(R.array.day_names)[6]
        ))
        val periods = compose.onNodeWithTag("jw_confirm_slot_mode#0")

        listOf(date, weeks, name, sourceEntry, suggestions, classMinutes, breakMinutes, firstWeekday, periods)
            .forEach { it.performScrollTo().assertIsDisplayed() }
        date.assertTextContains("2026-09-07")
        weeks.assertTextContains("24")
        name.assertTextContains(initialTableName)

        // Establish one scroll offset before reading every coordinate. Offscreen nodes
        // are still measured by this non-lazy Column; use their actual unclipped bounds.
        date.performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val positions = listOf(
            "date" to date.getUnclippedBoundsInRoot().top.value,
            "weeks" to weeks.getUnclippedBoundsInRoot().top.value,
            "name" to name.getUnclippedBoundsInRoot().top.value,
            "source entry" to sourceEntry.getUnclippedBoundsInRoot().top.value,
            "source suggestions" to suggestions.getUnclippedBoundsInRoot().top.value,
            "class duration" to classMinutes.getUnclippedBoundsInRoot().top.value,
            "break duration" to breakMinutes.getUnclippedBoundsInRoot().top.value,
            "first weekday" to firstWeekday.getUnclippedBoundsInRoot().top.value,
            "period editor" to periods.getUnclippedBoundsInRoot().top.value,
        )
        println("P15 actual confirmation positions in dp at one scroll offset: $positions")
        assertTrue(
            "Actual controls must follow date, weeks, name, source suggestions, periods: $positions",
            positions.zipWithNext().all { (before, after) -> before.second < after.second }
        )
        assertNoImportedRows()
    }

    /** Scenario P15-UI-2: a fresh confirmation must reject fewer weeks than the last course week. */
    @Test fun rejectsSemesterWeeksBeforeTheLastCourseWeek() {
        assertRejectedWeeks("15")
    }

    /** Scenario P15-UI-3: a fresh confirmation must reject values past the 366-week upper bound. */
    @Test fun rejectsSemesterWeeksPast366() {
        assertRejectedWeeks("367")
    }

    /** Scenario P15-UI-4: checkpoint an edited value, then save through real DAOs. */
    @Test fun editedSemesterWeeksCheckpointAndPersistWithoutChangingSourceClocks() {
        val draftId = openConfirmationDraft()
        compose.onNodeWithTag("jw_weeks_field").assertTextContains("24")
        replaceWeeks("26")
        // Wait for the real checkpoint before another edit, so writes cannot overtake each other.
        awaitState { runBlocking { drafts.get(draftId)?.termWeekCount == 26 } }
        compose.onNodeWithTag("jw_name_field").performScrollTo().performTextReplacement(editedTableName)
        awaitState {
            runBlocking {
                drafts.get(draftId)?.let { it.termWeekCount == 26 && it.tableName == editedTableName } == true
            }
        }
        val checkpoint = requireNotNull(runBlocking { drafts.get(draftId) })
        assertEquals(metadata, checkpoint.importSourceJson)
        assertEquals("UJS_02", checkpoint.selectedSgAdapterId)
        assertEquals(JwImportDraftPhase.CONFIGURE_CONFIRM, checkpoint.phase)
        assertEquals("2026-09-07", checkpoint.termStartDate)
        assertEquals("", checkpoint.datedCourseOrigin)
        assertEquals(listOf(JwImportDraftPeriod(1, "08:00", "08:45")), checkpoint.periods)
        assertEquals("08:00", checkpoint.courses.single().startTime)
        assertEquals("08:45", checkpoint.courses.single().endTime)
        assertNoImportedRows()

        compose.onNodeWithTag("jw_config_confirm").assertIsEnabled().performClick()
        awaitState {
            runBlocking {
                database.timeTableDao().count() == 1 && database.courseDao().getAll().size == 1 &&
                    database.importDraftDao().getById(draftId) == null
            }
        }
        val table = runBlocking { database.timeTableDao().getAll().single() }
        assertEquals(editedTableName, table.name)
        assertEquals("2026-09-07", table.startDate)
        assertEquals(26, table.maxWeek)
        assertEquals(metadata, table.importSourceJson)
        assertEquals(1, table.nodesPerDay)

        val course = runBlocking { database.courseDao().getByTable(table.id).single() }
        assertEquals("离散数学", course.courseName)
        assertEquals("甲", course.teacher)
        assertEquals("A101", course.room)
        assertEquals(1, course.day)
        assertEquals(1, course.startNode)
        assertEquals(1, course.step)
        assertEquals(1, course.startWeek)
        assertEquals(16, course.endWeek)
        assertTrue(course.ownTime)
        assertTrue(course.isIrregularTime)
        assertEquals("08:00", course.startTime)
        assertEquals("08:45", course.endTime)

        assertEquals(1, runBlocking { database.periodTableDao().getAll().size })
        assertNotNull("The restored automatic bell schedule must be saved", table.periodTableId)
        val period = runBlocking { database.periodTableDao().getById(table.periodTableId!!) }
        assertNotNull(period)
        val effectiveRows = TimeTableUtils.parseTimeSlotRows(table.hydratedWith(period).timeJson)
        assertEquals(1, effectiveRows.size)
        assertEquals(1, effectiveRows.single().node)
        assertEquals("08:00", effectiveRows.single().start)
        assertEquals("08:45", effectiveRows.single().end)
        assertNull(runBlocking { database.importDraftDao().getById(draftId) })
    }

    /** P15-UI-5: an invalid checkpoint stays readable and the real Activity restores the typed text. */
    @Test fun invalidSemesterWeekCheckpointRemainsReadableAndKeepsTheTypedValue() {
        val draftId = openConfirmationDraft()
        val beforePayload = requireNotNull(runBlocking {
            database.importDraftDao().getById(draftId)
        }).payloadJson
        replaceWeeks("367")
        // Both the broken and repaired implementations change the real stored payload.
        // Waiting for that change prevents an early read of the original valid 24-week draft.
        awaitState {
            runBlocking {
                database.importDraftDao().getById(draftId)?.payloadJson?.let { it != beforePayload } == true
            }
        }
        compose.onNodeWithTag("jw_config_confirm").assertIsEnabled().performClick()
        val weekError = context.getString(R.string.jw_invalid_semester_weeks, 16)
        compose.onNodeWithText(weekError).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("jw_weeks_field").assertTextContains("367")
        assertNoImportedRows()
        activity!!.onActivity { assertFalse(it.isFinishing) }

        val checkpoint = runBlocking { drafts.get(draftId) }
        assertNotNull("A rejected week edit must leave the checkpoint readable", checkpoint)
        val recovered = requireNotNull(checkpoint)
        assertEquals(metadata, recovered.importSourceJson)
        assertEquals("UJS_02", recovered.selectedSgAdapterId)
        assertEquals(listOf(JwImportDraftPeriod(1, "08:00", "08:45")), recovered.periods)
        assertEquals("08:00", recovered.courses.single().startTime)
        assertEquals("08:45", recovered.courses.single().endTime)

        // Rebuild the real Activity rather than reseeding fields or replacing setContent.
        activity!!.recreate()
        awaitState { compose.onAllNodesWithTag("jw_config_confirm").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("jw_weeks_field").performScrollTo().assertTextContains("367")
        compose.onNodeWithTag("jw_name_field").assertTextContains(initialTableName)
        compose.onNodeWithTag("jw_config_confirm").assertIsEnabled().performClick()
        compose.onNodeWithText(weekError).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("jw_weeks_field").assertTextContains("367")
        assertNoImportedRows()

        // Only this explicit correction is allowed to become the imported semester length.
        replaceWeeks("26")
        awaitState { runBlocking { drafts.get(draftId)?.termWeekCount == 26 } }
        compose.onNodeWithTag("jw_config_confirm").assertIsEnabled().performClick()
        awaitState {
            runBlocking {
                database.timeTableDao().count() == 1 && database.courseDao().getAll().size == 1 &&
                    database.importDraftDao().getById(draftId) == null
            }
        }
        val table = runBlocking { database.timeTableDao().getAll().single() }
        assertEquals(26, table.maxWeek)
        assertEquals(metadata, table.importSourceJson)
        val course = runBlocking { database.courseDao().getByTable(table.id).single() }
        assertEquals("08:00", course.startTime)
        assertEquals("08:45", course.endTime)
        assertTrue(course.ownTime)
        assertTrue(course.isIrregularTime)
        val period = runBlocking { database.periodTableDao().getById(requireNotNull(table.periodTableId)) }
        val rows = TimeTableUtils.parseTimeSlotRows(table.hydratedWith(period).timeJson)
        assertEquals(listOf(TimeTableUtils.TimeSlotRow(1, "08:00", "08:45")), rows)
    }

    private fun openConfirmationDraft(): String {
        val snapshot = JwImportDraftSnapshot(
            school = JwSchoolInfo(
                sortKey = "UJS", name = "P15 内存回归学校", url = "https://example.edu.cn/yjs",
                type = JwProtocol.TYPE_SG_ASSET, status = JwSchoolInfo.STATUS_SUPPORTED,
            ),
            courses = listOf(JwCourse(
                name = "离散数学", room = "A101", teacher = "甲", day = 1,
                startNode = 1, endNode = 1, startWeek = 1, endWeek = 16, type = 0,
                startTime = "08:00", endTime = "08:45",
            )),
            periods = listOf(JwImportDraftPeriod(1, "08:00", "08:45")),
            termStartDate = "2026-09-07",
            tableName = initialTableName,
            phase = JwImportDraftPhase.CONFIGURE_CONFIRM,
            termWeekCount = 24,
            datedCourseOrigin = "",
            importSourceJson = metadata,
            selectedSgAdapterId = "UJS_02",
        )
        val id = runBlocking { drafts.save(snapshot, sourceType = "jw", sourceUrl = snapshot.school.url) }
        assertEquals(snapshot, runBlocking { drafts.get(id) })
        assertNoImportedRows()
        activity = ActivityScenario.launch<JwImportActivity>(
            Intent(context, JwImportActivity::class.java).putExtra(JwImportActivity.EXTRA_DRAFT_ID, id)
        )
        awaitState { compose.onAllNodesWithTag("jw_config_confirm").fetchSemanticsNodes().isNotEmpty() }
        return id
    }

    private fun replaceWeeks(value: String) {
        compose.onNodeWithTag("jw_weeks_field").performScrollTo().performTextReplacement(value)
        compose.onNodeWithTag("jw_weeks_field").assertTextContains(value)
    }

    private fun assertRejectedWeeks(value: String) {
        openConfirmationDraft()
        compose.onNodeWithTag("jw_weeks_field").assertTextContains("24")
        replaceWeeks(value)
        compose.onNodeWithTag("jw_config_confirm").assertIsEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.jw_invalid_semester_weeks, 16))
            .performScrollTo().assertIsDisplayed()
        assertNoImportedRows()
        activity!!.onActivity { assertFalse("A rejected import must keep the Activity open", it.isFinishing) }
    }

    private fun assertNoImportedRows() {
        assertEquals("An unconfirmed or rejected import must not create a timetable", 0,
            runBlocking { database.timeTableDao().count() })
        assertTrue("An unconfirmed or rejected import must not insert courses",
            runBlocking { database.courseDao().getAll().isEmpty() })
        assertTrue("An unconfirmed or rejected import must not create a bell schedule",
            runBlocking { database.periodTableDao().getAll().isEmpty() })
    }

    private fun awaitState(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
        compose.waitForIdle()
    }

    private fun replaceField(type: Class<*>, owner: Any?, name: String, value: Any?) {
        val field = type.getDeclaredField(name).apply { isAccessible = true }
        replacedFields += RestoredField(field, owner, field.get(owner))
        field.set(owner, value)
    }

    private data class RestoredField(val field: Field, val owner: Any?, val originalValue: Any?)
}
