package com.lingion.sleepy.data.migration

import android.content.SharedPreferences
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件名→SharedPreferences 的内存版 PrefsStore; 未预置文件名按 Android 语义返回空 prefs。 */
internal class FakePrefsStore(vararg fileNames: String) : MigrationExecutor.PrefsStore {
    val prefsByName = LinkedHashMap(fileNames.associate { it to FakePrefs() })
    override fun open(fileName: String): SharedPreferences =
        prefsByName.getOrPut(fileName) { FakePrefs() }
}

class MigrationExecutorTest {

    private fun newDaos(): AggregatedDaos {
        val courseDao = FakeCourseDao()
        val timeTableDao = FakeTimeTableDao()
        val periodTableDao = FakePeriodTableDao()
        val importDraftDao = FakeImportDraftDao()
        return AggregatedDaos(courseDao, timeTableDao, periodTableDao, importDraftDao)
    }

    private class AggregatedDaos(
        val courseDao: FakeCourseDao,
        val timeTableDao: FakeTimeTableDao,
        val periodTableDao: FakePeriodTableDao,
        val importDraftDao: FakeImportDraftDao,
    )

    @Test
    fun `export with database pulls persisted_state dependency into package`() = runBlocking {
        val daos = newDaos()
        val store = FakePrefsStore("sleepy_prefs", "custom_themes", "widget_bindings")

        val result = MigrationExecutor.export(
            selected = setOf(MigrationModule.DATABASE),
            prefsStore = store,
            courseDao = daos.courseDao,
            timeTableDao = daos.timeTableDao,
            periodTableDao = daos.periodTableDao,
            importDraftDao = daos.importDraftDao,
            createdAt = 1234L,
        )

        assertEquals(listOf("database", "persisted_state"), result.manifest.modules)
        // persisted_state 当前为空集, 仍是合法空快照
        val content = MigrationPackageCodec.read(result.bytes)
        assertEquals(emptyMap<String, MigrationPrefsCodec.PrefsFileSnapshot>(), MigrationPrefsCodec.decode(content.modules.getValue(MigrationModule.PERSISTED_STATE)).files)
        // prefs 文件未被选择 → widgets/preferences 不在包内
        assertTrue(MigrationModule.PREFERENCES !in content.modules)
        assertTrue(MigrationModule.WIDGETS !in content.modules)
    }

    @Test
    fun `export without database keeps selected modules only`() = runBlocking {
        val daos = newDaos()
        val store = FakePrefsStore("sleepy_prefs", "widget_bindings", "widget_scroll_prefs")
        store.open("widget_scroll_prefs").edit().putString("marker", "v").commit()

        val result = MigrationExecutor.export(
            selected = setOf(MigrationModule.WIDGETS),
            prefsStore = store,
            courseDao = daos.courseDao,
            timeTableDao = daos.timeTableDao,
            periodTableDao = daos.periodTableDao,
            importDraftDao = daos.importDraftDao,
        )

        val content = MigrationPackageCodec.read(result.bytes)
        assertEquals(listOf(MigrationModule.WIDGETS), content.modules.keys.toList())
        val widgetFiles = MigrationPrefsCodec.decode(content.modules.getValue(MigrationModule.WIDGETS)).files
        // 非空文件进包; preferences 文件不混入 widgets 模块
        assertTrue(widgetFiles.keys.containsAll(listOf("widget_scroll_prefs")))
        assertTrue("sleepy_prefs" !in widgetFiles.keys)
        assertEquals("v", widgetFiles.getValue("widget_scroll_prefs").entries.getValue("marker").string)
    }

    @Test
    fun `overwrite import replaces prefs files present in package and leaves absent files untouched`() = runBlocking {
        val daos = newDaos()
        val store = FakePrefsStore("sleepy_prefs", "custom_themes", "widget_bindings")
        // 本机: sleepy_prefs 有包内没有的 stale 键; widget_bindings 本机有、包内没有 → 应保持不动
        store.open("sleepy_prefs").edit().putString("stale", "old").commit()
        store.open("widget_bindings").edit().putString("localBinding", "keep-me").commit()

        val source = FakePrefsStore("sleepy_prefs", "widget_scroll_prefs")
        source.open("sleepy_prefs").edit().putString("weekCount", "20").commit()
        source.open("widget_scroll_prefs").edit().putBoolean("scrollOn", true).commit()

        val exported = MigrationExecutor.export(
            selected = setOf(MigrationModule.PREFERENCES, MigrationModule.WIDGETS),
            prefsStore = source,
            courseDao = daos.courseDao, timeTableDao = daos.timeTableDao,
            periodTableDao = daos.periodTableDao, importDraftDao = daos.importDraftDao,
        )

        val target = newDaos()
        val report = MigrationExecutor.import(
            exported.bytes, MigrationExecutor.ImportMode.OVERWRITE,
            prefsStore = store,
            courseDao = target.courseDao, timeTableDao = target.timeTableDao,
            periodTableDao = target.periodTableDao, importDraftDao = target.importDraftDao,
        )

        // 包内文件全量替换: stale 键消失
        val sleepy = store.open("sleepy_prefs")
        assertEquals("20", sleepy.getString("weekCount", ""))
        assertNull(sleepy.getString("stale", null))
        // 包内缺失文件不动本机
        assertEquals("keep-me", store.open("widget_bindings").getString("localBinding", ""))
        // 包内出现的新文件落库
        assertEquals(true, store.open("widget_scroll_prefs").getBoolean("scrollOn", false))
        assertEquals(listOf(MigrationModule.PREFERENCES, MigrationModule.WIDGETS), report.appliedModules)
        assertEquals(2, report.counts.prefFiles)
        assertEquals(2, report.counts.prefKeys)
    }

    @Test
    fun `overwrite import restores database rows with original ids`() = runBlocking {
        val daos = newDaos()
        daos.periodTableDao.insert(PeriodTableEntity(name = "p", timeJson = "[]", createdAt = 1, updatedAt = 1).copy(id = 9))
        daos.timeTableDao.insert(TimeTableEntity(name = "t", startDate = "", timeJson = "[]", createdAt = 1).copy(id = 5, periodTableId = 9))
        daos.courseDao.insert(CourseEntity(groupId = "g", tableId = 5, courseName = "课", day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 1, color = "#FF6750A4").copy(id = 101))

        val exported = MigrationExecutor.export(
            setOf(MigrationModule.DATABASE), FakePrefsStore("sleepy_prefs"),
            daos.courseDao, daos.timeTableDao, daos.periodTableDao, daos.importDraftDao,
        )

        val target = newDaos()
        target.courseDao.insert(CourseEntity(groupId = "x", tableId = 1, courseName = "脏数据", day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 1, color = "#FF0000").copy(id = 999))
        val report = MigrationExecutor.import(
            exported.bytes, MigrationExecutor.ImportMode.OVERWRITE, FakePrefsStore("sleepy_prefs"),
            target.courseDao, target.timeTableDao, target.periodTableDao, target.importDraftDao,
        )

        assertEquals(listOf(9L), target.periodTableDao.rows.keys.toList())
        assertEquals(listOf(5L), target.timeTableDao.rows.keys.toList())
        assertEquals(listOf(101L), target.courseDao.rows.keys.toList())
        assertEquals(1, report.counts.courses)
    }

    @Test
    fun `merge import keeps local prefs keys not present in package`() = runBlocking {
        val source = FakePrefsStore("sleepy_prefs")
        source.open("sleepy_prefs").edit().putString("shared", "from-package").commit()
        val daos = newDaos()
        val exported = MigrationExecutor.export(
            setOf(MigrationModule.PREFERENCES), source,
            daos.courseDao, daos.timeTableDao, daos.periodTableDao, daos.importDraftDao,
        )

        val store = FakePrefsStore("sleepy_prefs")
        store.open("sleepy_prefs").edit().putString("shared", "local").putString("localOnly", "mine").commit()
        val report = MigrationExecutor.import(
            exported.bytes, MigrationExecutor.ImportMode.MERGE, store,
            FakeCourseDao(), FakeTimeTableDao(), FakePeriodTableDao(), FakeImportDraftDao(),
        )

        val prefs = store.open("sleepy_prefs")
        assertEquals("from-package", prefs.getString("shared", ""))
        assertEquals("mine", prefs.getString("localOnly", ""))
        assertEquals(MigrationExecutor.ImportMode.MERGE, report.mode)
    }

    @Test
    fun `corrupt package is rejected before touching local data`() = runBlocking {
        val store = FakePrefsStore("sleepy_prefs")
        store.open("sleepy_prefs").edit().putString("safe", "untouched").commit()
        val daos = newDaos()
        daos.courseDao.insert(CourseEntity(groupId = "g", tableId = 1, courseName = "本机课", day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 1, color = "#FF0000"))

        val thrown = runBlocking {
            runCatching {
                MigrationExecutor.import(
                    "not a zip".toByteArray(), MigrationExecutor.ImportMode.OVERWRITE, store,
                    daos.courseDao, daos.timeTableDao, daos.periodTableDao, daos.importDraftDao,
                )
            }
        }
        assertTrue(thrown.exceptionOrNull() is MigrationPackageException)
        assertEquals("untouched", store.open("sleepy_prefs").getString("safe", ""))
        assertEquals(1, daos.courseDao.totalCount())
    }
}
