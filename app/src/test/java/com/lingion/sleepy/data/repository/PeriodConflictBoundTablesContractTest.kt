package com.lingion.sleepy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 甲案 (作息冲突三选项) Task 2 契约: 绑定课表全量实体查询。
 * 仓库无 Robolectric, 按既有契约测试风格锁源码中的数据通道。
 */
class PeriodConflictBoundTablesContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val timeTableDao by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/data/dao/TimeTableDao.kt").readText()
    }
    private val repository by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt").readText()
    }
    private val periodTableDao by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/data/dao/PeriodTableDao.kt").readText()
    }

    @Test
    fun dao_getAllBoundTo_filters_time_tables_by_periodTableId() {
        assertTrue(
            "TimeTableDao 必须提供 getAllBoundTo(periodTableId) 返回完整实体列表",
            timeTableDao.contains("fun getAllBoundTo(periodTableId: Long): List<TimeTableEntity>")
        )
        assertTrue(
            "getAllBoundTo 必须按 periodTableId 过滤 time_tables",
            Regex("@Query\\(\\s*\"SELECT \\* FROM time_tables WHERE periodTableId = :periodTableId")
                .containsMatchIn(timeTableDao)
        )
    }

    @Test
    fun repo_getTablesBoundTo_delegates_to_tableDao_getAllBoundTo() {
        assertTrue(
            "ScheduleRepository 必须暴露 getTablesBoundTo(periodTableId)",
            repository.contains("fun getTablesBoundTo(periodTableId: Long): List<TimeTableEntity>")
        )
        assertTrue(
            "repo 实现必须委托 tableDao.getAllBoundTo, 禁止旁路自拼 SQL",
            repository.contains("tableDao.getAllBoundTo(periodTableId)")
        )
    }

    @Test
    fun legacy_boundTableIds_is_not_repurposed_for_full_entities() {
        assertTrue(
            "PeriodTableDao 的 boundTableIds(ID 列表) 原样保留, 兼容既有调用方",
            periodTableDao.contains("fun boundTableIds(id: Long): List<Long>")
        )
        assertFalse(
            "弹窗作用域判定禁止复用 ID 列表逐个反查 (N+1), 必须走 getAllBoundTo",
            repository.contains("periodTableDao.boundTableIds(")
        )
    }

    @Test
    fun repo_does_not_invent_new_dao_for_bound_query() {
        // getAllBoundTo 只允许在 TimeTableDao 出现一次, 禁止在其他 DAO 重复定义同名查询。
        val daoRoot = findUpward("app/src/main/java/com/lingion/sleepy/data/dao")
        val offenders = daoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "TimeTableDao.kt" }
            .filter { it.readText().contains("fun getAllBoundTo(") }
            .map { it.name }
            .toList()
        assertEquals("getAllBoundTo 只能定义在 TimeTableDao", emptyList<String>(), offenders)
    }

    private fun assertFalse(message: String, condition: Boolean) {
        assertTrue(message, !condition)
    }
}
