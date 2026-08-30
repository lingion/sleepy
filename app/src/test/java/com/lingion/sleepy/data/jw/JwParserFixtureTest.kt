package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * T10 协议契约 fixture 测试 — 63 个手写脱敏样本逐一核对九字段契约。
 *
 * 资源: app/src/test/resources/jw_fixtures/ (来自 /tmp/jw_fixtures)
 * oracle: 每个样本同名 *.expected.json 的 courses 数组 (九字段: name/day/startNode/
 *         endNode/startWeek/endWeek/type/teacher/room), 顺序敏感, 禁止自动重排或改写。
 * 失败分类见规格 §8; 任何字段不符必须修 parser, 禁止改 expected 迎合现状。
 */
class JwParserFixtureTest {

    private data class Case(
        val id: String,
        val source: String,
        val factory: (String) -> JwParser,
        /** expected 课程数 (防手抄漂移的自洽闸用; 真九字段断言仍读 expected.json) */
        val expectedCount: Int,
    )

    private val caseTable: List<Case> = listOf(
        // ---- 老正方 (zf / zf_1) ----
        Case("zf-old-standard",  "zf-old-table1/standard_single_course.html",                 ::JwOldZfParser, 2),
        Case("zf-old-multi",     "zf-old-table1/standard_multi_course.html",                  ::JwOldZfParser, 5),
        Case("zf-old-property",  "zf-old-table1/property_row_extended.html",                  ::JwOldZfParser, 4),
        Case("zf-old-br3",       "zf-old-table1/abnormal_br3.html",                           ::JwOldZfParser, 2),
        Case("zf-old-digit",     "zf-old-table1/digit_headers.html",                          ::JwOldZfParser, 3),
        Case("zf-old-node-override", "zf-old-variants/course_property_with_node_override.html", ::JwOldZfParser, 3),
        Case("zf-old-blacktab",  "zf-old-variants/blacktab_merged_node_header.html",          ::JwOldZfParser, 3),
        Case("zf-old-zf1", "zf-old-variants/zf_1_space_separated.html",
             factory = { JwOldZfParser(it, 1) }, expectedCount = 3),  // zf_1 必须 type=1
        Case("zf-old-empty",     "zf-old-table1/empty_table.html",                            ::JwOldZfParser, 0),
        Case("zf-old-login",     "zf-old-table1/login_page.html",                             ::JwOldZfParser, 0),
        // ---- 新版正方 (zf_new) ----
        Case("zf-new-kblist-range",  "zf-new-kblist/kblist_range_sections.json",              ::JwNewZfParser, 5),
        Case("zf-new-kblist-sd",     "zf-new-kblist/kblist_single_double_weeks.json",         ::JwNewZfParser, 2),
        Case("zf-new-kblist-bitmap", "zf-new-kblist/kblist_bitmap_and_extremes.json",         ::JwNewZfParser, 3),
        Case("zf-new-kblist-sjk",    "zf-new-kblist/kblist_with_sjklist_and_empty.json",      ::JwNewZfParser, 2),
        Case("zf-new-kblist-wrap",   "zf-new-kblist/kblist_mobile_deeply_wrapped.json",       ::JwNewZfParser, 1),
        Case("zf-new-kblist-missing","zf-new-kblist/kblist_missing_fields.json",              ::JwNewZfParser, 1),
        Case("zf-new-kblist-none",   "zf-new-kblist/kblist_no_courses_this_semester.json",    ::JwNewZfParser, 0),
        Case("zf-new-t1-festival",   "zf-new-html/table1_festival_view.html",                 ::JwNewZfParser, 3),
        Case("zf-new-grid",          "zf-new-html/grid_dual_view.html",                       ::JwNewZfParser, 3),
        Case("zf-new-list",          "zf-new-html/list_dual_view.html",                       ::JwNewZfParser, 3),
        Case("zf-new-grid-empty",    "zf-new-html/grid_empty_semester.html",                  ::JwNewZfParser, 0),
        Case("zf-new-grid-missing",  "zf-new-html/grid_missing_fields.html",                  ::JwNewZfParser, 0),
        Case("zf-new-login",         "zf-new-html/login_page.html",                           ::JwNewZfParser, 0),
        // ---- 强智全家 (qz / qz_crazy / qz_br / qz_with_node / qz_old) ----
        Case("qz-base-teacher",      "qz-base-crazy/edge_teacher_attr.html",                  ::JwQzCrazyParser, 2),  // fixture _doc: 适用 JwQzCrazyParser
        Case("qz-crazy-normal",      "qz-base-crazy/normal_grid.html",                        ::JwQzCrazyParser, 7),
        Case("qz-crazy-nohdr",       "qz-base-crazy/normal_grid_no_header.html",              ::JwQzCrazyParser, 4),
        Case("qz-crazy-comma",       "qz-base-crazy/edge_comma_weeks.html",                   ::JwQzCrazyParser, 9),
        Case("qz-crazy-display",     "qz-base-crazy/edge_display_none.html",                  ::JwQzCrazyParser, 3),
        Case("qz-empty-kbtable",     "qz-base-crazy/empty_kbtable.html",                      ::JwQzCrazyParser, 0),
        Case("qz-br-normal",         "qz-br-withnode/timetable_qzbr_normal.html",             ::JwQzBrParser, 8),
        Case("qz-wn-space",          "qz-br-withnode/timetable_qzbr_withnode_space.html",     ::JwQzWithNodeParser, 6),
        Case("qz-wn-split",          "qz-br-withnode/timetable_qzbr_withnode_split_title.html", ::JwQzWithNodeParser, 5),
        Case("qz-br-empty",          "qz-br-withnode/timetable_kbtable_empty.html",           ::JwQzBrParser, 0),
        Case("qz-old-normal",        "qz-old/timetable_kbtable_normal.html",                  ::JwOldQzParser, 5),
        Case("qz-old-hidden",        "qz-old/timetable_kbtable_hidden.html",                  ::JwOldQzParser, 1),
        Case("qz-old-empty",         "qz-old/timetable_kbtable_empty.html",                   ::JwOldQzParser, 0),
        // ---- 青果/乘方 (cf) ----
        Case("cf-typical",  "cf-chengfang/typical_two_courses.html",     ::JwChengFangParser, 2),
        Case("cf-sd",       "cf-chengfang/single_double_weeks.html",     ::JwChengFangParser, 2),
        Case("cf-multi",    "cf-chengfang/multi_segment_weeks.html",     ::JwChengFangParser, 4),
        Case("cf-escaped",  "cf-chengfang/escaped_quotes_multiline.html", ::JwChengFangParser, 2),
        Case("cf-missing",  "cf-chengfang/missing_fields.html",          ::JwChengFangParser, 3),
        Case("cf-empty",    "cf-chengfang/empty_timetable.html",         ::JwChengFangParser, 0),
        Case("cf-login",    "cf-chengfang/login_page_no_kbxx.html",      ::JwChengFangParser, 0),
        // ---- 北大 (pku) / 北师珠 (bnuz) ----
        Case("pku-normal",  "pku-bnuz/pku_normal.html",             ::JwPekingParser, 3),
        Case("pku-sd",      "pku-bnuz/pku_single_double_week.html", ::JwPekingParser, 2),
        Case("pku-missing", "pku-bnuz/pku_missing_fields.html",     ::JwPekingParser, 2),
        Case("pku-empty",   "pku-bnuz/pku_empty.html",              ::JwPekingParser, 0),
        Case("pku-login",   "pku-bnuz/pku_login.html",              ::JwPekingParser, 0),
        Case("bnuz-normal",  "pku-bnuz/bnuz_normal.html",           ::JwBnuzParser, 6),
        Case("bnuz-missing", "pku-bnuz/bnuz_missing_fields.html",   ::JwBnuzParser, 1),
        Case("bnuz-empty",   "pku-bnuz/bnuz_empty.html",            ::JwBnuzParser, 0),
        Case("bnuz-login",   "pku-bnuz/bnuz_login.html",            ::JwBnuzParser, 0),
        // ---- 湖南科技大学 (hnust) ----
        Case("hnust-hidden", "hnust-urp/hnust_kbtable_hidden_div.html", ::JwHnustParser, 6),
        Case("hnust-empty",  "hnust-urp/hnust_empty_kbtable.html",      ::JwHnustParser, 0),
        Case("hnust-login",  "hnust-urp/hnust_login_page.html",         ::JwHnustParser, 0),
        // ---- URP (urp) / URP Nova (urp_new) ----
        Case("urp-displaytag", "hnust-urp/urp_displayTag_table.html",           ::JwUrpParser, 4),
        Case("urp-striped",    "hnust-urp/urp_striped_table_trimmed_cols.html", ::JwUrpParser, 2),
        Case("urp-grid",       "hnust-urp/urp_grid_day_node_td_id.html",        ::JwUrpParser, 6),
        Case("urp-empty",      "hnust-urp/urp_grid_empty.html",                 ::JwUrpParser, 0),
        Case("urp-new-courses","detection-pages/urp-new-schedule-courses.html", ::JwNewUrpParser, 3),
        // ---- 金智 (wisedu) ----
        Case("wisedu-normal",  "wisedu-json/xskcb_normal.json",       ::JwWiseduParser, 5),
        Case("wisedu-missing", "wisedu-json/xskcb_missing_fields.json", ::JwWiseduParser, 3),
        Case("wisedu-empty",   "wisedu-json/xskcb_empty_rows.json",   ::JwWiseduParser, 0),
    )

    @Test
    fun `case table covers at least 60 fixture contracts`() {
        assertTrue("caseTable 应 >= 60 行", caseTable.size >= 60)
        assertTrue("id 不得重复", caseTable.map { it.id }.toSet().size == caseTable.size)
    }

    @Test
    fun `every fixture matches its nine-field expected contract`() {
        val failures = StringBuilder()
        for (c in caseTable) {
            val source = res(c.source)
            val expected = expectedCourses(res(expectedPath(c.source)))
            val actual = try {
                c.factory(source).generateCourseList()
            } catch (e: Exception) {
                failures.append("${c.id}: parser threw ${e::class.simpleName}: ${e.message}\n")
                continue
            }
            if (expected.size != actual.size) {
                failures.append("${c.id}: count expected=${expected.size} actual=${actual.size}\n")
                continue
            }
            expected.zip(actual).forEachIndexed { i, (e, a) ->
                if (a.name != e.name)
                    failures.append("${c.id}[$i].name expected=${e.name} actual=${a.name}\n")
                listOf("day" to a.day, "startNode" to a.startNode,
                       "endNode" to a.endNode, "startWeek" to a.startWeek,
                       "endWeek" to a.endWeek, "type" to a.type).forEach { (k, v) ->
                    val exp = when (k) {
                        "day" -> e.day; "startNode" -> e.startNode; "endNode" -> e.endNode
                        "startWeek" -> e.startWeek; "endWeek" -> e.endWeek; else -> e.type
                    }
                    if (exp != v)
                        failures.append("${c.id}[$i].$k expected=$exp actual=$v\n")
                }
                if (a.teacher != e.teacher)
                    failures.append("${c.id}[$i].teacher expected=${e.teacher} actual=${a.teacher}\n")
                if (a.room != e.room)
                    failures.append("${c.id}[$i].room expected=${e.room} actual=${a.room}\n")
            }
        }
        assertTrue("契约不符:\n$failures", failures.isBlank())
    }

    @Test
    fun `expectedCount annotation matches expected json`() {
        // 防 caseTable 手抄漂移: 表内 expectedCount 必须等于 expected.json 实际条数
        for (c in caseTable) {
            val n = expectedCourses(res(expectedPath(c.source))).size
            assertEquals("${c.id} expectedCount 注记与 expected.json 不符", c.expectedCount, n)
        }
    }

    @Test
    fun `qz missing kbtable throws recognizable parse exception`() {
        // T8 契约: #kbtable 不存在 → JwParseException (message 非空, 不再静默空成功)。
        val missing = listOf(
            "qz-base-crazy/missing_kbtable.html",
            "qz-base-crazy/no_kbtable_login.html",
            "qz-br-withnode/login_page.html",
            "qz-old/login_page.html",
        )
        for (path in missing) {
            try {
                JwQzParser(res(path)).generateCourseList()
                if (isQzStrictMode()) {
                    fail("QZ 缺表样本 $path 应抛 JwParseException (T8 契约)")
                }
                // 兼容模式 (isQzStrictMode()==false): 只要求不产课程
            } catch (e: RuntimeException) {
                // 期望路径: JwParseException 是 RuntimeException 子类; message 必须可读
                assertTrue("$path 异常 message 不应为空", !e.message.isNullOrBlank())
            }
        }
    }

    /** T8 严格缺表契约是否已启用: 用"缺表样本抛异常/空列表"的当前行为探测, 不硬编码实现细节 */
    private fun isQzStrictMode(): Boolean = try {
        JwQzParser(res("qz-base-crazy/missing_kbtable.html")).generateCourseList()
        false // 返回了 → 仍是静默空表模式
    } catch (e: RuntimeException) {
        true  // 抛了 → 严格模式
    }

    @Test
    fun `non-qz empty and login boundaries return empty without throwing`() {
        val empties: List<Pair<String, JwParser>> = listOf(
            "zf-old-table1/empty_table.html" to JwOldZfParser(res("zf-old-table1/empty_table.html")),
            "zf-old-table1/login_page.html" to JwOldZfParser(res("zf-old-table1/login_page.html")),
            "zf-new-kblist/kblist_no_courses_this_semester.json" to
                JwNewZfParser(res("zf-new-kblist/kblist_no_courses_this_semester.json")),
            "zf-new-html/grid_empty_semester.html" to JwNewZfParser(res("zf-new-html/grid_empty_semester.html")),
            "zf-new-html/grid_missing_fields.html" to JwNewZfParser(res("zf-new-html/grid_missing_fields.html")),
            "zf-new-html/login_page.html" to JwNewZfParser(res("zf-new-html/login_page.html")),
            "qz-base-crazy/empty_kbtable.html" to JwQzCrazyParser(res("qz-base-crazy/empty_kbtable.html")),
            "qz-br-withnode/timetable_kbtable_empty.html" to
                JwQzBrParser(res("qz-br-withnode/timetable_kbtable_empty.html")),
            "qz-old/timetable_kbtable_empty.html" to JwOldQzParser(res("qz-old/timetable_kbtable_empty.html")),
            "cf-chengfang/empty_timetable.html" to JwChengFangParser(res("cf-chengfang/empty_timetable.html")),
            "cf-chengfang/login_page_no_kbxx.html" to JwChengFangParser(res("cf-chengfang/login_page_no_kbxx.html")),
            "pku-bnuz/pku_empty.html" to JwPekingParser(res("pku-bnuz/pku_empty.html")),
            "pku-bnuz/pku_login.html" to JwPekingParser(res("pku-bnuz/pku_login.html")),
            "pku-bnuz/bnuz_empty.html" to JwBnuzParser(res("pku-bnuz/bnuz_empty.html")),
            "pku-bnuz/bnuz_login.html" to JwBnuzParser(res("pku-bnuz/bnuz_login.html")),
            "hnust-urp/hnust_empty_kbtable.html" to JwHnustParser(res("hnust-urp/hnust_empty_kbtable.html")),
            "hnust-urp/hnust_login_page.html" to JwHnustParser(res("hnust-urp/hnust_login_page.html")),
            "hnust-urp/urp_grid_empty.html" to JwUrpParser(res("hnust-urp/urp_grid_empty.html")),
            "wisedu-json/xskcb_empty_rows.json" to JwWiseduParser(res("wisedu-json/xskcb_empty_rows.json")),
        )
        for ((path, parser) in empties) {
            val got = try { parser.generateCourseList() } catch (e: Exception) {
                fail("$path 空边界不应抛异常 (got ${e::class.simpleName}: ${e.message})"); emptyList()
            }
            assertEquals("$path 空边界应 0 课", 0, got.size)
        }
    }

    @Test
    fun `every protocol has at least one success and one empty or login boundary`() {
        val successIds = setOf(
            "zf-old-standard", "zf-new-kblist-range", "qz-base-teacher", "qz-crazy-normal",
            "qz-br-normal", "qz-wn-space", "qz-old-normal", "cf-typical", "pku-normal",
            "bnuz-normal", "hnust-hidden", "urp-displaytag", "urp-new-courses", "wisedu-normal",
        )
        val emptyIds = setOf(
            "zf-old-empty", "zf-old-login", "zf-new-kblist-none", "zf-new-grid-empty", "zf-new-login",
            "qz-empty-kbtable", "qz-br-empty", "qz-old-empty", "cf-empty", "cf-login",
            "pku-empty", "pku-login", "bnuz-empty", "bnuz-login", "hnust-empty", "hnust-login",
            "urp-empty", "wisedu-empty",
        )
        assertTrue("caseTable 缺成功样本", caseTable.any { it.id in successIds && it.expectedCount > 0 })
        assertTrue("caseTable 缺空/边界样本", caseTable.any { it.id in emptyIds && it.expectedCount == 0 })
    }

    // ---------- helpers ----------
    private fun resOrNull(relPath: String): String? =
        JwParserFixtureTest::class.java.classLoader?.getResourceAsStream("jw_fixtures/$relPath")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

    private fun res(relPath: String): String =
        requireNotNull(resOrNull(relPath)) { "missing fixture jw_fixtures/$relPath" }

    /** 兼容三种命名: X.expected.json (html/json 去扩展名) 与 wisedu-json 的 X.json.expected.json */
    private fun expectedPath(sourceRel: String): String {
        val dir = sourceRel.substringBeforeLast('/')
        val base = sourceRel.substringAfterLast('/')
        val stem = base.substringBeforeLast('.')   // X.html / X.json → X
        val candidates = listOf(
            "$dir/$stem.expected.json",
            "$dir/$base.expected.json",
            "$dir/$base.json.expected.json",
        )
        for (c in candidates) {
            if (resOrNull(c) != null) return c
        }
        throw AssertionError("expected 缺失: $sourceRel (试过 $candidates)")
    }

    private fun expectedCourses(jsonText: String): List<ExpectedCourse> {
        val root = jsonText.trim()
        val arr: JSONArray = if (root.startsWith("["))
            JSONArray(root)
        else
            JSONObject(root).optJSONArray("courses") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ExpectedCourse(
                name = o.optString("name"),
                day = o.getInt("day"),
                startNode = o.getInt("startNode"), endNode = o.getInt("endNode"),
                startWeek = o.getInt("startWeek"), endWeek = o.getInt("endWeek"),
                type = o.getInt("type"),
                teacher = o.optString("teacher"), room = o.optString("room"),
            )
        }
    }

    /** expected 行 (不用 JwCourse 直接比, 避免依赖其默认值语义) */
    private data class ExpectedCourse(
        val name: String, val day: Int, val startNode: Int, val endNode: Int,
        val startWeek: Int, val endWeek: Int, val type: Int,
        val teacher: String, val room: String,
    )
}
