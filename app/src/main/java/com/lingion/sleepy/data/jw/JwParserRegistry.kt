package com.lingion.sleepy.data.jw

/**
 * T8 — 统一解析分发与兜底裁决。
 *
 * 单一来源：协议 type → parser 工厂；type 为空时跑全部候选按 confidence/课程数裁决。
 * ParserAttempt 快照供 T9 诊断（JwParseDiagnostics.classify）消费。
 */
object JwParserRegistry {

    /**
     * 单个 parser 尝试的快照。
     * T9 直接复用此 data class（不要在 T9 里再建一份）。
     */
    data class ParserAttempt(
        val parserName: String,        // e.g. "JwOldZfParser(type=0)"
        val type: String?,             // JwProtocol.TYPE_* 或 null（兜底时未声明）
        val courseCount: Int,
        val confidence: Int,
        val matchedFeatures: List<String>,
        val exception: String?,        // 简短异常类名+message，禁止含 HTML 全文
    )

    /**
     * 协议族优先级表。数字越小优先级越高（用于并列裁决）。
     * 优先级反映"误抢风险"：协议族特征越窄、越独特，越优先。
     */
    private val TYPE_PRIORITY: Map<String, Int> = linkedMapOf(
        JwProtocol.TYPE_WISEDU to 10,
        JwProtocol.TYPE_CQU to 15,
        JwProtocol.TYPE_EAMS5 to 18,
        JwProtocol.TYPE_PKU to 20,
        JwProtocol.TYPE_SEU to 22,
        JwProtocol.TYPE_ZJU to 23,
        JwProtocol.TYPE_USTC to 24,
        JwProtocol.TYPE_BNUZ to 30,
        JwProtocol.TYPE_CF to 40,
        JwProtocol.TYPE_HNUST to 50,
        JwProtocol.TYPE_HNIU to 60,
        JwProtocol.TYPE_ZF to 70,
        JwProtocol.TYPE_ZF_1 to 75,
        JwProtocol.TYPE_URP to 80,
        JwProtocol.TYPE_URP_NEW to 85,
        JwProtocol.TYPE_ZF_NEW to 90,
        JwProtocol.TYPE_QZ to 100,
        JwProtocol.TYPE_QZ_CRAZY to 110,
        JwProtocol.TYPE_QZ_BR to 120,
        JwProtocol.TYPE_QZ_WITH_NODE to 130,
        JwProtocol.TYPE_QZ_OLD to 140,
    )

    /**
     * 单一来源：协议 type → parser 工厂。
     * 注意：TYPE_ZF_1 复用 JwOldZfParser(type=1)。
     */
    private val FACTORIES: Map<String, (String) -> JwParser> = linkedMapOf(
        JwProtocol.TYPE_WISEDU to ::JwWiseduParser,
        JwProtocol.TYPE_CQU to ::JwCquParser,
        JwProtocol.TYPE_EAMS5 to ::JwEams5Parser,
        JwProtocol.TYPE_SEU to ::JwSeuParser,
        JwProtocol.TYPE_ZJU to ::JwZjuParser,
        JwProtocol.TYPE_USTC to ::JwUstcParser,
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
        JwProtocol.TYPE_CF to ::JwChengFangParser,
        JwProtocol.TYPE_PKU to ::JwPekingParser,
        JwProtocol.TYPE_BNUZ to ::JwBnuzParser,
        JwProtocol.TYPE_HNUST to { html -> JwHnustParser(html) },
        JwProtocol.TYPE_HNIU to ::JwHniuparser,
    )

    /** 静态候选（兜底用）：返回全部 parser 工厂列表，按 TYPE_PRIORITY 升序 */
    fun allCandidates(html: String): List<Pair<String?, JwParser>> =
        TYPE_PRIORITY.entries.mapNotNull { (t, _) ->
            val factory = FACTORIES[t] ?: return@mapNotNull null
            t to factory(html)
        }

    /**
     * 显式分发：type 已知时按 FACTORIES 表单派。
     * 未在表内的 type → 抛 IllegalArgumentException（与现行 parseHtml 兼容）。
     */
    fun parserFor(type: String, html: String): JwParser =
        FACTORIES[type]?.invoke(html)
            ?: throw IllegalArgumentException("协议 $type 暂不支持")

    private data class Row(
        val type: String?,
        val attempt: ParserAttempt,
        val result: List<JwCourse>,
    )

    /**
     * 兜底：type 为空时跑全部候选，按 confidence 裁决。
     * 返回 (best, attempts) — best 是 List<JwCourse>，attempts 给 T9 诊断用。
     *
     * 性能注意：所有 parser 都会被实例化并 generateCourseList 一次（含 0 课情形）。
     * 单测应控制 fixture 大小（< 50KB）以保持测试快速。
     */
    fun selectBest(
        html: String,
        declaredType: String? = null,
    ): Pair<List<JwCourse>, List<ParserAttempt>> {
        val attempts = mutableListOf<ParserAttempt>()
        val candidates = allCandidates(html)

        val results: List<Row> = candidates.map { (type, parser) ->
            val conf = try { parser.confidence() } catch (e: Exception) { 0 }
            val matched = try { parser.matchedFeatures() } catch (e: Exception) { emptyList() }
            val (count, result, exMsg) = try {
                val r = parser.generateCourseList()
                Triple(r.size, r, null)
            } catch (e: JwParseException) {
                // 缺表等已分类异常: 保留首个 attempt 的语义标记 (NO_TABLE_CONTAINER_MARKER 等)
                val marker = e.attempts.firstOrNull()?.exception
                Triple(0, emptyList<JwCourse>(), marker ?: "${e::class.simpleName}: ${e.message?.take(60)}")
            } catch (e: Exception) {
                Triple(0, emptyList<JwCourse>(), "${e::class.simpleName}: ${e.message?.take(60)}")
            }
            val attempt = ParserAttempt(
                parserName = try { parser.nameForDiag() } catch (e: Exception) { "JwParser" },
                type = type,
                courseCount = count,
                confidence = conf,
                matchedFeatures = matched,
                exception = exMsg,
            )
            attempts += attempt
            Row(type, attempt, result)
        }

        // 裁决规则（按优先级降序）：
        //   1. declaredType 已知且该 parser 解析出 >0 课 → 强制使用（用户选校信号 > 通用 confidence）
        //      declaredType 对应 parser 0 课 → 回退通用规则，不让"选错协议"变成永远 0 课死路
        //   2. 有 confidence >= 80 且 >0 课的候选 → 取 confidence 最高
        //   3. 其余 → courseCount 最大；并列时 confidence 高者先，再并列按 TYPE_PRIORITY 升序
        //   4. 全 0 课 → 空列表 + 全部 attempts
        val bestResult: List<JwCourse> = if (!declaredType.isNullOrBlank()) {
            val declaredResult = results.firstOrNull { it.type == declaredType }?.result.orEmpty()
            if (declaredResult.isNotEmpty()) declaredResult else generalAdjudication(results)
        } else {
            generalAdjudication(results)
        }

        return bestResult to attempts
    }

    /** 通用裁决（declaredType 为空，或 declaredType 对应 parser 0 课时的回退分支） */
    private fun generalAdjudication(results: List<Row>): List<JwCourse> {
        return results.filter { it.attempt.confidence >= 80 && it.result.isNotEmpty() }
            .maxByOrNull { it.attempt.confidence }
            ?.result
            ?: results.filter { it.result.isNotEmpty() }
                .maxWithOrNull(
                    compareBy<Row> { it.result.size }
                        .thenByDescending { it.attempt.confidence }
                        .thenBy { TYPE_PRIORITY[it.type] ?: 999 },
                )
                ?.result
            ?: emptyList()
    }
}

/**
 * T8 新增：诊断用异常，带每个 parser 的尝试快照。
 *
 * T9 通过 catch (e: JwParseException) 拿到 attempts 列表喂给 JwParseDiagnostics.classify。
 * 现有 JwImportActivity 的 catch (e: Exception) 已经会接住这个，无需改 import。
 */
class JwParseException(
    message: String,
    val attempts: List<JwParserRegistry.ParserAttempt> = emptyList(),
) : RuntimeException(message)

/** T8 辅助：把 JwParser 名字格式化（含 JwOldZfParser.type=0/1、HNUST 的 oldQzType 等） */
internal fun JwParser.nameForDiag(): String {
    val cls = this::class.simpleName ?: "JwParser"
    return when (this) {
        is JwOldZfParser -> "$cls(type=$type)"
        is JwHnustParser -> "$cls(oldQzType=$oldQzType)"
        else -> cls
    }
}
