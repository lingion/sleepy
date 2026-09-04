package com.lingion.sleepy.data.jw

/**
 * 武汉理工大学 (WHUT) — 金智 wisedu jwapp 变体解析器 (2026-09-05 调研落地)。
 *
 * WHUT 与 HEU 同属金智 jwapp 家族, 行字段 (KCM/SKJS/JASMC/SKXQ/KSJC/JSJC/SKZC)
 * 一一对应, 解析内核完全复用 [JwWiseduParser] (SKZC bitmap → weekRuns 单双周
 * 压缩)。两处 WHUT 特有差异:
 *
 * 1. **取数微应用不同**: 课表在 kcbcxby 微应用 (`cxxskcb.do`, 响应
 *    `datas.cxxskcb.rows[]`), 不是 HEU 的 wdkb (`xskcb.do`)。moduleNames
 *    同时认两条路径, HEU 数据喂进来也照常解析 (兼容回归测试钉死)。
 * 2. **节次 DM ≠ 物理节次**: WHUT 大节 DM 序列 1..16 中 6/7/13 缺位
 *    (中课/晚课大节), DM 8→物理6, 9→7, 14→11 …。不映射则下午/晚上课全部
 *    错位。表外 DM (教务改版新增) 原值直通, 禁丢行。实机上取数 JS 先拉
 *    jcjcx.do 动态映射, 此表兜底。
 *
 * 上游协议形态参考: XingHeYuZhuan/shiguang_warehouse (MIT) resources/WHUT/whut_01.js
 * 与 TokenTeam/iwut (AGPL-3.0, 仅引协议形态不抄码)。代码自写, 节次映射表
 * 为事实性数据。
 *
 * 取数 JS (登录态 WebView fetch): 见 JwWebViewLoginScreen WHUT_FETCH_JS。
 */
class JwWhutParser(source: String) : JwWiseduParser(source) {

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

    override val moduleNames: List<String> = listOf("cxxskcb", "xskcb")

    override fun mapSection(dm: Int): Int = mapSectionDm(dm)

    override fun confidence(): Int = when {
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
            if (source.contains("cxxskcb.do")) add("kcbcxby/cxxskcb.do")
            if (source.contains("cxxskcb")) add("datas.cxxskcb.rows")
        }
}
