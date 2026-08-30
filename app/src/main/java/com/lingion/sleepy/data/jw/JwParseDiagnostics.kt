package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 教务直连导入失败诊断 — T9
 *
 * 负责在 parser 调用之上做一次"分类嗅探"：区分
 *   - 会话过期 / 登录页（HTTP 200 渲染的是登录页 HTML）
 *   - 页面无课表容器（iframe 跨域 / WebView 抓取时序问题）
 *   - 有容器但组头缺失（合并行 / OCR 截图 / 图片课表）
 *   - 有容器有组头但课程格全空（图片课表 / 本学期无课）
 *   - 可信空课表（页面文本含"暂无课表"/"未产生课表数据"）
 *   - 抓取协议不匹配（parser 解析为 0 课但页面是其他协议 — T8 兜底失败）
 *
 * 关键约束：**绝不** 把学号 / 姓名 / Cookie / token / 完整 HTML 写入 userMessage 或 Log。
 * 只输出"指纹片段"这种用户能看懂、技术人员也能定位的信息。
 */
object JwParseDiagnostics {

    /** 失败分类 — 与 strings.xml 的 jw_diag_* 系列键一一对应 */
    enum class Category {
        /** 抓到的就是登录页 / 会话过期页 / CAS 跳转页 */
        SESSION_EXPIRED,
        /** 没有任何课表容器（Table1 / kbtable / kbList / dateList / kbxx / kbgrid / kblist 全无） */
        NO_TABLE_CONTAINER,
        /** 有容器但表头行全是合并或缺失节次行头 */
        HEADER_NO_NODE,
        /** 有容器有行头但课程格是 <img> / 全 &nbsp; / 全空串（图片课表） */
        IMAGE_OR_EMPTY_CELLS,
        /** 页面文本含"暂无课表"/"本学期无课"/"尚未产生课表数据"等可信空声明 */
        EMPTY_SEMESTER,
        /** 抓取协议与 parser 不匹配（多协议混淆 / iframe 抓取拿不到目标） */
        WRONG_PROTOCOL,
        /** 兜底：parser 解析为 0 课但不属于以上六类 */
        UNKNOWN_EMPTY,
    }

    /** 单次 parser 尝试的结果快照 */
    data class ParserAttempt(
        val parserName: String,
        val courseCount: Int,
        val exception: String?,
    )

    /** 完整诊断结果 */
    data class Result(
        val category: Category,
        val attempts: List<ParserAttempt>,
        val matchedFeatures: List<String>,
        val courseCount: Int,
        val userMessage: String,
    )

    /**
     * 给一段 HTML 做"页面级嗅探"。
     * 优先级: SessionExpired > NoContainer > ImageOrEmpty > EmptySemester > WrongProtocol > UnknownEmpty。
     */
    fun classify(html: String, url: String, school: JwSchoolInfo?, parsersAttempted: List<ParserAttempt>): Result {
        require(html.isNotBlank()) { "html 不能为空" }
        val doc = Jsoup.parse(html)
        val bodyText = doc.body()?.text() ?: ""
        val matched = mutableListOf<String>()

        // 1) 会话过期 / 登录页
        val loginMarkers = listOf(
            "login_slogin" to Regex("""login_slogin|csrfToken|csrftoken"""),
            "登录" to Regex("""用户登录|请输入密码|请输入账号|登录系统|统一身份认证登录|请重新登录"""),
            "captcha" to Regex("""kaptcha|verifycode|RANDOMCODE|输入验证码|CheckCode"""),
            "logon" to Regex("""Logon\.do\?method=logon|/jsxsd/xk/LoginToXk|Logon\.do"""),
            "cas" to Regex("""iaaa\.pku\.edu\.cn|/cas/login|casAuth"""),
            "viewstate" to Regex("""__VIEWSTATE|ASP\.NET_SessionId"""),
        )
        var loginHit = false
        for ((name, re) in loginMarkers) {
            if (re.containsMatchIn(html)) {
                matched += name
                loginHit = true
            }
        }
        val formAction = doc.select("form[action]").firstOrNull()?.attr("action") ?: ""
        val looksLikeLogin = loginHit ||
                (formAction.isNotBlank() &&
                    (formAction.contains("login", ignoreCase = true) ||
                     formAction.contains("Logon", ignoreCase = true) ||
                     formAction.contains("slogin", ignoreCase = true)) &&
                    !formAction.contains("xskbcx", ignoreCase = true) &&
                    !formAction.contains("xskb", ignoreCase = true))
        if (looksLikeLogin) {
            return Result(
                category = Category.SESSION_EXPIRED,
                attempts = parsersAttempted,
                matchedFeatures = matched,
                courseCount = 0,
                userMessage = "登录页与会话过期：检测到 ${matched.joinToString("/")}，" +
                    "请确认已完成登录并停留在「个人课表」页（而非登录页或首页）"
            )
        }

        // 1.5) 可信空课表声明 — 优先于容器缺失判定（页面明确声明无课 → 不误报 NO_TABLE_CONTAINER）
        val emptySemesterMarkers = listOf(
            "暂无课表", "暂无课程", "本学期暂无", "本学期无课",
            "尚未产生课表数据", "本学期暂无课表数据", "未查询到课表",
            "没有可选的课程", "未排课"
        )
        val emptyHit = emptySemesterMarkers.firstOrNull { bodyText.contains(it) }
        if (emptyHit != null) {
            matched += emptyHit
            return Result(
                category = Category.EMPTY_SEMESTER,
                attempts = parsersAttempted,
                matchedFeatures = matched,
                courseCount = 0,
                userMessage = "页面声明本学期暂无课程：\"" + emptyHit + "\"。请确认已选对学期，" +
                    "或下学期开学后再导入"
            )
        }

        // 2) 页面无课表容器
        val containerMarkers = listOf(
            "id=Table1" to Regex("""(?i)id\s*=\s*["']Table1["']"""),
            "id=kbtable" to Regex("""(?i)id\s*=\s*["']kbtable["']"""),
            "id=table1" to Regex("""(?i)id\s*=\s*["']table1["']"""),
            "kbList" to Regex("""["']kbList["']"""),
            "kbxx" to Regex("""var\s+kbxx\s*=|kbxx\s*=\s*\["""),
            "kbgrid" to Regex("kbgrid_table|kbgrid_view"),
            "kblist" to Regex("kblist_table"),
            "dateList" to Regex("""["']dateList["']"""),
            "datagrid" to Regex("""\.datagrid\b|class\s*=\s*["'][^"']*datagrid"""),
            "displayTag" to Regex("""class\s*=\s*["'][^"']*displayTag"""),
        )
        val containerHits = containerMarkers.filter { (_, re) -> re.containsMatchIn(html) }.map { it.first }
        if (containerHits.isEmpty()) {
            matched += "no_container"
            return Result(
                category = Category.NO_TABLE_CONTAINER,
                attempts = parsersAttempted,
                matchedFeatures = matched,
                courseCount = 0,
                userMessage = "未找到课表容器（Table1 / kbtable / kbList / dateList 均缺失）。" +
                    "可能原因：①抓取协议与实际教务系统不匹配；②WebView 抓取时机过早，课表尚未加载；" +
                    "③页面为图片课表或跨域 iframe"
            )
        }
        matched += containerHits

        // 3) 有容器但组头无逐节行头
        val hasNodeHeader = Regex("""第\s*[一二三四五六七八九十0-9]+\s*节""").containsMatchIn(bodyText)
        if (!hasNodeHeader && containerHits.any { it != "kbList" && it != "dateList" && it != "kbxx" }) {
            // 纯 JSON 协议（kbList/dateList/kbxx）不做行头检查 — 它本来就没有行头
            return Result(
                category = Category.HEADER_NO_NODE,
                attempts = parsersAttempted,
                matchedFeatures = matched,
                courseCount = 0,
                userMessage = "找到课表容器（${containerHits.joinToString("/")}）但未识别到逐节行头。" +
                    "可能原因：①该课表为图片截图，请改用 HTML/CSV 文件导入或手动添加；" +
                    "②组头被合并（如'第一节-第二节'），请反馈开发者适配"
            )
        }

        // 4) 容器在但课程格 <img> — 图片课表
        val imgInTable = doc.select("table img").size + doc.select("td img").size
        val imgWithLongAlt = doc.select("img").count { it.attr("alt").length >= 3 }
        if (imgInTable >= 2 || imgWithLongAlt >= 1) {
            return Result(
                category = Category.IMAGE_OR_EMPTY_CELLS,
                attempts = parsersAttempted,
                matchedFeatures = matched + "img_in_table",
                courseCount = 0,
                userMessage = "课表单元格为图片（检测到 $imgInTable 个 <img>），Sleepy 无法识别。" +
                    "建议改用 HTML/CSV 文件导入，或手动添加课程"
            )
        }

        // 6) 抓取协议不匹配
        val schoolType = school?.type
        val expectedFamily = when (schoolType) {
            JwProtocol.TYPE_ZF, JwProtocol.TYPE_ZF_1 -> setOf("id=Table1")
            JwProtocol.TYPE_ZF_NEW -> setOf("kbList", "kbgrid", "kblist")
            JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_CRAZY, JwProtocol.TYPE_QZ_BR,
            JwProtocol.TYPE_QZ_WITH_NODE, JwProtocol.TYPE_QZ_OLD -> setOf("id=kbtable")
            JwProtocol.TYPE_URP_NEW -> setOf("dateList")
            JwProtocol.TYPE_URP -> setOf("displayTag")
            JwProtocol.TYPE_CF -> setOf("kbxx")
            JwProtocol.TYPE_PKU -> setOf("datagrid")
            JwProtocol.TYPE_BNUZ -> setOf("id=table1", "id=Table1")
            else -> null
        }
        if (expectedFamily != null && containerHits.none { it in expectedFamily }) {
            return Result(
                category = Category.WRONG_PROTOCOL,
                attempts = parsersAttempted,
                matchedFeatures = matched,
                courseCount = 0,
                userMessage = "抓取协议与学校配置不一致：学校标注 $schoolType，" +
                    "但页面容器为 ${containerHits.joinToString("/")}。" +
                    "可能原因：①学校已切换教务系统，请反馈开发者更新 schools.json；" +
                    "②抓取时机过早；③页面为图片课表"
            )
        }

        // 7) 兜底
        return Result(
            category = Category.UNKNOWN_EMPTY,
            attempts = parsersAttempted,
            matchedFeatures = matched,
            courseCount = 0,
            userMessage = "解析结果为空，但未找到明确原因。请尝试重新加载页面或反馈开发者。" +
                "（诊断特征：${matched.take(5).joinToString("/")}）"
        )
    }
}
