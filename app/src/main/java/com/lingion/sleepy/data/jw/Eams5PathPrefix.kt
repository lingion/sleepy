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

/**
 * EAMS5 course-table HTML 解析出的 studentId 正则模式 (兼容多形态)。
 *
 * 上游 supwisdom 平台在 /for-std/course-table/info/<studentId> 页面 HTML <script>
 * 段里嵌入 studentId 写法不统一, 实测 6 种形态都能出现 (2026-09 用户反馈 +
 * 跨仓验证 Chiu-xaH/HFUT-Schedule JxglstuRepository.kt + BoynChan/HfutOpenApi):
 *   A. `var studentId = '2024210001';`         ← VAR 声明 + 单引号
 *   B. `var studentId="2024210001";`            ← VAR 声明 + 双引号无空格
 *   C. `studentId:'2024210001',`                ← 对象字面量 + 单引号
 *   D. `studentId: "2024210001",`               ← 对象字面量 + 双引号
 *   E. `studentId=2024210001;`                  ← 裸数字 (少见)
 *   F. `studentId=2024210001&...`               ← 查询串里
 *
 * 旧 regex `studentId\s*[=:]\s*['"]([\d]+)['"]` 只覆盖 A/B/C/D 且强约束引号,
 * 对象字面量无引号形态裸值时漏 → 用户看到"未取到 studentId" 误报。
 *
 * 当前 regex: 键名 studentId + 分隔符 `[=:]` + 可选引号 + `[\w]+` 值 + 可选引号。
 * 返回值允许 A-Za-z0-9_ (含字母+数字学号), 第一个匹配即返回。
 */
val EAMS5_STUDENT_ID_REGEX: Regex = Regex("""studentId\s*[=:]\s*['"]?([A-Za-z0-9]+)['"]?""")

/** 从 EAMS5 /for-std/course-table/info HTML 中提取 studentId, 失败返回 null */
fun extractEams5StudentId(html: String): String? {
    val m = EAMS5_STUDENT_ID_REGEX.find(html) ?: return null
    return m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
}

/**
 * 检测 fetch 最终 URL 是否被重定向到登录页 (会话失效标志)。
 * upstream 302 重定向到 /eams5-student/login 或 /student/login。
 */
fun isEams5LoginRedirect(finalUrl: String): Boolean {
    if (finalUrl.isBlank()) return false
    return finalUrl.contains("/login") || finalUrl.contains("login?")
}
