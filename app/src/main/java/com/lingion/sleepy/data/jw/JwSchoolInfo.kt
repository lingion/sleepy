package com.lingion.sleepy.data.jw

/**
 * 学校信息（教务入口元数据）
 *
 * 字段含义：
 *   - sortKey: 拼音首字母 / "#" 表示"我的学校"（最近用过）/ "通" 表示通用类型分组
 *   - name: 学校名（用户可见）
 *   - url: 教务入口 URL（WebView 直接打开此 URL）。status != "supported" 时为空
 *   - type: 协议类型（见 [JwProtocol]）。status != "supported" 时为 null
 *   - status: 支持状态 — "supported" 已实现 / "pending" 本科待适配 / "grad_supported" 研究生已支持 / "grad_pending" 研究生待适配 / "legacy" 旧条目
 */
data class JwSchoolInfo(
    val sortKey: String,
    val name: String,
    val url: String = "",
    val type: String? = null,
    val status: String = STATUS_SUPPORTED,
    val aliases: List<String> = emptyList()
) {
    val isSupported: Boolean get() = status == STATUS_SUPPORTED || status == STATUS_GRAD_SUPPORTED
    val isGrad: Boolean get() = status == STATUS_GRAD_SUPPORTED || status == STATUS_GRAD_PENDING

    companion object {
        const val STATUS_SUPPORTED = "supported"
        const val STATUS_PENDING = "pending"
        const val STATUS_GRAD_SUPPORTED = "grad_supported"
        const val STATUS_GRAD_PENDING = "grad_pending"
        const val STATUS_LEGACY = "legacy"
    }
}
