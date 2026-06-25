package com.lingion.sleepy.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScheduleState(
    val tables: List<TimeTableEntity> = emptyList(),
    val selectedTableId: Long? = null,
    val courses: List<CourseEntity> = emptyList(),
    val currentWeek: Int = 1,
    val nodesPerDay: Int = 12,
    val selectedCourseId: Long? = null,
    val showCourseDialog: Boolean = false,
    val error: String? = null
) {
    val currentWeekCourses: List<CourseEntity>
        get() = courses.filter { it.inWeek(currentWeek) }
            .let { list ->
                val tj = currentTable?.timeJson
                if (tj == null) list else list.map { c -> c.normalizeNode(tj) }
            }
    val currentTable: TimeTableEntity?
        get() = tables.find { it.id == selectedTableId }
}

class ScheduleViewModel : ViewModel() {

    private val repo: ScheduleRepository = SleepyApp.get().repository

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    /** Whether the user has explicitly selected a table (vs auto-picking default on load) */
    private var manualSelectDone = false

    /**
     * 当前在 observe 课程的协程。切换表时必须先 cancel 上一个，
     * 否则多个协程同时往 state.courses 写，后启动的会被后 emit 的旧协程覆盖，
     * 导致"显示成另一张表"的 bug。
     */
    private var coursesJob: Job? = null

    init {
        loadTables()
    }

    private fun loadTables() {
        viewModelScope.launch {
            combine(
                repo.observeAllTables(),
                kotlinx.coroutines.flow.flowOf(LocalDate.now())
            ) { tables, _ -> tables }
                .collect { tables ->
                    if (tables.isEmpty()) {
                        // 没有课表就老实空着，不强行造占位表。
                        // selectedTableId = null，UI 走空态。
                        _state.update { it.copy(tables = emptyList(), selectedTableId = null) }
                        return@collect
                    }
                    val selectedId = _state.value.selectedTableId
                    val targetId: Long = if (manualSelectDone && selectedId != null && tables.any { t -> t.id == selectedId }) {
                        selectedId
                    } else {
                        tables.find { it.isDefault }?.id ?: tables.first().id
                    }
                    _state.update { it.copy(tables = tables, selectedTableId = targetId) }
                    loadCourses(targetId)
                }
        }
    }

    private fun loadCourses(tableId: Long) {
        // 取消旧协程，避免多个 observeCourses 同时写 state.courses 互相覆盖
        coursesJob?.cancel()
        coursesJob = viewModelScope.launch {
            repo.observeCourses(tableId).collect { courses ->
                _state.update { st ->
                    val table = st.tables.find { it.id == tableId }
                    val week = table?.let { DateUtils.currentWeek(it.startDate) } ?: 1
                    st.copy(courses = courses, currentWeek = week, nodesPerDay = table?.nodesPerDay ?: 12)
                }
            }
        }
    }

    fun selectTable(id: Long) {
        manualSelectDone = true
        _state.update { it.copy(selectedTableId = id) }
        loadCourses(id)
    }

    /** Create a new empty table with auto-generated name */
    suspend fun createEmptyTable(): Long {
        val existingNames = _state.value.tables.map { it.name }
        var index = _state.value.tables.size + 1
        var name = com.lingion.sleepy.SleepyApp.get().getString(R.string.default_table_with_num, index)
        while (name in existingNames) { index++; name = com.lingion.sleepy.SleepyApp.get().getString(R.string.default_table_with_num, index) }
        val now = LocalDate.now()
        val lastWeekMonday = now.with(java.time.DayOfWeek.MONDAY).minusWeeks(1)
        // 没有任何表时，新表自动 isDefault = true，避免出现"无默认表"
        val isFirstTable = _state.value.tables.isEmpty()
        val table = TimeTableEntity(
            name = name,
            startDate = lastWeekMonday.toString(),
            isDefault = isFirstTable
        )
        val id = repo.insertTable(table)
        if (isFirstTable) {
            // 数据库侧 isDefault 唯一性保证（其他表如有 isDefault 会自动清掉）
            repo.setDefault(id)
        }
        manualSelectDone = true
        _state.update { it.copy(selectedTableId = id) }
        loadCourses(id)
        return id
    }

    fun updateTable(table: TimeTableEntity) {
        viewModelScope.launch { repo.updateTable(table) }
    }

    fun deleteTable(id: Long) {
        viewModelScope.launch {
            repo.deleteTable(id)
            manualSelectDone = false
        }
    }

    fun changeWeek(week: Int) {
        if (week < 1) return
        _state.update { it.copy(currentWeek = week) }
    }

    fun openCourse(id: Long) {
        _state.update { it.copy(selectedCourseId = id, showCourseDialog = true) }
    }

    fun dismissCourseDialog() {
        _state.update { it.copy(showCourseDialog = false) }
    }

    fun addEmptyCourse() {
        viewModelScope.launch {
            val tableId = _state.value.selectedTableId
                ?: createEmptyTable()  // 没课表就先生成一张，再加课
            val empty = CourseEntity(
                groupId = java.util.UUID.randomUUID().toString(),
                tableId = tableId,
                courseName = com.lingion.sleepy.SleepyApp.get().getString(com.lingion.sleepy.R.string.new_course),
                teacher = "",
                room = "",
                day = DateUtils.todayDayOfWeek(),
                startNode = 1,
                step = 1,
                startWeek = _state.value.currentWeek,
                endWeek = _state.value.currentWeek + 16,
                color = "#FF6750A4"
            )
            val id = repo.insertCourse(empty)
            openCourse(id)
        }
    }

    fun updateCourse(course: CourseEntity) {
        viewModelScope.launch { repo.updateCourse(course) }
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch {
            repo.deleteCourse(id)
            dismissCourseDialog()
        }
    }
}
