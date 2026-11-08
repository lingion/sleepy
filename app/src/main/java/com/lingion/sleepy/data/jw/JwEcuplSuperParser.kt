// ECUPL Super fallback dispatcher — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * ECUPL fallback order verified against the 6.6.2 ECUPLSuperParser DEX.
 * Keep the first nonempty annual TaskActivity result. Use the time-script branch
 * only when that branch has no courses; the two representations are not additive.
 */
class JwEcuplSuperParser(source: String) : JwParser(source) {

    private val mainParserImpl = JwEcuplParser(source)
    private val scriptParserImpl = JwEcuplScriptParser(source)

    override fun generateCourseList(): List<JwCourse> {
        val mainCourses = runCatching { mainParserImpl.generateCourseList() }.getOrDefault(emptyList())
        if (mainCourses.isNotEmpty()) return mainCourses
        return runCatching { scriptParserImpl.generateCourseList() }.getOrDefault(emptyList())
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
