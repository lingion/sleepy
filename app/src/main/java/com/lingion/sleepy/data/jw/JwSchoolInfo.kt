package com.lingion.sleepy.data.jw

/**
 * 学校信息（教务入口元数据）
 *
 * 字段含义：
 *   - sortKey: 拼音首字母 / "#" 表示"我的学校"（最近用过）/ "通" 表示通用类型分组
 *   - name: 学校名（用户可见）
 *   - url: HTTP(S) 教务入口；待适配条目可以没有入口。
 *   - type: 已声明的协议类型（见 [JwProtocol]）；未知协议为 null。
 *   - status: supported / grad_supported 保留既有支持；unverified / grad_unverified
 *     需用户确认后尝试；pending / grad_pending / legacy 不进入教务导入。
 */
data class JwSchoolInfo(
    val sortKey: String,
    val name: String,
    val url: String = "",
    val type: String? = null,
    val status: String = STATUS_SUPPORTED,
    val aliases: List<String> = emptyList(),
    val sortKeyFull: String = "",
    /** T11 新增:该校是否启用 WebView 内 fetch 模式。默认 false 避免给未验证学校强行 fetch 出 0 课。 */
    val enableFetch: Boolean = false,
    /** Known home/campus cities, used only for the school picker recommendations. */
    val cities: List<String> = emptyList(),
    /** Stable source identity; older assets and drafts keep their original key. */
    val catalogId: String = "",
) {
    val stableKey: String get() = catalogId.ifBlank { "${sortKey}_${name}" }
    val isUnverified: Boolean get() = status == STATUS_UNVERIFIED || status == STATUS_GRAD_UNVERIFIED
    /** Unverified entries require an explicit user confirmation before navigation. */
    val canImport: Boolean get() = (isSupported || (isUnverified && JwParserRegistry.supports(type))) && hasUrl
    val isSupported: Boolean get() = status == STATUS_SUPPORTED || status == STATUS_GRAD_SUPPORTED
    val isGrad: Boolean get() = status == STATUS_GRAD_SUPPORTED || status == STATUS_GRAD_PENDING || status == STATUS_GRAD_UNVERIFIED

    /** T13: pending/grad_pending 状态 — 待适配(点行弹申请适配弹窗, 不进 WebView) */
    val isPending: Boolean get() = status == STATUS_PENDING || status == STATUS_GRAD_PENDING

    /** A web URL with an explicit host and no embedded credentials. */
    val hasUrl: Boolean get() = runCatching {
        val uri = java.net.URI(url)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    companion object {
        /** Missing status keeps old assets compatible; unknown values never enable import. */
        fun normalizeStatus(raw: String?): String = when (raw) {
            null, "", STATUS_SUPPORTED -> STATUS_SUPPORTED
            STATUS_GRAD_SUPPORTED -> STATUS_GRAD_SUPPORTED
            STATUS_GRAD_PENDING -> STATUS_GRAD_PENDING
            STATUS_UNVERIFIED -> STATUS_UNVERIFIED
            STATUS_GRAD_UNVERIFIED -> STATUS_GRAD_UNVERIFIED
            STATUS_LEGACY -> STATUS_LEGACY
            else -> STATUS_PENDING
        }

        const val STATUS_UNVERIFIED = "unverified"
        const val STATUS_GRAD_UNVERIFIED = "grad_unverified"
        const val STATUS_SUPPORTED = "supported"
        const val STATUS_PENDING = "pending"
        const val STATUS_GRAD_SUPPORTED = "grad_supported"
        const val STATUS_GRAD_PENDING = "grad_pending"
        const val STATUS_LEGACY = "legacy"
    }
}
