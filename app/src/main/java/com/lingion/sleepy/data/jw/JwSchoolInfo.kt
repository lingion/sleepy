package com.lingion.sleepy.data.jw

/**
 * 学校信息（教务入口元数据）
 *
 * 字段含义：
 *   - sortKey: 拼音首字母 / "*" 表示"我的学校"（最近用过）/ "通" 表示通用类型分组
 *   - name: 学校名（用户可见）
 *   - url: 教务入口 URL（WebView 直接打开此 URL）
 *   - type: 协议类型（见 [JwProtocol]）
 */
data class JwSchoolInfo(
    val sortKey: String,
    val name: String,
    val url: String = "",
    val type: String? = null
)
