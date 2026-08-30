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
            JwProtocol.TYPE_QZ_BR -> JwQzBrParser(html)
            JwProtocol.TYPE_QZ_WITH_NODE -> JwQzWithNodeParser(html)
            JwProtocol.TYPE_QZ_OLD -> JwOldQzParser(html)
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

    /**
     * 从 URL 自动检测教务协议类型（T6: 大小写归一 + 空值保护，判型逻辑在 [detectProtocolFromUrlImpl]）
     */
    fun detectProtocolFromUrl(url: String): String? {
        if (url.isBlank()) return null
        return detectProtocolFromUrlImpl(url.lowercase())
    }

    /**
     * T6 新增：HTML 页面级协议识别（WebView 抓到的完整 HTML，已由 T7 合并 iframe/frame）
     * @return JwProtocol.TYPE_* 常量或 null（unknown）
     */
    fun detectProtocolFromHtml(html: String): String? = detectProtocolFromHtmlImpl(html)

    /**
     * T6 新增：URL + HTML 组合判型。
     * URL 层命中（任何置信度）直接返回；否则 HTML 层；否则 null。
     */
    fun detectProtocol(html: String, url: String? = null): String? {
        url?.takeIf { it.isNotBlank() }?.let { u ->
            detectProtocolFromUrlImpl(u.lowercase())?.let { return it }
        }
        return detectProtocolFromHtmlImpl(html)
    }

    companion object {
        /**
         * 兜底解析的实现，static 以便纯 JVM 单测直接验证兜底覆盖面
         * （issue #5 回归：老版正方页面必须能被 tryAllParsers 解析出来）。
         */
        fun tryAllParsersForTest(html: String): List<JwCourse> = tryAllParsersImpl(html)

        // T6 新增：纯 JVM 测试入口（与 tryAllParsersForTest 同模式，无 Android Context 依赖）
        @JvmStatic fun detectProtocolFromUrlForTest(url: String): String? =
            if (url.isBlank()) null else detectProtocolFromUrlImpl(url.lowercase())
        @JvmStatic fun detectProtocolFromHtmlForTest(html: String): String? = detectProtocolFromHtmlImpl(html)
        @JvmStatic fun detectProtocolForTest(html: String, url: String?): String? =
            url?.takeIf { it.isNotBlank() }?.let { u -> detectProtocolFromUrlImpl(u.lowercase())?.let { return it } }
                ?: detectProtocolFromHtmlImpl(html)
        @JvmStatic fun detectProtocolHitFeaturesForTest(html: String): List<String> = detectProtocolHitFeatures(html)

        /**
         * T6 ①：URL 高置信有序判型链。小写化后的 URL 进来。
         *
         * 变更对照（vs 旧实现）：
         *  - B1 `/xtgl/` → 边界正则（无尾斜杠也命中）
         *  - B2 删裸 `qz` 子串（qzu/quiz 等误伤）与无人佐证的 strongdesk；改强智 4 锚点
         *  - B3 删裸 `urp` 钉死 URP_NEW；老/新 URP 各自精确锚点
         *  - B5 补 CF/PKU/BNUZ/HNUST URL 分支
         */
        internal fun detectProtocolFromUrlImpl(u: String): String? = when {
            // ⓪ CAS / authserver 统一身份认证网关页：只是一跳中转，不当任何协议指纹。
            //    service= 参数里常带 jwglxt 等业务路径，不与业务锚点同判（fp.cas-gateway）。
            u.matches(Regex(""".*/cas/login.*"""))
                || u.matches(Regex(""".*/authserver/login.*"""))
                || u.contains("/cas/login")
                || u.contains("/authserver/login") -> null

            // WebVPN 路径重写形态（/webvpn/<host>/、/http/<hex>/、hex-host.webvpn.）：
            // host 段不可见，CF/PKU/BNUZ/HNUST 的 host 级与弱路径锚点在重写下不可靠 → 跳过 ⑦-⑩
            u.contains("/webvpn/")
                || Regex("""/http/[0-9a-f]+/""").containsMatchIn(u)
                || u.contains(".webvpn.") -> {
                when {
                    u.contains("/jwapp/") -> JwProtocol.TYPE_WISEDU
                    u.contains("jwglxt")
                        || u.matches(Regex(""".*/xtgl(/|$).*"""))
                        || u.contains("/kbcx/")
                        || u.contains("xskbcx_cx")
                        || u.contains("/jwtottxuxsysb/") -> JwProtocol.TYPE_ZF_NEW
                    u.contains("default2.aspx")
                        || u.contains("xskbcx.aspx") -> JwProtocol.TYPE_ZF
                    u.contains("/jsxsd/")
                        || u.matches(Regex(""".*/jxd(/|$).*"""))
                        || u.contains("logon.do")
                        || u.contains("verifycode.servlet") -> JwProtocol.TYPE_QZ
                    u.contains("xkaction.do")
                        || u.contains("actiontype=6") -> JwProtocol.TYPE_URP
                    u.contains("thissemestercurriculum")
                        || u.contains("courseselect")
                        || u.contains("ajaxstudentschedule") -> JwProtocol.TYPE_URP_NEW
                    else -> null
                }
            }

            // ① WISEDU — 金智 jwapp 微应用，URL 唯一锚点，优先级最高
            u.contains("/jwapp/") -> JwProtocol.TYPE_WISEDU

            // ② ZF_NEW — 新版正方 jwglxt 全系锚点
            //   a) /jwglxt/ 全路径 b) /xtgl(/|$) 边界（B1） c) /kbcx/ 接口
            //   d) xskbcx_cx（API 名，与 .aspx 不冲突） e) /jwtottxuxsysb/（来源不明，保留）
            u.contains("jwglxt")
                || u.matches(Regex(""".*/xtgl(/|$).*"""))
                || u.contains("/kbcx/")
                || u.contains("xskbcx_cx")
                || u.contains("/jwtottxuxsysb/") -> JwProtocol.TYPE_ZF_NEW

            // ③ ZF — 老版正方 default2.aspx / xskbcx.aspx（.aspx 后缀，不与新正方 .html 混）
            u.contains("default2.aspx")
                || u.contains("xskbcx.aspx") -> JwProtocol.TYPE_ZF

            // ④ QZ — 强智入口四件套（B2: 替代裸 qz）
            u.contains("/jsxsd/")
                || u.matches(Regex(""".*/jxd(/|$).*"""))
                || u.contains("logon.do")
                || u.contains("verifycode.servlet") -> JwProtocol.TYPE_QZ

            // ⑤ URP — 老 URP TeachRA / displayTag，精确锚点 xkAction.do（B3）
            u.contains("xkaction.do")
                || u.contains("actiontype=6") -> JwProtocol.TYPE_URP

            // ⑥ URP_NEW — 课表接口入口（URL 层高置信）
            u.contains("thissemestercurriculum")
                || u.contains("courseselect")
                || u.contains("ajaxstudentschedule") -> JwProtocol.TYPE_URP_NEW

            // ⑦ CF — 青果/乘方教务 URL 精确锚点（cf-chengfang-login.expected.json urlMarkers）
            u.contains("/xsgrkbcx")
                || u.contains("/new/xskb")
                || u.contains("jxfw.gdut")
                || u.contains("zhjw.smu")
                || u.contains("jxgl.wyu")
                || u.contains("jw.hbmu") -> JwProtocol.TYPE_CF

            // ⑧ PKU — 北大 IAAA / elective
            u.contains("elective.pku")
                || u.contains("iaaa.pku") -> JwProtocol.TYPE_PKU

            // ⑨ BNUZ — 北师珠 es.bnuz
            u.contains("es.bnuz") -> JwProtocol.TYPE_BNUZ

            // ⑩ HNUST — 湖南科大 kdjw / xxjw（HNUSTParser 由 T3 补，T6 先把路由做对）
            u.contains("kdjw.hnust")
                || u.contains("xxjw.hnust")
                || u.contains("jwgl.nepu") -> JwProtocol.TYPE_HNUST

            // ⑪ 兜底 null — 触发 HTML 二次判定
            else -> null
        }

        /**
         * T6 ②：HTML 页面级判型。大写原文传入，内部一次性 lowercase。
         * CAS / authserver 网关页不设指纹（返回 null 走兜底）。
         */
        internal fun detectProtocolFromHtmlImpl(html: String): String? {
            if (html.isBlank()) return null
            val lower = html.lowercase()
            val title = extractTitle(html)

            return when {
                // ⓪ BNUZ 必须先于 ZF 判：北师珠页同含 __VIEWSTATE/CheckCode.aspx，
                //    唯一次级锚点是 form action="default.aspx"（老正方是 default2.aspx）。
                //    课表页无表单，用校名 title 兜底；含 default2.aspx 的页面（老正方混入提示）排除。
                ((lower.contains("es.bnuz") || lower.contains("action=\"default.aspx\""))
                    && !lower.contains("default2.aspx"))
                    || title.contains("北师大珠海")
                    || title.contains("珠海分校") -> JwProtocol.TYPE_BNUZ

                // ① ZF_NEW — zftal-ui 资源 + 教学管理信息服务平台标题
                //    注意不用 "正方软件+版本v-" 页脚（老正方页脚同为 版本 V-x.y，会误吸）
                lower.contains("zftal-ui-")
                    || title.contains("教学管理信息服务平台")
                    || lower.contains("login_slogin.html")
                    || (title.contains("统一身份认证") && lower.contains("csrftoken")) -> JwProtocol.TYPE_ZF_NEW

                // ② ZF — 老正方指纹三件套（__VIEWSTATE / GBK title / CheckCode.aspx）
                lower.contains("__viewstate")
                    || lower.contains("asp.net_sessionid")
                    || title.contains("欢迎使用正方教务管理系统")
                    || title.contains("正方教务管理系统")
                    || lower.contains("checkcode.aspx") -> JwProtocol.TYPE_ZF

                // ③ QZ — 强智资源路径 + 版权 + title
                lower.contains("/framework/")
                    || lower.contains("verifycode.servlet")
                    || lower.contains("qzdatasoft.com")
                    || title.contains("强智")
                    || title.contains("教学一体化服务平台")
                    || lower.contains("logon.do")
                    || lower.contains("randomcode") -> JwProtocol.TYPE_QZ

                // ④ WISEDU — 业务回调路径
                lower.contains("/jwapp/sys/")
                    || (lower.contains("authserver/login") && lower.contains("execution=")) -> JwProtocol.TYPE_WISEDU

                // ⑤ URP_NEW — SM3 加密资源 + URPNova
                lower.contains("/js/sm3/")
                    || lower.contains("sm3web.js")
                    || lower.contains("urpnova")
                    || lower.contains("/js/login/login.js") -> JwProtocol.TYPE_URP_NEW

                // ⑥ URP — displayTag 老 URP
                lower.contains("displaytag")
                    || lower.contains("/checkcode")
                    || lower.contains("/js/xkaction.js") -> JwProtocol.TYPE_URP

                // ⑦ CF — 青果关键字（乘方教务/乘方科技；课表页页脚也有，不限 title）
                lower.contains("乘方教务")
                    || lower.contains("乘方科技")
                    || lower.contains("/new/validatecode") -> JwProtocol.TYPE_CF

                // ⑧ PKU — 北大 IAAA / elective（课表页 title 含 北京大学选课系统）
                lower.contains("iaaa.pku.edu.cn")
                    || lower.contains("pku.edu.cn")
                    || lower.contains("北京大学选课系统")
                    || (lower.contains("oauth.jsp") && lower.contains("appid"))
                    || (lower.contains("syllabus") && lower.contains("elective")) -> JwProtocol.TYPE_PKU

                // ⑨ BNUZ — es.bnuz host（action=default.aspx 形态已在 ⓪ 判）
                lower.contains("es.bnuz") -> JwProtocol.TYPE_BNUZ

                // ⑩ HNUST — 湖南科大（HTML 层特征由 T3 补）
                lower.contains("hnust.cn") -> JwProtocol.TYPE_HNUST

                else -> null
            }
        }

        /**
         * 抽 <title>...</title>（GBK/UTF-8 兼容，大文档用 indexOf 截窗，不依赖 Jsoup）
         * @return title 文本；无 title 返回空串
         */
        internal fun extractTitle(html: String): String {
            val start = html.indexOf("<title", ignoreCase = true)
            if (start < 0) return ""
            val openEnd = html.indexOf('>', start)
            if (openEnd < 0) return ""
            val closeStart = html.indexOf("</title>", openEnd + 1, ignoreCase = true)
            if (closeStart < 0) return ""
            return html.substring(openEnd + 1, closeStart).trim()
        }

        /**
         * T6 诊断 API：命中的指纹特征列表（T9 接到导入失败错误提示）。
         */
        internal fun detectProtocolHitFeatures(html: String): List<String> {
            if (html.isBlank()) return emptyList()
            val lower = html.lowercase()
            val title = extractTitle(html)
            val hits = mutableListOf<String>()
            if (lower.contains("zftal-ui-")) hits += "zftal-ui-"
            if (title.contains("教学管理信息服务平台")) hits += "title:教学管理信息服务平台"
            if (lower.contains("__viewstate")) hits += "__VIEWSTATE"
            if (lower.contains("asp.net_sessionid")) hits += "ASP.NET_SessionId"
            if (lower.contains("verifycode.servlet")) hits += "verifycode.servlet"
            if (lower.contains("/framework/")) hits += "/framework/"
            if (lower.contains("qzdatasoft.com")) hits += "qzdatasoft.com"
            if (lower.contains("/jwapp/sys/")) hits += "/jwapp/sys/"
            if (lower.contains("/js/sm3/")) hits += "/js/sm3/"
            if (lower.contains("urpnova")) hits += "URPNova"
            if (lower.contains("displaytag")) hits += "displaytag"
            if (lower.contains("/js/xkaction.js")) hits += "/js/xkAction.js"
            if (lower.contains("/checkcode")) hits += "/checkCode"
            if (lower.contains("乘方教务")) hits += "乘方教务"
            if (lower.contains("乘方科技")) hits += "乘方科技"
            if (lower.contains("iaaa.pku.edu.cn")) hits += "iaaa.pku.edu.cn"
            if (lower.contains("pku.edu.cn")) hits += "pku.edu.cn"
            if (lower.contains("北京大学选课系统")) hits += "北京大学选课系统"
            if (lower.contains("es.bnuz")) hits += "es.bnuz"
            if (title.contains("北师大珠海") || title.contains("珠海分校")) hits += "title:北师大珠海"
            if (lower.contains("action=\"default.aspx\"")) hits += "form action=default.aspx"
            if (lower.contains("hnust.cn")) hits += "hnust.cn"
            return hits
        }

        private fun tryAllParsersImpl(html: String): List<JwCourse> {
            val candidates = listOf(
                JwWiseduParser(html),
                JwNewUrpParser(html),
                JwNewZfParser(html),
                JwOldZfParser(html),
                JwOldZfParser(html, 1),
                JwQzParser(html),
                JwQzBrParser(html),         // T2 新增
                JwQzWithNodeParser(html),   // T2 新增
                JwQzCrazyParser(html),
                JwOldQzParser(html),        // T2 新增
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
