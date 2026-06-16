package com.lingion.sleepy.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.DateUtils
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
}

class ScheduleViewModel : ViewModel() {

    private val repo: ScheduleRepository = SleepyApp.get().repository

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

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
                        // 创建默认课表
                        val default = TimeTableEntity(
                            name = "我的课表",
                            startDate = LocalDate.now().toString(),
                            isDefault = true
                        )
                        val id = repo.insertTable(default)
                        _state.update { it.copy(tables = listOf(default.copy(id = id)), selectedTableId = id) }
                        loadCourses(id)
                    } else {
                        val defaultId = tables.find { it.isDefault }?.id ?: tables.first().id
                        _state.update { it.copy(tables = tables, selectedTableId = defaultId) }
                        loadCourses(defaultId)
                    }
                }
        }
    }

    private fun loadCourses(tableId: Long) {
        viewModelScope.launch {
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
        _state.update { it.copy(selectedTableId = id) }
        loadCourses(id)
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
            val tableId = _state.value.selectedTableId ?: return@launch
            val empty = CourseEntity(
                tableId = tableId,
                courseName = "新课程",
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
        viewModelScope.launch {
            repo.updateCourse(course)
        }
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch {
            repo.deleteCourse(id)
            dismissCourseDialog()
        }
    }
}