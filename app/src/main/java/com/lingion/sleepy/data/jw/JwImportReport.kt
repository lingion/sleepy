package com.lingion.sleepy.data.jw

import java.time.LocalDate

/**
 * 未验证学校导入成功后的可粘贴报告文本（离线回路, 2026-10-18）。
 *
 * 背景：项目无云端、承诺不上传任何数据 — 导入失败用户会开 issue，
 * 成功却是静默的，维护者永远看不到。本对象把成功事实压成一段纯文本，
 * 由 app 生成、用户复制到 QQ 群粘贴；维护者据此把该条目在
 * school_url_evidence 中翻为 verified，下个版本该学校的确认/报告提示即消失。
 *
 * 隐私边界（结构性保证）：输入只有学校侧公开信息 + 计数 + 版本号 + 日期，
 * 函数签名不接触账号、口令、session — 报告文本里不可能出现凭据。
 * 纯 JVM（无 android.*），本地化字段固定为 ASCII key 保证跨 locale 可 grep。
 */
object JwImportReport {

    /** QQ 群粘贴行的头部标记 — 维护者抓报告的第一道过滤。 */
    const val PASTE_MARKER = "[Sleepy导入成功]"

    /** type 缺失时的占位（不写 null, 避免被当成数据损坏）。 */
    const val UNKNOWN = "unknown"

    fun build(
        school: JwSchoolInfo,
        courseCount: Int,
        versionName: String,
        versionCode: Int,
        date: LocalDate = LocalDate.now(),
    ): String = buildString {
        appendLine(PASTE_MARKER)
        appendLine("app=${versionName} (${versionCode})")
        appendLine("date=${date}")
        appendLine("school=${school.name}")
        appendLine("catalogId=${school.catalogId.ifBlank { UNKNOWN }}")
        appendLine("status=${school.status}")
        appendLine("protocol=${school.type?.let { JwProtocol.displayName(it) } ?: UNKNOWN} (${school.type ?: UNKNOWN})")
        appendLine("url=${school.url.ifBlank { UNKNOWN }}")
        append("courses=$courseCount")
    }
}
