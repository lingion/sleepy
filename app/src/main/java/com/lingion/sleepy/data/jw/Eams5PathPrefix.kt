package com.lingion.sleepy.data.jw

/**
 * 2026-09 211 批量收录：EAMS5 (supwisdom 新版) 课表 API 路径前缀推断。
 *
 * 同一 supwisdom 平台存在两种部署前缀：
 *   - 合肥工业大学: /eams5-student/for-std/... (jxglstu.hfut.edu.cn)
 *   - 安徽大学 / 矿大北京: /student/for-std/... (jw.ahu.edu.cn / jwxt.cumtb.edu.cn,
 *     survey 211-survey.md A 档实测 eams-ui + for-std 302)
 *
 * 纯 JVM 函数 (无 Android 依赖)，WebView fetch JS 注入前调用；推断失败
 * 默认合工大形态保证向后兼容。
 */
object Eams5PathPrefix {

    /** 合工大形态默认前缀 */
    const val DEFAULT = "/eams5-student"

    /** supwisdom 新版短前缀 (安大/矿大北京) */
    const val STUDENT = "/student"

    /** 由学校条目 URL 推断该校的 API 前缀 */
    fun fromSchoolUrl(url: String): String {
        val u = url.lowercase()
        // 显式 /eams5-student/ 路径 → 合工大形态
        if (u.contains("/eams5-student")) return DEFAULT
        // for-std/student 路径且无 eams5 段 → supwisdom 新版短前缀
        if (u.contains("/for-std/") || u.contains("/student/")) return STUDENT
        return DEFAULT
    }
}

/** fetch JS 占位符 (UI 层 EAMS5_FETCH_JS 模板 + 单测共用同一符号) */
const val EAMS5_PREFIX_PLACEHOLDER = "__EAMS5_PREFIX__"

/** 测试与生产共用的顶层便捷函数 */
fun eams5PathPrefixFor(url: String): String = Eams5PathPrefix.fromSchoolUrl(url)
