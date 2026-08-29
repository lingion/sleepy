package com.lingion.sleepy.data.jw

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.AppDatabase
import androidx.room.withTransaction
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
            val aliasesArr = obj.optJSONArray("aliases")
            val aliases = if (aliasesArr != null) {
                (0 until aliasesArr.length()).map { aliasesArr.getString(it) }
            } else emptyList()
            list += JwSchoolInfo(
                sortKey = obj.optString("sortKey", ""),
                name = obj.optString("name", ""),
                url = obj.optString("url", ""),
                type = obj.optString("type", "").ifBlank { null },
                aliases = aliases,
                sortKeyFull = obj.optString("sortKeyFull", "")
            )
        }
        return list
    }

    /**
     * 解析 HTML 源码，返回课程列表（不入库）
     */
    suspend fun parseHtml(html: String, protocolType: String): List<JwCourse> = withContext(Dispatchers.IO) {
        if (protocolType.isBlank()) {
            // 未知协议（URL 直接登录）：尝试所有 parser，取课程数最多的结果
            return@withContext tryAllParsers(html)
        }
        val parser: JwParser = when (protocolType) {
            JwProtocol.TYPE_QZ -> JwQzParser(html)
            JwProtocol.TYPE_QZ_CRAZY -> JwQzCrazyParser(html)
            JwProtocol.TYPE_QZ_BR -> JwQzParser(html)
            JwProtocol.TYPE_QZ_WITH_NODE -> JwQzParser(html)
            JwProtocol.TYPE_QZ_OLD -> JwQzParser(html)
            JwProtocol.TYPE_URP -> JwUrpParser(html)
            JwProtocol.TYPE_URP_NEW -> JwNewUrpParser(html)
            JwProtocol.TYPE_WISEDU -> JwWiseduParser(html)
            // zf / zf_1 = 老版正方（default2.aspx 时代），上游 wakeup 里 TYPE_ZF → ZhengFangParser
            JwProtocol.TYPE_ZF -> JwOldZfParser(html)
            JwProtocol.TYPE_ZF_1 -> JwOldZfParser(html, 1)
            JwProtocol.TYPE_ZF_NEW -> JwNewZfParser(html)
            else -> throw IllegalArgumentException("协议 $protocolType 暂不支持")
        }
        parser.generateCourseList()
    }

    /** 未知协议时，尝试所有 parser，取课程数最多的结果 */
    private fun tryAllParsers(html: String): List<JwCourse> = tryAllParsersImpl(html)

    companion object {
        /**
         * 兜底解析的实现，static 以便纯 JVM 单测直接验证兜底覆盖面
         * （issue #5 回归：老版正方页面必须能被 tryAllParsers 解析出来）。
         */
        fun tryAllParsersForTest(html: String): List<JwCourse> = tryAllParsersImpl(html)

        private fun tryAllParsersImpl(html: String): List<JwCourse> {
            val candidates = listOf(
                JwWiseduParser(html),
                JwNewUrpParser(html),
                JwNewZfParser(html),
                JwOldZfParser(html),
                JwOldZfParser(html, 1),
                JwQzParser(html),
                JwQzCrazyParser(html),
                JwUrpParser(html)
            )
            var best = emptyList<JwCourse>()
            for (p in candidates) {
                try {
                    val result = p.generateCourseList()
                    if (result.size > best.size) best = result
                } catch (e: Exception) { continue }
            }
            return best
        }
    }

    /**
     * 从 URL 自动检测教务协议类型
     */
    fun detectProtocolFromUrl(url: String): String? {
        val u = url.lowercase()
        return when {
            u.contains("jwapp/sys/") || u.contains("/jwapp/") -> JwProtocol.TYPE_WISEDU
            u.contains("jwglxt") || u.contains("/xtgl/") -> JwProtocol.TYPE_ZF_NEW
            u.contains("/jwtottxuxsysb/") -> JwProtocol.TYPE_ZF_NEW
            // 老版正方指纹：登录页 default2.aspx / 课表页 xskbcx.aspx（issue #5：手输老正方地址猜不出协议）
            u.contains("default2.aspx") || u.contains("xskbcx.aspx") -> JwProtocol.TYPE_ZF
            // 强智入口指纹：/jsxsd/ 及其变体 /jxd/
            u.contains("jsxsd") || u.contains("/jxd/") -> JwProtocol.TYPE_QZ
            u.contains("qz") || u.contains("strongdesk") -> JwProtocol.TYPE_QZ
            u.contains("urp") -> JwProtocol.TYPE_URP_NEW
            else -> null
        }
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
        // ★ 整个建表 + 落库包在单一事务里：中途失败回滚，避免留下空课表。
        val newId = db.withTransaction {
            val tableDao = db.timeTableDao()
            val courseDao = db.courseDao()

            // ★ 用 autoGenerate (id=0) 让 Room 分配真实主键，避免手动 max(id)+1 撞旧 ID 覆盖既有课表。
            val resolvedStartDate = startDate?.takeIf { it.isNotBlank() }
                ?: computeCurrentSemesterStart()
            val maxNode = if (nodesPerDay > 0) nodesPerDay else courses.maxOf { maxOf(it.startNode, it.endNode) }
            val newTable = TimeTableEntity(
                id = 0,
                name = tableName.ifBlank { "导入的课表" },
                startDate = resolvedStartDate,
                timeJson = timeJson.ifBlank { TimeTableUtils.DEFAULT_TIME_JSON },
                nodesPerDay = maxNode,
                isDefault = true  // 导入的课表设为默认，widget 直接展示
            )
            val generatedId = tableDao.insert(newTable)
            // 把其他表设为非 default，确保只有当前表是 default
            tableDao.setDefault(generatedId)

            // 落库课程
            val defaultColor = "#FF6750A4"
            // 按课程名分 groupId（同名课程视为一组，便于编辑）
            val nameToGroup = mutableMapOf<String, String>()
            val entities = toCourseEntities(courses, generatedId, defaultColor).map { c ->
                val gid = nameToGroup.getOrPut(c.courseName) { java.util.UUID.randomUUID().toString() }
                c.copy(groupId = gid)
            }
            courseDao.insertAll(entities)
            generatedId
        }
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
