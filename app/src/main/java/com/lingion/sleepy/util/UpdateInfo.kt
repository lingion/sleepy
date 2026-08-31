package com.lingion.sleepy.util

/**
 * 远端 release 信息(纯数据,不含 Android 依赖,可单测)。
 *
 * [isUpdateAvailable] 由 [parseReleaseJson] 根据版本比较 + force flag 算出。
 * 调用方(UpdateManager)据它决定弹窗 vs Toast。
 */
data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

private const val FORCE_FLAG = "SLEEPY_FORCE_UPDATE=true"

/**
 * 解析 GitHub releases/latest 的 JSON 为 [UpdateInfo](纯函数,无 IO)。
 *
 * [abi] 形如 "arm64-v8a" / "armeabi-v7a" / "x86_64",用于挑对应 asset。
 * 找不到对应 asset 时 downloadUrl 返回空串(调用方走镜像回退)。
 */
fun parseReleaseJson(json: String, currentVersion: String, abi: String): UpdateInfo {
    val release = org.json.JSONObject(json)
    val version = release.optString("tag_name").removePrefix("v")
    val body = release.optString("body")
    val assetName = "app-$abi-release.apk"
    val downloadUrl = release.optJSONArray("assets")?.let { assets ->
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName }
            ?.optString("browser_download_url")
    } ?: ""
    val force = body.contains(FORCE_FLAG)
    val isUpdateAvailable = force ||
        VersionUtils.compare(version.ifBlank { "0" }, currentVersion) > 0
    return UpdateInfo(version, body, downloadUrl, isUpdateAvailable)
}

/**
 * 从镜像 release 页 HTML 提取 changelog,转成 Markdown。
 *
 * GitHub release 页把正文放在 <div class="markdown-body ..."> 里(渲染后的 HTML)。
 * 取第一个 markdown-body 块,做块级/行内两级转换回 Markdown。
 * 找不到块(页面 404/结构变化)返回空串,调用方回退到"看不到更新内容"而不是崩。
 *
 * 已知局限:markdown-body 内嵌套 <div>(如 <details>)会在第一个 </div> 被截断。
 * 纯文本+列表的 release notes(本项目的情况)不受影响。
 */
fun parseMirrorPage(pageHtml: String, tag: String): String {
    // 只认含 markdown-body 的 div 开标签(GitHub 固定写法,前面可能带 data-* 属性)
    val open = Regex("""<div\b[^>]*class="[^"]*markdown-body[^"]*"[^>]*>""")
        .find(pageHtml) ?: return ""
    val rest = pageHtml.substring(open.range.last + 1)
    val close = rest.indexOf("</div>")
    if (close < 0) return ""
    return htmlToMarkdown(rest.substring(0, close)).trim()
}

/** 块级 + 行内两级 HTML→Markdown。只覆盖 GitHub release notes 用到的标签。 */
private fun htmlToMarkdown(html: String): String {
    var s = html
    // 块级:标题、列表项、段落 → 前缀 + 换行
    s = Regex("""<h([1-6])[^>]*>(.*?)</h\1>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        val hashes = "#".repeat(m.groupValues[1].toInt())
        "\n\n$hashes ${m.groupValues[2].trim()}\n\n"
    }
    s = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        "\n- ${m.groupValues[1].trim()}"
    }
    s = Regex("""</?[uo]l[^>]*>""").replace(s, "\n")
    s = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        "\n\n${m.groupValues[1].trim()}\n\n"
    }
    // 行内:加粗、斜体、代码、链接(链接必须在其它行内标签之后,避免 href 里的 ** 被误转)
    s = Regex("""<(strong|b)[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        "**${m.groupValues[2].trim()}**"
    }
    s = Regex("""<(em|i)[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        "*${m.groupValues[2].trim()}*"
    }
    s = Regex("""<code[^>]*>(.*?)</code>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        "`${m.groupValues[1].trim()}`"
    }
    s = Regex("""<a\b[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
        "[${m.groupValues[2].trim()}](${m.groupValues[1]})"
    }
    // 剥掉剩余任何标签(div/span/hr/br/img/svg...),保留文本
    s = Regex("""<[^>]+>""").replace(s, "")
    s = decodeEntities(s)
    // 压掉多余空行(转换产生的 \n\n\n... 收敛到最多两个)
    return s.replace(Regex("""\n{3,}"""), "\n\n")
}

private fun decodeEntities(s: String): String = s
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&nbsp;", " ")
