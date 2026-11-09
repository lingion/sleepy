package com.lingion.sleepy.data.jw

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * UCAS (#18) 课程详情页抓取器。
 *
 * personSchedule 网格页 (xkgo.ucas.ac.cn:3000) 的课程格链接指向跨源详情站
 * xkcts.ucas.ac.cn:8443 — WebView 页面上下文 fetch 会被 CORS 拦 (采集器 v1.2
 * 也因此被迫走 tab 导航)。但详情页**免登录** (issue #18 v1.2 采集包实测:
 * 无 cookie 直连 200 + 完整周次数据), 所以原生 HTTP 直抓即可, 无须会话凭证。
 *
 * 用法: 网格 HTML 进，完整课程详情组合源出。任一失败交给导入界面处理。
 */
object UcasDetailFetch {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /** 详情页抓取上限 (实测一门课 11 页; 上限防异常网格撑爆导入流程) */
    private const val MAX_DETAILS = 50

    /**
     * 主入口: 网格 HTML → 逐课抓详情 → 拼组合源。
     * 全部详情有明确课程安排后才返回组合源，不把失败页面当成空课表。
     */
    suspend fun enrich(gridHtml: String): String = withContext(Dispatchers.IO) {
        enrichWith(gridHtml) { fetchOne(it) }
    }

    internal suspend fun enrichWith(gridHtml: String, fetch: suspend (String) -> String): String {
        val urls = try {
            JwUcasParser.extractDetailUrls(gridHtml)
        } catch (e: Exception) {
            emptyList()
        }
        if (urls.isEmpty()) return gridHtml
        if (urls.size > MAX_DETAILS) throw IOException("课程详情数量超过单次导入上限，无法完整导入")

        val details = mutableListOf<Pair<String, String>>()
        for (url in urls) {
            val html = fetch(url)
            if (JwUcasParser.parseDetailPages(html).isEmpty()) throw IOException("课程详情未提供完整上课安排，请重新进入课表页后重试")
            details += url to html
        }
        return combine(gridHtml, details)
    }

    /** 纯函数: 网格 + (url, html) 列表 → 带 [JwUcasParser.DETAIL_MARKER_OPEN] 分段的组合源 */
    fun combine(gridHtml: String, details: List<Pair<String, String>>): String = buildString {
        append(gridHtml)
        for ((url, html) in details) {
            append('\n')
            append(JwUcasParser.DETAIL_MARKER_OPEN)
            append(url)
            append("-->\n")
            append(html)
            append('\n')
            append(JwUcasParser.DETAIL_MARKER_CLOSE)
            append('\n')
        }
    }

    /** 抓单个详情页；非 200 或 IO 异常使本次采集失败。 */
    private fun fetchOne(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
            )
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val charset = conn.contentType
                ?.substringAfter("charset=", "")
                ?.substringBefore(';')?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "UTF-8"
            return conn.inputStream.bufferedReader(charset(charset)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
