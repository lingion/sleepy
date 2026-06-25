package com.lingion.sleepy.data.jw

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/**
 * 教务直连导入 ViewModel
 *
 * 职责：
 *  1. 加载 [schools.json] 学校列表
 *  2. 用户选定学校 + 协议后，通过 WebView 抓 HTML 源码
 *  3. 用对应协议 parser 解析 HTML → List<JwCourse>
 *  4. 转 [CourseEntity] 列表，落库
 *
 * 简化点（相对 wakeup 原版 ImportViewModel）：
 *  - 不在 ViewModel 内做 HTTP 抓取（WebView 内完成）
 *  - 不在 ViewModel 内做登录流程（用户输账号密码 + 验证码）
 *  - 特殊学校（清华/吉大/华科等）v1.0.8 不支持
 */
class JwImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _schools = MutableStateFlow<List<JwSchoolInfo>>(emptyList())
    val schools: StateFlow<List<JwSchoolInfo>> = _schools.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    init {
        loadSchools()
    }

    private fun loadSchools() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val text = app.assets.open("schools.json")
                    .bufferedReader().use { it.readText() }
                val list = parseSchoolsJson(text)
                _schools.value = list
            } catch (e: Exception) {
                _importState.value = ImportState.Error("加载学校列表失败: ${e.message}")
            }
        }
    }

    private fun parseSchoolsJson(text: String): List<JwSchoolInfo> {
        val arr = JSONArray(text)
        val list = ArrayList<JwSchoolInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list += JwSchoolInfo(
                sortKey = obj.optString("sortKey", ""),
                name = obj.optString("name", ""),
                url = obj.optString("url", ""),
                type = obj.optString("type", "").ifBlank { null }
            )
        }
        return list
    }

    /**
     * 解析 HTML 源码，返回课程列表（不入库）
     */
    suspend fun parseHtml(html: String, protocolType: String): List<JwCourse> = withContext(Dispatchers.IO) {
        val parser: JwParser = when (protocolType) {
            JwProtocol.TYPE_QZ -> JwQzParser(html)
            JwProtocol.TYPE_QZ_CRAZY -> JwQzCrazyParser(html)
            JwProtocol.TYPE_QZ_BR -> JwQzParser(html)  // 简化：先 fallback 到 QzParser
            JwProtocol.TYPE_QZ_WITH_NODE -> JwQzParser(html)
            JwProtocol.TYPE_QZ_OLD -> JwQzParser(html)
            JwProtocol.TYPE_URP -> JwUrpParser(html)
            JwProtocol.TYPE_URP_NEW -> JwNewUrpParser(html)
            JwProtocol.TYPE_WISEDU -> JwWiseduParser(html)
            JwProtocol.TYPE_ZF -> JwQzParser(html)  // ZF 先 fallback，后续补 JwNewZFParser
            JwProtocol.TYPE_ZF_NEW -> JwQzParser(html)
            JwProtocol.TYPE_ZF_1 -> JwQzParser(html)
            else -> throw IllegalArgumentException("协议 $protocolType 暂不支持")
        }
        parser.generateCourseList()
    }

    /**
     * 把 JwCourse 列表转成 sleepy 的 CourseEntity 列表
     */
    fun toCourseEntities(courses: List<JwCourse>, tableId: Long, defaultColor: String): List<CourseEntity> {
        return courses.map { jw ->
            val step = (jw.endNode - jw.startNode + 1).coerceAtLeast(1)
            CourseEntity(
                id = 0,
                groupId = "",
                tableId = tableId,
                courseName = jw.name.ifBlank { "未命名" },
                teacher = jw.teacher,
                room = jw.room,
                day = jw.day.coerceIn(1, 7),
                startNode = jw.startNode.coerceAtLeast(1),
                step = step,
                startWeek = jw.startWeek.coerceAtLeast(1),
                endWeek = jw.endWeek.coerceAtLeast(jw.startWeek),
                type = jw.type,
                color = defaultColor
            )
        }
    }

    /**
     * 创建新课表并落库。
     * 返回新 tableId。
     */
    suspend fun importAsNewTable(
        courses: List<JwCourse>,
        tableName: String,
        startDate: String? = null,
        timeJson: String = "",
        nodesPerDay: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        if (courses.isEmpty()) throw IllegalArgumentException("课程列表为空，请确认已到达课表页面")

        val db = AppDatabase.get(getApplication())
        val tableDao = db.timeTableDao()
        val courseDao = db.courseDao()

        // 1. 创建新 table
        val newId = (tableDao.getAll().maxOfOrNull { it.id } ?: 0) + 1
        val resolvedStartDate = startDate?.takeIf { it.isNotBlank() }
            ?: computeCurrentSemesterStart()
        val maxNode = if (nodesPerDay > 0) nodesPerDay else courses.maxOf { maxOf(it.startNode, it.endNode) }
        val newTable = TimeTableEntity(
            id = newId,
            name = tableName.ifBlank { "导入的课表" },
            startDate = resolvedStartDate,
            timeJson = timeJson.ifBlank { TimeTableUtils.DEFAULT_TIME_JSON },
            nodesPerDay = maxNode,
            isDefault = false
        )
        tableDao.insert(newTable)

        // 2. 落库课程
        val defaultColor = "#FF6750A4"
        // 按课程名分 groupId（同名课程视为一组，便于编辑）
        val nameToGroup = mutableMapOf<String, String>()
        val entities = toCourseEntities(courses, newId, defaultColor).map { c ->
            val gid = nameToGroup.getOrPut(c.courseName) { java.util.UUID.randomUUID().toString() }
            c.copy(groupId = gid)
        }
        courseDao.insertAll(entities)

        newId
    }

    /**
     * 默认学期开始日期：本学期第一周周一的 ISO 日期。
     * 如果当前是寒暑假（2月/8月），回退到上一学期。
     */
    private fun computeCurrentSemesterStart(): String {
        val today = LocalDate.now()
        val month = today.monthValue
        val semesterStartYear = if (month in 8..12) today.year else today.year - 1
        val semesterStartMonth = if (month in 8..12) 9 else 2
        val firstDay = LocalDate.of(semesterStartYear, semesterStartMonth, 1)
        return firstDay.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
            .toString()
    }

    sealed class ImportState {
        object Idle : ImportState()
        data class Parsed(val courses: List<JwCourse>) : ImportState()
        data class Imported(val tableId: Long) : ImportState()
        data class Error(val message: String) : ImportState()
    }
}
