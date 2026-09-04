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

/** 镜像下载前缀 — 目录结构与 github.com 1:1, 直接字符串替换即可改写。 */
private const val MIRROR_HOST = "https://gh.qdp.qzz.io"
private const val GITHUB_DOWNLOAD_HOST = "https://github.com"

/**
 * 解析 GitHub releases/latest 的 JSON 为 [UpdateInfo](纯函数,无 IO)。
 *
 * [abi] 形如 "arm64-v8a" / "armeabi-v7a" / "x86_64",用于挑对应 asset。
 * 找不到对应 asset 时 downloadUrl 返回空串(调用方走镜像回退)。
 *
 * 镜像改写(2026-09-05 用户报障): api.github.com 可达 ≠ github.com 资产可达 —
 * 信息拉到了、下载 15s 超时。browser_download_url 指向 github.com 的,
 * 一律改写到镜像 (目录 1:1), 下载失败时由 [toDirectGithubUrl] 还原直连兜底。
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
            ?.toMirrorDownloadUrl()
    } ?: ""
    val force = body.contains(FORCE_FLAG)
    val isUpdateAvailable = force ||
        VersionUtils.compare(version.ifBlank { "0" }, currentVersion) > 0
    return UpdateInfo(version, body, downloadUrl, isUpdateAvailable)
}

/** github.com 下载地址 → 镜像地址; 非下载路径原样返回。 */
private fun String.toMirrorDownloadUrl(): String =
    if (startsWith("$GITHUB_DOWNLOAD_HOST/lingion/sleepy/releases/download/"))
        replacePrefix(GITHUB_DOWNLOAD_HOST, MIRROR_HOST)
    else this

/** 镜像下载地址 → github.com 直连(下载失败的回退); 其余原样返回。 */
fun toDirectGithubUrl(url: String): String = when {
    url.startsWith("$MIRROR_HOST/lingion/sleepy/releases/download/") ->
        url.replacePrefix(MIRROR_HOST, GITHUB_DOWNLOAD_HOST)
    else -> url
}

private fun String.replacePrefix(prefix: String, replacement: String): String =
    replacement + substring(prefix.length)

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
