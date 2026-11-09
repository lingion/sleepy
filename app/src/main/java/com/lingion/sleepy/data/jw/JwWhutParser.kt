package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.json.JSONObject

/**
 * 武汉理工大学 (WHUT) — 金智 wisedu jwapp 变体解析器 (2026-09-05 调研落地)。
 *
 * WHUT 与 HEU 同属金智 jwapp 家族, 行字段 (KCM/SKJS/JASMC/SKXQ/KSJC/JSJC/SKZC)
 * 一一对应, 解析内核完全复用 [JwWiseduParser] (SKZC bitmap → weekRuns 单双周
 * 压缩)。两处 WHUT 特有差异:
 *
 * 1. **取数微应用不同**: 学生课表主通道是 wdkbby 微应用 (`cxxszhxqkb.do`,
 *    响应 `datas.cxxszhxqkb.rows[]`, 2026-09-05 用户采集包实锤 — 学生
 *    "我的课表"页面走它; 旧认知 kcbcxby/cxxskcb.do 实为教室课表(教师端),
 *    学生账号常 403/空, 降级为兜底通道)。moduleNames 按优先级认三条路径,
 *    HEU 数据喂进来也照常解析 (兼容回归测试钉死)。
 * 2. **节次 DM ≠ 物理节次**: WHUT 大节 DM 序列 1..16 中 6/7/13 缺位
 *    (中课/晚课大节), DM 8→物理6, 9→7, 14→11 …。不映射则下午/晚上课全部
 *    错位。表外 DM (教务改版新增) 原值直通, 禁丢行。取数 JS 透传
 *    jcjcx.do 动态映射和原始 DM，由本解析器唯一转换，此表兜底。
 *
 * 上游协议形态参考: XingHeYuZhuan/shiguang_warehouse (MIT) resources/WHUT/whut_01.js
 * 与 TokenTeam/iwut (AGPL-3.0, 仅引协议形态不抄码)。代码自写, 节次映射表
 * 为事实性数据。
 *
 * 取数 JS (登录态 WebView fetch): 见 JwWebViewLoginScreen WHUT_FETCH_JS。
 */
class JwWhutParser(source: String) : JwWiseduParser(source) {

    private val legacyTable by lazy { Jsoup.parse(source).selectFirst("#xqkb .table-class-even") }
    private val dynamicSections by lazy {
        runCatching {
            JSONObject(source).optJSONObject("sleepyWhutSectionMap")?.let { values ->
                values.keys().asSequence().mapNotNull { key ->
                    val dm = key.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    val node = values.opt(key)?.toString()?.toIntOrNull()
                        ?.takeIf { it in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND } ?: return@mapNotNull null
                    dm to node
                }.toMap()
            }.orEmpty()
        }.getOrDefault(emptyMap())
    }

    override fun generateCourseList(): List<JwCourse> {
        val table = legacyTable ?: return super.generateCourseList()
        return buildList {
            for (row in table.select("tr").filter { it.closest("table") == table }) {
                val cells = row.children().filter { it.tagName() == "td" }.dropWhile { !it.hasAttr("style") }
                for ((index, cell) in cells.withIndex()) {
                    val day = index + 1
                    if (day !in 1..7) continue
                    for (item in cell.select("a")) {
                        val name = item.ownText().trim()
                        val children = item.children()
                        if (name.isEmpty() || children.size < 2) continue
                        val time = children[1].text().trim().replace('（', '(').replace('）', ')')
                        val match = Regex("""(.+)\(([^()]*)节\)""").matchEntire(time) ?: continue
                        val weeks = schoolWeekRanges(match.groupValues[1])
                        // HTML already contains physical periods. Only the JSON path uses DM mapping.
                        for ((first, last) in schoolNodeRanges(match.groupValues[2])) for ((from, to, type) in weeks) {
                            add(JwCourse(name, children[0].text().trim().removePrefix("@"), "", day,
                                first, last, from, to, type))
                        }
                    }
                }
            }
        }.distinct()
    }

    companion object {
        /**
         * WHUT 节次 DM → 物理节次 (1..13)。
         * DM 序列 = 1,2,3,4,5, 8,9,10,11,12, 14,15,16 (6/7/13 缺位)。
         * 来源: shiguang_warehouse whut_01.js fallback 表 (MIT)。
         */
        val SECTION_DM_TO_NODE: Map<Int, Int> = mapOf(
            1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5,
            8 to 6, 9 to 7, 10 to 8, 11 to 9, 12 to 10,
            14 to 11, 15 to 12, 16 to 13
        )

        fun mapSectionDm(dm: Int): Int = SECTION_DM_TO_NODE[dm] ?: dm
    }

    override val moduleNames: List<String> = listOf("cxxszhxqkb", "cxxskcb", "xskcb")

    override fun mapSection(dm: Int): Int = dynamicSections[dm] ?: mapSectionDm(dm)

    override fun confidence(): Int = when {
        legacyTable != null -> 100
        // 2026-09 采集包实锤: 学生课表主通道 wdkbby/cxxszhxqkb.do
        source.contains("cxxszhxqkb") -> 96
        source.contains("cxxskcb.do") -> 95
        // 微应用名 cxxskcb (JSON key 与 URL 段都含), 兼容 pretty-print 响应
        source.contains("cxxskcb") -> 90
        // cxxskcb 与 xskcb 锚点可能同现 (取数 JS 带两条路径痕迹), WHUT 规则优先
        source.contains("xskcb.do") -> 90
        source.contains("datas.xskcb") -> 80
        source.contains("/jwapp/sys/wdkb/") -> 100
        else -> 0
    }

    override fun matchedFeatures(): List<String> =
        super.matchedFeatures() + buildList {
            if (legacyTable != null) add("xqkb/table-class-even")
            if (source.contains("cxxszhxqkb")) add("wdkbby/cxxszhxqkb.do")
            if (source.contains("cxxskcb.do")) add("kcbcxby/cxxskcb.do")
            if (source.contains("cxxskcb")) add("datas.cxxskcb.rows")
        }
}
