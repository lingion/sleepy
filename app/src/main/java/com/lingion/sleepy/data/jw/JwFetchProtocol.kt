package com.lingion.sleepy.data.jw

/**
 * T11: WebView 内 fetch JS 选择表(纯函数)。
 *
 * pick 返回 null = 走原有 outerHTML 路径(默认)。
 * 决策依据: 学校 type 显式声明 > URL 路径指纹(含 WebVPN 重写形态) > enableFetch 开关。
 */
enum class FetchKind { WISEDU, ZF_NEW, QZ }

object JwFetchProtocol {

    /** 学校 type + 当前 URL → fetch 类型; null = 走 outerHTML 抓取 */
    fun pick(school: JwSchoolInfo, currentUrl: String?): FetchKind? {
        val u = (currentUrl ?: school.url).lowercase()
        // ① 显式 type 优先
        when (school.type) {
            JwProtocol.TYPE_WISEDU -> if (u.contains("/jwapp/")) return FetchKind.WISEDU
            JwProtocol.TYPE_ZF_NEW -> if (u.contains("/jwglxt/") || WEBVPN_HTTP_HEX.containsMatchIn(u)) {
                return FetchKind.ZF_NEW
            }
            JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_BR, JwProtocol.TYPE_QZ_WITH_NODE,
            JwProtocol.TYPE_QZ_OLD, JwProtocol.TYPE_QZ_CRAZY -> {
                // 默认策略: QZ 不强推 fetch, 仅 enableFetch=true 的学校走
                if (school.enableFetch && u.contains("/jsxsd/")) return FetchKind.QZ
                return null
            }
        }
        // ② type 未命中但 URL 路径指纹命中
        if (u.contains("/jwapp/")) return FetchKind.WISEDU
        if (u.contains("/jwglxt/") || WEBVPN_HTTP_HEX.containsMatchIn(u)) return FetchKind.ZF_NEW
        if (school.enableFetch && u.contains("/jsxsd/")) return FetchKind.QZ
        return null
    }

    private val WEBVPN_HTTP_HEX = Regex("""/http/[0-9a-f]{4,8}/""")

    /** 协议段(不带前导斜杠): jwapp / jwglxt / jsxsd */
    fun pathSegment(kind: FetchKind): String = when (kind) {
        FetchKind.WISEDU -> "jwapp"
        FetchKind.ZF_NEW -> "jwglxt"
        FetchKind.QZ -> "jsxsd"
    }

    /** 从 URL 抓 gnmkdm 参数; 无则返回 default */
    fun extractGnmkdm(url: String, default: String): String {
        val m = Regex("""gnmkdm=([A-Za-z0-9]+)""").find(url)
        return m?.groupValues?.get(1) ?: default
    }

    /** 去 WebVPN 前缀: /http/<hex>/ 与 /webvpn/<host>/ 两种; 无前缀原样返回 */
    fun stripWebvpnPrefix(pathname: String): String {
        val httpHex = Regex("""^/http/[0-9a-f]{4,8}""")
        if (httpHex.containsMatchIn(pathname)) return pathname.substringAfter(httpHex.find(pathname)!!.value)
        val webHost = Regex("""^/webvpn/[^/]+""")
        if (webHost.containsMatchIn(pathname)) return pathname.substringAfter(webHost.find(pathname)!!.value)
        return pathname
    }
}
