package com.lingion.sleepy.ui.screen.schedule

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.screen.edit.OccurrenceSaveDialogs
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Contract for the float drag/delete persistence path: a legal grid drop moves exactly the
 * occurrence shown on the page (the rest of the recurring course stays intact), a resize
 * rewrite lands on that occurrence's span, deletes keep the standard veto dialog, and any
 * stale snapshot or vetoed dialog must leave the database untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SleepyApp::class)
class FloatOccurrenceHandlerTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private lateinit var dialogs: OccurrenceSaveDialogs
    private lateinit var app: SleepyApp
    private val asked = mutableListOf<Pair<String, String>>()
    private val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-10-05", maxWeek = 16, createdAt = 0)
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "Math", day = 1,
        startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")
    private val monday = LocalDate.parse("2026-10-05")
    private val wednesday = LocalDate.parse("2026-10-07")

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext<SleepyApp>()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        AppPrefs.setHolidayTransfers(app, table.id, emptyList())
        db.timeTableDao().insert(table)
        db.courseDao().insert(course)
        UndoManager.clear()
        dialogs = OccurrenceSaveDialogs(app)
    }

    @After fun cleanup() { ReminderRescheduler.resetForTest(); db.close(); UndoManager.clear() }

    private fun handler() = FloatOccurrenceHandler(app, repo, dialogs, table)

    @Test fun legalDropMovesOnlyThatOccurrenceToTheTargetCell() {
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().move(course, week = 1, day = 3, node = 4, step = 2))
        }
        runBlocking {
            assertEquals(2, repo.getCourses(table.id).size) // 去掉首周只剩 2-16 一条 + 单周移动块
            assertTrue(repo.getCoursesForDate(table, monday).isEmpty())
            val moved = repo.getCoursesForDate(table, wednesday).single()
            assertEquals(4, moved.startNode)
            assertEquals(2, moved.step)
            assertEquals("math", moved.groupId)
            assertEquals(1, moved.startWeek)
            assertEquals(1, moved.endWeek)
        }
    }

    @Test fun sameCellMoveWithoutResizeMergesBackToTheOriginalRow() {
        // 无实质变化 → 不弹任何确认(前端本不该调到这里,handler 兜底直存)。
        runWithDialogAnswers() {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().move(course, week = 1, day = 1, node = 1, step = 2))
        }
        runBlocking { assertEquals(listOf(course), repo.getCourses(table.id)) }
    }

    @Test fun resizeDuringTheDragRewritesThatOccurrenceSpan() {
        // 同日同起点的纯改跨度 → 「是否调整持续节次」一道确认。
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().move(course, week = 1, day = 1, node = 1, step = 3))
        }
        runBlocking {
            assertEquals(2, repo.getCourses(table.id).size)
            val mondayRow = repo.getCoursesForDate(table, monday).single()
            assertEquals(1, mondayRow.startNode)
            assertEquals(3, mondayRow.step)
        }
    }

    @Test fun crossWeekDropToTheSameCellStillAsksAndMovesThatOccurrence() {
        // 2026-10-28 用户反馈: 拖到右缘翻页后松手曾「像完全没有任何发生」——
        // 周复现课在新周同格本就有同 id 行, 旧单周号把它当「没变」静默自并入。
        // 现在: 从起拖周 resolve, 跨周必弹窗, 原周拆出、落点周生效。
        // 落点周同格已被本行 2-16 复现覆盖 → 与 OccurrenceEditor 同口径, 冲突二确认
        // 必须弹出让用户裁决(否决即不动), 确认即保留重叠, 绝不再静默吞掉。
        runWithDialogAnswers(0, 0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED,
                handler().move(course, week = 2, day = 1, node = 1, step = 2, sourceWeek = 1))
        }
        assertEquals(app.getString(R.string.occurrence_move_confirm_title), asked.first().first)
        assertTrue(asked.first().second.contains("Math"))
        assertTrue(asked.first().second.contains(app.getString(R.string.schedule_current_week, 1)))
        assertTrue(asked.first().second.contains(app.getString(R.string.schedule_current_week, 2)))
        assertEquals(2, asked.size) // 第二道 = 冲突确认(自身 2-16 复现行占用落点格)
        runBlocking {
            // 原周清空; 确认后落点周 = 复现行(2-16) + 用户批准的单周重叠块。
            assertTrue(repo.getCoursesForDate(table, monday).isEmpty())
            assertEquals(2, repo.getCoursesForDate(table, monday.plusWeeks(1)).size)
            assertTrue(UndoManager.hasSnapshot) // 落库成功 → 撤回胶囊有快照
        }
    }

    @Test fun crossWeekDropToAnotherCellMovesThatOccurrence() {
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED,
                handler().move(course, week = 2, day = 3, node = 4, step = 2, sourceWeek = 1))
        }
        runBlocking {
            assertTrue(repo.getCoursesForDate(table, monday).isEmpty())
            val moved = repo.getCoursesForDate(table, wednesday.plusWeeks(1)).single()
            assertEquals(4, moved.startNode)
            assertEquals(2, moved.step)
            assertEquals(2, moved.startWeek)
            assertEquals(2, moved.endWeek)
            assertEquals(listOf(course.copy(startWeek = 2)),
                repo.getCourses(table.id).filter { it.startWeek != it.endWeek })
        }
    }

    @Test fun vetoOnTheCrossWeekConfirmKeepsTheScheduleUntouched() {
        runWithDialogAnswers(null) {
            assertEquals(FloatOccurrenceHandler.Outcome.CANCELED,
                handler().move(course, week = 2, day = 3, node = 4, step = 2, sourceWeek = 1))
        }
        runBlocking { assertEquals(listOf(course), repo.getCourses(table.id)) }
    }

    @Test fun dropOnAnotherCellConfirmsTheMoveBeforeAnythingSaves() {
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().move(course, week = 1, day = 3, node = 4, step = 2))
        }
        assertEquals(app.getString(R.string.occurrence_move_confirm_title), asked.first().first)
        assertTrue(asked.first().second.contains("Math"))
        assertTrue(asked.first().second.contains("1-2"))
        assertTrue(asked.first().second.contains("4-5"))
    }

    @Test fun spanConfirmNamesTheOldAndTheNewSpan() {
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().move(course, week = 1, day = 1, node = 1, step = 3))
        }
        assertEquals(app.getString(R.string.occurrence_span_confirm_title), asked.first().first)
        assertTrue(asked.first().second.contains("1-2"))
        assertTrue(asked.first().second.contains("1-3"))
    }

    @Test fun vetoOnTheMoveConfirmKeepsTheScheduleUntouched() {
        runWithDialogAnswers(null) {
            assertEquals(FloatOccurrenceHandler.Outcome.CANCELED, handler().move(course, week = 1, day = 3, node = 4, step = 2))
        }
        runBlocking { assertEquals(listOf(course), repo.getCourses(table.id)) }
    }

    @Test fun vetoOnTheSpanConfirmKeepsTheScheduleUntouched() {
        runWithDialogAnswers(null) {
            assertEquals(FloatOccurrenceHandler.Outcome.CANCELED, handler().move(course, week = 1, day = 1, node = 1, step = 3))
        }
        runBlocking { assertEquals(listOf(course), repo.getCourses(table.id)) }
    }

    @Test fun deleteRemovesOnlyThatOccurrenceAndUndoRestoresIt() {
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().delete(course, week = 1))
        }
        runBlocking {
            assertEquals(listOf(course.copy(startWeek = 2)), repo.getCourses(table.id))
            assertTrue(repo.getCoursesForDate(table, monday).isEmpty())
            assertTrue(repo.restoreLastSnapshot())
            assertEquals(listOf(course), repo.getCourses(table.id))
        }
    }

    @Test fun deleteOfTheOnlyOccurrenceRemovesTheWholeCourse() {
        val single = course.copy(startWeek = 5, endWeek = 5)
        runBlocking { db.courseDao().update(single) }
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().delete(single, week = 5))
        }
        runBlocking { assertTrue(repo.getCourses(table.id).isEmpty()) }
    }

    @Test fun unknownCourseIdAfterARefreshIsNotFound() = runBlocking {
        assertEquals(FloatOccurrenceHandler.Outcome.NOT_FOUND,
            handler().move(course.copy(id = 999), week = 1, day = 3, node = 4, step = 2))
        assertEquals(listOf(course), repo.getCourses(table.id))
    }

    @Test fun concurrentCourseEditStalesTheDropInsteadOfOverwriting() {
        runBlocking { db.courseDao().update(course.copy(room = "changed elsewhere")) }
        // move() re-reads the snapshot itself, so it operates on the fresh row: the drop
        // must succeed against the changed row, proving the snapshot is taken internally.
        runWithDialogAnswers(0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED,
                handler().move(course.copy(room = "changed elsewhere"), week = 1, day = 3, node = 4, step = 2))
        }
        runBlocking { assertEquals(2, repo.getCourses(table.id).size) }
    }

    @Test fun conflictVetoKeepsTheScheduleUntouched() = runBlocking {
        db.courseDao().insert(course.copy(id = 8, groupId = "eng", courseName = "Eng", day = 3,
            startNode = 4, step = 2, startWeek = 1, endWeek = 16))
        runWithDialogAnswers(0, null) {
            assertEquals(FloatOccurrenceHandler.Outcome.CANCELED, handler().move(course, week = 1, day = 3, node = 4, step = 2))
        }
        assertEquals(2, repo.getCourses(table.id).size)
        assertEquals(listOf(course), repo.getCoursesForDate(table, monday))
    }

    @Test fun conflictConfirmationMovesWithTheConflictKept() = runBlocking {
        db.courseDao().insert(course.copy(id = 8, groupId = "eng", courseName = "Eng", day = 3,
            startNode = 4, step = 2, startWeek = 1, endWeek = 16))
        runWithDialogAnswers(0, 0) {
            assertEquals(FloatOccurrenceHandler.Outcome.SAVED, handler().move(course, week = 1, day = 3, node = 4, step = 2))
        }
        assertEquals(2, repo.getCoursesForDate(table, wednesday).size)
    }

    @Test fun deleteAsksForConfirmationAndVetoKeepsTheCourse() = runBlocking {
        runWithDialogAnswers(null) {
            assertEquals(FloatOccurrenceHandler.Outcome.CANCELED, handler().delete(course, week = 1))
        }
        assertEquals(listOf(course), repo.getCourses(table.id))
    }

    /**
     * Runs [body] on another thread; answers the dialogs the handler raises, in order, from
     * [answers] (null = veto). Fails when the handler raises more dialogs than scripted, and
     * records every raised (title, message) in [asked] for wording assertions.
     */
    private fun runWithDialogAnswers(vararg answers: Int?, body: suspend () -> Unit) {
        asked.clear()
        val started = CountDownLatch(1)
        var failure: Throwable? = null
        val running = Thread {
            started.countDown()
            try { runBlocking { body() } } catch (t: Throwable) { failure = t }
        }
        running.start()
        started.await()
        val scripted = answers.toList()
        var idx = 0
        val deadline = System.currentTimeMillis() + 5_000
        while (running.isAlive) {
            val q = dialogs.question
            if (q != null) {
                asked += q.title to q.message
                if (idx >= scripted.size) {
                    dialogs.answer(null)
                    throw AssertionError("handler raised more dialogs than scripted: ${q.title}")
                }
                dialogs.answer(scripted[idx++])
            }
            if (System.currentTimeMillis() > deadline) {
                failure?.let { throw AssertionError("handler body must not throw", it) }
                throw AssertionError("timed out after $idx/${scripted.size} scripted answers")
            }
            Thread.sleep(5)
        }
        running.join(5_000)
        failure?.let { throw AssertionError("handler body must not throw", it) }
        assertEquals("all scripted answers consumed", scripted.size, idx)
    }
}
