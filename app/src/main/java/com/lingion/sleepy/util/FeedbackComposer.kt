package com.lingion.sleepy.util

import java.net.URLEncoder

/**
 * 反馈工具类:拼装 GitHub 新 Issue URL 和 mailto: 链接,
 * 在关于页面供用户跳出去提反馈。
 *
 * 设计原则:
 * - 应用不调用 GitHub API,只构造 URL 让系统浏览器或邮件客户端打开。
 * - 诊断信息只包含版本/系统/设备/分辨率/语言/构建类型,不含课表内容、文件、账号、位置等隐私数据。
 * - 所有用户可控字符串都做 URL 编码,防止 ?title= 等被注入额外参数。
 *
 * 测试在 app/src/test/java/com/lingion/sleepy/util/FeedbackComposerTest.kt
 */
object FeedbackComposer {

    const val GITHUB_REPO = "lingion/sleepy"
    const val FALLBACK_EMAIL = "lingion@hrbeu.edu.cn"
    private const val NEW_ISSUE_PATH = "/issues/new"

    /**
     * 构造 GitHub 新 Issue URL。
     *
     * 参数顺序:?template= → ?title= → ?body=,保证 GitHub 优先识别模板并把诊断信息落到 body。
     * @param title 预填标题(用户的反馈主题)
     * @param body 预填正文(用户的描述)
     * @param diag 诊断信息(versionCode / android / device / resolution / locale / build)
     * @param template 选用的 Issue Form 名,例如 "bug_report"、"feature_request";传 null 表示不指定模板
     */
    fun githubIssueUrl(
        title: String,
        body: String,
        diag: Diagnostic,
        template: String? = null,
    ): String {
        val encodedTitle = enc(title)
        val encodedBody = enc("$body\n\n${formatDiagnostic(diag)}")
        val params = mutableListOf<String>()
        if (!template.isNullOrEmpty()) {
            params += "template=${enc(template)}"
        }
        params += "title=$encodedTitle"
        params += "body=$encodedBody"
        return "https://github.com/$GITHUB_REPO$NEW_ISSUE_PATH?${params.joinToString("&")}"
    }

    /**
     * 构造 mailto: URI。
     * 默认发到 FALLBACK_EMAIL(用户邮箱 lingion@hrbeu.edu.cn)。
     */
    fun mailtoUri(
        subject: String,
        body: String,
        diag: Diagnostic,
        email: String = FALLBACK_EMAIL,
    ): String {
        val encodedSubject = enc(subject)
        val encodedBody = enc("$body\n\n${formatDiagnostic(diag)}")
        return "mailto:$email?subject=$encodedSubject&body=$encodedBody"
    }

    /**
     * 渲染诊断信息块,作为 Issue / 邮件 body 的一部分。
     * Markdown 输出,Markdown 注入字符会被反斜杠转义防止误渲染。
     */
    fun formatDiagnostic(d: Diagnostic): String = buildString {
        appendLine("---")
        appendLine("**Version:** ${escapeMd(d.versionName)}")
        appendLine("**VersionCode:** ${d.versionCode}")
        appendLine("**Android:** ${escapeMd(d.androidVersion)}")
        appendLine("**Device:** ${escapeMd(d.brand)} ${escapeMd(d.model)}")
        appendLine("**Resolution:** ${escapeMd(d.resolution)}")
        appendLine("**Locale:** ${escapeMd(d.locale)}")
        append("**Build:** ${if (d.isDebug) "Debug" else "Release"}")
    }

    /**
     * 诊断数据快照。Android 上由调用方填实。
     */
    data class Diagnostic(
        val versionName: String,
        val versionCode: Int,
        val androidVersion: String,
        val brand: String,
        val model: String,
        val resolution: String,
        val locale: String,
        val isDebug: Boolean,
    )

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     * 把字面意义上有破坏性的 Markdown 字符转义成反斜杠前缀,防止被解析成格式。
     * 仅转义 * _ ` [ ] ( ),足以拦住注入而不影响普通文本。
     */
    private fun escapeMd(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c == '*' || c == '_' || c == '`' ||
                c == '[' || c == ']' || c == '(' || c == ')') {
                sb.append('\\').append(c)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}