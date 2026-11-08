// THU (清华大学 zhjwxk.cic.tsinghua.edu.cn) parser — wakeup-parity-thu-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * 清华大学 (THU) 教务解析器 — zhjwxk.cic.tsinghua.edu.cn
 *
 * 以下协议指纹来自 Starrah/THUCourseHelper 固定源码；fixture 为人工构造，未经登录实测：
 *  1. `<form action="/syxk.vsyxkKcapb.do">` + `name="p_xnxq"` 学期字段
 *  2. `<script>` 块内 `function setInitValue() {` + `Event.observe(...)`
 *  3. `strHTML += "<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>"` 课程链接
 *  4. `strHTML += "<b>课程名</b>"` 课程名 (复用 WakeUp C1 模板)
 *  5. `strHTML1 += "；教师"；课程类型"；周次"；地点"` 4 行连续 (C1 模式, 中文分号 ；)
 *  6. `getElementById('a{big}_{day}').innerHTML += strHTML` 网格坐标
 *  + C2 模式 `strHTML = "<a class='blue_red_none' ...><b><font color='blue'>...</font></b></a><font color='blue'>...</font>";`
 *
 *  - Starrah/THUCourseHelper THUCourseDataSouce.kt @ 64a543518f9007e2b5a615582bd049acf5d2ada9 (MIT): C1/C2 脚本及整体课表入口。
 *  - lingion/wakeup-mod THUParser.java @ 879f0d6781da19d1da9300d5eda6b0c2629b6a63: 反编译 APK 的大节映射与作息表；未核实原发布者授权，不复用源码。
 *
 * 与 Starrah 的课程字段正则保持同形；Sleepy 的 WebView 只抓当前页面 HTML，
 * 不在 JS 中另写协议正则。
 */
class JwThuparser(source: String) : JwParser(JwThuCapturePayload.htmlOrOriginal(source)) {
    private val semesterDataJson = JwThuCapturePayload.semesterJson(source)

    companion object {
        // ---- 节次↔大节映射 (仅反编译 WakeUp v6.1.06 副本可核；未登录实测) ----

        /**
         * 大节索引 (big, 0-6) → 实际起课节点 (startNode)。
         * big=0 占位 (无课); big=1 第 1 节; big=2 第 3-5 节 (连 3 节);
         * big=3 第 6-7 节; big=4 第 8-9 节; big=5 第 10-11 节; big=6 第 12-14 节 (连 3 节)。
         * 与固定版本反编译 THUParser 数组一致。
         */
        val START_NODE_MAP: IntArray = intArrayOf(0, 1, 3, 6, 8, 10, 12)

        /**
         * 大节索引 (big, 0-6) → 实际结课节点 (endNode)。
         * 与 WakeUp THUParser f9221OooO0OO 一致。
         */
        val END_NODE_MAP: IntArray = intArrayOf(0, 2, 5, 7, 9, 11, 14)

        /**
         * 反编译 WakeUp v6.1.06 中的 14 节作息表；不是学校当前官方作息保证。
         * pair[0] = 起始时间 HH:MM, pair[1] = 结束时间 HH:MM, index = 节次 1-14。
         * 与 WakeUp THUParser.OooO0O0() 同源 (WakeUp 字段名 TimeDetail 节次+起始+结束)。
         */
        val THU_TIME_SLOTS: List<Pair<String, String>> = listOf(
            "08:00" to "08:45",
            "08:50" to "09:35",
            "09:50" to "10:35",
            "10:40" to "11:25",
            "11:30" to "12:15",
            "13:30" to "14:15",
            "14:20" to "15:05",
            "15:20" to "16:05",
            "16:10" to "16:55",
            "17:05" to "17:50",
            "17:55" to "18:40",
            "19:20" to "20:05",
            "20:10" to "20:55",
            "21:00" to "21:45",
        )

        // ---- regex 常量 (与 Starrah/THUCourseHelper Kotlin 源码对照) ----

        /** 课程链接 `strHTML += "<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>"` — 8 位 courseId。 */
        val RE_C1_LINK = Regex("strHTML \\+= \"<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>\"")

        /** 课程名 `strHTML += "<b>(.*)</b>"`。 */
        val RE_C1_TITLE = Regex("""strHTML \+= "<b>(.*)</b>"""")

        /** 课程后续字段 (教师/课程属性/周安排/地点) — `strHTML1 += "；(.*)"` (中文分号 ；)。 */
        val RE_C1_DATA = Regex("""strHTML1 \+= ?"；(.*)"""")

        /** 网格坐标 `getElementById('a(\d)_(\d)').innerHTML += strHTML` — big (大节) + day (星期)。 */
        val RE_C1_WEEKBIG = Regex("""getElementById\('a(\d)_(\d)'\).innerHTML \+= strHTML""")

        /**
         * 特殊课程 (C2 模式, Starrah 实证为实验课/教辅类): 单行 strHTML = "..." 含 blue_red_none class
         *  + 蓝色教师 + 蓝色附加信息 ("北大"/"北外" / 周次 / 时间)。
         */
        val RE_C2_BLUE_RED_NONE = Regex(
            """strHTML = "<a class='blue_red_none' href='.*?p_id=([0-9A-Z]+)'.*?><b><font color='blue'>(.*?)</font></b></a><font color='blue'>(.*?)</font>";"""
        )

        /** 学期字段 `name="p_xnxq" value="..."` — 跨仓印证协议指纹 1。 */
        val RE_SEMESTER = Regex("""name="p_xnxq" value="([\d\-]+?)"""")

        /** setInitValue 函数定义 (定位 JS 块起点)。 */
        val RE_SET_INIT_VALUE = Regex("""function setInitValue\(\)""")

        /** Event.observe (定位 JS 块终点)。 */
        val RE_EVENT_OBSERVE = Regex("""Event\.observe""")

        /** 数字串 — 用于周次数字解析。 */
        val RE_NUMBER = Regex("""\d+""")
    }

    /** 课程类型识别 (教务统计 XKTYPE_LIST, Starrah 实证)。 */
    private val xkTypeList = listOf("必修", "限选", "任选")
    internal var skippedCourseCount: Int = 0
        private set

    override fun generateCourseList(): List<JwCourse> {
        val parser = JwThuScriptParser(source, semesterDataJson)
        val courses = parser.generateCourseList()
        skippedCourseCount = parser.skippedCourseCount
        return courses
    }

    override fun confidence(): Int {
        var hits = 0
        if (RE_SEMESTER.containsMatchIn(source)) hits++
        if (RE_SET_INIT_VALUE.containsMatchIn(source)) hits++
        if (RE_C1_LINK.containsMatchIn(source)) hits++
        if (RE_C1_TITLE.containsMatchIn(source)) hits++
        if (RE_C1_WEEKBIG.containsMatchIn(source)) hits++
        if (RE_C1_DATA.containsMatchIn(source)) hits++
        return when {
            hits >= 6 -> 95
            hits >= 5 -> 80
            hits >= 4 -> 70
            hits >= 3 -> 50
            hits >= 2 -> 30
            hits >= 1 -> 10
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> {
        val out = mutableListOf<String>()
        if (skippedCourseCount > 0) out += "thu:skipped-unsupported=$skippedCourseCount"
        if (source.contains("zhjwxk") || source.contains("cic.tsinghua")) out += "thu:login-or-sso-marker"
        if (RE_SEMESTER.containsMatchIn(source)) out += "thu:p_xnxq-form"
        if (RE_SET_INIT_VALUE.containsMatchIn(source)) out += "thu:setInitValue-block"
        if (RE_C1_LINK.containsMatchIn(source)) out += "thu:strHTML-course-link"
        if (RE_C1_WEEKBIG.containsMatchIn(source)) out += "thu:aN_M-innerHTML"
        if (xkTypeList.any { source.contains(it) }) out += "thu:xktype-必修/限选/任选"
        if (RE_C1_DATA.containsMatchIn(source)) out += "thu:week-text-grammar"
        if (out.isEmpty()) out += "guard:login-only-no-setInitValue"
        return out
    }
}
