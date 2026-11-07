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
        JwProtocol.TYPE_UMOOC to 13,
        JwProtocol.TYPE_YL to 13,
        JwProtocol.TYPE_SUDA_POST to 13,
        JwProtocol.TYPE_CUMTB to 14,
        JwProtocol.TYPE_XJU_POST to 15,
        JwProtocol.TYPE_ECUPL to 16,
        JwProtocol.TYPE_ECUPL_SCRIPT to 17,
        JwProtocol.TYPE_ECUPL_SUPER to 18,
        JwProtocol.TYPE_NEW_URP_SUPER to 16,
        JwProtocol.TYPE_THU to 16,
        JwProtocol.TYPE_NWPU_POST to 16,
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
        JwProtocol.TYPE_KINGO_ZX to 205,
        JwProtocol.TYPE_SCAU to 200,
        JwProtocol.TYPE_SG_ASSET to 210,
    )

    private val FACTORIES: Map<String, (String) -> JwParser> = linkedMapOf(
        JwProtocol.TYPE_JMUCY to { html -> JwUndergradHtmlParser(html, JwProtocol.TYPE_JMUCY) },
        JwProtocol.TYPE_NUAAJC to { html -> JwUndergradHtmlParser(html, JwProtocol.TYPE_NUAAJC) },
        JwProtocol.TYPE_QMU to { html -> JwUndergradHtmlParser(html, JwProtocol.TYPE_QMU) },
        JwProtocol.TYPE_JAVTC to { html -> JwUndergradHtmlParser(html, JwProtocol.TYPE_JAVTC) },
        JwProtocol.TYPE_HITSZ to { html -> JwUndergradHtmlParser(html, JwProtocol.TYPE_HITSZ) },
        JwProtocol.TYPE_KINGO_NEW to ::JwKingoParser,
        JwProtocol.TYPE_KINGO_ZX to ::JwKingoZxParser,
        JwProtocol.TYPE_JZ to ::JwJzParser,
        JwProtocol.TYPE_SOUTH_SOFT to ::JwSouthSoftParser,
        JwProtocol.TYPE_CHAOXING_LEGACY to ::JwChaoxingLegacyParser,
        JwProtocol.TYPE_SHUWEI to ::JwShuweiParser,
        JwProtocol.TYPE_UMOOC to ::JwUmoocParser,
        JwProtocol.TYPE_YL to ::JwYlParser,
        JwProtocol.TYPE_SUDA_POST to ::JwSudaParser,
        JwProtocol.TYPE_CUMTB to ::JwCumtbParser,
        JwProtocol.TYPE_XJU_POST to ::JwXjuParser,
        JwProtocol.TYPE_ECUPL to ::JwEcuplParser,
        JwProtocol.TYPE_ECUPL_SCRIPT to ::JwEcuplScriptParser,
        JwProtocol.TYPE_ECUPL_SUPER to ::JwEcuplSuperParser,
        JwProtocol.TYPE_NEW_URP_SUPER to ::JwNewUrpSuperParser,
        JwProtocol.TYPE_THU to ::JwThuparser,
        JwProtocol.TYPE_NWPU_POST to ::JwNwpuPostParser,
        JwProtocol.TYPE_WISEDU to ::JwWiseduParser,
        JwProtocol.TYPE_WHUT to ::JwWhutParser,
        JwProtocol.TYPE_CQU to ::JwCquParser,
        JwProtocol.TYPE_CHAOXING to ::JwChaoxingParser,
        JwProtocol.TYPE_BOYA_PP to ::JwBoyaPpParser,
        JwProtocol.TYPE_EAMS5 to ::JwEams5Parser,
        JwProtocol.TYPE_CLASSIC_EAMS to { html: String -> JwClassicEamsParser(html) },
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
        JwProtocol.TYPE_URP_NEW_AJAX to ::JwNewUrpParser,
        JwProtocol.TYPE_ZF_NEW to ::JwNewZfParser,
        JwProtocol.TYPE_ZF to { html -> JwOldZfParser(html, 0) },
        JwProtocol.TYPE_ZF_1 to { html -> JwOldZfParser(html, 1) },
        JwProtocol.TYPE_ZF_CLS to ::JwZfClassParser,
        JwProtocol.TYPE_URP to ::JwUrpParser,
        JwProtocol.TYPE_QZ to ::JwQzParser,
        JwProtocol.TYPE_QZ_2017 to ::JwQzParser,
        JwProtocol.TYPE_QZ_2024 to ::JwQzParser,
        JwProtocol.TYPE_QZ_FSPT to ::JwQzFsptParser,
        JwProtocol.TYPE_QZ_USTB to ::JwQzUstbParser,
        JwProtocol.TYPE_QZ_CRAZY to ::JwQzCrazyParser,
        JwProtocol.TYPE_QZ_BR to ::JwQzBrParser,
        JwProtocol.TYPE_QZ_WITH_NODE to ::JwQzWithNodeParser,
        // These HTML layouts are indistinguishable without a declared school type.
        // Keep their row maps out of automatic candidates.
        JwProtocol.TYPE_QZ_SINGLE_NODE to { html -> JwQzSchoolParser(html, JwProtocol.TYPE_QZ_SINGLE_NODE) },
        JwProtocol.TYPE_QZ_NJUST to { html -> JwQzSchoolParser(html, JwProtocol.TYPE_QZ_NJUST) },
        JwProtocol.TYPE_QZ_WIT to { html -> JwQzSchoolParser(html, JwProtocol.TYPE_QZ_WIT) },
        JwProtocol.TYPE_QZ_ECUST to { html -> JwQzSchoolParser(html, JwProtocol.TYPE_QZ_ECUST) },
        JwProtocol.TYPE_QZ_AHUT to { html -> JwQzSchoolParser(html, JwProtocol.TYPE_QZ_AHUT) },
        JwProtocol.TYPE_QZ_BJFU to { html -> JwQzSchoolParser(html, JwProtocol.TYPE_QZ_BJFU) },
        JwProtocol.TYPE_SIMC to ::JwSimcParser,
        JwProtocol.TYPE_SHTU_POST_2024 to ::JwShtu2024Parser,
        JwProtocol.TYPE_RUC to ::JwRucParser,
        JwProtocol.TYPE_BFA to ::JwBfaParser,
        JwProtocol.TYPE_HUST to ::JwHustParser,
        JwProtocol.TYPE_AIC to ::JwAicParser,
        JwProtocol.TYPE_HBMZU to ::JwHbmzuParser,
        JwProtocol.TYPE_NUA to ::JwNuaParser,
        JwProtocol.TYPE_STUM to ::JwStumParser,
        JwProtocol.TYPE_AHMU to ::JwAhmuParser,
        JwProtocol.TYPE_GDEI to ::JwGdeiParser,
        JwProtocol.TYPE_JLU_POST to ::JwJluPostParser,
        JwProtocol.TYPE_CCIBE to ::JwCcibeParser,
        JwProtocol.TYPE_CPPU to ::JwCppuParser,
        JwProtocol.TYPE_SHCC to ::JwShccParser,
        JwProtocol.TYPE_FSTVC to ::JwFstvcParser,
        JwProtocol.TYPE_GDBH to ::JwGdbhParser,
        JwProtocol.TYPE_GLIET to ::JwGlietParser,
        JwProtocol.TYPE_CNU to ::JwCnuParser,
        JwProtocol.TYPE_XBELLSOFT to ::JwXbellsoftParser,
        JwProtocol.TYPE_CDUT to ::JwCdutParser,
        JwProtocol.TYPE_GZHUYJS to ::JwGzhuyjsParser,
        JwProtocol.TYPE_DHU to ::JwDhuParser,
        JwProtocol.TYPE_ECJTU to ::JwEcjtuParser,
        JwProtocol.TYPE_ZPTC to ::JwZptcParser,
        JwProtocol.TYPE_CTGU to ::JwCtguParser,
        JwProtocol.TYPE_NYIST to ::JwNyistParser,
        JwProtocol.TYPE_JXAU to ::JwJxauParser,
        JwProtocol.TYPE_FDU to ::JwFduParser,
        JwProtocol.TYPE_ZTVTIT to ::JwZtvtitParser,
        JwProtocol.TYPE_SDU to ::JwSduParser,
        JwProtocol.TYPE_SEIG to ::JwSeigParser,
        JwProtocol.TYPE_KINGOSOFT to ::JwKingosoftParser,
        JwProtocol.TYPE_CHAOXING_SHARE to ::JwChaoxingShareParser,
        JwProtocol.TYPE_QZ_OLD to ::JwOldQzParser,
        JwProtocol.TYPE_JLICT_QZ_OLD to { html -> JwOldQzSuperParser(html, JwOldQzSuperParser.Form.JLICT) },
        JwProtocol.TYPE_CCUT_QZ_OLD to { html -> JwOldQzSuperParser(html, JwOldQzSuperParser.Form.CCUT) },
        JwProtocol.TYPE_CCSU_QZ_OLD to { html -> JwOldQzSuperParser(html, JwOldQzSuperParser.Form.CCSU) },
        JwProtocol.TYPE_QZ_APP to ::JwQzAppParser,
        JwProtocol.TYPE_QZ_IEAS to { html -> JwQzIeasParser(html) },
        JwProtocol.TYPE_UCAS to ::JwUcasParser,
        JwProtocol.TYPE_CF_NEW to ::JwCfNewParser,
        JwProtocol.TYPE_CF to ::JwChengFangParser,
        JwProtocol.TYPE_PKU to ::JwPekingParser,
        JwProtocol.TYPE_BNUZ to ::JwBnuzParser,
        JwProtocol.TYPE_HNUST to { html: String -> JwHnustParser(html) },
        JwProtocol.TYPE_HNIU to { html: String -> JwHniuparser(html) },
        JwProtocol.TYPE_SUSTECH to { html -> JwUndergradSchoolParser(html, JwProtocol.TYPE_SUSTECH) },
        JwProtocol.TYPE_UIC to { html -> JwUndergradSchoolParser(html, JwProtocol.TYPE_UIC) },
        JwProtocol.TYPE_CITYU to { html -> JwUndergradSchoolParser(html, JwProtocol.TYPE_CITYU) },
        JwProtocol.TYPE_STU to { html -> JwUndergradSchoolParser(html, JwProtocol.TYPE_STU) },
        JwProtocol.TYPE_NFU to { html -> JwUndergradSchoolParser(html, JwProtocol.TYPE_NFU) },
        JwProtocol.TYPE_SCAU to ::JwScauParser,
        // sg_asset: source 不是 HTML 而是拾光脚本 saveImportedCourses 提交的课程 JSON
        JwProtocol.TYPE_SG_ASSET to ::JwSgAssetParser,
        // jw_home: 登记型 (URL 可信、无服务端协议指纹)。恒空 parser, 只为 supports() 放行
        // canImport 门; 刻意不进 TYPE_PRIORITY → 不进 run-all 候选, 零假阳性。
        JwProtocol.TYPE_JW_HOME to ::JwHomeParser,
    )

    fun allCandidates(html: String): List<Pair<String?, JwParser>> =
        TYPE_PRIORITY.keys.mapNotNull { type -> FACTORIES[type]?.let { type to it(html) } }

    fun parserFor(type: String, html: String): JwParser =
        FACTORIES[type]?.invoke(html) ?: throw IllegalArgumentException("协议 $type 暂不支持")

    /** Check registration without constructing a parser or interpreting a page. */
    fun supports(type: String?): Boolean = type != null && FACTORIES.containsKey(type)

    private data class Row(val type: String?, val attempt: ParserAttempt, val result: List<JwCourse>)

    fun selectBest(html: String, declaredType: String? = null): Pair<List<JwCourse>, List<ParserAttempt>> {
        val candidates = allCandidates(html).toMutableList()
        if (declaredType != null && candidates.none { it.first == declaredType }) {
            FACTORIES[declaredType]?.let { candidates += declaredType to it(html) }
        }
        val rows = candidates.map { (type, parser) ->
            val confidence = runCatching { parser.confidence() }.getOrDefault(0)
            val parsed = runCatching { parser.generateCourseList() }
            val features = runCatching { parser.matchedFeatures() }.getOrDefault(emptyList())
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
        // These declared variants are semantically different from indistinguishable generic pages.
        // An empty result cannot fall back to the generic row or week convention.
        if (declaredType in DECLARED_ONLY_TYPES || declaredType == JwProtocol.TYPE_UMOOC ||
            declaredType == JwProtocol.TYPE_CF_NEW && html.contains("\"requestedWeeks\"")) {
            return rows.firstOrNull { it.type == declaredType }?.result.orEmpty() to attempts
        }
        // A modern QZ table owns its titled fields. If those fields are incomplete,
        // a different family's positional fallback must not manufacture a schedule.
        val qzDocument = org.jsoup.Jsoup.parse(html)
        val modernQz = JwQzModernHtml.features(qzDocument).isNotEmpty() || qzDocument.select(
            "table#kbtable .kbcontent [title], table#kbtable .kbcontent1 [title], " +
                "table#timetable .kbcontent [title], table#timetable .kbcontent1 [title]"
        ).any { it.attr("title") in setOf("老师", "教师", "教室", "上课地点", "周次", "节次", "周次(节次)", "选课人数") }
        val eligible = if (modernQz) rows.filter { it.type in QZ_HTML_TYPES } else rows
        val declared = eligible.firstOrNull { it.type == declaredType }?.result.orEmpty()
        val best = if (declared.isNotEmpty()) declared else adjudicate(eligible)
        return best to attempts
    }

    private val QZ_HTML_TYPES = setOf(
        JwProtocol.TYPE_QZ_2017, JwProtocol.TYPE_QZ_2024, JwProtocol.TYPE_QZ_FSPT, JwProtocol.TYPE_QZ_USTB,
        JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_CRAZY, JwProtocol.TYPE_QZ_BR, JwProtocol.TYPE_QZ_WITH_NODE,
        JwProtocol.TYPE_QZ_SINGLE_NODE, JwProtocol.TYPE_QZ_NJUST, JwProtocol.TYPE_QZ_WIT,
        JwProtocol.TYPE_QZ_ECUST, JwProtocol.TYPE_QZ_AHUT, JwProtocol.TYPE_QZ_BJFU,
    )

    private val DECLARED_ONLY_TYPES = setOf(
        JwProtocol.TYPE_QZ_2017, JwProtocol.TYPE_QZ_2024, JwProtocol.TYPE_QZ_FSPT, JwProtocol.TYPE_QZ_USTB, JwProtocol.TYPE_ZF_CLS,
        JwProtocol.TYPE_JMUCY, JwProtocol.TYPE_NUAAJC, JwProtocol.TYPE_QMU, JwProtocol.TYPE_JAVTC, JwProtocol.TYPE_HITSZ,
        JwProtocol.TYPE_HNIU, JwProtocol.TYPE_SUSTECH, JwProtocol.TYPE_UIC, JwProtocol.TYPE_CITYU, JwProtocol.TYPE_STU, JwProtocol.TYPE_NFU,
        JwProtocol.TYPE_NYIST, JwProtocol.TYPE_JXAU,
        JwProtocol.TYPE_FSTVC, JwProtocol.TYPE_GDBH, JwProtocol.TYPE_GLIET, JwProtocol.TYPE_CNU, JwProtocol.TYPE_XBELLSOFT,
        JwProtocol.TYPE_CDUT, JwProtocol.TYPE_GZHUYJS, JwProtocol.TYPE_DHU, JwProtocol.TYPE_ECJTU, JwProtocol.TYPE_ZPTC, JwProtocol.TYPE_CTGU,
        JwProtocol.TYPE_CCIBE, JwProtocol.TYPE_CPPU, JwProtocol.TYPE_SHCC,
        JwProtocol.TYPE_AHMU, JwProtocol.TYPE_GDEI, JwProtocol.TYPE_JLU_POST,
        JwProtocol.TYPE_HBMZU, JwProtocol.TYPE_NUA, JwProtocol.TYPE_STUM,
        JwProtocol.TYPE_HUST, JwProtocol.TYPE_AIC,
        JwProtocol.TYPE_BFA, JwProtocol.TYPE_FDU, JwProtocol.TYPE_ZTVTIT,
        JwProtocol.TYPE_UCAS,
        JwProtocol.TYPE_RUC, JwProtocol.TYPE_SDU, JwProtocol.TYPE_SEIG,
        JwProtocol.TYPE_SHTU_POST_2024, JwProtocol.TYPE_KINGOSOFT,
        JwProtocol.TYPE_JLICT_QZ_OLD, JwProtocol.TYPE_CCUT_QZ_OLD, JwProtocol.TYPE_CCSU_QZ_OLD, JwProtocol.TYPE_URP_NEW_AJAX,
        JwProtocol.TYPE_SIMC, JwProtocol.TYPE_CHAOXING_SHARE, JwProtocol.TYPE_QZ_SINGLE_NODE, JwProtocol.TYPE_QZ_NJUST,
        JwProtocol.TYPE_QZ_WIT, JwProtocol.TYPE_QZ_ECUST, JwProtocol.TYPE_QZ_AHUT, JwProtocol.TYPE_QZ_BJFU,
    )

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
