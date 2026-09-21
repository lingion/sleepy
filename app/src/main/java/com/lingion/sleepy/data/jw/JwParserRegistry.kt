package com.lingion.sleepy.data.jw

/**
 * T8 — 统一解析分发与兜底裁决。
 *
 * 单一来源：协议 type → parser 工厂；type 为空时跑全部候选按 confidence/课程数裁决。
 * ParserAttempt 快照供 T9 诊断（JwParseDiagnostics.classify）消费。
 */
object JwParserRegistry {
    data class ParserAttempt(
        val parserName: String,
        val type: String?,
        val courseCount: Int,
        val confidence: Int,
        val matchedFeatures: List<String>,
        val exception: String?,
    )

    private val TYPE_PRIORITY: Map<String, Int> = linkedMapOf(
        JwProtocol.TYPE_KINGO_NEW to 8,
        JwProtocol.TYPE_JZ to 9,
        JwProtocol.TYPE_SOUTH_SOFT to 10,
        JwProtocol.TYPE_CHAOXING_LEGACY to 11,
        JwProtocol.TYPE_SHUWEI to 12,
        JwProtocol.TYPE_SUDA_POST to 13,
        JwProtocol.TYPE_CUMTB to 14,
        JwProtocol.TYPE_XJU_POST to 15,
        JwProtocol.TYPE_WISEDU to 20,
        JwProtocol.TYPE_CQU to 25,
        JwProtocol.TYPE_CHAOXING to 26,
        JwProtocol.TYPE_BOYA_PP to 27,
        JwProtocol.TYPE_EAMS5 to 28,
        JwProtocol.TYPE_CLASSIC_EAMS to 29,
        JwProtocol.TYPE_NUIT to 30,
        JwProtocol.TYPE_KUST to 31,
        JwProtocol.TYPE_PKU to 30,
        JwProtocol.TYPE_SEU to 32,
        JwProtocol.TYPE_ZJU to 33,
        JwProtocol.TYPE_USTC to 34,
        JwProtocol.TYPE_SCU to 35,
        JwProtocol.TYPE_NEU to 36,
        JwProtocol.TYPE_BJTU to 37,
        JwProtocol.TYPE_YETHAN to 38,
        JwProtocol.TYPE_BNUZ to 40,
        JwProtocol.TYPE_CF_NEW to 45,
        JwProtocol.TYPE_CF to 50,
        JwProtocol.TYPE_HNUST to 60,
        JwProtocol.TYPE_HNIU to 65,
        JwProtocol.TYPE_ZF to 70,
        JwProtocol.TYPE_ZF_1 to 75,
        JwProtocol.TYPE_URP to 80,
        JwProtocol.TYPE_URP_NEW to 85,
        JwProtocol.TYPE_ZF_NEW to 90,
        JwProtocol.TYPE_QZ to 100,
        JwProtocol.TYPE_QZ_CRAZY to 110,
        JwProtocol.TYPE_QZ_BR to 120,
        JwProtocol.TYPE_QZ_WITH_NODE to 130,
        JwProtocol.TYPE_QZ_IEAS to 140,
        JwProtocol.TYPE_QZ_APP to 141,
        JwProtocol.TYPE_UCAS to 142,
        JwProtocol.TYPE_QZ_OLD to 145,
    )

    private val FACTORIES: Map<String, (String) -> JwParser> = linkedMapOf(
        JwProtocol.TYPE_KINGO_NEW to ::JwKingoParser,
        JwProtocol.TYPE_JZ to ::JwJzParser,
        JwProtocol.TYPE_SOUTH_SOFT to ::JwSouthSoftParser,
        JwProtocol.TYPE_CHAOXING_LEGACY to ::JwChaoxingLegacyParser,
        JwProtocol.TYPE_SHUWEI to ::JwShuweiParser,
        JwProtocol.TYPE_SUDA_POST to ::JwSudaParser,
        JwProtocol.TYPE_CUMTB to ::JwCumtbParser,
        JwProtocol.TYPE_XJU_POST to ::JwXjuParser,
        JwProtocol.TYPE_WISEDU to ::JwWiseduParser,
        JwProtocol.TYPE_WHUT to ::JwWhutParser,
        JwProtocol.TYPE_CQU to ::JwCquParser,
        JwProtocol.TYPE_CHAOXING to ::JwChaoxingParser,
        JwProtocol.TYPE_BOYA_PP to ::JwBoyaPpParser,
        JwProtocol.TYPE_EAMS5 to ::JwEams5Parser,
        JwProtocol.TYPE_CLASSIC_EAMS to ::JwClassicEamsParser,
        JwProtocol.TYPE_NUIT to ::JwNuitParser,
        JwProtocol.TYPE_KUST to ::JwKustParser,
        JwProtocol.TYPE_SEU to ::JwSeuParser,
        JwProtocol.TYPE_ZJU to ::JwZjuParser,
        JwProtocol.TYPE_USTC to ::JwUstcParser,
        JwProtocol.TYPE_SCU to ::JwScuParser,
        JwProtocol.TYPE_NEU to ::JwNeuParser,
        JwProtocol.TYPE_BJTU to ::JwBjtuParser,
        JwProtocol.TYPE_YETHAN to ::JwYethanParser,
        JwProtocol.TYPE_URP_NEW to ::JwNewUrpParser,
        JwProtocol.TYPE_ZF_NEW to ::JwNewZfParser,
        JwProtocol.TYPE_ZF to { html -> JwOldZfParser(html, 0) },
        JwProtocol.TYPE_ZF_1 to { html -> JwOldZfParser(html, 1) },
        JwProtocol.TYPE_URP to ::JwUrpParser,
        JwProtocol.TYPE_QZ to ::JwQzParser,
        JwProtocol.TYPE_QZ_CRAZY to ::JwQzCrazyParser,
        JwProtocol.TYPE_QZ_BR to ::JwQzBrParser,
        JwProtocol.TYPE_QZ_WITH_NODE to ::JwQzWithNodeParser,
        JwProtocol.TYPE_QZ_OLD to ::JwOldQzParser,
        JwProtocol.TYPE_QZ_APP to ::JwQzAppParser,
        JwProtocol.TYPE_QZ_IEAS to { html -> JwQzIeasParser(html) },
        JwProtocol.TYPE_UCAS to ::JwUcasParser,
        JwProtocol.TYPE_CF_NEW to ::JwCfNewParser,
        JwProtocol.TYPE_CF to ::JwChengFangParser,
        JwProtocol.TYPE_PKU to ::JwPekingParser,
        JwProtocol.TYPE_BNUZ to ::JwBnuzParser,
        JwProtocol.TYPE_HNUST to { html: String -> JwHnustParser(html) },
        JwProtocol.TYPE_HNIU to { html: String -> JwHniuparser(html) },
    )

    fun allCandidates(html: String): List<Pair<String?, JwParser>> =
        TYPE_PRIORITY.keys.mapNotNull { type -> FACTORIES[type]?.let { type to it(html) } }

    fun parserFor(type: String, html: String): JwParser =
        FACTORIES[type]?.invoke(html) ?: throw IllegalArgumentException("协议 $type 暂不支持")

    private data class Row(val type: String?, val attempt: ParserAttempt, val result: List<JwCourse>)

    fun selectBest(html: String, declaredType: String? = null): Pair<List<JwCourse>, List<ParserAttempt>> {
        val rows = allCandidates(html).map { (type, parser) ->
            val confidence = runCatching { parser.confidence() }.getOrDefault(0)
            val features = runCatching { parser.matchedFeatures() }.getOrDefault(emptyList())
            val parsed = runCatching { parser.generateCourseList() }
            val result = parsed.getOrDefault(emptyList())
            val exception = parsed.exceptionOrNull()?.let { error ->
                when {
                    error is JwParseException && error.attempts.firstOrNull()?.exception != null ->
                        error.attempts.first().exception
                    error is JwParseException && error.message?.contains("#kbtable") == true ->
                        "NO_TABLE_CONTAINER_MARKER"
                    else -> "${error::class.simpleName}: ${error.message?.take(60)}"
                }
            }
            Row(type, ParserAttempt(parser.nameForDiag(), type, result.size, confidence, features, exception), result)
        }
        val attempts = rows.map { it.attempt }
        val declared = rows.firstOrNull { it.type == declaredType }?.result.orEmpty()
        val best = if (declared.isNotEmpty()) declared else adjudicate(rows)
        return best to attempts
    }

    private fun adjudicate(rows: List<Row>): List<JwCourse> =
        rows.filter { it.result.isNotEmpty() && it.attempt.confidence >= 80 }
            .maxWithOrNull(compareBy<Row> { it.attempt.confidence }.thenBy { TYPE_PRIORITY[it.type] ?: 999 })?.result
            ?: rows.filter { it.result.isNotEmpty() }
                .maxWithOrNull(compareBy<Row> { it.result.size }.thenByDescending { it.attempt.confidence }.thenBy { TYPE_PRIORITY[it.type] ?: 999 })?.result
            ?: emptyList()
}

class JwParseException(message: String, val attempts: List<JwParserRegistry.ParserAttempt> = emptyList()) : RuntimeException(message)

internal fun JwParser.nameForDiag(): String = when (this) {
    is JwOldZfParser -> "${this::class.simpleName}(type=$type)"
    is JwHnustParser -> "${this::class.simpleName}(oldQzType=$oldQzType)"
    else -> this::class.simpleName ?: "JwParser"
}
