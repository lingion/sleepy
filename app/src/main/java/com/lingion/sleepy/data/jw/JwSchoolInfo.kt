package com.lingion.sleepy.data.jw

/**
 * 学校信息（教务入口元数据）
 *
 * 字段含义：
 *   - sortKey: 拼音首字母 / "*" 表示"我的学校"（最近用过）/ "通" 表示通用类型分组
 *   - name: 学校名（用户可见）
 *   - url: 教务入口 URL（WebView 直接打开此 URL）
 *   - type: 协议类型（见 [JwProtocol]）
 *   - timeJson: 校方节次时间表 JSON（可选）。导入配置页用它预填 — 用户仍可在 UI 调整。
 *             不填或 null → 用 [com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON]（WakeUp 12 节默认）。
 *
 * 例（HEU 13 节，2025-2026-2 学期实测）:
 *   [{"node":1,"start":"08:00","end":"08:45"}, ...]
 */
data class JwSchoolInfo(
    val sortKey: String,
    val name: String,
    val url: String = "",
    val type: String? = null,
    val timeJson: String? = null
)
