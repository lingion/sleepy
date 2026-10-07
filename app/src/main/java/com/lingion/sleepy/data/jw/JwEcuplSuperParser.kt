// ECUPL Super fallback dispatcher — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * 华东政法大学 (ECUPL) Super 解析器 — 3-parser 调度器。
 *
 * 调度策略 (WakeUp ECUPLSuperParser.smali 反编译产物):
 *  1. 主路径: [JwEcuplParser] (双 .listTable + 5 列模糊匹配)
 *  2. Script 路径: [JwEcuplScriptParser] (R5/R6/R7 script 子协议)
 *  3. Super fallback: 两个 parser 各自独立尝试, 合并去重结果
 *
 * 上游协议形态 (5 仓致谢):
 *  - LonelyMarch/OpenWakeUp EcuplSuperParser (AGPL-3.0, 主参考)
 *  - YZune/WakeUpSchedule ECUPLSuperParser.smali (Apache-2.0)
 *  - ershiyidian/WakeUp_SHU smali dump (INDIRECT)
 *  - zimo0o0omiz/wisedu-unified-login-api (MIT, INDIRECT)
 *  - only9464/HEU-Wisedu (MIT, INDIRECT)
 *  - CreamPig233/neu_wisedu2wakeup (无 LICENSE, INDIRECT)
 */
class JwEcuplSuperParser(source: String) : JwParser(source) {

    private val mainParserImpl = JwEcuplParser(source)
    private val scriptParserImpl = JwEcuplScriptParser(source)

    /**
     * Super 调度:
     *  - 主 parser 优先 (HTML 双表)
     *  - Script parser 补齐 (script 子协议, 教务通知页路径)
     *  - 合并去重: 同 name+teacher+startNode 视为重复
     */
    override fun generateCourseList(): List<JwCourse> {
        val mainCourses = runCatching { mainParserImpl.generateCourseList() }.getOrDefault(emptyList())
        val scriptCourses = runCatching { scriptParserImpl.generateCourseList() }.getOrDefault(emptyList())

        val merged = mutableListOf<JwCourse>()
        val seen = mutableSetOf<String>()

        // 主 parser 优先 (HTML 是首校路径)
        for (c in mainCourses) {
            val key = "${c.name}|${c.teacher}|${c.startNode}"
            if (seen.add(key)) merged += c
        }
        // Script parser 补齐 (通知页路径)
        for (c in scriptCourses) {
            val key = "${c.name}|${c.teacher}|${c.startNode}"
            if (seen.add(key)) merged += c
        }
        return merged
    }

    override fun confidence(): Int {
        val mainConf = mainParserImpl.confidence()
        val scriptConf = scriptParserImpl.confidence()
        // 取 max (双 parser 联合判别)
        return maxOf(mainConf, scriptConf)
    }

    override fun matchedFeatures(): List<String> = buildList {
        val mainFeats = runCatching { mainParserImpl.matchedFeatures() }.getOrDefault(emptyList())
        val scriptFeats = runCatching { scriptParserImpl.matchedFeatures() }.getOrDefault(emptyList())
        addAll(mainFeats)
        addAll(scriptFeats)
        if (isEmpty()) add("guard:no-ecupl-super-markers")
    }

    companion object {
        /** 复用 main parser 的 [JwEcuplParser.RE_LIST_TABLE] (cached) */
        val HAS_LIST_TABLE: (String) -> Boolean = { html ->
            JwEcuplParser.RE_LIST_TABLE.containsMatchIn(html)
        }

        /** 复用 script parser 的 [JwEcuplScriptParser.RE_NEW_ACTIVITY] (cached) */
        val HAS_NEW_ACTIVITY: (String) -> Boolean = { html ->
            JwEcuplScriptParser.RE_NEW_ACTIVITY.containsMatchIn(html)
        }
    }
}