package com.lingion.sleepy.data.jw

/**
 * issue #25 — 把 typed URL 映射回 schools.json 目录条目。
 *
 * 失败链:
 *   1. 用户输入 https://one.hfut.edu.cn/ (HFUT 统一信息门户, 非教务)
 *   2. SchoolSelectScreen.UrlDirectRow 命中 → 创建自定义条目 type=null
 *   3. WebView 跳到门户 → 通用抓取 → 0 课
 *
 * 修复:
 *   解析 typed URL 的 host, 取注册域, 在目录条目中查找相同注册域的 supported+有 URL 条目。
 *   命中后用目录条目替代自定义条目 (拿到权威 URL + 协议 type)。
 *
 * 命名: 与 [JwWebViewLoginScreen.SslBypassRegistry] 一致, 委托同一 registrableDomain 实现
 * (避免两个独立实现飘移)。
 */
object SchoolDomainMatch {
    private fun parsed(value: String): java.net.URI? = runCatching {
        java.net.URI(value.trim()).takeIf {
            it.scheme?.lowercase() in setOf("http", "https") &&
                !it.host.isNullOrBlank() && it.rawUserInfo == null
        }
    }.getOrNull()

    private fun sameEntry(a: java.net.URI, b: java.net.URI): Boolean {
        fun port(uri: java.net.URI) = if (uri.port >= 0) uri.port else
            if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
        return a.scheme.equals(b.scheme, ignoreCase = true) &&
            a.host.equals(b.host, ignoreCase = true) && port(a) == port(b) &&
            a.rawPath.orEmpty().ifEmpty { "/" } == b.rawPath.orEmpty().ifEmpty { "/" } &&
            a.rawQuery == b.rawQuery && a.rawFragment == b.rawFragment
    }

    /**
     * UI selection boundary: an exact known URL keeps its status, including pending.
     * Shared URLs return every candidate for the user to choose; never silently select one.
     * Only unknown URLs may become a custom entry or use the supported-only domain fallback.
     */
    fun selectionsForUrl(url: String, catalog: List<JwSchoolInfo>, protocolType: String?, customName: String): List<JwSchoolInfo> {
        val target = parsed(url)
        val exact = if (target == null) emptyList() else catalog.filter { school ->
            parsed(school.url)?.let { sameEntry(it, target) } == true
        }
        if (exact.isNotEmpty()) return exact
        return listOf(matchSchool(url, catalog) ?: JwSchoolInfo(
            sortKey = "", name = customName, url = url, type = protocolType,
            status = JwSchoolInfo.STATUS_SUPPORTED,
        ))
    }

    /**
     * 注册域启发式 — 与 SslBypassRegistry.registrableDomain 一致。
     * 取末两段 (edu.cn / edu.hk / ac.uk / edu.tw / edu.jp 等多段后缀取末三段)。
     */
    fun registrableDomain(host: String): String {
        val h = host.lowercase().trim().trimEnd('.')
        if (h.isEmpty()) return ""
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val secondLevel = parts[parts.size - 2]
        val tld = parts.last()
        if ((tld == "cn" || tld == "hk" || tld == "uk" || tld == "tw" || tld == "jp") &&
            secondLevel in setOf("edu", "ac", "gov", "org")
        ) {
            return parts.takeLast(3).joinToString(".")
        }
        return parts.takeLast(2).joinToString(".")
    }

    /** 抽 host: 处理 https?:// 前缀、尾部 /path、空白。空返回空串。 */
    fun hostOf(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return ""
        val schemeEnd = u.indexOf("://").let { if (it < 0) 0 else it + 3 }
        val rest = u.substring(schemeEnd)
        val slash = rest.indexOf('/')
        val hostAndPort = if (slash < 0) rest else rest.substring(0, slash)
        val colon = hostAndPort.indexOf(':')
        return if (colon < 0) hostAndPort else hostAndPort.substring(0, colon)
    }

    /** 是否 WebVPN 重写 host (不可见原 host, 不可参与目录映射)。 */
    fun isVpnRewrite(host: String): Boolean {
        if (host.isBlank()) return false
        return host.contains("/webvpn/")
            || host.contains(".webvpn.")
            || host.startsWith("webvpn.")
    }

    /**
     * 在 [catalog] 中查找: 同注册域, supported (含 grad_supported), 有 URL。
     * WebVPN / 未知域 / 空白 host → null。
     */
    fun matchSchool(url: String, catalog: List<JwSchoolInfo>): JwSchoolInfo? {
        val target = parsed(url) ?: return null
        val host = target.host.lowercase().trimEnd('.')
        if (isVpnRewrite(host)) return null
        // Pending and unverified entries must never redirect a typed URL automatically.
        val candidates = catalog.mapNotNull { school ->
            parsed(school.url)?.let { school to it }
        }
        fun supportedSingle(matches: List<Pair<JwSchoolInfo, java.net.URI>>): JwSchoolInfo? =
            matches.singleOrNull()?.first?.takeIf { it.isSupported && it.canImport }
        val exact = candidates.filter { (_, entry) ->
            sameEntry(entry, target)
        }
        if (exact.isNotEmpty()) return supportedSingle(exact)
        val sameHost = candidates.filter { (_, entry) ->
            entry.host.equals(host, ignoreCase = true) && entry.port == target.port
        }
        if (sameHost.isNotEmpty()) return supportedSingle(sameHost)
        val domain = registrableDomain(host)
        return supportedSingle(candidates.filter { (_, entry) ->
            registrableDomain(entry.host) == domain
        })
    }
}
