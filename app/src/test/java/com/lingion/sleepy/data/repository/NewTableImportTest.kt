package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class NewTableImportTest {
    private class SqlStore(val failAt: String? = null) : NewTableImportStore, AutoCloseable {
        val db = DriverManager.getConnection("jdbc:sqlite::memory:")
        init {
            db.createStatement().use { s ->
                s.execute("CREATE TABLE periods(id INTEGER PRIMARY KEY, time TEXT)")
                s.execute("CREATE TABLE tables(id INTEGER PRIMARY KEY, period INTEGER, selected INTEGER)")
                s.execute("CREATE TABLE courses(id INTEGER PRIMARY KEY, tableId INTEGER, groupId TEXT)")
                s.execute("INSERT INTO periods VALUES(7,'original')")
                s.execute("INSERT INTO tables VALUES(1,7,1)")
                s.execute("INSERT INTO courses VALUES(1,1,'old')")
            }
        }
        override suspend fun atomic(block: suspend () -> Long): Long {
            db.autoCommit = false
            return try { block().also { db.commit() } } catch (e: Throwable) {
                db.rollback(); throw e
            } finally { db.autoCommit = true }
        }
        fun scalar(sql: String): String = db.createStatement().use { s ->
            s.executeQuery(sql).use { r -> check(r.next()); r.getString(1) }
        }
        override suspend fun periodExists(id: Long) = scalar("SELECT COUNT(*) FROM periods WHERE id=$id") == "1"
        var captured = false
        override suspend fun captureUndo() { captured = true }
        override suspend fun insertPeriod(period: PeriodTableEntity): Long {
            check(captured) { "Undo must precede the first period write" }
            db.prepareStatement("INSERT INTO periods(time) VALUES(?)").use { it.setString(1,period.timeJson); it.executeUpdate() }
            if(failAt == "period") error("injected")
            return scalar("SELECT last_insert_rowid()").toLong()
        }
        override suspend fun insertTable(table: TimeTableEntity): Long {
            check(captured) { "Undo must precede the first timetable write" }
            db.prepareStatement("INSERT INTO tables(period,selected) VALUES(?,0)").use {
                it.setLong(1, requireNotNull(table.periodTableId)); it.executeUpdate()
            }
            if(failAt == "table") error("injected")
            return scalar("SELECT last_insert_rowid()").toLong()
        }
        override suspend fun insertCourses(courses: List<CourseEntity>) {
            courses.forEach { c -> db.prepareStatement("INSERT INTO courses(tableId,groupId) VALUES(?,?)").use {
                it.setLong(1,c.tableId);it.setString(2,c.groupId);it.executeUpdate()
            } }
            if(failAt == "courses") error("injected")
        }
        override suspend fun setDefault(id: Long) {
            db.createStatement().use { it.execute("UPDATE tables SET selected=CASE WHEN id=$id THEN 1 ELSE 0 END") }
            if(failAt == "default") error("injected")
        }
        override fun close() = db.close()
    }
    private fun course(group: String) = CourseEntity(groupId=group,tableId=1,courseName=" Same  Name ",day=1,
        startNode=1,step=1,startWeek=1,endWeek=20,color="#123456")
    private suspend fun apply(store: SqlStore, binding: Long? = null, keep: Boolean = false) =
        importNewTableAtomically(store,TimeTableEntity(name="new",startDate="2026-09-07"),
            listOf(course("a"),course("b")),PeriodTableEntity(name="new",timeJson="confirmed"),binding,keep)

    @Test fun captureRunsBeforeFirstWriteForPrivateAndSharedSchedules() = runBlocking {
        for (binding in listOf<Long?>(null, 7L)) SqlStore().use { s ->
            apply(s, binding = binding)
            assertTrue(s.captured)
        }
    }
    @Test fun rollbackAfterEveryWritePreservesAllTablesAndDefault() = runBlocking {
        for (stage in listOf("period","table","courses","default")) SqlStore(stage).use { s ->
            try { apply(s); fail("Expected failure at $stage") } catch (_: IllegalStateException) {}
            assertEquals("1",s.scalar("SELECT COUNT(*) FROM periods"))
            assertEquals("1",s.scalar("SELECT COUNT(*) FROM tables"))
            assertEquals("1",s.scalar("SELECT COUNT(*) FROM courses"))
            assertEquals("1",s.scalar("SELECT id FROM tables WHERE selected=1"))
        }
    }
    @Test fun explicitBindingIsNotCopiedOrOverwritten() = runBlocking {
        SqlStore().use { s ->
            val id=apply(s,binding=7)
            assertEquals("1",s.scalar("SELECT COUNT(*) FROM periods"))
            assertEquals("original",s.scalar("SELECT time FROM periods WHERE id=7"))
            assertEquals("7",s.scalar("SELECT period FROM tables WHERE id=$id"))
            assertEquals(id.toString(),s.scalar("SELECT id FROM tables WHERE selected=1"))
        }
    }
    @Test fun confirmedPrivateScheduleAndGroupPolicyArePreserved() = runBlocking {
        for (keep in listOf(false,true)) SqlStore().use { s ->
            val id=apply(s,keep=keep)
            assertEquals("confirmed",s.scalar("SELECT time FROM periods WHERE id=(SELECT period FROM tables WHERE id=$id)"))
            assertEquals(if(keep) "2" else "1",s.scalar("SELECT COUNT(DISTINCT groupId) FROM courses WHERE tableId=$id"))
        }
    }
    @Test fun deletedExplicitBindingFailsBeforeAnyWrite() = runBlocking {
        SqlStore().use { s ->
            try { apply(s,binding=99); fail("Missing binding must fail") } catch (_: IllegalArgumentException) {}
            assertEquals("1",s.scalar("SELECT COUNT(*) FROM periods"))
            assertEquals("1",s.scalar("SELECT COUNT(*) FROM tables"))
        }
    }
}
