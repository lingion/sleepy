// ECUPL Super fallback dispatcher — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import java.util.concurrent.CancellationException

/**
 * ECUPL fallback order verified against the 6.6.2 ECUPLSuperParser DEX.
 * Keep the first nonempty annual TaskActivity result. Use the time-script branch
 * only when that branch has no courses; the two representations are not additive.
 */
class JwEcuplSuperParser internal constructor(
    source: String,
    private val mainParserImpl: JwParser,
    private val scriptParserImpl: JwParser,
) : JwParser(source) {
    constructor(source: String) : this(source, JwEcuplParser(source), JwEcuplScriptParser(source))

    override fun generateCourseList(): List<JwCourse> {
        val errors = mutableListOf<Pair<String, Exception>>()
        for ((name, parser) in listOf("ECUPLParser" to mainParserImpl, "ECUPLScriptParser" to scriptParserImpl)) {
            try {
                val courses = parser.generateCourseList()
                if (courses.isNotEmpty()) return courses
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errors += name to error
            }
        }
        if (errors.isNotEmpty()) throw IllegalStateException(errors.joinToString("\n") { (name, error) ->
            "$name: ${error.message.orEmpty()}"
        }).apply { errors.forEach { (_, error) -> addSuppressed(error) } }
        return emptyList()
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
