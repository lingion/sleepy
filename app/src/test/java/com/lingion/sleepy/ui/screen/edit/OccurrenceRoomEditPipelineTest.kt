package com.lingion.sleepy.ui.screen.edit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseOccurrence
import com.lingion.sleepy.util.CourseOccurrences
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 长按/浮窗「调整本次」面板的地点编辑管线 (用户 2026-11-06 报: 单次调课只能改日期/
 * 节次/时间, 缺地点)。
 *
 * 锁定契约:
 * 1. 表单态即含「地点」输入框 (testTag occurrence-room), 种子 = 当前 room;
 * 2. 改地点保存 → saveOccurrence 拆原行剩余 + replacement 只覆盖本周, 其余周次保持
 *    原地点; 不改地点保存 → replacement 与原行可并回一条 (mergeMatchingRows 分桶键
 *    含 room, 不产生多余拆行);
 * 3. 乐观并发: 保存前 course 行被并发改 room → STALE 拒绝, 库里地点不被覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi", application = SleepyApp::class)
class OccurrenceRoomEditPipelineTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var app: SleepyApp
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    // 保存成功 (saveOccurrence 返 true → onDismiss) 的唯一终态信号; STALE/require 抛错
    // 路径都不会置真 → 用它当闸, 断言不再在「保存根本没发生」时假绿。
    @Volatile private var dismissed = false
    private val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07", maxWeek = 16, createdAt = 0)
    // 周一 1-2节 1-16周 — 调第 1 周的本次, 其余周次地点必须不动。
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "Math", room = "A101",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")
    private val date = java.time.LocalDate.parse("2026-10-05") // week 5 Monday (2026-09-07 起第 5 周)

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        AppPrefs.setHolidayTransfers(app, table.id, emptyList())
        db.timeTableDao().insert(table)
        db.courseDao().insert(course)
        UndoManager.clear()
    }

    @After fun cleanup() { ReminderRescheduler.resetForTest(); db.close(); UndoManager.clear() }

    private fun show(occurrence: CourseOccurrence) {
        dismissed = false
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                OccurrenceEditor(occurrence, table, onDismiss = { dismissed = true }, startInEdit = true)
            }
        }
        compose.waitForIdle()
    }

    private fun waitFor(predicate: suspend () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            runBlocking { predicate() }
        }
    }

    private suspend fun rowForDate() = repo.getCoursesForDate(table, date).single()

    @Test fun roomFieldSeedsFromCourseAndSavesOnlyThisOccurrenceToTheNewRoom() = runBlocking {
        show(CourseOccurrences.resolve(course, date, emptyList())!!)
        compose.onNodeWithTag("occurrence-room").performScrollTo()
        compose.onNodeWithTag("occurrence-room").performTextReplacement("B202")
        compose.onNodeWithText(app.getString(R.string.occurrence_save)).performScrollTo().performClick()
        waitFor { dismissed }
        // saveOccurrence: without() 抽走第 5 周 → 剩余 = [id7: 1..4 周, 新 id: 6..16 周]
        // (都 A101), replacement = 第 5 周 B202 (新 id); 不同 room 不并 — 3 行正确终态。
        val rows = repo.getCourses(1)
        assertEquals(3, rows.size)
        val remaining = rows.first { it.id == 7L }
        assertEquals("A101", remaining.room)
        assertTrue(remaining.inWeek(1))
        assertTrue(remaining.inWeek(4))
        assertFalse("第 5 周必须已从剩余行移除", remaining.inWeek(5))
        val moved = rowForDate()
        assertEquals("B202", moved.room)
        assertEquals(course.startNode, moved.startNode)
        assertEquals(course.step, moved.step)
        // 一次撤回回到拆分前的整条原行
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(listOf(course), repo.getCourses(1))
    }

    @Test fun keepingRoomVerbatimMergesBackIntoOneRow() = runBlocking {
        show(CourseOccurrences.resolve(course, date, emptyList())!!)
        compose.onNodeWithTag("occurrence-room").performScrollTo()
        compose.onNodeWithText(app.getString(R.string.occurrence_save)).performScrollTo().performClick()
        // 初始库里就有 1 行且并回后与种子同容, size==1 不能当闸 (保存没发生也成立) —
        // dismissed 是 saveOccurrence 返 true 的终态信号。
        waitFor { dismissed }
        assertEquals("A101", rowForDate().room)
        // room 未改 → 拆出的剩余与 replacement 完全同容, mergeMatchingRows 并回原行
        assertEquals(listOf(course), repo.getCourses(1))
    }

    @Test fun dirtyStoredRoomIsLeftByteIdenticalWhenUntouchedSoTheRowStillMergesBack() = runBlocking {
        // 导入端 (ScheduleParser position 分支) 不 trim — 库里 room 可带首尾空白。
        // 未改动时 replacement 必须与 without() 拆出的 remaining 逐字相同, 否则分桶键
        // (含 room) 永不并回 → 「什么都不改直接保存」也会把课程行永久拆碎。
        val dirty = course.copy(room = "A101 ")
        db.courseDao().update(dirty)
        show(CourseOccurrences.resolve(dirty, date, emptyList())!!)
        compose.onNodeWithTag("occurrence-room").performScrollTo()
        compose.onNodeWithText(app.getString(R.string.occurrence_save)).performScrollTo().performClick()
        waitFor { dismissed }
        val rows = repo.getCourses(1)
        assertEquals("未改地点必须并回一条, 不得半 trim 拆行", 1, rows.size)
        assertEquals("A101 ", rows.single().room)
    }

    @Test fun editingRoomNormalizesLeadingAndTrailingWhitespace() = runBlocking {
        val dirty = course.copy(room = "A101 ")
        db.courseDao().update(dirty)
        show(CourseOccurrences.resolve(dirty, date, emptyList())!!)
        compose.onNodeWithTag("occurrence-room").performScrollTo()
        compose.onNodeWithTag("occurrence-room").performTextReplacement(" B202 ")
        compose.onNodeWithText(app.getString(R.string.occurrence_save)).performScrollTo().performClick()
        waitFor { dismissed }
        assertEquals("B202", rowForDate().room)
    }

    @Test fun concurrentRoomChangeRejectsTheSaveAsStale() = runBlocking {
        show(CourseOccurrences.resolve(course, date, emptyList())!!)
        compose.onNodeWithTag("occurrence-room").performScrollTo()
        compose.onNodeWithTag("occurrence-room").performTextReplacement("B202")
        // 打开弹窗后并发改库里的 room (模拟另一端编辑) — 期望快照不等 → STALE
        db.courseDao().update(course.copy(room = "C303"))
        compose.onNodeWithText(app.getString(R.string.occurrence_save)).performScrollTo().performClick()
        // 保存协程末尾才置 error (OccurrenceEditor.save), 任何 DB 谓词在点击前就为真
        // 不构成闸 — 等 STALE 文案上屏 (= 拒绝分支跑完) 再断言库里地点。
        val stale = app.getString(R.string.occurrence_stale)
        waitFor { compose.onAllNodesWithText(stale).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("C303", repo.getCourses(1).single().room)
        assertFalse("STALE 拒绝不得走 onDismiss", dismissed)
    }
}
